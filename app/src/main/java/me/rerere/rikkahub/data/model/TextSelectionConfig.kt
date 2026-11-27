package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Configuration for Android text selection integration ("Ask LastChat").
 */
@Serializable
data class TextSelectionConfig(
    val assistantId: Uuid? = null, // null = follow active chat assistant
    val actions: List<TextSelectionAction> = DEFAULT_TEXT_SELECTION_ACTIONS,
    val translateLanguage: String = "English",
    // Lightweight isolation defaults for fast, low-token Quick Ask
    val enableMemoryRead: Boolean = false,
    val enableMemoryWrite: Boolean = false,
    val enableTools: Boolean = false,
    val enableExtraPromptInjections: Boolean = false,
    val showThinkingByDefault: Boolean = false,
    val inheritAssistantSystemPrompt: Boolean = false,
)

/**
 * A configurable quick action preset for the Ask LastChat floating popup.
 */
@Serializable
data class TextSelectionAction(
    val id: String = Uuid.random().toString(),
    val name: String,
    val icon: String,
    val prompt: String,
    val modelId: Uuid? = null,
    val enabled: Boolean = true,
    val isCustomPrompt: Boolean = false,
    val allowContinueInChat: Boolean = true,
    // Optional per-preset overrides (null = inherit from TextSelectionConfig)
    val enableMemoryReadOverride: Boolean? = null,
    val enableToolsOverride: Boolean? = null,
    val saveToStudio: Boolean = false,
    val autoSummarizeOnSave: Boolean = false,
)

val DEFAULT_TEXT_SELECTION_ACTIONS = listOf(
    TextSelectionAction(
        id = "translate",
        name = "Translate",
        icon = "Translate",
        prompt = """
            You are a translator. Translate the user's text to {{language}}.
            Only output the translation, nothing else. Do not include any explanations or notes.
        """.trimIndent(),
        allowContinueInChat = true,
    ),
    TextSelectionAction(
        id = "explain",
        name = "Explain",
        icon = "Lightbulb",
        prompt = """
            You are a helpful assistant. Explain the following text in simple, easy-to-understand terms.
            Be concise but thorough. Use examples if helpful.
        """.trimIndent(),
    ),
    TextSelectionAction(
        id = "summarize",
        name = "Summarize",
        icon = "Summarize",
        prompt = """
            You are a summarization assistant. Provide a clear, concise summary of the following text.
            Capture the key points and main ideas. Be brief but complete.
        """.trimIndent(),
    ),
    TextSelectionAction(
        id = "custom",
        name = "Ask anything…",
        icon = "AutoAwesome",
        prompt = """
            You are a helpful assistant. Answer the user's question about the provided text.
            User's question: {{custom_prompt}}
        """.trimIndent(),
        isCustomPrompt = true,
    ),
    TextSelectionAction(
        id = "save_to_studio",
        name = "Save to Studio",
        icon = "BookmarkAdd",
        prompt = "Save the selected text or shared image directly to Studio Hub.",
        isCustomPrompt = true,
        allowContinueInChat = false,
        saveToStudio = true,
    ),
)
