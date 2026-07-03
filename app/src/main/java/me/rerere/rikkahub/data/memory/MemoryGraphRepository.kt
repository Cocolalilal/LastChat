package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryActivityDao
import me.rerere.rikkahub.data.db.dao.MemoryConversationStateDao
import me.rerere.rikkahub.data.db.dao.MemoryEdgeDao
import me.rerere.rikkahub.data.db.dao.MemoryNodeDao
import me.rerere.rikkahub.data.db.dao.MemoryProvenanceDao
import me.rerere.rikkahub.data.db.entity.MemActivityKind
import me.rerere.rikkahub.data.db.entity.MemActivityState
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryProvenanceEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MemoryExport
import me.rerere.rikkahub.data.model.toExport
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.Uuid

/**
 * DAO façade for all memory reads plus the structural deletes that honour the store's privacy
 * boundaries. Writes to nodes/edges go exclusively through [MemoryOpApplier]; this repository only
 * reads, and performs the two explicit user-driven removals (character wipe, conversation forget).
 */
class MemoryGraphRepository(
    private val db: AppDatabase,
    private val nodeDao: MemoryNodeDao,
    private val edgeDao: MemoryEdgeDao,
    private val provenanceDao: MemoryProvenanceDao,
    private val activityDao: MemoryActivityDao,
    private val conversationStateDao: MemoryConversationStateDao,
    private val scopeLocks: MemoryScopeLocks,
) {
    companion object {
        /** Statuses considered "live" for retrieval/injection (searchable + shown). */
        val INJECTABLE_STATUSES = listOf(MemStatus.ACTIVE, MemStatus.PROVISIONAL)

        /** Statuses considered searchable (includes dormant/closed history). */
        val SEARCHABLE_STATUSES = listOf(MemStatus.ACTIVE, MemStatus.PROVISIONAL, MemStatus.DORMANT, MemStatus.CLOSED)

        /** All statuses the Browse tab can surface (adds FORGOTTEN-in-grace + SUPERSEDED history). */
        val BROWSABLE_STATUSES = listOf(
            MemStatus.ACTIVE, MemStatus.PROVISIONAL, MemStatus.DORMANT,
            MemStatus.CLOSED, MemStatus.SUPERSEDED, MemStatus.FORGOTTEN,
        )

        /** Grace window a FORGOTTEN node stays restorable before the sleep pass hard-deletes it (§5.4). */
        const val FORGET_GRACE_MILLIS = 30L * 24 * 60 * 60 * 1000
    }

    /** Everything the node sheet needs in one shot: the node, its provenance, edges (with the other
     *  endpoint's label + status), and the SUPERSEDES history chain ordered oldest→newest. */
    data class NodeDetail(
        val node: MemoryNodeEntity,
        val provenance: List<MemoryProvenanceEntity>,
        val edges: List<EdgeView>,
        val history: List<MemoryNodeEntity>,
    )

    data class EdgeView(
        val edge: MemoryEdgeEntity,
        val otherId: String,
        val otherLabel: String,
        val otherStatus: Int,
        val outgoing: Boolean,
    )

    /** Legacy-import progress for the health strip (§12.2). */
    data class ImportProgress(val completed: Boolean, val processed: Int, val total: Int)

    // ---- reads ----

    suspend fun getNode(id: String): MemoryNodeEntity? = nodeDao.getById(id)

    suspend fun getNodes(ids: List<String>): List<MemoryNodeEntity> =
        if (ids.isEmpty()) emptyList() else nodeDao.getByIds(ids)

    suspend fun getVisibleInjectable(assistantId: String): List<MemoryNodeEntity> =
        nodeDao.getVisibleWithStatuses(assistantId, INJECTABLE_STATUSES)

    suspend fun getVisibleByType(assistantId: String, type: Int, statuses: List<Int> = INJECTABLE_STATUSES): List<MemoryNodeEntity> =
        nodeDao.getVisibleByType(assistantId, type, statuses)

    suspend fun getRecentEpisodes(assistantId: String, limit: Int): List<MemoryNodeEntity> =
        nodeDao.getRecentEpisodes(assistantId, INJECTABLE_STATUSES, limit)

    suspend fun getEntities(assistantId: String): List<MemoryNodeEntity> =
        nodeDao.getVisibleEntities(assistantId)

    suspend fun searchFts(assistantId: String, ftsQuery: String, limit: Int, statuses: List<Int> = SEARCHABLE_STATUSES): List<String> =
        runCatching { nodeDao.searchFts(assistantId, ftsQuery, statuses, limit) }.getOrDefault(emptyList())

    /** Live count of dedup-adjudication-flagged nodes; drives the sleep pass's opportunistic scheduling. */
    suspend fun countPendingAdjudications(): Int = nodeDao.countPendingAdjudications()

    suspend fun getEdgesTouching(nodeId: String): List<MemoryEdgeEntity> = edgeDao.getTouching(nodeId)

    suspend fun getEdgesTouchingAny(nodeIds: List<String>): List<MemoryEdgeEntity> =
        if (nodeIds.isEmpty()) emptyList() else edgeDao.getTouchingAny(nodeIds)

    suspend fun getProvenance(nodeId: String): List<MemoryProvenanceEntity> = provenanceDao.getForNode(nodeId)

    fun observeActivity(assistantId: String, limit: Int = 200): Flow<List<MemoryActivityEntity>> =
        activityDao.observeVisible(assistantId, limit)

    fun observeNodes(assistantId: String): Flow<List<MemoryNodeEntity>> = nodeDao.observeVisible(assistantId)

    /** Reactive stream of every browsable node (incl. SUPERSEDED history + FORGOTTEN-in-grace). */
    fun observeBrowsable(assistantId: String): Flow<List<MemoryNodeEntity>> = nodeDao.observeBrowsable(assistantId)

    // ---- watermark resolution (message-id anchored, §7.3) ----

    /** Union of all message ids this conversation's provenance rows were extracted from. */
    suspend fun getProcessedMessageIds(conversationId: String): Set<String> =
        provenanceDao.getMessageIdsJsonForConversation(conversationId)
            .flatMap { MemoryWatermark.parseMessageIds(it) }
            .toSet()

    /**
     * Resolve the stored message-id anchor to an index into [currentMessageIds]. See [MemoryWatermark]:
     * present anchor → its index; missing anchor → nearest surviving earlier processed message.
     */
    suspend fun resolveWatermarkIndex(conversationId: String, currentMessageIds: List<String>): Int {
        val anchor = conversationStateDao.getAnchorMessageId(conversationId)
        if (anchor != null && currentMessageIds.contains(anchor)) {
            return currentMessageIds.indexOf(anchor)
        }
        val processed = if (anchor == null) emptySet() else getProcessedMessageIds(conversationId)
        return MemoryWatermark.resolveIndex(anchor, currentMessageIds, processed)
    }

    // ---- branch / regenerate demotion (§7.3) ----

    /**
     * Demote nodes whose evidence was entirely on an abandoned branch (regenerate / version switch)
     * to DORMANT (reason=branch); nodes evidenced on the surviving branch, in another conversation,
     * or whose only evidence was plain-deleted are kept. Runs under the per-scope write lock so it
     * cannot interleave with an in-flight extraction apply. Returns the number of nodes demoted.
     */
    suspend fun reconcileBranchDemotions(conversation: Conversation, now: Long = System.currentTimeMillis()): Int {
        val convId = conversation.id.toString()
        val assistantId = conversation.assistantId.toString()
        val (survivingIds, allExistingIds) = MemoryBranchLogic.branchSets(conversation)
        // No unselected versions exist → no branch was ever abandoned; deletion alone never demotes.
        if (allExistingIds.size == survivingIds.size) return 0

        return scopeLocks.withScope(assistantId) {
            db.withTransaction {
                val nodeIds = provenanceDao.getNodeIdsForConversation(convId).distinct()
                var demoted = 0
                val demotedIds = mutableListOf<String>()
                for (nodeId in nodeIds) {
                    val node = nodeDao.getById(nodeId) ?: continue
                    if (node.pinned) continue
                    if (node.status != MemStatus.ACTIVE && node.status != MemStatus.PROVISIONAL) continue
                    val rows = provenanceDao.getForNode(nodeId).map {
                        MemoryBranchLogic.ProvRow(it.conversationId, MemoryWatermark.parseMessageIds(it.messageIds))
                    }
                    if (MemoryBranchLogic.shouldDemote(rows, convId, survivingIds, allExistingIds)) {
                        nodeDao.update(node.copy(status = MemStatus.DORMANT, lastAccessedAt = now))
                        demoted++
                        demotedIds += nodeId
                    }
                }
                if (demoted > 0) {
                    activityDao.insert(
                        MemoryActivityEntity(
                            id = Uuid.random().toString(),
                            at = now,
                            scope = MemScope.CHARACTER,
                            ownerAssistantId = assistantId,
                            kind = MemActivityKind.DEMOTED_BRANCH,
                            summary = "$demoted demoted (abandoned branch)",
                            nodeIds = JsonInstant.encodeToString(demotedIds),
                            conversationId = convId,
                        )
                    )
                }
                demoted
            }
        }
    }

    /** Batched access-time bump for genuinely retrieved nodes (never core-sheet inclusion, §5.5). */
    suspend fun recordRetrieval(nodeIds: List<String>, now: Long = System.currentTimeMillis()) {
        if (nodeIds.isEmpty()) return
        db.withTransaction {
            for (id in nodeIds) {
                val node = nodeDao.getById(id) ?: continue
                nodeDao.update(node.copy(timesRetrieved = node.timesRetrieved + 1, lastAccessedAt = now))
            }
        }
    }

    // ---- structural deletes (privacy boundaries) ----

    /**
     * Character deletion cascade (§6.5): removes all CHARACTER-scoped nodes, their edges,
     * provenance, activity, and per-conversation state for [assistantId]. GLOBAL_USER facts survive.
     */
    suspend fun deleteCharacterMemory(assistantId: String) {
        scopeLocks.withScope(assistantId) {
        db.withTransaction {
            val ids = nodeDao.getCharacterNodeIds(assistantId)
            if (ids.isNotEmpty()) {
                edgeDao.deleteTouchingAny(ids)
                provenanceDao.deleteForNodes(ids)
                nodeDao.deleteFtsByNodes(ids)
                nodeDao.deleteByIds(ids)
            }
            activityDao.deleteForAssistant(assistantId)
            conversationStateDao.deleteForAssistant(assistantId)
        }
        }
    }

    /**
     * "Also forget memories from this chat" (§10): move nodes whose provenance points only at
     * [conversationId] to FORGOTTEN (grace), rather than hard-deleting. Nodes also evidenced
     * elsewhere are left intact.
     */
    suspend fun forgetConversationMemories(conversationId: String, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val nodeIds = provenanceDao.getNodeIdsForConversation(conversationId).distinct()
            for (id in nodeIds) {
                val node = nodeDao.getById(id) ?: continue
                if (node.pinned) continue
                val provenance = provenanceDao.getForNode(id)
                val onlyThisConversation = provenance.isNotEmpty() && provenance.all { it.conversationId == conversationId }
                if (onlyThisConversation) {
                    nodeDao.update(node.copy(status = MemStatus.FORGOTTEN, lastAccessedAt = now))
                }
            }
            conversationStateDao.delete(conversationId)
        }
    }

    // ---- node sheet detail ----

    suspend fun getNodeDetail(nodeId: String): NodeDetail? {
        val node = nodeDao.getById(nodeId) ?: return null
        val provenance = provenanceDao.getForNode(nodeId)
        val edges = edgeDao.getTouching(nodeId)
        val otherIds = edges.map { if (it.fromId == nodeId) it.toId else it.fromId }.distinct()
        val others = getNodes(otherIds).associateBy { it.id }
        val edgeViews = edges.map { e ->
            val outgoing = e.fromId == nodeId
            val otherId = if (outgoing) e.toId else e.fromId
            val other = others[otherId]
            EdgeView(
                edge = e,
                otherId = otherId,
                otherLabel = other?.let { it.displayLabel ?: it.content } ?: otherId.take(8),
                otherStatus = other?.status ?: MemStatus.FORGOTTEN,
                outgoing = outgoing,
            )
        }
        val history = collectSupersedeChain(nodeId).sortedBy { it.recordedAt }
        return NodeDetail(node = node, provenance = provenance, edges = edgeViews, history = history)
    }

    /** All nodes transitively connected to [nodeId] by SUPERSEDES edges (both directions). */
    private suspend fun collectSupersedeChain(nodeId: String): List<MemoryNodeEntity> {
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(nodeId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current)) continue
            for (e in edgeDao.getTouching(current)) {
                if (e.type != me.rerere.rikkahub.data.db.entity.MemEdgeType.SUPERSEDES) continue
                val next = if (e.fromId == current) e.toId else e.fromId
                if (next !in seen) queue.add(next)
            }
        }
        return if (seen.size <= 1) emptyList() else getNodes(seen.toList())
    }

    // ---- user-driven status changes (mirrors forgetConversationMemories, no model calls) ----

    suspend fun setPinned(nodeId: String, pinned: Boolean, now: Long = System.currentTimeMillis()) {
        val node = nodeDao.getById(nodeId) ?: return
        nodeDao.update(node.copy(pinned = pinned, lastAccessedAt = now))
    }

    /** User "Forget": move a node to FORGOTTEN with grace (reversible until the sleep pass purges it). */
    suspend fun forgetNode(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = nodeDao.getById(nodeId) ?: return
        nodeDao.update(node.copy(status = MemStatus.FORGOTTEN, lastAccessedAt = now))
    }

    /** One-tap restore for a FORGOTTEN-in-grace node (Browse "Recently forgotten"). */
    suspend fun restoreNode(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = nodeDao.getById(nodeId) ?: return
        if (node.status != MemStatus.FORGOTTEN) return
        nodeDao.update(node.copy(status = MemStatus.ACTIVE, lastAccessedAt = now, lastConfirmedAt = now))
    }

    /** FORGOTTEN nodes still inside the restore grace window, visible to [assistantId]. */
    suspend fun getRecentlyForgotten(assistantId: String, now: Long = System.currentTimeMillis()): List<MemoryNodeEntity> =
        nodeDao.getVisibleWithStatuses(assistantId, listOf(MemStatus.FORGOTTEN))
            .filter { now - it.lastAccessedAt <= FORGET_GRACE_MILLIS }

    suspend fun getBrowsable(assistantId: String): List<MemoryNodeEntity> =
        nodeDao.getVisibleWithStatuses(assistantId, BROWSABLE_STATUSES)

    // ---- promotion suggestion chips (§6.5); full promote policy is P5 ----

    suspend fun getPendingPromotionSuggestions(
        assistantId: String,
        now: Long = System.currentTimeMillis(),
        max: Int = 3,
    ): List<MemoryActivityEntity> =
        activityDao.getPendingSuggestions(assistantId, MemActivityKind.PROMOTION_SUGGESTED, MemActivityState.PENDING)
            .filter { now - it.at <= FORGET_GRACE_MILLIS } // 30-day expiry, §10.1
            .take(max)

    /**
     * Accept a promotion suggestion: mark the row accepted and best-effort promote its nodes to the
     * shared GLOBAL_USER layer. TODO(P5): the real promotion-review policy (category whitelist,
     * sensitivity gates) lives in the sleep pass; this is the minimal store-level action for the chip.
     */
    suspend fun acceptPromotionSuggestion(activityId: String, now: Long = System.currentTimeMillis()) {
        val row = activityDao.getById(activityId) ?: return
        db.withTransaction {
            for (id in parseNodeIds(row.nodeIds)) {
                val node = nodeDao.getById(id) ?: continue
                if (node.scope == MemScope.GLOBAL_USER) continue
                if (node.sensitivity != me.rerere.rikkahub.data.db.entity.MemSensitivity.NORMAL) continue
                nodeDao.update(node.copy(scope = MemScope.GLOBAL_USER, ownerAssistantId = null, lastAccessedAt = now))
            }
            activityDao.update(row.copy(state = MemActivityState.ACCEPTED))
            activityDao.insert(
                MemoryActivityEntity(
                    id = Uuid.random().toString(),
                    at = now,
                    scope = MemScope.GLOBAL_USER,
                    ownerAssistantId = null,
                    kind = MemActivityKind.PROMOTED,
                    summary = "shared with all characters",
                    nodeIds = row.nodeIds,
                )
            )
        }
    }

    suspend fun dismissPromotionSuggestion(activityId: String) {
        val row = activityDao.getById(activityId) ?: return
        activityDao.update(row.copy(state = MemActivityState.DISMISSED))
    }

    // ---- export / wipe (§3.7, §10.4) ----

    /** Per-character export: this character's CHARACTER-scoped nodes plus the shared GLOBAL_USER layer. */
    suspend fun exportCharacter(assistantId: String): String {
        val nodes = nodeDao.getVisibleWithStatuses(assistantId, BROWSABLE_STATUSES) +
            nodeDao.getVisibleWithStatuses(assistantId, listOf(MemStatus.SUPERSEDED))
        return buildExport(nodes.distinctBy { it.id }, "character:$assistantId")
    }

    /** Global-layer export: GLOBAL_USER nodes only (excludes every character's private memory). */
    suspend fun exportGlobal(): String =
        buildExport(nodeDao.getByScope(MemScope.GLOBAL_USER), "global")

    /** Whole-store export ("everything"). */
    suspend fun exportEverything(): String = buildExport(nodeDao.getAll(), "everything")

    private suspend fun buildExport(nodes: List<MemoryNodeEntity>, scopeLabel: String): String {
        val ids = nodes.map { it.id }.toSet()
        val edges = edgeDao.getAll().filter { it.fromId in ids && it.toId in ids }
        val provenance = if (ids.isEmpty()) emptyList() else provenanceDao.getForNodes(ids.toList())
        val export = MemoryExport(
            scope = scopeLabel,
            exportedAt = System.currentTimeMillis(),
            nodes = nodes.map { it.toExport() },
            edges = edges.map { it.toExport() },
            provenance = provenance.map { it.toExport() },
        )
        return JsonInstant.encodeToString(MemoryExport.serializer(), export)
    }

    /** Character wipe: cascade delete + a single WIPED activity row (§10). */
    suspend fun wipeCharacter(assistantId: String, now: Long = System.currentTimeMillis()) {
        deleteCharacterMemory(assistantId)
    }

    /** Whole-store wipe ("everything"): clears every memory table and records one WIPED row. */
    suspend fun wipeEverything(now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            nodeDao.deleteAllFts()
            nodeDao.deleteAll()
            edgeDao.deleteAll()
            provenanceDao.deleteAll()
            activityDao.deleteAll()
            conversationStateDao.deleteAll()
            activityDao.insert(
                MemoryActivityEntity(
                    id = Uuid.random().toString(),
                    at = now,
                    scope = MemScope.GLOBAL_USER,
                    ownerAssistantId = null,
                    kind = MemActivityKind.WIPED,
                    summary = "all memory erased",
                )
            )
        }
    }

    private fun parseNodeIds(json: String): List<String> = runCatching {
        (JsonInstant.decodeFromString(JsonArray.serializer(), json)).mapNotNull { (it as? JsonPrimitive)?.content }
    }.getOrDefault(emptyList())
}
