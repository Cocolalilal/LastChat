package me.rerere.rikkahub.data.ai.models

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Modality
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import me.rerere.common.http.await

private const val TAG = "ModelCatalogService"
private const val MODEL_CATALOG_DIR_NAME = "model_catalog"
private const val MODEL_CATALOG_FILE_NAME = "model_prices_and_context_window.json"
private const val MODEL_CATALOG_URL =
    "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json"

enum class ModelCatalogSource {
    BUNDLED,
    DOWNLOADED,
}

data class ModelCatalogEntry(
    val key: String,
    val canonicalModelId: String,
    val litellmProvider: String? = null,
    val mode: String? = null,
    val supportedModalities: List<Modality> = emptyList(),
    val supportsVision: Boolean = false,
    val supportsFunctionCalling: Boolean = false,
    val supportsReasoning: Boolean = false,
    val inputCostPerToken: Double? = null,
    val outputCostPerToken: Double? = null,
)

data class ModelCatalogSnapshot(
    val exactEntries: Map<String, ModelCatalogEntry>,
    val canonicalEntries: Map<String, List<ModelCatalogEntry>>,
)

data class ModelCatalogStatus(
    val source: ModelCatalogSource = ModelCatalogSource.BUNDLED,
    val entryCount: Int = 0,
    val lastSuccessfulRefreshAt: Long? = null,
    val isRefreshing: Boolean = false,
)

private data class LoadedCatalog(
    val snapshot: ModelCatalogSnapshot,
    val source: ModelCatalogSource,
    val lastSuccessfulRefreshAt: Long?,
)

