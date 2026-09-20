package me.rerere.common.runtime.local

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest

@Serializable
data class PortableDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

sealed interface PortableDownload {
    val modelId: String
    val displayName: String

    data class Running(
        override val modelId: String,
        override val displayName: String,
        val progress: PortableDownloadProgress,
        val kind: String,
    ) : PortableDownload

    data class Failed(
        override val modelId: String,
        override val displayName: String,
        val message: String,
        val kind: String,
    ) : PortableDownload
}

/**
 * Shared catalog + download manager for on-device LLM and STT files.
 * Inference stays on [me.rerere.common.runtime.OnDeviceLlmRuntime]; iOS can
 * download/manage the same files Android does even when the runtime is unavailable.
 */
class PortableOnDeviceModelManager(
    private val httpClient: PlatformHttpClient,
    private val fileStore: PlatformFileStore,
    private val bundledLlmCatalog: suspend () -> String,
    private val bundledSttCatalog: suspend () -> String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _llmCatalog = MutableStateFlow(LocalModelCatalog())
    val llmCatalog: StateFlow<LocalModelCatalog> = _llmCatalog.asStateFlow()

    private val _sttCatalog = MutableStateFlow(SherpaModelCatalog())
    val sttCatalog: StateFlow<SherpaModelCatalog> = _sttCatalog.asStateFlow()

    private val _installedLlm = MutableStateFlow<List<InstalledLocalModel>>(emptyList())
    val installedLlm: StateFlow<List<InstalledLocalModel>> = _installedLlm.asStateFlow()

    private val _installedStt = MutableStateFlow<List<InstalledSherpaModel>>(emptyList())
    val installedStt: StateFlow<List<InstalledSherpaModel>> = _installedStt.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, PortableDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, PortableDownload>> = _downloads.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    suspend fun warmUp() {
        _llmCatalog.value = runCatching {
            json.decodeFromString<LocalModelCatalog>(bundledLlmCatalog())
        }.getOrElse { LocalModelCatalog() }
        _sttCatalog.value = runCatching {
            json.decodeFromString<SherpaModelCatalog>(bundledSttCatalog())
        }.getOrElse { SherpaModelCatalog() }
        _installedLlm.value = loadInstalled(LLM_INDEX_PATH, emptyList())
        _installedStt.value = loadInstalled(STT_INDEX_PATH, emptyList())
    }

    fun downloadLlm(meta: LocalModelMetadata) {
        startDownload(meta.id, meta.name, "llm", meta.sizeInBytes, meta.downloadUrl) { path, size ->
            val installed = InstalledLocalModel(
                id = meta.id,
                displayName = meta.name,
                kind = meta.kind,
                filePath = path,
                tokenizerPath = null,
                commitHash = meta.commitHash,
                sizeInBytes = size,
                minDeviceMemoryGb = meta.minDeviceMemoryInGb,
                supportsImage = meta.supportsImage,
                supportsAudio = meta.supportsAudio,
                supportsThinking = meta.supportsThinking,
                supportsSpeculativeDecoding = meta.supportsSpeculativeDecoding,
                embeddingDimension = meta.embeddingDimension,
                defaultConfig = meta.defaultConfig,
            )
            val next = _installedLlm.value.filterNot { it.id == meta.id } + installed
            _installedLlm.value = next
            persist(LLM_INDEX_PATH, next)
        }
    }

    fun downloadStt(meta: SherpaModelMetadata) {
        startDownload(meta.id, meta.name, "stt", meta.archiveSizeBytes, meta.archiveUrl) { path, size ->
            val installed = InstalledSherpaModel(
                id = meta.id,
                displayName = meta.name,
                family = meta.family,
                languages = meta.languages,
                streaming = meta.streaming,
                onlineModelType = meta.onlineModelType,
                directoryPath = path,
                sizeInBytes = size,
                revision = meta.revision,
                files = meta.files,
                config = meta.defaultConfig,
            )
            val next = _installedStt.value.filterNot { it.id == meta.id } + installed
            _installedStt.value = next
            persist(STT_INDEX_PATH, next)
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        _downloads.update { it - id }
    }

    suspend fun deleteLlm(id: String) {
        val existing = _installedLlm.value.firstOrNull { it.id == id } ?: return
        fileStore.delete(existing.filePath)
        val next = _installedLlm.value.filterNot { it.id == id }
        _installedLlm.value = next
        persist(LLM_INDEX_PATH, next)
    }

    suspend fun deleteStt(id: String) {
        val existing = _installedStt.value.firstOrNull { it.id == id } ?: return
        fileStore.delete(existing.directoryPath)
        val next = _installedStt.value.filterNot { it.id == id }
        _installedStt.value = next
        persist(STT_INDEX_PATH, next)
    }

    private fun startDownload(
        id: String,
        name: String,
        kind: String,
        expectedBytes: Long,
        url: String,
        onInstalled: suspend (path: String, size: Long) -> Unit,
    ) {
        if (jobs[id]?.isActive == true) return
        jobs[id] = scope.launch {
            val dest = "$DOWNLOAD_DIR/$kind/$id.bin"
            put(PortableDownload.Running(id, name, PortableDownloadProgress(0, expectedBytes), kind))
            runCatching {
                if (fileStore.exists(dest)) fileStore.delete(dest)
                val status = httpClient.downloadTo(
                    request = PlatformHttpRequest(method = "GET", url = url),
                    onChunk = { chunk -> fileStore.appendBytes(dest, chunk) },
                    onProgress = { downloaded, total ->
                        put(
                            PortableDownload.Running(
                                id,
                                name,
                                PortableDownloadProgress(downloaded, if (total > 0) total else expectedBytes),
                                kind,
                            ),
                        )
                    },
                )
                if (status.statusCode !in 200..299) {
                    error("download failed: HTTP ${status.statusCode}")
                }
                val size = fileStore.fileSize(dest) ?: 0L
                onInstalled(dest, size)
                _downloads.update { it - id }
            }.onFailure { error ->
                put(PortableDownload.Failed(id, name, error.message ?: "download_failed", kind))
            }
            jobs.remove(id)
        }
    }

    private fun put(state: PortableDownload) = _downloads.update { it + (state.modelId to state) }

    private suspend inline fun <reified T> persist(path: String, value: T) {
        fileStore.writeBytes(path, json.encodeToString(value).encodeToByteArray())
    }

    private suspend inline fun <reified T> loadInstalled(path: String, fallback: T): T {
        val bytes = fileStore.readBytes(path) ?: return fallback
        return runCatching { json.decodeFromString<T>(bytes.decodeToString()) }.getOrElse { fallback }
    }

    companion object {
        private const val DOWNLOAD_DIR = "on_device_models"
        private const val LLM_INDEX_PATH = "$DOWNLOAD_DIR/llm_index.json"
        private const val STT_INDEX_PATH = "$DOWNLOAD_DIR/stt_index.json"
    }
}
