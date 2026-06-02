package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    val canonicalModelId: String? = null,
    val id: Uuid = Uuid.random(),
    val type: ModelType = ModelType.CHAT,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    val tools: Set<BuiltInTools> = emptySet(),
    val providerOverwrite: ProviderSetting? = null,
    val iconUrl: String? = null, // Remote icon URL
    val providerSlug: String? = null, // Provider slug for LobeHub CDN icons (e.g., "anthropic")
    val customIconUri: String? = null, // User-selected custom icon URI
    val imageGenerationMethod: ImageGenerationMethod? = null, // Only for IMAGE type models
    val reasoningBehavior: ReasoningRequestBehavior? = null,
    val contextWindowTokens: Int? = null,
    val localRuntime: LocalModelRuntime? = null,
    val localModelPath: String? = null,
    val localCatalogId: String? = null,
    val localRevision: String? = null,
    val localFileName: String? = null,
    val localDownloadState: LocalModelDownloadState = LocalModelDownloadState.NOT_DOWNLOADED,
    val localUpdateInfo: LocalModelUpdateInfo? = null,
    val localSamplerDefaults: LocalModelSamplerDefaults? = null,
    val localAccelerator: LocalModelAccelerator? = null,
    val localImported: Boolean = false,
)

@Serializable
enum class ModelType {
    CHAT,
    IMAGE,
    EMBEDDING,
}

@Serializable
enum class Modality {
    TEXT,
    IMAGE,
}

@Serializable
enum class ImageGenerationMethod {
    @SerialName("diffusion")
    DIFFUSION,      // Traditional diffusion models like DALL-E, Stable Diffusion
    @SerialName("multimodal")
    MULTIMODAL,     // Chat models with image output (GPT-4o, Gemini 2.0 Flash)
}

@Serializable
enum class ModelAbility {
    TOOL,
    REASONING,
}

@Serializable
enum class LocalModelRuntime {
    @SerialName("litert_lm")
    LITERT_LM,
}

@Serializable
enum class LocalModelDownloadState {
    @SerialName("not_downloaded")
    NOT_DOWNLOADED,

    @SerialName("downloading")
    DOWNLOADING,

    @SerialName("importing")
    IMPORTING,

    @SerialName("downloaded")
    DOWNLOADED,

    @SerialName("updating")
    UPDATING,

    @SerialName("failed")
    FAILED,
}

@Serializable
enum class LocalModelAccelerator {
    @SerialName("auto")
    AUTO,

    @SerialName("cpu")
    CPU,

    @SerialName("gpu")
    GPU,

    @SerialName("npu")
    NPU,

    @SerialName("tpu")
    TPU,
}

@Serializable
data class LocalModelSamplerDefaults(
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val temperature: Float = 1.0f,
    val maxTokens: Int = 1024,
    val maxContextLength: Int? = null,
    val accelerators: List<LocalModelAccelerator> = listOf(LocalModelAccelerator.GPU, LocalModelAccelerator.CPU),
    val visionAccelerator: LocalModelAccelerator? = null,
    val speculativeDecoding: Boolean = false,
)

@Serializable
data class LocalModelUpdateInfo(
    val latestRevision: String,
    val latestFileName: String,
    val message: String = "",
    val updateAvailable: Boolean = false,
)

// 模型(提供商)提供的内置工具选项
@Serializable
sealed class BuiltInTools {
    // https://ai.google.dev/gemini-api/docs/google-search?hl=zh-cn
    @Serializable
    @SerialName("search")
    data object Search : BuiltInTools()

    // https://ai.google.dev/gemini-api/docs/url-context?hl=zh-cn
    @Serializable
    @SerialName("url_context")
    data object UrlContext : BuiltInTools()
}

