package me.rerere.rikkahub.service.workspace

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.sync.WorkspaceExportMetadata
import me.rerere.rikkahub.utils.LogUtil
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstallStage

sealed interface WorkspaceEnvironmentTask {
    val workspaceId: String
    val workspaceName: String

    data class InstallingRootfs(
        override val workspaceId: String,
        override val workspaceName: String,
        val progress: RootfsInstallProgress,
    ) : WorkspaceEnvironmentTask

    data class InstallingPython(
        override val workspaceId: String,
        override val workspaceName: String,
    ) : WorkspaceEnvironmentTask

    data class Completed(
        override val workspaceId: String,
        override val workspaceName: String,
    ) : WorkspaceEnvironmentTask

    data class Failed(
        override val workspaceId: String,
        override val workspaceName: String,
        val error: String,
    ) : WorkspaceEnvironmentTask
}

class WorkspaceEnvironmentManager(
    private val context: Context,
    private val workspaceRepository: WorkspaceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<Map<String, WorkspaceEnvironmentTask>>(emptyMap())
    val tasks: StateFlow<Map<String, WorkspaceEnvironmentTask>> = _tasks.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun isBusy(workspaceId: String): Boolean {
        val task = _tasks.value[workspaceId]
        return task is WorkspaceEnvironmentTask.InstallingRootfs || task is WorkspaceEnvironmentTask.InstallingPython
    }

    fun hasRunningTasks(): Boolean {
        return _tasks.value.values.any {
            it is WorkspaceEnvironmentTask.InstallingRootfs || it is WorkspaceEnvironmentTask.InstallingPython
        }
    }

    private fun startService() {
        runCatching {
            val intent = Intent(context, WorkspaceEnvironmentService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { error ->
            LogUtil.e(TAG, "Failed to start WorkspaceEnvironmentService", error)
        }
    }

    fun installRootfs(
        workspaceId: String,
        workspaceName: String,
        url: String,
        installPythonAfter: Boolean = false,
    ) {
        if (isBusy(workspaceId)) return
        startService()

        val job = scope.launch {
            _tasks.update {
                it + (workspaceId to WorkspaceEnvironmentTask.InstallingRootfs(
                    workspaceId = workspaceId,
                    workspaceName = workspaceName,
                    progress = RootfsInstallProgress(stage = RootfsInstallStage.DOWNLOADING),
                ))
            }

            try {
                workspaceRepository.installRootfs(workspaceId, url) { progress ->
                    _tasks.update { current ->
                        val existing = current[workspaceId]
                        if (existing is WorkspaceEnvironmentTask.InstallingRootfs) {
                            current + (workspaceId to existing.copy(progress = progress))
                        } else {
                            current
                        }
                    }
                }

                if (installPythonAfter) {
                    _tasks.update {
                        it + (workspaceId to WorkspaceEnvironmentTask.InstallingPython(
                            workspaceId = workspaceId,
                            workspaceName = workspaceName,
                        ))
                    }
                    workspaceRepository.installPython(workspaceId)
                }

                _tasks.update {
                    it + (workspaceId to WorkspaceEnvironmentTask.Completed(
                        workspaceId = workspaceId,
                        workspaceName = workspaceName,
                    ))
                }
            } catch (e: CancellationException) {
                _tasks.update { it - workspaceId }
                throw e
            } catch (error: Throwable) {
                LogUtil.e(TAG, "installRootfs failed for workspace $workspaceId", error)
                _tasks.update {
                    it + (workspaceId to WorkspaceEnvironmentTask.Failed(
                        workspaceId = workspaceId,
                        workspaceName = workspaceName,
                        error = error.message ?: "Installation failed",
                    ))
                }
            } finally {
                jobs.remove(workspaceId)
            }
        }
        jobs[workspaceId] = job
    }

    fun installPython(
        workspaceId: String,
        workspaceName: String,
    ) {
        if (isBusy(workspaceId)) return
        startService()

        val job = scope.launch {
            _tasks.update {
                it + (workspaceId to WorkspaceEnvironmentTask.InstallingPython(
                    workspaceId = workspaceId,
                    workspaceName = workspaceName,
                ))
            }

            try {
                workspaceRepository.installPython(workspaceId)
                _tasks.update {
                    it + (workspaceId to WorkspaceEnvironmentTask.Completed(
                        workspaceId = workspaceId,
                        workspaceName = workspaceName,
                    ))
                }
            } catch (e: CancellationException) {
                _tasks.update { it - workspaceId }
                throw e
            } catch (error: Throwable) {
                LogUtil.e(TAG, "installPython failed for workspace $workspaceId", error)
                _tasks.update {
                    it + (workspaceId to WorkspaceEnvironmentTask.Failed(
                        workspaceId = workspaceId,
                        workspaceName = workspaceName,
                        error = error.message ?: "Python installation failed",
                    ))
                }
            } finally {
                jobs.remove(workspaceId)
            }
        }
        jobs[workspaceId] = job
    }

    fun autoRestoreWorkspaces(workspaces: List<WorkspaceExportMetadata>) {
        val targets = workspaces.filter { it.hasRootfs }
        if (targets.isEmpty()) return

        scope.launch {
            for (ws in targets) {
                if (isBusy(ws.id)) continue
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                val runtime = workspaceRuntimeSupport(nativeLibDir)
                if (!runtime.supported) {
                    LogUtil.w(TAG, "Skipping auto-restore for workspace ${ws.id}: runtime unsupported on this device")
                    continue
                }
                val url = ws.rootfsUrl?.trim()?.ifBlank { null } ?: defaultRootfsUrl(nativeLibDir)
                installRootfs(
                    workspaceId = ws.id,
                    workspaceName = ws.name,
                    url = url,
                    installPythonAfter = ws.hasPython,
                )
                // Await completion of this workspace before starting the next to avoid thrashing CPU/bandwidth
                jobs[ws.id]?.join()
            }
        }
    }

    fun cancel(workspaceId: String) {
        jobs.remove(workspaceId)?.cancel()
        _tasks.update { it - workspaceId }
    }

    fun clearTask(workspaceId: String) {
        _tasks.update { it - workspaceId }
    }

    companion object {
        private const val TAG = "WorkspaceEnvManager"
    }
}
