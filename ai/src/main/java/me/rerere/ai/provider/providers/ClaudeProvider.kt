package me.rerere.ai.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import me.rerere.common.platform.PlatformLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderProxy
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.ai.util.parseErrorDetail
import me.rerere.common.http.urlHostOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpProxy
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformMediaEncoder
import me.rerere.common.platform.PlatformServerEvent
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "ClaudeProvider"
private const val ANTHROPIC_VERSION = "2023-06-01"

private data class ClaudePromptCacheBreakpoints(
    val cacheSystem: Boolean,
    val messageIds: Set<Uuid>,
)

class ClaudeProvider(
    private val platformHttpClient: PlatformHttpClient,
    private val mediaEncoder: PlatformMediaEncoder,
) : Provider<ProviderSetting.Claude> {
    override suspend fun listModels(providerSetting: ProviderSetting.Claude): List<Model> =
        withContext(Dispatchers.IO) {
            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = "${providerSetting.baseUrl}/models",
                    headers = mapOf(
                        "x-api-key" to providerSetting.apiKey,
                        "anthropic-version" to ANTHROPIC_VERSION
                    ),
                    proxy = providerSetting.proxy.toPlatformProxy()
                )
            )
            val bodyStr = response.body.decodeToString()
            if (response.statusCode !in 200..299) {
                error("Failed to get models: ${response.statusCode} $bodyStr")
            }

            val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
            val data = bodyJson["data"]?.jsonArray ?: return@withContext emptyList()

            data.mapNotNull { modelJson ->
                val modelObj = modelJson.jsonObject
                val id = modelObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val displayName = modelObj["display_name"]?.jsonPrimitive?.contentOrNull ?: id

                Model(
                    modelId = id,
                    displayName = displayName,
                    canonicalModelId = ModelIdNormalizer.canonicalize(id),
                )
            }
        }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult {
        error("Claude provider does not support image generation")
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.Claude,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk = withContext(Dispatchers.IO) {
        val requestBody = buildMessageRequest(messages, params)
        val encodedRequestBody = json.encodeToString(requestBody)

        PlatformLog.i(TAG, "generateText: $encodedRequestBody")

        val response = platformHttpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/messages",
                headers = params.customHeaders.toHeaderMap()
                    .withReferHeaders(providerSetting.baseUrl)
                    .withClaudeHeaders(providerSetting.apiKey),
                body = encodedRequestBody.encodeToByteArray(),
                mediaType = "application/json",
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        val bodyStr = response.body.decodeToString()
        if (response.statusCode !in 200..299) {
            throw Exception("Failed to get response: ${response.statusCode} $bodyStr")
        }

        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject

        // 从 JsonObject 中提取必要的信息
        val id = bodyJson["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val model = bodyJson["model"]?.jsonPrimitive?.contentOrNull ?: ""
        val content = bodyJson["content"]?.jsonArray ?: JsonArray(emptyList())
        val stopReason = bodyJson["stop_reason"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val usage = parseTokenUsage(bodyJson["usage"] as? JsonObject)

        MessageChunk(
            id = id,
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = parseMessage(content),
                    finishReason = stopReason
                )
            ),
            usage = usage
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.Claude,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = callbackFlow {
        val requestBody = buildMessageRequest(messages, params, stream = true)
        val encodedRequestBody = json.encodeToString(requestBody)
        val request = PlatformHttpRequest(
            method = "POST",
            url = "${providerSetting.baseUrl}/messages",
            headers = params.customHeaders.toHeaderMap()
                .withReferHeaders(providerSetting.baseUrl)
                .withClaudeHeaders(providerSetting.apiKey),
            body = encodedRequestBody.encodeToByteArray(),
            mediaType = "application/json",
            proxy = providerSetting.proxy.toPlatformProxy()
        )

        PlatformLog.i(TAG, "streamText: $encodedRequestBody")

        requestBody["messages"]?.jsonArray?.forEach {
            PlatformLog.i(TAG, "streamText: $it")
        }

        val job = launch {
            platformHttpClient.streamEvents(request).collect { event ->
                when (event) {
                    is PlatformServerEvent.Open -> Unit
                    PlatformServerEvent.Closed -> close()
                    is PlatformServerEvent.Failure -> close(parseStreamFailure(event))
                    is PlatformServerEvent.Event -> {
                        PlatformLog.d(TAG, "onEvent: type=${event.event}, data=${event.data}")
                        if (event.event == "message_stop") {
                            PlatformLog.d(TAG, "Stream ended")
                            close()
                            return@collect
                        }
                        if (event.event == "error") {
                            val eventData = json.parseToJsonElement(event.data).jsonObject
                            close(eventData["error"]?.parseErrorDetail())
                            return@collect
                        }
                        runCatching {
                            trySend(parseStreamChunk(event))
                        }.onFailure { error ->
                            close(error)
                        }
                    }
                }
            }
        }

        awaitClose {
            job.cancel()
        }
    }.retryWhen { cause, attempt ->
        if (attempt < 3 && cause.message?.contains("429") == true) {
            PlatformLog.w(TAG, "streamText: Rate limit (429) hit. Retrying attempt ${attempt + 1}...")
            kotlinx.coroutines.delay(1000L * (attempt + 1))
            true
        } else {
            false
        }
    }

    private fun buildMessageRequest(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean = false
    ): JsonObject {
        val promptCacheBreakpoints = messages.promptCacheBreakpoints()
        return buildJsonObject {
            put("model", params.model.modelId)
            put("messages", buildMessages(messages, promptCacheBreakpoints.messageIds))
            put("max_tokens", params.maxTokens ?: 64_000)

            if (params.temperature != null && (params.thinkingBudget ?: 0) == 0) put(
                "temperature",
                params.temperature
            )
            if (params.topP != null) put("top_p", params.topP)

            put("stream", stream)

            // system prompt
            val systemMessage = messages.firstOrNull { it.role == MessageRole.SYSTEM }
            if (systemMessage != null) {
                val systemTextParts = systemMessage.parts.filterIsInstance<UIMessagePart.Text>()
                val cacheableSystemPartIndex = if (promptCacheBreakpoints.cacheSystem) {
                    systemTextParts.indexOfLast { it.text.isNotBlank() }
                } else {
                    -1
                }
                put("system", buildJsonArray {
                    systemTextParts.forEachIndexed { index, part ->
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", part.text)
                            if (index == cacheableSystemPartIndex) {
                                put("cache_control", buildPromptCacheControl())
                            }
                        })
                    }
                })
            }

            // 处理 thinking budget
            if (params.model.abilities.contains(ModelAbility.REASONING)) {
                val level = ReasoningLevel.fromBudgetTokens(params.thinkingBudget ?: 0)
                put("thinking", buildJsonObject {
                    if (level == ReasoningLevel.OFF) {
                        put("type", "disabled")
                    } else {
                        put("type", "enabled")
                        if (level != ReasoningLevel.AUTO) put("budget_tokens", params.thinkingBudget ?: 0)
                    }
                })
            }

            // 处理工具
            if (params.model.abilities.contains(ModelAbility.TOOL) && params.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    params.tools.forEachIndexed { index, tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", json.encodeToJsonElement(tool.parameters()))
                            if (promptCacheBreakpoints.cacheSystem && index == params.tools.lastIndex) {
                                put("cache_control", buildPromptCacheControl())
                            }
                        })
                    }
                }
            }
        }.mergeCustomBody(params.customBody)
    }

    private fun buildMessages(messages: List<UIMessage>, cacheMessageIds: Set<Uuid>) = buildJsonArray {
        messages
            .filter { it.isValidToUpload() && it.role != MessageRole.SYSTEM }
            .forEach { message ->
                if (message.role == MessageRole.TOOL) {
                    val toolResults = message.getToolResults()
                    toolResults.forEachIndexed { index, result ->
                        add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                add(buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", result.toolCallId)
                                    put("content", json.encodeToString(result.content))
                                    if (message.id in cacheMessageIds && index == toolResults.lastIndex) {
                                        put("cache_control", buildPromptCacheControl())
                                    }
                                })
                            }
                        })
                    }
                    return@forEach
                }

                add(buildJsonObject {
                    // role
                    put("role", JsonPrimitive(message.role.name.lowercase()))

                    // content
                    val cacheableTextPartIndex = if (message.id in cacheMessageIds) {
                        message.parts.indexOfLast { part ->
                            part is UIMessagePart.Text && part.text.isNotBlank()
                        }
                    } else {
                        -1
                    }
                    putJsonArray("content") {
                        message.parts.forEachIndexed { index, part ->
                            when (part) {
                                is UIMessagePart.Text -> {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", part.text)
                                        if (index == cacheableTextPartIndex) {
                                            put("cache_control", buildPromptCacheControl())
                                        }
                                    })
                                }

                                is UIMessagePart.Image -> {
                                    add(buildJsonObject {
                                        mediaEncoder.encodeImage(part.url).onSuccess { base64Data ->
                                            put("type", "image")
                                            put("source", buildJsonObject {
                                                put("type", "base64")
                                                put(
                                                    "media_type",
                                                    "image/jpeg"
                                                ) // 默认为 jpeg，可能需要根据实际情况调整
                                                put(
                                                    "data",
                                                    base64Data.substringAfter(",")
                                                ) // 移除 data:image/jpeg;base64, 前缀
                                            })
                                        }.onFailure {
                                            it.printStackTrace()
                                            PlatformLog.w(TAG, "encode image failed: ${part.url}")
                                            // 如果图片编码失败，添加一个空文本块
                                            put("type", "text")
                                            put("text", "")
                                        }
                                    })
                                }

                                is UIMessagePart.ToolCall -> {
                                    add(buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", part.toolCallId)
                                        put("name", part.toolName)
                                        put("input", json.parseToJsonElement(part.arguments))
                                    })
                                }

                                is UIMessagePart.Reasoning -> {
                                    add(buildJsonObject {
                                        put("type", "thinking")
                                        put("thinking", part.reasoning)
                                        part.metadata?.let {
                                            it.forEach { entry ->
                                                put(entry.key, entry.value)
                                            }
                                        }
                                    })
                                }

                                else -> {
                                    PlatformLog.w(TAG, "buildMessages: message part not supported: $part")
                                    // DO NOTHING
                                }
                            }
                        }
                    }
                })
            }
    }

    private fun buildPromptCacheControl() = buildJsonObject {
        put("type", "ephemeral")
    }

    private fun List<UIMessage>.promptCacheBreakpoints(): ClaudePromptCacheBreakpoints {
        val cacheSystem = any { message ->
            message.role == MessageRole.SYSTEM &&
                message.parts.any { it is UIMessagePart.Text && it.text.isNotBlank() }
        }
        val maxMessageBreakpoints = if (cacheSystem) 3 else 4
        val messageIds = asSequence()
            .filter { message ->
                message.role != MessageRole.SYSTEM &&
                    (
                        (message.role == MessageRole.TOOL && message.getToolResults().isNotEmpty()) ||
                        (message.role != MessageRole.TOOL && message.parts.any { it is UIMessagePart.Text && it.text.isNotBlank() })
                    )
            }
            .map { it.id }
            .toList()
            .takeLast(maxMessageBreakpoints)
            .toSet()

        return ClaudePromptCacheBreakpoints(
            cacheSystem = cacheSystem,
            messageIds = messageIds
        )
    }

    private fun parseMessage(content: JsonArray): UIMessage {
        val parts = mutableListOf<UIMessagePart>()

        content.forEach { contentBlock ->
            val block = contentBlock.jsonObject
            val type = block["type"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "text", "text_delta" -> {
                    val text = block["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    parts.add(UIMessagePart.Text(text))
                }

                "thinking", "thinking_delta", "signature_delta" -> {
                    val thinking = block["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
                    val signature = block["signature"]?.jsonPrimitive?.contentOrNull
                    val reasoning = UIMessagePart.Reasoning(
                        reasoning = thinking,
                        createdAt = Clock.System.now(),
                    )
                    if (signature != null) {
                        reasoning.metadata = buildJsonObject {
                            put("signature", signature)
                        }
                    }
                    parts.add(reasoning)
                }

                "redacted_thinking" -> {
                    error("redacted_thinking detected, not support yet!")
                }

                "tool_use" -> {
                    val id = block["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val name = block["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val input = block["input"]?.jsonObject ?: JsonObject(emptyMap())
                    parts.add(
                        UIMessagePart.ToolCall(
                            toolCallId = id,
                            toolName = name,
                            arguments = if (input.isEmpty()) "" else json.encodeToString(input)
                        )
                    )
                }

                "input_json_delta" -> {
                    val input = block["partial_json"]?.jsonPrimitive?.contentOrNull
                    parts.add(
                        UIMessagePart.ToolCall(
                            toolCallId = "",
                            toolName = "",
                            arguments = input ?: ""
                        )
                    )
                }
            }
        }

        return UIMessage(
            role = MessageRole.ASSISTANT,
            parts = parts
        )
    }

    private fun parseStreamChunk(event: PlatformServerEvent.Event): MessageChunk {
        val dataJson = json.parseToJsonElement(event.data).jsonObject
        val deltaMessage = parseMessage(buildJsonArray {
            val contentBlockObj = dataJson["content_block"]?.jsonObject
            val deltaObj = dataJson["delta"]?.jsonObject
            if (contentBlockObj != null) {
                add(contentBlockObj)
            }
            if (deltaObj != null) {
                add(deltaObj)
            }
        })
        val tokenUsage = parseTokenUsage(
            dataJson["usage"]?.jsonObject ?: dataJson["message"]?.jsonObject?.get("usage")?.jsonObject
        )

        return MessageChunk(
            id = event.id ?: "",
            model = "",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = deltaMessage,
                    message = null,
                    finishReason = null
                )
            ),
            usage = tokenUsage
        )
    }

    private fun parseTokenUsage(jsonObject: JsonObject?): TokenUsage? {
        if (jsonObject == null) return null
        val inputTokens = jsonObject["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val outputTokens = jsonObject["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val cacheCreationTokens = jsonObject["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val cacheReadTokens = jsonObject["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val promptTokens = inputTokens + cacheCreationTokens + cacheReadTokens
        return TokenUsage(
            promptTokens = promptTokens,
            completionTokens = outputTokens,
            cachedTokens = cacheReadTokens,
            totalTokens = promptTokens + outputTokens
        )
    }

    private fun parseStreamFailure(event: PlatformServerEvent.Failure): Throwable {
        val fallback = RuntimeException(
            "Stream failed${event.statusCode?.let { " #$it" }.orEmpty()}: ${event.message.orEmpty()}"
        )
        val bodyRaw = event.body
        return try {
            if (!bodyRaw.isNullOrBlank()) {
                val bodyElement = Json.parseToJsonElement(bodyRaw)
                PlatformLog.i(TAG, "Error response: $bodyElement")
                bodyElement.parseErrorDetail()
            } else {
                fallback
            }
        } catch (e: Throwable) {
            PlatformLog.w(TAG, "onFailure: failed to parse from $bodyRaw")
            e.printStackTrace()
            e
        }
    }
}

private fun List<CustomHeader>.toHeaderMap(): Map<String, String> {
    return filter { it.name.isNotBlank() }.associate { it.name to it.value }
}

private fun Map<String, String>.withClaudeHeaders(apiKey: String): Map<String, String> {
    return this + mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to ANTHROPIC_VERSION,
        "Content-Type" to "application/json"
    )
}

private fun Map<String, String>.withReferHeaders(baseUrl: String): Map<String, String> {
    return when (baseUrl.urlHostOrNull()) {
        "aihubmix.com" -> this + ("APP-Code" to "DKHA9468")
        "openrouter.ai" -> this + mapOf(
            "X-Title" to "LastChat",
            "HTTP-Referer" to "https://github.com/Cocolalilal/LastChat"
        )
        else -> this
    }
}

private fun ProviderProxy.toPlatformProxy(): PlatformHttpProxy? {
    return when (this) {
        ProviderProxy.None -> null
        is ProviderProxy.Http -> PlatformHttpProxy(
            host = address,
            port = port,
            username = username,
            password = password
        )
    }
}
