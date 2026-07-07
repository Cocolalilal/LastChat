package me.rerere.locallm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Streaming download progress. [totalBytes] may be -1 while unknown. */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

/** Parsed identity for a model to be installed from a pasted HuggingFace URL. */
data class ImportSpec(
    val id: String,
    val name: String,
    val hfRepo: String,
    val modelFile: String,
    val commitHash: String,
    val downloadUrl: String,
)

/**
 * Downloads `.litertlm` model files to app-private storage with resume support, and resolves pasted
 * HuggingFace URLs into installable specs.
 */
class ModelInstall(private val context: Context) {

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun modelDir(): File = File(context.filesDir, "local_models").apply { mkdirs() }

    fun modelFile(fileName: String): File = File(modelDir(), fileName)

    /** Downloads a curated model, returning its installed record. Resumes a prior partial download. */
    suspend fun download(
        meta: LocalModelMetadata,
        onProgress: (DownloadProgress) -> Unit,
    ): InstalledLocalModel {
        val target = modelFile(meta.modelFile)
        downloadTo(meta.downloadUrl, target, meta.sizeInBytes, onProgress)
        return InstalledLocalModel(
            id = meta.id,
            displayName = meta.name,
            filePath = target.absolutePath,
            commitHash = meta.commitHash,
            sizeInBytes = target.length(),
            supportsImage = meta.supportsImage,
            supportsAudio = meta.supportsAudio,
            supportsThinking = meta.supportsThinking,
            supportsSpeculativeDecoding = meta.supportsSpeculativeDecoding,
            defaultConfig = meta.defaultConfig,
            imported = false,
        )
    }

    /** Downloads a model from a pasted HuggingFace URL. */
    suspend fun downloadFromUrl(
        url: String,
        onProgress: (DownloadProgress) -> Unit,
    ): InstalledLocalModel {
        val spec = parseImportUrl(url) ?: throw IOException("invalid_url")
        val target = modelFile(spec.modelFile)
        downloadTo(spec.downloadUrl, target, -1, onProgress)
        return InstalledLocalModel(
            id = spec.id,
            displayName = spec.name,
            filePath = target.absolutePath,
            commitHash = spec.commitHash,
            sizeInBytes = target.length(),
            imported = true,
        )
    }

    fun delete(installed: InstalledLocalModel) {
        runCatching { File(installed.filePath).delete() }
        runCatching { File(installed.filePath + PART_SUFFIX).delete() }
    }

    private suspend fun downloadTo(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (DownloadProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File(target.absolutePath + PART_SUFFIX)
        var existing = if (part.exists()) part.length() else 0L

        val builder = Request.Builder().url(url)
        if (existing > 0) builder.header("Range", "bytes=$existing-")

        http.newCall(builder.build()).execute().use { resp ->
            // If the server ignored the Range (or the partial is stale), restart cleanly.
            if (existing > 0 && resp.code != 206) {
                part.delete()
                existing = 0
            }
            if (!resp.isSuccessful) throw IOException("http_${resp.code}")
            val body = resp.body ?: throw IOException("empty_body")

            val contentLength = body.contentLength()
            val total = when {
                expectedSize > 0 -> expectedSize
                contentLength > 0 -> existing + contentLength
                else -> -1L
            }

            body.byteStream().use { input ->
                java.io.RandomAccessFile(part, "rw").use { out ->
                    out.seek(existing)
                    val buffer = ByteArray(1 shl 16)
                    var downloaded = existing
                    var lastReported = 0L
                    onProgress(DownloadProgress(downloaded, total))
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = downloaded
                            onProgress(DownloadProgress(downloaded, total))
                        }
                    }
                    onProgress(DownloadProgress(downloaded, total))
                }
            }
        }

        if (expectedSize > 0 && part.length() != expectedSize) {
            // Size mismatch → corrupt/incomplete; drop the partial so a retry starts fresh.
            part.delete()
            throw IOException("size_mismatch")
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("rename_failed")
    }

    companion object {
        private const val PART_SUFFIX = ".part"
        private const val PROGRESS_STEP_BYTES = 2L * 1024 * 1024

        /**
         * Parses a HuggingFace `/blob/` or `/resolve/` URL of a `.litertlm` file into an [ImportSpec].
         * e.g. https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm
         */
        fun parseImportUrl(raw: String): ImportSpec? {
            val url = raw.trim()
            if (!url.startsWith("https://huggingface.co/")) return null
            val path = url.removePrefix("https://huggingface.co/").substringBefore('?')
            val marker = when {
                "/resolve/" in path -> "/resolve/"
                "/blob/" in path -> "/blob/"
                else -> return null
            }
            val repo = path.substringBefore(marker)
            val rest = path.substringAfter(marker)
            val commit = rest.substringBefore('/')
            val file = rest.substringAfter('/')
            if (repo.isBlank() || commit.isBlank() || file.isBlank()) return null
            if (!file.endsWith(".litertlm")) return null
            val downloadUrl = "https://huggingface.co/$repo/resolve/$commit/$file"
            val name = file.removeSuffix(".litertlm")
            return ImportSpec(
                id = "$repo/$file",
                name = name,
                hfRepo = repo,
                modelFile = file,
                commitHash = commit,
                downloadUrl = downloadUrl,
            )
        }
    }
}
