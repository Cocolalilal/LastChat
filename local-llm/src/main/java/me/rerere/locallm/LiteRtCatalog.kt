package me.rerere.locallm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Provides the curated list of downloadable on-device models.
 *
 * Priority: a locally cached remote snapshot → the bundled asset. The remote snapshot is refreshed
 * (best-effort) from Google AI Edge Gallery's `model_allowlist`, which is also the source of update
 * notifications (a bumped commit hash / `updateInfo`).
 */
class LiteRtCatalog(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val cacheFile: File
        get() = File(context.filesDir, "local_llm/catalog_cache.json")

    @Volatile
    private var cached: LocalModelCatalog? = null

    /** Returns the best available catalog without hitting the network. */
    suspend fun catalog(): LocalModelCatalog = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        val fromCache = runCatching {
            cacheFile.takeIf { it.exists() }?.readText()?.let { json.decodeFromString<LocalModelCatalog>(it) }
        }.getOrNull()
        val result = fromCache ?: loadBundled()
        cached = result
        result
    }

    /** Refreshes the catalog from the live allowlist; falls back silently to the current catalog. */
    suspend fun refresh(): LocalModelCatalog = withContext(Dispatchers.IO) {
        val bundled = loadBundled()
        val remote = runCatching { fetchRemoteMerged(bundled) }.getOrNull()
        val result = remote ?: catalog()
        if (remote != null) {
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(json.encodeToString(result))
            }
        }
        cached = result
        result
    }

    /**
     * The recommended model for this device: the most capable curated model whose minimum RAM
     * requirement fits, preferring multimodal + thinking models, then larger models.
     */
    suspend fun recommendedModel(deviceRamGb: Int): LocalModelMetadata? {
        val fitting = catalog().models.filter { it.minDeviceMemoryInGb <= deviceRamGb }
        if (fitting.isEmpty()) return catalog().models.minByOrNull { it.minDeviceMemoryInGb }
        return fitting.maxWithOrNull(
            compareBy(
                { if (it.supportsImage) 1 else 0 },
                { if (it.supportsThinking) 1 else 0 },
                { it.sizeInBytes },
            )
        )
    }

    /** Returns the catalog metadata for [installed] if a newer revision is available. */
    suspend fun updateFor(installed: InstalledLocalModel): LocalModelMetadata? {
        val meta = catalog().models.firstOrNull { it.id == installed.id } ?: return null
        return meta.takeIf { it.commitHash != installed.commitHash }
    }

    fun metadataFor(id: String): LocalModelMetadata? = cached?.models?.firstOrNull { it.id == id }

    private fun loadBundled(): LocalModelCatalog {
        val raw = context.assets.open("litert_catalog.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(raw)
    }

    /**
     * Fetches the Gallery allowlist and merges commit-hash / update-info changes onto the bundled
     * curated set (matched by [LocalModelMetadata.id] == allowlist `name`). Only our curated models are
     * kept; the allowlist's extra fields we don't need are ignored.
     */
    private fun fetchRemoteMerged(bundled: LocalModelCatalog): LocalModelCatalog {
        val request = Request.Builder().url(ALLOWLIST_URL).build()
        val body = http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return bundled
            resp.body?.string() ?: return bundled
        }
        val root = json.parseToJsonElement(body).jsonObject
        val allowlistModels = root["models"]?.jsonArray ?: return bundled
        val byName = allowlistModels.associateBy {
            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }
        val mergedModels = bundled.models.map { meta ->
            val entry = byName[meta.id]?.jsonObject ?: return@map meta
            val base = entry["commitHash"]?.jsonPrimitive?.contentOrNull
            // An updatable file entry supersedes the base commit and carries the update note.
            val updatable = entry["updatableModelFiles"]?.jsonArray
                ?.firstOrNull { it.jsonObject["fileName"]?.jsonPrimitive?.contentOrNull == meta.modelFile }
                ?.jsonObject
            val latestCommit = updatable?.get("commitHash")?.jsonPrimitive?.contentOrNull
                ?: base
                ?: meta.commitHash
            val updateNote = entry["updateInfo"]?.jsonPrimitive?.contentOrNull
            meta.copy(
                commitHash = latestCommit,
                updateInfo = updateNote ?: meta.updateInfo,
                defaultConfig = mergeConfig(meta.defaultConfig, entry),
            )
        }
        return bundled.copy(models = mergedModels)
    }

    private fun mergeConfig(
        base: LocalModelDefaultConfig,
        entry: kotlinx.serialization.json.JsonObject,
    ): LocalModelDefaultConfig {
        val cfg = entry["defaultConfig"]?.jsonObject ?: return base
        return base.copy(
            topK = cfg["topK"]?.jsonPrimitive?.intOrNull ?: base.topK,
            topP = cfg["topP"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: base.topP,
            temperature = cfg["temperature"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: base.temperature,
            maxContextLength = cfg["maxContextLength"]?.jsonPrimitive?.intOrNull ?: base.maxContextLength,
            maxTokens = cfg["maxTokens"]?.jsonPrimitive?.intOrNull ?: base.maxTokens,
        )
    }

    companion object {
        /** Gallery allowlist used for update detection. Kept in sync with the bundled snapshot version. */
        const val ALLOWLIST_URL =
            "https://raw.githubusercontent.com/google-ai-edge/gallery/main/model_allowlists/1_0_15.json"
    }
}
