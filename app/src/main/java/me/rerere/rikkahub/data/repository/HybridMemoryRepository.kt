package me.rerere.rikkahub.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.HybridMemoryDao
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheDAO
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.toByteArray
import me.rerere.rikkahub.data.db.entity.EmbeddingCacheEntity
import me.rerere.rikkahub.data.db.entity.MemoryConversationDigestEntity
import me.rerere.rikkahub.data.db.entity.MemoryDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryDocumentRevisionEntity
import me.rerere.rikkahub.data.db.entity.MemoryProcessingStateEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphProvenanceEntity
import me.rerere.rikkahub.data.db.entity.MemorySearchFtsEntity
import me.rerere.rikkahub.data.db.entity.MemorySearchRowEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.DocumentUpdateResult
import me.rerere.rikkahub.data.model.HybridMemorySearchResult
import me.rerere.rikkahub.data.model.MAX_MEMORY_DOCUMENT_CHAR_LIMIT
import me.rerere.rikkahub.data.model.MIN_MEMORY_DOCUMENT_CHAR_LIMIT
import me.rerere.rikkahub.data.model.MemoryDocumentKind
import me.rerere.rikkahub.data.model.MemorySourceKind
import me.rerere.rikkahub.data.model.MemoryConversionApplyResult
import me.rerere.rikkahub.data.model.MemoryConversionDirection
import me.rerere.rikkahub.data.model.MemoryConversionInput
import me.rerere.rikkahub.data.model.MemoryConversionPreview
import me.rerere.rikkahub.data.model.MemoryConversionEntryProposal
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryConversionStateEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphOverrideEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.memoryCodePointCount
import kotlin.uuid.Uuid

