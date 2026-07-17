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
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting

class MemoryConsolidationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val conversationRepository: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    private val chatEpisodeDAO: ChatEpisodeDAO by inject()
    private val settingsStore: SettingsStore by inject()
    private val embeddingService: EmbeddingService by inject()
    private val providerManager: me.rerere.ai.provider.ProviderManager by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            consolidateMemories()
            Result.success()
        } catch (e: Exception) {
            Log.e("MemoryConsolidation", "Error consolidating memories", e)
            Result.retry()
        }
    }

    private suspend fun consolidateMemories() {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        if (!assistant.enableMemory) return
        val summarizerModelId = settings.summarizerModelId
        val backgroundModelId = summarizerModelId ?: assistant.backgroundModelId ?: settings.chatModelId
        val model = settings.findModelById(backgroundModelId) ?: return
        val provider = model.findProvider(settings.providers) ?: return
        val providerHandler = providerManager.getProviderByType(provider)
        val assistantId = settings.assistantId.toString()

        // =========================================================================================
        // TRACK A: Episodic Memory Creation (Stream of Consciousness)
        // Only runs if enableMemoryConsolidation is true
        // =========================================================================================
        val isFullScan = inputData.getBoolean("FULL_SCAN", false)
        val forceConversationId = inputData.getString("FORCE_CONVERSATION_ID")
        
        var trackACount = 0
        val now = System.currentTimeMillis()
        
        // Only process conversations if consolidation is enabled
        if (assistant.enableMemoryConsolidation || forceConversationId != null) {
            val conversationsToProcess = if (forceConversationId != null) {
                // Manual consolidation: only process the specific conversation
                val targetConversation = conversationRepository.getConversationById(kotlin.uuid.Uuid.parse(forceConversationId))
                if (targetConversation != null) listOf(targetConversation) else emptyList()
            } else if (isFullScan) {
                conversationRepository.getConversationsOfAssistant(settings.assistantId).first()
            } else {
                conversationRepository.getRecentConversations(settings.assistantId, 10)
            }
            
            for (conversation in conversationsToProcess) {
            // Skip short conversations
            if (conversation.messageNodes.size < 4) continue
            
            // Check if already consolidated (unless forced or full scan)
            if (conversation.isConsolidated && !isFullScan && forceConversationId == null) continue
            
            // CHECK DELAY: Only consolidate if enough time has passed since last update
            // (Skip delay check for forced/manual consolidation)
            val delayMs = assistant.consolidationDelayMinutes * 60 * 1000L
            if (forceConversationId == null && now - conversation.updateAt.toEpochMilli() < delayMs && !isFullScan) {
                Log.i("MemoryConsolidation", "Skipping conversation ${conversation.id} (waiting for delay)")
                continue
            }
            
            // On full scan, skip conversations that already have an episode linked by conversationId.
            // We use the exact conversationId match instead of a time-based heuristic, which
            // could produce false positives when two conversations have nearby timestamps.
            if (isFullScan) {
                val existingEpisode = chatEpisodeDAO.getEpisodeByConversationId(conversation.id.toString())
                if (existingEpisode != null) {
                    conversationRepository.markAsConsolidated(conversation.id)
                    continue
                }
            }

            // Summarize into an episode with Significance Score
            // Only process messages after the last summary index to avoid redundant processing
            val allMessages = conversation.currentMessages
            val lastSummaryIndex = conversation.contextSummaryUpToIndex
            val hasSummary = !conversation.contextSummary.isNullOrBlank() && lastSummaryIndex >= 0
            
            val messagesToProcess = if (hasSummary && lastSummaryIndex < allMessages.size) {
                allMessages.subList((lastSummaryIndex + 1).coerceAtMost(allMessages.size), allMessages.size)
            } else {
                allMessages
            }.takeLast(30) // Limit to last 30 for processing
            
            val messagesText = messagesToProcess.joinToString("\n") { "${it.role}: ${it.toText()}" }
            
            // Include context summary if available for better context
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
                    params = settings.buildSummarizerGenerationParams(
                        model = model,
                        temperature = 0.5f,
                    )
                )
                val responseText = response.choices.firstOrNull()?.message?.toContentText() ?: continue
                
                var summary = responseText
                var significance = 5
                
                // Try to parse JSON
                try {
                    val jsonStart = responseText.indexOf("{")
                    val jsonEnd = responseText.lastIndexOf("}")
                    if (jsonStart != -1 && jsonEnd != -1) {
                        val jsonStr = responseText.substring(jsonStart, jsonEnd + 1)
                        val json = Json.parseToJsonElement(jsonStr).jsonObject
                        summary = json["summary"]?.jsonPrimitive?.content ?: summary
                        significance = json["significance"]?.jsonPrimitive?.intOrNull ?: 5
                    }
                } catch (e: Exception) {
                    // Fallback: use raw text as summary if JSON parsing fails
                }
                
                // Generate embedding for the episode
                val summaryEmbeddingResult = embeddingService.embedWithModelId(summary, assistantId)
                val summaryEmbedding = summaryEmbeddingResult.embeddings.firstOrNull()
                val embeddingModelId = summaryEmbeddingResult.modelId
                
                if (summaryEmbedding != null) {
                    // Check if an episode already exists for this conversation
                    val existingEpisode = chatEpisodeDAO.getEpisodeByConversationId(conversation.id.toString())
                    
                    if (existingEpisode != null) {
                        // Update existing episode
                        chatEpisodeDAO.insertEpisode(
                            existingEpisode.copy(
                                content = summary,
                                embedding = JsonInstant.encodeToString(summaryEmbedding),
                                embeddingModelId = embeddingModelId,
                                endTime = conversation.updateAt.toEpochMilli(),
                                lastAccessedAt = System.currentTimeMillis(),
                                significance = significance
                            )
                        )
                        Log.i("MemoryConsolidation", "Updated episode (sig=$significance) for conversation ${conversation.id}")
                    } else {
                        // Create new episode
                        chatEpisodeDAO.insertEpisode(
                            ChatEpisodeEntity(
                                assistantId = assistantId,
                                content = summary,
                                embedding = JsonInstant.encodeToString(summaryEmbedding),
                                embeddingModelId = embeddingModelId,
                                startTime = conversation.createAt.toEpochMilli(),
                                endTime = conversation.updateAt.toEpochMilli(),
                                lastAccessedAt = System.currentTimeMillis(),
                                significance = significance,
                                conversationId = conversation.id.toString()
                            )
                        )
                        Log.i("MemoryConsolidation", "Created episode (sig=$significance) for conversation ${conversation.id}")
                    }
                    
                    conversationRepository.markAsConsolidated(conversation.id)
                    trackACount++
                }
            } catch (e: Exception) {
                Log.e("MemoryConsolidation", "Failed to process conversation ${conversation.id}", e)
            }
        }
        
        // Update Track A Stats
        if (trackACount > 0 || isFullScan) {
            val resultMsg = if (trackACount > 0) "Processed $trackACount chats" else "No new chats ready"
            settingsStore.update { currentSettings ->
                currentSettings.copy(
                    assistants = currentSettings.assistants.map { 
                        if (it.id == settings.assistantId) {
                            it.copy(
                                lastConsolidationTime = now,
                                lastConsolidationResult = resultMsg
                            )
                        } else it
                    }
                )
            }
            }
        } // End of enableMemoryConsolidation check

        // =========================================================================================
        // PRUNING: The "Throw Out" Mechanism
        // =========================================================================================
        val allEpisodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        
        var prunedCount = 0
        for (episode in allEpisodes) {
            val age = now - episode.startTime
            val timeSinceAccess = now - episode.lastAccessedAt
            
            // Default 30 days retention
            val retentionDays = 30L
            
            val retentionMs = retentionDays * 24 * 60 * 60 * 1000L
            
            // If older than retention period AND not accessed recently (7 days buffer)
            if (age > retentionMs && timeSinceAccess > (7L * 24 * 60 * 60 * 1000L)) {
                chatEpisodeDAO.deleteEpisode(episode.id)
                prunedCount++
            }
        }
        if (prunedCount > 0) {
            Log.i("MemoryConsolidation", "Pruned $prunedCount fading episodic memories")
        }

        // =========================================================================================
        // AUTO-FIX: Embed any memories that are missing embeddings or have wrong model
        // =========================================================================================
        try {
            val (fixed, failed) = memoryRepository.embedMissingMemories(assistantId)
            if (fixed > 0 || failed > 0) {
                Log.i("MemoryConsolidation", "Auto-embedded $fixed memories ($failed failed)")
            }
        } catch (e: Exception) {
            Log.e("MemoryConsolidation", "Error auto-embedding memories", e)
        }
    }
}
