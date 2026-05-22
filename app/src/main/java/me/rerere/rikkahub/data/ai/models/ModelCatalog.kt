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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.ImageGenerationMethod
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ReasoningRequestBehavior
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import me.rerere.common.http.await

private const val TAG = "ModelCatalogService"
private const val MODEL_CATALOG_DIR_NAME = "model_catalog"
private const val MODEL_CATALOG_FILE_NAME = "lastchat_catalog.json"
private const val MODEL_CATALOG_ASSET_NAME = "lastchat_catalog.json"
private const val MODEL_CATALOG_URL =
    "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/lastchat_catalog.json"
private const val CATALOG_RAW_BASE_URL =
    "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/"

enum class ModelCatalogSource {
    BUNDLED,
    DOWNLOADED,
}

@Serializable
data class LastChatCatalog(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val providers: List<CatalogProvider> = emptyList(),
    val models: List<CatalogModel> = emptyList(),
    @SerialName("model_families")
    val modelFamilies: List<CatalogModelFamily> = emptyList(),
    @SerialName("model_groups")
    val legacyModelGroups: List<CatalogModelFamily> = emptyList(),
) {
    val effectiveModelFamilies: List<CatalogModelFamily>
        get() = modelFamilies.ifEmpty { legacyModelGroups }
}

@Serializable
data class CatalogProvider(
    val id: String,
    val name: String,
    val description: String = "",
    val type: CatalogProviderType = CatalogProviderType.OPENAI,
    @SerialName("base_url")
    val baseUrl: String,
    @SerialName("chat_completions_path")
    val chatCompletionsPath: String = "/chat/completions",
    @SerialName("use_response_api")
    val useResponseApi: Boolean = false,
    @SerialName("balance_option")
    val balanceOption: BalanceOption = BalanceOption(),
    val icon: String? = null,
    val enabled: Boolean = true,
    @SerialName("built_in")
    val builtIn: Boolean = false,
    val preset: Boolean = true,
    @SerialName("signup_url")
    val signupUrl: String? = null,
    @SerialName("api_key_url")
    val apiKeyUrl: String? = null,
    @SerialName("setup_recommended")
    val setupRecommended: Boolean = false,
    @SerialName("setup_order")
    val setupOrder: Int = 100,
    @SerialName("setup_description")
    val setupDescription: String? = null,
    @SerialName("setup_models")
    val setupModels: List<String> = emptyList(),
    @SerialName("setup_defaults")
    val setupDefaults: CatalogSetupDefaults? = null,
    @SerialName("setup_search_service")
    val setupSearchService: String? = null,
    @SerialName("reasoning_behavior")
    val reasoningBehavior: CatalogRequestBehavior? = null,
    @SerialName("stream_options_mode")
    val streamOptionsMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
    @SerialName("image_response_modalities_mode")
    val imageResponseModalitiesMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
    @SerialName("reasoning_content_replay_mode")
    val reasoningContentReplayMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
)

@Serializable
data class CatalogSetupDefaults(
    val chat: String? = null,
    val title: String? = null,
    val summarizer: String? = null,
    val ocr: String? = null,
)

@Serializable
enum class CatalogProviderType {
    @SerialName("openai")
    OPENAI,

    @SerialName("google")
    GOOGLE,

    @SerialName("claude")
    CLAUDE,
}

@Serializable
data class CatalogModel(
    val id: String,
    @SerialName("display_name")
    val displayName: String = "",
    @SerialName("canonical_model_id")
    val canonicalModelId: String? = null,
    @SerialName("api_aliases")
    val apiAliases: List<String> = emptyList(),
    @SerialName("provider_ids")
    val providerIds: List<String> = emptyList(),
    val type: ModelType = ModelType.CHAT,
    @SerialName("image_generation_method")
    val imageGenerationMethod: ImageGenerationMethod? = null,
    @SerialName("input_modalities")
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    @SerialName("output_modalities")
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    @SerialName("context_window")
    val contextWindow: Int? = null,
    @SerialName("input_cost_per_token")
    val inputCostPerToken: Double? = null,
    @SerialName("output_cost_per_token")
    val outputCostPerToken: Double? = null,
    @SerialName("family_id")
    val familyId: String? = null,
    @SerialName("group_id")
    val legacyGroupId: String? = null,
    @SerialName("provider_slug")
    val providerSlug: String? = null,
    @SerialName("reasoning_behavior")
    val reasoningBehavior: CatalogRequestBehavior? = null,
) {
    val effectiveFamilyId: String?
        get() = familyId ?: legacyGroupId
}

