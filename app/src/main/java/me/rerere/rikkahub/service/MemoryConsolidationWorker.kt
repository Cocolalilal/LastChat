package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
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
        val backgroundModelId = settings.getCurrentAssistant().backgroundModelId ?: settings.chatModelId
        val model = settings.findModelById(backgroundModelId) ?: return
        val provider = model.findProvider(settings.providers) ?: return
        val providerHandler = providerManager.getProviderByType(provider)
        val assistantId = settings.assistantId.toString()

        // =========================================================================================
        // TRACK A: Episodic Memory Creation (Stream of Consciousness)
        // =========================================================================================
        // Convert recent conversations into "Episodes"
        val recentConversations = conversationRepository.getRecentConversations(settings.assistantId, 3)
        
        for (conversation in recentConversations) {
            // Skip short conversations
            if (conversation.messageNodes.size < 4) continue
            
            // Check if this conversation has already been consolidated into an episode
            // Heuristic: Check if any episode ends at the same time as this conversation (approx)
            val existingEpisodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
            val isProcessed = existingEpisodes.any { 
                kotlin.math.abs(it.endTime - conversation.updateAt.toEpochMilli()) < 1000 * 60 // 1 minute tolerance
            }
            
            if (!isProcessed) {
                // Summarize into an episode
                val messagesText = conversation.currentMessages.takeLast(30).joinToString("\n") { "${it.role}: ${it.toText()}" }
                val prompt = """
                    Summarize the following conversation chunk into a concise "Memory Episode".
                    Focus on what happened, what was discussed, and how the user felt.
                    Keep it under 100 words.
                    
                    Conversation:
                    $messagesText
                """.trimIndent()
                
                try {
                    val response = providerHandler.generateText(
                        providerSetting = provider,
                        messages = listOf(UIMessage.user(prompt)),
                        params = TextGenerationParams(model = model, temperature = 0.5f)
                    )
                    val summary = response.choices.firstOrNull()?.message?.toText() ?: continue
                    
                    // Generate embedding for the episode
                    val summaryEmbedding = embeddingService.embed(summary, assistantId)
                    
                    if (summaryEmbedding != null) {
                        chatEpisodeDAO.insertEpisode(
                            ChatEpisodeEntity(
                                assistantId = assistantId,
                                content = summary,
                                embedding = JsonInstant.encodeToString(summaryEmbedding),
                                startTime = conversation.createAt.toEpochMilli(),
                                endTime = conversation.updateAt.toEpochMilli(),
                                lastAccessedAt = System.currentTimeMillis()
                            )
                        )
                        Log.i("MemoryConsolidation", "Created new episode for conversation ${conversation.id}")
                    }
                } catch (e: Exception) {
                    Log.e("MemoryConsolidation", "Failed to process conversation ${conversation.id}", e)
                }
            }
        }

        // =========================================================================================
        // TRACK B: Core Memory Extraction (The "Facts")
        // =========================================================================================
        // Review recent episodes and extract permanent facts
        val episodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId).take(10) // Look at last 10 episodes
        if (episodes.isNotEmpty()) {
            val episodesText = episodes.joinToString("\n") { "- ${it.content}" }
            val prompt = """
                Analyze the following episodic memories and extract any permanent, important facts about the user (e.g., name, preferences, location, relationships, specific constraints).
                Return the facts as a bulleted list.
                If no new important facts are found, return "NONE".
                Do NOT include temporary states (e.g., "User is eating a burger").
                
                Episodes:
                $episodesText
            """.trimIndent()

            try {
                val response = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = TextGenerationParams(model = model, temperature = 0.3f)
                )
                val factsText = response.choices.firstOrNull()?.message?.toText() ?: return
                
                if (factsText != "NONE" && factsText.isNotBlank()) {
                    val facts = factsText.split("\n").map { it.trim().removePrefix("- ").trim() }.filter { it.isNotBlank() }
                    
                    for (fact in facts) {
                        // Check for duplicates in Core Memory
                        val existingMemories = memoryRepository.getMemoriesOfAssistant(assistantId)
                        val isDuplicate = existingMemories.any { existing ->
                            // Simple fuzzy match
                            val words = fact.lowercase().split(" ").filter { it.length > 4 }
                            words.isNotEmpty() && words.all { existing.content.lowercase().contains(it) }
                        }
                        
                        if (!isDuplicate) {
                            memoryRepository.addMemory(assistantId, fact) // Defaults to CORE type
                            Log.i("MemoryConsolidation", "Extracted new core memory: $fact")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoryConsolidation", "Failed to extract core memories", e)
            }
        }

        // =========================================================================================
        // PRUNING: The "Throw Out" Mechanism
        // =========================================================================================
        // Delete episodic memories that are old AND haven't been recalled recently
        val allEpisodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        
        var prunedCount = 0
        for (episode in allEpisodes) {
            val age = now - episode.startTime
            val timeSinceAccess = now - episode.lastAccessedAt
            
            // If older than 30 days AND not accessed in last 7 days -> Fading -> Delete
            if (age > thirtyDaysMs && timeSinceAccess > sevenDaysMs) {
                chatEpisodeDAO.deleteEpisode(episode.id)
                prunedCount++
            }
        }
        if (prunedCount > 0) {
            Log.i("MemoryConsolidation", "Pruned $prunedCount fading episodic memories")
        }
    }
}
