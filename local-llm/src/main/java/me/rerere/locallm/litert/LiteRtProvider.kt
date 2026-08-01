package me.rerere.locallm.litert

import android.content.Context
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolCall
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.platform.PlatformLog
import me.rerere.common.inference.LocalInferenceManager
import me.rerere.common.inference.LocalInferenceWorkload
import me.rerere.locallm.InstalledLocalModel
import me.rerere.locallm.LiteRtEmbedder
import me.rerere.locallm.LiteRtRuntime
import me.rerere.locallm.LocalModelKind
import me.rerere.locallm.LocalModelStore
import kotlin.uuid.Uuid

@OptIn(ExperimentalApi::class)
class LiteRtProvider(
    private val context: Context,
    private val runtime: LiteRtRuntime,
    private val store: LocalModelStore,
    private val embedder: LiteRtEmbedder,
    private val inferenceManager: LocalInferenceManager,
) : Provider<ProviderSetting.LiteRtLocal> {

    private val gson = Gson()

    override suspend fun listModels(providerSetting: ProviderSetting.LiteRtLocal): List<Model> =
        providerSetting.models

    override suspend fun generateText(
        providerSetting: ProviderSetting.LiteRtLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk {
        val installed = requireInstalled(params)
        return inferenceManager.withLease(LocalInferenceWorkload.CHAT, installed.id) {
            val loaded = runtime.acquire(installed)
            val effective = loaded.model
            val preparedMessages = prepareLiteRtConversationMessages(effective, messages)
            val conversation = loaded.engine.createConversation(
                buildConversationConfig(effective, preparedMessages.messages, params)
            )
            runtime.setGenerating(effective)
            try {
                val sendable = preparedMessages.sendable
                val reply = conversation.sendMessage(toSendableMessage(effective, sendable), emptyMap())
                val (reasoning, text) = splitThink(reply.textString())
                val benchmark = conversation.getBenchmarkInfo()
                val parts = buildList {
                    if (reasoning.isNotBlank()) add(UIMessagePart.Reasoning(reasoning = reasoning))
                    if (text.isNotBlank()) add(UIMessagePart.Text(text))
                    addAll(reply.toolCalls.toUiToolCalls(gson))
                }
                MessageChunk(
                    id = Uuid.random().toString(),
                    model = params.model.modelId,
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = null,
                            message = UIMessage(role = MessageRole.ASSISTANT, parts = parts),
                            finishReason = if (reply.toolCalls.isNotEmpty()) "tool_calls" else "stop",
                        )
                    ),
                    usage = TokenUsage(
                        promptTokens = benchmark.lastPrefillTokenCount.coerceAtLeast(0),
                        completionTokens = benchmark.lastDecodeTokenCount.coerceAtLeast(0),
                        totalTokens = (benchmark.lastPrefillTokenCount + benchmark.lastDecodeTokenCount)
                            .coerceAtLeast(0),
                    ),
                )
            } finally {
                runtime.setReady(effective, loaded.backends.effective)
                runCatching { conversation.close() }
            }
        }
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.LiteRtLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> {
        val installed = requireInstalled(params)
        return flow {
            inferenceManager.withLease(LocalInferenceWorkload.CHAT, installed.id) {
                val loaded = runtime.acquire(installed)
                val effective = loaded.model
                val preparedMessages = prepareLiteRtConversationMessages(effective, messages)
                val conversation = loaded.engine.createConversation(
                    buildConversationConfig(effective, preparedMessages.messages, params)
                )
                val sendable = preparedMessages.sendable
                val modelId = params.model.modelId

                emitAll(callbackFlow {
                    runtime.setGenerating(effective)
                    var accumulated = ""
                    var emittedReasoning = ""
                    var emittedText = ""
                    var lastToolCalls: List<ToolCall> = emptyList()

                    fun emitTextChunk(reasoningDelta: String, textDelta: String) {
                        if (reasoningDelta.isEmpty() && textDelta.isEmpty()) return
                        val parts = buildList {
                            if (reasoningDelta.isNotEmpty()) {
                                add(UIMessagePart.Reasoning(reasoning = reasoningDelta, finishedAt = null))
                            }
                            if (textDelta.isNotEmpty()) add(UIMessagePart.Text(textDelta))
                        }
                        trySend(
                            MessageChunk(
                                id = modelId,
                                model = modelId,
                                choices = listOf(
                                    UIMessageChoice(
                                        index = 0,
                                        delta = UIMessage(role = MessageRole.ASSISTANT, parts = parts),
                                        message = null,
                                        finishReason = null,
                                    )
                                ),
                            )
                        )
                    }

                    val job = launch {
                        conversation.sendMessageAsync(toSendableMessage(effective, sendable), emptyMap())
                            .catch { close(it) }
                            .collect { msg ->
                                val raw = msg.textString()
                                accumulated = when {
                                    raw.isEmpty() -> accumulated
                                    raw.length >= accumulated.length && raw.startsWith(accumulated) -> raw
                                    else -> accumulated + raw
                                }
                                if (msg.toolCalls.isNotEmpty()) lastToolCalls = msg.toolCalls

                                val (reasoningFull, textFull) = splitThink(accumulated)
                                val reasoningDelta = reasoningFull.removePrefixSafe(emittedReasoning)
                                val textDelta = textFull.removePrefixSafe(emittedText)
                                emittedReasoning = reasoningFull
                                emittedText = textFull
                                emitTextChunk(reasoningDelta, textDelta)
                            }

                        val toolParts = lastToolCalls.toUiToolCalls(gson)
                        val benchmark = conversation.getBenchmarkInfo()
                        trySend(
                            MessageChunk(
                                id = modelId,
                                model = modelId,
                                choices = listOf(
                                    UIMessageChoice(
                                        index = 0,
                                        delta = if (toolParts.isEmpty()) {
                                            UIMessage(role = MessageRole.ASSISTANT, parts = emptyList())
                                        } else {
                                            UIMessage(role = MessageRole.ASSISTANT, parts = toolParts)
                                        },
                                        message = null,
                                        finishReason = if (toolParts.isNotEmpty()) "tool_calls" else "stop",
                                    )
                                ),
                                usage = TokenUsage(
                                    promptTokens = benchmark.lastPrefillTokenCount.coerceAtLeast(0),
                                    completionTokens = benchmark.lastDecodeTokenCount.coerceAtLeast(0),
                                    totalTokens = (benchmark.lastPrefillTokenCount + benchmark.lastDecodeTokenCount)
                                        .coerceAtLeast(0),
                                ),
                            )
                        )
                        close()
                    }

                    awaitClose {
                        runCatching { conversation.cancelProcess() }
                        job.cancel()
                        runtime.setReady(effective, loaded.backends.effective)
                        runCatching { conversation.close() }
                    }
                })
            }
        }
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: me.rerere.ai.provider.ImageGenerationParams,
    ): me.rerere.ai.ui.ImageGenerationResult {
        error("Local provider does not support image generation")
    }

    override suspend fun createEmbedding(
        providerSetting: ProviderSetting.LiteRtLocal,
        input: List<String>,
        model: Model,
    ): List<List<Float>> {
        if (input.isEmpty()) return emptyList()
        val installed = store.get(model.modelId)
            ?: throw IllegalStateException("model_not_installed:${model.modelId}")
        check(installed.kind == LocalModelKind.EMBEDDING) { "not_embedding_model:${model.modelId}" }
        return inferenceManager.withLease(LocalInferenceWorkload.EMBEDDING, installed.id) {
            embedder.embed(installed, input)
        }
    }

    private suspend fun requireInstalled(params: TextGenerationParams): InstalledLocalModel {
        val installed = store.get(params.model.modelId)
            ?: throw IllegalStateException("model_not_installed:${params.model.modelId}")
        check(installed.kind == LocalModelKind.LLM) { "not_llm_model:${params.model.modelId}" }
        return installed
    }

    private fun buildConversationConfig(
        model: InstalledLocalModel,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): ConversationConfig {
        val system = messages.firstOrNull { it.role == MessageRole.SYSTEM }
            ?.parts?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("\n") { it.text }
            ?.takeIf { it.isNotBlank() }

        val history = messages.filter { it.role != MessageRole.SYSTEM }
            .dropLast(1)
            .mapNotNull { toLiteMessage(model, it) }

        val sampler = SamplerConfig(
            model.config.topK ?: params.topK ?: model.defaultConfig.topK,
            (model.config.topP ?: params.topP ?: model.defaultConfig.topP).toDouble(),
            (model.config.temperature ?: params.temperature ?: model.defaultConfig.temperature).toDouble(),
            0,
        )

        val tools = if (params.tools.isNotEmpty()) LiteRtToolBridge.toToolProviders(params.tools) else emptyList()

        return ConversationConfig(
            systemInstruction = if (system != null) Contents.of(system) else Contents.of(""),
            initialMessages = history,
            tools = tools,
            samplerConfig = sampler,
            automaticToolCalling = false,
        )
    }

    private fun toSendableMessage(model: InstalledLocalModel, message: UIMessage): Message =
        toLiteMessage(model, message) ?: Message.user(contentsFor(model, message))

    private fun toLiteMessage(model: InstalledLocalModel, message: UIMessage): Message? {
        return when (message.role) {
            MessageRole.USER -> Message.user(contentsFor(model, message))
            MessageRole.ASSISTANT -> {
                val toolCalls = message.getToolCalls().map { call ->
                    ToolCall(call.toolName, parseArgs(call.arguments))
                }
                Message.model(contentsFor(model, message), toolCalls, emptyMap())
            }
            MessageRole.TOOL -> {
                val responses = message.getToolResults().map { result ->
                    Content.ToolResponse(result.toolName, result.content.toString())
                }
                Message.tool(Contents.of(responses.ifEmpty { listOf(Content.Text("")) }))
            }
            MessageRole.SYSTEM -> null
        }
    }

    private fun contentsFor(model: InstalledLocalModel, message: UIMessage): Contents {
        val contents = mutableListOf<Content>()
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> if (part.text.isNotBlank()) contents.add(Content.Text(part.text))
                is UIMessagePart.Image -> if (model.supportsImage) {
                    LiteRtMedia.readBytes(context, part.url)?.let { contents.add(Content.ImageBytes(it)) }
                        ?: PlatformLog.w(TAG, "Skipped unreadable image ${part.url}")
                }
                is UIMessagePart.Audio -> if (model.supportsAudio) {
                    LiteRtMedia.readBytes(context, part.url)?.let { contents.add(Content.AudioBytes(it)) }
                        ?: PlatformLog.w(TAG, "Skipped unreadable audio ${part.url}")
                }
                else -> Unit
            }
        }
        if (contents.isEmpty()) contents.add(Content.Text(""))
        return Contents.of(contents)
    }

    private fun prepareLiteRtConversationMessages(
        model: InstalledLocalModel,
        messages: List<UIMessage>,
    ): PreparedLiteRtMessages = prepareLiteRtConversationMessages(
        messages = messages,
        supportsImage = model.supportsImage,
        supportsAudio = model.supportsAudio,
    )

    private fun parseArgs(argumentsJson: String): Map<String, Any> {
        if (argumentsJson.isBlank()) return emptyMap()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(argumentsJson, Map::class.java) as? Map<String, Any>) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private fun Message.textString(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private fun String.removePrefixSafe(prefix: String): String =
        if (startsWith(prefix)) substring(prefix.length) else this

    companion object {
        private const val TAG = "LiteRtProvider"
        const val NAME = "litert_local"
    }
}

