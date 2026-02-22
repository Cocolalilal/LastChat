package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.db.entity.GraphEpisodeEntity
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.GraphMemoryRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.ai.memory.MemoryAgent
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
    private val graphMemoryRepo: GraphMemoryRepository by inject()
    private val settingsStore: SettingsStore by inject()
    private val embeddingService: EmbeddingService by inject()
    private val providerManager: me.rerere.ai.provider.ProviderManager by inject()
    private val memoryAgent: MemoryAgent by inject()

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
        val summarizerModelId = assistant.summarizerModelId
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
        
        // Only process conversations if consolidation is enabled AND graph memory is active
        // Episodic memory creation is exclusive to Advanced (graph) memory mode
        if ((assistant.enableMemoryConsolidation || forceConversationId != null) && assistant.useGraphMemory) {
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
            
            // Double check with graph episode DAO if we are doing a full scan (heuristic fallback)
            if (isFullScan) {
                val existingEpisodes = graphMemoryRepo.getRecentEpisodes(assistantId, 500)
                val isProcessed = existingEpisodes.any { 
                    kotlin.math.abs(it.endTime - conversation.updateAt.toEpochMilli()) < 1000 * 60 
                }
                if (isProcessed) {
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
            
            // Compute significance via heuristic (avoids unreliable LLM ratings)
            val significance = computeEpisodeSignificance(messagesToProcess)
            
            val prompt = """
                Summarize this conversation in under 100 words. Focus on key facts, decisions, and emotions.
                
                $contextSection
                Conversation:
                $messagesText
                
                Output ONLY the summary text, no JSON or formatting.
            """.trimIndent()
            
            try {
                val response = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = TextGenerationParams(model = model, temperature = 0.5f)
                )
                val summary = response.choices.firstOrNull()?.message?.toContentText() ?: continue
                
                // Generate embedding for the episode
                val summaryEmbeddingResult = embeddingService.embedWithModelId(summary, assistantId)
                val summaryEmbedding = summaryEmbeddingResult.embeddings.firstOrNull()
                val embeddingModelId = summaryEmbeddingResult.modelId
                
                if (summaryEmbedding != null) {
                    // Create graph episode (replaces legacy ChatEpisodeEntity)
                    graphMemoryRepo.insertEpisode(
                        GraphEpisodeEntity(
                            assistantId = assistantId,
                            conversationId = conversation.id.toString(),
                            content = summary,
                            significance = significance,
                            startTime = conversation.createAt.toEpochMilli(),
                            endTime = conversation.updateAt.toEpochMilli(),
                            embedding = JsonInstant.encodeToString(summaryEmbedding),
                            embeddingModelId = embeddingModelId,
                        )
                    )
                    Log.i("MemoryConsolidation", "Created graph episode (sig=$significance) for conversation ${conversation.id}")
                    
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

        // =========================================================================================
        // GRAPH MEMORY CONSOLIDATION (when advanced graph memory is enabled)
        // =========================================================================================
        if (assistant.useGraphMemory) {
            try {
                val memoryAgent: me.rerere.rikkahub.data.ai.memory.MemoryAgent by inject()
                memoryAgent.runConsolidation(
                    assistantId = assistantId,
                    decayHalfLifeDays = assistant.graphDecayRateDays.toDouble(),
                    maxNodes = assistant.graphMaxNodes,
                    timelineEnabled = assistant.graphTimelineEnabled,
                )
                Log.i("MemoryConsolidation", "Graph memory consolidation complete")
            } catch (e: Exception) {
                Log.e("MemoryConsolidation", "Error running graph consolidation", e)
            }
        }
    }

    /**
     * Compute episode significance (1-10) using a heuristic instead of asking the LLM.
     * Analyzes message count, question density, emotional markers, and presence of
     * names/dates/places to produce a consistent score.
     */
    private fun computeEpisodeSignificance(messages: List<me.rerere.ai.ui.UIMessage>): Int {
        var score = 3 // Baseline for any conversation worth consolidating

        val allText = messages.joinToString(" ") { it.toText() }.lowercase()
        val messageCount = messages.size

        // More messages = more substantial conversation
        if (messageCount >= 20) score += 2
        else if (messageCount >= 10) score += 1

        // Questions indicate exploration / seeking info
        val questionCount = allText.count { it == '?' }
        if (questionCount >= 5) score += 1

        // Emotional language markers
        val emotionalWords = listOf(
            "love", "hate", "excited", "scared", "angry", "happy", "sad", "afraid",
            "worried", "thrilled", "devastated", "amazing", "terrible", "awesome",
            "anxious", "proud", "grateful", "lonely", "heartbroken", "furious",
            "died", "death", "born", "married", "divorced", "pregnant", "fired",
            "hired", "promoted", "graduated", "diagnosed"
        )
        val emotionalHits = emotionalWords.count { allText.contains(it) }
        if (emotionalHits >= 3) score += 2
        else if (emotionalHits >= 1) score += 1

        // Life event markers (high significance)
        val lifeEventWords = listOf(
            "new job", "got fired", "moving to", "break up", "broke up",
            "engaged", "wedding", "baby", "surgery", "accident", "hospital",
            "university", "college", "degree", "retirement"
        )
        if (lifeEventWords.any { allText.contains(it) }) score += 2

        // Exclamation marks indicate intensity
        val exclamationCount = allText.count { it == '!' }
        if (exclamationCount >= 5) score += 1

        return score.coerceIn(1, 10)
    }
}
