package me.rerere.asr.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SherpaDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val phase: SherpaDownloadPhase = SherpaDownloadPhase.DOWNLOADING,
) {
    val percent: Int
        get() = if (totalBytes > 0) {
            ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
}

enum class SherpaDownloadPhase {
    DOWNLOADING,
    INSTALLING,
}

internal enum class ArchiveDownloadPlan {
    REUSE_COMPLETE,
    RESUME,
    RESTART,
}

internal fun archiveDownloadPlan(existingBytes: Long, expectedBytes: Long): ArchiveDownloadPlan = when {
    existingBytes <= 0L -> ArchiveDownloadPlan.RESTART
    expectedBytes > 0L && existingBytes == expectedBytes -> ArchiveDownloadPlan.REUSE_COMPLETE
    expectedBytes > 0L && existingBytes > expectedBytes -> ArchiveDownloadPlan.RESTART
    else -> ArchiveDownloadPlan.RESUME
}

class SherpaModelInstall(private val context: Context) {
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    fun rootDir(): File = File(context.filesDir, "local_stt").apply { mkdirs() }
    private fun downloadsDir(): File = File(rootDir(), "downloads").apply { mkdirs() }
    fun modelDir(id: String): File = File(rootDir(), "models/$id")

    suspend fun download(
        metadata: SherpaModelMetadata,
        onProgress: (SherpaDownloadProgress) -> Unit,
    ): InstalledSherpaModel = withContext(Dispatchers.IO) {
        val archive = File(downloadsDir(), "${metadata.id}.tar.bz2.part")
        downloadTo(metadata.archiveUrl, archive, metadata.archiveSizeBytes, onProgress)
        onProgress(
            SherpaDownloadProgress(
                bytesDownloaded = archive.length(),
                totalBytes = archive.length(),
                phase = SherpaDownloadPhase.INSTALLING,
            )
        )

        val target = modelDir(metadata.id)
        val staging = File(target.parentFile, "${target.name}.installing")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            extractRequiredFiles(archive, staging, metadata.files.values.toSet())
            val missing = metadata.files.values.filterNot { File(staging, it).isFile }
            if (missing.isNotEmpty()) throw IOException("Missing model files: ${missing.joinToString()}")

            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("Unable to finalize model installation")
            archive.delete()
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }

        InstalledSherpaModel(
            id = metadata.id,
            displayName = metadata.name,
            family = metadata.family,
            languages = metadata.languages,
            streaming = metadata.streaming,
            onlineModelType = metadata.onlineModelType,
            directoryPath = target.absolutePath,
            sizeInBytes = target.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            revision = metadata.revision,
            files = metadata.files.mapValues { (_, relative) -> File(target, relative).absolutePath },
            config = metadata.defaultConfig,
        )
    }

    suspend fun delete(model: InstalledSherpaModel) = withContext(Dispatchers.IO) {
        val target = File(model.directoryPath).canonicalFile
        val modelsRoot = File(rootDir(), "models").canonicalFile
        if (target.parentFile != modelsRoot) throw IOException("Refusing to delete model outside local STT storage")
        target.deleteRecursively()
        File(downloadsDir(), "${model.id}.tar.bz2.part").delete()
    }

    private suspend fun downloadTo(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (SherpaDownloadProgress) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val plan = archiveDownloadPlan(target.length(), expectedBytes)
        if (plan == ArchiveDownloadPlan.REUSE_COMPLETE) {
            onProgress(SherpaDownloadProgress(expectedBytes, expectedBytes))
            return
        }
        if (plan == ArchiveDownloadPlan.RESTART) target.delete()
        var existing = if (plan == ArchiveDownloadPlan.RESUME) target.length() else 0L
        val request = Request.Builder().url(url).apply {
            if (existing > 0) header("Range", "bytes=$existing-")
        }.build()

        http.newCall(request).execute().use { response ->
            // A completed archive left behind after a failed extraction asks for bytes past EOF on
            // the next attempt. GitHub correctly answers 416; discard that stale partial and retry
            // once from the beginning instead of surfacing an unhelpful download error.
            if (response.code == 416 && existing > 0) {
                target.delete()
                return downloadTo(url, target, expectedBytes, onProgress)
            }
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
            val append = existing > 0 && response.code == 206
            if (!append) existing = 0
            val total = when {
                expectedBytes > 0 -> expectedBytes
                response.body.contentLength() > 0 -> existing + response.body.contentLength()
                else -> -1
            }
            FileOutputStream(target, append).buffered().use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = existing
                    onProgress(SherpaDownloadProgress(downloaded, total))
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(SherpaDownloadProgress(downloaded, total))
                    }
                }
            }
        }
        if (expectedBytes > 0 && target.length() != expectedBytes) {
            throw IOException("Incomplete download: ${target.length()} of $expectedBytes bytes")
        }
    }

    private fun extractRequiredFiles(
        archive: File,
        destination: File,
        requiredPaths: Set<String>,
    ) {
        val normalizedRequired = requiredPaths.associateBy { it.replace('\\', '/').trimStart('/') }
        val found = mutableSetOf<String>()
        archive.inputStream().buffered().use { fileInput ->
            BZip2CompressorInputStream(fileInput).use { bzip ->
                TarArchiveInputStream(bzip).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        if (!entry.isFile) continue
                        val archivePath = entry.name.replace('\\', '/').trimStart('/')
                        val relative = normalizedRequired.keys.firstOrNull { required ->
                            archivePath == required || archivePath.endsWith("/$required")
                        } ?: continue
                        val output = File(destination, relative).canonicalFile
                        if (!output.path.startsWith(destination.canonicalPath + File.separator)) {
                            throw IOException("Unsafe archive path: ${entry.name}")
                        }
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { tar.copyTo(it) }
                        found += relative
                        if (found.size == normalizedRequired.size) break
                    }
                }
            }
        }
        val missing = normalizedRequired.keys - found
        if (missing.isNotEmpty()) throw IOException("Archive does not contain: ${missing.joinToString()}")
    }
}
