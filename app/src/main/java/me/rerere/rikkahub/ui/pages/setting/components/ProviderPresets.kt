package me.rerere.rikkahub.ui.pages.setting.components

import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.withComfyDefaults
import me.rerere.rikkahub.data.ai.models.CatalogProvider
import me.rerere.rikkahub.data.ai.models.CatalogProviderType
import me.rerere.rikkahub.data.ai.models.CatalogSetupDefaults
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.toCatalogIconUrl
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

/**
 * Data class representing a provider preset for quick setup.
 */
data class ProviderPreset(
    val id: String? = null,
    val name: String,
    val description: String,
    val type: KClass<out ProviderSetting>,
    val baseUrl: String,
    val balanceOption: BalanceOption = BalanceOption(),
    val useResponseApi: Boolean = false,
    val chatCompletionsPath: String = "/chat/completions",
    val customIconUri: String? = null,
    val streamOptionsMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
    val imageResponseModalitiesMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
    val reasoningContentReplayMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
    val promptCacheMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
    val reasoningBehavior: me.rerere.ai.provider.ReasoningRequestBehavior? = null,
    val signupUrl: String? = null,
    val apiKeyUrl: String? = null,
    val setupRecommended: Boolean = false,
    val setupOrder: Int = 100,
    val setupDescription: String? = null,
    val setupModelIds: List<String> = emptyList(),
    val setupDefaults: CatalogSetupDefaults? = null,
    val setupSearchService: String? = null,
)

val FALLBACK_PROVIDER_PRESETS = listOf(
    ProviderPreset(
        name = "OpenRouter",
        description = "Access many hosted models through one OpenAI-compatible API",
        type = ProviderSetting.OpenAI::class,
        baseUrl = "https://openrouter.ai/api/v1",
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/credits",
            resultPath = "data.total_credits - data.total_usage",
        ),
    ),
    ProviderPreset(
        name = "OpenAI",
        description = "Creator of GPT models, industry-leading AI",
        type = ProviderSetting.OpenAI::class,
        baseUrl = "https://api.openai.com/v1",
    ),
    ProviderPreset(
        name = "Google Gemini",
        description = "Multimodal AI with text, image, audio, and video",
        type = ProviderSetting.Google::class,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
    ),
    ProviderPreset(
        name = "Anthropic Claude",
        description = "Safety-focused AI with strong reasoning",
        type = ProviderSetting.Claude::class,
        baseUrl = "https://api.anthropic.com/v1",
    ),
)

val SPECIAL_PROVIDER_PRESETS = listOf(
    ProviderPreset(
        name = "ComfyUI",
        description = "Connect to your local ComfyUI for workflow-based image generation",
        type = ProviderSetting.ComfyUI::class,
        baseUrl = "http://127.0.0.1:8188",
        customIconUri = "icons/comfyui.svg",
    ),
)

fun List<ProviderPreset>.withSpecialProviderPresets(): List<ProviderPreset> {
    val existingNames = map { it.name.lowercase() }.toSet()
    return this + SPECIAL_PROVIDER_PRESETS.filter { it.name.lowercase() !in existingNames }
}

fun ModelCatalogSnapshot.toProviderPresets(): List<ProviderPreset> {
    return providers
        .filter { it.preset }
        .map { it.toProviderPreset(this) }
}

fun CatalogProvider.toProviderPreset(snapshot: ModelCatalogSnapshot): ProviderPreset {
    val presetType = when (type) {
        CatalogProviderType.OPENAI -> ProviderSetting.OpenAI::class
        CatalogProviderType.GOOGLE -> ProviderSetting.Google::class
        CatalogProviderType.CLAUDE -> ProviderSetting.Claude::class
    }
    return ProviderPreset(
        id = id,
        name = name,
        description = description,
        type = presetType,
        baseUrl = baseUrl,
        balanceOption = balanceOption,
        useResponseApi = useResponseApi,
        chatCompletionsPath = chatCompletionsPath,
        customIconUri = icon?.toCatalogIconUrl(),
        streamOptionsMode = streamOptionsMode,
        imageResponseModalitiesMode = imageResponseModalitiesMode,
        reasoningContentReplayMode = reasoningContentReplayMode,
        promptCacheMode = promptCacheMode,
        reasoningBehavior = reasoningBehavior?.toReasoningRequestBehavior(),
        signupUrl = signupUrl,
        apiKeyUrl = apiKeyUrl,
        setupRecommended = setupRecommended,
        setupOrder = setupOrder,
        setupDescription = setupDescription,
        setupModelIds = setupModels,
        setupDefaults = setupDefaults,
        setupSearchService = setupSearchService,
    )
}

/**
 * Creates a ProviderSetting from a preset.
 */
fun ProviderPreset.toProviderSetting(): ProviderSetting {
    val parsedId = id?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    return when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = parsedId ?: Uuid.random(),
            name = name,
            baseUrl = baseUrl,
            balanceOption = balanceOption,
            useResponseApi = useResponseApi,
            chatCompletionsPath = chatCompletionsPath,
            customIconUri = customIconUri,
            streamOptionsMode = streamOptionsMode,
            imageResponseModalitiesMode = imageResponseModalitiesMode,
            reasoningContentReplayMode = reasoningContentReplayMode,
            promptCacheMode = promptCacheMode,
            reasoningBehavior = reasoningBehavior,
        )

        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = parsedId ?: Uuid.random(),
            name = name,
            baseUrl = baseUrl,
            balanceOption = balanceOption,
            customIconUri = customIconUri,
        )

        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            id = parsedId ?: Uuid.random(),
            name = name,
            baseUrl = baseUrl,
            balanceOption = balanceOption,
            customIconUri = customIconUri,
        )

        ProviderSetting.ComfyUI::class -> ProviderSetting.ComfyUI(
            id = parsedId ?: Uuid.random(),
            name = name,
            baseUrl = baseUrl,
            customIconUri = customIconUri,
            models = listOf(
                Model(
                    modelId = "model.safetensors",
                    displayName = "ComfyUI model",
                ).withComfyDefaults()
            ),
        )

        else -> ProviderSetting.OpenAI(
            id = parsedId ?: Uuid.random(),
            name = name,
            baseUrl = baseUrl,
            balanceOption = balanceOption,
            customIconUri = customIconUri,
        )
    }
}
