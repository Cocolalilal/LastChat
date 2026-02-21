package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.db.entity.GraphEpisodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeStatus
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.db.entity.PersonRelationship
import me.rerere.rikkahub.data.db.entity.PersonRelationType
import me.rerere.rikkahub.data.db.entity.RelationType
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

    data class ProcessingResult(
        val nodesUpserted: Int = 0,
        val edgesCreated: Int = 0,
        val timelineEvents: Int = 0,
        val personUpdates: Int = 0,
        val error: String? = null,
    ) {
        val totalItems get() = nodesUpserted + edgesCreated + timelineEvents + personUpdates
        val isEmpty get() = totalItems == 0 && error == null
    }

    data class RepairStats(
        val orphansFixed: Int = 0,
        val profilesFixed: Int = 0,
        val edgesFixed: Int = 0,
    ) {
        val totalFixed get() = orphansFixed + profilesFixed + edgesFixed
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
        conversationId: String? = null,
        timelineEnabled: Boolean = true,
        isManualIngestion: Boolean = false,
    ): ProcessingResult {
        try {
            Log.i(TAG, "Processing exchange for assistant $assistantId (manual=$isManualIngestion)")

            // Auto-repair: Fix broken graph data before processing
            val repairStats = repairBrokenGraphData(assistantId)
            if (repairStats.totalFixed > 0) {
                Log.i(TAG, "Auto-repair: fixed ${repairStats.totalFixed} issues " +
                    "(orphans=${repairStats.orphansFixed}, profiles=${repairStats.profilesFixed})")
            }

            // Get existing node names for the extractor hint
            val existingNodes = graphRepo.getActiveNodes(assistantId)
            val existingNames = existingNodes.map { it.name }

            // 1. Extract structured data from the exchange
            val result = extractor.extract(assistantId, userMessage, assistantReply, existingNames)
            Log.i(TAG, "Extracted ${result.nodes.size} nodes, ${result.edges.size} edges, ${result.timelineEvents.size} timeline events, ${result.personUpdates.size} person updates")

            if (result.nodes.isEmpty() && result.edges.isEmpty() && result.timelineEvents.isEmpty() && result.personUpdates.isEmpty()) {
                Log.i(TAG, "Nothing meaningful extracted, skipping")
                return ProcessingResult()
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
                    validFrom = extractedNode.validFrom?.let { parseDateToEpoch(it) },
                    validUntil = extractedNode.validUntil?.let { parseDateToEpoch(it) },
                    confidence = 1.0f,
                    sourceTurn = conversationId ?: "",
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

            // 4. Create timeline events (only if timeline tracking is enabled)
            if (timelineEnabled) {
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
            }

            // 5. Process person profile updates
            for (update in result.personUpdates) {
                // Fuzzy match: exact → case-insensitive → substring → DB fallback
                val personNodeId = nameToId[update.personName]
                    ?: nameToId.entries.firstOrNull { it.key.equals(update.personName, ignoreCase = true) }?.value
                    ?: nameToId.entries.firstOrNull {
                        it.key.contains(update.personName, ignoreCase = true) ||
                        update.personName.contains(it.key, ignoreCase = true)
                    }?.value
                    ?: graphRepo.findNodeByName(assistantId, update.personName)?.id
                    ?: continue
                val personNode = graphRepo.getNodeById(personNodeId)
                if (personNode == null || personNode.nodeType != NodeType.PERSON) continue

                try {
                    applyPersonUpdate(assistantId, personNodeId, update, nameToId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply person update for ${update.personName}", e)
                }
            }

            // 6. Embed new/updated nodes that don't have embeddings yet
            try {
                embedMissingNodes(assistantId)
            } catch (e: Exception) {
                Log.w(TAG, "Embedding failed (non-fatal)", e)
            }

            // NOTE: Episode creation is handled by MemoryConsolidationWorker,
            // NOT inline per-exchange. This ensures episodes summarize completed
            // chats rather than individual message pairs.

            val processingResult = ProcessingResult(
                nodesUpserted = result.nodes.size,
                edgesCreated = result.edges.size,
                timelineEvents = result.timelineEvents.size,
                personUpdates = result.personUpdates.size,
            )
            Log.i(TAG, "Exchange processing complete for assistant $assistantId: $processingResult")
            return processingResult
        } catch (e: Exception) {
            Log.e(TAG, "Error processing exchange", e)
            return ProcessingResult(error = e.message ?: "Unknown error")
        }
    }

    /**
     * Apply extracted person profile updates to the profile entity and create
     * contributing edges from trait/attribute nodes to the person node.
     */
    private suspend fun applyPersonUpdate(
        assistantId: String,
        personNodeId: Int,
        update: RelationExtractor.ExtractedPersonUpdate,
        nameToId: Map<String, Int>,
    ) {
        // Get or create the profile
        val existing = graphRepo.getProfile(personNodeId)
        val profile = existing ?: PersonProfileEntity(
            nodeId = personNodeId,
            assistantId = assistantId,
            displayName = update.personName,
        )

        // Merge new relationships with existing ones
        val existingRelationships = try {
            JsonInstant.decodeFromString<List<PersonRelationship>>(profile.relationshipsJson)
        } catch (e: Exception) { emptyList() }
        
        val newRelationships = update.relationships.mapNotNull { rel ->
            // Find target node ID by name
            val targetId = nameToId[rel.targetName] ?: graphRepo.findNodeByName(assistantId, rel.targetName)?.id
            if (targetId == null) {
                Log.w(TAG, "Could not find target node for relationship: ${rel.targetName}")
                return@mapNotNull null
            }
            PersonRelationship(
                targetNodeId = targetId,
                targetName = rel.targetName,
                relationType = rel.relationType,
                relationLabel = rel.relationLabel,
                notes = rel.notes,
                bidirectional = true,
            )
        }
        
        // Merge relationships, avoiding duplicates (by targetNodeId + relationType)
        val mergedRelationships = (existingRelationships + newRelationships)
            .distinctBy { "${it.targetNodeId}_${it.relationType}" }
        
        // Merge interests with existing
        val existingInterests = try {
            JsonInstant.decodeFromString<List<String>>(profile.interestsJson)
        } catch (e: Exception) { emptyList() }
        val mergedInterests = (existingInterests + update.interests).distinct()

        // Update profile with all new info (don't overwrite with null/empty)
        val updatedProfile = profile.copy(
            birthYear = update.birthYear ?: profile.birthYear,
            birthMonth = update.birthMonth ?: profile.birthMonth,
            birthDay = update.birthDay ?: profile.birthDay,
            personalitySummary = appendToSummary(profile.personalitySummary, update.personalityTraits),
            physicalSummary = appendToSummary(profile.physicalSummary, update.physicalAttributes),
            otherInfoSummary = appendToSummary(profile.otherInfoSummary, update.otherInfo),
            pronouns = update.pronouns?.takeIf { it.isNotBlank() } ?: profile.pronouns,
            occupation = update.occupation?.takeIf { it.isNotBlank() } ?: profile.occupation,
            location = update.location?.takeIf { it.isNotBlank() } ?: profile.location,
            interestsJson = JsonInstant.encodeToString(mergedInterests),
            relationshipsJson = JsonInstant.encodeToString(mergedRelationships),
        )

        graphRepo.upsertProfile(updatedProfile)
        
        // Create bidirectional relationships in target profiles
        for (rel in newRelationships) {
            try {
                createBidirectionalRelationship(assistantId, personNodeId, update.personName, rel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create bidirectional relationship", e)
            }
        }

        // Create contributing edges for personality traits
        for (trait in update.personalityTraits) {
            val traitNodeId = nameToId[trait] ?: run {
                val node = MemoryNodeEntity(
                    assistantId = assistantId,
                    nodeType = NodeType.CONCEPT,
                    name = trait,
                    description = "Personality trait of ${update.personName}",
                    importance = 4,
                )
                graphRepo.upsertNode(assistantId, node)
            }
            graphRepo.upsertEdge(MemoryEdgeEntity(
                assistantId = assistantId,
                sourceNodeId = traitNodeId,
                targetNodeId = personNodeId,
                relationType = RelationType.DESCRIBES_PERSONALITY,
                description = trait,
            ))
        }

        // Create contributing edges for physical attributes
        for (attr in update.physicalAttributes) {
            val attrNodeId = nameToId[attr] ?: run {
                val node = MemoryNodeEntity(
                    assistantId = assistantId,
                    nodeType = NodeType.CONCEPT,
                    name = attr,
                    description = "Physical attribute of ${update.personName}",
                    importance = 4,
                )
                graphRepo.upsertNode(assistantId, node)
            }
            graphRepo.upsertEdge(MemoryEdgeEntity(
                assistantId = assistantId,
                sourceNodeId = attrNodeId,
                targetNodeId = personNodeId,
                relationType = RelationType.DESCRIBES_PHYSICAL,
                description = attr,
            ))
        }

        // Create contributing edges for other info
        for (info in update.otherInfo) {
            val infoNodeId = nameToId[info] ?: run {
                val node = MemoryNodeEntity(
                    assistantId = assistantId,
                    nodeType = NodeType.CONCEPT,
                    name = info,
                    description = "Info about ${update.personName}",
                    importance = 4,
                )
                graphRepo.upsertNode(assistantId, node)
            }
            graphRepo.upsertEdge(MemoryEdgeEntity(
                assistantId = assistantId,
                sourceNodeId = infoNodeId,
                targetNodeId = personNodeId,
                relationType = RelationType.DESCRIBES_OTHER,
                description = info,
            ))
        }

        // Create contributing edges for interests
        for (interest in update.interests) {
            val interestNodeId = nameToId[interest] ?: run {
                val node = MemoryNodeEntity(
                    assistantId = assistantId,
                    nodeType = NodeType.PREFERENCE,
                    name = interest,
                    description = "Interest of ${update.personName}",
                    importance = 4,
                )
                graphRepo.upsertNode(assistantId, node)
            }
            graphRepo.upsertEdge(MemoryEdgeEntity(
                assistantId = assistantId,
                sourceNodeId = interestNodeId,
                targetNodeId = personNodeId,
                relationType = RelationType.DESCRIBES_INTEREST,
                description = interest,
            ))
        }

        Log.i(TAG, "Applied person update for ${update.personName}: " +
            "birth=${update.birthYear}, pronouns=${update.pronouns}, occupation=${update.occupation}, " +
            "location=${update.location}, interests=${update.interests.size}, " +
            "traits=${update.personalityTraits.size}, physical=${update.physicalAttributes.size}, " +
            "other=${update.otherInfo.size}, relationships=${update.relationships.size}")
    }

    /**
     * Create a bidirectional relationship in the target person's profile.
     */
    private suspend fun createBidirectionalRelationship(
        assistantId: String,
        sourceNodeId: Int,
        sourceName: String,
        relationship: PersonRelationship,
    ) {
        if (!relationship.bidirectional) return
        
        val targetProfile = graphRepo.getProfile(relationship.targetNodeId) ?: run {
        // Create profile on-the-fly if missing — prevents silently dropping reverse relationships
        val targetNode = graphRepo.getNodeById(relationship.targetNodeId) ?: return
        if (targetNode.nodeType != NodeType.PERSON) return
        val newProfile = PersonProfileEntity(
            nodeId = relationship.targetNodeId,
            assistantId = assistantId,
            displayName = targetNode.name,
        )
        graphRepo.upsertProfile(newProfile)
        newProfile
    }
        val reverseType = PersonRelationType.getReverse(relationship.relationType)
        
        val existingRelationships = try {
            JsonInstant.decodeFromString<List<PersonRelationship>>(targetProfile.relationshipsJson)
        } catch (e: Exception) { emptyList() }
        
        // Check if reverse relationship already exists
        val alreadyExists = existingRelationships.any { 
            it.targetNodeId == sourceNodeId && it.relationType == reverseType 
        }
        if (alreadyExists) return
        
        val reverseRelationship = PersonRelationship(
            targetNodeId = sourceNodeId,
            targetName = sourceName,
            relationType = reverseType ?: relationship.relationType,
            relationLabel = null, // Don't copy custom labels to reverse
            notes = "",
            bidirectional = false, // Prevent infinite loop
        )
        
        val updatedRelationships = existingRelationships + reverseRelationship
        val updatedProfile = targetProfile.copy(
            relationshipsJson = JsonInstant.encodeToString(updatedRelationships)
        )
        graphRepo.upsertProfile(updatedProfile)
        
        Log.d(TAG, "Created bidirectional relationship: ${relationship.targetName} → $sourceName ($reverseType)")
    }

    /**
     * Append new items to an existing summary, avoiding duplicates.
     */
    private fun appendToSummary(existing: String, newItems: List<String>): String {
        if (newItems.isEmpty()) return existing
        val existingLower = existing.lowercase()
        val genuinelyNew = newItems.filter { it.lowercase() !in existingLower }
        if (genuinelyNew.isEmpty()) return existing
        val addition = genuinelyNew.joinToString(", ")
        return if (existing.isBlank()) addition
        else "$existing; $addition"
    }

    /**
     * Parse a date string (ISO format like "2025-03-01") to epoch millis.
     */
    private fun parseDateToEpoch(dateStr: String): Long? {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .parse(dateStr)?.time
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date: $dateStr", e)
            null
        }
    }

    /**
     * Build a concise episode summary from the exchange and extraction result.
     * This becomes the "narrative memory" of the exchange.
     */
    private fun buildEpisodeSummary(
        userMessage: String,
        assistantReply: String,
        result: RelationExtractor.ExtractionResult,
    ): String {
        val entityNames = result.nodes.map { it.name }
        val relations = result.edges.map { "${it.source} ${it.relationType.replace("_", " ")} ${it.target}" }

        return buildString {
            // Compact exchange summary
            append("User discussed: ")
            append(userMessage.take(200))
            if (userMessage.length > 200) append("...")

            if (entityNames.isNotEmpty()) {
                append("\nEntities: ")
                append(entityNames.joinToString(", "))
            }
            if (relations.isNotEmpty()) {
                append("\nRelations: ")
                append(relations.take(5).joinToString("; "))
                if (relations.size > 5) append("; ...")
            }
            if (result.personUpdates.isNotEmpty()) {
                append("\nPerson updates: ")
                append(result.personUpdates.joinToString(", ") { it.personName })
            }
        }.take(500) // Cap total length for token economy
    }

    /**
     * Heuristic significance score (1-10) based on extraction richness.
     */
    private fun calculateSignificance(result: RelationExtractor.ExtractionResult): Int {
        var score = 3 // Baseline: any exchange that produced data is at least 3

        // More entities = more significant
        score += (result.nodes.size / 2).coerceAtMost(2)

        // High-importance entities boost significance
        val maxImportance = result.nodes.maxOfOrNull { it.importance } ?: 0
        if (maxImportance >= 8) score += 2
        else if (maxImportance >= 6) score += 1

        // Person updates are high-signal
        if (result.personUpdates.isNotEmpty()) score += 1

        // Timeline events indicate planning/temporal significance
        if (result.timelineEvents.isNotEmpty()) score += 1

        // Strong emotional content
        val maxValence = result.nodes.maxOfOrNull { kotlin.math.abs(it.emotionalValence) } ?: 0f
        if (maxValence >= 0.7f) score += 1

        return score.coerceIn(1, 10)
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
     * Repair broken graph data before processing.
     * This runs at the start of each processExchange call to fix:
     * - Orphan nodes without any relationships
     * - Person nodes without profiles
     * - Profiles with broken relationship references
     */
    private suspend fun repairBrokenGraphData(assistantId: String): RepairStats {
        var orphansFixed = 0
        var profilesFixed = 0
        var edgesFixed = 0

        try {
            val allNodes = graphRepo.getActiveNodes(assistantId)
            val allEdges = graphRepo.getAllEdges(assistantId)
            
            // Build set of node IDs that have at least one edge
            val connectedNodeIds = mutableSetOf<Int>()
            for (edge in allEdges) {
                connectedNodeIds.add(edge.sourceNodeId)
                connectedNodeIds.add(edge.targetNodeId)
            }
            
            // Find orphan nodes (no edges at all)
            val orphanNodes = allNodes.filter { it.id !in connectedNodeIds }
            
            // For each orphan, try to create a connection or mark for review
            for (orphan in orphanNodes) {
                // Skip person nodes - they should have profiles, not be archived
                if (orphan.nodeType == NodeType.PERSON) {
                    // Validate: does this actually look like a person?
                    // If not, it was likely misclassified — fix the type instead of creating a profile
                    if (!looksLikePerson(orphan)) {
                        Log.w(TAG, "Node '${orphan.name}' typed as person but doesn't look like one — reclassifying to concept")
                        graphRepo.updateNode(orphan.copy(nodeType = NodeType.CONCEPT))
                        orphansFixed++
                        continue
                    }
                    
                    // Ensure valid person has a profile
                    val profile = graphRepo.getProfile(orphan.id)
                    if (profile == null) {
                        graphRepo.upsertProfile(PersonProfileEntity(
                            nodeId = orphan.id,
                            assistantId = assistantId,
                            displayName = orphan.name,
                        ))
                        profilesFixed++
                        Log.d(TAG, "Created missing profile for person: ${orphan.name}")
                    }
                    continue
                }
                
                // For trait/attribute orphans, try to find a relevant person to link to
                val descLower = orphan.description.lowercase()
                val personNodes = allNodes.filter { it.nodeType == NodeType.PERSON }
                
                var linkedToPerson = false
                for (person in personNodes) {
                    // Check if the orphan's description mentions this person
                    if (descLower.contains(person.name.lowercase())) {
                        // Determine the edge type based on orphan's nature
                        val edgeType = when {
                            descLower.contains("personality") || descLower.contains("trait") -> 
                                RelationType.DESCRIBES_PERSONALITY
                            descLower.contains("physical") || descLower.contains("appearance") -> 
                                RelationType.DESCRIBES_PHYSICAL
                            descLower.contains("interest") || descLower.contains("hobby") -> 
                                RelationType.DESCRIBES_INTEREST
                            else -> RelationType.DESCRIBES_OTHER
                        }
                        
                        graphRepo.upsertEdge(MemoryEdgeEntity(
                            assistantId = assistantId,
                            sourceNodeId = orphan.id,
                            targetNodeId = person.id,
                            relationType = edgeType,
                            description = "Auto-linked: ${orphan.name} → ${person.name}",
                        ))
                        orphansFixed++
                        edgesFixed++
                        linkedToPerson = true
                        Log.d(TAG, "Auto-linked orphan '${orphan.name}' to person '${person.name}'")
                        break
                    }
                }
                
                // If couldn't link to a person, and it's old/low-importance, archive it
                if (!linkedToPerson && orphan.importance <= 3) {
                    val daysSinceLastMention = (System.currentTimeMillis() - orphan.lastMentioned) / (1000 * 60 * 60 * 24)
                    if (daysSinceLastMention > 7) {
                        graphRepo.archiveNode(orphan.id)
                        orphansFixed++
                        Log.d(TAG, "Archived stale orphan: ${orphan.name}")
                    }
                }
            }
            
            // Fix profiles with broken relationship references
            val allProfiles = graphRepo.getAllProfiles(assistantId)
            val nodeIdSet = allNodes.map { it.id }.toSet()
            
            for (profile in allProfiles) {
                val relationships = try {
                    JsonInstant.decodeFromString<List<PersonRelationship>>(profile.relationshipsJson)
                } catch (e: Exception) { emptyList() }
                
                // Filter out relationships pointing to non-existent nodes
                val validRelationships = relationships.filter { it.targetNodeId in nodeIdSet }
                
                if (validRelationships.size != relationships.size) {
                    val updatedProfile = profile.copy(
                        relationshipsJson = JsonInstant.encodeToString(validRelationships)
                    )
                    graphRepo.upsertProfile(updatedProfile)
                    profilesFixed++
                    Log.d(TAG, "Cleaned ${relationships.size - validRelationships.size} broken relationships from profile: ${profile.displayName}")
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error during auto-repair", e)
        }

        return RepairStats(orphansFixed, profilesFixed, edgesFixed)
    }

    /**
     * Run background consolidation tasks (called periodically by MemoryConsolidationWorker).
     */
    suspend fun runConsolidation(
        assistantId: String,
        decayHalfLifeDays: Double = 14.0,
        maxNodes: Int = 200,
        timelineEnabled: Boolean = true,
    ) {
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

            // 5. Decay confidence for stale nodes
            decayEngine.decayConfidence(assistantId)

            // 6. Enforce graphMaxNodes cap
            decayEngine.enforceMaxNodes(assistantId, maxNodes)

            // 7. Sweep timeline (only if timeline tracking is enabled)
            if (timelineEnabled) {
                timelineManager.sweepTimeline(assistantId)
            }

            // 8. Re-embed any nodes that lost embeddings during merges
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

    /**
     * Heuristic: does this node's description look like it's about a person?
     * Used to catch misclassified nodes before blindly creating PersonProfileEntity.
     */
    private fun looksLikePerson(node: MemoryNodeEntity): Boolean {
        val desc = node.description.lowercase()
        val name = node.name.lowercase()

        // If the node is the user or assistant profile, it's definitely a person
        if (desc.contains("the user") || desc.contains("the assistant")) return true

        // Person indicators: pronouns, titles, age references, occupation words
        val personIndicators = listOf(
            "he ", "she ", "they ", "him ", "her ", "his ", "their ",
            "mr.", "mrs.", "ms.", "dr.", "prof.",
            "years old", "age ", "born ",
            "friend", "sibling", "parent", "partner", "colleague",
            "person", "man ", "woman ", "boy ", "girl ",
            "works as", "works at", "studies", "student",
        )
        if (personIndicators.any { desc.contains(it) }) return true

        // Non-person indicators: if description contains these, likely not a person
        val objectIndicators = listOf(
            "boat", "car", "house", "building", "tool", "device", "app",
            "software", "game", "book", "movie", "song", "food", "drink",
            "place", "city", "country", "location", "address",
            "concept", "idea", "theory", "method",
        )
        if (objectIndicators.any { desc.contains(it) || name.contains(it) }) return false

        // If description is very short or empty, we can't tell — assume it's valid
        // (better to keep than wrongly reclassify)
        if (desc.length < 10) return true

        return true // Default: trust the classification
    }
}
