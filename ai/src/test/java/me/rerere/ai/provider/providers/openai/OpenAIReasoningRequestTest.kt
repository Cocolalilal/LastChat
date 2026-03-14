package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
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

    private fun chatCompletionsBody(thinkingBudget: Int?): JsonObject {
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
            TextGenerationParams(model = reasoningModel, thinkingBudget = thinkingBudget),
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
}
