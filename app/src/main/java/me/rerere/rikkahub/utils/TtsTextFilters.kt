package me.rerere.rikkahub.utils

import me.rerere.rikkahub.data.datastore.TtsFilterMode
import me.rerere.rikkahub.data.datastore.TtsTextFilterRule

/** Delimiters are literal pairs, never patterns that may swallow unrelated paragraphs. */
internal fun prepareTtsText(text: String, rules: List<TtsTextFilterRule>): String =
    applyTtsTextFilters(text, rules).stripMarkdown().trim()

internal fun applyTtsTextFilters(text: String, rules: List<TtsTextFilterRule>): String {
    val enabled = rules.filter { it.enabled && it.pattern.isNotBlank() }
    if (enabled.isEmpty()) return text
    val onlyRead = enabled.filter { it.mode == TtsFilterMode.ONLY_READ }
    val skip = enabled.filter { it.mode == TtsFilterMode.SKIP }
    fun skipPairs(source: String): String = skip.fold(source) { result, rule ->
        val spans = delimitedTtsSpans(result, rule.pattern)
        buildString {
            var cursor = 0
            spans.forEach { span ->
                append(result, cursor, span.start)
                append(' ')
                cursor = span.end
            }
            append(result, cursor, result.length)
        }
    }
    if (onlyRead.isEmpty()) return skipPairs(text).trim()

    // Merge overlapping selections and retain their order in the message, not rule order.
    val selected = onlyRead.flatMap { rule -> delimitedTtsSpans(text, rule.pattern) }
        .map { it.contentStart until it.contentEnd }.sortedBy { it.first }
    val ranges = mutableListOf<IntRange>()
    selected.forEach { range ->
        val prior = ranges.lastOrNull()
        if (prior != null && range.first <= prior.last) {
            ranges[ranges.lastIndex] = prior.first..maxOf(prior.last, range.last)
        } else ranges += range
    }
    // Do not create a new delimiter pair across two separate ONLY_READ selections.
    return ranges.joinToString(" ") { skipPairs(text.substring(it)).trim() }.trim()
}

private data class TtsDelimitedSpan(val start: Int, val end: Int, val contentStart: Int, val contentEnd: Int)
private val ttsParagraphBreak = Regex("\\r?\\n[ \\t]*\\r?\\n")

private fun delimitedTtsSpans(text: String, delimiter: String): List<TtsDelimitedSpan> {
    val spans = mutableListOf<TtsDelimitedSpan>()
    var paragraphStart = 0
    val ends = ttsParagraphBreak.findAll(text).map { it.range.first to (it.range.last + 1) } +
        sequenceOf(text.length to text.length)
    ends.forEach { (paragraphEnd, nextParagraph) ->
        var cursor = paragraphStart
        var opening = -1
        while (cursor < paragraphEnd) {
            val index = text.indexOf(delimiter, cursor)
            if (index < 0 || index + delimiter.length > paragraphEnd) break
            val end = index + delimiter.length
            cursor = end
            var slash = index - 1
            while (slash >= paragraphStart && text[slash] == '\\') slash--
            if ((index - 1 - slash) % 2 != 0) continue
            val markdown = delimiter.all { it == delimiter.first() } && delimiter.first() in "*_"
            if (markdown) {
                // A '*' rule must never treat either half of '**bold**' as roleplay.
                if (text.getOrNull(index - 1) == delimiter.first() || text.getOrNull(end) == delimiter.first()) continue
                val lineStart = text.lastIndexOf('\n', index - 1) + 1
                if (delimiter == "*" && text.substring(lineStart, index).isBlank() &&
                    text.getOrNull(end)?.isWhitespace() == true) continue
                if (delimiter.first() == '_' && text.getOrNull(index - 1)?.isLetterOrDigit() == true &&
                    text.getOrNull(end)?.isLetterOrDigit() == true) continue
            }
            if (opening < 0) {
                if (!markdown || text.getOrNull(end)?.isWhitespace() == false) opening = index
            } else if (index > opening + delimiter.length &&
                (!markdown || text.getOrNull(index - 1)?.isWhitespace() == false)) {
                spans += TtsDelimitedSpan(opening, end, opening + delimiter.length, index)
                opening = -1
            }
        }
        paragraphStart = nextParagraph
    }
    return spans
}
