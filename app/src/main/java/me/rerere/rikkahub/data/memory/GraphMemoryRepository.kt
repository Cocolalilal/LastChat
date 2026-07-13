package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.memory.ExtractedMemory
import me.rerere.ai.memory.Mem0ExtractionEnvelope
import me.rerere.ai.memory.Mem0Hash
import me.rerere.ai.memory.Mem0Prompt
import me.rerere.ai.memory.Mem0Scoring
import me.rerere.ai.memory.MemoryInputMessage
import me.rerere.ai.memory.MemoryOrigin
import me.rerere.ai.memory.MemoryScope
import me.rerere.ai.memory.MemoryScopeKind
import me.rerere.ai.memory.MemorySearchCandidate
import me.rerere.ai.memory.MemorySpeaker
import me.rerere.ai.memory.NativeEntityExtractor
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.ai.rag.toByteArray
import me.rerere.rikkahub.data.ai.rag.toFloatArray
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryGraphDao
import me.rerere.rikkahub.data.db.entity.GraphEmbeddingCacheEntity
import me.rerere.rikkahub.data.db.entity.GraphEntityEntity
import me.rerere.rikkahub.data.db.entity.GraphMemoryEntity
import me.rerere.rikkahub.data.db.entity.GraphMemoryEntityLinkEntity
import me.rerere.rikkahub.data.db.entity.GraphMemoryHistoryEntity
import me.rerere.rikkahub.data.db.entity.GraphMemorySourceEntity
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryScopeMessageEntity
import me.rerere.rikkahub.data.db.entity.MemorySuppressionEntity
import me.rerere.rikkahub.utils.JsonInstant

data class GraphMemoryHit(
    val memory: GraphMemoryEntity,
    val score: Float,
    val semanticScore: Float,
    val bm25Score: Float,
    val entityBoost: Float,
)

data class GraphSnapshot(
    val memories: List<GraphMemoryEntity>,
    val entities: List<GraphEntityEntity>,
    val links: List<GraphMemoryEntityLinkEntity>,
)

data class MemoryIngestResult(
    val added: List<GraphMemoryEntity>,
    val skippedExactDuplicates: Int,
)

