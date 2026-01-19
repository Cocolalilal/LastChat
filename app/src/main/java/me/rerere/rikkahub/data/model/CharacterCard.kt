package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Standard Character Card V2 Format (Chub.ai / SillyTavern compatible)
 * based on the spec: https://github.com/malfoyslastname/character-card-spec-v2
 */
@Serializable
data class CharacterCardV2(
    val spec: String = "chara_card_v2",
    @SerialName("spec_version")
    val specVersion: String = "2.0",
    val data: CharacterCardV2Data
)

@Serializable
data class CharacterCardV2Data(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    @SerialName("first_mes")
    val firstMes: String = "",
    @SerialName("mes_example")
    val mesExample: String = "",
    
    // Creator Metadata
    @SerialName("creator_notes")
    val creatorNotes: String = "",
    @SerialName("system_prompt")
    val systemPrompt: String = "",
    @SerialName("post_history_instructions")
    val postHistoryInstructions: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("creator")
    val creator: String = "",
    @SerialName("character_version")
    val characterVersion: String = "",
    @SerialName("alternate_greetings")
    val alternateGreetings: List<String> = emptyList(),
    
    // Linked Lorebooks
    @SerialName("character_book")
    val characterBook: TavernCharacterBook? = null,
    
    // Extensions (for extra data like RikkaHub specific settings if needed)
    val extensions: Map<String, JsonObject> = emptyMap()
)
