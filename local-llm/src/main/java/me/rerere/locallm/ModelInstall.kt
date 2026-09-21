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
class ModelInstall(
    private val context: Context,
    private val huggingFaceTokenProvider: () -> String? = { null },
) {

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
        // Embedding models ship a small SentencePiece tokenizer alongside the .tflite; fetch it first
        // (it's tiny, so we don't fold it into the reported progress of the large model file).
        val tokenizerPath = meta.tokenizerDownloadUrl?.let { url ->
            val file = modelFile(meta.tokenizerFile!!)
            downloadTo(url, file, -1) { /* no progress for the tokenizer */ }
            file.absolutePath
        }
        val target = modelFile(meta.modelFile)
        downloadTo(meta.downloadUrl, target, meta.sizeInBytes, onProgress)
        return InstalledLocalModel(
            id = meta.id,
            displayName = meta.name,
            kind = meta.kind,
            filePath = target.absolutePath,
            tokenizerPath = tokenizerPath,
            commitHash = meta.commitHash,
            sizeInBytes = target.length(),
            minDeviceMemoryGb = meta.minDeviceMemoryInGb,
            supportsImage = meta.supportsImage,
            supportsAudio = meta.supportsAudio,
            supportsThinking = meta.supportsThinking,
            supportsSpeculativeDecoding = meta.supportsSpeculativeDecoding,
            embeddingDimension = meta.embeddingDimension,
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
        installed.tokenizerPath?.let { runCatching { File(it).delete() } }
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

        val reqBuilder = Request.Builder().url(url)
        if (url.startsWith("https://huggingface.co/")) {
            huggingFaceTokenProvider()?.trim()?.takeIf { it.isNotBlank() }?.let { token ->
                reqBuilder.header("Authorization", "Bearer $token")
            }
        }
        if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")

        http.newCall(reqBuilder.build()).execute().use { resp ->
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

        if (expectedSize > 0 && Math.abs(part.length() - expectedSize) > 10L * 1024 * 1024) {
            // Size mismatch by more than 10MB → corrupt/incomplete; drop the partial so a retry starts fresh.
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
         * Parses a HuggingFace `/blob/`, `/resolve/`, or `/raw/` URL (or shorthand repo/file path)
         * of a `.litertlm` file into an [ImportSpec].
         *
         * Supports:
         * - Standard HTTPS resolve / blob / raw URLs: https://huggingface.co/owner/repo/resolve/main/model.litertlm
         * - Short domains: https://hf.co/owner/repo/resolve/main/model.litertlm
         * - Optional `www.` prefix (www.huggingface.co or www.hf.co)
         * - Missing scheme (huggingface.co/... or hf.co/...)
         * - HTTP scheme (upgraded to HTTPS)
         * - Shorthand repo format: owner/repo/resolve/main/model.litertlm or owner/repo/model.litertlm
         * - Nested directory paths within repositories: owner/repo/resolve/main/sub/dir/model.litertlm
         * - Query parameters (?download=true) and fragments (#...)
         * - Surrounding quotes or angle brackets
         * - Case-insensitive `.litertlm` extension check
         */
        fun parseImportUrl(raw: String): ImportSpec? {
            val trimmed = raw.trim().trim('\"', '\'', '<', '>')
            if (trimmed.isBlank()) return null

            // Strip query string and fragment
            val withoutQueryOrFragment = trimmed.substringBefore('?').substringBefore('#')

            // Normalize scheme
            val withoutScheme = when {
                withoutQueryOrFragment.startsWith("https://", ignoreCase = true) ->
                    withoutQueryOrFragment.substring(8)
                withoutQueryOrFragment.startsWith("http://", ignoreCase = true) ->
                    withoutQueryOrFragment.substring(7)
                else -> withoutQueryOrFragment
            }

            // Check if domain is a known HuggingFace domain
            val withoutWww = if (withoutScheme.startsWith("www.", ignoreCase = true)) {
                withoutScheme.substring(4)
            } else {
                withoutScheme
            }

            val path = when {
                withoutWww.startsWith("huggingface.co/", ignoreCase = true) ->
                    withoutWww.substring("huggingface.co/".length)
                withoutWww.startsWith("hf.co/", ignoreCase = true) ->
                    withoutWww.substring("hf.co/".length)
                // If it had http(s):// but wasn't a HuggingFace domain, reject it
                withoutQueryOrFragment.startsWith("https://", ignoreCase = true) ||
                withoutQueryOrFragment.startsWith("http://", ignoreCase = true) ->
                    return null
                // If the first segment looks like an external domain with a dot (e.g. example.com/...), reject it
                withoutWww.substringBefore('/').contains('.') ->
                    return null
                // Otherwise treat as a shorthand repo path (e.g. owner/repo/...)
                else -> withoutWww
            }.trim('/')

            if (path.isBlank()) return null

            val marker = when {
                "/resolve/" in path -> "/resolve/"
                "/blob/" in path -> "/blob/"
                "/raw/" in path -> "/raw/"
                else -> null
            }

            val repo: String
            val commit: String
            val filePath: String

            if (marker != null) {
                repo = path.substringBefore(marker).trim('/')
                val rest = path.substringAfter(marker).trim('/')
                if (repo.isBlank() || rest.isBlank()) return null
                if (!rest.contains('/')) return null
                commit = rest.substringBefore('/')
                filePath = rest.substringAfter('/')
            } else {
                // Shorthand format: owner/repo/path/to/file.litertlm or repo/file.litertlm
                val segments = path.split('/').filter { it.isNotBlank() }
                if (segments.size < 2) return null
                if (segments.size == 2) {
                    repo = segments[0]
                    commit = "main"
                    filePath = segments[1]
                } else {
                    repo = "${segments[0]}/${segments[1]}"
                    commit = "main"
                    filePath = segments.drop(2).joinToString("/")
                }
            }

            if (repo.isBlank() || commit.isBlank() || filePath.isBlank()) return null

            val fileName = filePath.substringAfterLast('/')
            val ext = fileName.substringAfterLast('.', "")
            if (!ext.equals("litertlm", ignoreCase = true)) return null

            val cleanFileName = fileName.replace("%20", " ")
            val name = cleanFileName.substringBeforeLast('.')
            val downloadUrl = "https://huggingface.co/$repo/resolve/$commit/$filePath"

            return ImportSpec(
                id = "$repo/$filePath",
                name = name,
                hfRepo = repo,
                modelFile = cleanFileName,
                commitHash = commit,
                downloadUrl = downloadUrl,
            )
        }
    }
}
