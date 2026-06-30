package me.rerere.ai.provider.providers.openai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.ReasoningRequestBehavior
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformMediaEncoder
import me.rerere.common.platform.PlatformServerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIReasoningRequestTest {
    private val responseHttpClient = object : PlatformHttpClient {
        override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
            error("Network is not used by these reflection tests")
        }

        override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
    }
    private val mediaEncoder = object : PlatformMediaEncoder {
        override fun encodeImage(url: String, withPrefix: Boolean): Result<String> = Result.success(url)

        override fun encodeVideo(url: String, withPrefix: Boolean): Result<String> = Result.success(url)

        override fun encodeAudio(url: String, withPrefix: Boolean): Result<String> = Result.success(url)
    }
    private val providerSetting = ProviderSetting.OpenAI(
        apiKey = "test-key",
        baseUrl = "https://example.com/v1",
    )
    private val reasoningModel = Model(
        modelId = "qwen3-32b",
        displayName = "Qwen 3",
        abilities = listOf(ModelAbility.REASONING),
    )
    private val deepSeekReasoningModel = Model(
        modelId = "deepseek-v4-pro",
        displayName = "DeepSeek V4 Pro",
        abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
    )
    private val messages = listOf(UIMessage.user("Hello"))

    @Test
    fun chatCompletionsOmitsReasoningEffortWhenReasoningIsOff() {
        val body = chatCompletionsBody(thinkingBudget = 0)

        assertNull(body["reasoning_effort"])
    }

    @Test
    fun chatCompletionsKeepsAutoAutomatic() {
        val body = chatCompletionsBody(thinkingBudget = null)

        assertNull(body["reasoning_effort"])
    }

    @Test
    fun chatCompletionsMapsExplicitReasoningLevels() {
        val expected = mapOf(
            1_024 to "low",
            16_000 to "medium",
            32_000 to "high",
        )

        expected.forEach { (budget, effort) ->
            val body = chatCompletionsBody(thinkingBudget = budget)
            assertEquals(effort, body["reasoning_effort"]?.jsonPrimitive?.contentOrNull)
        }
    }

    @Test
    fun chatCompletionsUsesProviderCustomReasoningPayloadBeforeHostDefaults() {
        val body = chatCompletionsBody(
            messages = messages,
            model = reasoningModel,
            providerSetting = providerSetting.copy(
                baseUrl = "https://openrouter.ai/api/v1",
                reasoningBehavior = ReasoningRequestBehavior(
                    low = listOf(CustomBody("enable_thinking", JsonPrimitive(true)))
                )
            ),
            thinkingBudget = 1_024,
        )

        assertEquals("true", body["enable_thinking"]?.jsonPrimitive?.contentOrNull)
        assertFalse(body.containsKey("reasoning"))
        assertNull(body["reasoning_effort"])
    }

    @Test
    fun responseApiOmitsReasoningPayloadWhenReasoningIsOff() {
        val body = responseApiBody(thinkingBudget = 0)

        assertFalse(body.containsKey("reasoning"))
    }

    @Test
    fun responseApiKeepsAutoReasoningWithoutExplicitEffort() {
        val body = responseApiBody(thinkingBudget = null)
        val reasoning = body["reasoning"]?.jsonObject

        assertEquals("auto", reasoning?.get("summary")?.jsonPrimitive?.contentOrNull)
        assertNull(reasoning?.get("effort"))
    }

    @Test
    fun responseApiMapsExplicitReasoningLevels() {
        val expected = mapOf(
            1_024 to "low",
            16_000 to "medium",
            32_000 to "high",
        )

        expected.forEach { (budget, effort) ->
            val body = responseApiBody(thinkingBudget = budget)
            val reasoning = body["reasoning"]?.jsonObject
            assertEquals("auto", reasoning?.get("summary")?.jsonPrimitive?.contentOrNull)
            assertEquals(effort, reasoning?.get("effort")?.jsonPrimitive?.contentOrNull)
        }
    }

    @Test
    fun responseApiKeepsContentForToolCallOnlyAssistantMessages() {
        val body = responseApiBody(
            messages = listOf(
                UIMessage.user("Hello"),
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.ToolCall("call_1", "search_web", "{}"))
                )
            )
        )

        val input = body["input"]?.jsonArray ?: error("input is missing")
        assertEquals(3, input.size)
        assertEquals("assistant", input[1].jsonObject["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals("", input[1].jsonObject["content"]?.jsonPrimitive?.contentOrNull)
        assertEquals("function_call", input[2].jsonObject["type"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun chatCompletionsReplaysDeepSeekReasoningContent() {
        val body = chatCompletionsBody(
            messages = listOf(
                UIMessage.user("Hello"),
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(
                        UIMessagePart.Reasoning("DeepSeek private reasoning"),
                        UIMessagePart.Text("Visible answer")
                    )
                )
            ),
            model = deepSeekReasoningModel,
            providerSetting = providerSetting.copy(baseUrl = "https://api.deepseek.com")
        )

        val assistantMessage = body["messages"]?.jsonArray?.get(1)?.jsonObject
            ?: error("assistant message is missing")
        assertEquals("Visible answer", assistantMessage["content"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            "DeepSeek private reasoning",
            assistantMessage["reasoning_content"]?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun chatCompletionsReplaysDeepSeekReasoningContentWithToolCalls() {
        val body = chatCompletionsBody(
            messages = listOf(
                UIMessage.user("Hello"),
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(
                        UIMessagePart.Reasoning("Need a tool"),
                        UIMessagePart.ToolCall("call_1", "get_weather", "{\"city\":\"Paris\"}")
                    )
                )
            ),
            model = deepSeekReasoningModel,
            providerSetting = providerSetting.copy(baseUrl = "https://opencode.example.com/v1")
        )

        val assistantMessage = body["messages"]?.jsonArray?.get(1)?.jsonObject
            ?: error("assistant message is missing")
        assertEquals("", assistantMessage["content"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Need a tool", assistantMessage["reasoning_content"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1, assistantMessage["tool_calls"]?.jsonArray?.size)
    }

    @Test
    fun chatCompletionsOmitsReasoningContentForNonDeepSeekProviders() {
        val body = chatCompletionsBody(
            messages = listOf(
                UIMessage.user("Hello"),
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(
                        UIMessagePart.Reasoning("Do not serialize this"),
                        UIMessagePart.Text("Visible answer")
                    )
                )
            ),
            model = reasoningModel,
            providerSetting = providerSetting
        )

        val assistantMessage = body["messages"]?.jsonArray?.get(1)?.jsonObject
            ?: error("assistant message is missing")
        assertFalse(assistantMessage.containsKey("reasoning_content"))
    }

    @Test
    fun chatCompletionsUsesExplicitMarkerBeforeLeadingAssistantMessage() {
        val body = chatCompletionsBody(
            messages = listOf(
                UIMessage.assistant("Hey, I was thinking about you."),
                UIMessage.user("oh?")
            ),
            model = reasoningModel,
            providerSetting = providerSetting
        )

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val marker = messages[0].jsonObject
        val opening = messages[1].jsonObject
        val realUser = messages[2].jsonObject

        assertEquals("user", marker["role"]?.jsonPrimitive?.contentOrNull)
        assertTrue(marker["content"]?.jsonPrimitive?.contentOrNull?.contains("not a real user message") == true)
        assertFalse(marker["content"]?.jsonPrimitive?.contentOrNull == "...")
        assertEquals("assistant", opening["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Hey, I was thinking about you.", opening["content"]?.jsonPrimitive?.contentOrNull)
        assertEquals("user", realUser["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals("oh?", realUser["content"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun chatCompletionsAddsOpenRouterPromptCacheBreakpoints() {
        val body = chatCompletionsBody(
            messages = listOf(
                UIMessage.system("Stable system prompt"),
                UIMessage.user("First user turn"),
                UIMessage.assistant("First answer"),
                UIMessage.user("Current user turn")
            ),
            model = reasoningModel.copy(modelId = "anthropic/claude-sonnet-4.5"),
            providerSetting = providerSetting.copy(baseUrl = "https://openrouter.ai/api/v1")
        )

        assertFalse(body.containsKey("cache_control"))

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val systemContent = messages[0].jsonObject["content"]?.jsonArray ?: error("system content is missing")
        assertEquals(
            "ephemeral",
            systemContent[0].jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun chatCompletionsUsesStableBreakpointForOpenRouterGemini() {
        val body = chatCompletionsBody(
            messages = listOf(
                UIMessage.system("Stable system prompt"),
                UIMessage.user("Current question")
            ),
            model = reasoningModel.copy(modelId = "google/gemini-2.5-pro"),
            providerSetting = providerSetting.copy(baseUrl = "https://openrouter.ai/api/v1")
        )

        assertFalse(body.containsKey("cache_control"))

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val systemContent = messages[0].jsonObject["content"]?.jsonArray ?: error("system content is missing")
        assertEquals(
            "ephemeral",
            systemContent[0].jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        )
        assertEquals("Current question", messages[1].jsonObject["content"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun chatCompletionsDoesNotInjectCacheControlForOpenCodeGo() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel.copy(modelId = "glm-5.2"),
            providerSetting = providerSetting.copy(baseUrl = "https://opencode.ai/zen/go/v1")
        )

        assertFalse(body.containsKey("cache_control"))

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val systemContent = messages[0].jsonObject["content"]
        val userContent = messages[1].jsonObject["content"]
        // No cache_control injection — relies on automatic prefix caching
        assertFalse(systemContent is JsonArray)
        assertFalse(userContent is JsonArray)
    }

    @Test
    fun chatCompletionsSendsPromptCacheKeyForOpenCodeGo() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel.copy(modelId = "glm-5.2"),
            providerSetting = providerSetting.copy(baseUrl = "https://opencode.ai/zen/go/v1"),
            sessionId = "conversation-123",
        )

        assertEquals("conversation-123", body["prompt_cache_key"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun chatCompletionsSendsPromptCacheKeyForDeepSeek() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel,
            providerSetting = providerSetting.copy(baseUrl = "https://api.deepseek.com/v1"),
            sessionId = "conversation-123",
        )

        assertEquals("conversation-123", body["prompt_cache_key"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun chatCompletionsSendsPromptCacheKeyForZhipu() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel,
            providerSetting = providerSetting.copy(baseUrl = "https://open.bigmodel.cn/api/paas/v4"),
            sessionId = "conversation-123",
        )

        assertEquals("conversation-123", body["prompt_cache_key"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun chatCompletionsPromptCacheModeEnabledForcesBreakpoints() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel,
            providerSetting = providerSetting.copy(
                baseUrl = "https://generic.example.com/v1",
                promptCacheMode = OpenAICompatibilityMode.ENABLED,
            )
        )

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val userContent = messages[1].jsonObject["content"]?.jsonArray ?: error("user content should be array when breakpoints enabled")
        assertEquals(
            "ephemeral",
            userContent[0].jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun chatCompletionsPromptCacheModeDisabledSuppressesBreakpointsAndCacheKey() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel.copy(modelId = "anthropic/claude-sonnet-4.5"),
            providerSetting = providerSetting.copy(
                baseUrl = "https://openrouter.ai/api/v1",
                promptCacheMode = OpenAICompatibilityMode.DISABLED,
            ),
            sessionId = "conversation-123",
        )

        assertFalse(body.containsKey("cache_control"))
        assertFalse(body.containsKey("prompt_cache_key"))

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val systemContent = messages[0].jsonObject["content"]
        // No cache_control injection when disabled
        if (systemContent is JsonArray) {
            assertNull(systemContent[0].jsonObject["cache_control"])
        }
    }

    @Test
    fun chatCompletionsAddsOpenRouterSessionId() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel.copy(modelId = "google/gemini-2.5-pro"),
            providerSetting = providerSetting.copy(baseUrl = "https://openrouter.ai/api/v1"),
            sessionId = "conversation-123",
        )

        assertEquals("conversation-123", body["session_id"]?.jsonPrimitive?.contentOrNull)
        assertFalse(body.containsKey("prompt_cache_key"))
    }

    @Test
    fun chatCompletionsAddsPromptCacheKeyForOpenAI() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel,
            providerSetting = providerSetting.copy(baseUrl = "https://api.openai.com/v1"),
            sessionId = "conversation-123",
        )

        assertEquals("conversation-123", body["prompt_cache_key"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun chatCompletionsAddsPromptCacheKeyForMistral() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel.copy(modelId = "mistral-large-latest"),
            providerSetting = providerSetting.copy(baseUrl = "https://api.mistral.ai/v1"),
            sessionId = "conversation-123",
        )

        assertEquals("conversation-123", body["prompt_cache_key"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun chatCompletionsDoesNotLeakSessionIdToGenericProviders() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.user("Hello")),
            model = reasoningModel,
            providerSetting = providerSetting,
            sessionId = "conversation-123",
        )

        assertFalse(body.containsKey("session_id"))
        assertFalse(body.containsKey("prompt_cache_key"))
    }

    @Test
    fun chatCompletionsAddsDashScopePromptCacheBreakpoints() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel.copy(modelId = "qwen3.7-max"),
            providerSetting = providerSetting.copy(baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1")
        )

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val userContent = messages[1].jsonObject["content"]?.jsonArray ?: error("user content is missing")
        assertEquals(
            "ephemeral",
            userContent[0].jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun chatCompletionsAddsOpenRouterPromptCacheBreakpointsForAnyModel() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel.copy(modelId = "new-provider/new-model-family:free"),
            providerSetting = providerSetting.copy(baseUrl = "https://openrouter.ai/api/v1")
        )

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val userContent = messages[1].jsonObject["content"]?.jsonArray ?: error("user content is missing")
        assertEquals(
            "ephemeral",
            userContent[0].jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun chatCompletionsLeavesGenericProviderPromptShapeUnchanged() {
        val body = chatCompletionsBody(
            messages = listOf(UIMessage.system("Stable system prompt"), UIMessage.user("Hello")),
            model = reasoningModel,
            providerSetting = providerSetting
        )

        assertFalse(body.containsKey("cache_control"))

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        assertEquals("Stable system prompt", messages[0].jsonObject["content"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Hello", messages[1].jsonObject["content"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun parseChatCompletionsUsageReadsAnthropicStyleCacheFields() {
        val usage = parseChatCompletionsUsage(
            buildJsonObject {
                put("input_tokens", 100)
                put("cache_creation_input_tokens", 200)
                put("cache_read_input_tokens", 300)
                put("completion_tokens", 50)
            }
        ) ?: error("usage is missing")

        assertEquals(600, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(300, usage.cachedTokens)
        assertEquals(650, usage.totalTokens)
    }

    @Test
    fun parseChatCompletionsUsageReadsCacheReadTokenAliases() {
        val usage = parseChatCompletionsUsage(
            buildJsonObject {
                put("prompt_tokens", 1000)
                put("completion_tokens", 50)
                put("prompt_tokens_details", buildJsonObject {
                    put("cache_read_tokens", 400)
                    put("cache_write_tokens", 300)
                })
            }
        ) ?: error("usage is missing")

        assertEquals(1000, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(400, usage.cachedTokens)
        assertEquals(1050, usage.totalTokens)
    }

    @Test
    fun parseChatCompletionsUsageReadsDeepSeekCacheFields() {
        val usage = parseChatCompletionsUsage(
            buildJsonObject {
                put("prompt_cache_hit_tokens", 400)
                put("prompt_cache_miss_tokens", 600)
                put("completion_tokens", 50)
            }
        ) ?: error("usage is missing")

        assertEquals(1000, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(400, usage.cachedTokens)
        assertEquals(1050, usage.totalTokens)
    }

    @Test
    fun parseResponseApiUsageReadsCacheReadTokenAliases() {
        val usage = parseResponseUsage(
            buildJsonObject {
                put("input_tokens", 1000)
                put("output_tokens", 50)
                put("input_tokens_details", buildJsonObject {
                    put("cache_read_tokens", 400)
                    put("cache_write_tokens", 300)
                })
            }
        ) ?: error("usage is missing")

        assertEquals(1700, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(400, usage.cachedTokens)
        assertEquals(1750, usage.totalTokens)
    }

    @Test
    fun responseApiAddsPromptCacheKeyForOpenAI() {
        val body = responseApiBody(
            thinkingBudget = null,
            providerSetting = providerSetting.copy(baseUrl = "https://api.openai.com/v1"),
            sessionId = "conversation-123",
        )

        assertEquals("conversation-123", body["prompt_cache_key"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun parseMessageTreatsThinkingFieldAsReasoning() {
        val message = parseOpenAIMessage(
            JsonObject(
                mapOf(
                    "role" to JsonPrimitive("assistant"),
                    "thinking" to JsonPrimitive("NVIDIA streamed reasoning"),
                    "content" to JsonPrimitive("Final answer")
                )
            )
        )

        val reasoning = message.parts.filterIsInstance<UIMessagePart.Reasoning>().single()
        val text = message.parts.filterIsInstance<UIMessagePart.Text>().single()
        assertEquals("NVIDIA streamed reasoning", reasoning.reasoning)
        assertEquals("Final answer", text.text)
    }

    @Test
    fun responseApiReasoningSummaryDeltaExtractsTitle() {
        val chunk = parseResponseDelta(
            buildJsonObject {
                put("type", "response.reasoning_summary_text.delta")
                put("item_id", "rs_123")
                put("delta", "**Responding to a greeting**\n\nThe user said hello.")
            }
        ) ?: error("chunk is missing")

        val reasoning = chunk.choices.single().delta
            ?.parts
            ?.filterIsInstance<UIMessagePart.Reasoning>()
            ?.single()
            ?: error("reasoning is missing")

        assertEquals("Responding to a greeting", reasoning.title)
        assertEquals("**Responding to a greeting**\n\nThe user said hello.", reasoning.reasoning)
    }

    private fun chatCompletionsBody(thinkingBudget: Int?): JsonObject {
        return chatCompletionsBody(
            messages = messages,
            model = reasoningModel,
            providerSetting = providerSetting,
            thinkingBudget = thinkingBudget,
        )
    }

    private fun chatCompletionsBody(
        messages: List<UIMessage>,
        model: Model,
        providerSetting: ProviderSetting.OpenAI,
        thinkingBudget: Int? = null,
        sessionId: String? = null,
    ): JsonObject {
        val api = ChatCompletionsAPI(
            httpClient = responseHttpClient,
            keyRoulette = object : KeyRoulette {
                override fun next(keys: String): String = keys
            },
            mediaEncoder = mediaEncoder,
        )
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(
            api,
            messages,
            TextGenerationParams(model = model, thinkingBudget = thinkingBudget, sessionId = sessionId),
            providerSetting,
            false,
        ) as JsonObject
    }

    private fun parseOpenAIMessage(message: JsonObject): UIMessage {
        val api = ChatCompletionsAPI(
            httpClient = responseHttpClient,
            keyRoulette = object : KeyRoulette {
                override fun next(keys: String): String = keys
            },
            mediaEncoder = mediaEncoder,
        )
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "parseMessage",
            JsonObject::class.java,
        )
        method.isAccessible = true
        return method.invoke(api, message) as UIMessage
    }

    private fun parseChatCompletionsUsage(usage: JsonObject): me.rerere.ai.core.TokenUsage? {
        val api = ChatCompletionsAPI(
            httpClient = responseHttpClient,
            keyRoulette = object : KeyRoulette {
                override fun next(keys: String): String = keys
            },
            mediaEncoder = mediaEncoder,
        )
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "parseTokenUsage",
            JsonObject::class.java,
        )
        method.isAccessible = true
        return method.invoke(api, usage) as me.rerere.ai.core.TokenUsage?
    }

    private fun responseApi(): ResponseAPI = ResponseAPI(responseHttpClient, mediaEncoder)

    private fun parseResponseUsage(usage: JsonObject): me.rerere.ai.core.TokenUsage? {
        val api = responseApi()
        val method = ResponseAPI::class.java.getDeclaredMethod(
            "parseTokenUsage",
            JsonObject::class.java,
        )
        method.isAccessible = true
        return method.invoke(api, usage) as me.rerere.ai.core.TokenUsage?
    }

    private fun responseApiBody(thinkingBudget: Int?): JsonObject {
        val api = responseApi()
        val method = ResponseAPI::class.java.getDeclaredMethod(
            "buildRequestBody",
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(
            api,
            messages,
            TextGenerationParams(model = reasoningModel, thinkingBudget = thinkingBudget),
            false,
        ) as JsonObject
    }

    private fun responseApiBody(
        thinkingBudget: Int?,
        providerSetting: ProviderSetting.OpenAI,
        sessionId: String?,
    ): JsonObject {
        val api = responseApi()
        val method = ResponseAPI::class.java.getDeclaredMethod(
            "buildRequestBody",
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
            ProviderSetting.OpenAI::class.java,
        )
        method.isAccessible = true
        return method.invoke(
            api,
            messages,
            TextGenerationParams(
                model = reasoningModel,
                thinkingBudget = thinkingBudget,
                sessionId = sessionId,
            ),
            false,
            providerSetting,
        ) as JsonObject
    }

    private fun responseApiBody(messages: List<UIMessage>): JsonObject {
        val api = responseApi()
        val method = ResponseAPI::class.java.getDeclaredMethod(
            "buildRequestBody",
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(
            api,
            messages,
            TextGenerationParams(model = reasoningModel),
            false,
        ) as JsonObject
    }

    private fun parseResponseDelta(delta: JsonObject): me.rerere.ai.ui.MessageChunk? {
        val api = responseApi()
        val method = ResponseAPI::class.java.getDeclaredMethod(
            "parseResponseDelta",
            JsonObject::class.java,
        )
        method.isAccessible = true
        return method.invoke(api, delta) as me.rerere.ai.ui.MessageChunk?
    }
}