object ModelCatalogParser {
    fun parse(rawJson: String): ModelCatalogSnapshot {
        val root = JsonInstant.parseToJsonElement(rawJson).jsonObject
        val exactEntries = linkedMapOf<String, ModelCatalogEntry>()
        val canonicalEntries = linkedMapOf<String, MutableList<ModelCatalogEntry>>()

        root.forEach { (key, value) ->
            if (key == "sample_spec") return@forEach
            val obj = value.jsonObject
            val canonicalHint = obj["canonical_model_id"]?.jsonPrimitive?.contentOrNull
            val supportedModalities = obj["supported_modalities"]
                .asStringList()
                .mapNotNull(::toModalityOrNull)
                .distinct()
            val entry = ModelCatalogEntry(
                key = key,
                canonicalModelId = ModelIdNormalizer.canonicalize(
                    modelId = key,
                    canonicalHint = canonicalHint,
                ),
                litellmProvider = obj["litellm_provider"]?.jsonPrimitive?.contentOrNull,
                mode = obj["mode"]?.jsonPrimitive?.contentOrNull,
                supportedModalities = supportedModalities,
                supportsVision = obj["supports_vision"]?.jsonPrimitive?.booleanOrNull == true ||
                    supportedModalities.contains(Modality.IMAGE),
                supportsFunctionCalling = obj["supports_function_calling"]?.jsonPrimitive?.booleanOrNull == true,
                supportsReasoning = obj["supports_reasoning"]?.jsonPrimitive?.booleanOrNull == true,
                inputCostPerToken = obj["input_cost_per_token"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                outputCostPerToken = obj["output_cost_per_token"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
            )

            exactEntries.putIfAbsent(key.lowercase(), entry)
            canonicalEntries.getOrPut(entry.canonicalModelId) { mutableListOf() }.add(entry)
        }

        return ModelCatalogSnapshot(
            exactEntries = exactEntries,
            canonicalEntries = canonicalEntries.mapValues { (_, entries) -> entries.toList() },
        )
    }
}

class ModelCatalogService(
    private val context: Context,
    private val client: OkHttpClient,
) {
    @Volatile
    private var snapshot: ModelCatalogSnapshot? = null
    private val loadMutex = Mutex()
    private val _status = MutableStateFlow(ModelCatalogStatus())

    val status: StateFlow<ModelCatalogStatus> = _status.asStateFlow()

    fun snapshotOrNull(): ModelCatalogSnapshot? = snapshot

    suspend fun warmUp() {
        loadCatalogIfNeeded(forceReload = false)
    }

    suspend fun refreshCatalog(): ModelCatalogStatus {
        _status.value = _status.value.copy(isRefreshing = true)
        return try {
            val rawJson = downloadCatalogJson()
            ModelCatalogParser.parse(rawJson)
            writeDownloadedCatalog(rawJson)
            loadCatalogIfNeeded(forceReload = true)
        } finally {
            _status.value = _status.value.copy(isRefreshing = false)
        }
    }

    private suspend fun loadCatalogIfNeeded(forceReload: Boolean): ModelCatalogStatus {
        if (!forceReload) {
            snapshot?.let { existing ->
                if (_status.value.entryCount == existing.exactEntries.size) {
                    return _status.value
                }
            }
        }

        return loadMutex.withLock {
            if (!forceReload) {
                snapshot?.let { existing ->
                    if (_status.value.entryCount == existing.exactEntries.size) {
                        return@withLock _status.value
                    }
                }
            }

            val loadedCatalog = readActiveCatalog()
            snapshot = loadedCatalog.snapshot
            val nextStatus = ModelCatalogStatus(
                source = loadedCatalog.source,
                entryCount = loadedCatalog.snapshot.exactEntries.size,
                lastSuccessfulRefreshAt = loadedCatalog.lastSuccessfulRefreshAt,
                isRefreshing = _status.value.isRefreshing,
            )
            _status.value = nextStatus
            nextStatus
        }
    }

    private suspend fun readActiveCatalog(): LoadedCatalog {
        readDownloadedCatalogOrNull()?.let { return it }
        return readBundledCatalog()
    }

    private suspend fun readDownloadedCatalogOrNull(): LoadedCatalog? = withContext(Dispatchers.IO) {
        val file = downloadedCatalogFile()
        if (!file.exists()) return@withContext null

        runCatching {
            val snapshot = ModelCatalogParser.parse(file.readText())
            LoadedCatalog(
                snapshot = snapshot,
                source = ModelCatalogSource.DOWNLOADED,
                lastSuccessfulRefreshAt = file.lastModified().takeIf { it > 0L },
            )
        }.onFailure {
            Log.w(TAG, "Downloaded model catalog is invalid; falling back to bundled snapshot", it)
        }.getOrNull()
    }

    private suspend fun readBundledCatalog(): LoadedCatalog {
        val rawJson = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.model_prices_and_context_window)
                .bufferedReader()
                .use { it.readText() }
        }
        return LoadedCatalog(
            snapshot = ModelCatalogParser.parse(rawJson),
            source = ModelCatalogSource.BUNDLED,
            lastSuccessfulRefreshAt = null,
        )
    }

    private suspend fun downloadCatalogJson(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(MODEL_CATALOG_URL)
            .get()
            .build()
        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            throw IOException("Failed to download model catalog: ${response.code}")
        }
        response.body?.string()?.takeIf { it.isNotBlank() }
            ?: throw IOException("Downloaded model catalog was empty")
    }

    private suspend fun writeDownloadedCatalog(rawJson: String) = withContext(Dispatchers.IO) {
        val directory = downloadedCatalogDirectory()
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val target = downloadedCatalogFile()
        val temp = File(directory, "$MODEL_CATALOG_FILE_NAME.tmp")
        temp.writeText(rawJson)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun downloadedCatalogDirectory(): File {
        return File(context.filesDir, MODEL_CATALOG_DIR_NAME)
    }

    private fun downloadedCatalogFile(): File {
        return File(downloadedCatalogDirectory(), MODEL_CATALOG_FILE_NAME)
    }
}

private fun toModalityOrNull(raw: String): Modality? {
    return when (raw.lowercase()) {
        "text" -> Modality.TEXT
        "image" -> Modality.IMAGE
        else -> null
    }
}

private fun kotlinx.serialization.json.JsonElement?.asStringList(): List<String> {
    val array = this as? JsonArray ?: return emptyList()
    return array.mapNotNull { it.jsonPrimitive.contentOrNull }
}
