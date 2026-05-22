package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.ReasoningRequestBehavior
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIReasoningRequestTest {
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
    ): JsonObject {
        val api = ChatCompletionsAPI(
            client = OkHttpClient(),
            keyRoulette = object : KeyRoulette {
                override fun next(keys: String): String = keys
            }
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
            TextGenerationParams(model = model, thinkingBudget = thinkingBudget),
            providerSetting,
            false,
        ) as JsonObject
    }

    private fun responseApiBody(thinkingBudget: Int?): JsonObject {
        val api = ResponseAPI(OkHttpClient())
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

    private fun responseApiBody(messages: List<UIMessage>): JsonObject {
        val api = ResponseAPI(OkHttpClient())
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
}