class GraphMemoryRepository(
    private val database: AppDatabase,
    private val dao: MemoryGraphDao,
    private val embeddingService: EmbeddingService,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
    private val json: Json = JsonInstant,
) {
    fun observeMemories(assistantId: String): Flow<List<GraphMemoryEntity>> = dao.observeMemories(assistantId)
    fun observeEntities(assistantId: String): Flow<List<GraphEntityEntity>> = dao.observeEntities(assistantId)
    fun observeLinks(): Flow<List<GraphMemoryEntityLinkEntity>> = dao.observeLinks()
    fun observeActivity(assistantId: String): Flow<List<MemoryActivityEntity>> = dao.observeActivity(assistantId)

    suspend fun snapshot(assistantId: String): GraphSnapshot {
        val memories = dao.getMemories(
            assistantId = assistantId,
            scopeKind = MemoryScopeKind.ASSISTANT.name,
            conversationId = null,
            showExpired = false,
            now = System.currentTimeMillis(),
        )
        val entities = dao.getEntities(assistantId, MemoryScopeKind.ASSISTANT.name, null)
        val memoryIds = memories.asSequence().map { it.id }.toHashSet()
        val entityIds = entities.asSequence().map { it.id }.toHashSet()
        val links = dao.observeLinks().first().filter {
            it.memoryId in memoryIds && it.entityId in entityIds
        }
        return GraphSnapshot(memories, entities, links)
    }

    suspend fun ingest(
        scope: MemoryScope,
        messages: List<MemoryInputMessage>,
        origin: MemoryOrigin,
        summary: String = "",
    ): MemoryIngestResult = withContext(Dispatchers.IO) {
        val eligible = messages.filter { it.content.isNotBlank() }
        if (eligible.isEmpty()) return@withContext MemoryIngestResult(emptyList(), 0)
        val query = eligible.joinToString("\n") { it.content }.take(8_000)
        val existingCandidates = search(scope, query, topK = 10, threshold = 0f).map { it.memory }
        val all = dao.getMemories(
            scope.assistantId,
            scope.kind.name,
            scope.conversationId,
            showExpired = false,
            now = System.currentTimeMillis(),
        )
        val recentlyExtracted = all.take(20)
        val previousMessages = dao.getRecentScopeMessages(
            scope.assistantId,
            scope.kind.name,
            scope.conversationId,
            10,
        ).reversed()
        val prompt = buildExtractionPrompt(
            messages = eligible,
            summary = summary,
            recentlyExtracted = recentlyExtracted,
            existing = existingCandidates,
            lastMessages = previousMessages,
        )
        val extracted = extract(prompt)
        val distinct = LinkedHashMap<String, ExtractedMemory>()
        val existingHashes = existingCandidates.asSequence().map { it.contentHash }.toHashSet()
        var skipped = 0
        extracted.forEach { candidate ->
            val cleaned = candidate.text.trim()
            val hash = Mem0Hash.md5(cleaned)
            if (cleaned.isBlank() || hash in existingHashes || distinct.containsKey(hash)) {
                skipped++
            } else {
                distinct[hash] = candidate.copy(text = cleaned)
            }
        }
        val candidates = distinct.values.toList()
        val embeddingResult = if (candidates.isEmpty()) null else {
            runCatching { embeddingService.embedBatch(candidates.map { it.text }, scope.assistantId) }
                .getOrElse {
                    val vectors = candidates.map { candidate ->
                        embeddingService.embed(candidate.text, scope.assistantId)
                    }
                    me.rerere.rikkahub.data.ai.rag.EmbeddingResult(
                        embeddings = vectors,
                        modelId = embeddingService.getEmbeddingModelId(scope.assistantId),
                    )
                }
        }
        val now = System.currentTimeMillis()
        val added = candidates.mapIndexed { index, candidate ->
            val vector = requireNotNull(embeddingResult).embeddings[index].toFloatArray()
            val memory = GraphMemoryEntity(
                id = Uuid.random().toString(),
                assistantId = scope.assistantId,
                scopeKind = scope.kind.name,
                conversationId = scope.conversationId,
                content = candidate.text,
                contentHash = Mem0Hash.md5(candidate.text),
                lemmatizedText = NativeEntityExtractor.lemmatizeForBm25(candidate.text),
                attributedTo = candidate.attributedTo?.name,
                origin = origin.name,
                createdAt = now,
                updatedAt = now,
                embeddingBlob = vector.toByteArray(),
                embeddingModelId = embeddingResult.modelId,
            )
            database.withTransaction {
                dao.upsertMemory(memory)
                dao.upsertGraphEmbedding(
                    GraphEmbeddingCacheEntity(memory.id, "MEMORY", embeddingResult.modelId, vector.toByteArray(), now),
                )
                dao.insertHistory(
                    GraphMemoryHistoryEntity(
                        memoryId = memory.id,
                        event = "ADD",
                        newContent = memory.content,
                        createdAt = now,
                        actor = memory.attributedTo,
                    ),
                )
                eligible.forEach { source ->
                    dao.insertSource(
                        GraphMemorySourceEntity(
                            memoryId = memory.id,
                            sourceType = origin.name,
                            sourceId = source.messageId,
                            conversationId = scope.conversationId,
                            messageId = source.messageId,
                            speaker = source.role.name,
                            excerpt = source.content.take(900),
                            observedAt = source.observedAtEpochMillis,
                        ),
                    )
                }
                dao.insertActivity(
                    MemoryActivityEntity(
                        assistantId = scope.assistantId,
                        objectId = memory.id,
                        objectKind = "MEMORY",
                        event = "ADD",
                        summary = memory.content.take(220),
                        origin = origin.name,
                        modelId = settingsStore.settingsFlow.value.summarizerModelId?.toString(),
                        createdAt = now,
                    ),
                )
            }
            linkEntities(memory)
            memory
        }
        dao.insertScopeMessages(
            eligible.map {
                MemoryScopeMessageEntity(
                    assistantId = scope.assistantId,
                    scopeKind = scope.kind.name,
                    conversationId = scope.conversationId,
                    role = it.role.name,
                    content = it.content,
                    messageId = it.messageId,
                    observedAt = it.observedAtEpochMillis,
                )
            },
        )
        dao.trimScopeMessages(scope.assistantId, scope.kind.name, scope.conversationId, 10)
        MemoryIngestResult(added, skipped)
    }

    suspend fun addManual(
        scope: MemoryScope,
        content: String,
        sourceId: String? = null,
        expirationAt: Long? = null,
        origin: MemoryOrigin = MemoryOrigin.MANUAL,
    ): GraphMemoryEntity = withContext(Dispatchers.IO) {
        val clean = content.trim()
        require(clean.isNotBlank()) { "Memory text cannot be blank" }
        val existing = dao.getMemories(
            scope.assistantId, scope.kind.name, scope.conversationId, true, System.currentTimeMillis(),
        )
        existing.firstOrNull { it.contentHash == Mem0Hash.md5(clean) }?.let { return@withContext it }
        val embedded = runCatching { embeddingService.embedWithModelId(clean, scope.assistantId) }.getOrNull()
        val now = System.currentTimeMillis()
        val vector = embedded?.embeddings?.firstOrNull()?.toFloatArray()
        val memory = GraphMemoryEntity(
            id = Uuid.random().toString(),
            assistantId = scope.assistantId,
            scopeKind = scope.kind.name,
            conversationId = scope.conversationId,
            content = clean,
            contentHash = Mem0Hash.md5(clean),
            lemmatizedText = NativeEntityExtractor.lemmatizeForBm25(clean),
            attributedTo = MemorySpeaker.USER.name,
            origin = origin.name,
            createdAt = now,
            updatedAt = now,
            expirationAt = expirationAt,
            embeddingBlob = vector?.toByteArray(),
            embeddingModelId = embedded?.modelId,
        )
        database.withTransaction {
            dao.upsertMemory(memory)
            if (embedded != null && vector != null) {
                dao.upsertGraphEmbedding(GraphEmbeddingCacheEntity(memory.id, "MEMORY", embedded.modelId, vector.toByteArray(), now))
            }
            dao.insertHistory(GraphMemoryHistoryEntity(memoryId = memory.id, event = "ADD", newContent = clean, createdAt = now, actor = "USER"))
            dao.insertSource(
                GraphMemorySourceEntity(
                    memoryId = memory.id,
                    sourceType = origin.name,
                    sourceId = sourceId,
                    conversationId = scope.conversationId,
                    speaker = "USER",
                    excerpt = clean,
                    observedAt = now,
                ),
            )
            dao.insertActivity(MemoryActivityEntity(assistantId = scope.assistantId, objectId = memory.id, objectKind = "MEMORY", event = "ADD", summary = clean.take(220), origin = origin.name, createdAt = now))
        }
        linkEntities(memory)
        memory
    }

    suspend fun update(memoryId: String, content: String, expirationAt: Long? = null): GraphMemoryEntity {
        val previous = requireNotNull(dao.getMemory(memoryId)) { "Memory with id $memoryId not found" }
        val clean = content.trim()
        require(clean.isNotBlank()) { "Memory text cannot be blank" }
        val embedded = runCatching { embeddingService.embedWithModelId(clean, previous.assistantId) }.getOrNull()
        val now = System.currentTimeMillis()
        val vector = embedded?.embeddings?.firstOrNull()?.toFloatArray()
        val updated = previous.copy(
            content = clean,
            contentHash = Mem0Hash.md5(clean),
            lemmatizedText = NativeEntityExtractor.lemmatizeForBm25(clean),
            updatedAt = now,
            expirationAt = expirationAt,
            embeddingBlob = vector?.toByteArray(),
            embeddingModelId = embedded?.modelId,
        )
        database.withTransaction {
            dao.upsertMemory(updated)
            if (embedded != null && vector != null) {
                dao.upsertGraphEmbedding(GraphEmbeddingCacheEntity(updated.id, "MEMORY", embedded.modelId, vector.toByteArray(), now))
            }
            dao.deleteLinksForMemory(memoryId)
            dao.insertHistory(GraphMemoryHistoryEntity(memoryId = memoryId, event = "UPDATE", previousContent = previous.content, newContent = clean, createdAt = now, actor = "USER"))
            dao.insertActivity(MemoryActivityEntity(assistantId = updated.assistantId, objectId = memoryId, objectKind = "MEMORY", event = "UPDATE", summary = clean.take(220), origin = "MANUAL", createdAt = now))
        }
        linkEntities(updated)
        return updated
    }

    suspend fun forget(memoryId: String) {
        val memory = requireNotNull(dao.getMemory(memoryId)) { "Memory with id $memoryId not found" }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.insertHistory(GraphMemoryHistoryEntity(memoryId = memoryId, event = "DELETE", previousContent = memory.content, createdAt = now, actor = "USER"))
            dao.insertActivity(MemoryActivityEntity(assistantId = memory.assistantId, objectId = memoryId, objectKind = "MEMORY", event = "DELETE", summary = memory.content.take(220), origin = "MANUAL", createdAt = now))
            dao.upsertSuppression(MemorySuppressionEntity(Uuid.random().toString(), memory.assistantId, "MEMORY", memory.contentHash, "User forgot memory", now))
            dao.deleteMemoryGraph(memoryId)
            dao.deleteGraphEmbeddings(memoryId)
        }
    }

    suspend fun forgetEntity(entityId: String) {
        val entity = requireNotNull(dao.getEntity(entityId)) { "Entity with id $entityId not found" }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.deleteLinksForEntity(entityId)
            dao.deleteEntity(entityId)
            dao.deleteGraphEmbeddings(entityId)
            dao.upsertSuppression(MemorySuppressionEntity(Uuid.random().toString(), entity.assistantId, "ENTITY", entity.normalizedName, "User forgot entity", now))
            dao.insertActivity(MemoryActivityEntity(assistantId = entity.assistantId, objectId = entityId, objectKind = "ENTITY", event = "DELETE", summary = entity.canonicalName, origin = "MANUAL", createdAt = now))
        }
    }

    suspend fun search(
        scope: MemoryScope,
        query: String,
        topK: Int = Mem0Scoring.DEFAULT_TOP_K,
        threshold: Float = Mem0Scoring.DEFAULT_THRESHOLD,
        showExpired: Boolean = false,
    ): List<GraphMemoryHit> = withContext(Dispatchers.Default) {
        val trimmed = query.trim()
        require(trimmed.isNotBlank()) { "Query cannot be blank" }
        val memories = dao.getMemories(
            scope.assistantId,
            scope.kind.name,
            scope.conversationId,
            showExpired,
            System.currentTimeMillis(),
        )
        if (memories.isEmpty()) return@withContext emptyList()
        val queryEmbedding = embeddingService.embed(trimmed, scope.assistantId).toFloatArray()
        val semantic = memories.mapNotNull { memory ->
            val vector = memory.embeddingBlob?.toFloatArray() ?: return@mapNotNull null
            MemorySearchCandidate(memory.id, VectorEngine.cosineSimilarity(queryEmbedding, vector))
        }.sortedByDescending { it.semanticScore }.take(Mem0Scoring.internalLimit(topK))
        val lemmatized = NativeEntityExtractor.lemmatizeForBm25(trimmed)
        val rawBm25 = bm25(memories, lemmatized)
        val (midpoint, steepness) = Mem0Scoring.bm25Parameters(lemmatized)
        val normalizedBm25 = rawBm25.mapValues { (_, value) -> Mem0Scoring.normalizeBm25(value, midpoint, steepness) }
        val entityBoosts = computeEntityBoosts(scope, trimmed)
        val ranked = Mem0Scoring.scoreAndRank(semantic, normalizedBm25, entityBoosts, threshold, topK)
        val byId = memories.associateBy { it.id }
        ranked.mapNotNull { result ->
            byId[result.id]?.let {
                GraphMemoryHit(it, result.score, result.details.semanticScore, result.details.bm25Score, result.details.entityBoost)
            }
        }
    }

    suspend fun sources(memoryId: String): List<GraphMemorySourceEntity> = dao.getSources(memoryId)

    private suspend fun extract(prompt: String): List<ExtractedMemory> {
        val settings = settingsStore.settingsFlow.value
        val modelId = settings.summarizerModelId ?: error("Memory & summary model is not configured")
        val model = settings.findModelById(modelId) ?: error("Memory & summary model not found: $modelId")
        val providerSetting = model.findProvider(settings.providers) ?: error("Provider not found for memory model")
        val response = providerManager.getProviderByType(providerSetting).generateText(
            providerSetting = providerSetting,
            messages = listOf(UIMessage.user(prompt)),
            params = settings.buildSummarizerGenerationParams(model, temperature = 0.1f),
        )
        val text = response.choices.firstOrNull()?.message?.toContentText()
            ?: error("Memory model returned no text")
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end >= start) { "Memory model returned invalid JSON" }
        return json.decodeFromString<Mem0ExtractionEnvelope>(text.substring(start, end + 1)).memories
    }

    private fun buildExtractionPrompt(
        messages: List<MemoryInputMessage>,
        summary: String,
        recentlyExtracted: List<GraphMemoryEntity>,
        existing: List<GraphMemoryEntity>,
        lastMessages: List<MemoryScopeMessageEntity>,
    ): String {
        fun messagesJson(items: List<Pair<String, String>>) = buildJsonArray {
            items.forEach { (role, content) -> add(buildJsonObject { put("role", role); put("content", content) }) }
        }.toString()
        val observation = messages.minOf { it.observedAtEpochMillis }.toDate()
        val current = System.currentTimeMillis().toDate()
        return buildString {
            append(Mem0Prompt.ADDITIVE_EXTRACTION_PROMPT)
            append("\n\n# ACTUAL INPUT\n\nSummary: ")
            append(json.encodeToString(summary))
            append("\nRecently Extracted: ")
            append(json.encodeToString(recentlyExtracted.map { it.content }))
            append("\nExisting Memories: ")
            append(buildJsonArray {
                existing.forEach { memory -> add(buildJsonObject { put("id", memory.id); put("text", memory.content) }) }
            })
            append("\nLast k Messages: ")
            append(messagesJson(lastMessages.map { it.role.lowercase() to it.content }))
            append("\nNew Messages: ")
            append(messagesJson(messages.map { it.role.name.lowercase() to it.content }))
            append("\nObservation Date: $observation")
            append("\nCurrent Date: $current")
            append("\n\nReturn only the JSON object described above.")
        }
    }

    private suspend fun linkEntities(memory: GraphMemoryEntity) {
        val suppressions = dao.getSuppressions(memory.assistantId)
            .filter { it.kind == "ENTITY" }
            .map { it.normalizedValue }
            .toHashSet()
        val extracted = NativeEntityExtractor.extract(memory.content)
            .filter { NativeEntityExtractor.normalize(it.text) !in suppressions }
        if (extracted.isEmpty()) return
        val embedded = embeddingService.embedBatch(extracted.map { it.text }, memory.assistantId)
        val existing = dao.getEntities(memory.assistantId, memory.scopeKind, memory.conversationId)
        val now = System.currentTimeMillis()
        extracted.forEachIndexed { index, candidate ->
            val normalized = NativeEntityExtractor.normalize(candidate.text)
            val vector = embedded.embeddings[index].toFloatArray()
            val exact = existing.firstOrNull { it.normalizedName == normalized }
            val semantic = exact ?: existing.asSequence()
                .mapNotNull { entity -> entity.embeddingBlob?.toFloatArray()?.let { entity to VectorEngine.cosineSimilarity(vector, it) } }
                .filter { it.second >= 0.95f }
                .maxByOrNull { it.second }
                ?.first
            val entity = semantic ?: GraphEntityEntity(
                id = Uuid.random().toString(),
                assistantId = memory.assistantId,
                scopeKind = memory.scopeKind,
                conversationId = memory.conversationId,
                canonicalName = candidate.text,
                normalizedName = normalized,
                entityType = candidate.type,
                embeddingBlob = vector.toByteArray(),
                embeddingModelId = embedded.modelId,
                createdAt = now,
                updatedAt = now,
            )
            dao.upsertEntity(entity)
            dao.upsertGraphEmbedding(GraphEmbeddingCacheEntity(entity.id, "ENTITY", embedded.modelId, vector.toByteArray(), now))
            dao.insertLink(GraphMemoryEntityLinkEntity(memory.id, entity.id, 1f, now))
        }
    }

    private suspend fun computeEntityBoosts(scope: MemoryScope, query: String): Map<String, Float> {
        val queryEntities = NativeEntityExtractor.extract(query).distinctBy { NativeEntityExtractor.normalize(it.text) }.take(8)
        if (queryEntities.isEmpty()) return emptyMap()
        val stored = dao.getEntities(scope.assistantId, scope.kind.name, scope.conversationId)
        if (stored.isEmpty()) return emptyMap()
        val queryVectors = embeddingService.embedBatch(queryEntities.map { it.text }, scope.assistantId).embeddings
        val boosts = mutableMapOf<String, Float>()
        queryVectors.forEach { queryVectorList ->
            val queryVector = queryVectorList.toFloatArray()
            stored.asSequence()
                .mapNotNull { entity -> entity.embeddingBlob?.toFloatArray()?.let { entity to VectorEngine.cosineSimilarity(queryVector, it) } }
                .filter { it.second >= 0.5f }
                .sortedByDescending { it.second }
                .take(500)
                .forEach { (entity, similarity) ->
                    val links = dao.getLinksForEntity(entity.id)
                    val boost = Mem0Scoring.entityBoost(similarity, links.size)
                    links.forEach { link -> boosts[link.memoryId] = maxOf(boosts[link.memoryId] ?: 0f, boost) }
                }
        }
        return boosts
    }

    private fun bm25(memories: List<GraphMemoryEntity>, query: String): Map<String, Float> {
        val terms = query.split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty() || memories.isEmpty()) return emptyMap()
        val docs = memories.associate { it.id to it.lemmatizedText.split(' ').filter(String::isNotBlank) }
        val avgLength = docs.values.sumOf { it.size }.toDouble() / docs.size.coerceAtLeast(1)
        val scores = mutableMapOf<String, Float>()
        terms.distinct().forEach { term ->
            val docFrequency = docs.values.count { term in it }
            val idf = ln(1.0 + (docs.size - docFrequency + 0.5) / (docFrequency + 0.5))
            docs.forEach { (id, tokens) ->
                val tf = tokens.count { it == term }
                if (tf == 0) return@forEach
                val denominator = tf + 1.5 * (1 - 0.75 + 0.75 * tokens.size / avgLength.coerceAtLeast(1.0))
                scores[id] = (scores[id] ?: 0f) + (idf * (tf * 2.5 / denominator)).toFloat()
            }
        }
        return scores
    }

    private fun Long.toDate(): String = DateTimeFormatter.ISO_LOCAL_DATE.format(
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate(),
    )
}