private fun List<ToolCall>.toUiToolCalls(gson: Gson): List<UIMessagePart.ToolCall> =
    map { call ->
        UIMessagePart.ToolCall(
            toolCallId = "litert_${Uuid.random()}",
            toolName = call.name,
            arguments = gson.toJson(call.arguments),
        )
    }

internal data class PreparedLiteRtMessages(
    val messages: List<UIMessage>,
    val sendable: UIMessage,
)

/**
 * LiteRT-LM 0.11 renders media placeholders from ConversationConfig.initialMessages, but its first
 * send only supplies media bytes from the newly sent message. Keeping an image or audio part in
 * history therefore fails natively with "Provided less ... than expected in the prompt".
 *
 * Preserve the historical roles/text, replace historical media with an explanatory marker, and
 * attach that media to the live user/model message where LiteRT can actually consume its bytes.
 */
internal fun prepareLiteRtConversationMessages(
    messages: List<UIMessage>,
    supportsImage: Boolean,
    supportsAudio: Boolean,
): PreparedLiteRtMessages {
    val sendableIndex = messages.indexOfLast { it.role != MessageRole.SYSTEM }
    require(sendableIndex >= 0) { "no_message_to_send" }
    val canCarryHistoricalMedia = messages[sendableIndex].role == MessageRole.USER ||
        messages[sendableIndex].role == MessageRole.ASSISTANT

    val historicalMedia = mutableListOf<Pair<MessageRole, UIMessagePart>>()
    val sanitized = messages.mapIndexed { index, message ->
        if (index == sendableIndex) return@mapIndexed message

        val parts = buildList {
            message.parts.forEach { part ->
                val move = when (part) {
                    is UIMessagePart.Image -> supportsImage
                    is UIMessagePart.Audio -> supportsAudio
                    else -> false
                }
                if (move) {
                    historicalMedia += message.role to part
                    add(
                        UIMessagePart.Text(
                            historicalMediaMarker(message.role, part, canCarryHistoricalMedia)
                        )
                    )
                } else {
                    add(part)
                }
            }
        }
        message.copy(parts = parts)
    }.toMutableList()

    val originalSendable = sanitized[sendableIndex]
    val sendable = if (historicalMedia.isNotEmpty() && canCarryHistoricalMedia) {
        originalSendable.copy(
            parts = buildList {
                add(UIMessagePart.Text("[Earlier conversation media reattached for local inference]"))
                historicalMedia.forEach { (role, part) ->
                    add(UIMessagePart.Text(historicalMediaLabel(role, part)))
                    add(part)
                }
                add(UIMessagePart.Text("[Current message]"))
                addAll(originalSendable.parts)
            }
        )
    } else {
        originalSendable
    }
    sanitized[sendableIndex] = sendable

    return PreparedLiteRtMessages(messages = sanitized, sendable = sendable)
}

