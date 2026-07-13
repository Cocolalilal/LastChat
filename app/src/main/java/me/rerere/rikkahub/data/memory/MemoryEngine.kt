package me.rerere.rikkahub.data.memory

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.rerere.ai.memory.BuiltInMemoryEngines
import me.rerere.ai.memory.MemoryEngineCapabilities
import me.rerere.ai.memory.MemoryOrigin
import me.rerere.ai.memory.MemoryScope
import me.rerere.ai.memory.MemoryScopeKind
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.resolvedMemoryEngineId
import me.rerere.rikkahub.data.repository.MemoryRepository

data class UnifiedMemoryHit(
    val engineId: String,
    val stableId: String,
    val content: String,
    val score: Float,
    val kind: String,
    val source: String,
    val timestamp: Long,
)

interface MemoryEngine {
    val id: String
    val capabilities: MemoryEngineCapabilities

    suspend fun search(assistant: Assistant, conversationId: String?, query: String, limit: Int): List<UnifiedMemoryHit>
}

class OffMemoryEngine : MemoryEngine {
    override val id = BuiltInMemoryEngines.OFF
    override val capabilities = MemoryEngineCapabilities(manualCrud = false, transfer = false)
    override suspend fun search(assistant: Assistant, conversationId: String?, query: String, limit: Int) =
        emptyList<UnifiedMemoryHit>()
}

class SimpleMemoryEngine(
    private val repository: MemoryRepository,
) : MemoryEngine {
    override val id = BuiltInMemoryEngines.SIMPLE
    override val capabilities = MemoryEngineCapabilities(automaticRecall = true)

    override suspend fun search(
        assistant: Assistant,
        conversationId: String?,
        query: String,
        limit: Int,
    ): List<UnifiedMemoryHit> = repository.retrieveRelevantMemoriesWithScores(
        assistantId = assistant.id.toString(),
        query = query,
        limit = limit,
        similarityThreshold = assistant.ragSimilarityThreshold,
        includeCore = assistant.ragIncludeCore,
        includeEpisodes = assistant.ragIncludeEpisodes,
    ).map { (memory, score) ->
        UnifiedMemoryHit(
            engineId = id,
            stableId = memory.id.toString(),
            content = memory.content,
            score = score,
            kind = if (memory.type == 0) "MEMORY" else "EPISODE",
            source = if (memory.type == 0) "MANUAL" else "CHAT",
            timestamp = memory.timestamp,
        )
    }
}

class GraphMemoryEngine(
    private val repository: GraphMemoryRepository,
) : MemoryEngine {
    override val id = BuiltInMemoryEngines.GRAPH
    override val capabilities = MemoryEngineCapabilities(
        graph = true,
        entities = true,
        automaticRecall = true,
    )

    override suspend fun search(
        assistant: Assistant,
        conversationId: String?,
        query: String,
        limit: Int,
    ): List<UnifiedMemoryHit> = coroutineScope {
        val assistantScope = async {
            repository.search(
                MemoryScope(assistant.id.toString()),
                query,
                topK = limit,
            )
        }
        val sessionScope = conversationId?.let {
            async {
                repository.search(
                    MemoryScope(
                        assistantId = assistant.id.toString(),
                        kind = MemoryScopeKind.SESSION,
                        conversationId = it,
                    ),
                    query,
                    topK = limit,
                )
            }
        }
        (assistantScope.await() + sessionScope?.await().orEmpty())
            .distinctBy { it.memory.id }
            .sortedByDescending { it.score }
            .take(limit)
            .map {
                UnifiedMemoryHit(
                    engineId = id,
                    stableId = it.memory.id,
                    content = it.memory.content,
                    score = it.score,
                    kind = "MEMORY",
                    source = it.memory.origin,
                    timestamp = it.memory.createdAt,
                )
            }
    }
}

class MemoryEngineRegistry(engines: List<MemoryEngine>) {
    private val enginesById = engines.associateBy { it.id }

    fun require(id: String): MemoryEngine = requireNotNull(enginesById[id]) {
        "Unknown memory engine: $id"
    }

    fun builtIns(): List<MemoryEngine> = listOf(
        require(BuiltInMemoryEngines.OFF),
        require(BuiltInMemoryEngines.SIMPLE),
        require(BuiltInMemoryEngines.GRAPH),
    )
}

class MemoryCoordinator(
    private val registry: MemoryEngineRegistry,
    private val graphRepository: GraphMemoryRepository,
) {
    suspend fun recall(
        assistant: Assistant,
        conversationId: String?,
        query: String,
        limit: Int = if (assistant.resolvedMemoryEngineId() == BuiltInMemoryEngines.GRAPH) {
            assistant.graphRecallLimit.coerceIn(1, 20)
        } else {
            assistant.ragLimit.coerceIn(1, 50)
        },
    ): List<UnifiedMemoryHit> = registry.require(assistant.resolvedMemoryEngineId())
        .search(assistant, conversationId, query, limit)

    suspend fun ingestSession(
        assistant: Assistant,
        conversationId: String,
        messages: List<me.rerere.ai.memory.MemoryInputMessage>,
        summary: String = "",
    ): MemoryIngestResult = graphRepository.ingest(
        scope = MemoryScope(assistant.id.toString(), MemoryScopeKind.SESSION, conversationId),
        messages = messages,
        origin = MemoryOrigin.SESSION,
        summary = summary,
    )

    suspend fun promoteSessionMemory(assistant: Assistant, memory: me.rerere.rikkahub.data.db.entity.GraphMemoryEntity) {
        when (assistant.resolvedMemoryEngineId()) {
            BuiltInMemoryEngines.GRAPH -> graphRepository.addManual(
                scope = MemoryScope(assistant.id.toString()),
                content = memory.content,
                sourceId = memory.id,
                origin = MemoryOrigin.SESSION,
            )
            BuiltInMemoryEngines.SIMPLE -> Unit // Transfer service performs the lossless origin mapping.
            BuiltInMemoryEngines.OFF -> Unit
        }
    }
}
