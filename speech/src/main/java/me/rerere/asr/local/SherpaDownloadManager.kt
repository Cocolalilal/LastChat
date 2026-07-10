package me.rerere.asr.local

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SherpaDownload {
    val modelId: String

    data class Running(
        override val modelId: String,
        val displayName: String,
        val progress: SherpaDownloadProgress,
        val isUpdate: Boolean,
    ) : SherpaDownload

    data class Failed(
        override val modelId: String,
        val displayName: String,
        val message: String,
    ) : SherpaDownload
}

class SherpaDownloadManager(
    private val context: Context,
    private val install: SherpaModelInstall,
    private val store: SherpaModelStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val _downloads = MutableStateFlow<Map<String, SherpaDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, SherpaDownload>> = _downloads.asStateFlow()

    fun download(metadata: SherpaModelMetadata, isUpdate: Boolean = false) {
        if (jobs[metadata.id]?.isActive == true) return
        put(
            SherpaDownload.Running(
                metadata.id,
                metadata.name,
                SherpaDownloadProgress(0, metadata.archiveSizeBytes),
                isUpdate,
            )
        )
        ContextCompat.startForegroundService(context, Intent(context, SherpaDownloadService::class.java))
        jobs[metadata.id] = scope.launch {
            runCatching {
                install.download(metadata) { progress ->
                    put(SherpaDownload.Running(metadata.id, metadata.name, progress, isUpdate))
                }
            }.onSuccess { installed ->
                val prior = store.get(metadata.id)
                store.upsert(
                    installed.copy(
                        displayName = prior?.displayName ?: installed.displayName,
                        config = prior?.config ?: installed.config,
                        customIconUri = prior?.customIconUri,
                    )
                )
                remove(metadata.id)
            }.onFailure { error ->
                put(SherpaDownload.Failed(metadata.id, metadata.name, error.message ?: "download_failed"))
            }
            jobs.remove(metadata.id)
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        remove(id)
    }

    fun dismissError(id: String) = remove(id)

    private fun put(download: SherpaDownload) {
        _downloads.update { it + (download.modelId to download) }
    }

    private fun remove(id: String) {
        _downloads.update { it - id }
    }
}
