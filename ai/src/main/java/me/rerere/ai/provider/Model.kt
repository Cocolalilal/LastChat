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
)

