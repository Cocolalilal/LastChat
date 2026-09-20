package me.rerere.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.runtime.OnDeviceChatChunk
import me.rerere.common.runtime.OnDeviceChatMessage
import me.rerere.common.runtime.OnDeviceLlmRuntime
import me.rerere.common.runtime.flattenOnDeviceChat
import kotlin.uuid.Uuid

/**
 * Shared generation adapter for [ProviderSetting.LiteRtLocal].
 *
 * Android [OnDeviceLlmChatEngine] implementations keep LiteRT's full
 * UIMessage/tool loop. Other platforms flatten to [OnDeviceLlmRuntime] so iOS
 * and Android share one provider lookup instead of a second chat engine.
 */
class OnDeviceLlmProvider(
    private val runtime: OnDeviceLlmRuntime,
) : Provider<ProviderSetting.LiteRtLocal> {
    override val supportsEmbeddings: Boolean = true

    override suspend fun listModels(providerSetting: ProviderSetting.LiteRtLocal): List<Model> {
        val installed = runtime.listModels().map { model ->
            Model(
                modelId = model.id,
                displayName = model.displayName,
                type = if (model.supportsEmbeddings) ModelType.EMBEDDING else ModelType.CHAT,
            )
        }
        return installed.ifEmpty { providerSetting.models }
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.LiteRtLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk {
        val engine = runtime as? OnDeviceLlmChatEngine
        if (engine != null) {
            return engine.generateAsProvider(providerSetting, messages, params)
        }
        val chunk = runtime.generateChat(params.model.modelId, messages.toOnDeviceChat())
        return chunk.toMessageChunk(params.model.modelId)
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.LiteRtLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> {
        val engine = runtime as? OnDeviceLlmChatEngine
        if (engine != null) {
            return engine.streamAsProvider(providerSetting, messages, params)
        }
        return flow {
            runtime.streamChat(params.model.modelId, messages.toOnDeviceChat()).collect { chunk ->
                emit(chunk.toMessageChunk(params.model.modelId, delta = true))
            }
        }
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult {
        error("On-device LiteRT-LM does not generate images")
    }

    override suspend fun createEmbedding(
        providerSetting: ProviderSetting.LiteRtLocal,
        input: List<String>,
        model: Model,
    ): List<List<Float>> {
        val engine = runtime as? OnDeviceLlmChatEngine
        if (engine != null) {
            return engine.embedAsProvider(providerSetting, input, model)
        }
        return input.map { text -> runtime.embed(model.modelId, text).orEmpty() }
    }

    companion object {
        const val NAME = "litert_local"
    }
}

/**
 * Optional lossless bridge for Android LiteRT. Implement on the runtime so
 * GenerationHandler can keep tool calls, images, and token usage.
 */
interface OnDeviceLlmChatEngine {
    suspend fun generateAsProvider(
        providerSetting: ProviderSetting.LiteRtLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk

    suspend fun streamAsProvider(
        providerSetting: ProviderSetting.LiteRtLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk>

    suspend fun embedAsProvider(
        providerSetting: ProviderSetting.LiteRtLocal,
        input: List<String>,
        model: Model,
    ): List<List<Float>>
}

fun List<UIMessage>.toOnDeviceChat(): List<OnDeviceChatMessage> = map { message ->
    OnDeviceChatMessage(
        role = message.role.name.lowercase(),
        text = message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text },
    )
}

fun flattenMessagesForOnDevice(messages: List<UIMessage>): String =
    flattenOnDeviceChat(messages.toOnDeviceChat())

internal fun OnDeviceChatChunk.toMessageChunk(modelId: String, delta: Boolean = false): MessageChunk {
    val parts = buildList {
        if (reasoning.isNotBlank()) add(UIMessagePart.Reasoning(reasoning = reasoning))
        add(UIMessagePart.Text(text))
    }
    val message = UIMessage(role = MessageRole.ASSISTANT, parts = parts)
    return MessageChunk(
        id = Uuid.random().toString(),
        model = modelId,
        choices = listOf(
            UIMessageChoice(
                index = 0,
                delta = if (delta) message else null,
                message = if (delta) null else message,
                finishReason = finishReason,
            ),
        ),
        usage = usageOrNull(),
    )
}

private fun OnDeviceChatChunk.usageOrNull(): TokenUsage? {
    if (promptTokens == null && completionTokens == null) return null
    val prompt = promptTokens ?: 0
    val completion = completionTokens ?: 0
    return TokenUsage(
        promptTokens = prompt,
        completionTokens = completion,
        totalTokens = prompt + completion,
    )
}
