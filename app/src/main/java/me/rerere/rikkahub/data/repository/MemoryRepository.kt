package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.EmbeddingUnavailableException
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
import me.rerere.rikkahub.data.ai.rag.decodeStoredEmbedding
import kotlinx.coroutines.coroutineScope
import androidx.room.withTransaction
import me.rerere.ai.memory.MemoryVectorMath
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.db.AppDatabase

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val chatEpisodeDAO: ChatEpisodeDAO,
    private val embeddingService: EmbeddingService,
    private val embeddingCacheDAO: EmbeddingCacheDAO,
    private val database: AppDatabase,
) {
    private val embeddingCache = java.util.concurrent.ConcurrentHashMap<String, List<FloatArray>>()

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content, it.type, !it.embedding.isNullOrBlank() || it.embeddingBlob != null, it.embeddingModelId, it.createdAt) }
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
                AssistantMemory(it.id, it.content, it.type, !it.embedding.isNullOrBlank() || it.embeddingBlob != null, it.embeddingModelId, it.createdAt)
            }
            val episodicMemories = episodes.map { 
                AssistantMemory(
                    id = -it.id,
                    content = it.content,
                    type = MemoryType.EPISODIC,
                    hasEmbedding = !it.embedding.isNullOrBlank() || it.embeddingBlob != null,
                    embeddingModelId = it.embeddingModelId,
                    timestamp = it.startTime,
                    significance = it.significance,
                )
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
            .map { AssistantMemory(it.id, it.content, it.type, !it.embedding.isNullOrBlank() || it.embeddingBlob != null, it.embeddingModelId, it.createdAt) }
    }

    suspend fun getMemoryById(id: Int): AssistantMemory? {
        val memory = memoryDAO.getMemoryById(id) ?: return null
        return AssistantMemory(
            id = memory.id,
            content = memory.content,
            type = memory.type,
            hasEmbedding = !memory.embedding.isNullOrBlank() || memory.embeddingBlob != null,
            embeddingModelId = memory.embeddingModelId,
            timestamp = memory.createdAt
        )
    }

    suspend fun getMemoryEntitiesOfAssistantLimited(assistantId: String, limit: Int): List<MemoryEntity> {
        return memoryDAO.getMemoriesOfAssistantLimited(assistantId, limit)
    }

    suspend fun getEpisodeEntitiesOfAssistant(assistantId: String): List<ChatEpisodeEntity> {
        return chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
    }

    suspend fun getEpisodeCount(): Int = chatEpisodeDAO.getCount()

    fun getEpisodeCountFlow(): Flow<Int> = chatEpisodeDAO.getCountFlow()

    /**
     * Read a valid current-model embedding from the entity or persistent cache.
     * Retrieval never starts background repair work or fans out provider calls.
     */
    private fun decodeEmbeddings(embedding: String?, blob: ByteArray?): List<FloatArray>? {
        return try {
            decodeStoredEmbedding(embedding, blob) { legacyJson ->
                JsonInstant.decodeFromString<List<Float>>(legacyJson).toFloatArray()
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            PlatformLog.w(TAG, "Ignoring malformed stored embedding: ${throwable.message}")
            null
        }
    }

    suspend fun getCombinedMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        val core = getMemoriesOfAssistant(assistantId)
        val episodic = chatEpisodeDAO.getEpisodesOfAssistant(assistantId).map { episode ->
            AssistantMemory(
                id = -episode.id,
                content = episode.content,
                type = MemoryType.EPISODIC,
                hasEmbedding = !episode.embedding.isNullOrBlank() || episode.embeddingBlob != null,
                embeddingModelId = episode.embeddingModelId,
                timestamp = episode.startTime,
                significance = episode.significance,
            )
        }
        return core + episodic
    }

    private suspend fun getStoredEmbeddings(
        memoryId: Int,
        memoryType: Int,
        assistantId: String,
        existingEmbedding: String? = null,
        existingBlob: ByteArray? = null,
        existingModelId: String? = null
    ): List<FloatArray>? {
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        val cacheKey = "$memoryType:$memoryId:$modelId"

        embeddingCache[cacheKey]?.let { return it }

        if (existingModelId == modelId) {
            decodeEmbeddings(existingEmbedding, existingBlob)?.let { list ->
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
            }
        }

        val cached = embeddingCacheDAO.getEmbedding(memoryId, memoryType, modelId)
        val cachedVectors = cached?.let { decodeEmbeddings(it.embedding, it.embeddingBlob) }
        if (cachedVectors != null) {
            val normalizedBlob = cachedVectors.toByteArray()
            embeddingCache[cacheKey] = cachedVectors
            embeddingCacheDAO.insertEmbedding(
                EmbeddingCacheEntity(
                    memoryId = memoryId,
                    memoryType = memoryType,
                    modelId = modelId,
                    embedding = "",
                    embeddingBlob = normalizedBlob,
                )
            )
            return cachedVectors
        }
        return null
    }

    /**
     * Check if an embedding exists in cache for the current model.
     */

    private fun calculateKeywordScore(query: String, content: String): Float {
        return MemoryVectorMath.keywordScore(query, content)
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        val (memoryIds, episodeIds) = database.withTransaction {
            val memoryIds = memoryDAO.getMemoriesOfAssistant(assistantId).map { it.id }
            val episodeIds = chatEpisodeDAO.getEpisodesOfAssistant(assistantId).map { it.id }
            memoryDAO.deleteMemoriesOfAssistant(assistantId)
            chatEpisodeDAO.deleteEpisodesOfAssistant(assistantId)
            memoryIds.forEach { id ->
                embeddingCacheDAO.deleteByMemoryId(id, MemoryType.CORE)
            }
            episodeIds.forEach { id ->
                embeddingCacheDAO.deleteByMemoryId(id, MemoryType.EPISODIC)
            }
            memoryIds to episodeIds
        }
        removeCachedEmbeddings(memoryIds, MemoryType.CORE)
        removeCachedEmbeddings(episodeIds, MemoryType.EPISODIC)
    }

    suspend fun deleteEpisode(id: Int) {
        database.withTransaction {
            chatEpisodeDAO.deleteEpisode(id)
            embeddingCacheDAO.deleteByMemoryId(id, MemoryType.EPISODIC)
        }
        removeCachedEmbeddings(listOf(id), MemoryType.EPISODIC)
    }

    suspend fun deleteEpisodesByConversationId(conversationId: String): Int {
        val episodeIds = database.withTransaction {
            val ids = chatEpisodeDAO.getEpisodeIdsByConversationId(conversationId)
            if (ids.isNotEmpty()) {
                chatEpisodeDAO.deleteEpisodeByConversationId(conversationId)
                ids.forEach { id ->
                    embeddingCacheDAO.deleteByMemoryId(id, MemoryType.EPISODIC)
                }
            }
            ids
        }
        removeCachedEmbeddings(episodeIds, MemoryType.EPISODIC)
        return episodeIds.size
    }

    suspend fun deleteLegacyEpisodesForConversation(
        assistantId: String,
        conversationStartTime: Long,
    ): Int {
        val episodeIds = database.withTransaction {
            val ids = chatEpisodeDAO.getLegacyEpisodeIdsForConversation(
                assistantId = assistantId,
                conversationStartTime = conversationStartTime,
            )
            ids.forEach { id ->
                chatEpisodeDAO.deleteEpisode(id)
                embeddingCacheDAO.deleteByMemoryId(id, MemoryType.EPISODIC)
            }
            ids
        }
        removeCachedEmbeddings(episodeIds, MemoryType.EPISODIC)
        return episodeIds.size
    }

    private fun removeCachedEmbeddings(ids: Collection<Int>, memoryType: Int) {
        if (ids.isEmpty()) return
        val idSet = ids.toHashSet()
        embeddingCache.keys.removeAll { key ->
            val parts = key.split(':', limit = 3)
            parts.size == 3 &&
                parts[0].toIntOrNull() == memoryType &&
                parts[1].toIntOrNull() in idSet
        }
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val memory = memoryDAO.getMemoryById(id) ?: error("Memory not found")
        val newMemory = memory.copy(content = content, embedding = null, embeddingBlob = null, embeddingModelId = null)
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
        val newEpisode = episode.copy(content = content, embedding = null, embeddingBlob = null, embeddingModelId = null)
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
            if (e is CancellationException) throw e
            PlatformLog.w(TAG, "Core memory saved without an embedding: ${e.message}")
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
        
        if (embeddingResult != null) {
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
             embeddingCache["${MemoryType.CORE}:${id.toInt()}:$modelId"] =
                 embeddingResult.embeddings.map { it.toFloatArray() }
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
        val safeLimit = limit.coerceIn(1, MAX_RETRIEVAL_LIMIT)
        val outcome = retrieveRelevantMemoriesOutcome(
            assistantId, query, safeLimit, similarityThreshold, includeCore, includeEpisodes
        )
        val matches = outcome.matches.map { it.first }
        if (matches.isNotEmpty()) return matches
        if (outcome.vectorPathAvailable) return emptyList()

        // A provider outage, stale/incompatible vectors, or unavailable local runtime must not turn
        // a healthy durable memory store into an empty context forever. Do not use this fallback
        // merely because healthy vector search found no relevant match.
        val fallback = durableRecallFallback(
            assistantId = assistantId,
            limit = minOf(safeLimit, DURABLE_FALLBACK_LIMIT),
            includeCore = includeCore,
            includeEpisodes = includeEpisodes,
        )
        if (fallback.isNotEmpty()) {
            PlatformLog.w(
                TAG,
                "Vector recall unavailable; using ${fallback.size} durable fallback memories",
            )
        }
        return fallback
    }

    suspend fun retrieveRelevantMemoriesWithScores(
        assistantId: String,
        query: String,
        limit: Int = 5,
        similarityThreshold: Float = 0.5f,
        includeCore: Boolean = true,
        includeEpisodes: Boolean = true
    ): List<Pair<AssistantMemory, Float>> = retrieveRelevantMemoriesOutcome(
        assistantId = assistantId,
        query = query,
        limit = limit,
        similarityThreshold = similarityThreshold,
        includeCore = includeCore,
        includeEpisodes = includeEpisodes,
    ).matches

    private suspend fun retrieveRelevantMemoriesOutcome(
        assistantId: String,
        query: String,
        limit: Int,
        similarityThreshold: Float,
        includeCore: Boolean,
        includeEpisodes: Boolean,
    ): RecallOutcome = coroutineScope {
        val safeLimit = limit.coerceIn(1, MAX_RETRIEVAL_LIMIT)
        val queryEmbedding = try {
            embeddingService.embed(query, assistantId).toFloatArray()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            PlatformLog.w(TAG, "Vector query unavailable; using lexical memory retrieval: ${e.message}")
            null
        }

        // Fetch a reasonable number of candidates (limit * 20, max 1000) to avoid OOM
        val fetchLimit = (safeLimit * 20).coerceAtMost(1000)

        // Get both core memories and episodes with limit
        val memories = if (includeCore) memoryDAO.getMemoriesOfAssistantLimited(assistantId, fetchLimit) else emptyList()
        val episodes = if (includeEpisodes) chatEpisodeDAO.getEpisodesOfAssistantLimited(assistantId, fetchLimit) else emptyList()
        var hasCompatibleStoredEmbedding = false
        
        val memoryScores = memories.mapNotNull { memory ->
                val embeddings = if (queryEmbedding != null) getStoredEmbeddings(
                    memoryId = memory.id,
                    memoryType = MemoryType.CORE,
                    assistantId = assistantId,
                    existingEmbedding = memory.embedding,
                    existingBlob = memory.embeddingBlob,
                    existingModelId = memory.embeddingModelId
                ) else null

                val compatibleEmbeddings = if (queryEmbedding != null) {
                    embeddings.orEmpty().filter { it.isNotEmpty() && it.size == queryEmbedding.size }
                } else {
                    emptyList()
                }
                if (compatibleEmbeddings.isNotEmpty()) hasCompatibleStoredEmbedding = true
                val similarity = if (queryEmbedding != null) {
                    compatibleEmbeddings.maxOfOrNull { VectorEngine.cosineSimilarity(queryEmbedding, it) }
                } else null
                val keywordScore = calculateKeywordScore(query, memory.content)
                if (similarity == null && keywordScore <= 0f) return@mapNotNull null
                val score = if (similarity != null) {
                    (((similarity * 0.8f) + (keywordScore * 0.2f)) * 1.05f) + 0.05f
                } else {
                    0.1f + (keywordScore * 0.9f)
                }
                
                if (MemoryVectorMath.passesRecallThreshold(score, keywordScore, similarityThreshold)) {
                    Triple(memory, score, true)
                } else null
        }
        
        val episodeScores = episodes.mapNotNull { episode ->
                val embeddings = if (queryEmbedding != null) getStoredEmbeddings(
                    memoryId = episode.id,
                    memoryType = MemoryType.EPISODIC,
                    assistantId = assistantId,
                    existingEmbedding = episode.embedding,
                    existingBlob = episode.embeddingBlob,
                    existingModelId = episode.embeddingModelId
                ) else null

                val compatibleEmbeddings = if (queryEmbedding != null) {
                    embeddings.orEmpty().filter { it.isNotEmpty() && it.size == queryEmbedding.size }
                } else {
                    emptyList()
                }
                if (compatibleEmbeddings.isNotEmpty()) hasCompatibleStoredEmbedding = true
                val similarity = if (queryEmbedding != null) {
                    compatibleEmbeddings.maxOfOrNull { VectorEngine.cosineSimilarity(queryEmbedding, it) }
                } else null
                val keywordScore = calculateKeywordScore(query, episode.content)
                if (similarity == null && keywordScore <= 0f) return@mapNotNull null
                val combinedScore = if (similarity != null) {
                    (similarity * 0.8f) + (keywordScore * 0.2f)
                } else {
                    keywordScore
                }
                
                val ageInMillis = System.currentTimeMillis() - episode.startTime
                val ageInDays = ageInMillis / (1000.0 * 60 * 60 * 24)
                val recency = (1.0 / (1.0 + (ageInDays / 7.0))).toFloat()
                
                val score = (combinedScore * 0.7f) + (recency * 0.3f)
                
                if (MemoryVectorMath.passesRecallThreshold(score, keywordScore, similarityThreshold)) {
                    Triple(episode as Any, score, false)
                } else null
        }
        
        // Combine and sort by score
        val allScored = (memoryScores + episodeScores).sortedByDescending { it.second }
        
        // Update lastAccessedAt for retrieved memories
        allScored.take(safeLimit).forEach { (item, _, isMemory) ->
            if (isMemory) {
                val memory = item as MemoryEntity
                memoryDAO.updateMemory(memory.copy(lastAccessedAt = System.currentTimeMillis()))
            } else {
                val episode = item as ChatEpisodeEntity
                chatEpisodeDAO.insertEpisode(episode.copy(lastAccessedAt = System.currentTimeMillis()))
            }
        }
        
        val matches = allScored.take(safeLimit).mapNotNull { triple ->
            val item = triple.first
            val score = triple.second
            val isMemory = triple.third

            if (isMemory) {
                val memory = item as MemoryEntity
                Pair<AssistantMemory, Float>(AssistantMemory(memory.id, memory.content, memory.type, !memory.embedding.isNullOrBlank() || memory.embeddingBlob != null, memory.embeddingModelId, memory.createdAt), score)
            } else {
                val episode = item as ChatEpisodeEntity
                // Convert episode to AssistantMemory with a negative ID to distinguish
                Pair<AssistantMemory, Float>(
                    AssistantMemory(
                        id = -episode.id,
                        content = episode.content,
                        type = MemoryType.EPISODIC,
                        hasEmbedding = !episode.embedding.isNullOrBlank() || episode.embeddingBlob != null,
                        embeddingModelId = episode.embeddingModelId,
                        timestamp = episode.startTime,
                        significance = episode.significance,
                    ),
                    score,
                )
            }
        }
        RecallOutcome(
            matches = matches,
            vectorPathAvailable = queryEmbedding != null && hasCompatibleStoredEmbedding,
        )
    }

    private suspend fun durableRecallFallback(
        assistantId: String,
        limit: Int,
        includeCore: Boolean,
        includeEpisodes: Boolean,
    ): List<AssistantMemory> {
        if (limit <= 0 || (!includeCore && !includeEpisodes)) return emptyList()
        val core = if (includeCore) {
            memoryDAO.getMemoriesOfAssistantLimited(assistantId, limit)
                .map { memory ->
                    AssistantMemory(
                        id = memory.id,
                        content = memory.content,
                        type = memory.type,
                        hasEmbedding = !memory.embedding.isNullOrBlank() || memory.embeddingBlob != null,
                        embeddingModelId = memory.embeddingModelId,
                        timestamp = memory.createdAt,
                    )
                }
        } else {
            emptyList()
        }
        val episodes = if (includeEpisodes) {
            chatEpisodeDAO.getEpisodesOfAssistantLimited(assistantId, limit)
                .map { episode ->
                    AssistantMemory(
                        id = -episode.id,
                        content = episode.content,
                        type = MemoryType.EPISODIC,
                        hasEmbedding = !episode.embedding.isNullOrBlank() || episode.embeddingBlob != null,
                        embeddingModelId = episode.embeddingModelId,
                        timestamp = episode.startTime,
                        significance = episode.significance,
                    )
                }
        } else {
            emptyList()
        }

        // Keep both stores represented when possible, then fill the remaining small safety budget
        // by recency. This is intentionally bounded so fallback cannot swamp normal chat context.
        val selected = mutableListOf<AssistantMemory>()
        core.firstOrNull()?.let(selected::add)
        episodes.firstOrNull()?.let(selected::add)
        (core.drop(1) + episodes.drop(1))
            .sortedByDescending { it.timestamp }
            .forEach { memory ->
                if (selected.size < limit) selected += memory
            }
        return selected.take(limit)
    }

    private data class StoredEmbedding(val modelId: String, val vectors: List<FloatArray>)
    private data class RecallOutcome(
        val matches: List<Pair<AssistantMemory, Float>>,
        val vectorPathAvailable: Boolean,
    )

    private suspend fun buildEmbedding(content: String, assistantId: String): StoredEmbedding {
        val result = embeddingService.embedBatch(MemoryChunker.chunkText(content), assistantId)
        return StoredEmbedding(result.modelId, result.embeddings.map { it.toFloatArray() })
    }

    private suspend fun persistCoreEmbedding(memory: MemoryEntity, stored: StoredEmbedding) {
        val blob = stored.vectors.toByteArray()
        database.withTransaction {
            val latest = memoryDAO.getMemoryById(memory.id)
                ?: throw IllegalStateException("Core memory ${memory.id} was deleted during embedding")
            if (latest.content != memory.content) {
                throw IllegalStateException("Core memory ${memory.id} changed during embedding")
            }
            memoryDAO.updateMemory(
                latest.copy(embedding = null, embeddingBlob = blob, embeddingModelId = stored.modelId)
            )
            embeddingCacheDAO.insertEmbedding(
                EmbeddingCacheEntity(
                    memoryId = memory.id,
                    memoryType = MemoryType.CORE,
                    modelId = stored.modelId,
                    embedding = "",
                    embeddingBlob = blob,
                )
            )
        }
        embeddingCache["${MemoryType.CORE}:${memory.id}:${stored.modelId}"] = stored.vectors
    }

    private suspend fun persistEpisodeEmbedding(episode: ChatEpisodeEntity, stored: StoredEmbedding) {
        val blob = stored.vectors.toByteArray()
        database.withTransaction {
            val latest = chatEpisodeDAO.getEpisodeById(episode.id)
                ?: throw IllegalStateException("Episodic memory ${episode.id} was deleted during embedding")
            if (latest.content != episode.content) {
                throw IllegalStateException("Episodic memory ${episode.id} changed during embedding")
            }
            chatEpisodeDAO.insertEpisode(
                latest.copy(embedding = null, embeddingBlob = blob, embeddingModelId = stored.modelId)
            )
            embeddingCacheDAO.insertEmbedding(
                EmbeddingCacheEntity(
                    memoryId = episode.id,
                    memoryType = MemoryType.EPISODIC,
                    modelId = stored.modelId,
                    embedding = "",
                    embeddingBlob = blob,
                )
            )
        }
        embeddingCache["${MemoryType.EPISODIC}:${episode.id}:${stored.modelId}"] = stored.vectors
    }

    private suspend fun cachedEmbedding(
        id: Int,
        type: Int,
        contentEmbedding: String?,
        contentBlob: ByteArray?,
        contentModelId: String?,
        assistantId: String,
    ): StoredEmbedding? {
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        val vectors = getStoredEmbeddings(
            memoryId = id,
            memoryType = type,
            assistantId = assistantId,
            existingEmbedding = contentEmbedding,
            existingBlob = contentBlob,
            existingModelId = contentModelId,
        ) ?: return null
        return StoredEmbedding(modelId, vectors)
    }

    /** Force-rebuilds every Core and Episodic embedding with the currently selected model. */
    suspend fun regenerateEmbeddings(
        assistantId: String,
        onProgress: (Int, Int) -> Unit
    ): Pair<Int, Int> {
        val allMemories = memoryDAO.getMemoriesOfAssistant(assistantId)
        val allEpisodes = chatEpisodeDAO.getEpisodesOfAssistant(assistantId)
        val total = allMemories.size + allEpisodes.size
        var current = 0
        var successCount = 0
        var failureCount = 0
        var providerFailure: Exception? = null

        onProgress(0, total)
        if (total == 0) return 0 to 0

        allMemories.forEach { memory ->
            current++
            try {
                providerFailure?.let { throw it }
                persistCoreEmbedding(memory, buildEmbedding(memory.content, assistantId))
                successCount++
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is EmbeddingUnavailableException) providerFailure = providerFailure ?: e
                PlatformLog.w(TAG, "Unable to rebuild Core memory ${memory.id}: ${e.message}")
                failureCount++
            }
            onProgress(current, total)
        }

        allEpisodes.forEach { episode ->
            current++
            try {
                providerFailure?.let { throw it }
                persistEpisodeEmbedding(episode, buildEmbedding(episode.content, assistantId))
                successCount++
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is EmbeddingUnavailableException) providerFailure = providerFailure ?: e
                PlatformLog.w(TAG, "Unable to rebuild Episodic memory ${episode.id}: ${e.message}")
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
        var providerFailure: Exception? = null

        // Filter to only memories that need embedding
        val memoriesNeedingEmbedding = memories.filter { 
            decodeEmbeddings(it.embedding, it.embeddingBlob) == null || it.embeddingModelId != currentModelId
        }
        val episodesNeedingEmbedding = episodes.filter { 
            decodeEmbeddings(it.embedding, it.embeddingBlob) == null || it.embeddingModelId != currentModelId
        }

        memoriesNeedingEmbedding.forEach { memory ->
            try {
                val cached = cachedEmbedding(
                    memory.id, MemoryType.CORE, memory.embedding, memory.embeddingBlob,
                    memory.embeddingModelId, assistantId,
                )
                val stored = cached ?: if (providerFailure == null) {
                    buildEmbedding(memory.content, assistantId)
                } else {
                    throw providerFailure
                }
                persistCoreEmbedding(memory, stored)
                successCount++
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is EmbeddingUnavailableException) providerFailure = providerFailure ?: e
                PlatformLog.w(TAG, "Unable to repair Core memory ${memory.id}: ${e.message}")
                failureCount++
            }
        }

        episodesNeedingEmbedding.forEach { episode ->
            try {
                val cached = cachedEmbedding(
                    episode.id, MemoryType.EPISODIC, episode.embedding, episode.embeddingBlob,
                    episode.embeddingModelId, assistantId,
                )
                val stored = cached ?: if (providerFailure == null) {
                    buildEmbedding(episode.content, assistantId)
                } else {
                    throw providerFailure
                }
                persistEpisodeEmbedding(episode, stored)
                successCount++
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is EmbeddingUnavailableException) providerFailure = providerFailure ?: e
                PlatformLog.w(TAG, "Unable to repair Episodic memory ${episode.id}: ${e.message}")
                failureCount++
            }
        }
        
        return successCount to failureCount
    }

    private companion object {
        const val TAG = "MemoryRepository"
        const val MAX_RETRIEVAL_LIMIT = 1_000
        const val DURABLE_FALLBACK_LIMIT = 3
    }
}
