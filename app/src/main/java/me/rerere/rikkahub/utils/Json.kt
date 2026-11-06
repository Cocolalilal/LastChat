package me.rerere.rikkahub.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

val JsonInstant by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
}

val JsonInstantPretty by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
}

val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

/**
 * Robustly extracts the primary JSON payload from raw LLM output.
 * Strips reasoning tokens (<think>...</think>), markdown code fences (```json ... ```),
 * and handles conversational preamble/postamble.
 */
fun extractJsonPayload(text: String): String {
    if (text.isBlank()) return ""
    // 1. Strip reasoning blocks from models like DeepSeek-R1 / thinking models
    val stripped = text.replace(Regex("(?s)<think>.*?</think>"), "").trim()

    // 2. Check for markdown code fences (```json { ... } ``` or ``` { ... } ```)
    val fenceMatch = Regex("(?s)```(?:json)?\\s*([\\{\\[].*?[\\}\\]])\\s*```").find(stripped)
    if (fenceMatch != null) {
        val candidate = fenceMatch.groupValues[1].trim()
        if (candidate.isNotBlank()) return candidate
    }

    // 3. Find outer-most braces or brackets
    val firstBrace = stripped.indexOf('{')
    val lastBrace = stripped.lastIndexOf('}')
    if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
        return stripped.substring(firstBrace, lastBrace + 1).trim()
    }

    val firstBracket = stripped.indexOf('[')
    val lastBracket = stripped.lastIndexOf(']')
    if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
        return stripped.substring(firstBracket, lastBracket + 1).trim()
    }

    return stripped
}
