package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.toByteArray
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

/**
 * Automatic Core + Episodic memory maintenance.
 *
 * Each conversation is a unique WorkManager scope. The worker resolves the owning assistant from
 * the persisted conversation instead of the currently selected character, so switching chats
 * cannot leak or misattribute memories. Re-enqueueing the same conversation debounces short chats
 * and cancels a stale in-flight snapshot when a new reply arrives.
 */
class MemoryConsolidationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val conversationRepository: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val chatEpisodeDAO: ChatEpisodeDAO by inject()
    private val settingsStore: SettingsStore by inject()
    private val embeddingService: EmbeddingService by inject()
    private val providerManager: ProviderManager by inject()
    private val database: AppDatabase by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val conversationId = inputData.getString(KEY_CONVERSATION_ID)
        if (conversationId == null) {
            return@withContext runCatching {
                enqueuePendingConversations()
                Result.success()
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                PlatformLog.w(TAG, "Unable to queue memory catch-up: ${throwable.message}")
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }
        }

        val parsedId = runCatching { Uuid.parse(conversationId) }.getOrNull()
            ?: return@withContext Result.failure()
        runCatching { consolidateConversation(parsedId) }
            .fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        ConsolidationOutcome.COMPLETE,
                        ConsolidationOutcome.NOT_APPLICABLE,
                        -> Result.success()
                        ConsolidationOutcome.DEFERRED,
                        ConsolidationOutcome.STALE,
                        -> Result.retry()
                    }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    PlatformLog.w(
                        TAG,
                        "Unable to consolidate conversation $conversationId: ${throwable.message}",
                    )
                    if (runAttemptCount < MAX_RETRIES) {
                        Result.retry()
                    } else {
                        recordConsolidationFailure(parsedId, throwable)
                        Result.failure()
                    }
                },
            )
    }

    private suspend fun enqueuePendingConversations() {
        val settings = settingsStore.settingsFlow.value
        settings.assistants
            .asSequence()
            .filter { it.enableMemory && it.enableMemoryConsolidation }
            .forEach { assistant ->
                val repairResult = try {
                    memoryRepository.embedMissingMemories(assistant.id.toString())
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    PlatformLog.w(
                        TAG,
                        "Background embedding repair deferred for ${assistant.id}: ${throwable.message}",
                    )
                    null
                }
                val failedEmbeddings = repairResult?.second ?: 0
                if (failedEmbeddings > 0) {
                    PlatformLog.w(TAG, "Background embedding repair left $failedEmbeddings memories pending")
                }
                conversationRepository
                    .getPendingMemoryConversations(assistant.id, RECONCILE_BATCH_SIZE)
                    .forEach { conversation ->
                        enqueueForConversation(
                            context = applicationContext,
                            conversation = conversation,
                            consolidationDelayMinutes = assistant.consolidationDelayMinutes,
                        )
                    }
            }
    }

    private suspend fun consolidateConversation(conversationId: Uuid): ConsolidationOutcome {
        val conversation = conversationRepository.getConversationById(conversationId)
            ?: return ConsolidationOutcome.NOT_APPLICABLE
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getAssistantById(conversation.assistantId)
            ?: return ConsolidationOutcome.NOT_APPLICABLE
        if (!assistant.enableMemory || !assistant.enableMemoryConsolidation) {
            return ConsolidationOutcome.NOT_APPLICABLE
        }

        val meaningfulMessages = conversation.meaningfulMemoryMessages()
        val decision = decideMemoryConsolidation(
            meaningfulMessageCount = meaningfulMessages.size,
            idleMillis = System.currentTimeMillis() - conversation.updateAt.toEpochMilli(),
            configuredDelayMinutes = assistant.consolidationDelayMinutes,
        )
        when (decision) {
            MemoryConsolidationDecision.NotWorthwhile -> {
                return if (markReviewedIfUnchanged(conversation)) {
                    ConsolidationOutcome.COMPLETE
                } else {
                    ConsolidationOutcome.STALE
                }
            }

            is MemoryConsolidationDecision.Schedule -> {
                if (decision.delayMillis > 0L) {
                    return ConsolidationOutcome.DEFERRED
                }
            }
        }

        val backgroundModelId =
            settings.summarizerModelId ?: assistant.backgroundModelId ?: settings.chatModelId
        val model = settings.findModelById(backgroundModelId)
            ?: error("No model is available for automatic memory consolidation")
        val provider = model.findProvider(settings.providers)
            ?: error("No provider is available for automatic memory consolidation")
        val providerHandler = providerManager.getProviderByType(provider)

        val allMessages = conversation.currentMessages
        val lastSummaryIndex = conversation.contextSummaryUpToIndex
        val hasSummary = !conversation.contextSummary.isNullOrBlank() && lastSummaryIndex >= 0
        val messagesToProcess = if (hasSummary && lastSummaryIndex < allMessages.size) {
            allMessages.subList(
                (lastSummaryIndex + 1).coerceAtMost(allMessages.size),
                allMessages.size,
            )
        } else {
            allMessages
        }.filter { message ->
            (message.role == MessageRole.USER || message.role == MessageRole.ASSISTANT) &&
                message.toText().isNotBlank()
        }.takeLast(MAX_MESSAGES_PER_EPISODE)

        val contextSection = if (hasSummary) {
            """
            **Context Summary (from earlier messages):**
            ${conversation.contextSummary}

            **New messages:**
            """.trimIndent()
        } else {
            ""
        }
        val messagesText = messagesToProcess.joinToString("\n") { message ->
            "${message.role}: ${message.toText()}"
        }
        val prompt = """
            Analyze this conversation and create one episodic memory.

            $contextSection
            1. **Summary**: Concisely describe what happened in under 100 words.
            2. **Significance**: Rate its emotional impact or long-term importance from 1 to 10.

            Conversation:
            $messagesText

            Output JSON only:
            {
              "summary": "...",
              "significance": 5
            }
        """.trimIndent()

        val response = providerHandler.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.user(prompt)),
            params = settings.buildSummarizerGenerationParams(
                model = model,
                temperature = 0.5f,
            ),
        )
        val responseText = response.choices.firstOrNull()?.message?.toContentText()
            ?.takeIf { it.isNotBlank() }
            ?: error("Memory consolidation returned an empty response")
        val parsed = parseEpisodeResponse(responseText)
        val embeddingResult = try {
            embeddingService.embedWithModelId(
                text = parsed.summary,
                assistantId = assistant.id.toString(),
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            PlatformLog.w(
                TAG,
                "Saving episodic memory without a vector; lexical retrieval remains active: ${throwable.message}",
            )
            null
        }
        val embeddingBlob = embeddingResult?.embeddings
            ?.map { it.toFloatArray() }
            ?.toByteArray()

        // Generation and embedding may take long enough for another reply to land. Never commit a
        // stale snapshot or mark it complete; the replacement/retry job will consolidate the new
        // version instead.
        val now = System.currentTimeMillis()
        val committed = database.withTransaction {
            val latestEntity = database.conversationDao()
                .getConversationById(conversation.id.toString())
                ?: return@withTransaction false
            val latestConversation = conversationRepository
                .conversationEntityToConversation(latestEntity)
            if (!latestConversation.sameMemorySnapshotAs(conversation)) {
                return@withTransaction false
            }

            val existingEpisode =
                chatEpisodeDAO.getEpisodeByConversationId(conversation.id.toString())
            val episode = if (existingEpisode == null) {
                ChatEpisodeEntity(
                    assistantId = assistant.id.toString(),
                    content = parsed.summary,
                    embedding = null,
                    embeddingBlob = embeddingBlob,
                    embeddingModelId = embeddingResult?.modelId,
                    startTime = conversation.createAt.toEpochMilli(),
                    endTime = conversation.updateAt.toEpochMilli(),
                    lastAccessedAt = now,
                    significance = parsed.significance,
                    conversationId = conversation.id.toString(),
                )
            } else {
                existingEpisode.copy(
                    assistantId = assistant.id.toString(),
                    content = parsed.summary,
                    embedding = null,
                    embeddingBlob = embeddingBlob,
                    embeddingModelId = embeddingResult?.modelId,
                    endTime = conversation.updateAt.toEpochMilli(),
                    lastAccessedAt = now,
                    significance = parsed.significance,
                )
            }
            chatEpisodeDAO.insertEpisode(episode)
            database.conversationDao().updateConsolidatedStatus(
                conversation.id.toString(),
                isConsolidated = true,
            )
            true
        }
        if (!committed) return ConsolidationOutcome.STALE

        val (_, failedRepairs) = memoryRepository.embedMissingMemories(assistant.id.toString())
        val embeddingHealthy = embeddingResult != null && failedRepairs == 0
        updateAssistantStats(assistant.id, now, embeddingHealthy)
        pruneOldEpisodes(assistant.id.toString(), now)
        PlatformLog.i(TAG, "Consolidated ${conversation.id} for assistant ${assistant.id}")
        return ConsolidationOutcome.COMPLETE
    }

    private suspend fun markReviewedIfUnchanged(conversation: Conversation): Boolean {
        return database.withTransaction {
            val latestEntity = database.conversationDao()
                .getConversationById(conversation.id.toString())
                ?: return@withTransaction true
            val latest = conversationRepository.conversationEntityToConversation(latestEntity)
            if (!latest.sameMemorySnapshotAs(conversation)) return@withTransaction false
            database.conversationDao().updateConsolidatedStatus(
                conversation.id.toString(),
                isConsolidated = true,
            )
            true
        }
    }

    private suspend fun updateAssistantStats(assistantId: Uuid, now: Long, embeddingHealthy: Boolean) {
        settingsStore.update { current ->
            current.copy(
                assistants = current.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            lastConsolidationTime = now,
                            lastConsolidationResult = if (embeddingHealthy) {
                                "Automatic consolidation complete"
                            } else {
                                "Automatic consolidation complete; vector embeddings unavailable, lexical retrieval active"
                            },
                        )
                    } else {
                        assistant
                    }
                },
            )
        }
    }

    private suspend fun recordConsolidationFailure(conversationId: Uuid, throwable: Throwable) {
        val conversation = conversationRepository.getConversationById(conversationId) ?: return
        val safeDetail = throwable.message
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(180)
            ?: throwable::class.simpleName
            ?: "unknown error"
        settingsStore.update { current ->
            current.copy(
                assistants = current.assistants.map { assistant ->
                    if (assistant.id == conversation.assistantId) {
                        assistant.copy(
                            lastConsolidationTime = System.currentTimeMillis(),
                            lastConsolidationResult = "Automatic consolidation could not complete: $safeDetail",
                        )
                    } else {
                        assistant
                    }
                },
            )
        }
    }

    private suspend fun pruneOldEpisodes(assistantId: String, now: Long) {
        val retentionMillis = EPISODE_RETENTION_DAYS * 24L * 60L * 60L * 1_000L
        val recentAccessBufferMillis = 7L * 24L * 60L * 60L * 1_000L
        chatEpisodeDAO.getEpisodesOfAssistant(assistantId).forEach { episode ->
            if (
                now - episode.startTime > retentionMillis &&
                now - episode.lastAccessedAt > recentAccessBufferMillis
            ) {
                memoryRepository.deleteEpisode(episode.id)
            }
        }
    }

    private fun parseEpisodeResponse(responseText: String): EpisodeResponse {
        val jsonStart = responseText.indexOf('{')
        val jsonEnd = responseText.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            return EpisodeResponse(responseText.trim(), DEFAULT_SIGNIFICANCE)
        }
        return runCatching {
            val json = Json.parseToJsonElement(
                responseText.substring(jsonStart, jsonEnd + 1),
            ).jsonObject
            EpisodeResponse(
                summary = json["summary"]?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() }
                    ?: responseText.trim(),
                significance = json["significance"]?.jsonPrimitive?.intOrNull
                    ?.coerceIn(1, 10)
                    ?: DEFAULT_SIGNIFICANCE,
            )
        }.getOrElse {
            EpisodeResponse(responseText.trim(), DEFAULT_SIGNIFICANCE)
        }
    }

    private data class EpisodeResponse(
        val summary: String,
        val significance: Int,
    )

    private enum class ConsolidationOutcome {
        COMPLETE,
        DEFERRED,
        NOT_APPLICABLE,
        STALE,
    }

    companion object {
        private const val TAG = "MemoryConsolidation"
        private const val KEY_CONVERSATION_ID = "CONVERSATION_ID"
        private const val CONVERSATION_WORK_PREFIX = "memory_consolidation_conversation_"
        private const val CATCH_UP_WORK_NAME = "memory_consolidation_catch_up"
        private const val RECONCILE_BATCH_SIZE = 100
        private const val MAX_MESSAGES_PER_EPISODE = 30
        private const val EPISODE_RETENTION_DAYS = 30
        private const val DEFAULT_SIGNIFICANCE = 5
        private const val MAX_RETRIES = 3

        fun enqueueForConversation(
            context: Context,
            conversation: Conversation,
            consolidationDelayMinutes: Int,
        ) {
            val decision = decideMemoryConsolidation(
                meaningfulMessageCount = conversation.meaningfulMemoryMessages().size,
                idleMillis = System.currentTimeMillis() - conversation.updateAt.toEpochMilli(),
                configuredDelayMinutes = consolidationDelayMinutes,
            )
            val delayMillis = (decision as? MemoryConsolidationDecision.Schedule)
                ?.delayMillis
                ?: 0L
            val request = OneTimeWorkRequestBuilder<MemoryConsolidationWorker>()
                .setInputData(workDataOf(KEY_CONVERSATION_ID to conversation.id.toString()))
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                CONVERSATION_WORK_PREFIX + conversation.id,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueueCatchUp(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                CATCH_UP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MemoryConsolidationWorker>().build(),
            )
        }
    }
}

private fun Conversation.meaningfulMemoryMessages(): List<UIMessage> =
    currentMessages.filter { message ->
        (message.role == MessageRole.USER || message.role == MessageRole.ASSISTANT) &&
            message.toText().isNotBlank()
    }

private fun Conversation.sameMemorySnapshotAs(other: Conversation): Boolean =
    updateAt == other.updateAt &&
        currentMessages.map { it.id } == other.currentMessages.map { it.id }
