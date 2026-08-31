package me.rerere.ai.provider

import kotlinx.serialization.json.JsonObject

private val CODEX_UNSUPPORTED_RESPONSE_PARAMETERS = setOf(
    "max_output_tokens",
    "temperature",
    "top_p",
)

/**
 * Keep normal OpenAI Responses options from leaking into ChatGPT's narrower Codex endpoint.
 * Apply this after custom-body merging as the final transport boundary.
 */
fun sanitizeCodexResponseRequest(request: JsonObject): JsonObject = JsonObject(
    request.filterKeys { it !in CODEX_UNSUPPORTED_RESPONSE_PARAMETERS }
)
