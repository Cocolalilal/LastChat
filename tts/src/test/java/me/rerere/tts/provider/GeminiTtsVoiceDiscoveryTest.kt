package me.rerere.tts.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.platform.*
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.providers.GeminiTTSProvider
import org.junit.Assert.*
import org.junit.Test

class GeminiTtsVoiceDiscoveryTest {
    private class Client(val respond: (PlatformHttpRequest) -> PlatformHttpResponse) : PlatformHttpClient {
        val requests = mutableListOf<PlatformHttpRequest>()
        override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
            requests += request
            return respond(request)
        }
        override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
    }
    private fun response(body: String, code: Int = 200) = PlatformHttpResponse(code, body = body.encodeToByteArray())

    @Test fun fetchesEveryPageAndAcceptsNewVoicesWithoutBundledCatalogFiltering() = runBlocking {
        val client = Client { request ->
            if (!request.url.contains("page_token=")) response("""{"voices":[{"id":"FutureVoice","display_name":"Fresh voice","language_code":"de-DE","type":"prebuilt"}],"next_page_token":"next page/+"}""")
            else response("""{"voices":[{"id":"FutureVoice","type":"prebuilt"},{"id":"AnotherNewVoice","displayName":"Another voice","languageCode":"en-GB"},{"id":"private-clone","type":"replicated"}]}""")
        }
        val provider = TTSProviderSetting.Gemini(apiKey = "test-key", baseUrl = "https://example.test/v1beta/", model = "gemini-3.8-flash-tts")
        val voices = GeminiTTSProvider(client).listVoices(provider)
        assertEquals(listOf("FutureVoice", "AnotherNewVoice"), voices.map { it.providerVoiceId })
        assertEquals("Fresh voice", voices[0].name)
        assertEquals("de-DE", voices[0].locale)
        assertTrue(voices.all { it.model == provider.model })
        assertFalse(GeminiTtsCatalog.voices.contains("FutureVoice"))
        assertEquals(2, client.requests.size)
        assertTrue(client.requests.last().url.contains("page_token=next%20page%2F%2B"))
        assertTrue(client.requests.all { it.url.contains("type=prebuilt") })
        assertTrue(client.requests.all { it.headers["x-goog-api-key"] == "test-key" })
        assertTrue(client.requests.none { it.url.contains("test-key") })
    }

    @Test fun successfulEmptyCatalogStaysEmptyAndFailedCatalogDoesNotPretendToBeLive() = runBlocking {
        val empty = GeminiTTSProvider(Client { response("""{"voices":[]}""") })
        assertTrue(empty.listVoices(TTSProviderSetting.Gemini(apiKey = "test")).isEmpty())
        for (body in listOf("{}", "not json", "{\"error\":\"unavailable\"}")) {
            val client = Client { response(body, if (body.contains("unavailable")) 404 else 200) }
            var failed = false
            try { GeminiTTSProvider(client).listVoices(TTSProviderSetting.Gemini(apiKey = "test")) }
            catch (expected: Exception) { failed = true }
            assertTrue(body, failed)
        }
    }

    @Test fun repeatedPageTokensFailInsteadOfLoopingForever() = runBlocking {
        val client = Client { response("""{"voices":[],"next_page_token":"same"}""") }
        try {
            GeminiTTSProvider(client).listVoices(TTSProviderSetting.Gemini(apiKey = "test"))
            fail("Repeated page token must fail")
        } catch (expected: IllegalStateException) { assertEquals(2, client.requests.size) }
    }

    @Test fun cancellationOfVoiceDiscoveryPropagates() = runBlocking {
        val client = object : PlatformHttpClient {
            override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse = awaitCancellation()
            override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
        }
        try {
            withTimeout(50) { GeminiTTSProvider(client).listVoices(TTSProviderSetting.Gemini(apiKey = "test")) }
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) { }
    }

    @Test fun currentModelsSendTheReturnedVoiceIdWithoutRenamingIt() = runBlocking {
        val client = Client { response("""{"candidates":[{"content":{"parts":[{"inlineData":{"data":"AAA=","mimeType":"audio/pcm"}}]}}]}""") }
        GeminiTTSProvider(client).generateSpeech(
            TTSProviderSetting.Gemini(model = "gemini-3.8-flash-tts", voiceName = "FutureVoice"), TTSRequest("hello"),
        ).toList()
        val config = Json.parseToJsonElement(client.requests.single().body!!.decodeToString()).jsonObject
            .getValue("generationConfig").jsonObject.getValue("speechConfig").jsonObject.getValue("voiceConfig").jsonObject
        assertEquals("FutureVoice", config.getValue("voice").jsonPrimitive.content)
        assertFalse(config.containsKey("prebuiltVoiceConfig"))
    }

    @Test fun modelApiMayOfferCurrentGenerateContentTtsModels() = runBlocking {
        val client = Client { response("""{"models":[{"name":"models/gemini-3.8-flash-tts","supportedGenerationMethods":["generateContent"]}]}""") }
        assertEquals("gemini-3.8-flash-tts", GeminiTTSProvider(client)
            .listModels(TTSProviderSetting.Gemini(apiKey = "test")).single().id)
    }
}
