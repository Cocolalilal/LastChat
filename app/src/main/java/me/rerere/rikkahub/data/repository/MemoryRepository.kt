package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.ai.rag.EmbeddingResult
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

internal interface MemoryEmbeddingGateway {
    fun getEmbeddingModelId(assistantId: String? = null): String
    suspend fun embed(text: String, assistantId: String? = null): List<Float>
    suspend fun embedWithModelId(text: String, assistantId: String? = null): EmbeddingResult
}

internal class EmbeddingServiceMemoryGateway(
    private val embeddingService: EmbeddingService,
) : MemoryEmbeddingGateway {
    override fun getEmbeddingModelId(assistantId: String?): String {
        return embeddingService.getEmbeddingModelId(assistantId)
    }

    override suspend fun embed(text: String, assistantId: String?): List<Float> {
        return embeddingService.embed(text, assistantId)
    }

    override suspend fun embedWithModelId(text: String, assistantId: String?): EmbeddingResult {
        return embeddingService.embedWithModelId(text, assistantId)
    }
}

class MemoryRepository internal constructor(
    private val memoryDAO: MemoryDAO,
    private val chatEpisodeDAO: ChatEpisodeDAO,
    private val embeddingGateway: MemoryEmbeddingGateway,
    private val embeddingCacheDAO: EmbeddingCacheDAO
) {
    constructor(
        memoryDAO: MemoryDAO,
        chatEpisodeDAO: ChatEpisodeDAO,
        embeddingService: EmbeddingService,
        embeddingCacheDAO: EmbeddingCacheDAO,
    ) : this(
        memoryDAO = memoryDAO,
        chatEpisodeDAO = chatEpisodeDAO,
        embeddingGateway = EmbeddingServiceMemoryGateway(embeddingService),
        embeddingCacheDAO = embeddingCacheDAO,
    )

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities ->
                entities.map(::coreMemoryToAssistantMemory)
            }

    /**
     * Get combined memories (core) and episodes (episodic) as AssistantMemory objects.
     * This includes significance scores for episodic memories.
     */
    fun getCombinedMemoriesFlow(assistantId: String): Flow<List<AssistantMemory>> =
        combine(
            memoryDAO.getMemoriesOfAssistantFlow(assistantId),
            chatEpisodeDAO.getEpisodesOfAssistantFlow(assistantId)
        ) { memories, episodes ->
            val coreMemories = memories.map(::coreMemoryToAssistantMemory)
            val episodicMemories = episodes.map(::episodeToAssistantMemory)
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
            .map(::coreMemoryToAssistantMemory)
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

    suspend fun getStoredMemories(
        assistantId: String,
        includeCore: Boolean = true,
        includeEpisodes: Boolean = true,
        limit: Int? = null,
    ): List<AssistantMemory> {
        val combined = buildList {
            if (includeCore) {
                addAll(memoryDAO.getMemoriesOfAssistant(assistantId).map(::coreMemoryToAssistantMemory))
            }
            if (includeEpisodes) {
                addAll(chatEpisodeDAO.getEpisodesOfAssistant(assistantId).map(::episodeToAssistantMemory))
            }
        }
        val cappedLimit = limit?.coerceAtLeast(0)
        return if (cappedLimit != null) combined.take(cappedLimit) else combined
    }

    suspend fun resolveConfiguredMemories(
        assistantId: String,
        query: String,
        ragEnabled: Boolean,
        limit: Int,
        similarityThreshold: Float,
        includeCore: Boolean = true,
        includeEpisodes: Boolean = true,
    ): List<AssistantMemory> {
        if (!ragEnabled) {
            return getMemoriesOfAssistant(assistantId)
        }
        if (query.isBlank()) {
            return getStoredMemories(
                assistantId = assistantId,
                includeCore = includeCore,
                includeEpisodes = includeEpisodes,
                limit = limit,
            )
        }
        return retrieveRelevantMemories(
            assistantId = assistantId,
            query = query,
            limit = limit,
            similarityThreshold = similarityThreshold,
            includeCore = includeCore,
            includeEpisodes = includeEpisodes,
        )
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
        val modelId = embeddingGateway.getEmbeddingModelId(assistantId)
        
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
            val embedding = embeddingGateway.embed(content, assistantId)
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
        val modelId = embeddingGateway.getEmbeddingModelId(assistantId)
        return embeddingCacheDAO.hasEmbedding(memoryId, memoryType, modelId)
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        val coreMemories = memoryDAO.getMemoriesOfAssistant(assistantId)
        val episodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        deleteEmbeddingCacheFor(coreMemories = coreMemories, episodes = episodes)
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
        chatEpisodeDAO.deleteEpisodesOfAssistant(assistantId)
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val memory = memoryDAO.getMemoryById(id) ?: error("Memory not found")
        val newMemory = memory.copy(content = content, embedding = null, embeddingModelId = null)
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
        val newEpisode = episode.copy(content = content, embedding = null, embeddingModelId = null)
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
            embeddingGateway.embedWithModelId(content, assistantId)
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

    suspend fun deleteEpisode(id: Int) {
        chatEpisodeDAO.deleteEpisode(id)
        embeddingCacheDAO.deleteByMemoryId(id, MemoryType.EPISODIC)
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
            embeddingGateway.embed(query, assistantId)
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
            val score = computeCoreMemoryScore(similarity)
            
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
            val score = computeEpisodeScore(
                similarity = similarity,
                startTimeMillis = episode.startTime,
                significance = episode.significance,
            ) ?: return@mapNotNull null
            
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
                Pair<AssistantMemory, Float>(coreMemoryToAssistantMemory(memory), score)
            } else {
                val episode = item as ChatEpisodeEntity
                Pair<AssistantMemory, Float>(episodeToAssistantMemory(episode), score)
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
        val currentModelId = embeddingGateway.getEmbeddingModelId(assistantId)
        
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
                val embedding = embeddingGateway.embed(memory.content, assistantId)
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
                val embedding = embeddingGateway.embed(episode.content, assistantId)
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
        val currentModelId = embeddingGateway.getEmbeddingModelId(assistantId)
        
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
                val embedding = embeddingGateway.embed(memory.content, assistantId)
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
                val embedding = embeddingGateway.embed(episode.content, assistantId)
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
        val currentModelId = embeddingGateway.getEmbeddingModelId(assistantId)
        
        val memoriesNeedingEmbedding = memories.count { 
            it.embedding == null || it.embeddingModelId != currentModelId 
        }
        val episodesNeedingEmbedding = episodes.count { 
            it.embedding == null || it.embeddingModelId != currentModelId 
        }
        
        return memoriesNeedingEmbedding + episodesNeedingEmbedding
    }

    private suspend fun deleteEmbeddingCacheFor(
        coreMemories: List<MemoryEntity>,
        episodes: List<ChatEpisodeEntity>,
    ) {
        coreMemories.forEach { memory ->
            embeddingCacheDAO.deleteByMemoryId(memory.id, MemoryType.CORE)
        }
        episodes.forEach { episode ->
            embeddingCacheDAO.deleteByMemoryId(episode.id, MemoryType.EPISODIC)
        }
    }

    private fun coreMemoryToAssistantMemory(memory: MemoryEntity): AssistantMemory {
        return AssistantMemory(
            id = memory.id,
            content = memory.content,
            type = memory.type,
            hasEmbedding = memory.embedding != null,
            embeddingModelId = memory.embeddingModelId,
            timestamp = memory.createdAt,
        )
    }

    private fun episodeToAssistantMemory(episode: ChatEpisodeEntity): AssistantMemory {
        return AssistantMemory(
            id = -episode.id,
            content = episode.content,
            type = MemoryType.EPISODIC,
            hasEmbedding = episode.embedding != null,
            embeddingModelId = episode.embeddingModelId,
            timestamp = episode.startTime,
            significance = episode.significance,
        )
    }
}
