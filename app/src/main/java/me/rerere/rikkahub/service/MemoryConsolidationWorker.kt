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
        val summarizerModelId = assistant.summarizerModelId
        val backgroundModelId = summarizerModelId ?: assistant.backgroundModelId ?: settings.chatModelId
        val model = settings.findModelById(backgroundModelId) ?: return
        val provider = model.findProvider(settings.providers) ?: return
        val providerHandler = providerManager.getProviderByType(provider)
        val assistantId = settings.assistantId.toString()

        // =========================================================================================
        // TRACK A: Episodic Memory Creation (Stream of Consciousness)
        // =========================================================================================
        // Convert recent conversations into "Episodes"
        val isFullScan = inputData.getBoolean("FULL_SCAN", false)
        val conversationsToProcess = if (isFullScan) {
            conversationRepository.getConversationsOfAssistant(settings.assistantId).first()
        } else {
            conversationRepository.getRecentConversations(settings.assistantId, 10)
        }
        
        var trackACount = 0
        val now = System.currentTimeMillis()
        
        for (conversation in conversationsToProcess) {
            // Skip short conversations
            if (conversation.messageNodes.size < 4) continue
            
            // Check if already consolidated
            if (conversation.isConsolidated && !isFullScan) continue
            
            // CHECK DELAY: Only consolidate if enough time has passed since last update
            val delayMs = assistant.consolidationDelayMinutes * 60 * 1000L
            if (now - conversation.updateAt.toEpochMilli() < delayMs && !isFullScan) {
                Log.i("MemoryConsolidation", "Skipping conversation ${conversation.id} (waiting for delay)")
                continue
            }
            
            // Double check with DAO if we are doing a full scan (heuristic fallback)
            if (isFullScan) {
                val existingEpisodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
                val isProcessed = existingEpisodes.any { 
                    kotlin.math.abs(it.endTime - conversation.updateAt.toEpochMilli()) < 1000 * 60 
                }
                if (isProcessed) {
                    conversationRepository.markAsConsolidated(conversation.id)
                    continue
                }
            }

            // Summarize into an episode with Significance Score
            val messagesText = conversation.currentMessages.takeLast(30).joinToString("\n") { "${it.role}: ${it.toText()}" }
            val prompt = """
                Analyze the following conversation and create a "Memory Episode".
                
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
                val responseText = response.choices.firstOrNull()?.message?.toText() ?: continue
                
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
                val summaryEmbedding = embeddingService.embed(summary, assistantId)
                
                if (summaryEmbedding != null) {
                    chatEpisodeDAO.insertEpisode(
                        ChatEpisodeEntity(
                            assistantId = assistantId,
                            content = summary,
                            embedding = JsonInstant.encodeToString(summaryEmbedding),
                            startTime = conversation.createAt.toEpochMilli(),
                            endTime = conversation.updateAt.toEpochMilli(),
                            lastAccessedAt = System.currentTimeMillis(),
                            significance = significance
                        )
                    )
                    conversationRepository.markAsConsolidated(conversation.id)
                    trackACount++
                    Log.i("MemoryConsolidation", "Created episode (sig=$significance) for conversation ${conversation.id}")
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

        // =========================================================================================
        // TRACK B: Core Memory Extraction & Reflection
        // =========================================================================================
        // Only run if enabled AND enough time has passed
        val humanMemoryIntervalMs = assistant.humanMemoryUpdateIntervalHours * 60 * 60 * 1000L
        val timeSinceLastHumanUpdate = now - assistant.lastHumanMemoryUpdateTime
        
        if (assistant.enableHumanMemory && (timeSinceLastHumanUpdate > humanMemoryIntervalMs || isFullScan)) {
            var newFactsCount = 0
            // Review recent episodes and extract permanent facts OR high-level insights
            val episodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId).take(20) // Look at more episodes for reflection
            if (episodes.isNotEmpty()) {
                val episodesText = episodes.joinToString("\n") { "- [Sig: ${it.significance}] ${it.content}" }
                
                val prompt = """
                    Reflect on the following recent memory episodes.
                    1. Extract any new permanent facts about the user. ONLY extract facts that are likely to remain true for a long time (e.g., names, relationships, core preferences, life events). Do NOT extract temporary states or trivial details.
                    2. Identify any high-level patterns or insights about the user's behavior or state of mind.
                    
                    Return a bulleted list of facts/insights. Return "NONE" if nothing new.
                    
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
                        
                        // Pre-load existing embeddings for duplicate checking
                        val existingMemories = memoryRepository.getMemoryEntitiesOfAssistant(assistantId)
                            .filter { it.type == MemoryType.CORE }
                        val existingEmbeddings = existingMemories.mapNotNull { mem ->
                            mem.embedding?.let {
                                try {
                                    JsonInstant.decodeFromString<List<Float>>(it)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }

                        for (fact in facts) {
                            // Check for duplicates using embeddings
                            var isDuplicate = false
                            try {
                                val factEmbedding = embeddingService.embed(fact, assistantId)
                                if (factEmbedding != null) {
                                    // Check similarity against all existing core memories
                                    // Threshold 0.8 as requested
                                    isDuplicate = existingEmbeddings.any { existingEmb ->
                                        VectorEngine.cosineSimilarity(factEmbedding, existingEmb) > 0.8
                                    }
                                } else {
                                    // Fallback to simple word matching if embedding fails
                                    isDuplicate = existingMemories.any { existing ->
                                        val words = fact.lowercase().split(" ").filter { it.length > 4 }
                                        words.isNotEmpty() && words.all { existing.content.lowercase().contains(it) }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("MemoryConsolidation", "Failed to generate embedding for duplicate check", e)
                            }
                            
                            if (!isDuplicate) {
                                memoryRepository.addMemory(assistantId, fact)
                                newFactsCount++
                                Log.i("MemoryConsolidation", "Extracted new core memory: $fact")
                            } else {
                                Log.i("MemoryConsolidation", "Skipped duplicate memory: $fact")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MemoryConsolidation", "Failed to extract core memories", e)
                }
            }
            
            // Update Track B Stats
            val resultMsg = if (newFactsCount > 0) "Extracted $newFactsCount new core memories" else "No new insights found"
            settingsStore.update { currentSettings ->
                currentSettings.copy(
                    assistants = currentSettings.assistants.map { 
                        if (it.id == settings.assistantId) {
                            it.copy(
                                lastHumanMemoryUpdateTime = now,
                                lastHumanMemoryUpdateResult = resultMsg
                            )
                        } else it
                    }
                )
            }

            // Deduplicate Core Memories
            try {
                deduplicateMemories(assistantId, model, provider)
            } catch (e: Exception) {
                Log.e("MemoryConsolidation", "Error during deduplication", e)
            }
        }

        // =========================================================================================
        // PRUNING: The "Throw Out" Mechanism
        // =========================================================================================
        val allEpisodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        
        var prunedCount = 0
        for (episode in allEpisodes) {
            val age = now - episode.startTime
            val timeSinceAccess = now - episode.lastAccessedAt
            
            // Retention Policy based on Significance
            val retentionDays = if (assistant.enableHumanMemory) {
                when (episode.significance) {
                    in 8..10 -> 365L * 10 // Practically forever
                    in 5..7 -> 90L // 3 months
                    else -> 14L // 2 weeks for trivial stuff
                }
            } else {
                30L // Default 30 days
            }
            
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
    }

    private suspend fun deduplicateMemories(assistantId: String, model: Model, provider: ProviderSetting) {
        val memories = memoryRepository.getMemoryEntitiesOfAssistant(assistantId).filter { it.type == MemoryType.CORE }
        if (memories.size < 2) return

        // Ensure all memories have embeddings
        val memoriesWithEmbeddings = memories.mapNotNull { memory ->
            val embedding = if (memory.embedding != null) {
                try {
                    JsonInstant.decodeFromString<List<Float>>(memory.embedding)
                } catch (e: Exception) {
                    null
                }
            } else {
                try {
                    val newEmbedding = embeddingService.embed(memory.content, assistantId)
                    newEmbedding
                } catch (e: Exception) {
                    null
                }
            }
            if (embedding != null) memory to embedding else null
        }

        val visited = mutableSetOf<Int>()
        val providerHandler = providerManager.getProviderByType(provider)

        // Find clusters of similar memories
        for (i in memoriesWithEmbeddings.indices) {
            val current = memoriesWithEmbeddings[i]
            if (current.first.id in visited) continue

            val cluster = mutableListOf(current)
            visited.add(current.first.id)
            
            // Use a queue for BFS to find all connected components
            val queue = ArrayDeque<Pair<MemoryEntity, List<Float>>>()
            queue.add(current)
            
            while (queue.isNotEmpty()) {
                val (nodeMem, nodeEmb) = queue.removeFirst()
                
                // Check all other nodes
                for (j in memoriesWithEmbeddings.indices) {
                    val neighbor = memoriesWithEmbeddings[j]
                    if (neighbor.first.id !in visited) {
                        val similarity = VectorEngine.cosineSimilarity(nodeEmb, neighbor.second)
                        if (similarity >= 0.8) { // Threshold 0.8 as requested
                            visited.add(neighbor.first.id)
                            cluster.add(neighbor)
                            queue.add(neighbor)
                        }
                    }
                }
            }

            // If we found a cluster with more than 1 memory, merge them
            if (cluster.size > 1) {
                val memoriesToMerge = cluster.map { it.first }
                val memoriesText = memoriesToMerge.mapIndexed { index, mem -> "${index + 1}. ${mem.content}" }.joinToString("\n")
                
                val prompt = """
                    Here are ${cluster.size} memories about the user that are very similar:
                    
                    $memoriesText
                    
                    Please merge them into a single, concise memory that retains all key information from all of them.
                    If they are not actually duplicates or related enough to merge, return "NO_MERGE".
                    Return ONLY the merged memory text or "NO_MERGE".
                """.trimIndent()

                try {
                    val response = providerHandler.generateText(
                        providerSetting = provider,
                        messages = listOf(UIMessage.user(prompt)),
                        params = TextGenerationParams(model = model, temperature = 0.1f)
                    )
                    val mergedText = response.choices.firstOrNull()?.message?.toText()?.trim() ?: continue

                    if (mergedText != "NO_MERGE" && mergedText.isNotBlank() && mergedText.length > 5) {
                        Log.i("MemoryConsolidation", "Merging ${cluster.size} memories: ${memoriesToMerge.map { it.id }}")
                        
                        // Update the first memory with the merged text
                        val primaryMemory = memoriesToMerge.first()
                        memoryRepository.updateContent(primaryMemory.id, mergedText)
                        
                        // Delete the rest
                        for (k in 1 until memoriesToMerge.size) {
                            memoryRepository.deleteMemory(memoriesToMerge[k].id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MemoryConsolidation", "Failed to merge cluster", e)
                }
            }
        }
    }
}
