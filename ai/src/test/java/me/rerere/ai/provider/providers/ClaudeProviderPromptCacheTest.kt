package me.rerere.ai.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformMediaEncoder
import me.rerere.common.platform.PlatformServerEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeProviderPromptCacheTest {
    private val httpClient = object : PlatformHttpClient {
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

    @Test
    fun buildMessageRequestAddsPromptCacheBreakpoints() {
        val body = buildClaudeMessageRequest(
            listOf(
                UIMessage.system("Stable system prompt"),
                UIMessage.user("First user turn"),
                UIMessage.assistant("First answer"),
                UIMessage.user("Current user turn")
            )
        )

        val system = body["system"]?.jsonArray ?: error("system is missing")
        assertEquals(
            "ephemeral",
            system[0].jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        )

        val messages = body["messages"]?.jsonArray ?: error("messages are missing")
        val userContent = messages[2].jsonObject["content"]?.jsonArray ?: error("content is missing")
        assertEquals(
            "ephemeral",
            userContent[0].jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun parseTokenUsageIncludesCacheReadAndCreationTokens() {
        val usage = parseClaudeUsage(
            buildJsonObject {
                put("input_tokens", 100)
                put("cache_creation_input_tokens", 200)
                put("cache_read_input_tokens", 300)
                put("output_tokens", 50)
            }
        ) ?: error("usage is missing")

        assertEquals(600, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(300, usage.cachedTokens)
        assertEquals(650, usage.totalTokens)
    }

    private fun buildClaudeMessageRequest(messages: List<UIMessage>): JsonObject {
        val provider = ClaudeProvider(httpClient, mediaEncoder)
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(
            provider,
            messages,
            TextGenerationParams(model = Model(modelId = "claude-sonnet-4.5")),
            false,
        ) as JsonObject
    }

    private fun parseClaudeUsage(usage: JsonObject): me.rerere.ai.core.TokenUsage? {
        val provider = ClaudeProvider(httpClient, mediaEncoder)
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "parseTokenUsage",
            JsonObject::class.java,
        )
        method.isAccessible = true
        return method.invoke(provider, usage) as me.rerere.ai.core.TokenUsage?
    }
}
