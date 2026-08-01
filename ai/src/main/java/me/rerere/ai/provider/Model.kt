package me.rerere.ai.provider

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
    val iconUrl: String? = null,
    val providerSlug: String? = null,
    val customIconUri: String? = null,
    val imageGenerationMethod: ImageGenerationMethod? = null,
    val reasoningBehavior: ReasoningRequestBehavior? = null,
    val sttOptions: SttOptions? = null,
    /** Total input + output context supported by this API model. Null means unknown. */
    val contextWindowTokens: Int? = null,
    /** Maximum images accepted in one request. Null means unknown/unlimited. */
    val maxImagesInContext: Int? = null,
    // For CHAT models, when true this model is hidden from user-facing pickers (chat interface,
    // assistant model, etc.) and shown only in backend/background-task pickers (title, summarizer,
    // subagent, suggestions, translate, OCR). Non-chat models are always selected from their
    // feature-specific settings, so this flag is intentionally ignored for them.
    val backend: Boolean = false,
)

