package me.rerere.asr.local

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import org.koin.android.ext.android.inject

class SherpaDownloadService : Service() {
    private val manager: SherpaDownloadManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification("Downloading local speech models", 0, 0, true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        manager.downloads.onEach { downloads ->
            val running = downloads.values.filterIsInstance<SherpaDownload.Running>()
            if (running.isEmpty()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@onEach
            }
            val installing = running.filter { it.progress.phase == SherpaDownloadPhase.INSTALLING }
            val downloaded = running.sumOf { it.progress.bytesDownloaded }
            val total = running.sumOf { it.progress.totalBytes.coerceAtLeast(0) }
            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
            val title = if (installing.isNotEmpty()) {
                "Installing ${installing.joinToString { it.displayName }}"
            } else {
                running.joinToString { it.displayName }
            }
            getSystemService(android.app.NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                notification(title, percent, 100, installing.isNotEmpty() || total <= 0),
            )
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(title: String, progress: Int, max: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(
                if (indeterminate && title.startsWith("Installing ")) {
                    "Preparing model for offline speech recognition"
                } else {
                    "Downloading for offline speech recognition"
                }
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(max, progress, indeterminate)
            .setOngoing(true)
            .build()

    companion object {
        const val CHANNEL_ID = "local_model_download"
        private const val NOTIFICATION_ID = 1002
    }
}
