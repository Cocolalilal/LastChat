package me.rerere.rikkahub.data.memory

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.ai.rag.toByteArray
import me.rerere.rikkahub.data.ai.rag.toFloatArray
import me.rerere.rikkahub.data.ai.rag.toListOfFloatArrays
import me.rerere.rikkahub.data.db.dao.TemporalMemoryDao
import me.rerere.rikkahub.data.db.entity.MemoryClaimEntity
import me.rerere.rikkahub.data.db.entity.MemoryClaimKind
import me.rerere.rikkahub.data.db.entity.MemoryClaimStatus
import me.rerere.rikkahub.data.db.entity.MemoryEpisodeV3Entity
import me.rerere.rikkahub.data.db.entity.MemoryIngestStateEntity
import me.rerere.rikkahub.data.db.entity.MemoryProjectionEntity
import me.rerere.rikkahub.data.db.entity.MemoryReality
import me.rerere.rikkahub.data.db.entity.MemorySensitivity
import me.rerere.rikkahub.data.db.entity.MemorySourceKind
import me.rerere.rikkahub.data.db.entity.MemorySourceV3Entity
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.model.MemoryRerankMode
import kotlin.uuid.Uuid

class TemporalMemoryRepository(
    private val dao: TemporalMemoryDao,
    private val legacyRepository: MemoryRepository,
    private val embeddingService: EmbeddingService,
    private val reranker: MemoryReranker,
) {
    private val scopeLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun importLegacyMemories(assistantId: String) = scopeLock(assistantId).withLock {
        val legacyRows = legacyRepository.getMemoryEntitiesOfAssistant(assistantId)
        val currentIds = legacyRows.mapTo(mutableSetOf()) { it.id }
        dao.getLegacyClaims(assistantId)
            .filter { it.legacyMemoryId !in currentIds }
            .forEach { dao.deleteClaimIndexed(it.id) }
        legacyRows.forEach { legacy ->
            val blob = legacy.embeddingBlob ?: legacy.embedding?.let { encoded ->
                runCatching {
                    me.rerere.rikkahub.utils.JsonInstant.decodeFromString<List<Float>>(encoded)
                        .toFloatArray()
                        .let(::listOf)
                        .toByteArray()
                }.getOrNull()
            }
            val existing = dao.getClaimByLegacyId(legacy.id)
            val claim = MemoryClaimEntity(
                    id = existing?.id ?: 0,
                    assistantId = assistantId,
                    subject = "user",
                    predicate = "notes",
                    objectValue = legacy.content.take(160),
                    statement = legacy.content,
                    kind = MemoryClaimKind.DURATIVE,
                    confidence = 1f,
                    importance = 3,
                    observedAt = legacy.createdAt,
                    recordedAt = legacy.createdAt,
                    lastConfirmedAt = legacy.createdAt,
                    lastAccessedAt = legacy.lastAccessedAt,
                    source = MemorySourceKind.IMPORTED,
                    embeddingBlob = blob,
                    embeddingModelId = legacy.embeddingModelId,
                    legacyMemoryId = legacy.id,
                )
            if (existing == null) dao.insertClaimIndexed(claim) else dao.updateClaimIndexed(claim)
        }
    }

    suspend fun recordSources(
        assistantId: String,
        conversationId: String,
        messages: List<SourceMessage>,
    ) = scopeLock(assistantId).withLock {
        dao.demoteConversationSources(conversationId)
        messages.forEach { message ->
            dao.insertSourceIndexed(
                MemorySourceV3Entity(
                    assistantId = assistantId,
                    conversationId = conversationId,
                    messageId = message.id,
                    role = message.role,
                    excerpt = message.text.take(2_000),
                    contentHash = sha256(message.text),
                    observedAt = message.observedAt,
                )
            )
        }
        val selectedIds = messages.map { it.id }
        dao.selectSources(selectedIds)
        dao.demoteClaimsOutsideBranch(conversationId, selectedIds)
        dao.reactivateClaimsInBranch(conversationId, selectedIds)
    }

    suspend fun applyExtraction(
        assistantId: String,
        conversationId: String,
        messages: List<SourceMessage>,
        extraction: MemoryExtractionEnvelope,
    ) = scopeLock(assistantId).withLock {
        val now = System.currentTimeMillis()
        extraction.operations.take(MAX_OPERATIONS_PER_PASS).forEach { operation ->
            applyOperation(assistantId, conversationId, messages, operation, now)
        }
        extraction.episode?.let { episode ->
            val existing = dao.getEpisode(conversationId, episode.sceneKey.take(80))
            val entity = MemoryEpisodeV3Entity(
                id = existing?.id ?: 0,
                assistantId = assistantId,
                title = episode.title.trim().take(80),
                summary = episode.summary.trim().take(600),
                eventStart = episode.eventStart ?: messages.firstOrNull()?.observedAt ?: now,
                eventEnd = episode.eventEnd ?: messages.lastOrNull()?.observedAt,
                reality = episode.reality.toReality(),
                frame = episode.frame?.take(80),
                importance = episode.importance.coerceIn(1, 5),
                confidence = 0.8f,
                conversationId = conversationId,
                sceneKey = episode.sceneKey.take(80),
                sourceMessageIds = messages.joinToString(",") { it.id },
                recordedAt = existing?.recordedAt ?: now,
                lastAccessedAt = existing?.lastAccessedAt ?: now,
                timesRetrieved = existing?.timesRetrieved ?: 0,
                embeddingBlob = existing?.embeddingBlob,
                embeddingModelId = existing?.embeddingModelId,
            )
            dao.upsertEpisodeIndexed(entity)
        }
        rebuildProjection(assistantId, now)
        messages.lastOrNull()?.let { last ->
            dao.upsertIngestState(
                MemoryIngestStateEntity(
                    conversationId = conversationId,
                    assistantId = assistantId,
                    lastMessageId = last.id,
                    lastMessageHash = sha256(last.text),
                    lastProcessedAt = now,
                )
            )
        }
    }

    suspend fun getIngestState(conversationId: String) = dao.getIngestState(conversationId)

    suspend fun exportMemory(assistantId: String): TemporalMemoryExport = TemporalMemoryExport(
        claims = dao.getAllClaims(assistantId).filter { it.source != MemorySourceKind.IMPORTED }.map { claim ->
            ExportedTemporalClaim(
                subject = claim.subject,
                predicate = claim.predicate,
                objectValue = claim.objectValue,
                statement = claim.statement,
                kind = claim.kind,
                reality = claim.reality,
                frame = claim.frame,
                status = claim.status,
                confidence = claim.confidence,
                importance = claim.importance,
                sensitivity = claim.sensitivity,
                validFrom = claim.validFrom,
                validUntil = claim.validUntil,
                observedAt = claim.observedAt,
                recordedAt = claim.recordedAt,
                source = claim.source,
            )
        },
        episodes = dao.getAllEpisodes(assistantId).map { episode ->
            ExportedTemporalEpisode(
                title = episode.title,
                summary = episode.summary,
                eventStart = episode.eventStart,
                eventEnd = episode.eventEnd,
                reality = episode.reality,
                frame = episode.frame,
                importance = episode.importance,
                status = episode.status,
                confidence = episode.confidence,
            )
        },
    )

    suspend fun importMemory(assistantId: String, export: TemporalMemoryExport) = scopeLock(assistantId).withLock {
        val now = System.currentTimeMillis()
        export.claims.forEach { claim ->
            dao.insertClaimIndexed(
                MemoryClaimEntity(
                    assistantId = assistantId,
                    subject = claim.subject,
                    predicate = claim.predicate,
                    objectValue = claim.objectValue,
                    statement = claim.statement,
                    kind = claim.kind,
                    reality = claim.reality,
                    frame = claim.frame,
                    status = claim.status,
                    confidence = claim.confidence,
                    importance = claim.importance,
                    sensitivity = claim.sensitivity,
                    validFrom = claim.validFrom,
                    validUntil = claim.validUntil,
                    observedAt = claim.observedAt,
                    recordedAt = claim.recordedAt,
                    source = claim.source,
                )
            )
        }
        export.episodes.forEachIndexed { index, episode ->
            dao.upsertEpisodeIndexed(
                MemoryEpisodeV3Entity(
                    assistantId = assistantId,
                    title = episode.title,
                    summary = episode.summary,
                    eventStart = episode.eventStart,
                    eventEnd = episode.eventEnd,
                    reality = episode.reality,
                    frame = episode.frame,
                    importance = episode.importance,
                    status = episode.status,
                    confidence = episode.confidence,
                    conversationId = "imported-$assistantId",
                    sceneKey = "imported-$index-${sha256(episode.title).take(12)}",
                    sourceMessageIds = "",
                    recordedAt = now,
                )
            )
        }
        rebuildProjection(assistantId, now)
    }

    suspend fun recall(
        assistantId: String,
        query: String,
        limit: Int = 8,
        timeStart: Long? = null,
        timeEnd: Long? = null,
        rerankMode: MemoryRerankMode = MemoryRerankMode.AUTOMATIC,
        rerankModelId: Uuid? = null,
        minimumRelevance: Float = 0f,
        includeCore: Boolean = true,
        includeEpisodes: Boolean = true,
        includePastChats: Boolean = true,
    ): TemporalRecallPacket = withContext(Dispatchers.IO) {
        importLegacyMemories(assistantId)
        val boundedLimit = limit.coerceIn(1, 20)
        val candidateLimit = (boundedLimit * 5).coerceAtMost(80)
        val ftsQuery = query.toFtsQuery()
        val lexicalClaims = if (!includeCore || ftsQuery.isBlank()) emptyList() else {
            runCatching { dao.searchClaimsFts(assistantId, ftsQuery, candidateLimit) }.getOrDefault(emptyList())
        }
        val temporalClaims = if (includeCore && timeStart != null && timeEnd != null) {
            dao.searchClaimsByTime(assistantId, timeStart, timeEnd, candidateLimit)
        } else emptyList()
        val currentClaims = if (includeCore) dao.getCurrentClaims(assistantId, candidateLimit) else emptyList()
        val claimPool = (lexicalClaims + temporalClaims + currentClaims).distinctBy { it.id }

        val queryEmbedding = runCatching { embeddingService.embed(query, assistantId).toFloatArray() }.getOrNull()
        val claimScores = claimPool.map { claim ->
            val lexicalRank = lexicalClaims.indexOfFirst { it.id == claim.id }.retrievalRankScore(0.72f)
            val temporalRank = temporalClaims.indexOfFirst { it.id == claim.id }.retrievalRankScore(0.72f)
            val semantic = if (queryEmbedding != null && claim.embeddingBlob != null) {
                claim.embeddingBlob.toListOfFloatArrays().maxOfOrNull {
                    VectorEngine.cosineSimilarity(queryEmbedding, it)
                }?.coerceIn(0f, 1f) ?: 0f
            } else 0f
            val currentBoost = if (claim.status == MemoryClaimStatus.ACTIVE) 0.08f else 0f
            val score = maxOf(lexicalRank, temporalRank, semantic) + currentBoost + claim.importance * 0.02f
            claim to score
        }

        val lexicalEpisodes = if (!includeEpisodes || ftsQuery.isBlank()) emptyList() else {
            runCatching { dao.searchEpisodesFts(assistantId, ftsQuery, candidateLimit) }.getOrDefault(emptyList())
        }
        val recentEpisodes = if (includeEpisodes) dao.getRecentEpisodes(assistantId, 3) else emptyList()
        val episodeScores = (lexicalEpisodes + recentEpisodes).distinctBy { it.id }.map { episode ->
            val lexicalRank = lexicalEpisodes.indexOfFirst { it.id == episode.id }.retrievalRankScore(0.72f)
            val recencyRank = recentEpisodes.indexOfFirst { it.id == episode.id }.retrievalRankScore(0.35f)
            episode to (maxOf(lexicalRank, recencyRank) + episode.importance * 0.02f)
        }

        val sourceScores = if (!includePastChats || ftsQuery.isBlank()) emptyList() else {
            runCatching { dao.searchSourcesFts(assistantId, ftsQuery, candidateLimit) }
                .getOrDefault(emptyList())
                .mapIndexed { index, source -> source to (index.retrievalRankScore(0.72f) + 0.03f) }
        }

        val rankedCandidates = buildList {
            claimScores.forEach { (claim, score) ->
                add(
                    TemporalRecallItem(
                        stableId = "claim:${claim.id}",
                        text = claim.statement,
                        timestamp = claim.validFrom ?: claim.observedAt,
                        score = score,
                        kind = when {
                            claim.source == MemorySourceKind.IMPORTED -> RecallKind.LEGACY
                            claim.status == MemoryClaimStatus.CLOSED -> RecallKind.HISTORICAL_FACT
                            else -> RecallKind.CURRENT_STATE
                        },
                        confidence = claim.confidence,
                        validUntil = claim.validUntil,
                    )
                )
            }
            episodeScores.forEach { (episode, score) ->
                add(
                    TemporalRecallItem(
                        stableId = "episode:${episode.id}",
                        text = "${episode.title}: ${episode.summary}",
                        timestamp = episode.eventStart,
                        score = score,
                        kind = RecallKind.EPISODE,
                        confidence = episode.confidence,
                    )
                )
            }
            sourceScores.forEach { (source, score) ->
                add(
                    TemporalRecallItem(
                        stableId = "source:${source.id}",
                        text = source.excerpt,
                        timestamp = source.observedAt,
                        score = score,
                        kind = RecallKind.PAST_CHAT,
                        confidence = 1f,
                    )
                )
            }
        }.sortedByDescending { it.score }
            .distinctBy { it.text.lowercase().replace(Regex("\\s+"), " ") }
        val relevantCandidates = rankedCandidates.filter { it.score >= minimumRelevance.coerceIn(0f, 1f) }
        val selected = reranker.rerank(rerankMode, rerankModelId, query, relevantCandidates)
            .take(boundedLimit)

        val now = System.currentTimeMillis()
        selected.mapNotNull { it.stableId.removePrefix("claim:").toLongOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.let { dao.markClaimsRetrieved(it, now) }
        selected.mapNotNull { it.stableId.removePrefix("episode:").toLongOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.let { dao.markEpisodesRetrieved(it, now) }
        TemporalRecallPacket(
            projection = if (includeCore) dao.getProjection(assistantId)?.content else null,
            items = selected,
        )
    }

    private suspend fun applyOperation(
        assistantId: String,
        conversationId: String,
        messages: List<SourceMessage>,
        operation: MemoryWriteOperation,
        now: Long,
    ) {
        val subject = operation.subject.normalizedKey("user")
        val predicate = operation.predicate.normalizedKey("notes")
        val statement = operation.statement.trim().take(MAX_STATEMENT_LENGTH)
        if (statement.isBlank()) return
        val currentClaims = dao.findCurrentClaims(assistantId, subject, predicate)
        val exactMatch = currentClaims.firstOrNull { candidate ->
            candidate.statement.normalizedText() == statement.normalizedText() ||
                candidate.objectValue?.normalizedText() == operation.objectValue?.normalizedText()
        }
        val existing = operation.replacesClaimId?.let { dao.getClaim(it) }
            ?: when (operation.op) {
                MemoryOperationType.SUPERSEDE,
                MemoryOperationType.CLOSE -> exactMatch ?: currentClaims.firstOrNull()
                MemoryOperationType.ADD,
                MemoryOperationType.REINFORCE -> exactMatch
            }

        when (operation.op) {
            MemoryOperationType.REINFORCE -> existing?.let {
                dao.updateClaimIndexed(
                    it.copy(
                        confidence = maxOf(it.confidence, operation.confidence.coerceIn(0f, 1f)),
                        status = MemoryClaimStatus.ACTIVE,
                        lastConfirmedAt = now,
                        timesReinforced = it.timesReinforced + 1,
                    )
                )
            }
            MemoryOperationType.CLOSE -> existing?.let {
                dao.updateClaimIndexed(it.copy(status = MemoryClaimStatus.CLOSED, validUntil = operation.validUntil ?: now))
            }
            MemoryOperationType.SUPERSEDE -> {
                existing?.let {
                    dao.updateClaimIndexed(it.copy(status = MemoryClaimStatus.SUPERSEDED, validUntil = operation.validFrom ?: now))
                }
                insertOperationClaim(assistantId, conversationId, messages, operation, now, subject, predicate, statement)
            }
            MemoryOperationType.ADD -> {
                if (existing != null) {
                    dao.updateClaimIndexed(
                        existing.copy(
                            confidence = maxOf(existing.confidence, operation.confidence.coerceIn(0f, 1f)),
                            lastConfirmedAt = now,
                            timesReinforced = existing.timesReinforced + 1,
                        )
                    )
                } else {
                    insertOperationClaim(assistantId, conversationId, messages, operation, now, subject, predicate, statement)
                }
            }
        }
    }

    private suspend fun insertOperationClaim(
        assistantId: String,
        conversationId: String,
        messages: List<SourceMessage>,
        operation: MemoryWriteOperation,
        now: Long,
        subject: String,
        predicate: String,
        statement: String,
    ) {
        val observedAt = operation.sourceMessageId
            ?.let { id -> messages.firstOrNull { it.id == id }?.observedAt }
            ?: messages.lastOrNull()?.observedAt
            ?: now
        val embedding = runCatching {
            embeddingService.embedWithModelId(statement, assistantId)
        }.getOrNull()
        dao.insertClaimIndexed(
            MemoryClaimEntity(
                assistantId = assistantId,
                subject = subject,
                predicate = predicate,
                objectValue = operation.objectValue?.take(160),
                statement = statement,
                kind = operation.kind.toClaimKind(),
                reality = operation.reality.toReality(),
                frame = operation.frame?.take(80),
                status = if (operation.confidence >= 0.8f) MemoryClaimStatus.ACTIVE else MemoryClaimStatus.PROVISIONAL,
                confidence = operation.confidence.coerceIn(0f, 1f),
                importance = operation.importance.coerceIn(1, 5),
                sensitivity = if (operation.sensitive) MemorySensitivity.SENSITIVE else MemorySensitivity.NORMAL,
                validFrom = operation.validFrom,
                validUntil = operation.validUntil,
                observedAt = observedAt,
                recordedAt = now,
                lastConfirmedAt = observedAt,
                sourceConversationId = conversationId,
                sourceMessageId = operation.sourceMessageId,
                embeddingBlob = embedding?.embeddings?.map { it.toFloatArray() }?.toByteArray(),
                embeddingModelId = embedding?.modelId,
            )
        )
    }

    private suspend fun rebuildProjection(assistantId: String, now: Long) {
        val claims = dao.getCurrentClaims(assistantId, 24)
        val projection = claims.groupBy { it.predicate }.entries
            .sortedByDescending { (_, values) -> values.maxOf { it.importance } }
            .joinToString("\n") { (_, values) ->
                values.take(3).joinToString("; ") { "- ${it.statement}" }
            }
            .take(MAX_PROJECTION_LENGTH)
        dao.upsertProjection(MemoryProjectionEntity(assistantId, projection, now, now))
    }

    private fun scopeLock(assistantId: String) = scopeLocks.getOrPut(assistantId) { Mutex() }

    companion object {
        private const val MAX_OPERATIONS_PER_PASS = 12
        private const val MAX_STATEMENT_LENGTH = 600
        private const val MAX_PROJECTION_LENGTH = 2_400

        fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

private fun Int.retrievalRankScore(base: Float): Float =
    if (this < 0) 0f else base / (1f + this * 0.15f)

private fun String.toFtsQuery(): String = lowercase()
    .split(Regex("[^\\p{L}\\p{N}_-]+"))
    .filter { it.length > 1 }
    .distinct()
    .take(12)
    .joinToString(" OR ") { "\"${it.replace("\"", "\"\"")}\"" }

private fun String.normalizedKey(fallback: String): String = lowercase()
    .replace(Regex("[^a-z0-9_]+"), "_")
    .trim('_')
    .take(48)
    .ifBlank { fallback }

private fun String.normalizedText(): String = lowercase().replace(Regex("\\s+"), " ").trim()

private fun String.toClaimKind(): Int = when (lowercase()) {
    "point", "event" -> MemoryClaimKind.POINT
    "habit" -> MemoryClaimKind.HABIT
    "plan", "future" -> MemoryClaimKind.PLAN
    else -> MemoryClaimKind.DURATIVE
}

private fun String.toReality(): Int = when (lowercase()) {
    "fiction", "fictional", "roleplay" -> MemoryReality.FICTION
    "hypothetical", "hypothesis" -> MemoryReality.HYPOTHETICAL
    else -> MemoryReality.REAL
}
