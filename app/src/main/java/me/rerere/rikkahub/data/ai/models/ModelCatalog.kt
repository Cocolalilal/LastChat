package me.rerere.rikkahub.data.ai.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.JsonInstant

data class ModelCatalogEntry(
    val key: String,
    val canonicalModelId: String,
    val litellmProvider: String? = null,
    val mode: String? = null,
    val supportsVision: Boolean = false,
    val supportsFunctionCalling: Boolean = false,
    val supportsReasoning: Boolean = false,
    val inputCostPerToken: Double? = null,
    val outputCostPerToken: Double? = null,
)

data class ModelCatalogSnapshot(
    val exactEntries: Map<String, ModelCatalogEntry>,
    val canonicalEntries: Map<String, ModelCatalogEntry>,
)

object ModelCatalogParser {
    fun parse(rawJson: String): ModelCatalogSnapshot {
        val root = JsonInstant.parseToJsonElement(rawJson).jsonObject
        val exactEntries = linkedMapOf<String, ModelCatalogEntry>()
        val canonicalEntries = linkedMapOf<String, ModelCatalogEntry>()

        root.forEach { (key, value) ->
            if (key == "sample_spec") return@forEach
            val obj = value.jsonObject
            val entry = ModelCatalogEntry(
                key = key,
                canonicalModelId = ModelIdNormalizer.canonicalize(key),
                litellmProvider = obj["litellm_provider"]?.jsonPrimitive?.contentOrNull,
                mode = obj["mode"]?.jsonPrimitive?.contentOrNull,
                supportsVision = obj["supports_vision"]?.jsonPrimitive?.booleanOrNull == true,
                supportsFunctionCalling = obj["supports_function_calling"]?.jsonPrimitive?.booleanOrNull == true,
                supportsReasoning = obj["supports_reasoning"]?.jsonPrimitive?.booleanOrNull == true,
                inputCostPerToken = obj["input_cost_per_token"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                outputCostPerToken = obj["output_cost_per_token"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
            )

            exactEntries.putIfAbsent(key.lowercase(), entry)
            canonicalEntries.putIfAbsent(entry.canonicalModelId, entry)
        }

        return ModelCatalogSnapshot(
            exactEntries = exactEntries,
            canonicalEntries = canonicalEntries,
        )
    }
}

class ModelCatalogService(
    private val context: Context,
) {
    @Volatile
    private var snapshot: ModelCatalogSnapshot? = null
    private val loadMutex = Mutex()

    fun snapshotOrNull(): ModelCatalogSnapshot? = snapshot

    suspend fun warmUp() {
        if (snapshot != null) return

        loadMutex.withLock {
            if (snapshot != null) return

            val rawJson = withContext(Dispatchers.IO) {
                context.resources.openRawResource(R.raw.model_prices_and_context_window)
                    .bufferedReader()
                    .use { it.readText() }
            }
            snapshot = ModelCatalogParser.parse(rawJson)
        }
    }
}
