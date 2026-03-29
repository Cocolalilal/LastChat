package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Configuration for Android text selection integration.
 * Stores user preferences for the "Ask LastChat" popup.
 */
@Serializable
data class TextSelectionConfig(
    val assistantId: Uuid? = null, // null = use default assistant
    val actions: List<TextSelectionAction> = DEFAULT_TEXT_SELECTION_ACTIONS,
    val translateLanguage: String = "English"
)

/**
 * A configurable quick action button for text selection popup.
 */
@Serializable
data class TextSelectionAction(
    val id: String = Uuid.random().toString(),
    val name: String,
    val icon: String, // Material icon name (e.g., "Translate", "Lightbulb")
    val prompt: String,
    val modelId: Uuid? = null,
    val enabled: Boolean = true,
    val isCustomPrompt: Boolean = false // Special flag for "Ask anything" which shows input field
)

val DEFAULT_TEXT_SELECTION_ACTIONS = listOf(
    TextSelectionAction(
        id = "translate",
        name = "Translate",
        icon = "Translate",
        prompt = """
            You are a translator. Translate the user's text to {{language}}.
            Only output the translation, nothing else. Do not include any explanations or notes.
        """.trimIndent()
    ),
    TextSelectionAction(
        id = "explain",
        name = "Explain",
        icon = "Lightbulb",
        prompt = """
            You are a helpful assistant. Explain the following text in simple, easy-to-understand terms.
            Be concise but thorough. Use examples if helpful.
        """.trimIndent()
    ),
    TextSelectionAction(
        id = "summarize",
        name = "Summarize",
        icon = "Summarize",
        prompt = """
            You are a summarization assistant. Provide a clear, concise summary of the following text.
            Capture the key points and main ideas. Be brief but complete.
        """.trimIndent()
    ),
    TextSelectionAction(
        id = "custom",
        name = "Ask anything…",
        icon = "AutoAwesome",
        prompt = """
            You are a helpful assistant. Answer the user's question about the provided text.
            User's question: {{custom_prompt}}
        """.trimIndent(),
        isCustomPrompt = true
    )
)