class HybridMemoryRepository(
    private val database: AppDatabase,
    private val dao: HybridMemoryDao,
    private val memoryDao: MemoryDAO,
    private val embeddingService: EmbeddingService?,
    private val embeddingCacheDao: EmbeddingCacheDAO,
) {
    /**
     * Runs a complete hybrid-memory mutation as one Room transaction. This is used by
     * background processing so a stale model result can never partially update a digest,
     * document, graph, or processing watermark.
     */
    suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction {
        block()
    }

    fun observeDocuments(assistantId: String): Flow<List<MemoryDocumentEntity>> =
        dao.observeDocuments(assistantId)

    fun observeDigests(assistantId: String): Flow<List<MemoryConversationDigestEntity>> =
        dao.observeDigests(assistantId)

    fun observeDocumentRevisions(documentId: String): Flow<List<MemoryDocumentRevisionEntity>> =
        dao.observeRevisions(documentId)

    fun observeGraphNodes(assistantId: String) = dao.observeNodes(assistantId)

    fun observeGraphEdges(assistantId: String) = dao.observeEdges(assistantId)

    fun observeGraphOverrides(assistantId: String) = dao.observeOverrides(assistantId)

    suspend fun getGraphProvenance(graphId: String) = dao.getGraphProvenance(graphId)

    suspend fun addGraphOverride(
        assistantId: String,
        targetKind: String,
        targetId: String,
        operation: String,
        payload: String,
    ) {
        dao.upsertOverride(MemoryGraphOverrideEntity(
            id = "override:$assistantId:${Uuid.random()}",
            assistantId = assistantId,
            targetKind = targetKind,
            targetId = targetId,
            operation = operation,
            payload = payload,
            createdAt = System.currentTimeMillis(),
        ))
        val correctedText = runCatching {
            val obj = me.rerere.rikkahub.utils.JsonInstant.parseToJsonElement(payload) as? kotlinx.serialization.json.JsonObject
            obj?.get("label")?.jsonPrimitive?.contentOrNull
                ?: obj?.get("statement")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (!correctedText.isNullOrBlank()) {
            indexText(
                sourceKind = if (targetKind == "node") MemorySourceKind.GRAPH_NODE else MemorySourceKind.GRAPH_RELATION,
                sourceRefId = targetId,
                assistantId = assistantId,
                text = correctedText,
            )
        }
    }

    fun observeProcessingStates(assistantId: String) = dao.observeProcessingStates(assistantId)

    suspend fun getDocuments(assistant: Assistant): List<MemoryDocumentEntity> {
        ensureDocuments(assistant)
        return dao.getDocuments(assistant.id.toString())
    }

    suspend fun ensureDocuments(assistant: Assistant) = database.withTransaction {
        val now = System.currentTimeMillis()
        val assistantId = assistant.id.toString()
        val existing = dao.getDocuments(assistantId).associateBy { it.kind }
        listOf(
            MemoryDocumentKind.USER_PROFILE to assistant.userProfileCharLimit,
            MemoryDocumentKind.CHARACTER_MEMORY to assistant.characterMemoryCharLimit,
        ).forEach { (kind, requestedLimit) ->
            val key = kind.name
            val limit = requestedLimit.coerceIn(MIN_MEMORY_DOCUMENT_CHAR_LIMIT, MAX_MEMORY_DOCUMENT_CHAR_LIMIT)
            val current = existing[key]
            if (current == null) {
                val id = documentId(assistantId, kind)
                dao.upsertDocument(
                    MemoryDocumentEntity(
                        id = id,
                        assistantId = assistantId,
                        kind = key,
                        charLimit = limit,
                        updatedAt = now,
                    )
                )
                dao.insertRevision(
                    MemoryDocumentRevisionEntity(
                        id = "$id:0",
                        documentId = id,
                        revision = 0,
                        content = "",
                        reason = "Document created",
                        source = "system",
                        createdAt = now,
                    )
                )
            } else if (current.charLimit != limit) {
                dao.upsertDocument(current.copy(charLimit = limit, updatedAt = now))
            }
        }
    }

    suspend fun updateDocument(
        assistant: Assistant,
        kind: MemoryDocumentKind,
        content: String,
        expectedRevision: Long,
        reason: String,
        source: String,
    ): DocumentUpdateResult = database.withTransaction {
        ensureDocuments(assistant)
        val current = dao.getDocument(assistant.id.toString(), kind.name)
            ?: return@withTransaction DocumentUpdateResult(false, -1, 0, "Document is unavailable")
        val count = content.memoryCodePointCount()
        if (count > current.charLimit) {
            return@withTransaction DocumentUpdateResult(
                applied = false,
                revision = current.revision,
                codePointCount = count,
                error = "Document is $count/${current.charLimit} characters; compact it before saving.",
            )
        }
        if (current.revision != expectedRevision) {
            return@withTransaction DocumentUpdateResult(false, current.revision, count, "Document changed; reload and retry.")
        }
        if (current.content == content) {
            return@withTransaction DocumentUpdateResult(true, current.revision, count)
        }
        val nextRevision = current.revision + 1
        val now = System.currentTimeMillis()
        dao.insertRevision(
            MemoryDocumentRevisionEntity(
                id = "${current.id}:$nextRevision",
                documentId = current.id,
                revision = nextRevision,
                content = content,
                diff = buildReadableDiff(current.content, content),
                reason = reason,
                source = source,
                createdAt = now,
            )
        )
        dao.upsertDocument(current.copy(content = content, revision = nextRevision, updatedAt = now))
        indexText(
            sourceKind = if (kind == MemoryDocumentKind.USER_PROFILE) MemorySourceKind.USER_PROFILE else MemorySourceKind.CHARACTER_MEMORY,
            sourceRefId = current.id,
            assistantId = assistant.id.toString(),
            text = content,
            recordedAt = now,
        )
        DocumentUpdateResult(true, nextRevision, count)
    }

    suspend fun restoreDocument(
        assistant: Assistant,
        kind: MemoryDocumentKind,
        revision: Long,
    ): DocumentUpdateResult {
        ensureDocuments(assistant)
        val current = dao.getDocument(assistant.id.toString(), kind.name)
            ?: return DocumentUpdateResult(false, -1, 0, "Document is unavailable")
        val snapshot = dao.getRevisions(current.id).firstOrNull { it.revision == revision }
            ?: return DocumentUpdateResult(false, current.revision, current.content.memoryCodePointCount(), "Revision not found")
        return updateDocument(
            assistant = assistant,
            kind = kind,
            content = snapshot.content,
            expectedRevision = current.revision,
            reason = "Restored revision $revision",
            source = "restore",
        )
    }

    suspend fun indexConversation(conversation: Conversation) = database.withTransaction {
        val assistantId = conversation.assistantId.toString()
        val activeIds = conversation.currentMessages.map { it.id.toString() }.toSet()
        dao.deactivateConversationRawRows(conversation.id.toString())
        conversation.messageNodes.forEach { node ->
            node.messages.forEach { message ->
                val text = message.toContentText().trim()
                if (text.isNotBlank()) {
                    val timestamp = message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    indexText(
                        sourceKind = MemorySourceKind.RAW_CHAT,
                        sourceRefId = message.id.toString(),
                        assistantId = assistantId,
                        conversationId = conversation.id.toString(),
                        messageId = message.id.toString(),
                        nodeId = node.id.toString(),
                        versionTag = message.versionTag,
                        speaker = message.role.name,
                        text = text,
                        recordedAt = timestamp,
                        active = message.id.toString() in activeIds,
                    )
                }
            }
        }
        dao.upsertProcessingState(
            MemoryProcessingStateEntity(
                conversationId = conversation.id.toString(),
                assistantId = assistantId,
                branchSignature = branchSignature(conversation),
                indexedAt = System.currentTimeMillis(),
                processedAt = dao.getProcessingState(conversation.id.toString())?.processedAt ?: 0,
            )
        )
    }

    suspend fun indexText(
        sourceKind: MemorySourceKind,
        sourceRefId: String,
        assistantId: String,
        text: String,
        conversationId: String? = null,
        messageId: String? = null,
        nodeId: String? = null,
        versionTag: String? = null,
        speaker: String? = null,
        frame: String? = null,
        recordedAt: Long = System.currentTimeMillis(),
        eventStart: Long = 0,
        eventEnd: Long = 0,
        active: Boolean = true,
    ) {
        val existing = dao.findSearchRow(sourceKind.name, sourceRefId)
        val rowId = if (existing == null) {
            dao.insertSearchRow(
                MemorySearchRowEntity(
                    sourceKind = sourceKind.name,
                    sourceRefId = sourceRefId,
                    assistantId = assistantId,
                    conversationId = conversationId,
                    messageId = messageId,
                    nodeId = nodeId,
                    versionTag = versionTag,
                    speaker = speaker,
                    frame = frame,
                    eventStart = eventStart,
                    eventEnd = eventEnd,
                    recordedAt = recordedAt,
                    active = active,
                    text = text,
                )
            )
        } else {
            dao.updateSearchRow(
                rowId = existing.rowId,
                assistantId = assistantId,
                conversationId = conversationId,
                messageId = messageId,
                nodeId = nodeId,
                versionTag = versionTag,
                speaker = speaker,
                frame = frame,
                eventStart = eventStart,
                eventEnd = eventEnd,
                recordedAt = recordedAt,
                active = active,
                text = text,
            )
            existing.rowId
        }
        if (rowId > 0) dao.upsertFts(MemorySearchFtsEntity(rowId, text))
    }

    suspend fun search(
        assistantId: String,
        query: String,
        limit: Int,
        start: Long? = null,
        end: Long? = null,
    ): List<HybridMemorySearchResult> {
        val boundedLimit = limit.coerceIn(1, 8)
        val ftsQuery = query
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }
            .distinct()
            .take(12)
            .joinToString(" OR ") { "\"${it.replace("\"", "\"\"")}\"*" }
        val initialRows = buildList {
            if (ftsQuery.isNotBlank()) addAll(runCatching { dao.search(assistantId, ftsQuery, boundedLimit * 4) }.getOrDefault(emptyList()))
            if (start != null || end != null) {
                addAll(dao.searchByTime(assistantId, start ?: 0, end ?: Long.MAX_VALUE, boundedLimit * 4))
            }
        }.distinctBy { it.rowId }
        val graphNodeIds = initialRows.filter { it.sourceKind == MemorySourceKind.GRAPH_NODE.name }
            .take(4).map { it.sourceRefId }
        val expandedRows = if (graphNodeIds.isNotEmpty()) {
            val firstHop = dao.getEdgesForNodes(assistantId, graphNodeIds, boundedLimit * 4)
            val neighborIds = firstHop.flatMap { listOfNotNull(it.subjectId, it.objectId) }.distinct()
            val secondHop = if (neighborIds.isNotEmpty()) dao.getEdgesForNodes(assistantId, neighborIds, boundedLimit * 4) else emptyList()
            val sourceIds = (neighborIds + firstHop.map { it.id } + secondHop.map { it.id }).distinct()
            if (sourceIds.isEmpty()) emptyList() else dao.getSearchRowsForSources(assistantId, sourceIds, boundedLimit * 8)
        } else emptyList()
        val rows = (initialRows + expandedRows).distinctBy { it.rowId }
        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotBlank() }
        return rows.map { row ->
            val lowered = row.text.lowercase()
            val lexical = terms.count { lowered.contains(it) }.toFloat() / terms.size.coerceAtLeast(1)
            val recency = (1f / (1f + ((System.currentTimeMillis() - row.recordedAt).coerceAtLeast(0) / 86_400_000f / 30f)))
            row to (lexical * 0.8f + recency * 0.2f)
        }.sortedByDescending { it.second }
            .take(boundedLimit)
            .map { (row, score) -> row.toResult(score) }
    }

    suspend fun hotDigests(assistantId: String, query: String = "", now: Long = System.currentTimeMillis()): List<MemoryConversationDigestEntity> {
        val cutoff = now - 14L * 86_400_000L
        val queryTerms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.length >= 2 }.toSet()
        return dao.getDigests(assistantId)
            .filter { it.pinned || it.openThreads != "[]" || it.lastReinforcedAt >= cutoff || it.recordedAt >= cutoff }
            .sortedByDescending { digest ->
                val text = digest.summary.lowercase()
                val relevance = queryTerms.count(text::contains) * 20.0
                val recency = 20.0 / (1.0 + ((now - digest.recordedAt).coerceAtLeast(0) / 86_400_000.0))
                val reinforcement = if (digest.lastReinforcedAt >= cutoff) 12.0 else 0.0
                relevance + recency + digest.importance * 4 + reinforcement +
                    (if (digest.openThreads != "[]") 18 else 0) + (if (digest.pinned) 100 else 0)
            }
            .fold(mutableListOf()) { selected, digest ->
                val nextLength = selected.sumOf { it.summary.memoryCodePointCount() } + digest.summary.memoryCodePointCount()
                if (selected.size < 3 && nextLength <= 1200) selected += digest
                selected
            }
    }

    suspend fun setDigestPinned(id: String, pinned: Boolean) = dao.setDigestPinned(id, pinned)

    suspend fun dismissDigest(id: String) = dao.dismissDigest(id)

    suspend fun upsertDigest(digest: MemoryConversationDigestEntity) {
        dao.upsertDigest(digest)
        indexText(
            sourceKind = MemorySourceKind.CONTINUITY_DIGEST,
            sourceRefId = digest.id,
            assistantId = digest.assistantId,
            conversationId = digest.conversationId,
            text = digest.summary,
            recordedAt = digest.recordedAt,
            eventStart = digest.eventStart,
            eventEnd = digest.eventEnd,
        )
    }

    suspend fun upsertGraphNode(node: MemoryGraphNodeEntity, conversationId: String?, excerpt: String) {
        dao.upsertNode(node)
        dao.upsertProvenance(
            MemoryGraphProvenanceEntity(
                id = "node:${node.id}:${conversationId.orEmpty()}",
                graphKind = "node",
                graphId = node.id,
                conversationId = conversationId,
                excerpt = excerpt.take(500),
                createdAt = System.currentTimeMillis(),
            )
        )
        indexText(MemorySourceKind.GRAPH_NODE, node.id, node.assistantId, listOfNotNull(node.label, node.summary).joinToString(" "), conversationId = conversationId, recordedAt = node.updatedAt, frame = node.frame)
    }

    suspend fun upsertGraphEdge(edge: MemoryGraphEdgeEntity, conversationId: String?, excerpt: String) {
        dao.upsertEdge(edge)
        dao.upsertProvenance(
            MemoryGraphProvenanceEntity(
                id = "edge:${edge.id}:${conversationId.orEmpty()}",
                graphKind = "edge",
                graphId = edge.id,
                conversationId = conversationId,
                excerpt = excerpt.take(500),
                createdAt = System.currentTimeMillis(),
            )
        )
        indexText(MemorySourceKind.GRAPH_RELATION, edge.id, edge.assistantId, edge.statement, conversationId = conversationId, recordedAt = edge.updatedAt, eventStart = edge.eventStart, eventEnd = edge.eventEnd, frame = edge.frame)
    }

    suspend fun markProcessed(conversationId: String, assistantId: String, signature: String, error: String? = null) {
        val current = dao.getProcessingState(conversationId)
        dao.upsertProcessingState(
            MemoryProcessingStateEntity(
                conversationId = conversationId,
                assistantId = assistantId,
                branchSignature = signature,
                indexedAt = current?.indexedAt ?: 0,
                processedAt = if (error == null) System.currentTimeMillis() else current?.processedAt ?: 0,
                lastError = error,
            )
        )
    }

    fun conversationBranchSignature(conversation: Conversation): String = branchSignature(conversation)

    suspend fun deleteAssistantData(assistantId: String) = dao.deleteAssistantData(assistantId)

    suspend fun onConversationDeleted(conversationId: String) = dao.onConversationDeleted(conversationId)

    suspend fun indexEntry(assistantId: String, memory: AssistantMemory) {
        indexText(
            sourceKind = MemorySourceKind.ENTRY,
            sourceRefId = memory.id.toString(),
            assistantId = assistantId,
            text = memory.content,
            recordedAt = memory.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    suspend fun deleteIndexedSource(sourceKind: MemorySourceKind, sourceId: String) {
        dao.findSearchRow(sourceKind.name, sourceId)?.let { dao.deleteFts(it.rowId) }
        dao.deleteSearchRow(sourceKind.name, sourceId)
    }

    suspend fun backfillEmbeddings(assistantId: String, limit: Int = 64): Int {
        val embeddingService = embeddingService ?: return 0
        val modelId = embeddingService.getEmbeddingModelId(assistantId)
        val rows = dao.getRowsNeedingEmbedding(assistantId, modelId, limit)
        if (rows.isEmpty()) return 0
        val resolved = mutableMapOf<Long, ByteArray>()
        val missing = mutableListOf<MemorySearchRowEntity>()
        rows.forEach { row ->
            val cached = embeddingCacheDao.getSourceEmbedding(row.sourceRefId, row.sourceKind, modelId)
            if (cached?.embeddingBlob != null) resolved[row.rowId] = cached.embeddingBlob else missing += row
        }
        if (missing.isNotEmpty()) {
            val generated = embeddingService.embedBatch(missing.map { it.text }, assistantId)
            missing.zip(generated.embeddings).forEach { (row, vector) ->
                val blob = vector.toFloatArray().toByteArray()
                resolved[row.rowId] = blob
                embeddingCacheDao.insertEmbedding(EmbeddingCacheEntity(
                    memoryId = row.sourceRefId.hashCode(),
                    memoryType = hybridEmbeddingType(row.sourceKind),
                    modelId = generated.modelId,
                    embedding = "",
                    embeddingBlob = blob,
                    sourceId = row.sourceRefId,
                    sourceKind = row.sourceKind,
                ))
            }
        }
        database.withTransaction {
            rows.forEach { row ->
                val blob = resolved[row.rowId] ?: return@forEach
                dao.updateSearchEmbedding(row.rowId, blob, modelId)
                when (row.sourceKind) {
                    MemorySourceKind.ENTRY.name -> row.sourceRefId.toIntOrNull()?.let { id ->
                        memoryDao.getMemoryById(id)?.let { memoryDao.updateMemory(it.copy(embeddingBlob = blob, embeddingModelId = modelId)) }
                    }
                    MemorySourceKind.USER_PROFILE.name, MemorySourceKind.CHARACTER_MEMORY.name -> dao.updateDocumentEmbedding(row.sourceRefId, blob, modelId)
                    MemorySourceKind.CONTINUITY_DIGEST.name -> dao.updateDigestEmbedding(row.sourceRefId, blob, modelId)
                    MemorySourceKind.GRAPH_NODE.name -> dao.updateNodeEmbedding(row.sourceRefId, blob, modelId)
                    MemorySourceKind.GRAPH_RELATION.name -> dao.updateEdgeEmbedding(row.sourceRefId, blob, modelId)
                }
            }
        }
        return resolved.size
    }

    suspend fun rebuildSearchIndex(assistant: Assistant, conversations: List<Conversation>) {
        val assistantId = assistant.id.toString()
        database.withTransaction {
            dao.deleteAssistantFts(assistantId)
            dao.deleteAssistantSearchRows(assistantId)
        }
        memoryDao.getMemoriesOfAssistant(assistantId).forEach { memory ->
            indexEntry(assistantId, AssistantMemory(memory.id, memory.content, memory.type, memory.embedding != null, memory.embeddingModelId, memory.createdAt))
        }
        getDocuments(assistant).forEach { document ->
            indexText(
                if (document.kind == MemoryDocumentKind.USER_PROFILE.name) MemorySourceKind.USER_PROFILE else MemorySourceKind.CHARACTER_MEMORY,
                document.id, assistantId, document.content, recordedAt = document.updatedAt,
            )
        }
        dao.getDigests(assistantId).forEach { digest -> upsertDigest(digest) }
        dao.getAllNodes(assistantId).forEach { node ->
            indexText(MemorySourceKind.GRAPH_NODE, node.id, assistantId, listOfNotNull(node.label, node.summary).joinToString(" "), recordedAt = node.updatedAt)
        }
        dao.getAllEdges(assistantId).forEach { edge ->
            indexText(MemorySourceKind.GRAPH_RELATION, edge.id, assistantId, edge.statement, recordedAt = edge.updatedAt, eventStart = edge.eventStart, eventEnd = edge.eventEnd)
        }
        conversations.forEach { indexConversation(it) }
        dao.resetAssistantProcessingState(assistantId)
    }

    suspend fun buildConversionInput(
        assistant: Assistant,
        direction: MemoryConversionDirection,
    ): MemoryConversionInput {
        ensureDocuments(assistant)
        val assistantId = assistant.id.toString()
        val state = dao.getConversionState(assistantId, direction.name)
        val watermark = state?.sourceRevision ?: 0L
        val documents = dao.getDocuments(assistantId).associateBy { it.kind }
        val entries = memoryDao.getMemoriesOfAssistant(assistantId)
        val changedRows = dao.getChangedSearchRows(assistantId, watermark)
        val sourceRevision = buildList {
            add(watermark)
            addAll(changedRows.map { it.recordedAt })
            if (direction == MemoryConversionDirection.ENTRY_TO_DOCUMENT) {
                addAll(entries.map { maxOf(it.createdAt, it.lastAccessedAt) })
            } else {
                addAll(documents.values.map { it.updatedAt })
                addAll(dao.getDigests(assistantId).map { it.recordedAt })
                addAll(dao.getAllNodes(assistantId).map { it.updatedAt })
                addAll(dao.getAllEdges(assistantId).map { it.updatedAt })
            }
        }.maxOrNull() ?: watermark
        val evidence = buildList {
            if (direction == MemoryConversionDirection.ENTRY_TO_DOCUMENT) {
                entries.filter { maxOf(it.createdAt, it.lastAccessedAt) > watermark }
                    .forEach { add("Entry ${it.id}: ${it.content}") }
            } else {
                documents.values.filter { it.updatedAt > watermark }
                    .forEach { add("${it.kind}: ${it.content}") }
            }
            changedRows.take(100).forEach { add("${it.sourceKind}: ${it.text.take(600)}") }
        }.distinct().take(200)
        return MemoryConversionInput(
            assistantId = assistantId,
            direction = direction,
            sourceRevision = sourceRevision,
            previousWatermark = watermark,
            userProfileRevision = documents[MemoryDocumentKind.USER_PROFILE.name]?.revision ?: 0,
            characterMemoryRevision = documents[MemoryDocumentKind.CHARACTER_MEMORY.name]?.revision ?: 0,
            userProfile = documents[MemoryDocumentKind.USER_PROFILE.name]?.content.orEmpty(),
            characterMemory = documents[MemoryDocumentKind.CHARACTER_MEMORY.name]?.content.orEmpty(),
            entries = entries.map { AssistantMemory(it.id, it.content, it.type, it.embedding != null, it.embeddingModelId, it.createdAt) },
            changedEvidence = evidence,
        )
    }

    suspend fun applyConversionPreview(
        assistant: Assistant,
        preview: MemoryConversionPreview,
    ): MemoryConversionApplyResult = database.withTransaction {
        if (preview.assistantId != assistant.id.toString()) {
            return@withTransaction MemoryConversionApplyResult(false, error = "Preview belongs to another character")
        }
        val currentInput = buildConversionInput(assistant, preview.direction)
        if (currentInput.sourceRevision != preview.sourceRevision) {
            return@withTransaction MemoryConversionApplyResult(false, error = "Source memories changed; generate a new preview")
        }
        var added = 0
        var updated = 0
        when (preview.direction) {
            MemoryConversionDirection.ENTRY_TO_DOCUMENT -> {
                val documents = dao.getDocuments(preview.assistantId).associateBy { it.kind }
                val replacements = listOf(
                    MemoryDocumentKind.USER_PROFILE to preview.userProfileReplacement,
                    MemoryDocumentKind.CHARACTER_MEMORY to preview.characterMemoryReplacement,
                )
                replacements.forEach { (kind, replacement) ->
                    if (replacement == null) return@forEach
                    val current = documents[kind.name] ?: return@forEach
                    val expected = if (kind == MemoryDocumentKind.USER_PROFILE) preview.expectedUserProfileRevision else preview.expectedCharacterMemoryRevision
                    if (current.revision != expected) {
                        return@withTransaction MemoryConversionApplyResult(false, error = "A target document changed; generate a new preview")
                    }
                    if (replacement.memoryCodePointCount() > current.charLimit) {
                        return@withTransaction MemoryConversionApplyResult(false, error = "Converted ${kind.name.lowercase()} exceeds its character limit")
                    }
                    if (replacement != current.content) {
                        val revision = current.revision + 1
                        val now = System.currentTimeMillis()
                        dao.insertRevision(MemoryDocumentRevisionEntity(
                            id = "${current.id}:$revision",
                            documentId = current.id,
                            revision = revision,
                            content = replacement,
                            diff = buildReadableDiff(current.content, replacement),
                            reason = "Applied Entry-based conversion preview",
                            source = "conversion",
                            createdAt = now,
                        ))
                        dao.upsertDocument(current.copy(content = replacement, revision = revision, updatedAt = now))
                        indexText(
                            if (kind == MemoryDocumentKind.USER_PROFILE) MemorySourceKind.USER_PROFILE else MemorySourceKind.CHARACTER_MEMORY,
                            current.id,
                            preview.assistantId,
                            replacement,
                            recordedAt = now,
                        )
                    }
                }
            }
            MemoryConversionDirection.DOCUMENT_TO_ENTRY -> {
                val existing = memoryDao.getMemoriesOfAssistant(preview.assistantId)
                val byNormalized = existing.associateBy { normalizeMemoryText(it.content) }.toMutableMap()
                preview.entryProposals.forEach { proposal ->
                    val content = proposal.content.trim()
                    if (content.isBlank()) return@forEach
                    val normalized = normalizeMemoryText(content)
                    val target = proposal.existingEntryId?.let { id -> existing.firstOrNull { it.id == id } }
                        ?: byNormalized[normalized]
                    if (target == null) {
                        val now = System.currentTimeMillis()
                        val id = memoryDao.insertMemory(MemoryEntity(assistantId = preview.assistantId, content = content, createdAt = now, lastAccessedAt = now)).toInt()
                        indexText(MemorySourceKind.ENTRY, id.toString(), preview.assistantId, content, recordedAt = now)
                        added++
                    } else if (target.content != content && proposal.existingEntryId != null) {
                        val updatedEntity = target.copy(content = content, embedding = null, embeddingBlob = null, embeddingModelId = null, lastAccessedAt = System.currentTimeMillis())
                        memoryDao.updateMemory(updatedEntity)
                        indexText(MemorySourceKind.ENTRY, target.id.toString(), preview.assistantId, content, recordedAt = updatedEntity.lastAccessedAt)
                        updated++
                    }
                }
            }
        }
        dao.upsertConversionState(MemoryConversionStateEntity(
            assistantId = preview.assistantId,
            direction = preview.direction.name,
            sourceRevision = preview.sourceRevision,
            convertedAt = System.currentTimeMillis(),
        ))
        MemoryConversionApplyResult(true, added, updated)
    }

    private fun documentId(assistantId: String, kind: MemoryDocumentKind) = "$assistantId:${kind.name.lowercase()}"

    private fun branchSignature(conversation: Conversation): String = conversation.currentMessages
        .joinToString("|") { "${it.id}:${it.versionTag.orEmpty()}" }
        .hashCode().toUInt().toString(16)

    private fun buildReadableDiff(before: String, after: String): String {
        val beforeLines = before.lines().filter { it.isNotBlank() }.toSet()
        val afterLines = after.lines().filter { it.isNotBlank() }.toSet()
        return buildList {
            (beforeLines - afterLines).forEach { add("- $it") }
            (afterLines - beforeLines).forEach { add("+ $it") }
        }.joinToString("\n")
    }

    private fun normalizeMemoryText(value: String) = value.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun hybridEmbeddingType(sourceKind: String) = when (sourceKind) {
        MemorySourceKind.ENTRY.name -> 0
        MemorySourceKind.CONTINUITY_DIGEST.name -> 2
        MemorySourceKind.USER_PROFILE.name -> 3
        MemorySourceKind.CHARACTER_MEMORY.name -> 4
        MemorySourceKind.GRAPH_NODE.name -> 5
        MemorySourceKind.GRAPH_RELATION.name -> 6
        else -> 9
    }
}

private fun MemorySearchRowEntity.toResult(score: Float) = HybridMemorySearchResult(
    sourceKind = runCatching { MemorySourceKind.valueOf(sourceKind) }.getOrDefault(MemorySourceKind.RAW_CHAT),
    sourceId = sourceRefId,
    text = text,
    recordedAt = recordedAt,
    conversationId = conversationId,
    messageId = messageId,
    speaker = speaker,
    frame = frame,
    score = score,
    embeddingBlob = embeddingBlob,
    embeddingModelId = embeddingModelId,
)
