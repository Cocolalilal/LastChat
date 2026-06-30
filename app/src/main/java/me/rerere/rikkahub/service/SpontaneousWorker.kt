package me.rerere.rikkahub.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.SpontaneousMessagingStateStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.SpontaneousMessageMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalTime
import kotlin.random.Random
import kotlin.uuid.Uuid

private const val TAG = "SpontaneousWorker"

private data class EligibleAssistantContext(
    val assistant: Assistant,
    val conversation: Conversation?,
)

class SpontaneousWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val providerManager: me.rerere.ai.provider.ProviderManager by inject()
    private val spontaneousStateStore: SpontaneousMessagingStateStore by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = try {
            settingsStore.settingsFlow.first { !it.init }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load hydrated settings for spontaneous messaging", e)
            return@withContext Result.retry()
        }

        runCatching {
            process(settings)
            Result.success()
        }.getOrElse { error ->
            Log.e(TAG, "Spontaneous worker failed", error)
            Result.success()
        }
    }

    private suspend fun process(settings: Settings) {
        if (!canPostNotifications()) return

        val now = System.currentTimeMillis()
        if (now < spontaneousStateStore.globalQuietUntil) {
            Log.d(TAG, "Global quiet period active, skipping spontaneous run")
            return
        }

        val currentHour = LocalTime.now().hour
        val eligibleAssistants = settings.assistants
            .filter { it.enableSpontaneous }
            .mapNotNull { assistant ->
                if (!SpontaneousMessaging.isWithinActiveHours(
                        currentHour = currentHour,
                        startHour = assistant.notificationStartHour,
                        endHour = assistant.notificationEndHour,
                    )
                ) {
                    return@mapNotNull null
                }

                val minIntervalMillis = assistant.notificationFrequencyHours.coerceAtLeast(1) * 60 * 60 * 1000L
                if (assistant.lastNotificationTime > 0L && now - assistant.lastNotificationTime < minIntervalMillis) {
                    return@mapNotNull null
                }

                val modelId = assistant.backgroundModelId ?: assistant.chatModelId ?: settings.chatModelId
                val model = settings.findModelById(modelId) ?: return@mapNotNull null
                if (model.findProvider(settings.providers) == null) {
                    return@mapNotNull null
                }

                val recentConversation = conversationRepository.getRecentConversations(assistant.id, 1).firstOrNull()

                // CONTINUE_ONLY requires an existing conversation
                if (assistant.spontaneousMessageMode == SpontaneousMessageMode.CONTINUE_ONLY && recentConversation == null) {
                    return@mapNotNull null
                }

                EligibleAssistantContext(
                    assistant = assistant,
                    conversation = recentConversation,
                )
            }

        val random = Random(now)
        val selectedCandidate = SpontaneousMessaging.pickCandidate(
            candidates = eligibleAssistants.map { context ->
                SpontaneousCandidate(
                    assistantId = context.assistant.id,
                    lastNotificationTime = context.assistant.lastNotificationTime,
                )
            },
            lastSenderAssistantId = spontaneousStateStore.lastSenderAssistantId,
            random = random,
        ) ?: return

        val selectedContext = eligibleAssistants.firstOrNull { context ->
            context.assistant.id == selectedCandidate.assistantId
        } ?: return

        handleCandidate(settings, selectedContext, now, random)
    }

    private suspend fun handleCandidate(
        settings: Settings,
        context: EligibleAssistantContext,
        now: Long,
        random: Random,
    ) {
        val assistant = context.assistant
        val conversation = context.conversation
        val modelId = assistant.backgroundModelId ?: assistant.chatModelId ?: settings.chatModelId
        val model = settings.findModelById(modelId) ?: return
        val provider = model.findProvider(settings.providers) ?: return
        val providerHandler = providerManager.getProviderByType(provider)

        val history = conversation?.currentMessages
            ?.takeLast(10)
            ?.joinToString("\n") { message -> "${message.role}: ${message.toText()}" }
            ?.takeIf { it.isNotBlank() }
            ?: "No previous chat history."
        val timingContext = buildTimingContext(conversation, now)

        val memories = resolveMemoryContext(assistant, conversation)
        val lastNotificationContext = assistant.lastNotificationContent
            .takeIf { it.isNotBlank() && now - assistant.lastNotificationTime < 24 * 60 * 60 * 1000L }
            ?.let { content ->
                "Your last spontaneous notification was ${SpontaneousMessaging.describeElapsedTime(now - assistant.lastNotificationTime)} ago: \"$content\". Avoid repeating the same angle or wording."
            }
            ?: ""

        val prompt = assistant.spontaneousPrompt.ifBlank {
            buildDefaultPrompt(
                assistant = assistant,
                conversation = conversation,
                history = history,
                timingContext = timingContext,
                memories = memories,
                lastNotificationContext = lastNotificationContext,
            )
        }

        val responseText = runCatching {
            providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                    temperature = assistant.temperature ?: 0.7f,
                    thinkingBudget = 0,
                ),
            ).choices.firstOrNull()?.message?.toContentText()
        }.getOrElse { error ->
            Log.w(TAG, "Spontaneous generation failed for ${assistant.id}", error)
            return
        } ?: return

        val response = SpontaneousMessaging.parseResponse(responseText) ?: return
        if (!response.shouldSend) {
            applyHalfDelay(assistant, now)
            return
        }

        val relation = response.relation ?: return
        val effectiveRelation = when (assistant.spontaneousMessageMode) {
            SpontaneousMessageMode.CONTINUE_ONLY -> SpontaneousMessageRelation.RECENT_CHAT
            SpontaneousMessageMode.NEW_ONLY -> SpontaneousMessageRelation.UNRELATED
            SpontaneousMessageMode.BOTH -> if (conversation == null) {
                SpontaneousMessageRelation.UNRELATED
            } else {
                relation
            }
        }

        val content = response.content?.trim().orEmpty()
        if (content.isBlank()) return

        val eventId = Uuid.random().toString()

        sendNotification(
            assistantId = assistant.id,
            conversationId = if (effectiveRelation == SpontaneousMessageRelation.RECENT_CHAT) {
                conversation?.id
            } else {
                null
            },
            eventId = eventId,
            title = response.title?.takeIf { it.isNotBlank() } ?: assistant.name.ifBlank { applicationContext.getString(R.string.app_name) },
            content = content,
            relation = effectiveRelation,
        )

        settingsStore.update { currentSettings ->
            currentSettings.copy(
                assistants = currentSettings.assistants.map { existing ->
                    if (existing.id == assistant.id) {
                        existing.copy(
                            lastNotificationTime = now,
                            lastNotificationContent = content,
                        )
                    } else {
                        existing
                    }
                }
            )
        }
        spontaneousStateStore.updateDeliveryState(
            globalQuietUntil = SpontaneousMessaging.computeGlobalQuietUntil(now, random),
            assistantId = assistant.id,
        )
    }

    private suspend fun resolveMemoryContext(
        assistant: Assistant,
        conversation: Conversation?,
    ): String {
        val assistantId = assistant.id.toString()
        val retrievedMemories = if (conversation != null) {
            val lastUserMessage = conversation.currentMessages
                .lastOrNull { it.role == MessageRole.USER }
                ?.toText()
                .orEmpty()
            if (lastUserMessage.isNotBlank()) {
                val searchLimit = if (assistant.ragLimit > 50) 9999 else assistant.ragLimit
                memoryRepository.retrieveRelevantMemories(
                    assistantId = assistantId,
                    query = lastUserMessage,
                    limit = searchLimit,
                )
            } else {
                val limit = (if (assistant.ragLimit > 50) 9999 else assistant.ragLimit).coerceAtMost(100)
                memoryRepository.getMemoryEntitiesOfAssistantLimited(assistantId, limit)
                    .map { AssistantMemory(it.id, it.content, it.type, it.embedding != null, it.embeddingModelId, it.createdAt) }
            }
        } else {
            val limit = (if (assistant.ragLimit > 50) 9999 else assistant.ragLimit).coerceAtMost(100)
            memoryRepository.getMemoryEntitiesOfAssistantLimited(assistantId, limit)
                .map { AssistantMemory(it.id, it.content, it.type, it.embedding != null, it.embeddingModelId, it.createdAt) }
        }

        val episodicMemories = if (conversation == null && retrievedMemories.size < 5) {
            memoryRepository.getEpisodeEntitiesOfAssistant(assistantId)
                .take(5 - retrievedMemories.size)
                .map { episode ->
                    me.rerere.rikkahub.data.model.AssistantMemory(
                        id = -episode.id,
                        content = episode.content,
                        type = 1,
                        hasEmbedding = episode.embedding != null,
                        embeddingModelId = episode.embeddingModelId,
                        timestamp = episode.startTime,
                        significance = episode.significance,
                    )
                }
        } else {
            emptyList()
        }

        return (retrievedMemories + episodicMemories)
            .joinToString("\n") { memory -> "- ${memory.content}" }
            .ifBlank { "No relevant memories." }
    }

    private fun buildDefaultPrompt(
        assistant: Assistant,
        conversation: Conversation?,
        history: String,
        timingContext: String,
        memories: String,
        lastNotificationContext: String,
    ): String {
        val contactInstructions = if (conversation == null) {
            """
            There is no existing chat history yet. You may initiate first contact, but only if it feels warm, low-pressure, and genuinely worth saying.
            """.trimIndent()
        } else {
            """
            There is a most recent chat available. Treat the decision carefully:
            - choose `recent_chat` only if reopening that exact thread feels natural at this timing
            - choose `unrelated` only if a fresh standalone ping feels more honest than continuing the old thread
            - choose `send = false` if neither option feels justified right now
            """.trimIndent()
        }

        return """
            You are ${assistant.name}.
            You are considering whether to send the user a spontaneous in-app message.

            $contactInstructions

            Recent chat history:
            $history

            Timing signals:
            $timingContext

            Relevant memories:
            $memories

            $lastNotificationContext

            Decide whether to send a spontaneous message right now.
            Only send if it feels timely, affectionate, interesting, or meaningfully connected to prior context.
            Avoid repetitive check-ins, filler, or anything that would feel spammy.
            Use the timing signals and who spoke last when deciding.
            If the user spoke very recently, if they are still obviously waiting for a normal in-chat reply, or if the timing feels awkward, prefer `send = false`.
            Choose `recent_chat` only if the message clearly continues the most recent chat.
            Choose `unrelated` only if it should stand alone as a fresh new conversation rather than continue the most recent chat.
            ${buildRelationConstraint(assistant, conversation)}

            Return JSON only:
            {
              "send": true or false,
              "reason": "brief internal reason",
              "relation": "recent_chat" or "unrelated",
              "title": "short notification title",
              "content": "the exact spontaneous assistant message"
            }
        """.trimIndent()
    }

    private fun buildRelationConstraint(
        assistant: Assistant,
        conversation: Conversation?,
    ): String {
        return when (assistant.spontaneousMessageMode) {
            SpontaneousMessageMode.CONTINUE_ONLY -> "You MUST use relation=`recent_chat`. Your message must continue or reference the existing chat."
            SpontaneousMessageMode.NEW_ONLY -> "You MUST use relation=`unrelated`. Your message must start a fresh, standalone conversation."
            SpontaneousMessageMode.BOTH -> if (conversation == null) {
                "Because there is no recent chat available, relation must be `unrelated`."
            } else {
                ""
            }
        }
    }

    private fun buildTimingContext(
        conversation: Conversation?,
        now: Long,
    ): String {
        if (conversation == null) {
            return "- No previous chat timing is available."
        }

        val messages = conversation.currentMessages
        val lastMessage = messages.lastOrNull()
        val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
        val lastAssistantMessage = messages.lastOrNull { it.role == MessageRole.ASSISTANT }

        fun elapsedSince(message: UIMessage?): String {
            if (message == null) return "no message yet"
            val createdAtMillis = message.createdAt
                .toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            return SpontaneousMessaging.describeElapsedTime(now - createdAtMillis)
        }

        val lastSpeaker = when (lastMessage?.role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
            MessageRole.TOOL -> "tool"
            null -> "unknown"
        }

        return """
            - Last speaker in the most recent chat: $lastSpeaker
            - Time since any message in that chat: ${elapsedSince(lastMessage)}
            - Time since the user's last message: ${elapsedSince(lastUserMessage)}
            - Time since your last message: ${elapsedSince(lastAssistantMessage)}
        """.trimIndent()
    }

    private suspend fun applyHalfDelay(assistant: Assistant, now: Long) {
        val halfIntervalMillis = assistant.notificationFrequencyHours.coerceAtLeast(1) * 60 * 60 * 1000L / 2
        settingsStore.update { currentSettings ->
            currentSettings.copy(
                assistants = currentSettings.assistants.map { existing ->
                    if (existing.id == assistant.id) {
                        existing.copy(lastNotificationTime = now - halfIntervalMillis)
                    } else {
                        existing
                    }
                }
            )
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ActivityCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendNotification(
        assistantId: Uuid,
        conversationId: Uuid?,
        eventId: String,
        title: String,
        content: String,
        relation: SpontaneousMessageRelation,
    ) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                SPONTANEOUS_NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_spontaneous),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )

        val intent = Intent(applicationContext, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_IS_SPONTANEOUS_NOTIFICATION, true)
            putExtra("assistantId", assistantId.toString())
            conversationId?.let { putExtra("conversationId", it.toString()) }
            putExtra(EXTRA_SPONTANEOUS_EVENT_ID, eventId)
            putExtra(EXTRA_SPONTANEOUS_MESSAGE, content)
            putExtra(EXTRA_SPONTANEOUS_RELATION, relation.wireValue)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(applicationContext, SPONTANEOUS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(eventId.hashCode(), notification)
    }
}
