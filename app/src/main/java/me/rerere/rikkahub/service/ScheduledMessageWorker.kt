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

    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val providerManager: me.rerere.ai.provider.ProviderManager by inject()

    override suspend fun doWork(): Result {
        return try {
            val assistantIdStr = inputData.getString("assistantId") ?: return Result.failure()
            val conversationIdStr = inputData.getString("conversationId") ?: return Result.failure()
            val reason = inputData.getString("reason") ?: "No reason provided"

            me.rerere.common.android.Logging.log("ScheduledMessageWorker", "Processing scheduled message: $reason")

            val assistantId = Uuid.parse(assistantIdStr)
            val conversationId = Uuid.parse(conversationIdStr)

            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.getAssistantById(assistantId) ?: return Result.failure()
            val conversation = conversationRepository.getConversationById(conversationId) ?: return Result.failure()

            // Prepare context
            val history = conversation.currentMessages.takeLast(10).joinToString("\n") { "${it.role}: ${it.toText()}" }
            
            // RAG Retrieval
            val lastUserMessage = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText() ?: ""
            val memories = if (lastUserMessage.isNotBlank()) {
                memoryRepository.retrieveRelevantMemories(
                    assistantId = assistant.id.toString(),
                    query = lastUserMessage,
                    limit = 5
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
            val model = settings.findModelById(modelId) ?: return Result.failure()
            val provider = model.findProvider(settings.providers) ?: return Result.failure()
            val providerHandler = providerManager.getProviderByType(provider)

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.7f,
                    thinkingBudget = 0
                )
            )

            val content = result.choices.firstOrNull()?.message?.toText()?.trim() ?: return Result.failure()

            sendNotification(assistant.name, content, conversationId)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun sendNotification(title: String, content: String, conversationId: Uuid) {
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
            .setSmallIcon(R.drawable.small_icon)
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
        }
    }
}
