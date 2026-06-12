package me.rerere.rikkahub.data.ai.prompts

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.truncate

internal val DEFAULT_SUGGESTION_PROMPT = """
    You are writing reply suggestions for the human user in LastChat.

    Task:
    Generate 3~5 short messages the **USER** could send next to the **ASSISTANT**.

    Speaker contract:
    - USER = the human app user. Imitate USER's recent tone, language, punctuation, emoji use, and typical length.
    - ASSISTANT = the character/AI. Never speak as ASSISTANT, continue ASSISTANT's monologue, or answer on ASSISTANT's behalf.
    - Your output will be inserted into the user's input box, so every line must be something the USER would personally say.

    Rules:
    1. Reply directly with suggestions, do not add formatting, numbering, bullets, or speaker labels.
    2. Use {locale} language.
    3. Keep each suggestion concise and chat-ready.
    4. Prefer the USER's previous conversational style over generic assistant-like phrasing.
    5. If there are no USER style examples, keep suggestions natural and brief.
    6. Do not include explanations, markdown, quotes, or alternatives for the ASSISTANT.

    <conversation_context>
    {content}
    </conversation_context>
""".trimIndent()

private val LEGACY_DEFAULT_SUGGESTION_PROMPT = """
    I will provide you with some chat content in the `<content>` block, including conversations between the User and the AI assistant.
    You need to act as the **User** to reply to the assistant, generating 3~5 appropriate and contextually relevant responses to the assistant.

    Rules:
    1. Reply directly with suggestions, do not add any formatting, and separate suggestions with newlines, no need to add markdown list formats.
    2. Use {locale} language.
    3. Ensure each suggestion is valid.
    4. Each suggestion should not exceed 10 characters.
    5. Imitate the user's previous conversational style.
    6. Act as a User, not an Assistant!

    <content>
    {content}
    </content>
""".trimIndent()

internal fun normalizeSuggestionPrompt(prompt: String): String {
    return if (prompt == LEGACY_DEFAULT_SUGGESTION_PROMPT) {
        DEFAULT_SUGGESTION_PROMPT
    } else {
        prompt
    }
}

internal fun buildSuggestionPromptContent(
    messages: List<UIMessage>,
    truncateIndex: Int,
): String {
    val visibleMessages = messages
        .truncate(truncateIndex)
        .filter { message -> message.role == MessageRole.USER || message.role == MessageRole.ASSISTANT }
        .mapNotNull { message ->
            val text = message.toPromptText()
            if (text.isBlank()) null else message.role to text
        }
        .takeLast(10)

    if (visibleMessages.isEmpty()) {
        return """
            Speaker legend:
            - USER = the human app user whose next reply you are suggesting.
            - ASSISTANT = the character/AI. Do not write as ASSISTANT.

            No visible transcript is available.
            Next speaker to suggest: USER
        """.trimIndent()
    }

    val userStyleExamples = visibleMessages
        .filter { (role, _) -> role == MessageRole.USER }
        .map { (_, text) -> text }
        .takeLast(5)

    return buildString {
        appendLine("Speaker legend:")
        appendLine("- USER = the human app user whose next reply you are suggesting.")
        appendLine("- ASSISTANT = the character/AI. Do not write as ASSISTANT.")
        appendLine()

        if (userStyleExamples.isNotEmpty()) {
            appendLine("USER style examples, newest last:")
            userStyleExamples.forEach { text ->
                appendLine("USER_STYLE_EXAMPLE: $text")
            }
            appendLine()
        } else {
            appendLine("USER style examples: none yet. Keep suggestions brief and natural.")
            appendLine()
        }

        appendLine("Transcript, oldest to newest:")
        visibleMessages.forEach { (role, text) ->
            appendLine("${role.suggestionLabel()}: $text")
        }
        appendLine()
        appendLine("Next speaker to suggest: USER")
        appendLine("Output only USER replies, one per line.")
    }.trim()
}

internal fun parseSuggestionLines(rawText: String): List<String> {
    return rawText
        .lineSequence()
        .mapNotNull(::sanitizeSuggestionLine)
        .distinct()
        .take(5)
        .toList()
}

private fun sanitizeSuggestionLine(line: String): String? {
    var text = line.trim()
        .replace(Regex("^[-*\\u2022]+\\s+"), "")
        .replace(Regex("^\\d+[.)]\\s*"), "")
        .trim()

    val labelMatch = Regex(
        pattern = "^\\[?\\s*(user|human|me|assistant|ai|character)\\s*\\]?\\s*[:\\-\\uFF1A]\\s*(.*)$",
        option = RegexOption.IGNORE_CASE,
    ).matchEntire(text)
    if (labelMatch != null) {
        val label = labelMatch.groupValues[1].lowercase()
        if (label == "assistant" || label == "ai" || label == "character") {
            return null
        }
        text = labelMatch.groupValues[2].trim()
    }

    text = text
        .trim('"', '\'', '\u201C', '\u201D', '\u2018', '\u2019')
        .trim()

    return text.takeIf { it.isNotBlank() && it != "..." }
}

private fun UIMessage.toPromptText(maxLength: Int = 800): String {
    val text = toText()
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (text.length > maxLength) {
        text.take(maxLength).trimEnd() + "..."
    } else {
        text
    }
}

private fun MessageRole.suggestionLabel(): String {
    return when (this) {
        MessageRole.USER -> "USER"
        MessageRole.ASSISTANT -> "ASSISTANT"
        MessageRole.SYSTEM -> "SYSTEM"
        MessageRole.TOOL -> "TOOL"
    }
}
