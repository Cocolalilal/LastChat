package me.rerere.asr.local

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

sealed interface SherpaDownload {
    val modelId: String
    data class Running(override val modelId: String, val displayName: String,
        val progress: SherpaDownloadProgress, val isUpdate: Boolean) : SherpaDownload
    data class Failed(override val modelId: String, val displayName: String, val message: String) : SherpaDownload
}

class SherpaDownloadManager(
    private val context: Context,
    private val install: SherpaModelInstall,
    private val store: SherpaModelStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val lock = Any()
    private val _downloads = MutableStateFlow<Map<String, SherpaDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, SherpaDownload>> = _downloads.asStateFlow()

    fun download(metadata: SherpaModelMetadata, isUpdate: Boolean = false): Unit = synchronized(lock) {
        val priorJob = jobs[metadata.id]
        if (priorJob?.isActive == true) return@synchronized
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val ownJob = currentCoroutineContext().job
            try {
                // A retry cannot touch the same staging files until cancellation has cleaned up.
                priorJob?.join()
                var lastTime = 0L
                var lastPhase: SherpaDownloadPhase? = null
                val installed = install.download(metadata) { progress ->
                    val now = SystemClock.elapsedRealtime()
                    if (progress.phase != lastPhase || now - lastTime >= 100 ||
                        (progress.totalBytes > 0 && progress.bytesDownloaded == progress.totalBytes)) {
                        synchronized(lock) {
                            if (ownJob.isActive && jobs[metadata.id] === ownJob) {
                                put(SherpaDownload.Running(metadata.id, metadata.name, progress, isUpdate))
                            }
                        }
                        lastTime = now
                        lastPhase = progress.phase
                    }
                }
                currentCoroutineContext().ensureActive()
                // Persistence failures are installation failures too; never leave a stuck running row.
                val prior = store.get(metadata.id)
                store.upsert(installed.copy(displayName = prior?.displayName ?: installed.displayName,
                    config = prior?.config ?: installed.config, customIconUri = prior?.customIconUri))
                synchronized(lock) { if (jobs[metadata.id] === ownJob) remove(metadata.id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (jobs[metadata.id] === ownJob) put(SherpaDownload.Failed(metadata.id, metadata.name,
                        error.message ?: "Unable to install speech model"))
                }
            } finally {
                synchronized(lock) {
                    if (jobs[metadata.id] === ownJob) {
                        jobs.remove(metadata.id)
                        if (_downloads.value[metadata.id] is SherpaDownload.Running) remove(metadata.id)
                    }
                }
            }
        }
        jobs[metadata.id] = job
        put(SherpaDownload.Running(metadata.id, metadata.name, SherpaDownloadProgress(0, metadata.downloadSizeBytes), isUpdate))
        try {
            ContextCompat.startForegroundService(context, Intent(context, SherpaDownloadService::class.java))
            job.start()
        } catch (error: Exception) {
            jobs.remove(metadata.id)
            job.cancel()
            put(SherpaDownload.Failed(metadata.id, metadata.name, error.message ?: "Unable to start model download"))
        }
    }

    fun cancel(id: String) = synchronized(lock) {
        jobs[id]?.cancel()
        remove(id)
    }

    fun dismissError(id: String) = remove(id)

    private fun put(download: SherpaDownload) { _downloads.update { it + (download.modelId to download) } }
    private fun remove(id: String) { _downloads.update { it - id } }
}
