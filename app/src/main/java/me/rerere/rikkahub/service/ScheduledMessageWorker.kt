package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

class ScheduledMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {
    companion object {
        private const val TAG = "ScheduledMessageWorker"
    }

    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val providerManager: me.rerere.ai.provider.ProviderManager by inject()

    override suspend fun doWork(): Result {
        return try {
            val assistantIdStr = inputData.getString(ScheduledMessageWorkSpec.KEY_ASSISTANT_ID)
                ?: return Result.failure()
            val conversationIdStr = inputData.getString(ScheduledMessageWorkSpec.KEY_CONVERSATION_ID)
                ?: return Result.failure()
            val reason = inputData.getString(ScheduledMessageWorkSpec.KEY_REASON) ?: "No reason provided"
            val createdAtMillis = inputData.getLong(ScheduledMessageWorkSpec.KEY_CREATED_AT, 0L)
            val scheduledAtMillis = inputData.getLong(ScheduledMessageWorkSpec.KEY_SCHEDULED_AT, 0L)

            val assistantId = runCatching { Uuid.parse(assistantIdStr) }.getOrElse {
                me.rerere.common.android.Logging.log(TAG, "Invalid assistantId in scheduled work: $assistantIdStr")
                return Result.failure()
            }
            val conversationId = runCatching { Uuid.parse(conversationIdStr) }.getOrElse {
                me.rerere.common.android.Logging.log(TAG, "Invalid conversationId in scheduled work: $conversationIdStr")
                return Result.failure()
            }

            val latenessMillis = if (scheduledAtMillis > 0L) {
                (System.currentTimeMillis() - scheduledAtMillis).coerceAtLeast(0L)
            } else {
                0L
            }
            me.rerere.common.android.Logging.log(
                TAG,
                "Processing scheduled message attempt=${runAttemptCount + 1} createdAt=$createdAtMillis scheduledAt=$scheduledAtMillis latenessMs=$latenessMillis reason=$reason"
            )

            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.getAssistantById(assistantId) ?: return Result.failure()
            val conversation = conversationRepository.getConversationById(conversationId) ?: return Result.failure()

            // Prepare context
            val history = conversation.currentMessages.takeLast(10).joinToString("\n") { "${it.role}: ${it.toText()}" }
            
            // RAG Retrieval
            val lastUserMessage = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText() ?: ""
            val memories = if (lastUserMessage.isNotBlank()) {
                val searchLimit = if (assistant.ragLimit > 50) 9999 else assistant.ragLimit
                memoryRepository.retrieveRelevantMemories(
                    assistantId = assistant.id.toString(),
                    query = lastUserMessage,
                    limit = searchLimit
                )
            } else {
                emptyList()
            }
            val memoryContext = memories.joinToString("\n") { "- ${it.content}" }

            val prompt = """
                You are ${assistant.name}.
                You scheduled a message to be sent to the user now.
                
                Reason for scheduling: "$reason"
                
                Recent Chat History:
                $history
                
                Relevant Memories:
                $memoryContext
                
                Generate the content of the notification message now.
                - Be natural and conversational.
                - Directly address the reason.
                - Keep it concise (under 2 sentences if possible) as it is a notification.
                - Do NOT include quotes or prefixes like "Notification:". Just the content.
            """.trimIndent()

            val modelId = assistant.chatModelId ?: settings.chatModelId
            val model = settings.findModelById(modelId)
            if (model == null) {
                me.rerere.common.android.Logging.log(TAG, "Retrying scheduled message because model is unavailable for assistant ${assistant.id}")
                return Result.retry()
            }
            val provider = model.findProvider(settings.providers)
            if (provider == null) {
                me.rerere.common.android.Logging.log(TAG, "Retrying scheduled message because provider is unavailable for model ${model.modelId}")
                return Result.retry()
            }
            val providerHandler = runCatching { providerManager.getProviderByType(provider) }
                .getOrElse {
                    me.rerere.common.android.Logging.log(TAG, "Retrying scheduled message because provider handler lookup failed: ${it.message}")
                    return Result.retry()
                }

            val content = try {
                val result = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = TextGenerationParams(
                        model = model,
                        temperature = 0.7f,
                        thinkingBudget = 0
                    )
                )
                result.choices.firstOrNull()?.message?.toContentText()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: run {
                        me.rerere.common.android.Logging.log(TAG, "Retrying scheduled message because generated content was blank")
                        return Result.retry()
                    }
            } catch (e: Exception) {
                me.rerere.common.android.Logging.log(TAG, "Retrying scheduled message after generation failure: ${e.message}")
                return Result.retry()
            }

            if (!sendNotification(assistant.name, content, conversationId)) {
                if (runAttemptCount + 1 < ScheduledMessageWorkSpec.MAX_NOTIFICATION_PERMISSION_RETRIES) {
                    me.rerere.common.android.Logging.log(
                        TAG,
                        "Notification could not be posted, retrying scheduled message (${runAttemptCount + 1}/${ScheduledMessageWorkSpec.MAX_NOTIFICATION_PERMISSION_RETRIES})"
                    )
                    return Result.retry()
                }
                me.rerere.common.android.Logging.log(
                    TAG,
                    "Dropping scheduled message after notification-post retries; permission or channel access is unavailable"
                )
                return Result.success()
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            me.rerere.common.android.Logging.log(TAG, "Retrying scheduled message after unexpected failure: ${e.message}")
            Result.retry()
        }
    }

    private fun sendNotification(title: String, content: String, conversationId: Uuid): Boolean {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "assistant_scheduled"
        val channel = android.app.NotificationChannel(
            channelId,
            "Scheduled Messages",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            return true
        }
        me.rerere.common.android.Logging.log(TAG, "POST_NOTIFICATIONS permission missing when posting scheduled message")
        return false
    }
}
