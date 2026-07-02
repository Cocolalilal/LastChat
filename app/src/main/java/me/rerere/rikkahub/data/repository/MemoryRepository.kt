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

import me.rerere.rikkahub.data.ai.rag.MemoryChunker
import me.rerere.rikkahub.data.ai.rag.toByteArray
import me.rerere.rikkahub.data.ai.rag.toFloatArray
import me.rerere.rikkahub.data.ai.rag.toListOfFloatArrays
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val chatEpisodeDAO: ChatEpisodeDAO,
    private val embeddingService: EmbeddingService,
    private val embeddingCacheDAO: EmbeddingCacheDAO
) {
    private val embeddingCache = java.util.concurrent.ConcurrentHashMap<String, List<FloatArray>>()

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

    suspend fun getMemoryEntitiesOfAssistantLimited(assistantId: String, limit: Int): List<MemoryEntity> {
        return memoryDAO.getMemoriesOfAssistantLimited(assistantId, limit)
    }

    suspend fun getEpisodeEntitiesOfAssistant(assistantId: String): List<ChatEpisodeEntity> {
        return chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
    }

    suspend fun getEpisodeEntitiesOfAssistantLimited(assistantId: String, limit: Int): List<ChatEpisodeEntity> {
        return chatEpisodeDAO.getEpisodesOfAssistantLimited(assistantId, limit)
    }

    /**
     * Get or create an embedding for a memory/episode content.
     * First checks the cache, then generates if not found.
     * @return The embedding if successful, null otherwise
     */
    private suspend fun getOrCreateEmbeddings(
        memoryId: Int,
        memoryType: Int,
        content: String,
        assistantId: String,
        existingEmbedding: String? = null,
        existingBlob: ByteArray? = null,
        existingModelId: String? = null
    ): List<FloatArray>? {
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        val cacheKey = "$memoryType:$memoryId:$modelId"

        embeddingCache[cacheKey]?.let { return it }

        if (existingModelId == modelId) {
            if (existingBlob != null) {
                val list = existingBlob.toListOfFloatArrays()
                embeddingCache[cacheKey] = list
                embeddingCacheDAO.insertEmbedding(
                    EmbeddingCacheEntity(
                        memoryId = memoryId,
                        memoryType = memoryType,
                        modelId = modelId,
                        embedding = "",
                        embeddingBlob = existingBlob
                    )
                )
                return list
            } else if (existingEmbedding != null) {
                try {
                    val floats = JsonInstant.decodeFromString<List<Float>>(existingEmbedding).toFloatArray()
                    val list = listOf(floats)
                    embeddingCache[cacheKey] = list
                    embeddingCacheDAO.insertEmbedding(
                        EmbeddingCacheEntity(
                            memoryId = memoryId,
                            memoryType = memoryType,
                            modelId = modelId,
                            embedding = "",
                            embeddingBlob = list.toByteArray()
                        )
                    )
                    return list
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return try {
            val chunks = MemoryChunker.chunkText(content)
            val result = embeddingService.embedBatch(chunks, assistantId)
            val listOfFloatArrays = result.embeddings.map { it.toFloatArray() }
            val blob = listOfFloatArrays.toByteArray()

            embeddingCache[cacheKey] = listOfFloatArrays
            embeddingCacheDAO.insertEmbedding(
                EmbeddingCacheEntity(
                    memoryId = memoryId,
                    memoryType = memoryType,
                    modelId = modelId,
                    embedding = "",
                    embeddingBlob = blob
                )
            )
            listOfFloatArrays
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Check if an embedding exists in cache for the current model.
     */

    private fun calculateKeywordScore(query: String, content: String): Float {
        val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
        if (queryWords.isEmpty()) return 0f
        val contentLower = content.lowercase()
        var matches = 0
        for (word in queryWords) {
            if (contentLower.contains(word)) matches++
        }
        return matches.toFloat() / queryWords.size.toFloat()
    }

suspend fun hasEmbeddingForCurrentModel(memoryId: Int, memoryType: Int, assistantId: String): Boolean {
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        val cacheKey = "$memoryType:$memoryId:$modelId"
        if (embeddingCache.containsKey(cacheKey)) return true
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
        embeddingCache.keys.removeAll { it.startsWith("${MemoryType.CORE}:$id:") }

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
        embeddingCache.keys.removeAll { it.startsWith("${MemoryType.EPISODIC}:$id:") }

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
        val chunks = MemoryChunker.chunkText(content)
        val embeddingResult = try {
            embeddingService.embedBatch(chunks, assistantId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        val floatArrays = embeddingResult?.embeddings?.map { it.toFloatArray() }
        val blob = floatArrays?.toByteArray()

        val entity = MemoryEntity(
            assistantId = assistantId,
            content = content,
            embedding = null,
            embeddingBlob = blob,
            embeddingModelId = embeddingResult?.modelId,
            type = MemoryType.CORE,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )
        
        val id = memoryDAO.insertMemory(entity)
        
        if (embeddingResult != null && blob != null && floatArrays != null) {
             val modelId = embeddingResult.modelId
             embeddingCacheDAO.insertEmbedding(
                EmbeddingCacheEntity(
                    memoryId = id.toInt(),
                    memoryType = MemoryType.CORE,
                    modelId = modelId,
                    embedding = "",
                    embeddingBlob = blob
                )
             )
             embeddingCache["${MemoryType.CORE}:${id.toInt()}:$modelId"] = floatArrays
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
        if (id < 0) error("Cannot delete episodic memories via tool — they are auto-managed")
        memoryDAO.deleteMemory(id)
        embeddingCacheDAO.deleteByMemoryId(id, MemoryType.CORE)
        embeddingCache.keys.removeAll { it.startsWith("${MemoryType.CORE}:$id:") }
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
    ): List<Pair<AssistantMemory, Float>> = coroutineScope {
        val queryEmbedding = try {
            embeddingService.embed(query, assistantId).toFloatArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return@coroutineScope emptyList()
        }

        // Fetch a reasonable number of candidates (limit * 20, max 1000) to avoid OOM
        val fetchLimit = (limit * 20).coerceAtMost(1000)

        // Get both core memories and episodes with limit
        val memories = if (includeCore) memoryDAO.getMemoriesOfAssistantLimited(assistantId, fetchLimit) else emptyList()
        val episodes = if (includeEpisodes) chatEpisodeDAO.getEpisodesOfAssistantLimited(assistantId, fetchLimit) else emptyList()
        
        val memoryDeferred = memories.map { memory ->
            async {
                val embeddings = getOrCreateEmbeddings(
                    memoryId = memory.id,
                    memoryType = MemoryType.CORE,
                    content = memory.content,
                    assistantId = assistantId,
                    existingEmbedding = memory.embedding,
                    existingBlob = memory.embeddingBlob,
                    existingModelId = memory.embeddingModelId
                ) ?: return@async null
                
                val similarity = embeddings.maxOfOrNull { VectorEngine.cosineSimilarity(queryEmbedding, it) } ?: 0f
                val keywordScore = calculateKeywordScore(query, memory.content)
                val combinedScore = (similarity * 0.8f) + (keywordScore * 0.2f)
                
                val score = (combinedScore * 1.05f) + 0.05f
                
                if (score >= similarityThreshold) {
                    Triple(memory, score, true)
                } else null
            }
        }
        
        val episodeDeferred = episodes.map { episode ->
            async {
                val embeddings = getOrCreateEmbeddings(
                    memoryId = episode.id,
                    memoryType = MemoryType.EPISODIC,
                    content = episode.content,
                    assistantId = assistantId,
                    existingEmbedding = episode.embedding,
                    existingBlob = episode.embeddingBlob,
                    existingModelId = episode.embeddingModelId
                ) ?: return@async null
                
                val similarity = embeddings.maxOfOrNull { VectorEngine.cosineSimilarity(queryEmbedding, it) } ?: 0f
                val keywordScore = calculateKeywordScore(query, episode.content)
                val combinedScore = (similarity * 0.8f) + (keywordScore * 0.2f)
                
                val ageInMillis = System.currentTimeMillis() - episode.startTime
                val ageInDays = ageInMillis / (1000.0 * 60 * 60 * 24)
                val recency = (1.0 / (1.0 + (ageInDays / 7.0))).toFloat()
                
                val score = (combinedScore * 0.7f) + (recency * 0.3f)
                
                if (score >= similarityThreshold) {
                    Triple(episode as Any, score, false)
                } else null
            }
        }
        
        val memoryScores = memoryDeferred.awaitAll().filterNotNull()
        val episodeScores = episodeDeferred.awaitAll().filterNotNull()
        
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
        
        allScored.take(limit).mapNotNull { triple ->
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
                val embeddingJson = embeddingCacheDAO.getEmbedding(memory.id, MemoryType.CORE, currentModelId)?.embedding
                    ?: JsonInstant.encodeToString(embeddingService.embed(memory.content, assistantId))
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
                val embeddingJson = embeddingCacheDAO.getEmbedding(episode.id, MemoryType.EPISODIC, currentModelId)?.embedding
                    ?: JsonInstant.encodeToString(embeddingService.embed(episode.content, assistantId))
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
                val embeddingJson = embeddingCacheDAO.getEmbedding(memory.id, MemoryType.CORE, currentModelId)?.embedding
                    ?: JsonInstant.encodeToString(embeddingService.embed(memory.content, assistantId))
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
                val embeddingJson = embeddingCacheDAO.getEmbedding(episode.id, MemoryType.EPISODIC, currentModelId)?.embedding
                    ?: JsonInstant.encodeToString(embeddingService.embed(episode.content, assistantId))
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
