package me.rerere.ai.provider.providers.openai

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
import kotlinx.serialization.json.add
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
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ProviderProxy
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.ai.util.parseErrorDetail
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.common.http.urlHostOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpProxy
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.common.platform.PlatformMediaEncoder
import kotlin.time.Clock

private const val TAG = "ChatCompletionsAPI"
private const val LEADING_ASSISTANT_COMPATIBILITY_USER_PROMPT =
    "Provider compatibility marker: the conversation begins with the assistant's next message. " +
        "This is not a real user message. Do not answer, quote, or infer user intent from this marker; " +
        "use the later USER messages as the user's words."

private data class PromptCachePolicy(
    val explicitBreakpoints: Boolean,
    val topLevelCacheControl: Boolean,
    val useSingleStableBreakpoint: Boolean = false,
)

class ChatCompletionsAPI(
    private val httpClient: PlatformHttpClient,
    private val keyRoulette: KeyRoulette,
    private val mediaEncoder: PlatformMediaEncoder,
) : OpenAIImpl {
    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        val requestBody =
            buildChatCompletionRequest(
                messages = messages,
                params = params,
                providerSetting = providerSetting
            )

        val encodedRequestBody = json.encodeToString(requestBody)

        PlatformLog.i(TAG, "generateText: $encodedRequestBody")

        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}",
                headers = params.customHeaders.toHeaderMap()
                    .withReferHeaders(providerSetting.baseUrl)
                    .withAuthAndJson(keyRoulette.next(providerSetting.apiKey)),
                body = encodedRequestBody.encodeToByteArray(),
                mediaType = "application/json",
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        if (response.statusCode !in 200..299) {
            throw Exception("Failed to get response: ${response.statusCode} ${response.body.decodeToString()}")
        }

        val bodyStr = response.body.decodeToString()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject

        // 从 JsonObject 中提取必要的信息
        val id = bodyJson["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val model = bodyJson["model"]?.jsonPrimitive?.contentOrNull ?: ""
        val choice = bodyJson["choices"]?.jsonArray?.get(0)?.jsonObject ?: error("choices is null")

        val message = choice["message"]?.jsonObject ?: throw Exception("message is null")
        val finishReason = choice["finish_reason"]
            ?.jsonPrimitive
            ?.content
            ?: "unknown"
        val usage = parseTokenUsage(bodyJson["usage"] as? JsonObject)

        MessageChunk(
            id = id,
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = parseMessage(message),
                    finishReason = finishReason
                )
            ),
            usage = usage
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = callbackFlow {
        val requestBody = buildChatCompletionRequest(
            messages = messages,
            params = params,
            providerSetting = providerSetting,
            stream = true,
        )
        val encodedRequestBody = json.encodeToString(requestBody)
        val request = PlatformHttpRequest(
            method = "POST",
            url = "${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}",
            headers = params.customHeaders.toHeaderMap()
                .withReferHeaders(providerSetting.baseUrl)
                .withAuthAndJson(keyRoulette.next(providerSetting.apiKey)),
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
                        if (event.data == "[DONE]") {
                            close()
                            return@collect
                        }
                        PlatformLog.d(TAG, "onEvent: ${event.data}")
                        try {
                            parseStreamData(event.data).forEach { chunk ->
                                trySend(chunk)
                            }
                        } catch (e: Throwable) {
                            close(e)
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

    private fun parseStreamData(data: String): List<MessageChunk> {
        return data
            .trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .map { json.parseToJsonElement(it).jsonObject }
            .map { jsonObject ->
                val error = jsonObject["error"]
                if (error != null) {
                    throw error.parseErrorDetail()
                }
                val id = jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val model = jsonObject["model"]?.jsonPrimitive?.contentOrNull ?: ""

                val choices = jsonObject["choices"]?.jsonArray ?: JsonArray(emptyList())
                val choiceList = buildList {
                    if (choices.isNotEmpty()) {
                        val choice = choices[0].jsonObject
                        val message = choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject
                            ?: throw Exception("delta/message is null")
                        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
                            ?: "unknown"
                        add(
                            UIMessageChoice(
                                index = 0,
                                delta = parseMessage(message),
                                message = null,
                                finishReason = finishReason,
                            )
                        )
                    }
                }
                val usage = parseTokenUsage(jsonObject["usage"] as? JsonObject)

                MessageChunk(
                    id = id,
                    model = model,
                    choices = choiceList,
                    usage = usage
                )
            }
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



    private fun buildChatCompletionRequest(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        providerSetting: ProviderSetting.OpenAI,
        stream: Boolean = false,
    ): JsonObject {
        val host = providerSetting.baseUrl.urlHostOrNull().orEmpty()
        return buildJsonObject {
            put("model", params.model.modelId)
            if (host == "openrouter.ai" && !params.sessionId.isNullOrBlank()) {
                put("session_id", params.sessionId)
            }
            if (providerSetting.shouldIncludePromptCacheKey(host) && !params.sessionId.isNullOrBlank()) {
                put("prompt_cache_key", params.sessionId)
            }
            val processedMessages = if (params.model.abilities.contains(ModelAbility.REASONING) && 
                ReasoningLevel.fromBudgetTokens(params.thinkingBudget) == ReasoningLevel.OFF) {
                // If reasoning is OFF but it's a reasoning model, inject an empty think tag as an assistant prefill
                // This tricks models like Qwen 3.5 into believing they have already completed their reasoning phase
                val mutableMessages = messages.toMutableList()
                mutableMessages.add(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("<think></think>\n"))
                    )
                )
                mutableMessages
            } else {
                messages
            }

            val safeMessages = mutableListOf<UIMessage>()
            var lastNonSystemRole: MessageRole? = null
            for (msg in processedMessages) {
                if (msg.role == MessageRole.ASSISTANT && lastNonSystemRole == null) {
                    safeMessages.add(
                        UIMessage(
                            role = MessageRole.USER,
                            parts = listOf(UIMessagePart.Text(LEADING_ASSISTANT_COMPATIBILITY_USER_PROMPT))
                        )
                    )
                    lastNonSystemRole = MessageRole.USER
                }
                safeMessages.add(msg)
                if (msg.role != MessageRole.SYSTEM) {
                    lastNonSystemRole = msg.role
                }
            }

            val promptCachePolicy = providerSetting.promptCachePolicy(host, params.model.modelId)
            if (promptCachePolicy.topLevelCacheControl) {
                put("cache_control", buildPromptCacheControl())
            }

            put(
                "messages",
                buildMessages(
                    messages = safeMessages,
                    providerSetting = providerSetting,
                    host = host,
                    modelId = params.model.modelId,
                    promptCachePolicy = promptCachePolicy
                )
            )

            if (isModelAllowTemperature(params.model)) {
                if (params.temperature != null) put("temperature", params.temperature)
                if (params.topP != null) put("top_p", params.topP)
            }
            if (params.maxTokens != null) put("max_tokens", params.maxTokens)

            put("stream", stream)
            if (stream) {
                // Some providers don't support stream_options
                if (providerSetting.shouldIncludeStreamOptions(host)) {
                    put("stream_options", buildJsonObject {
                        put("include_usage", true)
                    })
                }
            }

            // open router适配
            if(providerSetting.shouldIncludeImageModalities(host)) {
                if(params.model.outputModalities.contains(Modality.IMAGE)) {
                    put("modalities", buildJsonArray {
                        add("image")
                        add("text")
                    })
                }
            }

            if (params.model.abilities.contains(ModelAbility.REASONING)) {
                val level = ReasoningLevel.fromBudgetTokens(params.thinkingBudget)
                val catalogBodies = params.model.reasoningBehavior?.bodiesFor(level)
                    ?.takeIf { it.isNotEmpty() }
                    ?: providerSetting.reasoningBehavior?.bodiesFor(level)?.takeIf { it.isNotEmpty() }

                if (catalogBodies != null) {
                    catalogBodies.forEach { body ->
                        if (body.key.isNotBlank()) {
                            put(body.key, body.value)
                        }
                    }
                } else when (host) {
                    "openrouter.ai" -> {
                        // https://openrouter.ai/docs/use-cases/reasoning-tokens
                        put("reasoning", buildJsonObject {
                            if (level != ReasoningLevel.AUTO) put("max_tokens", params.thinkingBudget ?: 0)
                            if (!level.isEnabled) {
                                put("enabled", false)
                            }
                        })
                    }

                    "dashscope.aliyuncs.com" -> {
                        // 阿里云百炼
                        // https://bailian.console.aliyun.com/console?tab=doc#/doc/?type=model&url=https%3A%2F%2Fhelp.aliyun.com%2Fdocument_detail%2F2870973.html&renderType=iframe
                        put("enable_thinking", level.isEnabled)
                        if (level != ReasoningLevel.AUTO) put("thinking_budget", params.thinkingBudget ?: 0)
                    }

                    "ark.cn-beijing.volces.com" -> {
                        // 豆包 (火山)
                        put("thinking", buildJsonObject {
                            put("type", if (!level.isEnabled) "disabled" else "enabled")
                        })
                    }

                    "api.mistral.ai" -> {
                        // Mistral 不支持
                    }

                    "chat.intern-ai.org.cn" -> {
                        // 书生
                        // https://internlm.intern-ai.org.cn/api/document?lang=zh
                        put("thinking_mode", level.isEnabled)
                    }

                    "api.siliconflow.cn" -> {
                        // https://docs.siliconflow.cn/cn/userguide/capabilities/reasoning#3-1-api-%E5%8F%82%E6%95%B0
                        val modelId = params.model.modelId
                        if(modelId.contains("DeepSeek-V3.1") || modelId.contains("GLM-4.5") || modelId.contains("Qwen3-8B")) {
                            put("enable_thinking", level.isEnabled)
                        }
                    }

                    "open.bigmodel.cn" -> {
                        put("thinking", buildJsonObject {
                            put("type", if (!level.isEnabled) "disabled" else "enabled")
                        })
                    }

                    else -> {
                        // OpenAI 官方
                        // 文档中，只支持 "low", "medium", "high"
                        if (level != ReasoningLevel.AUTO && level != ReasoningLevel.OFF) {
                            put("reasoning_effort", if(level.effort == "minimal") "low" else level.effort)
                        } else if (level == ReasoningLevel.OFF) {
                            // Suppress reasoning mode on local fast-tier LLMs (e.g. LM Studio, vLLM, Ollama)
                            // This acts as a Jinja template override for models like Qwen 3.5
                            put("chat_template_kwargs", buildJsonObject {
                                put("enable_thinking", false)
                            })
                        }
                    }
                }
            }

            if (params.model.abilities.contains(ModelAbility.TOOL) && params.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    params.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put(
                                    "parameters",
                                    json.encodeToJsonElement(
                                        tool.parameters()
                                    )
                                )
                            })
                        })
                    }
                }
            }
        }.mergeCustomBody(params.customBody)
    }

    private fun isModelAllowTemperature(model: Model): Boolean {
        return !ModelRegistry.OPENAI_O_MODELS.match(model.modelId) && !ModelRegistry.GPT_5.match(model.modelId)
    }

    private fun buildMessages(
        messages: List<UIMessage>,
        providerSetting: ProviderSetting.OpenAI,
        host: String,
        modelId: String,
        promptCachePolicy: PromptCachePolicy
    ) = buildJsonArray {
        val shouldReplayDeepSeekReasoning = providerSetting.shouldReplayReasoningContent(host, modelId)
        val uploadableMessages = messages.filter { it.isValidToUpload() }
        val cacheBreakpointIndices = uploadableMessages.cacheBreakpointIndices(promptCachePolicy)
        uploadableMessages
            .forEachIndexed { index, message ->
                if (message.role == MessageRole.TOOL) {
                    val toolResults = message.getToolResults()
                    toolResults.forEachIndexed { resultIndex, result ->
                        add(buildJsonObject {
                            put("role", "tool")
                            put("name", result.toolName)
                            put("tool_call_id", result.toolCallId)
                            // Zhipu AI requires content to be a JSON object, not a string
                            if (host == "open.bigmodel.cn") {
                                put("content", result.content)
                            } else {
                                put("content", json.encodeToString(result.content))
                            }
                            
                            val shouldCacheMessage = index in cacheBreakpointIndices
                            if (shouldCacheMessage && resultIndex == toolResults.lastIndex) {
                                put("cache_control", buildPromptCacheControl())
                            }
                        })
                    }
                    return@forEachIndexed
                }
                add(buildJsonObject {
                    // role
                    put("role", JsonPrimitive(message.role.name.lowercase()))

                    // content
                    val shouldCacheMessage = index in cacheBreakpointIndices
                    if (message.parts.isOnlyTextPart() && !shouldCacheMessage) {
                        // 如果只是纯文本，直接赋值给content
                        put(
                            "content",
                            message.parts.filterIsInstance<UIMessagePart.Text>().first().text
                        )
                    } else {
                        // 否则，使用parts构建
                        val uploadableParts = message.parts
                            .filter { it is UIMessagePart.Text || it is UIMessagePart.Image || it is UIMessagePart.Audio }
                        val cacheableTextPartIndex = if (shouldCacheMessage) {
                            uploadableParts.indexOfLast { part ->
                                part is UIMessagePart.Text && part.text.isNotBlank()
                            }
                        } else {
                            -1
                        }
                        putJsonArray("content") {
                            uploadableParts.forEachIndexed { partIndex, part ->
                                when (part) {
                                    is UIMessagePart.Text -> {
                                        add(buildJsonObject {
                                            put("type", "text")
                                            put("text", part.text)
                                            if (partIndex == cacheableTextPartIndex) {
                                                put("cache_control", buildPromptCacheControl())
                                            }
                                        })
                                    }

                                    is UIMessagePart.Image -> {
                                        add(buildJsonObject {
                                            mediaEncoder.encodeImage(part.url).onSuccess {
                                                put("type", "image_url")
                                                put("image_url", buildJsonObject {
                                                    put("url", it)
                                                })
                                            }.onFailure {
                                                it.printStackTrace()
                                                println("encode image failed: ${part.url}")

                                                put("type", "text")
                                                put("text", "")
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

                                                put("type", "text")
                                                put("text", "")
                                            }
                                        })
                                    }

                                    is UIMessagePart.Reasoning,
                                    is UIMessagePart.ToolCall -> {
                                        // Reasoning and tool calls are serialized as top-level fields.
                                    }

                                    else -> {
                                        PlatformLog.w(
                                            TAG,
                                            "buildMessages: message part not supported: $part"
                                        )
                                        // DO NOTHING
                                    }
                                }
                            }
                        }
                        if (shouldReplayDeepSeekReasoning && message.role == MessageRole.ASSISTANT &&
                            message.parts.none { it is UIMessagePart.Text || it is UIMessagePart.Image || it is UIMessagePart.Audio }
                        ) {
                            put("content", "")
                        }
                    }

                    if (shouldReplayDeepSeekReasoning && message.role == MessageRole.ASSISTANT) {
                        message.parts
                            .filterIsInstance<UIMessagePart.Reasoning>()
                            .joinToString(separator = "\n") { it.reasoning }
                            .takeIf { it.isNotBlank() }
                            ?.let { reasoning ->
                                put("reasoning_content", reasoning)
                            }
                    }

                    // tool_calls
                    message.getToolCalls()
                        .takeIf { it.isNotEmpty() }
                        ?.let { toolCalls ->
                            put("tool_calls", buildJsonArray {
                                toolCalls.forEach { toolCall ->
                                    add(buildJsonObject {
                                        put("id", toolCall.toolCallId)
                                        put("type", "function")
                                        put("function", buildJsonObject {
                                            put("name", toolCall.toolName)
                                            put("arguments", toolCall.arguments)
                                        })
                                    })
                                }
                            })
                        }
                })
            }
    }

    private fun isDeepSeekCompatible(host: String, modelId: String): Boolean {
        val normalizedHost = host.lowercase()
        val normalizedModelId = modelId.lowercase()
        return normalizedHost == "api.deepseek.com" ||
            normalizedHost.endsWith(".deepseek.com") ||
            normalizedModelId.contains("deepseek")
    }

    private fun ProviderSetting.OpenAI.shouldIncludeStreamOptions(host: String): Boolean {
        return when (streamOptionsMode) {
            OpenAICompatibilityMode.ENABLED -> true
            OpenAICompatibilityMode.DISABLED -> false
            OpenAICompatibilityMode.AUTO -> host != "api.mistral.ai" && host != "open.bigmodel.cn"
        }
    }

    private fun ProviderSetting.OpenAI.shouldIncludeImageModalities(host: String): Boolean {
        return when (imageResponseModalitiesMode) {
            OpenAICompatibilityMode.ENABLED -> true
            OpenAICompatibilityMode.DISABLED -> false
            OpenAICompatibilityMode.AUTO -> host == "openrouter.ai"
        }
    }

    private fun ProviderSetting.OpenAI.shouldReplayReasoningContent(host: String, modelId: String): Boolean {
        return when (reasoningContentReplayMode) {
            OpenAICompatibilityMode.ENABLED -> true
            OpenAICompatibilityMode.DISABLED -> false
            OpenAICompatibilityMode.AUTO -> isDeepSeekCompatible(host, modelId)
        }
    }

    private fun ProviderSetting.OpenAI.shouldIncludePromptCacheKey(host: String): Boolean {
        if (promptCacheMode == OpenAICompatibilityMode.DISABLED) return false
        val normalizedHost = host.lowercase()
        return normalizedHost == "api.openai.com" ||
            normalizedHost == "api.mistral.ai" ||
            normalizedHost == "api.deepseek.com" ||
            normalizedHost == "open.bigmodel.cn" ||
            normalizedHost == "opencode.ai" ||
            normalizedHost.endsWith(".opencode.ai")
    }

    private fun ProviderSetting.OpenAI.promptCachePolicy(host: String, modelId: String): PromptCachePolicy {
        if (promptCacheMode == OpenAICompatibilityMode.ENABLED) {
            return PromptCachePolicy(
                explicitBreakpoints = true,
                topLevelCacheControl = false
            )
        }
        if (promptCacheMode == OpenAICompatibilityMode.DISABLED) {
            return PromptCachePolicy(
                explicitBreakpoints = false,
                topLevelCacheControl = false
            )
        }
        val normalizedHost = host.lowercase()
        val normalizedModelId = modelId.lowercase()
        return when {
            normalizedHost == "openrouter.ai" -> PromptCachePolicy(
                explicitBreakpoints = true,
                topLevelCacheControl = false,
                useSingleStableBreakpoint = normalizedModelId.contains("gemini")
            )

            normalizedHost == "dashscope.aliyuncs.com" &&
                (normalizedModelId.contains("qwen") || normalizedModelId.contains("deepseek")) -> PromptCachePolicy(
                explicitBreakpoints = true,
                topLevelCacheControl = false
            )

            else -> PromptCachePolicy(
                explicitBreakpoints = false,
                topLevelCacheControl = false
            )
        }
    }

    private fun buildPromptCacheControl() = buildJsonObject {
        put("type", "ephemeral")
    }

    private fun List<UIMessage>.cacheBreakpointIndices(policy: PromptCachePolicy): Set<Int> {
        if (!policy.explicitBreakpoints) return emptySet()

        val eligibleIndices = mapIndexedNotNull { index, message ->
            val hasCacheableText = message.parts.any { part ->
                (part is UIMessagePart.Text &&
                    part.text.isNotBlank() &&
                    part.text != LEADING_ASSISTANT_COMPATIBILITY_USER_PROMPT) ||
                part is UIMessagePart.ToolCall
            } || message.role == MessageRole.TOOL
            if (hasCacheableText) index else null
        }
        if (eligibleIndices.isEmpty()) return emptySet()

        if (policy.useSingleStableBreakpoint) {
            val lastUserIndex = indexOfLast { it.role == MessageRole.USER }
            return eligibleIndices
                .filter { it < lastUserIndex }
                .lastOrNull()
                ?.let { setOf(it) }
                ?: setOf(eligibleIndices.last())
        }

        return eligibleIndices.takeLast(4).toSet()
    }

    private fun parseMessage(jsonObject: JsonObject): UIMessage {
        val role = MessageRole.valueOf(
            jsonObject["role"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "ASSISTANT"
        )

        // 也许支持其他模态的输出content? 暂时只支持文本吧
        val content = jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val reasoning = jsonObject["reasoning_content"]?.jsonPrimitive?.contentOrNull
            ?: jsonObject["reasoning"]?.jsonPrimitive?.contentOrNull
            ?: jsonObject["thinking"]?.jsonPrimitive?.contentOrNull
        val toolCalls = jsonObject["tool_calls"] as? JsonArray ?: JsonArray(emptyList())
        val images = jsonObject["images"] as? JsonArray ?: JsonArray(emptyList())

        return UIMessage(
            role = role,
            parts = buildList {
                if (!reasoning.isNullOrEmpty()) {
                    add(
                        UIMessagePart.Reasoning(
                            reasoning = reasoning,
                            createdAt = Clock.System.now(),
                            finishedAt = null
                        )
                    )
                }
                toolCalls.forEach { toolCalls ->
                    val type = toolCalls.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                    if (!type.isNullOrEmpty() && type != "function") error("tool call type not supported: $type")
                    val toolCallId = toolCalls.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    val toolName =
                        toolCalls.jsonObject["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    val arguments =
                        toolCalls.jsonObject["function"]?.jsonObject?.get("arguments")?.jsonPrimitive?.contentOrNull
                    add(
                        UIMessagePart.ToolCall(
                            toolCallId = toolCallId ?: "",
                            toolName = toolName ?: "",
                            arguments = arguments ?: ""
                        )
                    )
                }
                add(UIMessagePart.Text(content))
                images.forEach { image ->
                    val imageObject = image.jsonObjectOrNull ?: return@forEach
                    val type = imageObject["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (type != "image_url") return@forEach
                    val url = imageObject["image_url"]?.jsonObjectOrNull?.get("url")?.jsonPrimitive?.contentOrNull ?: return@forEach
                    require(url.startsWith("data:image")) { "Only data uri is supported" }
                    add(UIMessagePart.Image(url.substringAfter("data:image/png;base64,")))
                }
            },
            annotations = parseAnnotations(
                jsonArray = jsonObject["annotations"]?.jsonArrayOrNull ?: JsonArray(
                    emptyList()
                )
            ),
        )
    }

    private fun parseAnnotations(jsonArray: JsonArray): List<UIMessageAnnotation> {
        return jsonArray.map { element ->
            val type =
                element.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: error("type is null")
            when (type) {
                "url_citation" -> {
                    UIMessageAnnotation.UrlCitation(
                        title = element.jsonObject["url_citation"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                            ?: "",
                        url = element.jsonObject["url_citation"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: "",
                    )
                }

                else -> error("unknown annotation type: $type")
            }
        }
    }

    private fun parseTokenUsage(jsonObject: JsonObject?): TokenUsage? {
        if (jsonObject == null) return null
        val promptTokens = jsonObject["prompt_tokens"]?.jsonPrimitive?.intOrNull
        val completionTokens = jsonObject["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
        val promptTokensDetails = jsonObject["prompt_tokens_details"]?.jsonObjectOrNull
        val inputTokensDetails = jsonObject["input_tokens_details"]?.jsonObjectOrNull
        val cacheCreationTokens = promptTokensDetails?.firstPositiveIntOrNull(
            "cache_creation_input_tokens",
            "cache_creation_tokens",
            "cache_write_tokens"
        )
            ?: inputTokensDetails?.firstPositiveIntOrNull(
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
        val cacheReadTokens = promptTokensDetails?.firstPositiveIntOrNull(
            "cached_tokens",
            "cache_read_input_tokens",
            "cache_read_tokens",
            "prompt_cache_hit_tokens"
        )
            ?: inputTokensDetails?.firstPositiveIntOrNull(
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
        val cacheMissTokens = promptTokensDetails?.firstPositiveIntOrNull("prompt_cache_miss_tokens")
            ?: inputTokensDetails?.firstPositiveIntOrNull("prompt_cache_miss_tokens")
            ?: jsonObject.firstPositiveIntOrNull("prompt_cache_miss_tokens")
            ?: 0
        val inputTokens = jsonObject["input_tokens"]?.jsonPrimitive?.intOrNull
        val effectivePromptTokens = promptTokens
            ?: inputTokens?.let { it + cacheCreationTokens + cacheReadTokens }
            ?: (cacheReadTokens + cacheMissTokens).takeIf { it > 0 }
            ?: 0
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
}
