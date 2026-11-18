package me.rerere.asr.local

import android.content.Context
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class SherpaDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val phase: SherpaDownloadPhase = SherpaDownloadPhase.DOWNLOADING,
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

enum class SherpaDownloadPhase { DOWNLOADING, INSTALLING }

internal enum class ArchiveDownloadPlan { REUSE_COMPLETE, RESUME, RESTART }

internal fun archiveDownloadPlan(existingBytes: Long, expectedBytes: Long): ArchiveDownloadPlan = when {
    existingBytes <= 0L -> ArchiveDownloadPlan.RESTART
    expectedBytes > 0L && existingBytes == expectedBytes -> ArchiveDownloadPlan.REUSE_COMPLETE
    expectedBytes > 0L && existingBytes > expectedBytes -> ArchiveDownloadPlan.RESTART
    else -> ArchiveDownloadPlan.RESUME
}

class SherpaModelInstall(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(10, TimeUnit.MINUTES).build(),
) {
    fun rootDir(): File = File(context.filesDir, "local_stt").apply { mkdirs() }
    private fun downloadsDir(): File = File(rootDir(), "downloads").apply { mkdirs() }
    fun modelDir(id: String): File {
        require(id.isNotBlank() && id != "." && id != ".." && '/' !in id && '\\' !in id) { "Invalid model ID" }
        return File(rootDir(), "models/$id")
    }

    suspend fun download(
        metadata: SherpaModelMetadata,
        onProgress: (SherpaDownloadProgress) -> Unit,
    ): InstalledSherpaModel = withContext(Dispatchers.IO) {
        require(metadata.files.isNotEmpty()) { "Model has no runtime files" }
        val target = modelDir(metadata.id)
        val committed = runCatching {
            Json.decodeFromString<InstalledSherpaModel>(File(target, INSTALL_MANIFEST).readText())
        }.getOrNull()
        if (committed?.id == metadata.id && committed.revision == metadata.revision &&
            metadata.files.all { (role, relative) ->
                val file = safeFile(target, relative)
                committed.files[role] == file.absolutePath && file.isFile && file.length() > 0 &&
                    (metadata.downloads[role]?.sizeBytes?.let { it == file.length() } ?: true)
            }) {
            onProgress(SherpaDownloadProgress(metadata.downloadSizeBytes, metadata.downloadSizeBytes, SherpaDownloadPhase.INSTALLING))
            return@withContext committed
        }
        val staging = File(target.parentFile, "${target.name}.installing")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create model staging directory" }
        val archive = File(downloadsDir(), "${metadata.id}.tar.bz2.part")
        try {
            if (metadata.downloads.isNotEmpty()) {
                require(metadata.downloads.keys == metadata.files.keys) { "Incomplete model download metadata" }
                var completed = 0L
                val total = metadata.downloadSizeBytes
                metadata.files.forEach { (role, relative) ->
                    val source = metadata.downloads.getValue(role)
                    require(source.sizeBytes > 0 && source.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid model checksum" }
                    val partial = File(downloadsDir(), "${metadata.id}/${source.sha256}.part")
                    downloadTo(source.url, partial, source.sizeBytes) { progress ->
                        onProgress(SherpaDownloadProgress(completed + progress.bytesDownloaded, total))
                    }
                    val lastFile = completed + source.sizeBytes == total
                    if (lastFile) onProgress(SherpaDownloadProgress(0, source.sizeBytes, SherpaDownloadPhase.INSTALLING))
                    verifyChecksum(partial, source.sha256) { verified ->
                        if (lastFile) onProgress(SherpaDownloadProgress(verified, source.sizeBytes, SherpaDownloadPhase.INSTALLING))
                    }
                    val output = safeFile(staging, relative)
                    output.parentFile?.mkdirs()
                    partial.inputStream().buffered().use { input ->
                        output.outputStream().buffered().use { copyCancellable(input, it) }
                    }
                    completed += source.sizeBytes
                }
                onProgress(SherpaDownloadProgress(total, total, SherpaDownloadPhase.INSTALLING))
            } else {
                downloadTo(metadata.archiveUrl, archive, metadata.archiveSizeBytes, onProgress)
                onProgress(SherpaDownloadProgress(0, archive.length(), SherpaDownloadPhase.INSTALLING))
                extractRequiredFiles(archive, staging, metadata.files.values.toSet(), onProgress)
            }
            currentCoroutineContext().ensureActive()
            val missing = metadata.files.values.filterNot { safeFile(staging, it).let { f -> f.isFile && f.length() > 0 } }
            if (missing.isNotEmpty()) throw IOException("Missing model files: ${missing.joinToString()}")
            val installed = InstalledSherpaModel(
                id = metadata.id, displayName = metadata.name, family = metadata.family,
                languages = metadata.languages, streaming = metadata.streaming, onlineModelType = metadata.onlineModelType,
                directoryPath = target.absolutePath,
                sizeInBytes = metadata.files.values.sumOf { safeFile(staging, it).length() },
                revision = metadata.revision,
                files = metadata.files.mapValues { (_, relative) -> safeFile(target, relative).absolutePath },
                config = metadata.defaultConfig,
            )
            // The manifest moves with the files, allowing the registry to recover after process death.
            File(staging, INSTALL_MANIFEST).writeText(Json.encodeToString(installed))
            currentCoroutineContext().ensureActive()
            promote(staging, target)
            archive.delete()
            File(downloadsDir(), metadata.id).deleteRecursively()
            installed
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun delete(model: InstalledSherpaModel) = withContext(Dispatchers.IO) {
        val target = File(model.directoryPath).canonicalFile
        val modelsRoot = File(rootDir(), "models").canonicalFile
        if (target.parentFile != modelsRoot) throw IOException("Refusing to delete model outside local STT storage")
        if (target.exists() && !target.deleteRecursively()) throw IOException("Unable to delete model files")
        File(downloadsDir(), "${model.id}.tar.bz2.part").delete()
        File(downloadsDir(), model.id).deleteRecursively()
    }

    private fun promote(staging: File, target: File) {
        val backup = File(target.parentFile, "${target.name}.previous")
        // Restore a previous install if a process was killed during an earlier promotion.
        if (backup.exists() && !target.exists() && !backup.renameTo(target)) throw IOException("Unable to restore previous model")
        backup.deleteRecursively()
        if (target.exists() && !target.renameTo(backup)) throw IOException("Unable to preserve previous model")
        if (!staging.renameTo(target)) {
            backup.renameTo(target)
            throw IOException("Unable to finalize model installation")
        }
        backup.deleteRecursively()
    }

    private suspend fun verifyChecksum(file: File, expected: String, onVerified: (Long) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            var verified = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                verified += count
                onVerified(verified)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            file.delete()
            throw IOException("Model checksum mismatch. Retry the download.")
        }
    }

    private suspend fun downloadTo(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (SherpaDownloadProgress) -> Unit,
    ): Unit = coroutineScope {
        target.parentFile?.mkdirs()
        val plan = archiveDownloadPlan(target.length(), expectedBytes)
        if (plan == ArchiveDownloadPlan.REUSE_COMPLETE) {
            onProgress(SherpaDownloadProgress(expectedBytes, expectedBytes))
            return@coroutineScope
        }
        if (plan == ArchiveDownloadPlan.RESTART) target.delete()
        val existing = if (plan == ArchiveDownloadPlan.RESUME) target.length() else 0L
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").apply {
            if (existing > 0) header("Range", "bytes=$existing-")
        }.build()
        val call = http.newCall(request)
        // Cancel a blocked connect/read immediately instead of waiting for the socket timeout.
        val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            var restart = false
            call.execute().use { response ->
                if (response.code == 416 && existing > 0) {
                    target.delete()
                    restart = true
                    return@use
                }
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val append = existing > 0 && response.code == 206
                if (response.code == 206) {
                    val rangeStart = response.header("Content-Range")?.substringAfter("bytes ")?.substringBefore('-')?.toLongOrNull()
                    if (rangeStart != (if (append) existing else 0L)) throw IOException("Invalid download resume response")
                }
                val offset = if (append) existing else 0L
                val total = expectedBytes.takeIf { it > 0 } ?: response.body.contentLength().takeIf { it > 0 }?.plus(offset) ?: -1L
                FileOutputStream(target, append).buffered().use { output ->
                    response.body.byteStream().use { input ->
                        var downloaded = offset
                        onProgress(SherpaDownloadProgress(downloaded, total))
                        copyCancellable(input, output) { count ->
                            downloaded += count
                            onProgress(SherpaDownloadProgress(downloaded, total))
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (restart) downloadTo(url, target, expectedBytes, onProgress)
            if (expectedBytes > 0 && target.length() != expectedBytes) {
                throw IOException("Incomplete download: ${target.length()} of $expectedBytes bytes")
            }
        } catch (error: Exception) {
            // Socket cancellation reports IOException; preserve the coroutine cancellation contract.
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            cancellation.cancel()
        }
    }

    private suspend fun extractRequiredFiles(
        archive: File, destination: File, requiredPaths: Set<String>,
        onProgress: (SherpaDownloadProgress) -> Unit,
    ) {
        val required = requiredPaths.associateBy { it.replace('\\', '/').trimStart('/') }
        val found = mutableSetOf<String>()
        archive.inputStream().buffered().use { fileInput ->
            BZip2CompressorInputStream(fileInput).use { bzip ->
                TarArchiveInputStream(bzip).use { tar ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = tar.nextEntry ?: break
                        if (!entry.isFile) continue
                        val path = entry.name.replace('\\', '/').trimStart('/')
                        val relative = required.keys.firstOrNull { path == it || path.endsWith("/$it") }
                        val progress = { _: Int ->
                            onProgress(SherpaDownloadProgress(bzip.compressedCount, archive.length(), SherpaDownloadPhase.INSTALLING))
                        }
                        if (relative == null) {
                            copyCancellable(tar, null, progress)
                        } else {
                            val output = safeFile(destination, relative)
                            output.parentFile?.mkdirs()
                            output.outputStream().buffered().use { copyCancellable(tar, it, progress) }
                            found += relative
                        }
                        if (found.size == required.size) break
                    }
                }
            }
        }
        val missing = required.keys - found
        if (missing.isNotEmpty()) throw IOException("Archive does not contain: ${missing.joinToString()}")
    }

    private fun safeFile(root: File, relative: String): File {
        val file = File(root, relative).canonicalFile
        if (!file.path.startsWith(root.canonicalPath + File.separator)) throw IOException("Unsafe model path: $relative")
        return file
    }

    private suspend fun copyCancellable(input: InputStream, output: OutputStream?, onBytes: (Int) -> Unit = {}) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output?.write(buffer, 0, count)
            onBytes(count)
        }
    }

    companion object {
        const val INSTALL_MANIFEST = "installed.json"
    }
}
