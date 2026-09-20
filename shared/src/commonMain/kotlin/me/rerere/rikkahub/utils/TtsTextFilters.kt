package me.rerere.rikkahub.utils

import kotlinx.serialization.Serializable

@Serializable
enum class TtsFilterMode {
    SKIP,
    ONLY_READ,
}

@Serializable
data class TtsTextFilterRule(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val pattern: String = "*",
    val mode: TtsFilterMode = TtsFilterMode.SKIP,
    val enabled: Boolean = true,
)

/**
 * Apply TTS text filter rules.
 * - ONLY_READ rules run first and extract `$pattern(.+?)$pattern` captures
 * - SKIP rules then remove `$pattern.+?$pattern`
 */
fun applyTtsTextFilters(
    text: String,
    rules: List<TtsTextFilterRule>,
): String {
    val enabled = rules.filter { it.enabled && it.pattern.isNotEmpty() }
    if (enabled.isEmpty()) return text

    var result = text
    val onlyReadRules = enabled.filter { it.mode == TtsFilterMode.ONLY_READ }
    if (onlyReadRules.isNotEmpty()) {
        val extracted = StringBuilder()
        for (rule in onlyReadRules) {
            val regex = Regex("${Regex.escape(rule.pattern)}(.+?)${Regex.escape(rule.pattern)}")
            regex.findAll(result).forEach { match ->
                if (extracted.isNotEmpty()) extracted.append(" ")
                extracted.append(match.groupValues.getOrNull(1).orEmpty())
            }
        }
        result = extracted.toString()
    }

    enabled.filter { it.mode == TtsFilterMode.SKIP }.forEach { rule ->
        val regex = Regex("${Regex.escape(rule.pattern)}.+?${Regex.escape(rule.pattern)}")
        result = result.replace(regex, "")
    }
    return result.trim()
}

fun prepareTtsPlaybackText(
    text: String,
    rules: List<TtsTextFilterRule>,
): String = applyTtsTextFilters(text, rules).stripMarkdown().trim()

private val REGEX_CODE_BLOCK = Regex("```[\\s\\S]*?```|`[^`]*?`")
private val REGEX_IMAGE_LINK = Regex("!?\\[([^\\]]+)\\]\\([^\\)]*\\)")
private val REGEX_BOLD = Regex("\\*\\*([^*]+?)\\*\\*")
private val REGEX_ITALIC = Regex("\\*([^*]+?)\\*")
private val REGEX_UNDERLINE_DOUBLE = Regex("__([^_]+?)__")
private val REGEX_UNDERLINE_SINGLE = Regex("_([^_]+?)_")
private val REGEX_STRIKETHROUGH = Regex("~~([^~]+?)~~")
private val REGEX_HIGHLIGHT = Regex("==([^=]+?)==|<mark>(.*?)</mark>")
private val REGEX_UNDERLINE_PLUS = Regex("\\+\\+([^+]+?)\\+\\+")
private val REGEX_HTML_FORMATTING_TAG =
    Regex("(?i)</?(?:u|ins|mark|b|strong|i|em|s|del|strike|sub|sup|small|code|span|font)(?:\\s+[^>]*)?>")
private val REGEX_HEADING = Regex("(?m)^#+\\s*")
private val REGEX_LIST_BULLET = Regex("(?m)^\\s*[-*+]\\s+")
private val REGEX_LIST_NUMBERED = Regex("(?m)^\\s*\\d+\\.\\s+")
private val REGEX_BLOCKQUOTE = Regex("(?m)^>\\s*")
private val REGEX_HORIZONTAL_RULE = Regex("(?m)^(\\s*[-*_]){3,}\\s*$")
private val REGEX_MULTIPLE_NEWLINES = Regex("\n{3,}")

fun String.stripMarkdown(): String {
    return this
        .replace(REGEX_CODE_BLOCK, "")
        .replace(REGEX_IMAGE_LINK, "$1")
        .replace(REGEX_BOLD, "$1")
        .replace(REGEX_ITALIC, "$1")
        .replace(REGEX_UNDERLINE_DOUBLE, "$1")
        .replace(REGEX_UNDERLINE_SINGLE, "$1")
        .replace(REGEX_STRIKETHROUGH, "$1")
        .replace(REGEX_HIGHLIGHT) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        .replace(REGEX_UNDERLINE_PLUS, "$1")
        .replace(REGEX_HTML_FORMATTING_TAG, "")
        .replace(REGEX_HEADING, "")
        .replace(REGEX_LIST_BULLET, "")
        .replace(REGEX_LIST_NUMBERED, "")
        .replace(REGEX_BLOCKQUOTE, "")
        .replace(REGEX_HORIZONTAL_RULE, "")
        .replace(REGEX_MULTIPLE_NEWLINES, "\n\n")
        .trim()
}
