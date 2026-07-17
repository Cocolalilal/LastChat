package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.memory.ExtractedMemory
import me.rerere.ai.memory.Mem0ExtractionParser
import me.rerere.ai.memory.Mem0Hash
import me.rerere.ai.memory.Mem0IngestPolicy
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
import me.rerere.rikkahub.data.db.entity.GraphMemoryRelationEntity
import me.rerere.rikkahub.data.db.entity.GraphMemorySourceEntity
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryScopeMessageEntity
import me.rerere.rikkahub.data.db.entity.MemorySuppressionEntity
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.common.platform.PlatformLog

data class GraphMemoryHit(
    val memory: GraphMemoryEntity,
    val score: Float,
    val semanticScore: Float,
    val bm25Score: Float,
    val entityBoost: Float,
    val rawScore: Float,
    val maxPossibleScore: Float,
    val threshold: Float,
)

data class GraphSnapshot(
    val memories: List<GraphMemoryEntity>,
    val entities: List<GraphEntityEntity>,
    val links: List<GraphMemoryEntityLinkEntity>,
    val relations: List<GraphMemoryRelationEntity>,
)

data class MemoryIngestResult(
    val added: List<GraphMemoryEntity>,
    val skippedExactDuplicates: Int,
)

private data class ExtractionPrompt(
    val text: String,
    val memoryIdByPromptId: Map<String, String>,
)

private data class PendingEntity(
    val type: String,
    val text: String,
    val memoryIds: MutableSet<String>,
)

private data class ScopeLockEntry(
    val mutex: Mutex = Mutex(),
    var users: Int = 0,
)

