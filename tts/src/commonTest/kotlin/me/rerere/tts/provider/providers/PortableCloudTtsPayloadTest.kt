package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.common.http.jsonPrimitiveOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PortableCloudTtsPayloadTest {
    @Test
    fun openAiPayloadPreservesWireShape() = runBlocking {
        val client = CapturingHttpClient()
        OpenAITTSProvider(client).generateSpeech(
            TTSProviderSetting.OpenAI(model = "tts-model", voice = "alloy"),
            TTSRequest("hello"),
        ).toList()

        val body = client.body()
        assertEquals("tts-model", body.string("model"))
        assertEquals("hello", body.string("input"))
        assertEquals("alloy", body.string("voice"))
        assertEquals("mp3", body.string("response_format"))
    }

    @Test
    fun geminiPayloadPreservesNestedSpeechConfig() = runBlocking {
        val response = """{"candidates":[{"content":{"parts":[{"inlineData":{"data":"AA==","mimeType":"audio/pcm"}}]}}]}"""
        val client = CapturingHttpClient(response.encodeToByteArray())
        GeminiTTSProvider(client).generateSpeech(
            TTSProviderSetting.Gemini(model = "gemini-tts", voiceName = "Kore"),
            TTSRequest("hello"),
        ).toList()

        val body = client.body()
        val firstPart = body["contents"]?.jsonArrayOrNull?.firstOrNull()
            ?.jsonObjectOrNull?.get("parts")?.jsonArrayOrNull?.firstOrNull()?.jsonObjectOrNull
        assertEquals("hello", firstPart?.string("text"))
        val config = body["generationConfig"]?.jsonObjectOrNull
        val voice = config?.get("speechConfig")?.jsonObjectOrNull
            ?.get("voiceConfig")?.jsonObjectOrNull
            ?.get("prebuiltVoiceConfig")?.jsonObjectOrNull
        assertEquals("Kore", voice?.string("voiceName"))
        assertEquals("AUDIO", config?.get("responseModalities")?.jsonArrayOrNull?.firstOrNull()
            ?.jsonPrimitiveOrNull?.contentOrNull)
    }

    @Test
    fun elevenLabsAndFishAudioPayloadsPreserveProviderFields() = runBlocking {
        val elevenClient = CapturingHttpClient()
        ElevenLabsTTSProvider(elevenClient).generateSpeech(
            TTSProviderSetting.ElevenLabs(modelId = "eleven-v2", voiceId = "voice-1"),
            TTSRequest("hello"),
        ).toList()
        assertEquals("eleven-v2", elevenClient.body().string("model_id"))

        val fishClient = CapturingHttpClient()
        FishAudioTTSProvider(fishClient).generateSpeech(
            TTSProviderSetting.FishAudio(referenceId = "reference", speed = 1.2f),
            TTSRequest("hello"),
        ).toList()
        val fish = fishClient.body()
        assertEquals("reference", fish.string("reference_id"))
        assertEquals("1.2", fish["prosody"]?.jsonObjectOrNull?.get("speed")?.toString())
    }

    @Test
    fun cartesiaPayloadPreservesOutputAndGenerationConfig() = runBlocking {
        val client = CapturingHttpClient()
        CartesiaTTSProvider(client).generateSpeech(
            TTSProviderSetting.Cartesia(
                modelId = "sonic",
                voiceId = "voice-2",
                outputFormat = "mp3",
                emotion = "happy",
            ),
            TTSRequest("hello"),
        ).toList()

        val body = client.body()
        assertEquals("voice-2", body["voice"]?.jsonObjectOrNull?.string("id"))
        assertEquals("44100", body["output_format"]?.jsonObjectOrNull?.get("sample_rate")?.toString())
        assertEquals("happy", body["generation_config"]?.jsonObjectOrNull?.string("emotion"))
    }

    @Test
    fun qwenAndPlayHtStreamingPayloadsRemainPortable() = runBlocking {
        val qwenClient = CapturingHttpClient()
        QwenTTSProvider(qwenClient).generateSpeech(
            TTSProviderSetting.Qwen(model = "qwen", voice = "Cherry", languageType = "Auto"),
            TTSRequest("hello"),
        ).toList()
        val qwenInput = qwenClient.body()["input"]?.jsonObjectOrNull
        assertEquals("Cherry", qwenInput?.string("voice"))
        assertEquals("Auto", qwenInput?.string("language_type"))

        val playClient = CapturingHttpClient()
        PlayHTTTSProvider(playClient).generateSpeech(
            TTSProviderSetting.PlayHT(voice = "voice", voiceEngine = "PlayHT2.0", speed = 0.9f),
            TTSRequest("hello"),
        ).toList()
        val play = playClient.body()
        assertEquals("voice", play.string("voice"))
        assertEquals("PlayHT2.0", play.string("voice_engine"))
        assertEquals("0.9", play["speed"]?.toString())
    }
}

private class CapturingHttpClient(
    private val responseBody: ByteArray = byteArrayOf(1),
) : PlatformHttpClient {
    private var captured: PlatformHttpRequest? = null

    override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
        captured = request
        return PlatformHttpResponse(statusCode = 200, body = responseBody)
    }

    override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> {
        captured = request
        return flowOf(PlatformServerEvent.Open(200), PlatformServerEvent.Closed)
    }

    fun body(): JsonObject {
        val request = assertNotNull(captured)
        val bytes = assertNotNull(request.body)
        return assertNotNull(ttsJson.parseToJsonElement(bytes.decodeToString()).jsonObjectOrNull)
    }
}

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
