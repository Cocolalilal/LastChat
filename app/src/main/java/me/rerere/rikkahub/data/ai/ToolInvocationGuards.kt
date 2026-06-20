package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage

private val EXPLICIT_WEB_SEARCH_PATTERNS = listOf(
    Regex("""\b(search|browse|google|bing)\b""", RegexOption.IGNORE_CASE),
    Regex("""\blook\s+(it|this|that|them|up)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(on|from)\s+the\s+(web|internet)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(web|internet|online)\s+(search|lookup|source|sources|results?)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(latest|current|up[-\s]?to[-\s]?date|today'?s|news|recent)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(source|sources|citation|citations|cite|verify|fact[-\s]?check)\b""", RegexOption.IGNORE_CASE),
)

private val EXPLICIT_MEMORY_SEARCH_PATTERNS = listOf(
    Regex("""\b(search|look\s+through|check)\s+(your\s+)?(memory|memories|past\s+chats?|old\s+chats?|chat\s+history)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(do\s+you\s+)?(remember|recall)\b""", RegexOption.IGNORE_CASE),
    Regex("""\bwhat\s+did\s+i\s+(tell|say|mention)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(earlier|previously|before|last\s+time|from\s+before|in\s+the\s+past)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(memory|memories|past\s+chats?|old\s+chats?|chat\s+history)\b""", RegexOption.IGNORE_CASE),
)

internal fun latestUserText(messages: List<UIMessage>): String {
    return messages.lastOrNull { it.role == MessageRole.USER }
        ?.toContentText()
        .orEmpty()
}

internal fun hasExplicitWebSearchIntent(messages: List<UIMessage>): Boolean {
    val text = latestUserText(messages)
    return text.isNotBlank() && EXPLICIT_WEB_SEARCH_PATTERNS.any { it.containsMatchIn(text) }
}

internal fun hasExplicitMemorySearchIntent(messages: List<UIMessage>): Boolean {
    val text = latestUserText(messages)
    return text.isNotBlank() && EXPLICIT_MEMORY_SEARCH_PATTERNS.any { it.containsMatchIn(text) }
}
