package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeType
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
        userMessage: String,
        assistantReply: String,
        existingNodeNames: List<String> = emptyList(),
    ): ExtractionResult {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()

        val backgroundModelId = assistant.summarizerModelId
            ?: assistant.backgroundModelId
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

        val prompt = """
            Analyze this conversation exchange and extract structured information for a knowledge graph.
            $existingNodesHint
            **User:** $userMessage
            
            **Assistant:** $assistantReply
            
            Extract:
            1. **Entities** (people, places, objects, events, concepts, preferences, emotions, plans)
            2. **Relations** between entities
            3. **Timeline events** (anything with temporal relevance: upcoming plans, deadlines, ongoing activities)
            
            Valid node types: ${NodeType.ALL.joinToString(", ")}
            Valid relation types: knows, likes, dislikes, scheduled_for, happened_at, related_to, feels_about, owns, part_of, similar_to
            Valid event types: upcoming, ongoing, completed, recurring
            
            Only extract genuinely meaningful information. Skip trivial/generic content.
            Importance: 1-3 trivial, 4-6 normal, 7-9 important, 10 critical.
            Emotional valence: -1.0 very negative to 1.0 very positive, 0 neutral.
            
            Output ONLY valid JSON (no markdown fences):
            {
              "nodes": [{"name": "...", "type": "...", "description": "...", "importance": 5, "emotionalValence": 0.0}],
              "edges": [{"source": "NodeName1", "target": "NodeName2", "relationType": "...", "description": "..."}],
              "timelineEvents": [{"nodeName": "...", "eventType": "upcoming", "description": "...", "scheduledDate": "2025-03-01"}]
            }
            
            If nothing meaningful to extract, return: {"nodes": [], "edges": [], "timelineEvents": []}
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

            ExtractionResult(nodes, edges, timelineEvents)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse extraction result", e)
            ExtractionResult()
        }
    }
}
