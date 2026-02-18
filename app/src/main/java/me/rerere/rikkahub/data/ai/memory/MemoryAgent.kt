package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeStatus
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.GraphEpisodeEntity
import me.rerere.rikkahub.data.repository.GraphMemoryRepository
import me.rerere.rikkahub.utils.JsonInstant

/**
 * Central orchestrator for the graph memory system.
 * Responsible for:
 * - Processing conversation exchanges (async, fire-and-forget)
 * - Embedding new/updated nodes
 * - Coordinating extraction → upsert → embed pipeline
 * - Running background consolidation (decay, merge, sweep)
 */
class MemoryAgent(
    private val graphRepo: GraphMemoryRepository,
    private val extractor: RelationExtractor,
    private val decayEngine: DecayEngine,
    private val timelineManager: TimelineManager,
    private val embeddingService: EmbeddingService,
) {
    companion object {
        private const val TAG = "MemoryAgent"
    }

    /**
     * Process a conversation exchange asynchronously.
     * Called fire-and-forget after each user↔assistant message pair.
     *
     * Pipeline:
     * 1. Extract entities/relations from the exchange
     * 2. Upsert nodes (merge if similar exists)
     * 3. Create/reinforce edges
     * 4. Create timeline events
     * 5. Embed new/updated nodes
     */
    suspend fun processExchange(
        assistantId: String,
        userMessage: String,
        assistantReply: String,
    ) {
        try {
            Log.i(TAG, "Processing exchange for assistant $assistantId")

            // Get existing node names for the extractor hint
            val existingNodes = graphRepo.getActiveNodes(assistantId)
            val existingNames = existingNodes.map { it.name }

            // 1. Extract structured data from the exchange
            val result = extractor.extract(assistantId, userMessage, assistantReply, existingNames)
            Log.i(TAG, "Extracted ${result.nodes.size} nodes, ${result.edges.size} edges, ${result.timelineEvents.size} timeline events")

            if (result.nodes.isEmpty() && result.edges.isEmpty() && result.timelineEvents.isEmpty()) {
                Log.i(TAG, "Nothing meaningful extracted, skipping")
                return
            }

            // 2. Upsert nodes and build name→id map
            val now = System.currentTimeMillis()
            val nameToId = mutableMapOf<String, Int>()

            for (extractedNode in result.nodes) {
                val nodeType = if (extractedNode.type in NodeType.ALL) extractedNode.type else "concept"

                val entity = MemoryNodeEntity(
                    assistantId = assistantId,
                    nodeType = nodeType,
                    name = extractedNode.name,
                    description = extractedNode.description,
                    importance = extractedNode.importance.coerceIn(1, 10),
                    emotionalValence = extractedNode.emotionalValence.coerceIn(-1f, 1f),
                    firstMentioned = now,
                    lastMentioned = now,
                    mentionCount = 1,
                    status = NodeStatus.ACTIVE,
                )

                val nodeId = graphRepo.upsertNode(assistantId, entity)
                nameToId[extractedNode.name] = nodeId
            }

            // Also map existing nodes not in extraction
            for (existingNode in existingNodes) {
                if (existingNode.name !in nameToId) {
                    nameToId[existingNode.name] = existingNode.id
                }
            }

            // 3. Create/reinforce edges
            for (extractedEdge in result.edges) {
                val sourceId = nameToId[extractedEdge.source]
                val targetId = nameToId[extractedEdge.target]

                if (sourceId != null && targetId != null && sourceId != targetId) {
                    val edge = MemoryEdgeEntity(
                        assistantId = assistantId,
                        sourceNodeId = sourceId,
                        targetNodeId = targetId,
                        relationType = extractedEdge.relationType,
                        description = extractedEdge.description,
                        createdAt = now,
                        lastReinforced = now,
                    )
                    graphRepo.upsertEdge(edge)
                }
            }

            // 4. Create timeline events
            for (extractedEvent in result.timelineEvents) {
                val nodeId = nameToId[extractedEvent.nodeName]
                if (nodeId != null) {
                    timelineManager.createEventFromExtraction(
                        assistantId = assistantId,
                        nodeId = nodeId,
                        eventType = extractedEvent.eventType,
                        description = extractedEvent.description,
                        scheduledDateStr = extractedEvent.scheduledDate,
                    )
                }
            }

            // 5. Create episode for this exchange
            val involvedNodeIds = nameToId.values.toList()
            val avgSignificance = if (result.nodes.isNotEmpty()) {
                result.nodes.map { it.importance.coerceIn(1, 10) }.average().toInt().coerceIn(1, 10)
            } else 5
            val episodeContent = buildString {
                append("User: ")
                append(userMessage.take(200))
                if (userMessage.length > 200) append("...")
                append("\nAssistant: ")
                append(assistantReply.take(300))
                if (assistantReply.length > 300) append("...")
            }
            graphRepo.insertEpisode(
                GraphEpisodeEntity(
                    assistantId = assistantId,
                    content = episodeContent,
                    significance = avgSignificance,
                    startTime = now,
                    endTime = System.currentTimeMillis(),
                    nodeIds = JsonInstant.encodeToString(involvedNodeIds),
                )
            )

            // 6. Embed new/updated nodes that don't have embeddings yet
            embedMissingNodes(assistantId)

            Log.i(TAG, "Exchange processing complete for assistant $assistantId")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing exchange", e)
        }
    }

    /**
     * Generate embeddings for nodes that don't have them yet (or have stale ones).
     */
    private suspend fun embedMissingNodes(assistantId: String) {
        val nodes = graphRepo.getActiveNodes(assistantId)
        val currentModelId = try {
            embeddingService.getEmbeddingModelId(assistantId)
        } catch (e: Exception) {
            Log.w(TAG, "No embedding model configured, skipping embedding", e)
            return
        }

        var embedded = 0
        for (node in nodes) {
            if (node.embedding != null && node.embeddingModelId == currentModelId) continue

            try {
                val textToEmbed = "${node.name}: ${node.description}".take(500)
                val embedding = embeddingService.embed(textToEmbed, assistantId)
                val embeddingJson = JsonInstant.encodeToString(embedding)

                graphRepo.updateNode(node.copy(
                    embedding = embeddingJson,
                    embeddingModelId = currentModelId,
                ))
                embedded++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to embed node ${node.name}", e)
            }
        }

        if (embedded > 0) {
            Log.i(TAG, "Embedded $embedded nodes for assistant $assistantId")
        }
    }

    /**
     * Run background consolidation tasks (called periodically by MemoryConsolidationWorker).
     */
    suspend fun runConsolidation(assistantId: String, decayHalfLifeDays: Double = 14.0) {
        try {
            Log.i(TAG, "Running graph consolidation for assistant $assistantId")

            // 1. Decay edge strengths
            decayEngine.decayEdges(assistantId, decayHalfLifeDays)

            // 2. Prune weak edges
            decayEngine.pruneWeakEdges(assistantId)

            // 3. Archive orphaned nodes
            decayEngine.archiveOrphanedNodes(assistantId)

            // 4. Merge near-duplicate nodes
            decayEngine.mergeNearDuplicates(assistantId)

            // 5. Sweep timeline (expire overdue, reschedule recurring)
            timelineManager.sweepTimeline(assistantId)

            // 6. Re-embed any nodes that lost embeddings during merges
            embedMissingNodes(assistantId)

            Log.i(TAG, "Graph consolidation complete for assistant $assistantId")
        } catch (e: Exception) {
            Log.e(TAG, "Error during graph consolidation", e)
        }
    }

    /**
     * Migrate existing core memories to graph nodes.
     * Called when enabling advanced memory for the first time.
     */
    suspend fun migrateFromCoreMemories(
        assistantId: String,
        coreMemories: List<Pair<String, String?>>, // content, embedding
    ) {
        try {
            Log.i(TAG, "Migrating ${coreMemories.size} core memories to graph")

            for ((content, embedding) in coreMemories) {
                val node = MemoryNodeEntity(
                    assistantId = assistantId,
                    nodeType = NodeType.CONCEPT,
                    name = content.take(50).trim(),
                    description = content,
                    importance = 5,
                    embedding = embedding,
                )
                graphRepo.insertNode(node)
            }

            Log.i(TAG, "Migration complete: ${coreMemories.size} core memories → graph nodes")
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating core memories", e)
        }
    }
}
