package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.ai.rag.EmbeddingResult
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.db.entity.EmbeddingCacheEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {
    @Test
    fun retrieveRelevantMemoriesWithScores_skipsEpisodesBelowSimilarityFloor() = runBlocking {
        val memoryDao = FakeMemoryDao(
            listOf(
                memoryEntity(
                    id = 1,
                    assistantId = ASSISTANT_ID,
                    content = "User likes espresso",
                    embedding = listOf(1f, 0f),
                )
            )
        )
        val episodeDao = FakeChatEpisodeDao(
            listOf(
                episodeEntity(
                    id = 2,
                    assistantId = ASSISTANT_ID,
                    content = "Talked about a random movie",
                    embedding = listOf(0f, 1f),
                    startTime = NOW,
                    significance = 10,
                )
            )
        )
        val cacheDao = FakeEmbeddingCacheDao()
        val repository = MemoryRepository(
            memoryDAO = memoryDao,
            chatEpisodeDAO = episodeDao,
            embeddingGateway = FakeEmbeddingGateway(
                modelId = MODEL_ID,
                embeddingsByText = mapOf("espresso query" to listOf(1f, 0f)),
            ),
            embeddingCacheDAO = cacheDao,
        )

        val results = repository.retrieveRelevantMemoriesWithScores(
            assistantId = ASSISTANT_ID,
            query = "espresso query",
            limit = 5,
            similarityThreshold = 0f,
            includeCore = true,
            includeEpisodes = true,
        )

        assertEquals(1, results.size)
        assertEquals(1, results.first().first.id)
        assertTrue(cacheDao.getAllEmbeddings().any { it.memoryId == 1 && it.memoryType == MemoryType.CORE })
        assertTrue(cacheDao.getAllEmbeddings().any { it.memoryId == 2 && it.memoryType == MemoryType.EPISODIC })
    }

    @Test
    fun retrieveRelevantMemoriesWithScores_prefersHigherSignificanceWhenSimilarityMatches() = runBlocking {
        val memoryDao = FakeMemoryDao()
        val episodeDao = FakeChatEpisodeDao(
            listOf(
                episodeEntity(
                    id = 1,
                    assistantId = ASSISTANT_ID,
                    content = "High significance episode",
                    embedding = listOf(1f, 0f),
                    startTime = NOW,
                    significance = 10,
                ),
                episodeEntity(
                    id = 2,
                    assistantId = ASSISTANT_ID,
                    content = "Low significance episode",
                    embedding = listOf(1f, 0f),
                    startTime = NOW,
                    significance = 1,
                ),
            )
        )
        val repository = MemoryRepository(
            memoryDAO = memoryDao,
            chatEpisodeDAO = episodeDao,
            embeddingGateway = FakeEmbeddingGateway(
                modelId = MODEL_ID,
                embeddingsByText = mapOf("important query" to listOf(1f, 0f)),
            ),
            embeddingCacheDAO = FakeEmbeddingCacheDao(),
        )

        val results = repository.retrieveRelevantMemoriesWithScores(
            assistantId = ASSISTANT_ID,
            query = "important query",
            limit = 2,
            similarityThreshold = 0f,
            includeCore = false,
            includeEpisodes = true,
        )

        assertEquals(listOf(-1, -2), results.map { it.first.id })
        assertTrue(results.first().second > results.last().second)
    }

    @Test
    fun deleteMemoriesOfAssistant_clearsCoreAndEpisodeCaches() = runBlocking {
        val memoryDao = FakeMemoryDao(
            listOf(
                memoryEntity(
                    id = 1,
                    assistantId = ASSISTANT_ID,
                    content = "Core memory",
                    embedding = listOf(1f, 0f),
                )
            )
        )
        val episodeDao = FakeChatEpisodeDao(
            listOf(
                episodeEntity(
                    id = 2,
                    assistantId = ASSISTANT_ID,
                    content = "Episode memory",
                    embedding = listOf(1f, 0f),
                    startTime = NOW,
                    significance = 5,
                )
            )
        )
        val cacheDao = FakeEmbeddingCacheDao(
            listOf(
                EmbeddingCacheEntity(
                    memoryId = 1,
                    memoryType = MemoryType.CORE,
                    modelId = MODEL_ID,
                    embedding = JsonInstant.encodeToString(listOf(1f, 0f)),
                ),
                EmbeddingCacheEntity(
                    memoryId = 2,
                    memoryType = MemoryType.EPISODIC,
                    modelId = MODEL_ID,
                    embedding = JsonInstant.encodeToString(listOf(1f, 0f)),
                ),
            )
        )
        val repository = MemoryRepository(
            memoryDAO = memoryDao,
            chatEpisodeDAO = episodeDao,
            embeddingGateway = FakeEmbeddingGateway(modelId = MODEL_ID),
            embeddingCacheDAO = cacheDao,
        )

        repository.deleteMemoriesOfAssistant(ASSISTANT_ID)

        assertTrue(memoryDao.getMemoriesOfAssistant(ASSISTANT_ID).isEmpty())
        assertTrue(episodeDao.getEpisodesOfAssistant(ASSISTANT_ID).isEmpty())
        assertTrue(cacheDao.getAllEmbeddings().isEmpty())
    }

    private fun memoryEntity(
        id: Int,
        assistantId: String,
        content: String,
        embedding: List<Float>,
    ): MemoryEntity {
        return MemoryEntity(
            id = id,
            assistantId = assistantId,
            content = content,
            embedding = JsonInstant.encodeToString(embedding),
            embeddingModelId = MODEL_ID,
            createdAt = NOW,
            lastAccessedAt = NOW,
        )
    }

    private fun episodeEntity(
        id: Int,
        assistantId: String,
        content: String,
        embedding: List<Float>,
        startTime: Long,
        significance: Int,
    ): ChatEpisodeEntity {
        return ChatEpisodeEntity(
            id = id,
            assistantId = assistantId,
            content = content,
            embedding = JsonInstant.encodeToString(embedding),
            embeddingModelId = MODEL_ID,
            startTime = startTime,
            endTime = startTime,
            lastAccessedAt = startTime,
            significance = significance,
            conversationId = "conv-$id",
        )
    }

    private class FakeEmbeddingGateway(
        private val modelId: String,
        private val embeddingsByText: Map<String, List<Float>> = emptyMap(),
    ) : MemoryEmbeddingGateway {
        override fun getEmbeddingModelId(assistantId: String?): String = modelId

        override suspend fun embed(text: String, assistantId: String?): List<Float> {
            return embeddingsByText[text] ?: error("No embedding registered for '$text'")
        }

        override suspend fun embedWithModelId(text: String, assistantId: String?): EmbeddingResult {
            return EmbeddingResult(listOf(embed(text, assistantId)), modelId)
        }
    }

    private class FakeMemoryDao(
        initialMemories: List<MemoryEntity> = emptyList(),
    ) : MemoryDAO {
        private val memories = initialMemories.toMutableList()
        private val flow = MutableStateFlow(memories.toList())
        private var nextId = (initialMemories.maxOfOrNull { it.id } ?: 0) + 1

        override fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>> = flow

        override suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity> {
            return memories
                .filter { it.assistantId == assistantId }
                .sortedWith(compareByDescending<MemoryEntity> { it.createdAt }.thenByDescending { it.id })
        }

        override suspend fun getMemoryById(id: Int): MemoryEntity? = memories.firstOrNull { it.id == id }

        override suspend fun insertMemory(memory: MemoryEntity): Long {
            val assignedId = if (memory.id == 0) nextId++ else memory.id
            memories.removeAll { it.id == assignedId }
            memories.add(memory.copy(id = assignedId))
            emit()
            return assignedId.toLong()
        }

        override suspend fun updateMemory(memory: MemoryEntity) {
            memories.replaceAll { if (it.id == memory.id) memory else it }
            emit()
        }

        override suspend fun deleteMemory(id: Int) {
            memories.removeAll { it.id == id }
            emit()
        }

        override suspend fun deleteMemoriesOfAssistant(assistantId: String) {
            memories.removeAll { it.assistantId == assistantId }
            emit()
        }

        private fun emit() {
            flow.value = memories.toList()
        }
    }

    private class FakeChatEpisodeDao(
        initialEpisodes: List<ChatEpisodeEntity> = emptyList(),
    ) : ChatEpisodeDAO {
        private val episodes = initialEpisodes.toMutableList()
        private val flow = MutableStateFlow(episodes.sortedByDescending { it.endTime })
        private var nextId = (initialEpisodes.maxOfOrNull { it.id } ?: 0) + 1

        override suspend fun getEpisodesOfAssistant(assistantId: String): List<ChatEpisodeEntity> {
            return episodes.filter { it.assistantId == assistantId }.sortedByDescending { it.endTime }
        }

        override fun getEpisodesOfAssistantFlow(assistantId: String): Flow<List<ChatEpisodeEntity>> = flow

        override suspend fun insertEpisode(episode: ChatEpisodeEntity): Long {
            val assignedId = if (episode.id == 0) nextId++ else episode.id
            episodes.removeAll { it.id == assignedId }
            episodes.add(episode.copy(id = assignedId))
            emit()
            return assignedId.toLong()
        }

        override suspend fun deleteEpisode(id: Int) {
            episodes.removeAll { it.id == id }
            emit()
        }

        override suspend fun deleteEpisodesOfAssistant(assistantId: String) {
            episodes.removeAll { it.assistantId == assistantId }
            emit()
        }

        override suspend fun deleteEpisodeByTimeRange(assistantId: String, startTime: Long, endTime: Long) {
            episodes.removeAll {
                it.assistantId == assistantId && it.startTime >= startTime && it.endTime <= endTime
            }
            emit()
        }

        override suspend fun getCount(): Int = episodes.size

        override fun getCountFlow(): Flow<Int> = MutableStateFlow(episodes.size)

        override suspend fun deleteEpisodeByConversationId(conversationId: String): Int {
            val before = episodes.size
            episodes.removeAll { it.conversationId == conversationId }
            emit()
            return before - episodes.size
        }

        override suspend fun getEpisodesByConversationId(conversationId: String): List<ChatEpisodeEntity> {
            return episodes.filter { it.conversationId == conversationId }
        }

        override suspend fun getEpisodesByTimeRange(assistantId: String, startTime: Long, endTime: Long): List<ChatEpisodeEntity> {
            return episodes.filter {
                it.assistantId == assistantId && it.startTime >= startTime && it.endTime <= endTime
            }
        }

        override suspend fun getEpisodeByConversationId(conversationId: String): ChatEpisodeEntity? {
            return episodes.firstOrNull { it.conversationId == conversationId }
        }

        override suspend fun getEpisodeById(id: Int): ChatEpisodeEntity? {
            return episodes.firstOrNull { it.id == id }
        }

        private fun emit() {
            flow.value = episodes.sortedByDescending { it.endTime }
        }
    }

    private class FakeEmbeddingCacheDao(
        initialEmbeddings: List<EmbeddingCacheEntity> = emptyList(),
    ) : EmbeddingCacheDAO {
        private val embeddings = initialEmbeddings.toMutableList()
        private var nextId = (initialEmbeddings.maxOfOrNull { it.id } ?: 0) + 1

        override suspend fun getEmbedding(memoryId: Int, memoryType: Int, modelId: String): EmbeddingCacheEntity? {
            return embeddings.firstOrNull {
                it.memoryId == memoryId && it.memoryType == memoryType && it.modelId == modelId
            }
        }

        override suspend fun insertEmbedding(embedding: EmbeddingCacheEntity) {
            embeddings.removeAll {
                it.memoryId == embedding.memoryId &&
                    it.memoryType == embedding.memoryType &&
                    it.modelId == embedding.modelId
            }
            embeddings.add(embedding.copy(id = if (embedding.id == 0) nextId++ else embedding.id))
        }

        override suspend fun hasEmbedding(memoryId: Int, memoryType: Int, modelId: String): Boolean {
            return embeddings.any {
                it.memoryId == memoryId && it.memoryType == memoryType && it.modelId == modelId
            }
        }

        override suspend fun getEmbeddingsByModel(modelId: String): List<EmbeddingCacheEntity> {
            return embeddings.filter { it.modelId == modelId }
        }

        override suspend fun deleteByModelId(modelId: String) {
            embeddings.removeAll { it.modelId == modelId }
        }

        override suspend fun deleteByMemoryId(memoryId: Int, memoryType: Int) {
            embeddings.removeAll { it.memoryId == memoryId && it.memoryType == memoryType }
        }

        override suspend fun countEmbeddingsByModel(modelId: String): Int {
            return embeddings.count { it.modelId == modelId }
        }

        override suspend fun getAllEmbeddings(): List<EmbeddingCacheEntity> = embeddings.toList()
    }

    companion object {
        private const val ASSISTANT_ID = "assistant-1"
        private const val MODEL_ID = "model-1"
        private const val NOW = 1_700_000_000_000L
    }
}