@Serializable
data class CatalogModelFamily(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val aliases: List<String> = emptyList(),
    @SerialName("match_patterns")
    val matchPatterns: List<String> = emptyList(),
    val icon: String? = null,
    val type: ModelType = ModelType.CHAT,
    @SerialName("image_generation_method")
    val imageGenerationMethod: ImageGenerationMethod? = null,
    @SerialName("input_modalities")
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    @SerialName("output_modalities")
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    @SerialName("provider_slug")
    val providerSlug: String? = null,
    @SerialName("reasoning_behavior")
    val reasoningBehavior: CatalogRequestBehavior? = null,
    val versions: List<CatalogModelVersion> = emptyList(),
)

@Serializable
data class CatalogModelVersion(
    val id: String = "",
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("match_patterns")
    val matchPatterns: List<String> = emptyList(),
    @SerialName("exclude_patterns")
    val excludePatterns: List<String> = emptyList(),
    val type: ModelType? = null,
    @SerialName("image_generation_method")
    val imageGenerationMethod: ImageGenerationMethod? = null,
    @SerialName("input_modalities")
    val inputModalities: List<Modality>? = null,
    @SerialName("output_modalities")
    val outputModalities: List<Modality>? = null,
    val abilities: List<ModelAbility>? = null,
    @SerialName("provider_slug")
    val providerSlug: String? = null,
    @SerialName("canonical_model_id")
    val canonicalModelId: String? = null,
    @SerialName("reasoning_behavior")
    val reasoningBehavior: CatalogRequestBehavior? = null,
)

@Serializable
data class CatalogRequestBehavior(
    val off: List<CatalogCustomBody> = emptyList(),
    val auto: List<CatalogCustomBody> = emptyList(),
    val low: List<CatalogCustomBody> = emptyList(),
    val medium: List<CatalogCustomBody> = emptyList(),
    val high: List<CatalogCustomBody> = emptyList(),
) {
    fun toReasoningRequestBehavior(): ReasoningRequestBehavior {
        return ReasoningRequestBehavior(
            off = off.toCustomBodies(),
            auto = auto.toCustomBodies(),
            low = low.toCustomBodies(),
            medium = medium.toCustomBodies(),
            high = high.toCustomBodies(),
        )
    }
}

@Serializable
data class CatalogCustomBody(
    val key: String,
    val value: JsonElement,
)

data class ModelCatalogEntry(
    val key: String,
    val canonicalModelId: String,
    val apiAliases: List<String> = emptyList(),
    val providerIds: List<String> = emptyList(),
    val displayName: String? = null,
    val modelFamilyId: String? = null,
    val mode: String? = null,
    val supportedModalities: List<Modality> = emptyList(),
    val inputModalities: List<Modality> = emptyList(),
    val outputModalities: List<Modality> = emptyList(),
    val supportsVision: Boolean = false,
    val supportsFunctionCalling: Boolean = false,
    val supportsReasoning: Boolean = false,
    val imageGenerationMethod: ImageGenerationMethod? = null,
    val inputCostPerToken: Double? = null,
    val outputCostPerToken: Double? = null,
    val iconUrl: String? = null,
    val providerSlug: String? = null,
    val reasoningBehavior: ReasoningRequestBehavior? = null,
)

data class ModelCatalogSnapshot(
    val exactEntries: Map<String, ModelCatalogEntry>,
    val canonicalEntries: Map<String, List<ModelCatalogEntry>>,
    val providers: List<CatalogProvider> = emptyList(),
    val modelFamilies: List<CatalogModelFamily> = emptyList(),
) {
    val catalog: LastChatCatalog
        get() = LastChatCatalog(
            providers = providers,
            modelFamilies = modelFamilies,
        )
}

