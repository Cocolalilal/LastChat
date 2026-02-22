package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import me.rerere.rikkahub.data.db.dao.MemoryEdgeDAO
import me.rerere.rikkahub.data.db.dao.MemoryNodeDAO
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeStatus
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.RelationType
import kotlin.math.exp

/**
 * Handles memory decay: weakens edges over time, archives low-importance
 * nodes with no strong connections, and merges near-duplicate nodes.
 */
class DecayEngine(
    private val nodeDAO: MemoryNodeDAO,
    private val edgeDAO: MemoryEdgeDAO,
) {
    companion object {
        private const val TAG = "DecayEngine"
        private const val DEFAULT_DECAY_HALF_LIFE_DAYS = 14.0
        private const val WEAK_EDGE_THRESHOLD = 0.1f
        private const val MERGE_NAME_SIMILARITY_THRESHOLD = 0.80f
    }

    /**
     * Apply time-based decay to all edge strengths.
     * Uses exponential decay: strength * exp(-lambda * daysSinceLastReinforced)
     *
     * @param halfLifeDays how many days for an edge to lose half its strength if not reinforced
     */
    suspend fun decayEdges(assistantId: String, halfLifeDays: Double = DEFAULT_DECAY_HALF_LIFE_DAYS) {
        val now = System.currentTimeMillis()
        val lambda = Math.log(2.0) / halfLifeDays
        val edges = edgeDAO.getAllEdges(assistantId)

        var updatedCount = 0
        for (edge in edges) {
            // Never decay 'knows' edges (person-to-person relationships)
            if (edge.relationType == RelationType.KNOWS) continue

            val daysSinceReinforced = (now - edge.lastReinforced).toDouble() / (1000 * 60 * 60 * 24)
            if (daysSinceReinforced < 1.0) continue // Skip recently reinforced edges

            val decayFactor = exp(-lambda * daysSinceReinforced).toFloat()
            val newStrength = (edge.strength * decayFactor).coerceIn(0f, 1f)

            if (newStrength != edge.strength) {
                edgeDAO.reinforceEdge(edge.id, newStrength, edge.lastReinforced) // Keep original time
                updatedCount++
            }
        }

        Log.i(TAG, "Decayed $updatedCount edges for assistant $assistantId")
    }

    /**
     * Remove edges that have decayed below the threshold.
     * Returns the number of edges pruned.
     */
    suspend fun pruneWeakEdges(assistantId: String, threshold: Float = WEAK_EDGE_THRESHOLD): Int {
        val pruned = edgeDAO.deleteWeakEdges(assistantId, threshold)
        Log.i(TAG, "Pruned $pruned weak edges for assistant $assistantId")
        return pruned
    }

    /**
     * Archive nodes that have no strong edges and low importance.
     * These are nodes that have faded from relevance.
     */
    suspend fun archiveOrphanedNodes(assistantId: String) {
        val nodes = nodeDAO.getActiveNodes(assistantId)
        var archivedCount = 0

        for (node in nodes) {
            // Never archive person nodes — they carry profile data
            if (node.nodeType == NodeType.PERSON) continue
            // Don't archive high-importance nodes
            if (node.importance >= 7) continue

            val edges = edgeDAO.getEdgesForNode(node.id)
            val hasStrongEdges = edges.any { it.strength >= WEAK_EDGE_THRESHOLD }

            if (!hasStrongEdges && edges.isEmpty()) {
                // Only archive if node hasn't been mentioned recently (30 days)
                val daysSinceLastMention = (System.currentTimeMillis() - node.lastMentioned) / (1000.0 * 60 * 60 * 24)
                if (daysSinceLastMention > 30) {
                    nodeDAO.archive(node.id)
                    archivedCount++
                }
            }
        }

        Log.i(TAG, "Archived $archivedCount orphaned nodes for assistant $assistantId")
    }

    /**
     * Merge nodes that are very similar (same type + similar names).
     * Uses simple string similarity for name comparison.
     */
    suspend fun mergeNearDuplicates(assistantId: String) {
        val nodes = nodeDAO.getActiveNodes(assistantId)
        val merged = mutableSetOf<Int>() // IDs already merged
        var mergeCount = 0

        for (i in nodes.indices) {
            if (nodes[i].id in merged) continue
            for (j in i + 1 until nodes.size) {
                if (nodes[j].id in merged) continue
                if (nodes[i].nodeType != nodes[j].nodeType) continue

                val similarity = nameSimilarity(nodes[i].name, nodes[j].name)
                if (similarity >= MERGE_NAME_SIMILARITY_THRESHOLD) {
                    mergeNodes(nodes[i], nodes[j])
                    merged.add(nodes[j].id)
                    mergeCount++
                }
            }
        }

        Log.i(TAG, "Merged $mergeCount duplicate nodes for assistant $assistantId")
    }

    /**
     * Merge node B into node A, transferring edges and updating stats.
     */
    private suspend fun mergeNodes(primary: MemoryNodeEntity, duplicate: MemoryNodeEntity) {
        // Update primary with combined info
        val merged = primary.copy(
            description = if (duplicate.description.isNotBlank() && primary.description != duplicate.description) {
                "${primary.description}\n${duplicate.description}".take(500)
            } else primary.description,
            importance = maxOf(primary.importance, duplicate.importance),
            mentionCount = primary.mentionCount + duplicate.mentionCount,
            lastMentioned = maxOf(primary.lastMentioned, duplicate.lastMentioned),
            firstMentioned = minOf(primary.firstMentioned, duplicate.firstMentioned),
        )
        nodeDAO.update(merged)

        // Transfer edges from duplicate to primary
        val duplicateEdges = edgeDAO.getEdgesForNode(duplicate.id)
        for (edge in duplicateEdges) {
            val newSourceId = if (edge.sourceNodeId == duplicate.id) primary.id else edge.sourceNodeId
            val newTargetId = if (edge.targetNodeId == duplicate.id) primary.id else edge.targetNodeId

            // Check if primary already has this edge
            val existing = edgeDAO.findEdge(newSourceId, newTargetId, edge.relationType)
            if (existing != null) {
                // Reinforce the existing edge
                edgeDAO.reinforceEdge(existing.id, minOf(1f, existing.strength + edge.strength * 0.5f))
            } else {
                // Create new edge pointing to primary
                edgeDAO.insert(edge.copy(id = 0, sourceNodeId = newSourceId, targetNodeId = newTargetId))
            }
        }

        // Archive the duplicate
        nodeDAO.archive(duplicate.id)
    }

    /**
     * Slightly decay confidence for nodes not mentioned recently.
     * Nodes mentioned within the last 7 days are unaffected.
     * Person nodes decay much slower.
     */
    suspend fun decayConfidence(assistantId: String) {
        val now = System.currentTimeMillis()
        val nodes = nodeDAO.getActiveNodes(assistantId)
        var decayed = 0

        for (node in nodes) {
            val daysSinceMention = (now - node.lastMentioned).toDouble() / (1000 * 60 * 60 * 24)
            if (daysSinceMention < 7.0) continue // Recently mentioned — no decay
            if (node.confidence <= 0.3f) continue // Already low enough

            // Person nodes decay 5x slower
            val decayRate = if (node.nodeType == NodeType.PERSON) 0.005f else 0.025f
            val newConfidence = (node.confidence - decayRate).coerceIn(0.3f, 1.0f)

            if (newConfidence != node.confidence) {
                nodeDAO.update(node.copy(confidence = newConfidence))
                decayed++
            }
        }

        if (decayed > 0) {
            Log.i(TAG, "Decayed confidence for $decayed nodes for assistant $assistantId")
        }
    }

    /**
     * Enforce graphMaxNodes: if active node count exceeds the limit,
     * archive lowest preservation-score non-person nodes until under the cap.
     * 
     * Preservation score is a composite of importance, mention frequency,
     * connectedness, recency, and emotional weight — making forgetting 
     * more organic rather than purely mechanical.
     */
    suspend fun enforceMaxNodes(assistantId: String, maxNodes: Int) {
        val activeCount = nodeDAO.getActiveNodeCount(assistantId)
        if (activeCount <= maxNodes) return

        val excess = activeCount - maxNodes
        val now = System.currentTimeMillis()
        val nodes = nodeDAO.getActiveNodes(assistantId)
            .filter { it.nodeType != NodeType.PERSON } // Never archive person nodes

        // Build edge count index for preservation scoring
        val allEdges = edgeDAO.getAllEdges(assistantId)
        val edgeCountByNode = mutableMapOf<Int, Int>()
        for (edge in allEdges) {
            edgeCountByNode[edge.sourceNodeId] = (edgeCountByNode[edge.sourceNodeId] ?: 0) + 1
            edgeCountByNode[edge.targetNodeId] = (edgeCountByNode[edge.targetNodeId] ?: 0) + 1
        }

        // Composite preservation score: higher = more worth keeping
        val scored = nodes.map { node ->
            val daysSinceMention = (now - node.lastMentioned).toFloat() / (1000 * 60 * 60 * 24)
            val recencyBonus = when {
                daysSinceMention < 7 -> 50f    // Very recent — strong protection
                daysSinceMention < 14 -> 30f   // Recent
                daysSinceMention < 30 -> 15f   // Somewhat recent
                else -> 0f
            }
            val emotionalBonus = kotlin.math.abs(node.emotionalValence) * 20f  // Emotionally charged = memorable
            val edgeCount = edgeCountByNode[node.id] ?: 0

            val preservationScore = (node.importance * 30f) +
                (node.mentionCount * 5f) +
                (edgeCount * 10f) +
                recencyBonus +
                emotionalBonus +
                (node.confidence * 10f)

            Pair(node, preservationScore)
        }.sortedBy { it.second } // Lowest preservation score first

        var archived = 0
        for ((node, _) in scored) {
            if (archived >= excess) break
            if (node.importance >= 8) break // Don't archive high-importance even under pressure
            if (node.confidence >= 0.9f) continue // Don't archive high-confidence nodes
            nodeDAO.archive(node.id)
            archived++
        }

        if (archived > 0) {
            Log.i(TAG, "Enforced max nodes ($maxNodes): archived $archived excess nodes for assistant $assistantId")
        }
    }

    /**
     * Simple normalized string similarity using Levenshtein distance.
     */
    private fun nameSimilarity(a: String, b: String): Float {
        val la = a.lowercase().trim()
        val lb = b.lowercase().trim()
        if (la == lb) return 1f

        val maxLen = maxOf(la.length, lb.length)
        if (maxLen == 0) return 1f

        val distance = levenshteinDistance(la, lb)
        return 1f - (distance.toFloat() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