class GraphMemoryRepository(
    private val database: AppDatabase,
    private val dao: MemoryGraphDao,
    private val embeddingService: EmbeddingService,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
    private val json: Json = JsonInstant,
) {
    private val scopeLocksGuard = Mutex()
    private val scopeLocks = mutableMapOf<String, ScopeLockEntry>()

    fun observeMemories(assistantId: String): Flow<List<GraphMemoryEntity>> = dao.observeMemories(assistantId)
    fun observeEntities(assistantId: String): Flow<List<GraphEntityEntity>> = dao.observeEntities(assistantId)
    fun observeLinks(): Flow<List<GraphMemoryEntityLinkEntity>> = dao.observeLinks()
    fun observeMemoryRelations(): Flow<List<GraphMemoryRelationEntity>> = dao.observeMemoryRelations()
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
        val relations = dao.observeMemoryRelations().first().filter {
            it.memoryId in memoryIds && it.relatedMemoryId in memoryIds
        }
        return GraphSnapshot(memories, entities, links, relations)
    }

    suspend fun ingest(
        scope: MemoryScope,
        messages: List<MemoryInputMessage>,
        origin: MemoryOrigin,
        summary: String = "",
    ): MemoryIngestResult = withContext(Dispatchers.IO) {
        withScopeLock(scope) scopeLock@{
            val eligible = messages.filter { it.content.isNotBlank() }
            if (eligible.isEmpty()) return@scopeLock MemoryIngestResult(emptyList(), 0)
            val query = eligible.joinToString("\n") { it.content }.take(8_000)
            val existingCandidates = search(scope, query, topK = 10, threshold = 0f).map { it.memory }
            val all = dao.getMemories(
                scope.assistantId,
                scope.kind.name,
                scope.conversationId,
                showExpired = false,
                now = System.currentTimeMillis(),
            )
            val previousMessages = dao.getRecentScopeMessages(
                scope.assistantId,
                scope.kind.name,
                scope.conversationId,
                10,
            ).reversed()
            val extractionPrompt = buildExtractionPrompt(
                messages = eligible,
                summary = summary,
                recentlyExtracted = all.take(20),
                existing = existingCandidates,
                lastMessages = previousMessages,
            )
            val existingHashes = all.asSequence().map { it.contentHash }.toHashSet()
            val suppressedHashes = dao.getSuppressions(scope.assistantId).asSequence()
                .filter { it.kind == "MEMORY" }
                .map { it.normalizedValue }
                .toHashSet()
            val selection = Mem0IngestPolicy.selectNewMemories(
                extracted = extract(extractionPrompt.text),
                existingHashes = existingHashes,
                suppressedHashes = suppressedHashes,
                memoryIdByPromptId = extractionPrompt.memoryIdByPromptId,
            )
            val candidates = selection.memories
            val embeddingResult = if (candidates.isEmpty()) null else {
                val modelId = embeddingService.getEmbeddingModelId(scope.assistantId)
                runCatching {
                    embeddingService.embedBatchForModelId(candidates.map { it.text }, modelId)
                }.getOrElse { batchFailure ->
                    PlatformLog.w(TAG, "Batch memory embedding failed; falling back to per-item embedding: ${batchFailure::class.simpleName}")
                    me.rerere.rikkahub.data.ai.rag.EmbeddingResult(
                        embeddings = candidates.map { candidate ->
                            embeddingService.embedBatchForModelId(listOf(candidate.text), modelId).embeddings.single()
                        },
                        modelId = modelId,
                    )
                }
            }
            val now = System.currentTimeMillis()
            val added = candidates.mapIndexed { index, candidate ->
                val vector = requireNotNull(embeddingResult).embeddings[index].toFloatArray()
                GraphMemoryEntity(
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
            }
            val candidateByMemoryId = added.mapIndexed { index, memory -> memory.id to candidates[index] }.toMap()
            val recentMessageIds = previousMessages.mapNotNull { it.messageId }.toHashSet()
            val scopeMessages = eligible.filter { source ->
                source.messageId == null || source.messageId !in recentMessageIds
            }.map {
                MemoryScopeMessageEntity(
                    assistantId = scope.assistantId,
                    scopeKind = scope.kind.name,
                    conversationId = scope.conversationId,
                    role = it.role.name,
                    content = it.content,
                    messageId = it.messageId,
                    observedAt = it.observedAtEpochMillis,
                )
            }
            database.withTransaction {
                if (added.isNotEmpty()) {
                    dao.upsertMemories(added)
                    dao.upsertGraphEmbeddings(added.map { memory ->
                        GraphEmbeddingCacheEntity(
                            memory.id,
                            "MEMORY",
                            requireNotNull(memory.embeddingModelId),
                            requireNotNull(memory.embeddingBlob),
                            now,
                        )
                    })
                    dao.insertHistories(added.map { memory ->
                        GraphMemoryHistoryEntity(
                            memoryId = memory.id,
                            event = "ADD",
                            newContent = memory.content,
                            createdAt = now,
                            actor = memory.attributedTo,
                        )
                    })
                    dao.insertSources(added.flatMap { memory ->
                        eligible.map { source ->
                            GraphMemorySourceEntity(
                                memoryId = memory.id,
                                sourceType = origin.name,
                                sourceId = source.messageId,
                                conversationId = scope.conversationId,
                                messageId = source.messageId,
                                speaker = source.role.name,
                                excerpt = source.content.take(900),
                                observedAt = source.observedAtEpochMillis,
                            )
                        }
                    })
                    dao.insertActivities(added.map { memory ->
                        MemoryActivityEntity(
                            assistantId = scope.assistantId,
                            objectId = memory.id,
                            objectKind = "MEMORY",
                            event = "ADD",
                            summary = memory.content.take(220),
                            origin = origin.name,
                            modelId = settingsStore.settingsFlow.value.summarizerModelId?.toString(),
                            createdAt = now,
                        )
                    })
                    dao.insertRelations(added.flatMap { memory ->
                        candidateByMemoryId.getValue(memory.id).linkedMemoryIds.map { relatedId ->
                            GraphMemoryRelationEntity(memory.id, relatedId, createdAt = now)
                        }
                    })
                }
                if (scopeMessages.isNotEmpty()) dao.insertScopeMessages(scopeMessages)
                dao.trimScopeMessages(scope.assistantId, scope.kind.name, scope.conversationId, 10)
            }
            linkEntitiesBestEffort(added)
            MemoryIngestResult(added, selection.skipped)
        }
    }

    suspend fun addManual(
        scope: MemoryScope,
        content: String,
        sourceId: String? = null,
        expirationAt: Long? = null,
        origin: MemoryOrigin = MemoryOrigin.MANUAL,
    ): GraphMemoryEntity = withContext(Dispatchers.IO) {
        withScopeLock(scope) scopeLock@{
            val clean = content.trim()
            require(clean.isNotBlank()) { "Memory text cannot be blank" }
            val hash = Mem0Hash.md5(clean)
            val existing = dao.getMemories(
                scope.assistantId, scope.kind.name, scope.conversationId, true, System.currentTimeMillis(),
            )
            existing.firstOrNull { it.contentHash == hash }?.let { return@scopeLock it }
            val embedded = runCatching { embeddingService.embedWithModelId(clean, scope.assistantId) }.getOrNull()
            val now = System.currentTimeMillis()
            val vector = embedded?.embeddings?.firstOrNull()?.toFloatArray()
            val memory = GraphMemoryEntity(
                id = Uuid.random().toString(),
                assistantId = scope.assistantId,
                scopeKind = scope.kind.name,
                conversationId = scope.conversationId,
                content = clean,
                contentHash = hash,
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
                dao.deleteSuppression(scope.assistantId, "MEMORY", hash)
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
            linkEntitiesBestEffort(listOf(memory))
            memory
        }
    }

    suspend fun update(memoryId: String, content: String, expirationAt: Long? = null): GraphMemoryEntity {
        val previous = requireNotNull(dao.getMemory(memoryId)) { "Memory with id $memoryId not found" }
        val scope = previous.toScope()
        return withScopeLock(scope) {
            val current = requireNotNull(dao.getMemory(memoryId)) { "Memory with id $memoryId not found" }
            val clean = content.trim()
            require(clean.isNotBlank()) { "Memory text cannot be blank" }
            val hash = Mem0Hash.md5(clean)
            val embedded = runCatching { embeddingService.embedWithModelId(clean, current.assistantId) }.getOrNull()
            val now = System.currentTimeMillis()
            val vector = embedded?.embeddings?.firstOrNull()?.toFloatArray()
            val updated = current.copy(
                content = clean,
                contentHash = hash,
                lemmatizedText = NativeEntityExtractor.lemmatizeForBm25(clean),
                updatedAt = now,
                expirationAt = expirationAt,
                embeddingBlob = vector?.toByteArray(),
                embeddingModelId = embedded?.modelId,
            )
            database.withTransaction {
                dao.deleteSuppression(updated.assistantId, "MEMORY", hash)
                dao.upsertMemory(updated)
                dao.deleteGraphEmbeddings(updated.id)
                if (embedded != null && vector != null) {
                    dao.upsertGraphEmbedding(GraphEmbeddingCacheEntity(updated.id, "MEMORY", embedded.modelId, vector.toByteArray(), now))
                }
                dao.deleteLinksForMemory(memoryId)
                dao.deleteOutgoingRelations(memoryId)
                dao.insertHistory(GraphMemoryHistoryEntity(memoryId = memoryId, event = "UPDATE", previousContent = current.content, newContent = clean, createdAt = now, actor = "USER"))
                dao.insertActivity(MemoryActivityEntity(assistantId = updated.assistantId, objectId = memoryId, objectKind = "MEMORY", event = "UPDATE", summary = clean.take(220), origin = "MANUAL", createdAt = now))
            }
            linkEntitiesBestEffort(listOf(updated))
            cleanupOrphanEntities(updated.assistantId)
            updated
        }
    }

    suspend fun forget(memoryId: String) {
        val memory = requireNotNull(dao.getMemory(memoryId)) { "Memory with id $memoryId not found" }
        withScopeLock(memory.toScope()) {
            val current = requireNotNull(dao.getMemory(memoryId)) { "Memory with id $memoryId not found" }
            val now = System.currentTimeMillis()
            database.withTransaction {
                dao.insertHistory(GraphMemoryHistoryEntity(memoryId = memoryId, event = "DELETE", previousContent = current.content, createdAt = now, actor = "USER"))
                dao.insertActivity(MemoryActivityEntity(assistantId = current.assistantId, objectId = memoryId, objectKind = "MEMORY", event = "DELETE", summary = current.content.take(220), origin = "MANUAL", createdAt = now))
                dao.upsertSuppression(MemorySuppressionEntity(Uuid.random().toString(), current.assistantId, "MEMORY", current.contentHash, "User forgot memory", now))
                dao.deleteMemoryGraph(memoryId)
                dao.deleteGraphEmbeddings(memoryId)
            }
            cleanupOrphanEntities(current.assistantId)
        }
    }

    suspend fun forgetEntity(entityId: String) {
        val entity = requireNotNull(dao.getEntity(entityId)) { "Entity with id $entityId not found" }
        withScopeLock(entity.toScope()) {
            val current = requireNotNull(dao.getEntity(entityId)) { "Entity with id $entityId not found" }
            val now = System.currentTimeMillis()
            database.withTransaction {
                dao.deleteLinksForEntity(entityId)
                dao.deleteEntity(entityId)
                dao.deleteGraphEmbeddings(entityId)
                dao.upsertSuppression(MemorySuppressionEntity(Uuid.random().toString(), current.assistantId, "ENTITY", current.normalizedName, "User forgot entity", now))
                dao.insertActivity(MemoryActivityEntity(assistantId = current.assistantId, objectId = entityId, objectKind = "ENTITY", event = "DELETE", summary = current.canonicalName, origin = "MANUAL", createdAt = now))
            }
        }
    }

    suspend fun search(
        scope: MemoryScope,
        query: String,
        topK: Int = Mem0Scoring.DEFAULT_TOP_K,
        threshold: Float = Mem0Scoring.DEFAULT_THRESHOLD,
        showExpired: Boolean = false,
    ): List<GraphMemoryHit> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
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
        val modelId = embeddingService.getEmbeddingModelId(scope.assistantId)
        val queryEmbedding = embeddingService.embedBatchForModelId(listOf(trimmed), modelId)
            .embeddings.single().toFloatArray()
        val memoryVectors = resolveMemoryVectors(memories, modelId)
        val semantic = memories.mapNotNull { memory ->
            val vector = memoryVectors[memory.id] ?: return@mapNotNull null
            MemorySearchCandidate(memory.id, VectorEngine.cosineSimilarity(queryEmbedding, vector))
        }.sortedByDescending { it.semanticScore }.take(Mem0Scoring.internalLimit(topK))
        val lemmatized = NativeEntityExtractor.lemmatizeForBm25(trimmed)
        val rawBm25 = bm25(memories, lemmatized)
        val (midpoint, steepness) = Mem0Scoring.bm25Parameters(lemmatized)
        val normalizedBm25 = rawBm25.mapValues { (_, value) -> Mem0Scoring.normalizeBm25(value, midpoint, steepness) }
        val entityBoosts = computeEntityBoosts(scope, trimmed, modelId)
        val ranked = Mem0Scoring.scoreAndRank(semantic, normalizedBm25, entityBoosts, threshold, topK)
        val byId = memories.associateBy { it.id }
        val hits = ranked.mapNotNull { result ->
            byId[result.id]?.let {
                GraphMemoryHit(
                    memory = it,
                    score = result.score,
                    semanticScore = result.details.semanticScore,
                    bm25Score = result.details.bm25Score,
                    entityBoost = result.details.entityBoost,
                    rawScore = result.details.rawScore,
                    maxPossibleScore = result.details.maxPossibleScore,
                    threshold = result.details.threshold,
                )
            }
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        if (elapsedMs >= SLOW_SEARCH_MS || memories.size >= LARGE_SCOPE_MEMORY_COUNT) {
            PlatformLog.i(
                TAG,
                "Graph search scope=${scope.kind.name} memories=${memories.size} candidates=${semantic.size} hits=${hits.size} elapsedMs=$elapsedMs",
            )
        }
        hits
    }

    suspend fun sources(memoryId: String): List<GraphMemorySourceEntity> = dao.getSources(memoryId)
    suspend fun relations(memoryId: String): List<GraphMemoryRelationEntity> = dao.getRelationsForMemory(memoryId)

    private suspend fun extract(prompt: String): List<ExtractedMemory> {
        val settings = settingsStore.settingsFlow.value
        val modelId = settings.summarizerModelId ?: error("Memory & summary model is not configured")
        val model = settings.findModelById(modelId) ?: error("Memory & summary model not found: $modelId")
        val providerSetting = model.findProvider(settings.providers) ?: error("Provider not found for memory model")
        repeat(EXTRACTION_PARSE_ATTEMPTS) { attempt ->
            val requestPrompt = if (attempt == 0) {
                prompt
            } else {
                "$prompt\n\nThe previous response was not valid JSON. Return exactly one valid JSON object and no surrounding text."
            }
            val response = providerManager.getProviderByType(providerSetting).generateText(
                providerSetting = providerSetting,
                messages = listOf(UIMessage.user(requestPrompt)),
                params = settings.buildSummarizerGenerationParams(model, temperature = 0.1f),
            )
            val text = response.choices.firstOrNull()?.message?.toContentText().orEmpty()
            Mem0ExtractionParser.parseOrNull(text, json)?.let { return it.memories }
            PlatformLog.w(TAG, "Memory extraction response was not parseable JSON; retry=${attempt + 1}")
        }
        error("Memory model returned invalid JSON after $EXTRACTION_PARSE_ATTEMPTS attempts")
    }

    private fun buildExtractionPrompt(
        messages: List<MemoryInputMessage>,
        summary: String,
        recentlyExtracted: List<GraphMemoryEntity>,
        existing: List<GraphMemoryEntity>,
        lastMessages: List<MemoryScopeMessageEntity>,
    ): ExtractionPrompt {
        fun messagesJson(items: List<Pair<String, String>>) = buildJsonArray {
            items.forEach { (role, content) -> add(buildJsonObject { put("role", role); put("content", content) }) }
        }.toString()
        val observation = messages.minOf { it.observedAtEpochMillis }.toDate()
        val current = System.currentTimeMillis().toDate()
        val memoryIdByPromptId = existing.mapIndexed { index, memory -> index.toString() to memory.id }.toMap()
        val text = buildString {
            append(Mem0Prompt.ADDITIVE_EXTRACTION_PROMPT)
            append("\n\n# ACTUAL INPUT\n\nSummary: ")
            append(json.encodeToString(summary))
            append("\nRecently Extracted: ")
            append(json.encodeToString(recentlyExtracted.map { it.content }))
            append("\nExisting Memories: ")
            append(buildJsonArray {
                existing.forEachIndexed { index, memory ->
                    add(buildJsonObject { put("id", index.toString()); put("text", memory.content) })
                }
            })
            append("\nLast k Messages: ")
            append(messagesJson(lastMessages.map { it.role.lowercase() to it.content }))
            append("\nNew Messages: ")
            append(messagesJson(messages.map { it.role.name.lowercase() to it.content }))
            append("\nObservation Date: $observation")
            append("\nCurrent Date: $current")
            append("\n\nReturn only the JSON object described above.")
        }
        return ExtractionPrompt(text, memoryIdByPromptId)
    }

    private suspend fun linkEntitiesBestEffort(memories: List<GraphMemoryEntity>) {
        if (memories.isEmpty()) return
        try {
            memories.groupBy { it.toScope().stableKey }.values.forEach { linkEntityGroup(it) }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            PlatformLog.w(TAG, "Entity linking was unavailable; memories remain stored: ${throwable::class.simpleName}")
        }
    }

    private suspend fun linkEntityGroup(memories: List<GraphMemoryEntity>) {
        val first = memories.firstOrNull() ?: return
        val suppressions = dao.getSuppressions(first.assistantId).asSequence()
            .filter { it.kind == "ENTITY" }
            .map { it.normalizedValue }
            .toHashSet()
        val pendingByName = linkedMapOf<String, PendingEntity>()
        memories.forEach { memory ->
            NativeEntityExtractor.extract(memory.content).forEach { candidate ->
                val normalized = NativeEntityExtractor.normalize(candidate.text)
                if (normalized !in suppressions) {
                    pendingByName.getOrPut(normalized) {
                        PendingEntity(candidate.type, candidate.text, linkedSetOf())
                    }.memoryIds += memory.id
                }
            }
        }
        if (pendingByName.isEmpty()) return

        val pending = pendingByName.values.toList()
        val embedded = embeddingService.embedBatch(pending.map { it.text }, first.assistantId)
        if (embedded.embeddings.size != pending.size) {
            error("Embedding provider returned ${embedded.embeddings.size} entity vectors for ${pending.size} entities")
        }
        val existing = dao.getEntities(first.assistantId, first.scopeKind, first.conversationId)
        val vectorsByEntityId = resolveEntityVectors(existing, embedded.modelId).toMutableMap()
        val knownEntities = existing.toMutableList()
        val entitiesToInsert = mutableListOf<GraphEntityEntity>()
        val embeddingsToInsert = mutableListOf<GraphEmbeddingCacheEntity>()
        val linksToInsert = mutableListOf<GraphMemoryEntityLinkEntity>()
        val now = System.currentTimeMillis()

        pending.forEachIndexed { index, candidate ->
            val normalized = NativeEntityExtractor.normalize(candidate.text)
            val vector = embedded.embeddings[index].toFloatArray()
            val exact = knownEntities.firstOrNull { it.normalizedName == normalized }
            val semantic = exact ?: knownEntities.asSequence()
                .mapNotNull { entity ->
                    vectorsByEntityId[entity.id]?.let { entity to VectorEngine.cosineSimilarity(vector, it) }
                }
                .filter { it.second >= ENTITY_DEDUP_THRESHOLD }
                .maxByOrNull { it.second }
                ?.first
            val entity = semantic ?: GraphEntityEntity(
                id = Uuid.random().toString(),
                assistantId = first.assistantId,
                scopeKind = first.scopeKind,
                conversationId = first.conversationId,
                canonicalName = candidate.text,
                normalizedName = normalized,
                entityType = candidate.type,
                embeddingBlob = vector.toByteArray(),
                embeddingModelId = embedded.modelId,
                createdAt = now,
                updatedAt = now,
            ).also { created ->
                knownEntities += created
                vectorsByEntityId[created.id] = vector
                entitiesToInsert += created
                embeddingsToInsert += GraphEmbeddingCacheEntity(
                    created.id, "ENTITY", embedded.modelId, vector.toByteArray(), now,
                )
            }
            candidate.memoryIds.forEach { memoryId ->
                linksToInsert += GraphMemoryEntityLinkEntity(memoryId, entity.id, 1f, now)
            }
        }
        database.withTransaction {
            if (entitiesToInsert.isNotEmpty()) dao.upsertEntities(entitiesToInsert)
            if (embeddingsToInsert.isNotEmpty()) dao.upsertGraphEmbeddings(embeddingsToInsert)
            if (linksToInsert.isNotEmpty()) dao.insertLinks(linksToInsert)
        }
    }

    private suspend fun computeEntityBoosts(
        scope: MemoryScope,
        query: String,
        modelId: String,
    ): Map<String, Float> {
        val queryEntities = NativeEntityExtractor.extract(query).distinctBy { NativeEntityExtractor.normalize(it.text) }.take(8)
        if (queryEntities.isEmpty()) return emptyMap()
        val stored = dao.getEntities(scope.assistantId, scope.kind.name, scope.conversationId)
        if (stored.isEmpty()) return emptyMap()
        val queryVectors = embeddingService.embedBatchForModelId(queryEntities.map { it.text }, modelId).embeddings
        val storedVectors = resolveEntityVectors(stored, modelId)
        val linksByEntityId = dao.getLinksForEntities(stored.map { it.id }).groupBy { it.entityId }
        val boosts = mutableMapOf<String, Float>()
        queryVectors.forEach { queryVectorList ->
            val queryVector = queryVectorList.toFloatArray()
            stored.asSequence()
                .mapNotNull { entity ->
                    storedVectors[entity.id]?.let { entity to VectorEngine.cosineSimilarity(queryVector, it) }
                }
                .filter { it.second >= 0.5f }
                .sortedByDescending { it.second }
                .take(500)
                .forEach { (entity, similarity) ->
                    val links = linksByEntityId[entity.id].orEmpty()
                    val boost = Mem0Scoring.entityBoost(similarity, links.size)
                    links.forEach { link -> boosts[link.memoryId] = maxOf(boosts[link.memoryId] ?: 0f, boost) }
                }
        }
        return boosts
    }

    private suspend fun resolveMemoryVectors(
        memories: List<GraphMemoryEntity>,
        modelId: String,
    ): Map<String, FloatArray> {
        val resolved = mutableMapOf<String, FloatArray>()
        val needingCache = memories.filter { memory ->
            val blob = memory.embeddingBlob
            if (blob != null && memory.embeddingModelId == modelId) {
                resolved[memory.id] = blob.toFloatArray()
                false
            } else {
                true
            }
        }
        if (needingCache.isEmpty()) return resolved
        val cached = dao.getGraphEmbeddings(needingCache.map { it.id }, "MEMORY", modelId).associateBy { it.ownerId }
        val sourceUpdates = mutableListOf<GraphMemoryEntity>()
        needingCache.forEach { memory ->
            cached[memory.id]?.let { hit ->
                resolved[memory.id] = hit.embeddingBlob.toFloatArray()
                sourceUpdates += memory.copy(embeddingBlob = hit.embeddingBlob, embeddingModelId = modelId)
            }
        }
        val missing = needingCache.filter { it.id !in resolved }
        val cacheUpdates = mutableListOf<GraphEmbeddingCacheEntity>()
        if (missing.isNotEmpty()) {
            runCatching { embeddingService.embedBatchForModelId(missing.map { it.content }, modelId) }
                .onSuccess { result ->
                    result.embeddings.take(missing.size).forEachIndexed { index, vectorList ->
                        val memory = missing[index]
                        val blob = vectorList.toFloatArray().toByteArray()
                        resolved[memory.id] = blob.toFloatArray()
                        sourceUpdates += memory.copy(embeddingBlob = blob, embeddingModelId = modelId)
                        cacheUpdates += GraphEmbeddingCacheEntity(memory.id, "MEMORY", modelId, blob, System.currentTimeMillis())
                    }
                }
                .onFailure { failure ->
                    PlatformLog.w(TAG, "Could not refresh ${missing.size} memory embeddings: ${failure::class.simpleName}")
                }
        }
        if (sourceUpdates.isNotEmpty() || cacheUpdates.isNotEmpty()) {
            runCatching {
                val cacheUpdatesByOwner = cacheUpdates.associateBy { it.ownerId }
                database.withTransaction {
                    sourceUpdates.forEach { memory ->
                        val updated = dao.updateMemoryEmbeddingIfUnchanged(
                            memory.id,
                            memory.updatedAt,
                            requireNotNull(memory.embeddingBlob),
                            modelId,
                        )
                        if (updated > 0) cacheUpdatesByOwner[memory.id]?.let { dao.upsertGraphEmbedding(it) }
                    }
                }
            }.onFailure { PlatformLog.w(TAG, "Could not persist refreshed memory embeddings: ${it::class.simpleName}") }
        }
        return resolved
    }

    private suspend fun resolveEntityVectors(
        entities: List<GraphEntityEntity>,
        modelId: String,
    ): Map<String, FloatArray> {
        val resolved = mutableMapOf<String, FloatArray>()
        val needingCache = entities.filter { entity ->
            val blob = entity.embeddingBlob
            if (blob != null && entity.embeddingModelId == modelId) {
                resolved[entity.id] = blob.toFloatArray()
                false
            } else {
                true
            }
        }
        if (needingCache.isEmpty()) return resolved
        val cached = dao.getGraphEmbeddings(needingCache.map { it.id }, "ENTITY", modelId).associateBy { it.ownerId }
        val sourceUpdates = mutableListOf<GraphEntityEntity>()
        needingCache.forEach { entity ->
            cached[entity.id]?.let { hit ->
                resolved[entity.id] = hit.embeddingBlob.toFloatArray()
                sourceUpdates += entity.copy(embeddingBlob = hit.embeddingBlob, embeddingModelId = modelId)
            }
        }
        val missing = needingCache.filter { it.id !in resolved }
        val cacheUpdates = mutableListOf<GraphEmbeddingCacheEntity>()
        if (missing.isNotEmpty()) {
            runCatching { embeddingService.embedBatchForModelId(missing.map { it.canonicalName }, modelId) }
                .onSuccess { result ->
                    result.embeddings.take(missing.size).forEachIndexed { index, vectorList ->
                        val entity = missing[index]
                        val blob = vectorList.toFloatArray().toByteArray()
                        resolved[entity.id] = blob.toFloatArray()
                        sourceUpdates += entity.copy(embeddingBlob = blob, embeddingModelId = modelId)
                        cacheUpdates += GraphEmbeddingCacheEntity(entity.id, "ENTITY", modelId, blob, System.currentTimeMillis())
                    }
                }
                .onFailure { failure ->
                    PlatformLog.w(TAG, "Could not refresh ${missing.size} entity embeddings: ${failure::class.simpleName}")
                }
        }
        if (sourceUpdates.isNotEmpty() || cacheUpdates.isNotEmpty()) {
            runCatching {
                val cacheUpdatesByOwner = cacheUpdates.associateBy { it.ownerId }
                database.withTransaction {
                    sourceUpdates.forEach { entity ->
                        val updated = dao.updateEntityEmbeddingIfUnchanged(
                            entity.id,
                            entity.updatedAt,
                            requireNotNull(entity.embeddingBlob),
                            modelId,
                        )
                        if (updated > 0) cacheUpdatesByOwner[entity.id]?.let { dao.upsertGraphEmbedding(it) }
                    }
                }
            }.onFailure { PlatformLog.w(TAG, "Could not persist refreshed entity embeddings: ${it::class.simpleName}") }
        }
        return resolved
    }

    private suspend fun cleanupOrphanEntities(assistantId: String) {
        val orphanIds = dao.getOrphanEntityIds(assistantId)
        if (orphanIds.isEmpty()) return
        database.withTransaction {
            dao.deleteGraphEmbeddings(orphanIds)
            dao.deleteEntities(orphanIds)
        }
    }

    private suspend fun <T> withScopeLock(scope: MemoryScope, block: suspend () -> T): T {
        val key = scope.stableKey
        val entry = scopeLocksGuard.withLock {
            scopeLocks.getOrPut(key) { ScopeLockEntry() }.also { it.users++ }
        }
        return try {
            entry.mutex.withLock { block() }
        } finally {
            scopeLocksGuard.withLock {
                entry.users--
                if (entry.users == 0) scopeLocks.remove(key)
            }
        }
    }

    private fun GraphMemoryEntity.toScope() = MemoryScope(
        assistantId = assistantId,
        kind = MemoryScopeKind.valueOf(scopeKind),
        conversationId = conversationId,
    )

    private fun GraphEntityEntity.toScope() = MemoryScope(
        assistantId = assistantId,
        kind = MemoryScopeKind.valueOf(scopeKind),
        conversationId = conversationId,
    )

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

    companion object {
        private const val TAG = "GraphMemoryRepository"
        private const val EXTRACTION_PARSE_ATTEMPTS = 2
        private const val ENTITY_DEDUP_THRESHOLD = 0.95f
        private const val SLOW_SEARCH_MS = 250L
        private const val LARGE_SCOPE_MEMORY_COUNT = 2_000
    }
}
