package me.rerere.rikkahub.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.LOCAL_MODEL_DOWNLOAD_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.local.LocalInstallProgressSnapshot
import me.rerere.rikkahub.data.ai.local.LocalModelRepository
import me.rerere.rikkahub.data.ai.local.LocalModelStatus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val LOCAL_MODEL_NOTIFICATION_ID = 3101

class LocalModelInstallWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val repository: LocalModelRepository by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val action = inputData.getString(KEY_ACTION) ?: return@withContext Result.failure()
        val catalogId = inputData.getString(KEY_CATALOG_ID) ?: return@withContext Result.failure()
        ensureNotificationChannel()
        try {
            setForeground(getForegroundInfo())
            when (action) {
                ACTION_DOWNLOAD -> repository.downloadModel(catalogId) { snapshot ->
                    setForeground(createForegroundInfo(snapshot))
                }

                ACTION_IMPORT -> {
                    val sourceUri = inputData.getString(KEY_SOURCE_URI)?.takeIf { it.isNotBlank() }
                        ?: return@withContext Result.failure()
                    repository.importModel(catalogId, Uri.parse(sourceUri)) { snapshot ->
                        setForeground(createForegroundInfo(snapshot))
                    }
                }

                else -> return@withContext Result.failure()
            }
            showTerminalNotification(
                title = applicationContext.getString(R.string.local_model_notification_ready_title),
                body = applicationContext.getString(R.string.local_model_notification_ready_body),
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            repository.markCanceled(catalogId)
            throw cancelled
        } catch (throwable: Throwable) {
            showTerminalNotification(
                title = applicationContext.getString(R.string.local_model_notification_failed_title),
                body = throwable.message ?: applicationContext.getString(R.string.local_model_notification_failed_body),
            )
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(
            LocalInstallProgressSnapshot(
                catalogId = inputData.getString(KEY_CATALOG_ID).orEmpty(),
                displayName = applicationContext.getString(R.string.local_model_notification_generic_title),
                status = LocalModelStatus.QUEUED,
                bytesDownloaded = 0L,
                bytesTotal = 0L,
                progressPercent = 0,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
                currentFile = "",
                lastError = "",
            )
        )
    }

    private fun createForegroundInfo(snapshot: LocalInstallProgressSnapshot): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            snapshot.catalogId.hashCode(),
            Intent(applicationContext, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val isDeterminate = snapshot.progressPercent > 0 || snapshot.bytesTotal > 0L
        val contentText = buildNotificationText(snapshot)
        val notification = NotificationCompat.Builder(applicationContext, LOCAL_MODEL_DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(snapshot.displayName)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(snapshot.status in setOf(LocalModelStatus.QUEUED, LocalModelStatus.DOWNLOADING, LocalModelStatus.VERIFYING, LocalModelStatus.INSTALLING))
            .setProgress(100, snapshot.progressPercent.coerceIn(0, 100), !isDeterminate)
            .addAction(0, applicationContext.getString(R.string.cancel), cancelIntent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                LOCAL_MODEL_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(LOCAL_MODEL_NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotificationText(snapshot: LocalInstallProgressSnapshot): String {
        val progress = when {
            snapshot.progressPercent > 0 -> "${snapshot.progressPercent}%"
            snapshot.status == LocalModelStatus.QUEUED -> applicationContext.getString(R.string.local_model_status_queued)
            else -> applicationContext.getString(R.string.local_model_status_working)
        }
        val fileSuffix = snapshot.currentFile.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
        return "$progress$fileSuffix"
    }

    private fun showTerminalNotification(title: String, body: String) {
        if (!canPostNotifications()) {
            return
        }
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, LOCAL_MODEL_DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(LOCAL_MODEL_NOTIFICATION_ID, notification)
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationChannel() {
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                LOCAL_MODEL_DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW,
            )
                .setName(applicationContext.getString(R.string.notification_channel_local_models))
                .setVibrationEnabled(false)
                .build()
        )
    }

    companion object {
        const val KEY_ACTION = "local_model_action"
        const val KEY_CATALOG_ID = "local_model_catalog_id"
        const val KEY_SOURCE_URI = "local_model_source_uri"
        const val ACTION_DOWNLOAD = "download"
        const val ACTION_IMPORT = "import"
    }
}
