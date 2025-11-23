package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.utils.applyPlaceholders
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

class SpontaneousWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val providerManager: me.rerere.ai.provider.ProviderManager by inject()

    override suspend fun doWork(): Result {
        return try {
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.getCurrentAssistant()
            
            // Check if enabled
            if (!assistant.enableSpontaneous) return Result.success()
            
            // Check time window
            val currentHour = java.time.LocalTime.now().hour
            if (currentHour < assistant.notificationStartHour || currentHour >= assistant.notificationEndHour) {
                return Result.success() // Outside notification hours
            }
            
            // Check frequency
            val timeSinceLastNotification = System.currentTimeMillis() - assistant.lastNotificationTime
            val minIntervalMs = assistant.notificationFrequencyHours * 60 * 60 * 1000L
            if (timeSinceLastNotification < minIntervalMs) {
                return Result.success() // Too soon since last notification
            }

            // Get latest conversation
            val conversations = conversationRepository.getRecentConversations(assistant.id, 1)
            val conversation = conversations.firstOrNull() ?: return Result.success()

            // Don't spam: check if last message was recent (e.g., within 24 hours) but not TOO recent (e.g., > 30 mins)
            // This logic can be refined. For now, let's just run.

            val modelId = assistant.backgroundModelId ?: assistant.chatModelId ?: settings.chatModelId
            val model = settings.findModelById(modelId) 
                ?: return Result.success()
            val provider = model.findProvider(settings.providers) ?: return Result.success()
            val providerHandler = providerManager.getProviderByType(provider)

            val oneDayMs = 24 * 60 * 60 * 1000L
            val lastNotificationInfo = if (
                assistant.lastNotificationContent.isNotBlank() && 
                (System.currentTimeMillis() - assistant.lastNotificationTime < oneDayMs)
            ) {
                "\n\nYou last sent a notification: \"${assistant.lastNotificationContent}\". Don't repeat yourself or be redundant."
            } else ""

            // RAG Retrieval
            val lastUserMessage = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText() ?: "User status"
            val memories = memoryRepository.retrieveRelevantMemories(
                assistantId = assistant.id.toString(),
                query = lastUserMessage,
                limit = 5
            )
            val memoryContext = memories.joinToString("\n") { "- ${it.content}" }
            
            val customPrompt = assistant.spontaneousPrompt.ifBlank {
                """
                You are ${assistant.name}. You are running in the background to check in on the user. Be casual and friendly.
                
                Recent chat history:
                {{history}}
                
                Relevant Memories:
                {{memories}}
                $lastNotificationInfo
                
                Do you want to send a spontaneous notification to the user right now?
                Consider the context. Only send if:
                - It's genuinely helpful or relevant
                - You have a good reason (explain it in the "reason" field)
                - It's not repetitive or annoying
                
                Output JSON format:
                {
                    "send": true/false,
                    "reason": "Why you want to send this notification",
                    "title": "Notification Title",
                    "content": "Notification Content"
                }
                """.trimIndent()
            }

            val history = conversation.currentMessages.takeLast(5).joinToString("\n") { "${it.role}: ${it.toText()}" }
            val prompt = customPrompt
                .replace("{{history}}", history)
                .replace("{{memories}}", memoryContext)

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.7f,
                    thinkingBudget = 0
                )
            )

            val responseText = result.choices.firstOrNull()?.message?.toText() ?: return Result.failure()
            
            // Parse JSON (simple parsing, assuming model follows instruction)
            try {
                val jsonStart = responseText.indexOf("{")
                val jsonEnd = responseText.lastIndexOf("}")
                if (jsonStart != -1 && jsonEnd != -1) {
                    val jsonStr = responseText.substring(jsonStart, jsonEnd + 1)
                    val json = Json.parseToJsonElement(jsonStr).jsonObject
                    
                    val send = json["send"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (send) {
                        val title = json["title"]?.jsonPrimitive?.contentOrNull ?: assistant.name
                        val content = json["content"]?.jsonPrimitive?.contentOrNull ?: ""
                        val reason = json["reason"]?.jsonPrimitive?.contentOrNull ?: "No reason provided"
                        
                        if (content.isNotBlank()) {
                            sendNotification(title, content)
                            
                            // Update assistant's last notification info (Full Reset)
                            val updatedAssistant = assistant.copy(
                                lastNotificationTime = System.currentTimeMillis(),
                                lastNotificationContent = content
                            )
                            val updatedSettings = settings.copy(
                                assistants = settings.assistants.map {
                                    if (it.id == assistant.id) updatedAssistant else it
                                }
                            )
                            settingsStore.update(updatedSettings)
                        }
                    } else {
                        // AI declined to send. Apply "Half Delay" logic.
                        // We set the last time to (Now - Interval/2), so we only wait half the interval from now.
                        val halfIntervalMs = minIntervalMs / 2
                        val updatedAssistant = assistant.copy(
                            lastNotificationTime = System.currentTimeMillis() - halfIntervalMs
                        )
                        val updatedSettings = settings.copy(
                            assistants = settings.assistants.map {
                                if (it.id == assistant.id) updatedAssistant else it
                            }
                        )
                        settingsStore.update(updatedSettings)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun sendNotification(title: String, content: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "assistant_spontaneous"
        val channel = android.app.NotificationChannel(
            channelId,
            "Assistant Updates",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
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
