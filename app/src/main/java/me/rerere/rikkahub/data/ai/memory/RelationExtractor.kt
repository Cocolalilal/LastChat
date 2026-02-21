package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.RelationType
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Uses an LLM to extract entities, relations, and timeline events from conversation messages.
 * This runs asynchronously after each user↔assistant exchange.
 */
class RelationExtractor(
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val TAG = "RelationExtractor"
    }

    @Serializable
    data class ExtractionResult(
        val nodes: List<ExtractedNode> = emptyList(),
        val edges: List<ExtractedEdge> = emptyList(),
        val timelineEvents: List<ExtractedTimelineEvent> = emptyList(),
        val personUpdates: List<ExtractedPersonUpdate> = emptyList(),
    )

    @Serializable
    data class ExtractedPersonUpdate(
        val personName: String,
        val birthYear: Int? = null,
        val birthMonth: Int? = null,
        val birthDay: Int? = null,
        val personalityTraits: List<String> = emptyList(),
        val physicalAttributes: List<String> = emptyList(),
        val otherInfo: List<String> = emptyList(),
        val pronouns: String? = null,
        val occupation: String? = null,
        val location: String? = null,
        val interests: List<String> = emptyList(),
        val relationships: List<ExtractedRelationship> = emptyList(),
    )

    @Serializable
    data class ExtractedRelationship(
        val targetName: String,
        val relationType: String,
        val relationLabel: String? = null,
        val notes: String = "",
    )

    @Serializable
    data class ExtractedNode(
        val name: String,
        val type: String,
        val description: String = "",
        val importance: Int = 5,
        val emotionalValence: Float = 0f,
        val validFrom: String? = null,
        val validUntil: String? = null,
    )

    @Serializable
    data class ExtractedEdge(
        val source: String, // Node name
        val target: String, // Node name
        val relationType: String,
        val description: String = "",
    )

    @Serializable
    data class ExtractedTimelineEvent(
        val nodeName: String,
        val eventType: String, // "upcoming", "ongoing", "completed"
        val description: String = "",
        val scheduledDate: String? = null, // ISO date string
    )

    /**
     * Extract entities, relations, and timeline events from a conversation exchange.
     */
    suspend fun extract(
        assistantId: String,
        userMessage: String,
        assistantReply: String,
        existingNodeNames: List<String> = emptyList(),
    ): ExtractionResult {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.assistants.find { it.id.toString() == assistantId }
        if (assistant == null) {
            Log.w(TAG, "Assistant not found: $assistantId")
            return ExtractionResult()
        }

        val backgroundModelId = assistant.summarizerModelId
            ?: assistant.backgroundModelId
            ?: assistant.chatModelId
            ?: settings.chatModelId
        val model = settings.findModelById(backgroundModelId) ?: run {
            Log.w(TAG, "No background model found for graph extraction")
            return ExtractionResult()
        }
        val providerSetting = model.findProvider(settings.providers) ?: run {
            Log.w(TAG, "No provider found for background model")
            return ExtractionResult()
        }
        val provider = providerManager.getProviderByType(providerSetting)

        val existingNodesHint = if (existingNodeNames.isNotEmpty()) {
            "\n**Existing nodes in the graph** (re-use these names if referring to the same entity):\n${existingNodeNames.joinToString(", ")}\n"
        } else ""

        val profileEdgeTypes = RelationType.PROFILE_SUMMARY.joinToString(", ")
        val semanticEdgeTypes = RelationType.SEMANTIC.joinToString(", ")

        val prompt = """
            Analyze this conversation exchange and extract structured information for a knowledge graph.
            $existingNodesHint
            **User:** $userMessage
            
            **Assistant:** $assistantReply
            
            Extract:
            1. **Entities** (people, places, objects, events, concepts, preferences, emotions, plans)
            2. **Relations** between entities (semantic connections, NOT interpersonal relationships)
            3. **Timeline events** (anything with temporal relevance: upcoming plans, deadlines, ongoing activities)
            4. **Person profile updates** — CRITICAL: emit a personUpdate for EVERY person mentioned when ANY personal info is learned:
               - Birth year/month/day (MUST go here, NOT as separate nodes)
               - Pronouns (he/him, she/her, they/them, etc.)
               - Occupation (job, profession, role)
               - Location (city, country, or general area)
               - Interests (hobbies, likes, passions — as a list)
               - Personality traits (character, temperament, behavior patterns — NOT physical)
               - Physical attributes (appearance, height, hair color, etc. — NOT personality)
               - Other info (background facts — NOT the above categories)
               - Relationships with other people (family, friends, romantic partners, etc.)
               Each category is STRICTLY separate. Never mix categories.
            
            IMPORTANT RULES:
            - Personal facts (birthday, traits, appearance, occupation, location, interests) MUST be in personUpdates. You may ALSO create nodes/edges for them, but the personUpdate is MANDATORY.
            - Relationships are BIDIRECTIONAL: if A is B's sibling, emit personUpdates for BOTH A and B with the relationship to each other.
            - ALWAYS use the person's exact name as it appears in existing nodes (match case and spelling exactly).
            - If someone is described as having a trait (e.g. "empathetic"), put it in personalityTraits AND you may create a concept node and edge.
            - If a birthday is mentioned (e.g. "born March 5"), put birthMonth: 3, birthDay: 5 in the personUpdate.
            
            Valid node types: ${NodeType.ALL.joinToString(", ")}
            Valid semantic edge types: $semanticEdgeTypes
            Valid profile-contributing edge types: $profileEdgeTypes
            Valid event types: upcoming, ongoing, completed, recurring
            
            Relationship types for personUpdates.relationships:
            Family: parent, child, sibling, spouse, grandparent, grandchild, aunt_uncle, niece_nephew, cousin, in_law
            Social: friend, best_friend, acquaintance, neighbor
            Professional: colleague, boss, employee, mentor, mentee, client
            Romantic: partner, ex_partner, crush
            Other: rival, enemy, roommate, pet_owner, pet
            
            For edges, use semantic types (connected_to, associated_with, interacts_with, likes, owns, etc.)
            For trait/attribute → person edges, use describes_personality, describes_physical, describes_other, describes_interest.
            For interpersonal relationships, put them in personUpdates.relationships (NOT as edges).
            
            Only extract genuinely meaningful information. Skip trivial/generic content.
            Importance: 1-3 trivial, 4-6 normal, 7-9 important, 10 critical.
            Emotional valence: -1.0 very negative to 1.0 very positive, 0 neutral.
            
            Output ONLY valid JSON (no markdown fences):
            {
              "nodes": [{"name": "...", "type": "...", "description": "...", "importance": 5, "emotionalValence": 0.0}],
              "edges": [{"source": "NodeName1", "target": "NodeName2", "relationType": "connected_to", "description": "..."}],
              "timelineEvents": [{"nodeName": "...", "eventType": "upcoming", "description": "...", "scheduledDate": "2025-03-01"}],
              "personUpdates": [{"personName": "Alice", "pronouns": "she/her", "occupation": "Engineer", "location": "Tokyo", "interests": ["hiking", "cooking"], "personalityTraits": ["kind"], "physicalAttributes": ["tall"], "otherInfo": [], "birthMonth": 3, "birthDay": 5, "relationships": [{"targetName": "Bob", "relationType": "sibling", "relationLabel": "older brother", "notes": ""}]}]
            }
            
            If nothing meaningful to extract, return: {"nodes": [], "edges": [], "timelineEvents": [], "personUpdates": []}
        """.trimIndent()

        return try {
            val response = provider.generateText(
                providerSetting = providerSetting,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(model = model, temperature = 0.3f)
            )
            val responseText = response.choices.firstOrNull()?.message?.toContentText() ?: return ExtractionResult()
            parseExtractionResult(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract relations", e)
            ExtractionResult()
        }
    }

    private fun parseExtractionResult(responseText: String): ExtractionResult {
        return try {
            // Find JSON in the response
            val jsonStart = responseText.indexOf("{")
            val jsonEnd = responseText.lastIndexOf("}") + 1
            if (jsonStart < 0 || jsonEnd <= jsonStart) return ExtractionResult()

            val jsonStr = responseText.substring(jsonStart, jsonEnd)
            val json = JsonInstant.parseToJsonElement(jsonStr).jsonObject

            val nodes = json["nodes"]?.jsonArray?.mapNotNull { nodeEl ->
                try {
                    val obj = nodeEl.jsonObject
                    ExtractedNode(
                        name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        type = obj["type"]?.jsonPrimitive?.content ?: "concept",
                        description = obj["description"]?.jsonPrimitive?.content ?: "",
                        importance = obj["importance"]?.jsonPrimitive?.intOrNull ?: 5,
                        emotionalValence = obj["emotionalValence"]?.jsonPrimitive?.floatOrNull ?: 0f,
                        validFrom = obj["validFrom"]?.jsonPrimitive?.content,
                        validUntil = obj["validUntil"]?.jsonPrimitive?.content,
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()

            val edges = json["edges"]?.jsonArray?.mapNotNull { edgeEl ->
                try {
                    val obj = edgeEl.jsonObject
                    ExtractedEdge(
                        source = obj["source"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        target = obj["target"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        relationType = obj["relationType"]?.jsonPrimitive?.content ?: "related_to",
                        description = obj["description"]?.jsonPrimitive?.content ?: "",
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()

            val timelineEvents = json["timelineEvents"]?.jsonArray?.mapNotNull { eventEl ->
                try {
                    val obj = eventEl.jsonObject
                    ExtractedTimelineEvent(
                        nodeName = obj["nodeName"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        eventType = obj["eventType"]?.jsonPrimitive?.content ?: "upcoming",
                        description = obj["description"]?.jsonPrimitive?.content ?: "",
                        scheduledDate = obj["scheduledDate"]?.jsonPrimitive?.content,
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()

            val personUpdates = json["personUpdates"]?.jsonArray?.mapNotNull { updateEl ->
                try {
                    val obj = updateEl.jsonObject
                    val relationships = obj["relationships"]?.jsonArray?.mapNotNull { relEl ->
                        try {
                            val relObj = relEl.jsonObject
                            ExtractedRelationship(
                                targetName = relObj["targetName"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                                relationType = relObj["relationType"]?.jsonPrimitive?.content ?: "friend",
                                relationLabel = relObj["relationLabel"]?.jsonPrimitive?.content,
                                notes = relObj["notes"]?.jsonPrimitive?.content ?: "",
                            )
                        } catch (e: Exception) { null }
                    } ?: emptyList()
                    
                    ExtractedPersonUpdate(
                        personName = obj["personName"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        birthYear = obj["birthYear"]?.jsonPrimitive?.intOrNull,
                        birthMonth = obj["birthMonth"]?.jsonPrimitive?.intOrNull,
                        birthDay = obj["birthDay"]?.jsonPrimitive?.intOrNull,
                        personalityTraits = obj["personalityTraits"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
                        physicalAttributes = obj["physicalAttributes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
                        otherInfo = obj["otherInfo"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
                        pronouns = obj["pronouns"]?.jsonPrimitive?.content,
                        occupation = obj["occupation"]?.jsonPrimitive?.content,
                        location = obj["location"]?.jsonPrimitive?.content,
                        interests = obj["interests"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList(),
                        relationships = relationships,
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()

            ExtractionResult(nodes, edges, timelineEvents, personUpdates)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse extraction result", e)
            ExtractionResult()
        }
    }
}
