package me.rerere.lastchat.ios.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
enum class IosInjectionPosition {
    @SerialName("before_system")
    BEFORE_SYSTEM,

    @SerialName("after_system")
    AFTER_SYSTEM,

    @SerialName("after_memory")
    AFTER_MEMORY,

    @SerialName("before_messages")
    BEFORE_MESSAGES,

    @SerialName("at_depth")
    AT_DEPTH;

    fun displayName(): String = when (this) {
        BEFORE_SYSTEM -> "Before system prompt"
        AFTER_SYSTEM -> "After system prompt"
        AFTER_MEMORY -> "After memory"
        BEFORE_MESSAGES -> "Before messages"
        AT_DEPTH -> "At depth"
    }
}

@Serializable
data class IosSkill(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val description: String = "",
    val icon: String? = null,
    val instructions: String = "",
    val enabled: Boolean = true,
    @SerialName("always_enabled")
    val alwaysEnabled: Boolean = true,
    @SerialName("available_for_all_assistants")
    val availableForAllAssistants: Boolean = true,
    @SerialName("available_assistant_ids")
    val availableAssistantIds: Set<String> = emptySet(),
    @SerialName("injection_position")
    val injectionPosition: IosInjectionPosition = IosInjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0,
    @SerialName("created_at")
    val createdAt: Long = 0L,
    @SerialName("updated_at")
    val updatedAt: Long = 0L,
)

@Serializable
enum class IosLorebookActivationType {
    @SerialName("always")
    ALWAYS,

    @SerialName("keywords")
    KEYWORDS,

    @SerialName("rag")
    RAG;

    fun displayName(): String = when (this) {
        ALWAYS -> "Always active"
        KEYWORDS -> "Keyword triggered"
        RAG -> "Semantic similarity"
    }
}

@Serializable
data class IosLorebookEntry(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val prompt: String = "",
    val enabled: Boolean = true,
    @SerialName("injection_position")
    val injectionPosition: IosInjectionPosition = IosInjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0,
    @SerialName("activation_type")
    val activationType: IosLorebookActivationType = IosLorebookActivationType.KEYWORDS,
    val keywords: List<String> = emptyList(),
    @SerialName("case_sensitive")
    val caseSensitive: Boolean = false,
    @SerialName("use_regex")
    val useRegex: Boolean = false,
    @SerialName("scan_depth")
    val scanDepth: Int = 10,
)

@Serializable
data class IosLorebook(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val entries: List<IosLorebookEntry> = emptyList(),
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val author: String = "",
    val version: String = "1.0",
)
