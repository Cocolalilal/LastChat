package me.rerere.locallm

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.runtime.OnDeviceLlmModel
import me.rerere.common.runtime.OnDeviceLlmRuntime
import me.rerere.locallm.litert.LiteRtProvider

/**
 * Android LiteRT-LM binding for the shared on-device LLM contract.
 * Chat orchestration stays in GenerationHandler / IosAppController; this adapter
 * only executes installed local models.
 */
class AndroidOnDeviceLlmRuntime(
    private val provider: LiteRtProvider,
    private val store: LocalModelStore,
    private val embedder: LiteRtEmbedder,
) : OnDeviceLlmRuntime {
    override val available: Boolean = true
    override val unavailableReason: String? = null

    override suspend fun listModels(): List<OnDeviceLlmModel> =
        store.current().map { installed ->
            OnDeviceLlmModel(
                id = installed.id,
                displayName = installed.displayName,
                supportsEmbeddings = installed.isEmbedding,
            )
        }

    override suspend fun generateText(modelId: String, prompt: String): String {
        val installed = store.get(modelId)
            ?: store.current().firstOrNull { !it.isEmbedding }
            ?: error("No on-device LLM is installed")
        val model = Model(
            modelId = installed.id,
            displayName = installed.displayName,
            type = ModelType.CHAT,
        )
        val chunk = provider.generateText(
            providerSetting = ProviderSetting.LiteRtLocal(models = listOf(model)),
            messages = listOf(UIMessage.user(prompt)),
            params = TextGenerationParams(model = model),
        )
        return chunk.choices.firstOrNull()?.message?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            .orEmpty()
    }

    override suspend fun embed(modelId: String, text: String): List<Float>? {
        val installed = store.get(modelId)?.takeIf { it.isEmbedding } ?: return null
        return embedder.embed(installed, listOf(text)).firstOrNull()
    }
}
