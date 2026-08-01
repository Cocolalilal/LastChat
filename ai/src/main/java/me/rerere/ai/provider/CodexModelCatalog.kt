package me.rerere.ai.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Parses the ChatGPT Codex model manifest. This endpoint exposes generative Codex models only. */
fun parseCodexModelCatalog(root: JsonObject): List<Model> {
    val models = root["models"] as? JsonArray ?: return emptyList()
    return models.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        if (item.stringValue("visibility") != "list") return@mapNotNull null

        val slug = item.stringValue("slug") ?: return@mapNotNull null
        val inputModalities = (item["input_modalities"] as? JsonArray)
            ?.mapNotNull { modality ->
                when (runCatching { modality.jsonPrimitive.contentOrNull }.getOrNull()) {
                    "text" -> Modality.TEXT
                    "image" -> Modality.IMAGE
                    "audio" -> Modality.AUDIO
                    else -> null
                }
            }
            ?.distinct()
            ?.ifEmpty { listOf(Modality.TEXT) }
            ?: listOf(Modality.TEXT, Modality.IMAGE)
        val contextWindow = item.positiveInt("context_window")
            ?: item.positiveInt("max_context_window")
        val supportsReasoning = (item["supported_reasoning_levels"] as? JsonArray)
            ?.isNotEmpty() == true ||
            runCatching {
                item["supports_reasoning_summaries"]?.jsonPrimitive?.booleanOrNull
            }.getOrNull() == true

        Model(
            modelId = slug,
            displayName = item.stringValue("display_name") ?: slug,
            type = ModelType.CHAT,
            inputModalities = inputModalities,
            abilities = buildList {
                add(ModelAbility.TOOL)
                if (supportsReasoning) add(ModelAbility.REASONING)
            },
            contextWindowTokens = contextWindow,
            contextLimitSource = ContextLimitSource.PROVIDER.takeIf { contextWindow != null },
        )
    }
}

private fun JsonObject.stringValue(key: String): String? = runCatching {
    get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}.getOrNull()

private fun JsonObject.positiveInt(key: String): Int? = runCatching {
    get(key)?.jsonPrimitive?.longOrNull
        ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
        ?.toInt()
}.getOrNull()