private fun historicalMediaMarker(
    role: MessageRole,
    part: UIMessagePart,
    reattached: Boolean,
): String = if (reattached) {
    "[Earlier ${role.name.lowercase()} ${mediaKind(part)} is reattached with the current message.]"
} else {
    "[Earlier ${role.name.lowercase()} ${mediaKind(part)} is unavailable during this tool turn.]"
}

private fun historicalMediaLabel(role: MessageRole, part: UIMessagePart): String =
    "[Earlier ${role.name.lowercase()} ${mediaKind(part)}]"

private fun mediaKind(part: UIMessagePart): String = when (part) {
    is UIMessagePart.Image -> "image"
    is UIMessagePart.Audio -> "audio"
    else -> "media"
}

internal fun splitThink(raw: String): Pair<String, String> {
    if (!raw.contains("<think>")) return "" to raw
    val reasoning = StringBuilder()
    val text = StringBuilder()
    var i = 0
    var inThink = false
    while (i < raw.length) {
        if (!inThink && raw.startsWith("<think>", i)) {
            inThink = true
            i += "<think>".length
            continue
        }
        if (inThink && raw.startsWith("</think>", i)) {
            inThink = false
            i += "</think>".length
            continue
        }
        if (inThink) reasoning.append(raw[i]) else text.append(raw[i])
        i++
    }
    return reasoning.toString().trim() to text.toString().trimStart()
}
