package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelType {
    CHAT,
    IMAGE,
    EMBEDDING,
    STT,
}

@Serializable
enum class Modality {
    TEXT,
    IMAGE,
    AUDIO,
}

@Serializable
enum class ImageGenerationMethod {
    @SerialName("diffusion")
    DIFFUSION,

    @SerialName("multimodal")
    MULTIMODAL,
}

@Serializable
enum class ModelAbility {
    TOOL,
    REASONING,
}

@Serializable
sealed class BuiltInTools {
    @Serializable
    @SerialName("search")
    data object Search : BuiltInTools()

    @Serializable
    @SerialName("url_context")
    data object UrlContext : BuiltInTools()
}

