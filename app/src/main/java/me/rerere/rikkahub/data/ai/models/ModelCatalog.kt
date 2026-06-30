package me.rerere.rikkahub.data.ai.models

import android.content.Context
import android.util.Log
import java.io.IOException
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
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.ReasoningRequestBehavior
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "ModelCatalogService"
private const val MODEL_CATALOG_DIR_NAME = "model_catalog"
private const val MODEL_CATALOG_FILE_NAME = "lastchat_catalog.json"
private const val MODEL_CATALOG_FILE_PATH = "$MODEL_CATALOG_DIR_NAME/$MODEL_CATALOG_FILE_NAME"
private const val MODEL_CATALOG_ASSET_NAME = "lastchat_catalog.json"
private const val MODEL_CATALOG_URL =
    "https://raw.githubusercontent.com/Cocolalilal/LastChat/LastChat/catalog/lastchat_catalog.json"
private const val CATALOG_RAW_BASE_URL =
    "https://raw.githubusercontent.com/Cocolalilal/LastChat/LastChat/catalog/"

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
    @SerialName("global_rules")
    val globalRules: List<CatalogModelRule> = emptyList(),
    @SerialName("model_overrides")
    val modelOverrides: List<CatalogModelOverride> = emptyList(),
    @SerialName("model_families")
    val modelFamilies: List<CatalogModelFamily> = emptyList(),
    @SerialName("search_providers")
    val searchProviders: List<CatalogServiceProvider> = emptyList(),
    @SerialName("tts_providers")
    val ttsProviders: List<CatalogTTSProvider> = emptyList(),
    @SerialName("stt_providers")
    val sttProviders: List<CatalogServiceProvider> = emptyList(),
    @SerialName("model_groups")
    val legacyModelGroups: List<CatalogModelFamily> = emptyList(),
) {
    val effectiveModelFamilies: List<CatalogModelFamily>
        get() = modelFamilies.ifEmpty { legacyModelGroups }
}

@Serializable
data class CatalogServiceProvider(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val icon: String? = null,
    val preset: Boolean = true,
    @SerialName("built_in")
    val builtIn: Boolean = false,
)

@Serializable
data class CatalogTTSProvider(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val icon: String? = null,
    val preset: Boolean = true,
    @SerialName("built_in")
    val builtIn: Boolean = false,
    val type: CatalogTTSProviderType = CatalogTTSProviderType.OPENAI,
    @SerialName("base_url")
    val baseUrl: String = "",
    @SerialName("default_model")
    val defaultModel: String = "",
    @SerialName("default_voice")
    val defaultVoice: String = "",
    @SerialName("signup_url")
    val signupUrl: String? = null,
    @SerialName("api_key_url")
    val apiKeyUrl: String? = null,
)

