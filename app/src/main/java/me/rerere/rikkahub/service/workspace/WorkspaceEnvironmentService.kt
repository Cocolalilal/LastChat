package me.rerere.rikkahub.service.workspace

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.rerere.rikkahub.R
import me.rerere.rikkahub.WORKSPACE_ENVIRONMENT_NOTIFICATION_CHANNEL_ID
import me.rerere.workspace.RootfsInstallStage
import org.koin.android.ext.android.inject

class WorkspaceEnvironmentService : Service() {
    private val environmentManager: WorkspaceEnvironmentManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val initialNotification = NotificationCompat.Builder(
            this,
            WORKSPACE_ENVIRONMENT_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.workspace_env_notification_title))
            .setContentText(getString(R.string.workspace_env_notification_downloading_indeterminate, ""))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            initialNotification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        val notificationManager = getSystemService(NotificationManager::class.java)

        environmentManager.tasks.onEach { tasks ->
            val runningTasks = tasks.values.filter {
                it is WorkspaceEnvironmentTask.InstallingRootfs || it is WorkspaceEnvironmentTask.InstallingPython
            }

            if (runningTasks.isEmpty()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@onEach
            }

            val active = runningTasks.first()
            val builder = NotificationCompat.Builder(
                this@WorkspaceEnvironmentService,
                WORKSPACE_ENVIRONMENT_NOTIFICATION_CHANNEL_ID
            )
                .setContentTitle(getString(R.string.workspace_env_notification_title))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)

            when (active) {
                is WorkspaceEnvironmentTask.InstallingRootfs -> {
                    when (active.progress.stage) {
                        RootfsInstallStage.DOWNLOADING -> {
                            val total = active.progress.totalBytes ?: -1L
                            val read = active.progress.bytesRead
                            if (total > 0) {
                                val progress = (read * 100 / total).toInt().coerceIn(0, 100)
                                builder.setContentText(
                                    getString(
                                        R.string.workspace_env_notification_downloading,
                                        active.workspaceName,
                                        read / 1_000_000,
                                        total / 1_000_000
                                    )
                                )
                                builder.setProgress(100, progress, false)
                            } else {
                                builder.setContentText(
                                    getString(
                                        R.string.workspace_env_notification_downloading_indeterminate,
                                        active.workspaceName
                                    )
                                )
                                builder.setProgress(0, 0, true)
                            }
                        }
                        RootfsInstallStage.EXTRACTING, RootfsInstallStage.CONFIGURING, RootfsInstallStage.INSTALLED -> {
                            builder.setContentText(
                                getString(R.string.workspace_env_notification_extracting, active.workspaceName)
                            )
                            builder.setProgress(0, 0, true)
                        }
                    }
                }
                is WorkspaceEnvironmentTask.InstallingPython -> {
                    builder.setContentText(
                        getString(R.string.workspace_env_notification_installing_python, active.workspaceName)
                    )
                    builder.setProgress(0, 0, true)
                }
                else -> Unit
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
    }
}
