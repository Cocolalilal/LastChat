package me.rerere.ai.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformJwtSigner
import me.rerere.common.platform.PlatformMediaEncoder
import me.rerere.common.platform.PlatformServerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTokenCountTest {
    private val mediaEncoder = object : PlatformMediaEncoder {
        override fun encodeImage(url: String, withPrefix: Boolean): Result<String> = Result.success(url)
        override fun encodeVideo(url: String, withPrefix: Boolean): Result<String> = Result.success(url)
        override fun encodeAudio(url: String, withPrefix: Boolean): Result<String> = Result.success(url)
    }

    @Test
    fun `Gemini count request mirrors the generation payload`() = runBlocking {
        val client = RecordingClient("""{"totalTokens":321}""")
        val provider = GoogleProvider(
            platformHttpClient = client,
            mediaEncoder = mediaEncoder,
            platformJwtSigner = object : PlatformJwtSigner {
                override fun signRs256(data: ByteArray, pkcs8PrivateKeyPem: String): ByteArray =
                    ByteArray(0)
            },
        )
        val count = provider.countInputTokens(
            providerSetting = ProviderSetting.Google(apiKey = "test-key"),
            messages = listOf(UIMessage.system("Rules"), UIMessage.user("Hello")),
            params = TextGenerationParams(model = Model(modelId = "gemini-2.5-pro")),
        )

        assertEquals(321, count)
        assertTrue(client.request.url.contains("models/gemini-2.5-pro:countTokens"))
        val body = Json.parseToJsonElement(client.request.body!!.decodeToString()).jsonObject
        assertTrue(body.containsKey("generateContentRequest"))
    }

    @Test
    fun `Claude count request removes generation-only controls`() = runBlocking {
        val client = RecordingClient("""{"input_tokens":654}""")
        val provider = ClaudeProvider(client, mediaEncoder)
        val count = provider.countInputTokens(
            providerSetting = ProviderSetting.Claude(apiKey = "test-key"),
            messages = listOf(UIMessage.system("Rules"), UIMessage.user("Hello")),
            params = TextGenerationParams(
                model = Model(modelId = "claude-sonnet-4-5"),
                maxTokens = 4096,
                temperature = 0.4f,
            ),
        )

        assertEquals(654, count)
        assertTrue(client.request.url.endsWith("/messages/count_tokens"))
        val body = Json.parseToJsonElement(client.request.body!!.decodeToString()).jsonObject
        assertFalse(body.containsKey("max_tokens"))
        assertFalse(body.containsKey("temperature"))
        assertTrue(body.containsKey("messages"))
    }

    private class RecordingClient(private val responseBody: String) : PlatformHttpClient {
        lateinit var request: PlatformHttpRequest

        override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
            this.request = request
            return PlatformHttpResponse(statusCode = 200, body = responseBody.encodeToByteArray())
        }

        override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
    }
}
