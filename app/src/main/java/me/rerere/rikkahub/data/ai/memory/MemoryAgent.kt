package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeStatus
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.db.entity.CategorizedAttribute
import me.rerere.rikkahub.data.db.entity.PersonRelationship
import me.rerere.rikkahub.data.db.entity.PersonRelationType
import me.rerere.rikkahub.data.db.entity.RelationType
import me.rerere.rikkahub.data.repository.GraphMemoryRepository
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.serialization.json.Json

/**
 * Orchestrates the advanced memory pipeline.
 *
 * Design philosophy: SPARSE, PERSON-CENTRIC
 * - Nodes = entities that exist in the world (person, place, thing)
 * - Attributes = properties OF entities, stored on their profile/description
 * - Person profiles are the single source of truth for person facts
 *
 * Pipeline:
 * 1. Extract entities + attributes from conversation
 * 2. Upsert entity nodes (max 3 per exchange)
 * 3. Update person profiles directly (no trait/attribute nodes)
 * 4. Create/reinforce edges between entities
 * 5. Create timeline events (with deduplication)
 * 6. Embed new/updated nodes
 */
class MemoryAgent(
    private val graphRepo: GraphMemoryRepository,
    private val extractor: RelationExtractor,
    private val decayEngine: DecayEngine,
    private val timelineManager: TimelineManager,
    private val embeddingService: EmbeddingService,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val TAG = "MemoryAgent"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
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

    /**
     * Process a conversation exchange.
     * Called fire-and-forget after each user↔assistant message pair.
     *
     * Pipeline:
     * 1. Extract entities/attributes from the exchange
     * 2. Upsert entity nodes (max 3 new per exchange)
     * 3. Update person profiles directly with extracted attributes
     * 4. Create/reinforce edges
     * 5. Create timeline events (deduplicated)
     * 6. Embed new/updated nodes
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

            // Resolve model/provider for extraction
            val settings = settingsStore.settingsFlow.value
            val assistant = settings.getCurrentAssistant()
            val backgroundModelId = assistant.summarizerModelId ?: assistant.backgroundModelId ?: settings.chatModelId
            val model = settings.findModelById(backgroundModelId) ?: run {
                Log.w(TAG, "No model found for extraction")
                return ProcessingResult(error = "No model configured")
            }
            val provider = model.findProvider(settings.providers) ?: run {
                Log.w(TAG, "No provider found for extraction model")
                return ProcessingResult(error = "No provider configured")
            }

            // Get existing nodes and their aliases for the extractor hint
            val existingNodes = graphRepo.getActiveNodes(assistantId)
            val existingNames = existingNodes.map { it.name }
            val existingAliases = buildAliasMap(assistantId, existingNodes)

            // Build existing profile summaries for the extractor
            val profileSummaries = buildProfileSummaries(assistantId, existingNodes)

            // 1. Extract structured data from the exchange
            val result = extractor.extract(
                assistantId = assistantId,
                userMessage = userMessage,
                assistantReply = assistantReply,
                existingNodeNames = existingNames,
                existingAliases = existingAliases,
                existingProfileSummaries = profileSummaries,
                provider = provider,
                model = model,
            )
            Log.i(TAG, "Extracted ${result.entities.size} entities, ${result.relationships.size} relationships, ${result.timelineEvents.size} timeline events, ${result.personUpdates.size} person updates")

            if (result.entities.isEmpty() && result.relationships.isEmpty() && result.timelineEvents.isEmpty() && result.personUpdates.isEmpty()) {
                Log.i(TAG, "Nothing meaningful extracted, skipping")
                return ProcessingResult()
            }

            // 2. Upsert entity nodes and build name→id map
            val now = System.currentTimeMillis()
            val nameToId = mutableMapOf<String, Int>()
            var nodesUpserted = 0

            for (entity in result.entities) {
                // Fixed importance: person=10, place/thing=7
                val importance = when (entity.type) {
                    NodeType.PERSON -> 10
                    else -> 7
                }

                val node = MemoryNodeEntity(
                    assistantId = assistantId,
                    nodeType = entity.type,
                    name = entity.name,
                    description = entity.description,
                    importance = importance,
                    firstMentioned = now,
                    lastMentioned = now,
                    mentionCount = 1,
                    status = NodeStatus.ACTIVE,
                    confidence = 1.0f,
                    sourceTurn = conversationId ?: "",
                )

                val nodeId = graphRepo.upsertNode(assistantId, node)
                nameToId[entity.name] = nodeId
                nodesUpserted++

                // Auto-create PersonProfile for person-type nodes
                if (entity.type == NodeType.PERSON) {
                    ensurePersonProfile(assistantId, nodeId, entity.name)
                }
            }

            // Also map existing nodes not in extraction
            for (existingNode in existingNodes) {
                if (existingNode.name !in nameToId) {
                    nameToId[existingNode.name] = existingNode.id
                }
                // Also map by aliases
                val aliases = existingAliases[existingNode.name] ?: emptyList()
                for (alias in aliases) {
                    if (alias !in nameToId) {
                        nameToId[alias] = existingNode.id
                    }
                }
            }

            // 3. Process person profile updates — attributes go directly into profile
            var personUpdatesCount = 0
            for (update in result.personUpdates) {
                val personNodeId = resolvePersonNodeId(update.personName, nameToId, assistantId)
                    ?: continue

                try {
                    applyPersonUpdate(assistantId, personNodeId, update, nameToId)
                    personUpdatesCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply person update for ${update.personName}", e)
                }
            }

            // 4. Create/reinforce edges
            var edgesCreated = 0
            for (rel in result.relationships) {
                val sourceId = resolveNodeId(rel.source, nameToId, assistantId)
                val targetId = resolveNodeId(rel.target, nameToId, assistantId)

                if (sourceId != null && targetId != null && sourceId != targetId) {
                    val edge = MemoryEdgeEntity(
                        assistantId = assistantId,
                        sourceNodeId = sourceId,
                        targetNodeId = targetId,
                        relationType = if (rel.type in RelationType.ALL) rel.type else RelationType.RELATED_TO,
                        description = rel.description,
                        createdAt = now,
                        lastReinforced = now,
                    )
                    graphRepo.upsertEdge(edge)
                    edgesCreated++
                }
            }

            // 5. Create timeline events (deduplicated)
            var timelineEventsCreated = 0
            if (timelineEnabled) {
                for (event in result.timelineEvents) {
                    val nodeId = resolveNodeId(event.entityName, nameToId, assistantId)
                    if (nodeId != null) {
                        // Determine event type based on date (past vs future)
                        val eventType = when {
                            event.recurring != null -> "recurring"
                            event.date != null -> {
                                val eventEpoch = parseDateToEpoch(event.date)
                                if (eventEpoch != null && eventEpoch < now) "completed" else "upcoming"
                            }
                            else -> "upcoming"
                        }
                        val created = timelineManager.createEventFromExtraction(
                            assistantId = assistantId,
                            nodeId = nodeId,
                            eventType = eventType,
                            description = event.description,
                            scheduledDateStr = event.date,
                            recurrenceRule = event.recurring,
                        )
                        if (created > 0) timelineEventsCreated++
                    }
                }
            }

            // 6. Embed new/updated nodes
            try {
                embedMissingNodes(assistantId)
            } catch (e: Exception) {
                Log.w(TAG, "Embedding failed (non-fatal)", e)
            }

            val processingResult = ProcessingResult(
                nodesUpserted = nodesUpserted,
                edgesCreated = edgesCreated,
                timelineEvents = timelineEventsCreated,
                personUpdates = personUpdatesCount,
            )
            Log.i(TAG, "Exchange processing complete: $processingResult")
            return processingResult
        } catch (e: Exception) {
            Log.e(TAG, "Error processing exchange", e)
            return ProcessingResult(error = e.message ?: "Unknown error")
        }
    }

    // =============================================
    // Person Profile Updates — Direct, No Trait Nodes
    // =============================================

    /**
     * Apply extracted person attributes directly to PersonProfileEntity.
     * Uses the contradiction policy:
     * - Single-value mutable (pronouns, occupation, location): REPLACE
     * - Lists (interests): ADD, no duplicates
     * - Structured lists (personality, physical, otherInfo): MERGE by category
     * - Immutable (birth dates): SET ONCE
     */
    private suspend fun applyPersonUpdate(
        assistantId: String,
        personNodeId: Int,
        update: RelationExtractor.ExtractedPersonUpdate,
        nameToId: Map<String, Int>,
    ) {
        val profile = graphRepo.getProfile(personNodeId)
            ?: PersonProfileEntity(nodeId = personNodeId, assistantId = assistantId)

        var updated = profile

        // SET ONCE: birth info (only overwrite if currently null)
        if (update.birthYear != null && updated.birthYear == null) {
            updated = updated.copy(birthYear = update.birthYear)
        }
        if (update.birthMonth != null && updated.birthMonth == null) {
            updated = updated.copy(birthMonth = update.birthMonth)
        }
        if (update.birthDay != null && updated.birthDay == null) {
            updated = updated.copy(birthDay = update.birthDay)
        }

        // REPLACE: single-value mutable fields
        if (!update.pronouns.isNullOrBlank()) {
            updated = updated.copy(pronouns = update.pronouns)
        }
        if (!update.occupation.isNullOrBlank()) {
            updated = updated.copy(occupation = update.occupation)
        }
        if (!update.location.isNullOrBlank()) {
            updated = updated.copy(location = update.location)
        }

        // MERGE by category: personality traits
        if (update.personalityTraits.isNotEmpty()) {
            updated = updated.copy(
                personalityJson = mergeCategorizedAttributes(
                    existing = updated.personalityJson,
                    newItems = update.personalityTraits,
                )
            )
        }

        // MERGE by category: physical attributes
        if (update.physicalAttributes.isNotEmpty()) {
            updated = updated.copy(
                physicalJson = mergeCategorizedAttributes(
                    existing = updated.physicalJson,
                    newItems = update.physicalAttributes,
                )
            )
        }

        // MERGE by category: other info
        if (update.otherInfo.isNotEmpty()) {
            updated = updated.copy(
                otherInfoJson = mergeCategorizedAttributes(
                    existing = updated.otherInfoJson,
                    newItems = update.otherInfo,
                )
            )
        }

        // ADD: interests (no duplicates)
        if (update.interests.isNotEmpty()) {
            val existingInterests = try {
                json.decodeFromString<List<String>>(updated.interestsJson)
            } catch (e: Exception) { emptyList() }
            val merged = (existingInterests + update.interests).distinctBy { it.lowercase().trim() }
            updated = updated.copy(interestsJson = json.encodeToString(merged))
        }

        // Relationships
        if (update.relationships.isNotEmpty()) {
            val existingRels = try {
                json.decodeFromString<List<PersonRelationship>>(updated.relationshipsJson)
            } catch (e: Exception) { emptyList() }

            val updatedRels = existingRels.toMutableList()
            for (rel in update.relationships) {
                val targetNodeId = resolveNodeId(rel.targetName, nameToId, assistantId)
                if (targetNodeId != null && targetNodeId != personNodeId) {
                    val relType = if (rel.relationType in PersonRelationType.ALL) rel.relationType else PersonRelationType.ACQUAINTANCE
                    val relationship = PersonRelationship(
                        targetNodeId = targetNodeId,
                        targetName = rel.targetName,
                        relationType = relType,
                        relationLabel = rel.label,
                    )
                    // Replace any existing relationship with the same target person
                    val existingIdx = updatedRels.indexOfFirst { it.targetNodeId == targetNodeId }
                    if (existingIdx >= 0) {
                        updatedRels[existingIdx] = relationship
                    } else {
                        updatedRels.add(relationship)
                    }
                    // Create bidirectional relationship (also replaces)
                    createBidirectionalRelationship(assistantId, personNodeId, update.personName, relationship)
                }
            }

            updated = updated.copy(relationshipsJson = json.encodeToString(updatedRels.toList()))
        }

        graphRepo.upsertProfile(updated)
        Log.d(TAG, "Updated profile for ${update.personName} (nodeId=$personNodeId)")
    }

    /**
     * Merge new categorized attributes into existing ones.
     * Same-category items get REPLACED; different categories get ADDED.
     */
    private fun mergeCategorizedAttributes(
        existing: String,
        newItems: List<RelationExtractor.ExtractedAttribute>,
    ): String {
        val existingAttrs = try {
            json.decodeFromString<List<CategorizedAttribute>>(existing)
        } catch (e: Exception) { emptyList() }

        val merged = existingAttrs.toMutableList()
        for (item in newItems) {
            val attr = CategorizedAttribute(
                category = item.category.lowercase().trim(),
                value = item.value.trim(),
            )
            // Fuzzy match: if new category contains existing or vice versa, treat as same
            val existingIndex = merged.indexOfFirst { existing ->
                existing.category == attr.category ||
                existing.category.contains(attr.category) ||
                attr.category.contains(existing.category)
            }
            if (existingIndex >= 0) {
                merged[existingIndex] = attr
            } else {
                merged.add(attr)
            }
        }
        return json.encodeToString(merged)
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
        val targetProfile = graphRepo.getProfile(relationship.targetNodeId) ?: return
        val existingRels = try {
            json.decodeFromString<List<PersonRelationship>>(targetProfile.relationshipsJson)
        } catch (e: Exception) { emptyList() }

        val reverseType = PersonRelationType.getReverse(relationship.relationType) ?: relationship.relationType

        val reverseRel = PersonRelationship(
            targetNodeId = sourceNodeId,
            targetName = sourceName,
            relationType = reverseType,
        )

        // Replace any existing relationship with the same source person
        val updatedRels = existingRels.toMutableList()
        val existingIdx = updatedRels.indexOfFirst { it.targetNodeId == sourceNodeId }
        if (existingIdx >= 0) {
            updatedRels[existingIdx] = reverseRel
        } else {
            updatedRels.add(reverseRel)
        }
        graphRepo.upsertProfile(
            targetProfile.copy(relationshipsJson = json.encodeToString(updatedRels.toList()))
        )
    }

    // =============================================
    // Helpers
    // =============================================

    /**
     * Build alias map: nodeName -> list of aliases.
     */
    private suspend fun buildAliasMap(
        assistantId: String,
        nodes: List<MemoryNodeEntity>,
    ): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        for (node in nodes) {
            if (node.nodeType == NodeType.PERSON) {
                val profile = graphRepo.getProfile(node.id)
                if (profile != null) {
                    val aliases = try {
                        json.decodeFromString<List<String>>(profile.aliasesJson)
                    } catch (e: Exception) { emptyList() }
                    if (aliases.isNotEmpty()) {
                        result[node.name] = aliases
                    }
                }
            }
        }
        return result
    }

    /**
     * Build profile summaries for each person node (for extraction context).
     * Keeps summaries concise to avoid bloating the prompt.
     */
    private suspend fun buildProfileSummaries(
        assistantId: String,
        nodes: List<MemoryNodeEntity>,
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (node in nodes) {
            if (node.nodeType != NodeType.PERSON) continue
            val profile = graphRepo.getProfile(node.id) ?: continue
            val parts = mutableListOf<String>()
            if (profile.pronouns.isNotBlank()) parts.add("pronouns: ${profile.pronouns}")
            if (profile.occupation.isNotBlank()) parts.add("occupation: ${profile.occupation}")
            if (profile.location.isNotBlank()) parts.add("location: ${profile.location}")
            val personality = try { json.decodeFromString<List<CategorizedAttribute>>(profile.personalityJson) } catch (e: Exception) { emptyList() }
            if (personality.isNotEmpty()) parts.add("personality: ${personality.joinToString(", ") { it.value }}")
            val physical = try { json.decodeFromString<List<CategorizedAttribute>>(profile.physicalJson) } catch (e: Exception) { emptyList() }
            if (physical.isNotEmpty()) parts.add("physical: ${physical.joinToString(", ") { it.value }}")
            val otherInfo = try { json.decodeFromString<List<CategorizedAttribute>>(profile.otherInfoJson) } catch (e: Exception) { emptyList() }
            if (otherInfo.isNotEmpty()) parts.add("other: ${otherInfo.joinToString(", ") { it.value }}")
            val rels = try { json.decodeFromString<List<PersonRelationship>>(profile.relationshipsJson) } catch (e: Exception) { emptyList() }
            if (rels.isNotEmpty()) parts.add("relationships: ${rels.joinToString(", ") { "${it.targetName} (${it.relationType})" }}")
            if (parts.isNotEmpty()) {
                result[node.name] = parts.joinToString("; ")
            }
        }
        return result
    }

    /**
     * Resolve a person name to node ID, checking:
     * exact name → case-insensitive → substring → alias → DB fallback
     */
    private suspend fun resolvePersonNodeId(
        name: String,
        nameToId: Map<String, Int>,
        assistantId: String,
    ): Int? {
        // Case-insensitive match requires checking all keys
        return resolveNodeId(name, nameToId, assistantId)?.let { id ->
            // Verify it's actually a person node
            val node = graphRepo.getNodeById(id)
            if (node?.nodeType == NodeType.PERSON) id else null
        }
    }

    /**
     * Resolve any entity name to node ID.
     */
    private suspend fun resolveNodeId(
        name: String,
        nameToId: Map<String, Int>,
        assistantId: String,
    ): Int? {
        return nameToId[name]
            ?: nameToId.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
            ?: nameToId.entries.firstOrNull {
                it.key.contains(name, ignoreCase = true) ||
                name.contains(it.key, ignoreCase = true)
            }?.value
            ?: graphRepo.findNodeByName(assistantId, name)?.id
    }

    /**
     * Ensure a PersonProfile exists for a person node.
     */
    private suspend fun ensurePersonProfile(
        assistantId: String,
        nodeId: Int,
        displayName: String,
    ) {
        val existing = graphRepo.getProfile(nodeId)
        if (existing == null) {
            graphRepo.upsertProfile(
                PersonProfileEntity(
                    nodeId = nodeId,
                    assistantId = assistantId,
                    displayName = displayName,
                )
            )
        }
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

            // 3. Archive orphaned nodes (tightened: 7 days)
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

            // 9. Repair: ensure person nodes have profiles
            repairMissingProfiles(assistantId)

            Log.i(TAG, "Graph consolidation complete for assistant $assistantId")
        } catch (e: Exception) {
            Log.e(TAG, "Error during graph consolidation", e)
        }
    }

    /**
     * Ensure all person nodes have a PersonProfile.
     * Runs during consolidation, NOT every exchange (cheaper).
     */
    private suspend fun repairMissingProfiles(assistantId: String) {
        val personNodes = graphRepo.getActiveNodes(assistantId).filter { it.nodeType == NodeType.PERSON }
        var fixed = 0
        for (node in personNodes) {
            if (graphRepo.getProfile(node.id) == null) {
                graphRepo.upsertProfile(
                    PersonProfileEntity(
                        nodeId = node.id,
                        assistantId = assistantId,
                        displayName = node.name,
                    )
                )
                fixed++
            }
        }
        if (fixed > 0) {
            Log.i(TAG, "Created $fixed missing person profiles")
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
                    nodeType = NodeType.THING,
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
