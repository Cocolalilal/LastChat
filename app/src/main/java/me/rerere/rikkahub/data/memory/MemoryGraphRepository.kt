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
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryProvenanceEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant
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
    }

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

    suspend fun getEdgesTouching(nodeId: String): List<MemoryEdgeEntity> = edgeDao.getTouching(nodeId)

    suspend fun getEdgesTouchingAny(nodeIds: List<String>): List<MemoryEdgeEntity> =
        if (nodeIds.isEmpty()) emptyList() else edgeDao.getTouchingAny(nodeIds)

    suspend fun getProvenance(nodeId: String): List<MemoryProvenanceEntity> = provenanceDao.getForNode(nodeId)

    fun observeActivity(assistantId: String, limit: Int = 200): Flow<List<MemoryActivityEntity>> =
        activityDao.observeVisible(assistantId, limit)

    fun observeNodes(assistantId: String): Flow<List<MemoryNodeEntity>> = nodeDao.observeVisible(assistantId)

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
}
