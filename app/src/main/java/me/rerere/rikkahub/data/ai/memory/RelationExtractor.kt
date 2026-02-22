package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.RelationType

private const val TAG = "RelationExtractor"

class RelationExtractor(
    private val providerManager: ProviderManager,
) {
    // =============================================
    // Data classes for extraction results
    // =============================================

    @Serializable
    data class ExtractedEntity(
        val name: String,
        val type: String, // "person", "place", "thing"
        val description: String = "",
    )

    @Serializable
    data class ExtractedPersonUpdate(
        val personName: String,
        val birthYear: Int? = null,
        val birthMonth: Int? = null,
        val birthDay: Int? = null,
        val pronouns: String? = null,
        val occupation: String? = null,
        val location: String? = null,
        val interests: List<String> = emptyList(),
        val personalityTraits: List<ExtractedAttribute> = emptyList(),
        val physicalAttributes: List<ExtractedAttribute> = emptyList(),
        val otherInfo: List<ExtractedAttribute> = emptyList(),
        val relationships: List<ExtractedRelationshipUpdate> = emptyList(),
    )

    @Serializable
    data class ExtractedAttribute(
        val category: String, // e.g. "hair", "height", "demeanor"
        val value: String, // e.g. "blonde hair", "tall", "calm and composed"
    )

    @Serializable
    data class ExtractedRelationshipUpdate(
        val targetName: String,
        val relationType: String,
        val label: String? = null,
    )

    @Serializable
    data class ExtractedRelationship(
        val source: String,
        val target: String,
        val type: String, // from RelationType
        val description: String = "",
    )

    @Serializable
    data class ExtractedTimelineEvent(
        val entityName: String,
        val description: String,
        val date: String? = null, // ISO date: "2025-03-15"
        val recurring: String? = null, // e.g. "weekly", "monthly", "yearly"
    )

    @Serializable
    data class ExtractionResult(
        val entities: List<ExtractedEntity> = emptyList(),
        val personUpdates: List<ExtractedPersonUpdate> = emptyList(),
        val relationships: List<ExtractedRelationship> = emptyList(),
        val timelineEvents: List<ExtractedTimelineEvent> = emptyList(),
    )

    // =============================================
    // Generic name blocklist for post-extraction guardrails
    // =============================================

    companion object {
        val GENERIC_NAMES = setOf(
            "barista", "waiter", "waitress", "driver", "teacher",
            "professor", "doctor", "nurse", "stranger", "someone",
            "person", "man", "woman", "boy", "girl", "friend",
            "user", "assistant", "ai", "bot", "human",
            "they", "them", "he", "she", "it",
            "everyone", "anyone", "nobody", "somebody",
        )
    }

    // =============================================
    // Post-extraction server-side guardrails
    // =============================================

    fun validateExtraction(result: ExtractionResult): ExtractionResult {
        return result.copy(
            entities = result.entities
                .take(3) // Hard cap: max 3 new entities per exchange
                .filter { it.type in NodeType.ALL } // Valid types only
                .filter { it.name.length >= 2 } // No single-char names
                .filter { it.name.lowercase().trim() !in GENERIC_NAMES }, // Blocklist
            personUpdates = result.personUpdates
                .filter { it.personName.length >= 2 }
                .filter { it.personName.lowercase().trim() !in GENERIC_NAMES },
            relationships = result.relationships
                .filter { it.type in RelationType.ALL } // Valid relation types only
                .filter { it.source.isNotBlank() && it.target.isNotBlank() },
            timelineEvents = result.timelineEvents
                .filter { !it.date.isNullOrBlank() || !it.recurring.isNullOrBlank() } // Must have a date or recurring rule
                .filter { it.entityName.isNotBlank() && it.description.isNotBlank() },
        )
    }

    // =============================================
    // Main extraction function
    // =============================================

    suspend fun extract(
        assistantId: String,
        userMessage: String,
        assistantReply: String,
        existingNodeNames: List<String> = emptyList(),
        existingAliases: Map<String, List<String>> = emptyMap(),
        existingProfileSummaries: Map<String, String> = emptyMap(),
        provider: ProviderSetting,
        model: Model,
    ): ExtractionResult {
        val existingNamesSection = if (existingNodeNames.isNotEmpty()) {
            val namesList = existingNodeNames.joinToString(", ") { name ->
                val aliases = existingAliases[name]
                if (!aliases.isNullOrEmpty()) {
                    "$name (also known as: ${aliases.joinToString(", ")})"
                } else {
                    name
                }
            }
            "**Existing entities** — reuse these EXACT names if referring to the same entity:\n$namesList"
        } else {
            "**No existing entities yet.**"
        }

        val profileSection = if (existingProfileSummaries.isNotEmpty()) {
            val entries = existingProfileSummaries.entries.joinToString("\n") { (name, summary) ->
                "- $name: $summary"
            }
            "**What we already know about these people** (update/correct if the conversation changes any of this):\n$entries"
        } else ""

        val prompt = """
            Analyze this conversation and extract ONLY what is worth remembering long-term.
            
            $existingNamesSection
            
            $profileSection
            
            **User:** $userMessage
            **Assistant:** $assistantReply
            
            Rules:
            1. Only extract NAMED, SPECIFIC entities (people, places, things).
               - People: Only if they have a name or clear identity (e.g. "Mom", "Dr. Smith", "Julia")
               - Places: Only if specifically named (e.g. "Tokyo", "MIT", not "the store")
               - Things: Only if named/specific and meaningful (e.g. "Max the dog", "Project Aurora")
            2. Do NOT create entities for: emotions, activities, concepts, unnamed things, traits, or preferences.
            3. Return AT MOST 3 new entities per exchange. If nothing significant, return empty arrays.
            4. For person updates: put ALL personal attributes in personUpdates.
               - CRITICAL: Each value MUST be a **self-contained, readable phrase**. 
                 BAD: {"category": "hair", "value": "brown"} — "brown" alone is meaningless
                 GOOD: {"category": "hair", "value": "brown hair"}
                 BAD: {"category": "education", "value": "MIT"} — no context
                 GOOD: {"category": "education", "value": "studies at MIT"}
               - Categories:
                 - personalityTraits: category = dimension (e.g. "demeanor"), value = readable trait (e.g. "calm and composed")
                 - physicalAttributes: category = feature (e.g. "hair", "eyes", "build"), value = full description (e.g. "curly brown hair", "wears glasses")
                 - otherInfo: category = info type (e.g. "education", "hobby"), value = complete fact (e.g. "goes to school at Lincoln High")
               - relationships: specify target person and type. If a relationship type CHANGED (e.g. acquaintance→partner), output the NEW type — it will replace the old one.
            5. If the conversation CORRECTS or UPDATES existing info, include the correction. Do NOT avoid outputting changes.
            6. Timeline events: ONLY for concrete dates. Use recurring for repeating events:
               - "yearly" for birthdays and anniversaries
               - "weekly" / "monthly" for regular schedules
               - null for one-time events
               Past events (like a birthday that already happened) still get a date — the system handles past vs future.
            7. Entity relationships (not person profile relationships): only use: related_to, owns, part_of, likes, dislikes, located_at, happened_at, knows
            8. If the exchange is casual banter with no new facts, return ALL empty arrays.
            
            Output ONLY valid JSON:
            {
              "entities": [{"name": "...", "type": "person|place|thing", "description": "..."}],
              "personUpdates": [
                {
                  "personName": "...",
                  "birthYear": null, "birthMonth": null, "birthDay": null,
                  "pronouns": null, "occupation": null, "location": null,
                  "interests": [],
                  "personalityTraits": [{"category": "...", "value": "self-contained description"}],
                  "physicalAttributes": [{"category": "...", "value": "self-contained description"}],
                  "otherInfo": [{"category": "...", "value": "self-contained description"}],
                  "relationships": [{"targetName": "...", "relationType": "parent|sibling|friend|partner|colleague|...", "label": null}]
                }
              ],
              "relationships": [{"source": "...", "target": "...", "type": "...", "description": "..."}],
              "timelineEvents": [{"entityName": "...", "description": "...", "date": "YYYY-MM-DD", "recurring": "yearly|monthly|weekly|daily|null"}]
            }
        """.trimIndent()

        val handler = providerManager.getProviderByType(provider)
        val response = handler.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.user(prompt)),
            params = TextGenerationParams(model = model, temperature = 0.3f),
        )

        val content = response.choices.firstOrNull()?.message?.toContentText() ?: return ExtractionResult()

        return try {
            val jsonStr = extractJsonFromResponse(content)
            val result = lenientJson.decodeFromString<ExtractionResult>(jsonStr)
            // Apply server-side guardrails
            validateExtraction(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse extraction result: $content", e)
            ExtractionResult()
        }
    }

    // =============================================
    // Helpers
    // =============================================

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun extractJsonFromResponse(content: String): String {
        // Try to find JSON block in markdown code fence
        val codeBlockRegex = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?\s*```""")
        codeBlockRegex.find(content)?.let {
            return it.groupValues[1].trim()
        }
        // Try to find JSON object directly
        val jsonStart = content.indexOf('{')
        val jsonEnd = content.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return content.substring(jsonStart, jsonEnd + 1)
        }
        return content
    }
}
