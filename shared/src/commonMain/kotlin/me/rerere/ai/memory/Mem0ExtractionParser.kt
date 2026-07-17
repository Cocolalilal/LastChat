package me.rerere.ai.memory

import kotlinx.serialization.json.Json

/** Defensive parser for the JSON-only response requested by the Mem0 extraction prompt. */
object Mem0ExtractionParser {
    private val codeFence = Regex("""(?s)^\s*```(?:json)?\s*(.*?)\s*```\s*$""", RegexOption.IGNORE_CASE)

    fun parseOrNull(response: String, json: Json): Mem0ExtractionEnvelope? {
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return null
        val fenced = codeFence.matchEntire(trimmed)?.groupValues?.getOrNull(1)
        return listOfNotNull(
            trimmed,
            fenced,
            extractBalancedObject(trimmed),
            fenced?.let(::extractBalancedObject),
        ).distinct().firstNotNullOfOrNull { candidate ->
            runCatching { json.decodeFromString<Mem0ExtractionEnvelope>(candidate) }.getOrNull()
        }
    }

    internal fun extractBalancedObject(input: String): String? {
        val start = input.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaping = false
        for (index in start until input.length) {
            val char = input[index]
            if (escaping) {
                escaping = false
                continue
            }
            if (inString && char == '\\') {
                escaping = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (char) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return input.substring(start, index + 1)
                    if (depth < 0) return null
                }
            }
        }
        return null
    }
}
