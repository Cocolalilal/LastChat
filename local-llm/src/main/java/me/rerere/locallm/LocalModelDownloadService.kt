package me.rerere.locallm

import android.app.Service
import android.content.Intent
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

class LocalModelDownloadService : Service() {
    private val downloadManager: LocalDownloadManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        
        val notification = NotificationCompat.Builder(this, "local_model_download")
            .setContentTitle("LastChat")
            .setContentText("Downloading local models...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
            
        ServiceCompat.startForeground(
            this,
            1001,
            notification,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
        
        downloadManager.downloads.onEach { downloads ->
            val running = downloads.values.filterIsInstance<LocalDownload.Running>()
            if (running.isEmpty()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@onEach
            }
            
            // Update notification
            var totalBytes = 0L
            var downloadedBytes = 0L
            for (r in running) {
                if (r.progress.totalBytes > 0) {
                    totalBytes += r.progress.totalBytes
                    downloadedBytes += r.progress.bytesDownloaded
                }
            }
            val titles = running.joinToString { it.displayName }
            
            val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100).toInt() else 0
            val maxProgress = if (totalBytes > 0) 100 else 0
            
            val updatedNotification = NotificationCompat.Builder(this@LocalModelDownloadService, "local_model_download")
                .setContentTitle("Downloading: $titles")
                .setContentText("${downloadedBytes / 1_000_000} MB / ${totalBytes / 1_000_000} MB")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(maxProgress, progress, totalBytes <= 0)
                .setOngoing(true)
                .build()
                
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.notify(1001, updatedNotification)
            
        }.launchIn(scope)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