data class ModelCatalogStatus(
    val source: ModelCatalogSource = ModelCatalogSource.BUNDLED,
    val entryCount: Int = 0,
    val providerCount: Int = 0,
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
        val catalog = JsonInstant.decodeFromString<LastChatCatalog>(rawJson)
        val exactEntries = linkedMapOf<String, ModelCatalogEntry>()
        val canonicalEntries = linkedMapOf<String, MutableList<ModelCatalogEntry>>()
        val modelFamilies = catalog.effectiveModelFamilies
        val familiesById = modelFamilies.associateBy { it.id }

        catalog.models.forEach { model ->
            val familyId = model.effectiveFamilyId
            val family = familyId?.let(familiesById::get)
            val canonicalModelId = ModelIdNormalizer.canonicalize(
                modelId = model.id,
                canonicalHint = model.canonicalModelId,
            )
            val inputModalities = model.inputModalities.ifEmpty { listOf(Modality.TEXT) }
            val outputModalities = model.outputModalities.ifEmpty {
                defaultOutputModalities(model.type)
            }
            val entry = ModelCatalogEntry(
                key = model.id,
                canonicalModelId = canonicalModelId,
                apiAliases = model.apiAliases,
                providerIds = model.providerIds,
                displayName = model.displayName.ifBlank { null },
                modelFamilyId = familyId,
                mode = model.type.name.lowercase(),
                supportedModalities = (inputModalities + outputModalities).distinct(),
                inputModalities = inputModalities,
                outputModalities = outputModalities,
                supportsVision = inputModalities.contains(Modality.IMAGE),
                supportsFunctionCalling = model.abilities.contains(ModelAbility.TOOL),
                supportsReasoning = model.abilities.contains(ModelAbility.REASONING),
                imageGenerationMethod = model.imageGenerationMethod,
                inputCostPerToken = model.inputCostPerToken,
                outputCostPerToken = model.outputCostPerToken,
                iconUrl = family?.icon?.toCatalogIconUrl(),
                providerSlug = model.providerSlug,
                reasoningBehavior = model.reasoningBehavior?.toReasoningRequestBehavior(),
            )

            buildList {
                add(model.id)
                model.canonicalModelId?.takeIf { it.isNotBlank() }?.let(::add)
                addAll(model.apiAliases)
            }.map { candidate -> candidate.lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { key ->
                    exactEntries.putIfAbsent(key, entry)
                }
            canonicalEntries.getOrPut(entry.canonicalModelId) { mutableListOf() }.add(entry)
        }

        canonicalEntries
            .filterValues { entries -> entries.size > 1 }
            .keys
            .forEach { ambiguousCanonicalId ->
                exactEntries.remove(ambiguousCanonicalId.lowercase())
            }

        return ModelCatalogSnapshot(
            exactEntries = exactEntries,
            canonicalEntries = canonicalEntries.mapValues { (_, entries) -> entries.toList() },
            providers = catalog.providers,
            modelFamilies = modelFamilies,
        )
    }
}

fun ModelCatalogSnapshot.inferFamilyEntry(
    modelId: String,
    canonicalHint: String? = null,
): ModelCatalogEntry? {
    if (modelId.isBlank()) return null

    val canonicalModelId = ModelIdNormalizer.canonicalize(
        modelId = modelId,
        canonicalHint = canonicalHint,
    )
    val candidates = buildList {
        add(modelId)
        canonicalHint?.takeIf { it.isNotBlank() }?.let(::add)
        add(canonicalModelId)
    }.distinct()

    val family = modelFamilies.firstOrNull { it.matchesAny(candidates) } ?: return null
    val matchedVersions = family.versions.filter { it.matchesAny(candidates) }
    var type = family.type
    var imageGenerationMethod = family.imageGenerationMethod
    var inputModalities = family.inputModalities.ifEmpty { listOf(Modality.TEXT) }
    var outputModalities = family.outputModalities.ifEmpty { defaultOutputModalities(type) }
    var abilities = family.abilities
    var providerSlug = family.providerSlug
    var reasoningBehavior = family.reasoningBehavior
    var displayName: String? = null
    var inferredCanonicalId = canonicalModelId

    matchedVersions.forEach { version ->
        version.type?.let { nextType ->
            type = nextType
            outputModalities = defaultOutputModalities(nextType)
            if (nextType == ModelType.EMBEDDING) {
                inputModalities = listOf(Modality.TEXT)
            }
        }
        imageGenerationMethod = version.imageGenerationMethod ?: imageGenerationMethod
        inputModalities = version.inputModalities ?: inputModalities
        outputModalities = version.outputModalities ?: outputModalities
        abilities = version.abilities ?: abilities
        providerSlug = version.providerSlug ?: providerSlug
        reasoningBehavior = version.reasoningBehavior ?: reasoningBehavior
        displayName = version.displayName ?: displayName
        inferredCanonicalId = version.canonicalModelId
            ?.takeIf { it.isNotBlank() }
            ?.let { ModelIdNormalizer.canonicalize(modelId, it) }
            ?: inferredCanonicalId
    }

    inputModalities = inputModalities.ifEmpty { listOf(Modality.TEXT) }
    outputModalities = outputModalities.ifEmpty { defaultOutputModalities(type) }

    return ModelCatalogEntry(
        key = modelId,
        canonicalModelId = inferredCanonicalId,
        displayName = displayName,
        modelFamilyId = family.id,
        mode = type.name.lowercase(),
        supportedModalities = (inputModalities + outputModalities).distinct(),
        inputModalities = inputModalities,
        outputModalities = outputModalities,
        supportsVision = inputModalities.contains(Modality.IMAGE),
        supportsFunctionCalling = abilities.contains(ModelAbility.TOOL),
        supportsReasoning = abilities.contains(ModelAbility.REASONING),
        imageGenerationMethod = imageGenerationMethod,
        iconUrl = family.icon?.toCatalogIconUrl(),
        providerSlug = providerSlug,
        reasoningBehavior = reasoningBehavior?.toReasoningRequestBehavior(),
    )
}

