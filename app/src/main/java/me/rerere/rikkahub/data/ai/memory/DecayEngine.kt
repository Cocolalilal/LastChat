package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import me.rerere.rikkahub.data.db.dao.MemoryEdgeDAO
import me.rerere.rikkahub.data.db.dao.MemoryNodeDAO
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeStatus
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
        private const val MERGE_NAME_SIMILARITY_THRESHOLD = 0.85f
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
