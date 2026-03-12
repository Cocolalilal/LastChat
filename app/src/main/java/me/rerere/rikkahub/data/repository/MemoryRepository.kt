package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.db.entity.EmbeddingCacheEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.utils.JsonInstant

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val chatEpisodeDAO: ChatEpisodeDAO,
    private val embeddingService: EmbeddingService,
    private val embeddingCacheDAO: EmbeddingCacheDAO
) {
    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content, it.type, it.embedding != null, it.embeddingModelId, it.createdAt) }
            }

    /**
     * Get combined memories (core) and episodes (episodic) as AssistantMemory objects.
     * This includes significance scores for episodic memories.
     */
    fun getCombinedMemoriesFlow(assistantId: String): Flow<List<AssistantMemory>> =
        kotlinx.coroutines.flow.combine(
            memoryDAO.getMemoriesOfAssistantFlow(assistantId),
            chatEpisodeDAO.getEpisodesOfAssistantFlow(assistantId)
        ) { memories, episodes ->
            val coreMemories = memories.map { 
                AssistantMemory(it.id, it.content, it.type, it.embedding != null, it.embeddingModelId, it.createdAt)
            }
            val episodicMemories = episodes.map { 
                AssistantMemory(-it.id, it.content, MemoryType.EPISODIC, it.embedding != null, it.embeddingModelId, it.startTime, it.significance)
            }
            coreMemories + episodicMemories
        }

    fun getAverageMemoryLength(assistantId: String): Flow<Int> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities ->
                if (entities.isEmpty()) return@map 150 // Default estimate
                val totalLength = entities.sumOf { it.content.length.toLong() }
                (totalLength / entities.size).toInt()
            }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(assistantId)
            .map { AssistantMemory(it.id, it.content, it.type, it.embedding != null, it.embeddingModelId, it.createdAt) }
    }

    suspend fun getMemoryById(id: Int): AssistantMemory? {
        val memory = memoryDAO.getMemoryById(id) ?: return null
        return AssistantMemory(
            id = memory.id,
            content = memory.content,
            type = memory.type,
            hasEmbedding = memory.embedding != null,
            embeddingModelId = memory.embeddingModelId,
            timestamp = memory.createdAt
        )
    }

    suspend fun getMemoryEntitiesOfAssistant(assistantId: String): List<MemoryEntity> {
        return memoryDAO.getMemoriesOfAssistant(assistantId)
    }

    suspend fun getEpisodeEntitiesOfAssistant(assistantId: String): List<ChatEpisodeEntity> {
        return chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
    }

    /**
     * Get or create an embedding for a memory/episode content.
     * First checks the cache, then generates if not found.
     * @return The embedding if successful, null otherwise
     */
    private suspend fun getOrCreateEmbedding(
        memoryId: Int,
        memoryType: Int,
        content: String,
        assistantId: String,
        existingEmbedding: String? = null,
        existingModelId: String? = null
    ): List<Float>? {
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        
        // Check cache first
        val cached = embeddingCacheDAO.getEmbedding(memoryId, memoryType, modelId)
        if (cached != null) {
            return try {
                JsonInstant.decodeFromString<List<Float>>(cached.embedding)
            } catch (e: Exception) {
                null
            }
        }
        
        // Check existing embedding in entity (Fallback / Optimization)
        if (existingEmbedding != null && existingModelId == modelId) {
             try {
                val emb = JsonInstant.decodeFromString<List<Float>>(existingEmbedding)
                // Backfill cache for future performance
                embeddingCacheDAO.insertEmbedding(
                    EmbeddingCacheEntity(
                        memoryId = memoryId,
                        memoryType = memoryType,
                        modelId = modelId,
                        embedding = existingEmbedding
                    )
                )
                return emb
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }

        // Generate new embedding
        return try {
            val embedding = embeddingService.embed(content, assistantId)
            // Cache it
            embeddingCacheDAO.insertEmbedding(
                EmbeddingCacheEntity(
                    memoryId = memoryId,
                    memoryType = memoryType,
                    modelId = modelId,
                    embedding = JsonInstant.encodeToString(embedding)
                )
            )
            embedding
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Check if an embedding exists in cache for the current model.
     */
    suspend fun hasEmbeddingForCurrentModel(memoryId: Int, memoryType: Int, assistantId: String): Boolean {
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        return embeddingCacheDAO.hasEmbedding(memoryId, memoryType, modelId)
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
        chatEpisodeDAO.deleteEpisodesOfAssistant(assistantId)
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val memory = memoryDAO.getMemoryById(id) ?: error("Memory not found")
        val newMemory = memory.copy(content = content, embedding = null) // Invalidate embedding
        memoryDAO.updateMemory(newMemory)

        // Invalidate cache
        embeddingCacheDAO.deleteByMemoryId(id, MemoryType.CORE)

        return AssistantMemory(
            id = newMemory.id,
            content = newMemory.content,
            type = newMemory.type,
            hasEmbedding = false,
            timestamp = newMemory.createdAt
        )
    }

    suspend fun updateEpisodeContent(id: Int, content: String): AssistantMemory {
        val episode = chatEpisodeDAO.getEpisodeById(id) ?: error("Episode not found")
        val newEpisode = episode.copy(content = content, embedding = null) // Invalidate embedding
        chatEpisodeDAO.insertEpisode(newEpisode)

        // Invalidate cache
        embeddingCacheDAO.deleteByMemoryId(id, MemoryType.EPISODIC)

        return AssistantMemory(
            id = -newEpisode.id,
            content = newEpisode.content,
            type = MemoryType.EPISODIC,
            hasEmbedding = false,
            timestamp = newEpisode.startTime,
            significance = newEpisode.significance
        )
    }

    suspend fun addMemory(assistantId: String, content: String): AssistantMemory {
        val embeddingResult = try {
            embeddingService.embedWithModelId(content, assistantId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        val entity = MemoryEntity(
            assistantId = assistantId,
            content = content,
            embedding = embeddingResult?.embeddings?.firstOrNull()?.let { JsonInstant.encodeToString(it) },
            embeddingModelId = embeddingResult?.modelId,
            type = MemoryType.CORE,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )
        
        val id = memoryDAO.insertMemory(entity)
        
        // Add to cache immediately if available
        if (embeddingResult != null && embeddingResult.embeddings.isNotEmpty()) {
             embeddingCacheDAO.insertEmbedding(
                EmbeddingCacheEntity(
                    memoryId = id.toInt(),
                    memoryType = MemoryType.CORE,
                    modelId = embeddingResult.modelId,
                    embedding = JsonInstant.encodeToString(embeddingResult.embeddings.first())
                )
             )
        }

        return AssistantMemory(
            id = id.toInt(),
            content = content,
            type = MemoryType.CORE,
            hasEmbedding = embeddingResult != null,
            embeddingModelId = embeddingResult?.modelId
        )
    }

    suspend fun deleteMemory(id: Int) {
        memoryDAO.deleteMemory(id)
        embeddingCacheDAO.deleteByMemoryId(id, MemoryType.CORE)
    }

    /**
     * Retrieve relevant memories with scores for debugging
     */
    suspend fun retrieveRelevantMemoriesWithScores(assistantId: String, query: String, limit: Int = 5, similarityThreshold: Float = 0.5f): List<Pair<AssistantMemory, Float>> {
        return retrieveRelevantMemoriesWithScores(
            assistantId = assistantId,
            query = query,
            limit = limit,
            similarityThreshold = similarityThreshold,
            includeCore = true,
            includeEpisodes = true
        )
    }

    suspend fun retrieveRelevantMemories(
        assistantId: String,
        query: String,
        limit: Int = 5,
        similarityThreshold: Float = 0.5f,
        includeCore: Boolean = true,
        includeEpisodes: Boolean = true
    ): List<AssistantMemory> {
        return retrieveRelevantMemoriesWithScores(
            assistantId, query, limit, similarityThreshold, includeCore, includeEpisodes
        ).map { it.first }
    }

    suspend fun retrieveRelevantMemoriesWithScores(
        assistantId: String,
        query: String,
        limit: Int = 5,
        similarityThreshold: Float = 0.5f,
        includeCore: Boolean = true,
        includeEpisodes: Boolean = true
    ): List<Pair<AssistantMemory, Float>> {
        val queryEmbedding = try {
            embeddingService.embed(query, assistantId)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }

        // Get both core memories and episodes
        val memories = if (includeCore) memoryDAO.getMemoriesOfAssistant(assistantId) else emptyList()
        val episodes = if (includeEpisodes) chatEpisodeDAO.getEpisodesOfAssistant(assistantId) else emptyList()
        
        // Score core memories - use cache for embeddings
        val memoryScores = memories.mapNotNull { memory ->
            val embedding = getOrCreateEmbedding(
                memoryId = memory.id,
                memoryType = MemoryType.CORE,
                content = memory.content,
                assistantId = assistantId,
                existingEmbedding = memory.embedding,
                existingModelId = memory.embeddingModelId
            ) ?: return@mapNotNull null
            
            val similarity = VectorEngine.cosineSimilarity(queryEmbedding, embedding)
            
            // Core memories don't decay, score is just similarity
            // But we can give them a slight boost to ensure important facts are prioritized
            val score = similarity * 1.05f 
            
            if (score >= similarityThreshold) {
                Triple(memory, score, true) // true = is memory
            } else null
        }
        
        // Score episodes - use cache for embeddings
        val episodeScores = episodes.mapNotNull { episode ->
            val embedding = getOrCreateEmbedding(
                memoryId = episode.id,
                memoryType = MemoryType.EPISODIC,
                content = episode.content,
                assistantId = assistantId,
                existingEmbedding = episode.embedding,
                existingModelId = episode.embeddingModelId
            ) ?: return@mapNotNull null
            
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
        
        return allScored.take(limit).mapNotNull { triple ->
            val item = triple.first
            val score = triple.second
            val isMemory = triple.third

            if (isMemory) {
                val memory = item as MemoryEntity
                Pair<AssistantMemory, Float>(AssistantMemory(memory.id, memory.content, memory.type, true, memory.embeddingModelId, memory.createdAt), score)
            } else {
                val episode = item as ChatEpisodeEntity
                // Convert episode to AssistantMemory with a negative ID to distinguish
                Pair<AssistantMemory, Float>(AssistantMemory(-episode.id, episode.content, MemoryType.EPISODIC, true, episode.embeddingModelId, episode.startTime, episode.significance), score)
            }
        }
    }

    /**
     * Regenerate embeddings for memories and episodes that need it.
     * Only processes memories that:
     * - Have no embedding
     * - Have an embedding from a different model
     * 
     * @param assistantId The assistant ID to regenerate embeddings for
     * @return Pair of (successCount, failureCount)
     */
    suspend fun regenerateEmbeddings(
        assistantId: String,
        onProgress: (Int, Int) -> Unit
    ): Pair<Int, Int> {
        val allMemories = memoryDAO.getMemoriesOfAssistant(assistantId)
        val allEpisodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        
        // Get current embedding model ID
        val currentModelId = embeddingService.getEmbeddingModelId(assistantId)
        
        // Filter to only memories that need embedding
        val memoriesNeedingEmbedding = allMemories.filter { 
            it.embedding == null || it.embeddingModelId != currentModelId 
        }
        val episodesNeedingEmbedding = allEpisodes.filter { 
            it.embedding == null || it.embeddingModelId != currentModelId 
        }
        
        val total = memoriesNeedingEmbedding.size + episodesNeedingEmbedding.size
        var current = 0
        var successCount = 0
        var failureCount = 0

        onProgress(0, total)
        if (total == 0) return 0 to 0

        // Process Core Memories that need embedding
        memoriesNeedingEmbedding.forEach { memory ->
            current++
            try {
                val embedding = embeddingService.embed(memory.content, assistantId)
                val embeddingJson = JsonInstant.encodeToString(embedding)
                // Store in entity for backward compatibility
                memoryDAO.updateMemory(memory.copy(embedding = embeddingJson, embeddingModelId = currentModelId))
                // Store in cache for model-based persistence
                embeddingCacheDAO.insertEmbedding(
                    EmbeddingCacheEntity(
                        memoryId = memory.id,
                        memoryType = MemoryType.CORE,
                        modelId = currentModelId,
                        embedding = embeddingJson
                    )
                )
                successCount++
            } catch (e: Exception) {
                e.printStackTrace()
                failureCount++
            }
            onProgress(current, total)
        }

        // Process Episodes that need embedding
        episodesNeedingEmbedding.forEach { episode ->
            current++
            try {
                val embedding = embeddingService.embed(episode.content, assistantId)
                val embeddingJson = JsonInstant.encodeToString(embedding)
                // Store in entity for backward compatibility
                chatEpisodeDAO.insertEpisode(episode.copy(embedding = embeddingJson, embeddingModelId = currentModelId))
                // Store in cache for model-based persistence
                embeddingCacheDAO.insertEmbedding(
                    EmbeddingCacheEntity(
                        memoryId = episode.id,
                        memoryType = MemoryType.EPISODIC,
                        modelId = currentModelId,
                        embedding = embeddingJson
                    )
                )
                successCount++
            } catch (e: Exception) {
                e.printStackTrace()
                failureCount++
            }
            onProgress(current, total)
        }
        
        return successCount to failureCount
    }

    /**
     * Embed only memories that are missing embeddings or have wrong model.
     * Called during consolidation to fix any gaps without regenerating everything.
     * 
     * @param assistantId The assistant ID to fix embeddings for
     * @return Pair of (successCount, failureCount)
     */
    suspend fun embedMissingMemories(assistantId: String): Pair<Int, Int> {
        val memories = memoryDAO.getMemoriesOfAssistant(assistantId)
        val episodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        val currentModelId = embeddingService.getEmbeddingModelId(assistantId)
        
        var successCount = 0
        var failureCount = 0

        // Filter to only memories that need embedding
        val memoriesNeedingEmbedding = memories.filter { 
            it.embedding == null || it.embeddingModelId != currentModelId 
        }
        val episodesNeedingEmbedding = episodes.filter { 
            it.embedding == null || it.embeddingModelId != currentModelId 
        }

        // Process Core Memories that need embedding
        memoriesNeedingEmbedding.forEach { memory ->
            try {
                val embedding = embeddingService.embed(memory.content, assistantId)
                val embeddingJson = JsonInstant.encodeToString(embedding)
                memoryDAO.updateMemory(memory.copy(
                    embedding = embeddingJson,
                    embeddingModelId = currentModelId
                ))
                // Also cache
                embeddingCacheDAO.insertEmbedding(
                    EmbeddingCacheEntity(
                        memoryId = memory.id,
                        memoryType = MemoryType.CORE,
                        modelId = currentModelId,
                        embedding = embeddingJson
                    )
                )
                successCount++
            } catch (e: Exception) {
                e.printStackTrace()
                failureCount++
            }
        }

        // Process Episodes that need embedding
        episodesNeedingEmbedding.forEach { episode ->
            try {
                val embedding = embeddingService.embed(episode.content, assistantId)
                val embeddingJson = JsonInstant.encodeToString(embedding)
                chatEpisodeDAO.insertEpisode(episode.copy(
                    embedding = embeddingJson,
                    embeddingModelId = currentModelId
                ))
                // Also cache
                embeddingCacheDAO.insertEmbedding(
                    EmbeddingCacheEntity(
                        memoryId = episode.id,
                        memoryType = MemoryType.EPISODIC,
                        modelId = currentModelId,
                        embedding = embeddingJson
                    )
                )
                successCount++
            } catch (e: Exception) {
                e.printStackTrace()
                failureCount++
            }
        }
        
        return successCount to failureCount
    }

    /**
     * Count how many memories need embedding (no embedding or wrong model).
     * Used to determine if the regenerate button should be shown.
     */
    suspend fun countMemoriesNeedingEmbedding(assistantId: String): Int {
        val memories = memoryDAO.getMemoriesOfAssistant(assistantId)
        val episodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        val currentModelId = embeddingService.getEmbeddingModelId(assistantId)
        
        val memoriesNeedingEmbedding = memories.count { 
            it.embedding == null || it.embeddingModelId != currentModelId 
        }
        val episodesNeedingEmbedding = episodes.count { 
            it.embedding == null || it.embeddingModelId != currentModelId 
        }
        
        return memoriesNeedingEmbedding + episodesNeedingEmbedding
    }
}
