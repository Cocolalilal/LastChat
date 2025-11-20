package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.utils.JsonInstant

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val chatEpisodeDAO: ChatEpisodeDAO,
    private val embeddingService: EmbeddingService
) {
    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content, it.type, it.embedding != null) }
            }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(assistantId)
            .map { AssistantMemory(it.id, it.content, it.type, it.embedding != null) }
    }

    suspend fun getMemoryEntitiesOfAssistant(assistantId: String): List<MemoryEntity> {
        return memoryDAO.getMemoriesOfAssistant(assistantId)
    }

    suspend fun getEpisodeEntitiesOfAssistant(assistantId: String): List<ChatEpisodeEntity> {
        return chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
    }


    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
        chatEpisodeDAO.deleteEpisodesOfAssistant(assistantId)
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val memory = memoryDAO.getMemoryById(id) ?: error("Memory not found")
        val newMemory = memory.copy(content = content, embedding = null) // Invalidate embedding
        memoryDAO.updateMemory(newMemory)
        return AssistantMemory(
            id = newMemory.id,
            content = newMemory.content,
            type = newMemory.type,
            hasEmbedding = false
        )
    }

    suspend fun addMemory(assistantId: String, content: String): AssistantMemory {
        val embedding = try {
            embeddingService.embed(content, assistantId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        val entity = MemoryEntity(
            assistantId = assistantId,
            content = content,
            embedding = embedding?.let { JsonInstant.encodeToString(it) },
            type = MemoryType.CORE,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )
        
        val id = memoryDAO.insertMemory(entity)
        
        return AssistantMemory(
            id = id.toInt(),
            content = content,
            type = MemoryType.CORE
        )
    }

    suspend fun deleteMemory(id: Int) {
        memoryDAO.deleteMemory(id)
    }

    suspend fun retrieveRelevantMemories(assistantId: String, query: String, limit: Int = 5, similarityThreshold: Float = 0.5f): List<AssistantMemory> {
        val queryEmbedding = try {
            embeddingService.embed(query, assistantId)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }

        // Get both core memories and episodes
        val memories = memoryDAO.getMemoriesOfAssistant(assistantId)
        val episodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        
        // Score core memories - generate embeddings on-the-fly for memories without them
        val memoryScores = memories.mapNotNull { memory ->
            var embedding: List<Float>? = null
            
            // Try to get existing embedding
            val embeddingStr = memory.embedding
            if (embeddingStr != null) {
                embedding = try {
                    JsonInstant.decodeFromString<List<Float>>(embeddingStr)
                } catch (e: Exception) {
                    null
                }
            }
            
            // If no embedding exists, generate it lazily
            if (embedding == null) {
                try {
                    embedding = embeddingService.embed(memory.content, assistantId)
                    // Save the generated embedding back to the database
                    memoryDAO.updateMemory(memory.copy(embedding = JsonInstant.encodeToString(embedding)))
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@mapNotNull null
                }
            }
            
            val similarity = VectorEngine.cosineSimilarity(queryEmbedding, embedding)
            
            // Core memories don't decay, score is just similarity
            // But we can give them a slight boost to ensure important facts are prioritized
            val score = similarity * 1.05f 
            
            if (score >= similarityThreshold) {
                Triple(memory, score, true) // true = is memory
            } else null
        }
        
        // Score episodes - generate embeddings on-the-fly for episodes without them
        val episodeScores = episodes.mapNotNull { episode ->
            var embedding: List<Float>? = null
            
            // Try to get existing embedding
            val embeddingStr = episode.embedding
            if (embeddingStr != null) {
                embedding = try {
                    JsonInstant.decodeFromString<List<Float>>(embeddingStr)
                } catch (e: Exception) {
                    null
                }
            }
            
            // If no embedding exists, generate it lazily
            if (embedding == null) {
                try {
                    embedding = embeddingService.embed(episode.content, assistantId)
                    // Save the generated embedding back to the database
                    chatEpisodeDAO.insertEpisode(episode.copy(embedding = JsonInstant.encodeToString(embedding)))
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@mapNotNull null
                }
            }
            
            val similarity = VectorEngine.cosineSimilarity(queryEmbedding, embedding)
            
            // Calculate Recency Score
            // Decay over 7 days (half-life)
            val ageInMillis = System.currentTimeMillis() - episode.startTime
            val ageInDays = ageInMillis / (1000.0 * 60 * 60 * 24)
            val recency = (1.0 / (1.0 + (ageInDays / 7.0))).toFloat()
            
            // Dual-Track Score Formula
            val score = (similarity * 0.7f) + (recency * 0.3f)
            
            if (score >= similarityThreshold) {
                Triple(episode as Any, score, false) // false = is episode
            } else null
        }
        
        // Combine and sort by score
        val allScored = (memoryScores + episodeScores).sortedByDescending { it.second }
        
        // Update lastAccessedAt for retrieved memories
        allScored.take(limit).forEach { (item, _, isMemory) ->
            if (isMemory) {
                val memory = item as MemoryEntity
                memoryDAO.updateMemory(memory.copy(lastAccessedAt = System.currentTimeMillis()))
            } else {
                val episode = item as ChatEpisodeEntity
                chatEpisodeDAO.insertEpisode(episode.copy(lastAccessedAt = System.currentTimeMillis()))
            }
        }
        
        return allScored.take(limit).mapNotNull { (item, _, isMemory) ->
            if (isMemory) {
                val memory = item as MemoryEntity
                AssistantMemory(memory.id, memory.content, memory.type, true)
            } else {
                val episode = item as ChatEpisodeEntity
                // Convert episode to AssistantMemory with a negative ID to distinguish
                AssistantMemory(-episode.id, episode.content, MemoryType.EPISODIC, true)
            }
        }
    }

    /**
     * Regenerate embeddings for all memories and episodes that are missing embeddings.
     * This is useful when:
     * - Embedding model has changed
     * - Old memories were created before embeddings were implemented
     * - Embeddings failed to generate initially
     * 
     * @param assistantId The assistant ID to regenerate embeddings for
     * @param forceRegenerate If true, regenerate all embeddings even if they exist
     * @return Number of items updated
     */
    suspend fun regenerateEmbeddings(
        assistantId: String,
        onProgress: (Int, Int) -> Unit
    ): Pair<Int, Int> {
        val memories = memoryDAO.getMemoriesOfAssistant(assistantId)
        val episodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        val total = memories.size + episodes.size
        var current = 0
        var successCount = 0
        var failureCount = 0

        onProgress(0, total)
        if (total == 0) return 0 to 0

        // Helper to process items
        suspend fun processItem(content: String, update: suspend (String) -> Unit) {
            try {
                val embedding = embeddingService.embed(content, assistantId)
                // embeddingService.embed throws if it fails, so if we get here, it's not null
                update(JsonInstant.encodeToString(embedding))
                successCount++
            } catch (e: Exception) {
                e.printStackTrace()
                failureCount++
            } finally {
                current++
                onProgress(current, total)
            }
        }

        // Process Core Memories
        memories.forEach { memory ->
            if (memory.embedding == null) {
                processItem(memory.content) { embeddingJson ->
                    memoryDAO.updateMemory(memory.copy(embedding = embeddingJson))
                }
            } else {
                current++
                onProgress(current, total)
            }
        }

        // Process Episodes
        episodes.forEach { episode ->
            if (episode.embedding == null) {
                processItem(episode.content) { embeddingJson ->
                    chatEpisodeDAO.insertEpisode(episode.copy(embedding = embeddingJson))
                }
            } else {
                current++
                onProgress(current, total)
            }
        }
        
        return successCount to failureCount
    }
}
