package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.db.entity.EmbeddingCacheEntity
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.shouldPruneEpisode
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

class MemoryConsolidationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {
    companion object {
        private const val TAG = "MemoryConsolidation"
    }

    private val conversationRepository: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val chatEpisodeDAO: ChatEpisodeDAO by inject()
    private val embeddingCacheDAO: EmbeddingCacheDAO by inject()
    private val settingsStore: SettingsStore by inject()
    private val embeddingService: EmbeddingService by inject()
    private val providerManager: me.rerere.ai.provider.ProviderManager by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            consolidateMemories()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error consolidating memories", e)
            Result.retry()
        }
    }

    private suspend fun consolidateMemories() {
        val settings = settingsStore.settingsFlow.value
        val forceConversationId = inputData.getString(MEMORY_CONSOLIDATION_KEY_FORCE_CONVERSATION_ID)
        val forcedConversation = forceConversationId
            ?.let { conversationId -> runCatching { conversationRepository.getConversationById(Uuid.parse(conversationId)) }.getOrNull() }
        val assistantId = resolveConsolidationAssistantId(
            explicitAssistantId = inputData.getString(MEMORY_CONSOLIDATION_KEY_ASSISTANT_ID),
            fallbackAssistantId = forcedConversation?.assistantId?.toString() ?: settings.assistantId.toString(),
        )
        val assistantUuid = runCatching { Uuid.parse(assistantId) }.getOrNull() ?: return
        val assistant = settings.getAssistantById(assistantUuid) ?: return
        if (forcedConversation != null && forcedConversation.assistantId != assistant.id) {
            Log.w(
                TAG,
                "Skipping forced consolidation for ${forcedConversation.id} because assistant ${assistant.id} does not own it",
            )
            return
        }
        if (!assistant.enableMemory) return

        val summarizerModelId = settings.summarizerModelId
        val backgroundModelId = summarizerModelId ?: assistant.backgroundModelId ?: settings.chatModelId
        val model = settings.findModelById(backgroundModelId) ?: return
        val provider = model.findProvider(settings.providers) ?: return
        val providerHandler = providerManager.getProviderByType(provider)
        val isFullScan = inputData.getBoolean(MEMORY_CONSOLIDATION_KEY_FULL_SCAN, false)

        var trackACount = 0
        val now = System.currentTimeMillis()

        if (assistant.enableMemoryConsolidation || forceConversationId != null) {
            val conversationsToProcess = when {
                forcedConversation != null -> listOf(forcedConversation)
                isFullScan -> conversationRepository.getConversationsOfAssistant(assistant.id).first()
                else -> conversationRepository.getRecentConversations(assistant.id, 10)
            }

            for (conversation in conversationsToProcess) {
                if (conversation.messageNodes.size < 4) continue
                if (conversation.isConsolidated && !isFullScan && forceConversationId == null) continue

                val delayMs = assistant.consolidationDelayMinutes * 60 * 1000L
                if (forceConversationId == null && now - conversation.updateAt.toEpochMilli() < delayMs && !isFullScan) {
                    Log.i(TAG, "Skipping conversation ${conversation.id} (waiting for delay)")
                    continue
                }

                if (isFullScan) {
                    val existingEpisodes = memoryRepository.getEpisodeEntitiesOfAssistant(assistantId)
                    val isProcessed = existingEpisodes.any {
                        kotlin.math.abs(it.endTime - conversation.updateAt.toEpochMilli()) < 1000 * 60
                    }
                    if (isProcessed) {
                        conversationRepository.markAsConsolidated(conversation.id)
                        continue
                    }
                }

                val allMessages = conversation.currentMessages
                val lastSummaryIndex = conversation.contextSummaryUpToIndex
                val hasSummary = !conversation.contextSummary.isNullOrBlank() && lastSummaryIndex >= 0
                val messagesToProcess = if (hasSummary && lastSummaryIndex < allMessages.size) {
                    allMessages.subList((lastSummaryIndex + 1).coerceAtMost(allMessages.size), allMessages.size)
                } else {
                    allMessages
                }.takeLast(30)
                val messagesText = messagesToProcess.joinToString("\n") { "${it.role}: ${it.toText()}" }
                val contextSection = if (hasSummary) {
                    """
                    **Context Summary (from previous summarization):**
                    ${conversation.contextSummary}

                    **New Messages (${messagesToProcess.size} since last summary):**
                    """.trimIndent()
                } else ""

                val prompt = """
                    Analyze the following conversation and create a "Memory Episode".

                    $contextSection
                    1. **Summary**: Concise summary of what happened (under 100 words).
                    2. **Significance**: Rate the emotional impact or importance of this conversation from 1-10 (10 = life-changing, 1 = trivial).

                    Conversation:
                    $messagesText

                    Output JSON format:
                    {
                        "summary": "...",
                        "significance": 5
                    }
                """.trimIndent()

                try {
                    val response = providerHandler.generateText(
                        providerSetting = provider,
                        messages = listOf(UIMessage.user(prompt)),
                        params = TextGenerationParams(model = model, temperature = 0.5f)
                    )
                    val responseText = response.choices.firstOrNull()?.message?.toContentText() ?: continue

                    var summary = responseText
                    var significance = 5
                    try {
                        val jsonStart = responseText.indexOf("{")
                        val jsonEnd = responseText.lastIndexOf("}")
                        if (jsonStart != -1 && jsonEnd != -1) {
                            val jsonStr = responseText.substring(jsonStart, jsonEnd + 1)
                            val json = Json.parseToJsonElement(jsonStr).jsonObject
                            summary = json["summary"]?.jsonPrimitive?.content ?: summary
                            significance = json["significance"]?.jsonPrimitive?.intOrNull ?: 5
                        }
                    } catch (_: Exception) {
                    }

                    val summaryEmbeddingResult = embeddingService.embedWithModelId(summary, assistantId)
                    val summaryEmbedding = summaryEmbeddingResult.embeddings.firstOrNull() ?: continue
                    val embeddingModelId = summaryEmbeddingResult.modelId
                    val embeddingJson = JsonInstant.encodeToString(summaryEmbedding)
                    val existingEpisode = chatEpisodeDAO.getEpisodeByConversationId(conversation.id.toString())

                    val episodeId = if (existingEpisode != null) {
                        chatEpisodeDAO.insertEpisode(
                            existingEpisode.copy(
                                content = summary,
                                embedding = embeddingJson,
                                embeddingModelId = embeddingModelId,
                                endTime = conversation.updateAt.toEpochMilli(),
                                lastAccessedAt = System.currentTimeMillis(),
                                significance = significance,
                            )
                        )
                        Log.i(TAG, "Updated episode (sig=$significance) for conversation ${conversation.id}")
                    } else {
                        chatEpisodeDAO.insertEpisode(
                            ChatEpisodeEntity(
                                assistantId = assistantId,
                                content = summary,
                                embedding = embeddingJson,
                                embeddingModelId = embeddingModelId,
                                startTime = conversation.createAt.toEpochMilli(),
                                endTime = conversation.updateAt.toEpochMilli(),
                                lastAccessedAt = System.currentTimeMillis(),
                                significance = significance,
                                conversationId = conversation.id.toString(),
                            )
                        )
                        Log.i(TAG, "Created episode (sig=$significance) for conversation ${conversation.id}")
                    }
                    embeddingCacheDAO.insertEmbedding(
                        EmbeddingCacheEntity(
                            memoryId = episodeId.toInt(),
                            memoryType = MemoryType.EPISODIC,
                            modelId = embeddingModelId,
                            embedding = embeddingJson,
                        )
                    )

                    conversationRepository.markAsConsolidated(conversation.id)
                    trackACount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process conversation ${conversation.id}", e)
                }
            }

            if (trackACount > 0 || isFullScan) {
                val resultMsg = if (trackACount > 0) "Processed $trackACount chats" else "No new chats ready"
                settingsStore.update { currentSettings ->
                    currentSettings.copy(
                        assistants = currentSettings.assistants.map {
                            if (it.id == assistant.id) {
                                it.copy(
                                    lastConsolidationTime = now,
                                    lastConsolidationResult = resultMsg,
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
            }
        }

        val allEpisodes = memoryRepository.getEpisodeEntitiesOfAssistant(assistantId)
        var prunedCount = 0
        for (episode in allEpisodes) {
            if (shouldPruneEpisode(episode = episode, nowMillis = now)) {
                memoryRepository.deleteEpisode(episode.id)
                prunedCount++
            }
        }
        if (prunedCount > 0) {
            Log.i(TAG, "Pruned $prunedCount fading episodic memories")
        }

        try {
            val (fixed, failed) = memoryRepository.embedMissingMemories(assistantId)
            if (fixed > 0 || failed > 0) {
                Log.i(TAG, "Auto-embedded $fixed memories ($failed failed)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-embedding memories", e)
        }
    }
}
