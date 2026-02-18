package me.rerere.rikkahub.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.db.dao.GraphEpisodeDAO
import me.rerere.rikkahub.data.db.dao.MemoryEdgeDAO
import me.rerere.rikkahub.data.db.dao.MemoryNodeDAO
import me.rerere.rikkahub.data.db.dao.PersonProfileDAO
import me.rerere.rikkahub.data.db.dao.TimelineEventDAO
import me.rerere.rikkahub.data.db.entity.GraphEpisodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.db.entity.NodeStatus
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Repository for the graph-based advanced memory system.
 * Combines nodes, edges, timeline events, and episodes into a unified
 * temporal knowledge graph with multi-signal retrieval.
 */
class GraphMemoryRepository(
    private val nodeDAO: MemoryNodeDAO,
    private val edgeDAO: MemoryEdgeDAO,
    private val timelineEventDAO: TimelineEventDAO,
    private val graphEpisodeDAO: GraphEpisodeDAO,
    private val personProfileDAO: PersonProfileDAO,
    private val embeddingService: EmbeddingService,
) {
    companion object {
        private const val TAG = "GraphMemoryRepo"
    }

    // ─── Node Operations ────────────────────────────────────────────────

    suspend fun insertNode(node: MemoryNodeEntity): Int {
        return nodeDAO.insert(node).toInt()
    }

    suspend fun updateNode(node: MemoryNodeEntity) {
        nodeDAO.update(node)
    }

    suspend fun getNodeById(id: Int): MemoryNodeEntity? = nodeDAO.getById(id)

    suspend fun getActiveNodes(assistantId: String): List<MemoryNodeEntity> =
        nodeDAO.getActiveNodes(assistantId)

    fun getActiveNodesFlow(assistantId: String): Flow<List<MemoryNodeEntity>> =
        nodeDAO.getActiveNodesFlow(assistantId)

    fun getAllNodesFlow(assistantId: String): Flow<List<MemoryNodeEntity>> =
        nodeDAO.getAllNodesFlow(assistantId)

    suspend fun findNodeByName(assistantId: String, name: String): MemoryNodeEntity? =
        nodeDAO.findByName(assistantId, name)

    suspend fun searchNodes(assistantId: String, query: String): List<MemoryNodeEntity> =
        nodeDAO.searchByName(assistantId, query)

    suspend fun touchNode(id: Int) = nodeDAO.touchNode(id)

    suspend fun archiveNode(id: Int) = nodeDAO.archive(id)

    suspend fun deleteNode(id: Int) = nodeDAO.delete(id)

    suspend fun deleteAllNodesForAssistant(assistantId: String) = nodeDAO.deleteAllForAssistant(assistantId)



    // ─── Person Profile Operations ───────────────────────────────────────

    suspend fun getPersonProfileByNodeId(nodeId: Int): PersonProfileEntity? =
        personProfileDAO.getByNodeId(nodeId)

    fun getPersonProfilesFlow(assistantId: String): Flow<List<PersonProfileEntity>> =
        personProfileDAO.getByAssistantFlow(assistantId)

    suspend fun upsertPersonProfile(profile: PersonProfileEntity): Int {
        val existing = personProfileDAO.getByNodeId(profile.nodeId)
        return if (existing != null) {
            personProfileDAO.update(
                existing.copy(
                    displayName = profile.displayName.ifBlank { existing.displayName },
                    avatar = profile.avatar ?: existing.avatar,
                    dateOfBirth = profile.dateOfBirth ?: existing.dateOfBirth,
                    birthYear = profile.birthYear ?: existing.birthYear,
                    physicalSummary = if (profile.physicalSummary.isBlank()) existing.physicalSummary else profile.physicalSummary,
                    physicalSourceNodeIds = if (profile.physicalSourceNodeIds == "[]") existing.physicalSourceNodeIds else profile.physicalSourceNodeIds,
                    personalitySummary = if (profile.personalitySummary.isBlank()) existing.personalitySummary else profile.personalitySummary,
                    personalitySourceNodeIds = if (profile.personalitySourceNodeIds == "[]") existing.personalitySourceNodeIds else profile.personalitySourceNodeIds,
                    otherSummary = if (profile.otherSummary.isBlank()) existing.otherSummary else profile.otherSummary,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            existing.id
        } else {
            personProfileDAO.insert(profile.copy(updatedAt = System.currentTimeMillis())).toInt()
        }
    }

    // ─── Edge Operations ────────────────────────────────────────────────

    suspend fun insertEdge(edge: MemoryEdgeEntity): Int {
        return edgeDAO.insert(edge).toInt()
    }

    suspend fun updateEdge(edge: MemoryEdgeEntity) = edgeDAO.update(edge)

    suspend fun getEdgesForNode(nodeId: Int): List<MemoryEdgeEntity> =
        edgeDAO.getEdgesForNode(nodeId)

    fun getAllEdgesFlow(assistantId: String): Flow<List<MemoryEdgeEntity>> =
        edgeDAO.getAllEdgesFlow(assistantId)

    suspend fun findEdge(sourceId: Int, targetId: Int, relationType: String): MemoryEdgeEntity? =
        edgeDAO.findEdge(sourceId, targetId, relationType)

    suspend fun reinforceEdge(id: Int, strength: Float) =
        edgeDAO.reinforceEdge(id, strength)

    suspend fun deleteWeakEdges(assistantId: String, threshold: Float): Int =
        edgeDAO.deleteWeakEdges(assistantId, threshold)

    suspend fun deleteEdge(id: Int) = edgeDAO.delete(id)

    suspend fun deleteAllEdgesForAssistant(assistantId: String) = edgeDAO.deleteAllForAssistant(assistantId)

    // ─── Timeline Operations ────────────────────────────────────────────

    suspend fun insertTimelineEvent(event: TimelineEventEntity): Int {
        return timelineEventDAO.insert(event).toInt()
    }

    suspend fun updateTimelineEvent(event: TimelineEventEntity) = timelineEventDAO.update(event)

    suspend fun getActiveEvents(assistantId: String): List<TimelineEventEntity> =
        timelineEventDAO.getActiveEvents(assistantId)

    fun getActiveEventsFlow(assistantId: String): Flow<List<TimelineEventEntity>> =
        timelineEventDAO.getActiveEventsFlow(assistantId)

    fun getAllEventsFlow(assistantId: String): Flow<List<TimelineEventEntity>> =
        timelineEventDAO.getAllEventsFlow(assistantId)

    suspend fun getUpcomingEvents(assistantId: String): List<TimelineEventEntity> =
        timelineEventDAO.getUpcomingEvents(assistantId)

    suspend fun getOverdueEvents(assistantId: String): List<TimelineEventEntity> =
        timelineEventDAO.getOverdueEvents(assistantId)

    suspend fun updateEventStatus(id: Int, newType: String, completedAt: Long? = null) =
        timelineEventDAO.updateEventStatus(id, newType, completedAt)

    suspend fun deleteAllEventsForAssistant(assistantId: String) = timelineEventDAO.deleteAllForAssistant(assistantId)

    // ─── Episode Operations ─────────────────────────────────────────────

    suspend fun insertEpisode(episode: GraphEpisodeEntity): Int {
        return graphEpisodeDAO.insert(episode).toInt()
    }

    suspend fun updateEpisode(episode: GraphEpisodeEntity) = graphEpisodeDAO.update(episode)

    fun getAllEpisodesFlow(assistantId: String): Flow<List<GraphEpisodeEntity>> =
        graphEpisodeDAO.getAllEpisodesFlow(assistantId)

    suspend fun getRecentEpisodes(assistantId: String, limit: Int): List<GraphEpisodeEntity> =
        graphEpisodeDAO.getRecentEpisodes(assistantId, limit)

    suspend fun deleteAllEpisodesForAssistant(assistantId: String) = graphEpisodeDAO.deleteAllForAssistant(assistantId)

    // ─── Graph Statistics ────────────────────────────────────────────────

    data class GraphStats(
        val nodeCount: Int,
        val edgeCount: Int,
        val activeEventCount: Int,
        val episodeCount: Int,
    )

    suspend fun getStats(assistantId: String): GraphStats {
        return GraphStats(
            nodeCount = nodeDAO.getActiveNodeCount(assistantId),
            edgeCount = edgeDAO.getEdgeCount(assistantId),
            activeEventCount = timelineEventDAO.getActiveEventCount(assistantId),
            episodeCount = graphEpisodeDAO.getEpisodeCount(assistantId),
        )
    }

    fun getNodeCountFlow(assistantId: String): Flow<Int> = nodeDAO.getActiveNodeCountFlow(assistantId)
    fun getEdgeCountFlow(assistantId: String): Flow<Int> = edgeDAO.getEdgeCountFlow(assistantId)
    fun getActiveEventCountFlow(assistantId: String): Flow<Int> = timelineEventDAO.getActiveEventCountFlow(assistantId)
    fun getEpisodeCountFlow(assistantId: String): Flow<Int> = graphEpisodeDAO.getEpisodeCountFlow(assistantId)

    // ─── Upsert (Merge-Aware Insert) ────────────────────────────────────

    /**
     * Insert a node, or if a node with the same name already exists for the assistant,
     * update it by merging the new info and bumping mention count.
     * Returns the (new or existing) node ID.
     */
    suspend fun upsertNode(assistantId: String, node: MemoryNodeEntity): Int {
        val existing = nodeDAO.findByName(assistantId, node.name)
        if (existing != null) {
            val merged = existing.copy(
                description = if (node.description.isNotBlank() && node.description != existing.description) {
                    // Append new info if different
                    if (existing.description.isBlank()) node.description
                    else "${existing.description}\n${node.description}"
                } else existing.description,
                importance = maxOf(existing.importance, node.importance),
                emotionalValence = (existing.emotionalValence + node.emotionalValence) / 2f,
                lastMentioned = maxOf(existing.lastMentioned, node.lastMentioned),
                mentionCount = existing.mentionCount + 1,
                status = if (existing.status == NodeStatus.ARCHIVED) NodeStatus.ACTIVE else existing.status,
                validFrom = node.validFrom ?: existing.validFrom,
                validUntil = node.validUntil ?: existing.validUntil,
            )
            nodeDAO.update(merged)
            return existing.id
        } else {
            return nodeDAO.insert(node).toInt()
        }
    }

    /**
     * Insert or reinforce an edge.
     * If an edge with the same source, target, and relation type already exists,
     * reinforce it instead of creating a duplicate.
     */
    suspend fun upsertEdge(edge: MemoryEdgeEntity): Int {
        val existing = edgeDAO.findEdge(edge.sourceNodeId, edge.targetNodeId, edge.relationType)
        if (existing != null) {
            val newStrength = minOf(1.0f, existing.strength + 0.1f)
            val merged = existing.copy(
                strength = newStrength,
                lastReinforced = System.currentTimeMillis(),
                description = if (edge.description.isNotBlank()) edge.description else existing.description,
                episodeId = edge.episodeId ?: existing.episodeId,
            )
            edgeDAO.update(merged)
            return existing.id
        } else {
            return edgeDAO.insert(edge).toInt()
        }
    }

    // ─── Multi-Signal Retrieval ──────────────────────────────────────────

    /**
     * Retrieve relevant nodes using multi-signal scoring:
     * - semantic_similarity (0.3): embedding cosine similarity to query
     * - graph_distance (0.25): proximity to recently active/mentioned nodes
     * - recency (0.15): how recently the node was mentioned
     * - importance (0.15): base importance score
     * - temporal_relevance (0.15): whether the node is relevant right now (timeline)
     *
     * Then applies MMR diversity filtering to avoid fixation.
     */
    suspend fun retrieveRelevantNodes(
        assistantId: String,
        queryText: String,
        limit: Int = 20,
        recentNodeIds: Set<Int> = emptySet(),
    ): List<ScoredNode> {
        val allNodes = nodeDAO.getActiveNodes(assistantId)
        if (allNodes.isEmpty()) return emptyList()

        // Generate query embedding
        val queryEmbedding = try {
            embeddingService.embed(queryText, assistantId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to embed query, falling back to recency-based retrieval", e)
            null
        }

        val now = System.currentTimeMillis()
        val activeEvents = timelineEventDAO.getActiveEvents(assistantId)
        val activeEventNodeIds = activeEvents.map { it.nodeId }.toSet()

        // Score each node
        val scored = allNodes.map { node ->
            // 1. Semantic similarity
            val semanticScore = if (queryEmbedding != null && node.embedding != null) {
                try {
                    val nodeEmb = JsonInstant.decodeFromString<List<Float>>(node.embedding)
                    VectorEngine.cosineSimilarity(queryEmbedding, nodeEmb).coerceIn(0f, 1f)
                } catch (e: Exception) { 0f }
            } else 0f

            // 2. Graph distance (simplified: boost nodes connected to recently active nodes)
            val graphScore = if (recentNodeIds.isNotEmpty()) {
                val edges = edgeDAO.getEdgesForNode(node.id)
                val connectedToRecent = edges.any { e ->
                    (e.sourceNodeId in recentNodeIds || e.targetNodeId in recentNodeIds)
                }
                if (connectedToRecent) 0.8f else if (node.id in recentNodeIds) 1.0f else 0f
            } else 0f

            // 3. Recency score (exponential decay over 30 days)
            val daysSinceLastMention = (now - node.lastMentioned).toFloat() / (1000 * 60 * 60 * 24)
            val recencyScore = Math.exp(-daysSinceLastMention / 14.0).toFloat().coerceIn(0f, 1f)

            // 4. Importance score (normalize 1-10 to 0-1)
            val importanceScore = (node.importance / 10f).coerceIn(0f, 1f)

            // 5. Temporal relevance (boost if has active timeline event)
            val temporalScore = if (node.id in activeEventNodeIds) 1.0f else {
                // Also check validFrom/validUntil
                val isTemporallyRelevant = (node.validFrom == null || node.validFrom <= now) &&
                        (node.validUntil == null || node.validUntil >= now)
                if (isTemporallyRelevant && node.validFrom != null) 0.5f else 0f
            }

            val finalScore = (semanticScore * 0.30f) +
                    (graphScore * 0.25f) +
                    (recencyScore * 0.15f) +
                    (importanceScore * 0.15f) +
                    (temporalScore * 0.15f)

            ScoredNode(node, finalScore, semanticScore)
        }

        // Sort by score, then apply MMR diversity filtering
        val sorted = scored.sortedByDescending { it.score }
        return applyMMR(sorted, limit, lambda = 0.7f)
    }

    /**
     * Maximum Marginal Relevance: selects diverse results by penalizing
     * candidates too similar to already-selected ones.
     */
    private fun applyMMR(
        candidates: List<ScoredNode>,
        limit: Int,
        lambda: Float = 0.7f
    ): List<ScoredNode> {
        if (candidates.size <= limit) return candidates

        val selected = mutableListOf<ScoredNode>()
        val remaining = candidates.toMutableList()

        // Always add the top-scoring candidate first
        if (remaining.isNotEmpty()) {
            selected.add(remaining.removeFirst())
        }

        while (selected.size < limit && remaining.isNotEmpty()) {
            var bestCandidate: ScoredNode? = null
            var bestMMRScore = Float.MIN_VALUE

            for (candidate in remaining) {
                // Max similarity to any already-selected node
                val maxSimilarity = selected.maxOfOrNull { selected ->
                    if (candidate.semanticScore > 0f && selected.semanticScore > 0f &&
                        candidate.node.embedding != null && selected.node.embedding != null) {
                        try {
                            val candEmb = JsonInstant.decodeFromString<List<Float>>(candidate.node.embedding!!)
                            val selEmb = JsonInstant.decodeFromString<List<Float>>(selected.node.embedding!!)
                            VectorEngine.cosineSimilarity(candEmb, selEmb)
                        } catch (e: Exception) { 0f }
                    } else 0f
                } ?: 0f

                val mmrScore = lambda * candidate.score - (1 - lambda) * maxSimilarity
                if (mmrScore > bestMMRScore) {
                    bestMMRScore = mmrScore
                    bestCandidate = candidate
                }
            }

            if (bestCandidate != null) {
                selected.add(bestCandidate)
                remaining.remove(bestCandidate)
            } else break
        }

        return selected
    }

    /**
     * Build the formatted graph context string for injection into the system prompt.
     */
    suspend fun buildGraphContext(
        assistantId: String,
        queryText: String,
        limit: Int = 20,
        recentNodeIds: Set<Int> = emptySet(),
    ): String {
        val nodes = retrieveRelevantNodes(assistantId, queryText, limit, recentNodeIds)
        if (nodes.isEmpty()) return ""

        val nodeIds = nodes.map { it.node.id }.toSet()

        // Get edges between relevant nodes
        val relevantEdges = mutableListOf<MemoryEdgeEntity>()
        for (node in nodes) {
            val edges = edgeDAO.getEdgesForNode(node.node.id)
            relevantEdges.addAll(edges.filter { e ->
                e.sourceNodeId in nodeIds || e.targetNodeId in nodeIds
            })
        }
        val uniqueEdges = relevantEdges.distinctBy { it.id }

        // Get active timeline events for relevant nodes
        val activeEvents = timelineEventDAO.getActiveEvents(assistantId)
            .filter { it.nodeId in nodeIds }

        return buildString {
            appendLine("## Knowledge Graph Memory")
            appendLine()

            // Group nodes by type
            val grouped = nodes.groupBy { it.node.nodeType }
            for ((type, typeNodes) in grouped) {
                appendLine("### ${type.replaceFirstChar { it.uppercase() }}s")
                for (scoredNode in typeNodes) {
                    val n = scoredNode.node
                    val importance = "★".repeat(n.importance.coerceIn(1, 5))
                    append("- **${n.name}** ($importance)")
                    if (n.description.isNotBlank()) {
                        append(": ${n.description.take(200)}")
                    }
                    if (n.emotionalValence != 0f) {
                        val mood = when {
                            n.emotionalValence > 0.3f -> "😊"
                            n.emotionalValence < -0.3f -> "😟"
                            else -> "😐"
                        }
                        append(" $mood")
                    }
                    appendLine()
                }
                appendLine()
            }

            // Relations
            if (uniqueEdges.isNotEmpty()) {
                appendLine("### Relations")
                val nodeNameMap = nodes.associate { it.node.id to it.node.name }
                for (edge in uniqueEdges.take(15)) {
                    val sourceName = nodeNameMap[edge.sourceNodeId] ?: "?"
                    val targetName = nodeNameMap[edge.targetNodeId] ?: "?"
                    append("- $sourceName → [${edge.relationType}] → $targetName")
                    if (edge.description.isNotBlank()) {
                        append(": ${edge.description.take(100)}")
                    }
                    appendLine()
                }
                appendLine()
            }

            // Timeline
            if (activeEvents.isNotEmpty()) {
                appendLine("### Timeline")
                val nodeNameMap = nodes.associate { it.node.id to it.node.name }
                for (event in activeEvents) {
                    val nodeName = nodeNameMap[event.nodeId] ?: "?"
                    val status = when (event.eventType) {
                        "upcoming" -> "⏳"
                        "ongoing" -> "🔄"
                        "recurring" -> "🔁"
                        else -> "📌"
                    }
                    append("- $status **$nodeName**: ${event.description.take(100)}")
                    if (event.scheduledAt != null) {
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date(event.scheduledAt))
                        append(" (scheduled: $date)")
                    }
                    appendLine()
                }
                appendLine()
            }
        }
    }

    // ─── Delete All Graph Data ───────────────────────────────────────────

    suspend fun deleteAllGraphData(assistantId: String) {
        graphEpisodeDAO.deleteAllForAssistant(assistantId)
        timelineEventDAO.deleteAllForAssistant(assistantId)
        edgeDAO.deleteAllForAssistant(assistantId)
        nodeDAO.deleteAllForAssistant(assistantId)
    }

    data class ScoredNode(
        val node: MemoryNodeEntity,
        val score: Float,
        val semanticScore: Float,
    )
}
