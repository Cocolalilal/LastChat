package me.rerere.lastchat.ios

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

internal val IOS_STANDARD_RP_PATTERNS =
    setOf("*", "**", "~~", "`", "#", "##", "###", "####", "#####", "######", ">")

internal fun normalizeIosColorHex(value: String): String? {
    val raw = value.trim().removePrefix("#")
    if (raw.length != 6 && raw.length != 8) return null
    if (raw.any { it.digitToIntOrNull(16) == null }) return null
    return "#${raw.uppercase()}"
}

internal fun iosColorFromHex(value: String): Color? {
    val normalized = normalizeIosColorHex(value) ?: return null
    val raw = normalized.removePrefix("#")
    val argb = if (raw.length == 6) "FF$raw" else raw
    return argb.toULongOrNull(16)?.let(::Color)
}

internal fun buildIosRoleplayText(
    text: String,
    rules: List<IosRpStyleRule>,
): AnnotatedString {
    val enabledRules = buildMap {
        rules.filter { it.enabled }.forEach { rule ->
            if (!containsKey(rule.pattern)) {
                iosColorFromHex(rule.colorHex)?.let { color -> put(rule.pattern, color) }
            }
        }
    }
    val customRules = enabledRules
        .filterKeys { pattern -> pattern !in IOS_STANDARD_RP_PATTERNS && pattern.isNotEmpty() }
        .entries
        .sortedByDescending { it.key.length }

    return buildAnnotatedString {
        text.split('\n').forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) append('\n')
            val heading = (6 downTo 1).firstNotNullOfOrNull { level ->
                val marker = "#".repeat(level)
                line.removePrefix("$marker ").takeIf { it != line }?.let { marker to it }
            }
            when {
                heading != null -> {
                    val (marker, content) = heading
                    pushStyle(
                        SpanStyle(
                            color = enabledRules[marker] ?: Color.Unspecified,
                            fontWeight = FontWeight.Bold,
                        )
                    )
                    appendRoleplayInline(content, enabledRules, customRules)
                    pop()
                }
                line.startsWith("> ") -> {
                    pushStyle(
                        SpanStyle(
                            color = enabledRules[">"] ?: Color.Unspecified,
                            fontStyle = FontStyle.Italic,
                        )
                    )
                    appendRoleplayInline(line.removePrefix("> "), enabledRules, customRules)
                    pop()
                }
                else -> appendRoleplayInline(line, enabledRules, customRules)
            }
        }
    }
}

private fun AnnotatedString.Builder.appendRoleplayInline(
    text: String,
    standardColors: Map<String, Color>,
    customRules: List<Map.Entry<String, Color>>,
) {
    var index = 0
    while (index < text.length) {
        val match = findNextRoleplayMatch(text, index, standardColors, customRules)
        if (match == null) {
            append(text.substring(index))
            return
        }
        if (match.start > index) append(text.substring(index, match.start))
        pushStyle(match.style.copy(color = match.color ?: Color.Unspecified))
        append(match.content)
        pop()
        index = match.endExclusive
    }
}

private data class IosRoleplayMatch(
    val start: Int,
    val endExclusive: Int,
    val content: String,
    val style: SpanStyle,
    val color: Color?,
)

private fun findNextRoleplayMatch(
    text: String,
    fromIndex: Int,
    standardColors: Map<String, Color>,
    customRules: List<Map.Entry<String, Color>>,
): IosRoleplayMatch? {
    val delimiters = buildList {
        add("**" to SpanStyle(fontWeight = FontWeight.SemiBold))
        add("~~" to SpanStyle(textDecoration = TextDecoration.LineThrough))
        add("`" to SpanStyle(fontFamily = FontFamily.Monospace))
        add("*" to SpanStyle(fontStyle = FontStyle.Italic))
        customRules.forEach { add(it.key to SpanStyle()) }
    }
    var best: IosRoleplayMatch? = null
    for ((delimiter, style) in delimiters) {
        val start = text.indexOf(delimiter, fromIndex)
        if (start < 0) continue
        val contentStart = start + delimiter.length
        val end = text.indexOf(delimiter, contentStart)
        if (end <= contentStart) continue
        val ruleColor = standardColors[delimiter]
            ?: customRules.firstOrNull { it.key == delimiter }?.value
        val candidate = IosRoleplayMatch(
            start = start,
            endExclusive = end + delimiter.length,
            content = text.substring(contentStart, end),
            style = style,
            color = ruleColor,
        )
        if (best == null || candidate.start < best.start) {
            best = candidate
        }
    }
    return best
}
