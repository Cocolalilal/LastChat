package me.rerere.ai.provider.providers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.ContextLimitSource
import me.rerere.ai.provider.baseCapacityTokens
import me.rerere.ai.provider.contextCapacityTokens
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

    @Test
    fun modelPreservesBaseCapacityWhenCustomLimitIsAppliedOrRemoved() {
        val baseModel = me.rerere.ai.provider.Model(
            modelId = "claude-sonnet-4-5",
            contextWindowTokens = 200_000,
            maxOutputTokens = 8_192,
        )

        assertEquals(200_000, baseModel.baseCapacityTokens)
        assertEquals(200_000, baseModel.contextCapacityTokens)

        // Custom limit applied
        val customizedModel = baseModel.copy(
            customContextLimitTokens = 32_000,
            contextLimitSource = ContextLimitSource.MANUAL,
        )
        // Base capacity remains 200k, effective capacity is capped to 32k
        assertEquals(200_000, customizedModel.baseCapacityTokens)
        assertEquals(32_000, customizedModel.contextCapacityTokens)

        // Custom limit removed / reset to max
        val resetModel = customizedModel.copy(
            customContextLimitTokens = null,
            contextLimitSource = null,
        )
        // Never loses base capacity
        assertEquals(200_000, resetModel.baseCapacityTokens)
        assertEquals(200_000, resetModel.contextCapacityTokens)
    }
}
