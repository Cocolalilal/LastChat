package me.rerere.ai.provider.providers.openai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import me.rerere.common.platform.PlatformLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
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
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderProxy
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.extractReasoningSummaryTitle
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.ai.util.parseErrorDetail
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.common.http.urlHostOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpProxy
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.common.platform.PlatformMediaEncoder
import kotlin.time.Clock

private const val TAG = "ResponseAPI"

class ResponseAPI(
    private val httpClient: PlatformHttpClient,
    private val mediaEncoder: PlatformMediaEncoder,
) : OpenAIImpl {
    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        val requestBody = buildRequestBody(
            messages = messages,
            params = params,
            stream = false,
            providerSetting = providerSetting,
        )
        val encodedRequestBody = json.encodeToString(requestBody)

        PlatformLog.i(TAG, "generateText: $encodedRequestBody")

        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/responses",
                headers = params.customHeaders.toHeaderMap()
                    .withReferHeaders(providerSetting.baseUrl)
                    .withAuthAndJson(providerSetting.apiKey),
                body = encodedRequestBody.encodeToByteArray(),
                mediaType = "application/json",
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        val bodyStr = response.body.decodeToString()
        if (response.statusCode !in 200..299) {
            throw Exception("Failed to get response: ${response.statusCode} $bodyStr")
        }

        PlatformLog.i(TAG, "generateText: $bodyStr")
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val output = parseResponseOutput(bodyJson)

        return output
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = callbackFlow {
        val requestBody = buildRequestBody(
            messages = messages,
            params = params,
            stream = true,
            providerSetting = providerSetting,
        )
        val encodedRequestBody = json.encodeToString(requestBody)
        val request = PlatformHttpRequest(
            method = "POST",
            url = "${providerSetting.baseUrl}/responses",
            headers = params.customHeaders.toHeaderMap()
                .withReferHeaders(providerSetting.baseUrl)
                .withAuthAndJson(providerSetting.apiKey),
            body = encodedRequestBody.encodeToByteArray(),
            mediaType = "application/json",
            proxy = providerSetting.proxy.toPlatformProxy()
        )

        PlatformLog.i(TAG, "streamText: $encodedRequestBody")

        val job = launch {
            httpClient.streamEvents(request).collect { event ->
                when (event) {
                    is PlatformServerEvent.Open -> Unit
                    PlatformServerEvent.Closed -> close()
                    is PlatformServerEvent.Failure -> close(parseStreamFailure(event))
                    is PlatformServerEvent.Event -> {
                        PlatformLog.d(TAG, "onEvent: ${event.id}/${event.event} ${event.data}")
                        if (event.data.isNotBlank()) {
                            val eventJson = json.parseToJsonElement(event.data).jsonObject
                            val chunk = parseResponseDelta(eventJson)
                            if (chunk != null) {
                                trySend(chunk)
                            }
                        }
                        if (event.event == "response.completed") {
                            close()
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

    private fun buildRequestBody(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean
    ): JsonObject {
        return buildRequestBody(
            messages = messages,
            params = params,
            stream = stream,
            providerSetting = null,
        )
    }

    private fun buildRequestBody(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean,
        providerSetting: ProviderSetting.OpenAI?,
    ): JsonObject {
        return buildJsonObject {
            put("model", params.model.modelId)
            put("stream", stream)
            if (providerSetting?.baseUrl?.contains("api.openai.com", ignoreCase = true) == true &&
                !params.sessionId.isNullOrBlank()
            ) {
                put("prompt_cache_key", params.sessionId)
            }

            if (isModelAllowTemperature(params.model)) {
                if (params.temperature != null) put("temperature", params.temperature)
                if (params.topP != null) put("top_p", params.topP)
            }
            if (params.maxTokens != null) put("max_output_tokens", params.maxTokens)

            // system instructions
            if (messages.any { it.role == MessageRole.SYSTEM }) {
                val parts = messages.first { it.role == MessageRole.SYSTEM }.parts
                put(
                    "instructions",
                    parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text })
            }

            // messages
            put("input", buildMessages(messages))

            // reasoning
            if (params.model.abilities.contains(ModelAbility.REASONING)) {
                val level = ReasoningLevel.fromBudgetTokens(params.thinkingBudget)
                if (level != ReasoningLevel.OFF) {
                    put("reasoning", buildJsonObject {
                        put("summary", "auto")
                        if (level != ReasoningLevel.AUTO) {
                            put("effort", level.effort)
                        }
                    })
                }
            }

            // tools
            if (params.model.abilities.contains(ModelAbility.TOOL) && params.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    params.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("name", tool.name)
                            put("description", tool.description)
                            put(
                                "parameters",
                                json.encodeToJsonElement(
                                    tool.parameters()
                                )
                            )
                        })
                    }
                }
            }
        }.mergeCustomBody(params.customBody)
    }

    private fun buildMessages(messages: List<UIMessage>) = buildJsonArray {
        messages
            .filter {
                it.isValidToUpload() && it.role != MessageRole.SYSTEM
            }
            .forEachIndexed { _, message ->
                if (message.role == MessageRole.TOOL) {
                    message.getToolResults().forEach { result ->
                        add(buildJsonObject {
                            put("type", "function_call_output")
                            put("call_id", result.toolCallId)
                            put("output", json.encodeToString(result.content))
                        })
                    }
                    return@forEachIndexed
                }

                val contentParts = buildResponseContent(message)
                add(buildJsonObject {
                    put("role", JsonPrimitive(message.role.name.lowercase()))

                    if (message.parts.isOnlyTextPart()) {
                        put(
                            "content",
                            message.parts.filterIsInstance<UIMessagePart.Text>().first().text
                        )
                    } else if (contentParts.isNotEmpty()) {
                        put("content", contentParts)
                    } else {
                        logWarning(
                            "buildMessages: falling back to empty content for role=${message.role} parts=${message.parts}"
                        )
                        put("content", "")
                    }
                })

                message.getToolCalls()
                    .takeIf { it.isNotEmpty() }
                    ?.forEach { toolCall ->
                        add(buildJsonObject {
                            put("type", "function_call")
                            put("call_id", toolCall.toolCallId)
                            put("name", toolCall.toolName)
                            put("arguments", toolCall.arguments)
                        })
                    }
            }
    }

    private fun buildResponseContent(message: UIMessage): JsonArray = buildJsonArray {
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    add(buildJsonObject {
                        put(
                            "type",
                            if (message.role == MessageRole.USER) "input_text" else "output_text"
                        )
                        put("text", part.text)
                    })
                }

                is UIMessagePart.Image -> {
                    add(buildJsonObject {
                        mediaEncoder.encodeImage(part.url).onSuccess {
                            put(
                                "type",
                                if (message.role == MessageRole.USER) "input_image" else "output_image"
                            )
                            put("image_url", it)
                        }.onFailure {
                            it.printStackTrace()
                            println("encode image failed: ${part.url}")

                            put("type", "input_text")
                            put("text", "Error: Failed to encode image to base64")
                        }
                    })
                }

                is UIMessagePart.Audio -> {
                    add(buildJsonObject {
                        mediaEncoder.encodeAudio(part.url, withPrefix = false).onSuccess { base64Data ->
                            val format = if (part.url.endsWith(".wav") || part.url.startsWith("data:audio/wav")) "wav" else "mp3"
                            put("type", "input_audio")
                            put("input_audio", buildJsonObject {
                                put("data", base64Data)
                                put("format", format)
                            })
                        }.onFailure {
                            it.printStackTrace()
                            println("encode audio failed: ${part.url}")

                            put("type", "input_text")
                            put("text", "Error: Failed to encode audio to base64")
                        }
                    })
                }

                else -> {
                    logWarning("buildMessages: message part not supported: $part")
                }
            }
        }
    }

    private fun parseResponseDelta(jsonObject: JsonObject): MessageChunk? {
        val chunkType = jsonObject["type"]?.jsonPrimitive?.content ?: error("chunk type not found")

        when (chunkType) {
            "response.output_text.delta" -> {
                return MessageChunk(
                    id = jsonObject["item_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    model = "",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = UIMessage.assistant(
                                jsonObject["delta"]?.jsonPrimitive?.contentOrNull ?: ""
                            ),
                            message = null,
                            finishReason = null
                        )
                    )
                )
            }

            "response.reasoning_summary_text.delta" -> {
                val delta = jsonObject["delta"]?.jsonPrimitive?.contentOrNull ?: ""
                return MessageChunk(
                    id = jsonObject["item_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    model = "",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = UIMessage(
                                role = MessageRole.ASSISTANT,
                                parts = listOf(
                                    UIMessagePart.Reasoning(
                                        reasoning = delta,
                                        createdAt = Clock.System.now(),
                                        finishedAt = null,
                                        title = delta.extractReasoningSummaryTitle(),
                                        metadata = buildJsonObject {
                                            put("reasoning_kind", "summary")
                                        }
                                    )
                                )
                            ),
                            message = null,
                            finishReason = null
                        )
                    )
                )
            }

            "response.output_item.added" -> {
                val item = jsonObject["item"]?.jsonObject ?: error("chunk item not found")
                val type = item["type"]?.jsonPrimitive?.content ?: error("chunk type not found")
                val id = item["id"]?.jsonPrimitive?.content ?: error("chunk id not found")
                if (type == "function_call") {
                    return MessageChunk(
                        id = id,
                        model = "",
                        choices = listOf(
                            UIMessageChoice(
                                index = 0,
                                message = null,
                                delta = UIMessage(
                                    role = MessageRole.ASSISTANT,
                                    parts = listOf(
                                        UIMessagePart.ToolCall(
                                            toolCallId = id,
                                            toolName = item["name"]?.jsonPrimitive?.content ?: "",
                                            arguments = item["arguments"]?.jsonPrimitive?.content
                                                ?: ""
                                        )
                                    )
                                ),
                                finishReason = null
                            )
                        )
                    )
                } else if(type == "reasoning") {
                    return MessageChunk(
                        id = id,
                        model = "",
                        choices = listOf(
                            UIMessageChoice(
                                index = 0,
                                message = null,
                                delta = UIMessage(
                                    role = MessageRole.ASSISTANT,
                                    parts = listOf(
                                        UIMessagePart.Reasoning(
                                            reasoning = "",
                                            createdAt = Clock.System.now(),
                                            finishedAt = null,
                                            metadata = buildJsonObject {
                                                put("reasoning_kind", "summary")
                                            }
                                        )
                                    )
                                ),
                                finishReason = null,
                            )
                        )
                    )
                }
            }

            "response.function_call_arguments.done" -> {
                val toolCallId =
                    jsonObject["item_id"]?.jsonPrimitive?.content ?: error("item_id not found")
                val arguments =
                    jsonObject["arguments"]?.jsonPrimitive?.content ?: error("arguments not found")
                return MessageChunk(
                    id = toolCallId,
                    model = "",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = UIMessage(
                                role = MessageRole.ASSISTANT,
                                parts = listOf(
                                    UIMessagePart.ToolCall(
                                        toolCallId = toolCallId,
                                        toolName = "",
                                        arguments = arguments,
                                    )
                                )
                            ),
                            message = null,
                            finishReason = null
                        )
                    ),
                )
            }

            "response.completed" -> {
                return MessageChunk(
                    id = jsonObject["item_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    model = "",
                    choices = emptyList(),
                    usage = parseTokenUsage(jsonObject["response"]?.jsonObject?.get("usage")?.jsonObject)
                )
            }
        }

        return null
    }

    private fun parseResponseOutput(jsonObject: JsonObject): MessageChunk {
        println(jsonObject)
        val outputs = jsonObject["output"]?.jsonArray ?: error("output not found")
        val parts = arrayListOf<UIMessagePart>()

        outputs.forEach { outputItem ->
            val output = outputItem.jsonObject
            val type = output["type"]?.jsonPrimitive?.content ?: error("output type not found")
            when (type) {
                "reasoning" -> {
                    val summary = output["summary"]?.jsonArray ?: error("summary not found")
                    summary.map { it.jsonObject }.forEach { part ->
                        val partType = part["type"]?.jsonPrimitive?.content ?: error("part type not found")
                        when (partType) {
                            "summary_text" -> {
                                val text = part["text"]?.jsonPrimitive?.content ?: error("text not found")
                                parts.add(
                                    UIMessagePart.Reasoning(
                                        reasoning = text,
                                        createdAt = Clock.System.now(),
                                        finishedAt = Clock.System.now(),
                                        title = text.extractReasoningSummaryTitle(),
                                        metadata = buildJsonObject {
                                            put("reasoning_kind", "summary")
                                        }
                                    )
                                )
                            }
                        }
                    }
                }

                "function_call" -> {
                    val callId = output["call_id"]?.jsonPrimitive?.content ?: error("call_id not found")
                    val name = output["name"]?.jsonPrimitive?.content ?: error("name not found")
                    val arguments =
                        output["arguments"]?.jsonPrimitive?.content ?: error("arguments not found")
                    parts.add(
                        UIMessagePart.ToolCall(
                            toolCallId = callId,
                            toolName = name,
                            arguments = arguments
                        )
                    )
                }

                "message" -> {
                    val content = output["content"]?.jsonArray ?: error("content not found")
                    content.map { it.jsonObject }.forEach { part ->
                        val partType = part["type"]?.jsonPrimitive?.content ?: error("part type not found")
                        when (partType) {
                            "output_text" -> {
                                val text = part["text"]?.jsonPrimitive?.content ?: error("text not found")
                                parts.add(
                                    UIMessagePart.Text(
                                        text = text
                                    )
                                )
                            }

                            else -> error("unknown part type $partType")
                        }
                    }
                }
            }
        }

        return MessageChunk(
            id = jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: "",
            model = jsonObject["model"]?.jsonPrimitive?.contentOrNull ?: "",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = parts,
                    ),
                    finishReason = null,
                    delta = null
                )
            ),
            usage = parseTokenUsage(jsonObject["usage"]?.jsonObject)
        )
    }

    private fun parseTokenUsage(jsonObject: JsonObject?): TokenUsage? {
        if (jsonObject == null) return null
        val promptTokens = jsonObject["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val completionTokens = jsonObject["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val inputTokensDetails = jsonObject["input_tokens_details"]?.jsonObjectOrNull
        val promptTokensDetails = jsonObject["prompt_tokens_details"]?.jsonObjectOrNull
        val cacheReadTokens = inputTokensDetails?.firstPositiveIntOrNull(
            "cached_tokens",
            "cache_read_input_tokens",
            "cache_read_tokens",
            "prompt_cache_hit_tokens"
        )
            ?: promptTokensDetails?.firstPositiveIntOrNull(
                "cached_tokens",
                "cache_read_input_tokens",
                "cache_read_tokens",
                "prompt_cache_hit_tokens"
            )
            ?: jsonObject.firstPositiveIntOrNull(
                "cache_read_input_tokens",
                "cache_read_tokens",
                "cached_tokens",
                "prompt_cache_hit_tokens"
            )
            ?: 0
        val cacheCreationTokens = inputTokensDetails?.firstPositiveIntOrNull(
            "cache_creation_input_tokens",
            "cache_creation_tokens",
            "cache_write_tokens"
        )
            ?: promptTokensDetails?.firstPositiveIntOrNull(
                "cache_creation_input_tokens",
                "cache_creation_tokens",
                "cache_write_tokens"
            )
            ?: jsonObject.firstPositiveIntOrNull(
                "cache_creation_input_tokens",
                "cache_creation_tokens",
                "cache_write_tokens"
            )
            ?: 0
        val cacheMissTokens = inputTokensDetails?.firstPositiveIntOrNull("prompt_cache_miss_tokens")
            ?: promptTokensDetails?.firstPositiveIntOrNull("prompt_cache_miss_tokens")
            ?: jsonObject.firstPositiveIntOrNull("prompt_cache_miss_tokens")
            ?: 0
        val effectivePromptTokens = if (promptTokens > 0) {
            promptTokens + cacheReadTokens + cacheCreationTokens
        } else {
            cacheReadTokens + cacheMissTokens + cacheCreationTokens
        }
        return TokenUsage(
            promptTokens = effectivePromptTokens,
            completionTokens = completionTokens,
            totalTokens = jsonObject["total_tokens"]?.jsonPrimitive?.intOrNull
                ?: (effectivePromptTokens + completionTokens),
            cachedTokens = cacheReadTokens
        )
    }

    private fun JsonObject.firstPositiveIntOrNull(vararg keys: String): Int? {
        for (key in keys) {
            val value = this[key]?.jsonPrimitive?.intOrNull
            if (value != null && value > 0) return value
        }
        return null
    }

    private fun parseStreamFailure(event: PlatformServerEvent.Failure): Throwable {
        val bodySnippet = event.body?.takeIf { it.isNotBlank() }?.take(500)
        val fallback = RuntimeException(
            "Stream failed${event.statusCode?.let { " #$it" }.orEmpty()}: ${event.message.orEmpty()}" +
                (bodySnippet?.let { " (body: $it)" } ?: "")
        )
        val bodyRaw = event.body
        return try {
            if (!bodyRaw.isNullOrBlank()) {
                val bodyElement = Json.parseToJsonElement(bodyRaw)
                bodyElement.parseErrorDetail()
            } else {
                fallback
            }
        } catch (e: Throwable) {
            PlatformLog.w(TAG, "onFailure: failed to parse from $bodyRaw")
            fallback
        }
    }
}

private fun isModelAllowTemperature(model: Model): Boolean {
    return !ModelRegistry.OPENAI_O_MODELS.match(model.modelId) && !ModelRegistry.GPT_5.match(model.modelId)
}

private fun logWarning(message: String) {
    runCatching {
        PlatformLog.w(TAG, message)
    }.onFailure {
        println(message)
    }
}

private fun List<UIMessagePart>.isOnlyTextPart(): Boolean {
    val gonnaSend = filter { it is UIMessagePart.Text || it is UIMessagePart.Image || it is UIMessagePart.Audio }.size
    val texts = filter { it is UIMessagePart.Text }.size
    return gonnaSend == texts && texts == 1
}

private fun List<CustomHeader>.toHeaderMap(): Map<String, String> {
    return filter { it.name.isNotBlank() }.associate { it.name to it.value }
}

private fun Map<String, String>.withAuthAndJson(apiKey: String): Map<String, String> {
    return this + mapOf(
        "Authorization" to "Bearer $apiKey",
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
