package me.rerere.rikkahub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.repository.StudioHubItem
import me.rerere.rikkahub.data.repository.StudioHubRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

internal object StudioReminderWorkSpec {
    const val KEY_ITEM_ID = "studio_item_id"
    const val CHANNEL_ID = "studio_reminders"
    const val UNIQUE_WORK_PREFIX = "studio_reminder"

    fun uniqueWorkName(itemId: String): String = "$UNIQUE_WORK_PREFIX:$itemId"
}

internal interface StudioReminderScheduler {
    fun schedule(item: StudioHubItem)
    fun cancel(itemId: String)
}

internal class WorkManagerStudioReminderScheduler(
    private val context: Context,
) : StudioReminderScheduler {
    override fun schedule(item: StudioHubItem) {
        val reminderAt = item.reminderAtMillis
        if (reminderAt == null || item.isCompleted || item.isArchived || item.reminderTriggered) {
            cancel(item.id)
            return
        }
        val workManager = context.workManagerOrNull() ?: return
        val delayMs = (reminderAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<StudioReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(StudioReminderWorkSpec.KEY_ITEM_ID to item.id))
            .build()
        workManager.enqueueUniqueWork(
            StudioReminderWorkSpec.uniqueWorkName(item.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(itemId: String) {
        val workManager = context.workManagerOrNull() ?: return
        workManager.cancelUniqueWork(StudioReminderWorkSpec.uniqueWorkName(itemId))
    }
}

class StudioReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val studioHubRepository: StudioHubRepository by inject()

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(StudioReminderWorkSpec.KEY_ITEM_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure()

        val item = studioHubRepository.getItem(itemId)
            ?: return Result.success()

        if (item.isCompleted || item.isArchived) {
            return Result.success()
        }

        val title = item.title.ifBlank { "Studio Reminder" }
        val body = item.body.takeIf { it.isNotBlank() }
            ?: item.briefSummary.takeIf { it.isNotBlank() }
            ?: if (item.checklist.isNotEmpty()) {
                val done = item.checklist.count { it.isChecked }
                "$done/${item.checklist.size} checklist items completed"
            } else {
                "Tap to open in Studio Hub"
            }

        postNotification(itemId = item.id, title = title, content = body)
        studioHubRepository.markReminderTriggered(item.id)
        return Result.success()
    }

    private fun postNotification(itemId: String, title: String, content: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            StudioReminderWorkSpec.CHANNEL_ID,
            "Studio Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RouteActivity.EXTRA_OPEN_STUDIO, true)
            putExtra(RouteActivity.EXTRA_STUDIO_ITEM_ID, itemId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(applicationContext, StudioReminderWorkSpec.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(itemId.hashCode(), notification)
        }
    }
}
