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
import me.rerere.rikkahub.data.db.entity.NodeStatus
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.db.entity.RelationType
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import java.util.Calendar
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

    // ─── Person Profile Operations ────────────────────────────────────────

    suspend fun getProfile(nodeId: Int): PersonProfileEntity? =
        personProfileDAO.getByNodeId(nodeId)

    fun getProfileFlow(nodeId: Int): Flow<PersonProfileEntity?> =
        personProfileDAO.getByNodeIdFlow(nodeId)

    suspend fun getUserProfile(assistantId: String): PersonProfileEntity? =
        personProfileDAO.getUserProfile(assistantId)

    suspend fun getCharacterProfile(assistantId: String): PersonProfileEntity? =
        personProfileDAO.getCharacterProfile(assistantId)

    fun getAllProfilesFlow(assistantId: String): Flow<List<PersonProfileEntity>> =
        personProfileDAO.getAllProfilesFlow(assistantId)

    suspend fun getAllProfiles(assistantId: String): List<PersonProfileEntity> =
        personProfileDAO.getAllProfiles(assistantId)

    suspend fun upsertProfile(profile: PersonProfileEntity): Int {
        val existing = personProfileDAO.getByNodeId(profile.nodeId)
        if (existing != null) {
            personProfileDAO.update(profile.copy(id = existing.id))
            return existing.id
        } else {
            return personProfileDAO.insert(profile).toInt()
        }
    }

    suspend fun updateProfile(profile: PersonProfileEntity) {
        personProfileDAO.update(profile)
    }

    suspend fun deleteProfile(nodeId: Int) = personProfileDAO.deleteByNodeId(nodeId)

    /**
     * Get person-to-person relationships for a given person node.
     * Returns edges where both source and target are person nodes,
     * filtered to person-to-person relation types.
     */
    suspend fun getPersonRelationships(nodeId: Int): List<MemoryEdgeEntity> {
        val edges = edgeDAO.getEdgesForNode(nodeId)
        return edges.filter { it.relationType in RelationType.PERSON_TO_PERSON }
    }

    /**
     * Get nodes that contribute to a person's physical description.
     */
    suspend fun getPhysicalAttributeNodes(personNodeId: Int): List<MemoryNodeEntity> {
        val edges = edgeDAO.getIncomingEdges(personNodeId)
            .filter { it.relationType == RelationType.DESCRIBES_PHYSICAL }
        return edges.mapNotNull { nodeDAO.getById(it.sourceNodeId) }
    }

    /**
     * Get nodes that contribute to a person's personality description.
     */
    suspend fun getPersonalityNodes(personNodeId: Int): List<MemoryNodeEntity> {
        val edges = edgeDAO.getIncomingEdges(personNodeId)
            .filter { it.relationType == RelationType.DESCRIBES_PERSONALITY }
        return edges.mapNotNull { nodeDAO.getById(it.sourceNodeId) }
    }

    /**
     * Get nodes that contribute to a person's "other info" description.
     */
    suspend fun getOtherInfoNodes(personNodeId: Int): List<MemoryNodeEntity> {
        val edges = edgeDAO.getIncomingEdges(personNodeId)
            .filter { it.relationType == RelationType.DESCRIBES_OTHER }
        return edges.mapNotNull { nodeDAO.getById(it.sourceNodeId) }
    }

    /**
     * Seed default User and Character person nodes + profiles when graph memory is enabled.
     * No-op if profiles already exist for this assistant.
     */
    suspend fun seedDefaultProfiles(
        assistantId: String,
        userName: String,
        characterName: String,
    ) {
        // Seed user profile
        if (personProfileDAO.getUserProfile(assistantId) == null) {
            val userDisplayName = userName.ifBlank { "User" }
            val userNodeId = upsertNode(assistantId, MemoryNodeEntity(
                assistantId = assistantId,
                nodeType = NodeType.PERSON,
                name = userDisplayName,
                description = "The user.",
                importance = 10,
            ))
            personProfileDAO.insert(PersonProfileEntity(
                nodeId = userNodeId,
                assistantId = assistantId,
                isUserProfile = true,
                displayName = userDisplayName,
            ))
            Log.i(TAG, "Seeded user profile node=$userNodeId for assistant $assistantId")
        }

        // Seed character profile
        if (personProfileDAO.getCharacterProfile(assistantId) == null) {
            val charDisplayName = characterName.ifBlank { "Assistant" }
            val charNodeId = upsertNode(assistantId, MemoryNodeEntity(
                assistantId = assistantId,
                nodeType = NodeType.PERSON,
                name = charDisplayName,
                description = "The assistant character.",
                importance = 10,
            ))
            personProfileDAO.insert(PersonProfileEntity(
                nodeId = charNodeId,
                assistantId = assistantId,
                isCharacterProfile = true,
                displayName = charDisplayName,
            ))
            Log.i(TAG, "Seeded character profile node=$charNodeId for assistant $assistantId")
        }
    }

    /**
     * Calculate age from birth year (and optional month/day).
     * Returns null if birthYear is null.
     */
    fun calculateAge(birthYear: Int?, birthMonth: Int? = null, birthDay: Int? = null): Int? {
        if (birthYear == null) return null
        val now = Calendar.getInstance()
        var age = now.get(Calendar.YEAR) - birthYear
        if (birthMonth != null) {
            val currentMonth = now.get(Calendar.MONTH) + 1
            if (birthDay != null) {
                val currentDay = now.get(Calendar.DAY_OF_MONTH)
                if (birthMonth > currentMonth || (birthMonth == currentMonth && birthDay > currentDay)) {
                    age--
                }
            } else if (birthMonth > now.get(Calendar.MONTH) + 1) {
                age--
            }
        }
        return age.coerceAtLeast(0)
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

    suspend fun getAllEdges(assistantId: String): List<MemoryEdgeEntity> =
        edgeDAO.getAllEdges(assistantId)

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

    suspend fun deleteTimelineEvent(id: Int) = timelineEventDAO.delete(id)

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

    suspend fun deleteEpisode(id: Int) = graphEpisodeDAO.delete(id)

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
            // Reinforce confidence: each re-mention nudges confidence up (diminishing returns)
            val reinforcedConfidence = minOf(1.0f, existing.confidence + (1.0f - existing.confidence) * 0.15f)
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
                confidence = reinforcedConfidence,
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

            // Group nodes by type, render persons with profile summaries first
            val grouped = nodes.groupBy { it.node.nodeType }
            val personNodes = grouped[NodeType.PERSON].orEmpty()
            val otherGroups = grouped.filterKeys { it != NodeType.PERSON }

            // Person profiles with rich summaries
            if (personNodes.isNotEmpty()) {
                appendLine("### People")
                for (scoredNode in personNodes) {
                    val n = scoredNode.node
                    val profile = try { personProfileDAO.getByNodeId(n.id) } catch (_: Exception) { null }
                    val importance = "★".repeat(n.importance.coerceIn(1, 5))
                    append("- **${profile?.displayName ?: n.name}** ($importance)")
                    if (profile != null) {
                        val age = calculateAge(profile.birthYear, profile.birthMonth, profile.birthDay)
                        if (age != null) append(", age $age")
                        appendLine()
                        if (profile.personalitySummary.isNotBlank()) {
                            appendLine("  - Personality: ${profile.personalitySummary.take(200)}")
                        }
                        if (profile.physicalSummary.isNotBlank()) {
                            appendLine("  - Physical: ${profile.physicalSummary.take(200)}")
                        }
                        if (profile.otherInfoSummary.isNotBlank()) {
                            appendLine("  - Info: ${profile.otherInfoSummary.take(200)}")
                        }
                    } else {
                        if (n.description.isNotBlank()) append(": ${n.description.take(200)}")
                        appendLine()
                    }
                }
                appendLine()
            }

            // Other node types
            for ((type, typeNodes) in otherGroups) {
                appendLine("### ${type.replaceFirstChar { it.uppercase() }}s")
                for (scoredNode in typeNodes) {
                    val n = scoredNode.node
                    val importance = "★".repeat(n.importance.coerceIn(1, 5))
                    append("- **${n.name}** ($importance)")
                    // Mark low-confidence facts so the AI can weigh them appropriately
                    if (n.confidence < 0.5f) append(" [uncertain]")
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

            // Episodic Memory — relevant past conversation summaries
            val relevantEpisodes = try {
                retrieveRelevantEpisodes(assistantId, queryText, limit = 5)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve episodes for context", e)
                emptyList()
            }
            if (relevantEpisodes.isNotEmpty()) {
                appendLine("### Recent Episodes")
                val dateFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                for (scored in relevantEpisodes) {
                    val ep = scored.episode
                    val date = dateFormat.format(java.util.Date(ep.endTime))
                    val sigMarker = if (ep.significance >= 7) " ★" else ""
                    append("- [$date$sigMarker] ${ep.content.take(200)}")
                    if (ep.content.length > 200) append("...")
                    appendLine()
                }
                appendLine()
            }
        }
    }

    // ─── Delete All Graph Data ───────────────────────────────────────────

    suspend fun deleteAllGraphData(assistantId: String) {
        personProfileDAO.deleteAllForAssistant(assistantId)
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

    data class ScoredEpisode(
        val episode: GraphEpisodeEntity,
        val score: Float,
    )

    /**
     * Retrieve the most relevant graph episodes for a given query.
     * Scoring: semantic_similarity × 0.5 + recency × 0.3 + significance × 0.2
     */
    suspend fun retrieveRelevantEpisodes(
        assistantId: String,
        queryText: String,
        limit: Int = 5,
    ): List<ScoredEpisode> {
        val episodes = graphEpisodeDAO.getEpisodesWithEmbeddings(assistantId)
        if (episodes.isEmpty()) return emptyList()

        val queryEmbedding = try {
            embeddingService.embed(queryText, assistantId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to embed query for episode retrieval", e)
            null
        }

        val now = System.currentTimeMillis()

        val scored = episodes.map { episode ->
            // Semantic similarity
            val semanticScore = if (queryEmbedding != null && episode.embedding != null) {
                try {
                    val episodeEmb = JsonInstant.decodeFromString<List<Float>>(episode.embedding!!)
                    VectorEngine.cosineSimilarity(queryEmbedding, episodeEmb).coerceIn(0f, 1f)
                } catch (e: Exception) { 0f }
            } else 0f

            // Recency (exponential decay over 14 days)
            val daysSince = (now - episode.endTime).toFloat() / (1000 * 60 * 60 * 24)
            val recencyScore = Math.exp(-daysSince / 14.0).toFloat().coerceIn(0f, 1f)

            // Significance (normalize 1-10 to 0-1)
            val significanceScore = (episode.significance / 10f).coerceIn(0f, 1f)

            val finalScore = (semanticScore * 0.5f) + (recencyScore * 0.3f) + (significanceScore * 0.2f)
            ScoredEpisode(episode, finalScore)
        }

        return scored
            .sortedByDescending { it.score }
            .take(limit)
            .filter { it.score > 0.1f } // Filter out very low scoring episodes
    }
}
