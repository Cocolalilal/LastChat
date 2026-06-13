package me.rerere.ai.provider.providers.openai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.configureClientWithProxy
import me.rerere.ai.util.configureReferHeaders
import me.rerere.ai.util.encodeBase64
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.ai.util.parseErrorDetail
import me.rerere.ai.util.stringSafe
import me.rerere.ai.util.toHeaders
import me.rerere.common.http.await
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
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
    private val client: OkHttpClient,
    private val keyRoulette: KeyRoulette
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

        val proxyClient = client.configureClientWithProxy(providerSetting.proxy)

        val request = Request.Builder()
            .url("${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}")
            .headers(params.customHeaders.toHeaders())
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${keyRoulette.next(providerSetting.apiKey)}")
            .configureReferHeaders(providerSetting.baseUrl)
            .build()

        Log.i(TAG, "generateText: ${json.encodeToString(requestBody)}")

        val response = proxyClient.newCall(request).await()
        if (!response.isSuccessful) {
            throw Exception("Failed to get response: ${response.code} ${response.body?.string()}")
        }

        val bodyStr = response.body?.string() ?: ""
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

        val proxyClient = client.configureClientWithProxy(providerSetting.proxy)

        val request = Request.Builder()
            .url("${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}")
            .headers(params.customHeaders.toHeaders())
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${keyRoulette.next(providerSetting.apiKey)}")
            .addHeader("Content-Type", "application/json")
            .configureReferHeaders(providerSetting.baseUrl)
            .build()

        Log.i(TAG, "streamText: ${json.encodeToString(requestBody)}")

        // just for debugging response body
        // println(client.newCall(request).await().body?.string())

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    println("[onEvent] (done) 结束流: $data")
                    close()
                    return
                }
                Log.d(TAG, "onEvent: $data")
                data
                    .trim()
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .map { json.parseToJsonElement(it).jsonObject }
                    .forEach {
                        if (it["error"] != null) {
                            val error = it["error"]!!.parseErrorDetail()
                            throw error
                        }
                        val id = it["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val model = it["model"]?.jsonPrimitive?.contentOrNull ?: ""

                        val choices = it["choices"]?.jsonArray ?: JsonArray(emptyList())
                        val choiceList = buildList {
                            if (choices.isNotEmpty()) {
                                val choice = choices[0].jsonObject
                                val message =
                                    choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject
                                    ?: throw Exception("delta/message is null")
                                val finishReason =
                                    choice["finish_reason"]?.jsonPrimitive?.contentOrNull
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
                        val usage = parseTokenUsage(it["usage"] as? JsonObject)

                        val messageChunk = MessageChunk(
                            id = id,
                            model = model,
                            choices = choiceList,
                            usage = usage
                        )
                        trySend(messageChunk)
                    }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                var exception = t

                t?.printStackTrace()
                println("[onFailure] 发生错误: ${t?.javaClass?.name} ${t?.message} / $response")

                val bodyRaw = response?.body?.stringSafe()
                try {
                    if (!bodyRaw.isNullOrBlank()) {
                        val bodyElement = Json.parseToJsonElement(bodyRaw)
                        println(bodyElement)
                        exception = bodyElement.parseErrorDetail()
                        Log.i(TAG, "onFailure: $exception")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "onFailure: failed to parse from $bodyRaw")
                    e.printStackTrace()
                    exception = e
                } finally {
                    close(exception)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(proxyClient).newEventSource(request, listener)

        awaitClose {
            println("[awaitClose] 关闭eventSource ")
            eventSource.cancel()
        }
    }


    private fun buildChatCompletionRequest(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        providerSetting: ProviderSetting.OpenAI,
        stream: Boolean = false,
    ): JsonObject {
        val host = providerSetting.baseUrl.toHttpUrl().host
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
                    message.getToolResults().forEach { result ->
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
                            .filter { it is UIMessagePart.Text || it is UIMessagePart.Image }
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
                                            part.encodeBase64().onSuccess {
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

                                    is UIMessagePart.Reasoning,
                                    is UIMessagePart.ToolCall -> {
                                        // Reasoning and tool calls are serialized as top-level fields.
                                    }

                                    else -> {
                                        Log.w(
                                            TAG,
                                            "buildMessages: message part not supported: $part"
                                        )
                                        // DO NOTHING
                                    }
                                }
                            }
                        }
                        if (shouldReplayDeepSeekReasoning && message.role == MessageRole.ASSISTANT &&
                            message.parts.none { it is UIMessagePart.Text || it is UIMessagePart.Image }
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
        val normalizedHost = host.lowercase()
        return normalizedHost == "api.openai.com" || normalizedHost == "api.mistral.ai"
    }

    private fun ProviderSetting.OpenAI.promptCachePolicy(host: String, modelId: String): PromptCachePolicy {
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

            normalizedHost == "opencode.ai" ||
                normalizedHost.endsWith(".opencode.ai") ||
                normalizedModelId.startsWith("opencode-go/") -> PromptCachePolicy(
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
                part is UIMessagePart.Text &&
                    part.text.isNotBlank() &&
                    part.text != LEADING_ASSISTANT_COMPATIBILITY_USER_PROMPT
            }
            if (message.role != MessageRole.TOOL && hasCacheableText) index else null
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
        val gonnaSend = filter { it is UIMessagePart.Text || it is UIMessagePart.Image }.size
        val texts = filter { it is UIMessagePart.Text }.size
        return gonnaSend == texts && texts == 1
    }
}