@Serializable
enum class CatalogTTSProviderType {
    @SerialName("openai") OPENAI,
    @SerialName("gemini") GEMINI,
    @SerialName("system") SYSTEM,
    @SerialName("minimax") MINIMAX,
    @SerialName("elevenlabs") ELEVENLABS,
    @SerialName("qwen") QWEN,
    @SerialName("fishaudio") FISHAUDIO,
    @SerialName("cartesia") CARTESIA,
    @SerialName("playht") PLAYHT,
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
    @SerialName("prompt_cache_mode")
    val promptCacheMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
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
data class CatalogModelRule(
    val id: String = "",
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
data class CatalogModelOverride(
    val id: String = "",
    @SerialName("canonical_model_id")
    val canonicalModelId: String? = null,
    @SerialName("api_aliases")
    val apiAliases: List<String> = emptyList(),
    @SerialName("provider_ids")
    val providerIds: List<String> = emptyList(),
    @SerialName("provider_slugs")
    val providerSlugs: List<String> = emptyList(),
    @SerialName("base_url_patterns")
    val baseUrlPatterns: List<String> = emptyList(),
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
    @SerialName("input_cost_per_token")
    val inputCostPerToken: Double? = null,
    @SerialName("output_cost_per_token")
    val outputCostPerToken: Double? = null,
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
    val globalRules: List<CatalogModelRule> = emptyList(),
    val modelOverrides: List<CatalogModelOverride> = emptyList(),
    val searchProviders: List<CatalogServiceProvider> = emptyList(),
    val ttsProviders: List<CatalogTTSProvider> = emptyList(),
    val sttProviders: List<CatalogServiceProvider> = emptyList(),
) {
    val catalog: LastChatCatalog
        get() = LastChatCatalog(
            providers = providers,
            modelFamilies = modelFamilies,
            globalRules = globalRules,
            modelOverrides = modelOverrides,
            searchProviders = searchProviders,
            ttsProviders = ttsProviders,
            sttProviders = sttProviders,
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
        val effectiveOverrides = catalog.modelOverrides + catalog.models.map { it.toModelOverride() }

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

        catalog.modelOverrides.forEach { override ->
            val entry = override.toCatalogEntry(modelFamilies) ?: return@forEach
            buildList {
                override.id.takeIf { it.isNotBlank() }?.let(::add)
                override.canonicalModelId?.takeIf { it.isNotBlank() }?.let(::add)
                addAll(override.apiAliases)
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
            globalRules = catalog.globalRules,
            modelOverrides = effectiveOverrides,
            searchProviders = catalog.searchProviders,
            ttsProviders = catalog.ttsProviders,
            sttProviders = catalog.sttProviders,
        )
    }
}

private fun CatalogModel.toModelOverride(): CatalogModelOverride {
    return CatalogModelOverride(
        id = id,
        canonicalModelId = canonicalModelId,
        apiAliases = apiAliases,
        providerIds = providerIds,
        type = type,
        imageGenerationMethod = imageGenerationMethod,
        inputModalities = inputModalities,
        outputModalities = outputModalities,
        abilities = abilities,
        providerSlug = providerSlug,
        inputCostPerToken = inputCostPerToken,
        outputCostPerToken = outputCostPerToken,
        reasoningBehavior = reasoningBehavior,
    )
}

private fun CatalogModelOverride.toCatalogEntry(modelFamilies: List<CatalogModelFamily>): ModelCatalogEntry? {
    val key = id.takeIf { it.isNotBlank() } ?: apiAliases.firstOrNull { it.isNotBlank() } ?: return null
    val resolvedType = type ?: ModelType.CHAT
    val inputs = inputModalities ?: listOf(Modality.TEXT)
    val outputs = outputModalities ?: defaultOutputModalities(resolvedType)
    val resolvedAbilities = abilities ?: emptyList()
    val fingerprint = ModelCatalogFingerprint(
        modelId = key,
        canonicalHint = canonicalModelId,
        providerHint = null,
        providerSlugHint = providerSlug
    )
    val family = modelFamilies.firstOrNull { it.matches(fingerprint) }
    return ModelCatalogEntry(
        key = key,
        canonicalModelId = ModelIdNormalizer.canonicalize(key, canonicalModelId),
        apiAliases = apiAliases,
        providerIds = providerIds,
        modelFamilyId = family?.id,
        mode = resolvedType.name.lowercase(),
        supportedModalities = (inputs + outputs).distinct(),
        inputModalities = inputs,
        outputModalities = outputs,
        supportsVision = inputs.contains(Modality.IMAGE),
        supportsFunctionCalling = resolvedAbilities.contains(ModelAbility.TOOL),
        supportsReasoning = resolvedAbilities.contains(ModelAbility.REASONING),
        imageGenerationMethod = imageGenerationMethod,
        inputCostPerToken = inputCostPerToken,
        outputCostPerToken = outputCostPerToken,
        iconUrl = family?.icon?.toCatalogIconUrl(),
        providerSlug = providerSlug,
        reasoningBehavior = reasoningBehavior?.toReasoningRequestBehavior(),
    )
}

fun ModelCatalogSnapshot.inferFamilyEntry(
    modelId: String,
    canonicalHint: String? = null,
): ModelCatalogEntry? {
    return resolveModelEntry(
        modelId = modelId,
        canonicalHint = canonicalHint,
        includeOverrides = false,
    )
}

fun ModelCatalogSnapshot.resolveModelEntry(
    modelId: String,
    canonicalHint: String? = null,
    providerHint: ProviderSetting? = null,
    providerSlugHint: String? = null,
    includeOverrides: Boolean = true,
): ModelCatalogEntry? {
    if (modelId.isBlank()) return null
    val fingerprint = ModelCatalogFingerprint(
        modelId = modelId,
        canonicalHint = canonicalHint,
        providerHint = providerHint,
        providerSlugHint = providerSlugHint,
    )
    val builder = ModelCatalogEntryBuilder(modelId, fingerprint.canonicalModelId)
    globalRules
        .filter { it.matches(fingerprint) }
        .forEach { builder.applyRule(it, fingerprint) }

    val family = modelFamilies.firstOrNull { it.matches(fingerprint) }
    family?.let { matchedFamily ->
        builder.modelFamilyId = matchedFamily.id
        builder.iconUrl = matchedFamily.icon?.toCatalogIconUrl()
        builder.applyFamily(matchedFamily)
        matchedFamily.versions
            .filter { it.matches(fingerprint) }
            .forEach { builder.applyVersion(it, fingerprint) }
    }

    if (includeOverrides) {
        modelOverrides
            .filter { it.matches(fingerprint) }
            .forEach { builder.applyOverride(it, fingerprint) }
    }

    return if (builder.hasMatchedRule) builder.build() else null
}

data class ModelCatalogResolutionTrace(
    val globalRules: List<String>,
    val familyId: String?,
    val familyVersions: List<String>,
    val overrides: List<String>,
    val entry: ModelCatalogEntry?,
)

fun ModelCatalogSnapshot.explainModelResolution(
    modelId: String,
    canonicalHint: String? = null,
    providerHint: ProviderSetting? = null,
    providerSlugHint: String? = null,
): ModelCatalogResolutionTrace {
    if (modelId.isBlank()) {
        return ModelCatalogResolutionTrace(emptyList(), null, emptyList(), emptyList(), null)
    }
    val fingerprint = ModelCatalogFingerprint(
        modelId = modelId,
        canonicalHint = canonicalHint,
        providerHint = providerHint,
        providerSlugHint = providerSlugHint,
    )
    val matchedGlobalRules = globalRules.filter { it.matches(fingerprint) }
    val family = modelFamilies.firstOrNull { it.matches(fingerprint) }
    val matchedVersions = family?.versions?.filter { it.matches(fingerprint) }.orEmpty()
    val matchedOverrides = modelOverrides.filter { it.matches(fingerprint) }
    val entry = resolveModelEntry(
        modelId = modelId,
        canonicalHint = canonicalHint,
        providerHint = providerHint,
        providerSlugHint = providerSlugHint,
    )
    return ModelCatalogResolutionTrace(
        globalRules = matchedGlobalRules.map { it.id.ifBlank { it.matchPatterns.joinToString() } },
        familyId = family?.id,
        familyVersions = matchedVersions.map { it.id.ifBlank { it.matchPatterns.joinToString() } },
        overrides = matchedOverrides.map { it.id.ifBlank { it.matchPatterns.joinToString() } },
        entry = entry,
    )
}

private data class ModelCatalogFingerprint(
    val modelId: String,
    val canonicalHint: String?,
    val providerHint: ProviderSetting?,
    val providerSlugHint: String?,
) {
    val canonicalModelId: String = ModelIdNormalizer.canonicalize(modelId, canonicalHint)
    private val preprocessedModelId: String = ModelIdNormalizer.preprocess(modelId, canonicalHint)
    val candidates: List<String> = buildList {
        add(modelId)
        canonicalHint?.takeIf { it.isNotBlank() }?.let(::add)
        add(canonicalModelId)
        add(preprocessedModelId)
    }.filter { it.isNotBlank() }.distinct()
    val providerId: String? = providerHint?.id?.toString()
    val providerBaseUrl: String? = providerHint?.catalogBaseUrl()
    val providerSlugs: Set<String> = buildSet {
        providerSlugHint?.takeIf { it.isNotBlank() }?.let { add(it.normalizeCatalogToken()) }
        providerHint?.catalogProviderTokens()?.forEach(::add)
    }
}

private class ModelCatalogEntryBuilder(
    private val key: String,
    initialCanonicalModelId: String,
) {
    var canonicalModelId: String = initialCanonicalModelId
    var modelFamilyId: String? = null
    var type: ModelType = ModelType.CHAT
    var imageGenerationMethod: ImageGenerationMethod? = null
    var inputModalities: List<Modality> = listOf(Modality.TEXT)
    var outputModalities: List<Modality> = listOf(Modality.TEXT)
    var abilities: List<ModelAbility> = emptyList()
    var inputCostPerToken: Double? = null
    var outputCostPerToken: Double? = null
    var iconUrl: String? = null
    var providerSlug: String? = null
    var reasoningBehavior: CatalogRequestBehavior? = null
    var apiAliases: List<String> = emptyList()
    var providerIds: List<String> = emptyList()
    var hasMatchedRule: Boolean = false

    fun applyFamily(family: CatalogModelFamily) {
        hasMatchedRule = true
        type = family.type
        imageGenerationMethod = family.imageGenerationMethod
        inputModalities = family.inputModalities.ifEmpty { listOf(Modality.TEXT) }
        outputModalities = family.outputModalities.ifEmpty { defaultOutputModalities(type) }
        abilities = family.abilities
        providerSlug = family.providerSlug
        reasoningBehavior = family.reasoningBehavior
    }

    fun applyRule(rule: CatalogModelRule, fingerprint: ModelCatalogFingerprint) {
        hasMatchedRule = true
        applySharedFields(
            type = rule.type,
            imageGenerationMethod = rule.imageGenerationMethod,
            inputModalities = rule.inputModalities,
            outputModalities = rule.outputModalities,
            abilities = rule.abilities,
            providerSlug = rule.providerSlug,
            canonicalModelId = rule.canonicalModelId,
            reasoningBehavior = rule.reasoningBehavior,
            fingerprint = fingerprint,
        )
    }

    fun applyVersion(version: CatalogModelVersion, fingerprint: ModelCatalogFingerprint) {
        hasMatchedRule = true
        applySharedFields(
            type = version.type,
            imageGenerationMethod = version.imageGenerationMethod,
            inputModalities = version.inputModalities,
            outputModalities = version.outputModalities,
            abilities = version.abilities,
            providerSlug = version.providerSlug,
            canonicalModelId = version.canonicalModelId,
            reasoningBehavior = version.reasoningBehavior,
            fingerprint = fingerprint,
        )
    }

    fun applyOverride(override: CatalogModelOverride, fingerprint: ModelCatalogFingerprint) {
        hasMatchedRule = true
        apiAliases = override.apiAliases.ifEmpty { apiAliases }
        providerIds = override.providerIds.ifEmpty { providerIds }
        inputCostPerToken = override.inputCostPerToken ?: inputCostPerToken
        outputCostPerToken = override.outputCostPerToken ?: outputCostPerToken
        applySharedFields(
            type = override.type,
            imageGenerationMethod = override.imageGenerationMethod,
            inputModalities = override.inputModalities,
            outputModalities = override.outputModalities,
            abilities = override.abilities,
            providerSlug = override.providerSlug,
            canonicalModelId = override.canonicalModelId,
            reasoningBehavior = override.reasoningBehavior,
            fingerprint = fingerprint,
        )
    }

    private fun applySharedFields(
        type: ModelType?,
        imageGenerationMethod: ImageGenerationMethod?,
        inputModalities: List<Modality>?,
        outputModalities: List<Modality>?,
        abilities: List<ModelAbility>?,
        providerSlug: String?,
        canonicalModelId: String?,
        reasoningBehavior: CatalogRequestBehavior?,
        fingerprint: ModelCatalogFingerprint,
    ) {
        type?.let { nextType ->
            this.type = nextType
            this.outputModalities = defaultOutputModalities(nextType)
            if (nextType == ModelType.EMBEDDING) {
                this.inputModalities = listOf(Modality.TEXT)
            }
        }
        imageGenerationMethod?.let { this.imageGenerationMethod = it }
        inputModalities?.let { this.inputModalities = it.ifEmpty { listOf(Modality.TEXT) } }
        outputModalities?.let { this.outputModalities = it.ifEmpty { defaultOutputModalities(this.type) } }
        abilities?.let { this.abilities = it }
        providerSlug?.let { this.providerSlug = it }
        reasoningBehavior?.let { this.reasoningBehavior = it }
        canonicalModelId
            ?.takeIf { it.isNotBlank() }
            ?.let { this.canonicalModelId = ModelIdNormalizer.canonicalize(fingerprint.modelId, it) }
    }

    fun build(): ModelCatalogEntry {
        val inputs = inputModalities.ifEmpty { listOf(Modality.TEXT) }
        val outputs = outputModalities.ifEmpty { defaultOutputModalities(type) }
        return ModelCatalogEntry(
            key = key,
            canonicalModelId = canonicalModelId,
            apiAliases = apiAliases,
            providerIds = providerIds,
            modelFamilyId = modelFamilyId,
            mode = type.name.lowercase(),
            supportedModalities = (inputs + outputs).distinct(),
            inputModalities = inputs,
            outputModalities = outputs,
            supportsVision = inputs.contains(Modality.IMAGE),
            supportsFunctionCalling = abilities.contains(ModelAbility.TOOL),
            supportsReasoning = abilities.contains(ModelAbility.REASONING),
            imageGenerationMethod = imageGenerationMethod,
            inputCostPerToken = inputCostPerToken,
            outputCostPerToken = outputCostPerToken,
            iconUrl = iconUrl,
            providerSlug = providerSlug,
            reasoningBehavior = reasoningBehavior?.toReasoningRequestBehavior(),
        )
    }
}

class ModelCatalogService(
    private val context: Context,
    private val httpClient: PlatformHttpClient,
    private val fileStore: PlatformFileStore,
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

    private suspend fun readDownloadedCatalogOrNull(): LoadedCatalog? {
        val rawJson = fileStore.readBytes(MODEL_CATALOG_FILE_PATH)?.decodeToString() ?: return null

        return runCatching {
            val snapshot = ModelCatalogParser.parse(rawJson)
            LoadedCatalog(
                snapshot = snapshot,
                source = ModelCatalogSource.DOWNLOADED,
                lastSuccessfulRefreshAt = fileStore.lastModified(MODEL_CATALOG_FILE_PATH),
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
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = MODEL_CATALOG_URL,
            )
        )
        if (response.statusCode !in 200..299) {
            throw IOException("Failed to download LastChat catalog: ${response.statusCode}")
        }
        response.body.decodeToString().takeIf { it.isNotBlank() }
            ?: throw IOException("Downloaded LastChat catalog was empty")
    }

    private suspend fun writeDownloadedCatalog(rawJson: String) {
        fileStore.writeBytes(MODEL_CATALOG_FILE_PATH, rawJson.encodeToByteArray())
    }
}

internal fun String.toCatalogIconUrl(): String {
    return when {
        startsWith("http://") || startsWith("https://") -> this
        else -> CATALOG_RAW_BASE_URL + trimStart('/')
    }
}

fun ModelCatalogSnapshot.searchProviderIconUri(providerIdOrName: String): String? {
    return searchProviders
        .firstOrNull { provider -> provider.matchesCatalogServiceProvider(providerIdOrName) }
        ?.icon
        ?.toCatalogIconUrl()
}

fun ModelCatalogSnapshot.ttsProviderIconUri(providerIdOrName: String): String? {
    return ttsProviders
        .firstOrNull { provider -> provider.matchesCatalogTTSProvider(providerIdOrName) }
        ?.icon
        ?.toCatalogIconUrl()
}

fun ModelCatalogSnapshot.ttsProviderById(providerId: String): CatalogTTSProvider? {
    return ttsProviders.firstOrNull { it.id == providerId }
}

fun ModelCatalogSnapshot.sttProviderIconUri(providerIdOrName: String): String? {
    return sttProviders
        .firstOrNull { provider -> provider.matchesCatalogServiceProvider(providerIdOrName) }
        ?.icon
        ?.toCatalogIconUrl()
}

private fun CatalogServiceProvider.matchesCatalogServiceProvider(value: String): Boolean {
    val normalizedValue = value.normalizeCatalogToken()
    if (normalizedValue.isBlank()) return false
    return sequenceOf(id, name)
        .plus(aliases)
        .map { it.normalizeCatalogToken() }
        .any { it == normalizedValue }
}

private fun CatalogTTSProvider.matchesCatalogTTSProvider(value: String): Boolean {
    val normalizedValue = value.normalizeCatalogToken()
    if (normalizedValue.isBlank()) return false
    return sequenceOf(id, name)
        .plus(aliases)
        .map { it.normalizeCatalogToken() }
        .any { it == normalizedValue }
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
        ModelType.STT -> listOf(Modality.TEXT)
    }
}

private fun CatalogModelFamily.matchesAny(candidates: List<String>): Boolean {
    return candidates.any { candidate ->
        matchPatterns.any { pattern -> candidate.matchesCatalogPattern(pattern) }
    }
}

private fun CatalogModelFamily.matches(fingerprint: ModelCatalogFingerprint): Boolean {
    return matchPatterns.anyPatternMatches(fingerprint.candidates)
}

private fun CatalogModelRule.matches(fingerprint: ModelCatalogFingerprint): Boolean {
    if (excludePatterns.anyPatternMatches(fingerprint.candidates)) return false
    if (matchPatterns.isEmpty()) return false
    return matchPatterns.anyPatternMatches(fingerprint.candidates)
}

private fun CatalogModelVersion.matches(fingerprint: ModelCatalogFingerprint): Boolean {
    if (excludePatterns.anyPatternMatches(fingerprint.candidates)) return false
    if (matchPatterns.isEmpty()) return false
    return matchPatterns.anyPatternMatches(fingerprint.candidates)
}

private fun CatalogModelOverride.matches(fingerprint: ModelCatalogFingerprint): Boolean {
    if (!matchesProviderConstraints(fingerprint)) return false
    if (excludePatterns.anyPatternMatches(fingerprint.candidates)) return false

    val exactCandidates = buildList {
        id.takeIf { it.isNotBlank() }?.let(::add)
        addAll(apiAliases)
    }.map { it.lowercase() }
    val fingerprintCandidates = fingerprint.candidates.map { it.lowercase() }
    if (exactCandidates.any { it in fingerprintCandidates }) return true
    if (matchPatterns.isEmpty()) return false
    return matchPatterns.anyPatternMatches(fingerprint.candidates)
}

private fun CatalogModelOverride.matchesProviderConstraints(fingerprint: ModelCatalogFingerprint): Boolean {
    if (providerIds.isNotEmpty() && fingerprint.providerId !in providerIds) return false
    if (
        providerSlugs.isNotEmpty() &&
        providerSlugs.map { it.normalizeCatalogToken() }.none { it in fingerprint.providerSlugs }
    ) {
        return false
    }
    if (
        baseUrlPatterns.isNotEmpty() &&
        fingerprint.providerBaseUrl?.let { baseUrl ->
            baseUrlPatterns.any { pattern -> baseUrl.matchesCatalogPattern(pattern) }
        } != true
    ) {
        return false
    }
    return true
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

private fun List<String>.anyPatternMatches(candidates: List<String>): Boolean {
    return candidates.any { candidate ->
        any { pattern -> candidate.matchesCatalogPattern(pattern) }
    }
}

private fun String.matchesCatalogPattern(pattern: String): Boolean {
    if (pattern.isBlank()) return false
    return runCatching {
        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(this)
    }.getOrDefault(false)
}

private fun ProviderSetting.catalogBaseUrl(): String {
    return when (this) {
        is ProviderSetting.Claude -> baseUrl
        is ProviderSetting.Google -> baseUrl
        is ProviderSetting.OpenAI -> baseUrl
        is ProviderSetting.ComfyUI -> baseUrl
    }
}

private fun ProviderSetting.catalogProviderTokens(): Set<String> {
    return when (this) {
        is ProviderSetting.Claude -> setOf("anthropic", "claude")
        is ProviderSetting.Google -> {
            if (vertexAI) {
                setOf("google", "vertex-ai", "vertex-ai-language-models")
            } else {
                setOf("google", "gemini", "google-ai-studio")
            }
        }
        is ProviderSetting.OpenAI -> {
            val base = baseUrl.lowercase()
            buildSet {
                if ("api.openai.com" in base) add("openai")
                if ("openrouter" in base) add("openrouter")
                if ("github" in base) add("github")
                if ("ollama" in base) add("ollama")
            }
        }

        is ProviderSetting.ComfyUI -> setOf("comfyui")
    }.map { it.normalizeCatalogToken() }.toSet()
}

private fun String.normalizeCatalogToken(): String {
    return trim()
        .lowercase()
        .replace(Regex("\\s+"), "-")
        .replace('_', '-')
        .replace('.', '-')
}
