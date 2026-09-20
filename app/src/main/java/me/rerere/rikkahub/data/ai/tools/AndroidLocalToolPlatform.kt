package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.service.AssistantNotificationListener
import me.rerere.rikkahub.service.ScheduledMessageWorkSpec
import me.rerere.rikkahub.utils.createImageFileFromBase64
import me.rerere.rikkahub.utils.getImagesDir
import java.time.Instant
import java.io.File
import java.security.MessageDigest
import kotlin.uuid.Uuid

data class SavedGeneratedToolImage(
    val uri: String,
    val path: String,
    val markdownImage: String,
)

interface GeneratedToolImageSaver {
    suspend fun save(
        item: ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
    ): SavedGeneratedToolImage
}

data class ScheduledLocalToolMessage(
    val status: String,
    val scheduledAt: String? = null,
    val workName: String? = null,
)

data class LocalToolNotificationSnapshot(
    val packageName: String,
    val title: String,
    val content: String,
    val postTime: Long,
)

interface LocalToolNotificationPlatform {
    fun sendNotification(
        conversationId: Uuid,
        title: String,
        content: String,
    ): String

    fun scheduleMessage(
        assistantId: Uuid,
        conversationId: Uuid,
        reason: String,
        delayMinutes: Long,
    ): ScheduledLocalToolMessage

    fun getRecentNotifications(limit: Int): List<LocalToolNotificationSnapshot>
}

internal object AndroidLocalToolPlatform {
    fun pathExists(path: String): Boolean {
        return File(path).exists()
    }

    fun shortStableHash(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(8)
    }
}

class AndroidLocalToolNotificationPlatform(
    private val context: Context,
    private val taskScheduler: me.rerere.ai.generation.PortableTaskScheduler =
        me.rerere.rikkahub.service.WorkManagerPortableTaskScheduler(context),
) : LocalToolNotificationPlatform {
    override fun sendNotification(
        conversationId: Uuid,
        title: String,
        content: String,
    ): String {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "assistant_notification"
        val channel = NotificationChannel(
            channelId,
            "Assistant Notification",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            "success"
        } else {
            "error: permission denied"
        }
    }

    override fun scheduleMessage(
        assistantId: Uuid,
        conversationId: Uuid,
        reason: String,
        delayMinutes: Long,
    ): ScheduledLocalToolMessage {
        return try {
            val createdAt = System.currentTimeMillis()
            val scheduledAt = createdAt + (delayMinutes * 60 * 1000)
            val uniqueWorkName = ScheduledMessageWorkSpec.buildUniqueWorkName(
                assistantId = assistantId.toString(),
                conversationId = conversationId.toString(),
                reason = reason,
                scheduledAtMillis = scheduledAt,
            )
            taskScheduler.enqueue(
                me.rerere.ai.generation.PortableTaskRequest(
                    task = me.rerere.ai.generation.PortableBackgroundTask.SCHEDULED_MESSAGES,
                    uniqueName = uniqueWorkName,
                    delayMs = delayMinutes * 60_000L,
                    extras = mapOf(
                        ScheduledMessageWorkSpec.KEY_ASSISTANT_ID to assistantId.toString(),
                        ScheduledMessageWorkSpec.KEY_CONVERSATION_ID to conversationId.toString(),
                        ScheduledMessageWorkSpec.KEY_REASON to reason,
                        ScheduledMessageWorkSpec.KEY_CREATED_AT to createdAt.toString(),
                        ScheduledMessageWorkSpec.KEY_SCHEDULED_AT to scheduledAt.toString(),
                    ),
                ),
            )

            ScheduledLocalToolMessage(
                status = "success",
                scheduledAt = Instant.ofEpochMilli(scheduledAt).toString(),
                workName = uniqueWorkName,
            )
        } catch (e: Exception) {
            ScheduledLocalToolMessage(status = "error: ${e.message}")
        }
    }

    override fun getRecentNotifications(limit: Int): List<LocalToolNotificationSnapshot> {
        return AssistantNotificationListener.notifications.value.take(limit).map { notification ->
            LocalToolNotificationSnapshot(
                packageName = notification.packageName,
                title = notification.title,
                content = notification.content,
                postTime = notification.postTime,
            )
        }
    }
}

class AndroidGeneratedToolImageSaver(
    private val context: Context,
    private val genMediaRepository: GenMediaRepository,
) : GeneratedToolImageSaver {
    override suspend fun save(
        item: ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
    ): SavedGeneratedToolImage = withContext(Dispatchers.IO) {
        val imagesDir = context.getImagesDir()
        val timestamp = System.currentTimeMillis()
        val safeModelName = modelName.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(48).ifBlank { "image" }
        val imageFile = File(imagesDir, "${timestamp}_${safeModelName}_tool_$index.png")
        context.createImageFileFromBase64(item.data, imageFile.absolutePath)
        genMediaRepository.insertMedia(
            GenMediaEntity(
                path = "images/${imageFile.name}",
                modelId = modelName,
                prompt = prompt,
                createAt = timestamp,
            )
        )
        val uri = "file://${imageFile.absolutePath}"
        SavedGeneratedToolImage(
            uri = uri,
            path = imageFile.absolutePath,
            markdownImage = "![Generated image]($uri)",
        )
    }
}
