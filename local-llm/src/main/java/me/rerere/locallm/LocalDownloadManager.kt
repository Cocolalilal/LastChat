package me.rerere.locallm

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state of an in-flight (or recently failed) download, keyed by model id. */
sealed interface LocalDownload {
    val modelId: String

    data class Running(
        override val modelId: String,
        val displayName: String,
        val progress: DownloadProgress,
        val isUpdate: Boolean,
    ) : LocalDownload

    data class Failed(
        override val modelId: String,
        val displayName: String,
        val message: String,
    ) : LocalDownload
}

/**
 * App-scoped manager for on-device model downloads. Runs on its own coroutine scope so a download keeps
 * going (and its progress keeps updating) even after the user leaves the on-device settings screen.
 * On success the model is persisted to [LocalModelStore] and the catalog is refreshed so update badges
 * settle.
 */
class LocalDownloadManager(
    private val context: Context,
    private val install: ModelInstall,
    private val store: LocalModelStore,
    private val catalog: LiteRtCatalog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<Map<String, LocalDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, LocalDownload>> = _downloads.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun isDownloading(id: String): Boolean = _downloads.value[id] is LocalDownload.Running

    /** Downloads (or re-downloads, for updates) a curated model. No-op if already running. */
    fun download(meta: LocalModelMetadata, isUpdate: Boolean = false) {
        if (isDownloading(meta.id)) return
        val job = scope.launch {
            put(
                LocalDownload.Running(
                    meta.id,
                    meta.name,
                    DownloadProgress(0, meta.sizeInBytes),
                    isUpdate,
                )
            )
            runCatching {
                install.download(meta) { progress ->
                    put(LocalDownload.Running(meta.id, meta.name, progress, isUpdate))
                }
            }.onSuccess { installed ->
                // Preserve any prior user config/icon/name across an update.
                val prior = store.get(meta.id)
                store.upsert(
                    installed.copy(
                        displayName = prior?.displayName ?: installed.displayName,
                        config = prior?.config ?: installed.config,
                        customIconUri = prior?.customIconUri,
                    )
                )
                runCatching { catalog.refresh() }
                remove(meta.id)
            }.onFailure { error ->
                put(LocalDownload.Failed(meta.id, meta.name, error.message ?: "download_failed"))
            }
            jobs.remove(meta.id)
        }
        jobs[meta.id] = job
    }

    /** Downloads a model from a pasted HuggingFace URL. */
    fun downloadFromUrl(url: String, spec: ImportSpec) {
        if (isDownloading(spec.id)) return
        val job = scope.launch {
            put(LocalDownload.Running(spec.id, spec.name, DownloadProgress(0, -1), false))
            runCatching {
                install.downloadFromUrl(url) { progress ->
                    put(LocalDownload.Running(spec.id, spec.name, progress, false))
                }
            }.onSuccess { installed ->
                store.upsert(installed)
                remove(spec.id)
            }.onFailure { error ->
                put(LocalDownload.Failed(spec.id, spec.name, error.message ?: "download_failed"))
            }
            jobs.remove(spec.id)
        }
        jobs[spec.id] = job
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        remove(id)
    }

    fun dismissError(id: String) {
        if (_downloads.value[id] is LocalDownload.Failed) remove(id)
    }

    private fun put(state: LocalDownload) = _downloads.update { it + (state.modelId to state) }
    private fun remove(id: String) = _downloads.update { it - id }
}
