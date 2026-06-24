import os

repo_path = "app/src/main/java/me/rerere/rikkahub/data/repository/MemoryRepository.kt"
with open(repo_path, "r", encoding="utf-8") as f:
    content = f.read()

new_imports = """
import me.rerere.rikkahub.data.ai.rag.MemoryChunker
import me.rerere.rikkahub.data.ai.rag.toByteArray
import me.rerere.rikkahub.data.ai.rag.toFloatArray
import me.rerere.rikkahub.data.ai.rag.toListOfFloatArrays
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
"""
content = content.replace("import me.rerere.rikkahub.utils.JsonInstant\n", "import me.rerere.rikkahub.utils.JsonInstant\n" + new_imports)

cache_code = """
    private val embeddingCache = java.util.concurrent.ConcurrentHashMap<String, List<FloatArray>>()
    private val cacheInitializedModels = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private suspend fun ensureCacheLoaded(modelId: String) {
        if (cacheInitializedModels[modelId] == true) return
        val cachedEntities = embeddingCacheDAO.getEmbeddingsByModel(modelId)
        for (entity in cachedEntities) {
            val cacheKey = "${entity.memoryType}:${entity.memoryId}:$modelId"
            val blob = entity.embeddingBlob
            if (blob != null) {
                embeddingCache[cacheKey] = blob.toListOfFloatArrays()
            } else {
                try {
                    val floats = JsonInstant.decodeFromString<List<Float>>(entity.embedding).toFloatArray()
                    embeddingCache[cacheKey] = listOf(floats)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        cacheInitializedModels[modelId] = true
    }
"""

content = content.replace("class MemoryRepository(\n    private val memoryDAO: MemoryDAO,\n    private val chatEpisodeDAO: ChatEpisodeDAO,\n    private val embeddingService: EmbeddingService,\n    private val embeddingCacheDAO: EmbeddingCacheDAO\n) {", "class MemoryRepository(\n    private val memoryDAO: MemoryDAO,\n    private val chatEpisodeDAO: ChatEpisodeDAO,\n    private val embeddingService: EmbeddingService,\n    private val embeddingCacheDAO: EmbeddingCacheDAO\n) {" + cache_code)

content = content.replace("""    private suspend fun getOrCreateEmbedding(
        memoryId: Int,
        memoryType: Int,
        content: String,
        assistantId: String,
        existingEmbedding: String? = null,
        existingModelId: String? = null
    ): List<Float>? {""", """    private suspend fun getOrCreateEmbeddings(
        memoryId: Int,
        memoryType: Int,
        content: String,
        assistantId: String,
        existingEmbedding: String? = null,
        existingBlob: ByteArray? = null,
        existingModelId: String? = null
    ): List<FloatArray>? {""")

get_or_create_body = """
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        val cacheKey = "$memoryType:$memoryId:$modelId"

        ensureCacheLoaded(modelId)

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
    }"""

import re
content = re.sub(r'        val modelId = embeddingService\.getEmbeddingModelId\(assistantId\).*?catch \(e: Exception\) \{\n            e\.printStackTrace\(\)\n            null\n        \}\n    \}', get_or_create_body.strip(), content, flags=re.DOTALL)

# Add keyword search function
keyword_score_func = """
    private fun calculateKeywordScore(query: String, content: String): Float {
        val queryWords = query.lowercase().split(Regex("\\\\W+")).filter { it.isNotBlank() }
        if (queryWords.isEmpty()) return 0f
        val contentLower = content.lowercase()
        var matches = 0
        for (word in queryWords) {
            if (contentLower.contains(word)) matches++
        }
        return matches.toFloat() / queryWords.size.toFloat()
    }
"""
content = content.replace("    suspend fun hasEmbeddingForCurrentModel(memoryId: Int, memoryType: Int, assistantId: String): Boolean {", keyword_score_func + "\n    suspend fun hasEmbeddingForCurrentModel(memoryId: Int, memoryType: Int, assistantId: String): Boolean {")

# Update hasEmbeddingForCurrentModel to check cache
new_has_embedding = """
    suspend fun hasEmbeddingForCurrentModel(memoryId: Int, memoryType: Int, assistantId: String): Boolean {
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        val cacheKey = "$memoryType:$memoryId:$modelId"
        if (embeddingCache.containsKey(cacheKey)) return true
        return embeddingCacheDAO.hasEmbedding(memoryId, memoryType, modelId)
    }
"""
content = re.sub(r'    suspend fun hasEmbeddingForCurrentModel.*?return embeddingCacheDAO\.hasEmbedding.*?\}', new_has_embedding.strip(), content, flags=re.DOTALL)


# Update addMemory
old_add_memory = """    suspend fun addMemory(assistantId: String, content: String): AssistantMemory {
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
    }"""

new_add_memory = """    suspend fun addMemory(assistantId: String, content: String): AssistantMemory {
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
    }"""
content = content.replace(old_add_memory, new_add_memory)

# update retrieveRelevantMemoriesWithScores
old_retrieve = """    suspend fun retrieveRelevantMemoriesWithScores(
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
            val score = (similarity * 1.05f) + 0.05f
            
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
        }"""

new_retrieve = """    suspend fun retrieveRelevantMemoriesWithScores(
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

        // Get both core memories and episodes
        val memories = if (includeCore) memoryDAO.getMemoriesOfAssistant(assistantId) else emptyList()
        val episodes = if (includeEpisodes) chatEpisodeDAO.getEpisodesOfAssistant(assistantId) else emptyList()
        
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
        val episodeScores = episodeDeferred.awaitAll().filterNotNull()"""
content = content.replace(old_retrieve, new_retrieve)

with open(repo_path, "w", encoding="utf-8") as f:
    f.write(content)

print("MemoryRepository.kt rewritten successfully!")
