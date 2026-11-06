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
 * Robustly extracts and auto-heals JSON from raw LLM output.
 * 1. Strips thinking tags (<think>...</think>).
 * 2. Unwraps markdown code fences (```json ... ``` or ``` ... ```).
 * 3. Escapes literal unescaped newlines/tabs inside string literals.
 * 4. Stack-based repair for truncated outputs: closes unterminated strings,
 *    strips trailing commas/colons, and cleanly pops and closes open arrays and objects in reverse order.
 */
fun extractJsonPayload(text: String): String {
    if (text.isBlank()) return ""
    // 1. Strip reasoning blocks from models like DeepSeek-R1 / thinking models
    var clean = text.replace(Regex("(?s)<think>.*?(</think>|$)"), "").trim()

    // 2. Check for markdown code fences
    val fenceMatch = Regex("(?s)```(?:json)?\\s*([\\{\\[].*?)(?:```|$)").find(clean)
    if (fenceMatch != null) {
        clean = fenceMatch.groupValues[1].trim()
    }

    // 3. Locate the starting brace or bracket
    val firstBrace = clean.indexOf('{')
    val firstBracket = clean.indexOf('[')
    val start = when {
        firstBrace != -1 && firstBracket != -1 -> minOf(firstBrace, firstBracket)
        firstBrace != -1 -> firstBrace
        firstBracket != -1 -> firstBracket
        else -> return clean
    }
    clean = clean.substring(start).trim()

    // 4. Token scan and container stack tracking
    val sb = StringBuilder(clean.length + 32)
    val stack = ArrayDeque<Char>()
    var inString = false
    var escape = false

    for (i in clean.indices) {
        val ch = clean[i]
        if (ch == '"' && !escape) {
            inString = !inString
            sb.append(ch)
        } else if (inString) {
            when (ch) {
                '\n' -> sb.append("\\n")
                '\r' -> { /* drop */ }
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        } else {
            when (ch) {
                '{' -> {
                    stack.addLast('}')
                    sb.append(ch)
                }
                '[' -> {
                    stack.addLast(']')
                    sb.append(ch)
                }
                '}', ']' -> {
                    if (stack.isNotEmpty() && stack.last() == ch) {
                        stack.removeLast()
                    }
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }

        if (ch == '\\' && !escape) {
            escape = true
        } else {
            escape = false
        }
    }

    // If truncated inside a string, close the string
    if (inString) {
        if (sb.isNotEmpty() && sb.last() == '\\') {
            sb.deleteCharAt(sb.length - 1)
        }
        sb.append('"')
    }

    // Strip trailing commas or dangling colons before closing unclosed containers
    var result = sb.toString().trim()
    while (stack.isNotEmpty()) {
        result = result.trim()
        if (result.endsWith(',')) {
            result = result.substring(0, result.length - 1).trim()
        } else if (result.endsWith(':')) {
            result = "$result null"
        }
        val closer = stack.removeLast()
        result += closer
    }

    return result
}