class ModelCatalogService(
    private val context: Context,
    private val client: OkHttpClient,
) {
    @Volatile
    private var snapshot: ModelCatalogSnapshot? = null
    private val loadMutex = Mutex()
    private val _status = MutableStateFlow(ModelCatalogStatus())
    private val _providerPresets = MutableStateFlow<List<CatalogProvider>>(emptyList())
    private val _snapshotFlow = MutableStateFlow<ModelCatalogSnapshot?>(null)

    val status: StateFlow<ModelCatalogStatus> = _status.asStateFlow()
    val providerPresets: StateFlow<List<CatalogProvider>> = _providerPresets.asStateFlow()
    val snapshotFlow: StateFlow<ModelCatalogSnapshot?> = _snapshotFlow.asStateFlow()

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
            _snapshotFlow.value = loadedCatalog.snapshot
            _providerPresets.value = loadedCatalog.snapshot.providers.filter { it.preset }
            val nextStatus = ModelCatalogStatus(
                source = loadedCatalog.source,
                entryCount = loadedCatalog.snapshot.exactEntries.size,
                providerCount = loadedCatalog.snapshot.providers.size,
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
            Log.w(TAG, "Downloaded LastChat catalog is invalid; falling back to bundled snapshot", it)
        }.getOrNull()
    }

    private suspend fun readBundledCatalog(): LoadedCatalog {
        val rawJson = withContext(Dispatchers.IO) {
            context.assets.open(MODEL_CATALOG_ASSET_NAME)
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
            throw IOException("Failed to download LastChat catalog: ${response.code}")
        }
        response.body?.string()?.takeIf { it.isNotBlank() }
            ?: throw IOException("Downloaded LastChat catalog was empty")
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

internal fun String.toCatalogIconUrl(): String {
    return when {
        startsWith("http://") || startsWith("https://") -> this
        else -> CATALOG_RAW_BASE_URL + trimStart('/')
    }
}

private fun List<CatalogCustomBody>.toCustomBodies(): List<CustomBody> {
    return mapNotNull { body ->
        body.key.takeIf { it.isNotBlank() }?.let {
            CustomBody(key = it, value = body.value)
        }
    }
}

private fun defaultOutputModalities(type: ModelType): List<Modality> {
    return when (type) {
        ModelType.CHAT, ModelType.EMBEDDING -> listOf(Modality.TEXT)
        ModelType.IMAGE -> listOf(Modality.IMAGE)
    }
}

private fun CatalogModelFamily.matchesAny(candidates: List<String>): Boolean {
    return candidates.any { candidate ->
        matchPatterns.any { pattern -> candidate.matchesCatalogPattern(pattern) }
    }
}

private fun CatalogModelVersion.matchesAny(candidates: List<String>): Boolean {
    if (excludePatterns.any { pattern ->
            candidates.any { candidate -> candidate.matchesCatalogPattern(pattern) }
        }
    ) {
        return false
    }
    if (matchPatterns.isEmpty()) return false
    return candidates.any { candidate ->
        matchPatterns.any { pattern -> candidate.matchesCatalogPattern(pattern) }
    }
}

private fun String.matchesCatalogPattern(pattern: String): Boolean {
    if (pattern.isBlank()) return false
    return runCatching {
        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(this)
    }.getOrDefault(false)
}
