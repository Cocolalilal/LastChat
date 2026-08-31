package me.rerere.ai.provider.providers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.ContextLimitSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderContextMetadataTest {
    @Test
    fun openRouterUsesSmallerDeploymentLimitAndCompletionLimit() {
        val model = Json.parseToJsonElement(
            """
            {
              "context_length": 131072,
              "top_provider": {
                "context_length": 65536,
                "max_completion_tokens": 8192
              }
            }
            """.trimIndent()
        ).jsonObject

        val limits = parseOpenAIProviderContextLimits(model)

        assertEquals(65_536, limits.contextWindowTokens)
        assertEquals(8_192, limits.maxOutputTokens)
        assertEquals(ContextLimitSource.PROVIDER, limits.source)
    }

    @Test
    fun mistralStyleMaxContextLengthIsRecognized() {
        val model = Json.parseToJsonElement("""{"max_context_length":32768}""").jsonObject

        val limits = parseOpenAIProviderContextLimits(model)

        assertEquals(32_768, limits.contextWindowTokens)
        assertNull(limits.maxInputTokens)
    }

    @Test
    fun genericModelWithoutLimitsRemainsUnknown() {
        val model = Json.parseToJsonElement("""{"id":"unknown"}""").jsonObject

        val limits = parseOpenAIProviderContextLimits(model)

        assertNull(limits.contextWindowTokens)
        assertNull(limits.maxInputTokens)
        assertNull(limits.maxOutputTokens)
        assertNull(limits.source)
    }

    @Test
    fun geminiPreservesIndependentInputAndOutputLimits() {
        val model = Json.parseToJsonElement(
            """{"inputTokenLimit":1048576,"outputTokenLimit":65536}"""
        ).jsonObject

        val limits = parseGoogleProviderContextLimits(model)

        assertEquals(1_048_576, limits.maxInputTokens)
        assertEquals(65_536, limits.maxOutputTokens)
        assertEquals(1_114_112, limits.contextWindowTokens)
        assertEquals(ContextLimitSource.PROVIDER, limits.source)
    }
}
