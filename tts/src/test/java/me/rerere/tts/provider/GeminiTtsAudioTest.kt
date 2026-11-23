package me.rerere.tts.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.tts.controller.pcmToWavBytes
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.providers.GeminiTTSProvider
import me.rerere.tts.provider.providers.decodeGeminiAudio
import org.junit.Assert.*
import org.junit.Test
import kotlin.io.encoding.Base64

class GeminiTtsAudioTest {
    private val samples = byteArrayOf(1, 0, 2, 0, -1, 127, 0, -128)

    @Test fun waveHeadersAndMetadataAreNeverPlayedAsSamplesEvenWhenLabeledPcm() {
        val wave = pcmToWavBytes(samples, 24_000)
        val metadata = byteArrayOf(76, 73, 83, 84, 4, 0, 0, 0, 73, 78, 70, 79)
        val container = wave + metadata
        val length = container.size - 8
        repeat(4) { container[4 + it] = (length ushr (8 * it)).toByte() }
        for (mime in listOf("audio/wav", "audio/L16;codec=pcm;rate=24000")) {
            val chunk = decodeGeminiAudio(container, mime)
            assertEquals(AudioFormat.PCM, chunk.format)
            assertEquals(24_000, chunk.sampleRate)
            assertArrayEquals(samples, chunk.data)
        }
    }

    @Test fun rawPcmPreservesSpeechAndReportedSampleRateWithoutNoiseTrimming() {
        val decoded = decodeGeminiAudio(samples, "audio/L16;codec=pcm;rate=48000")
        assertArrayEquals(samples, decoded.data)
        assertEquals(48_000, decoded.sampleRate)
        assertEquals(AudioFormat.PCM, decoded.format)
        assertEquals(AudioFormat.MP3, decodeGeminiAudio(samples, "audio/mpeg").format)
    }

    @Test fun corruptOrUnsupportedAudioFailsInsteadOfPlayingNoise() {
        val wave = pcmToWavBytes(samples, 24_000)
        assertFails { decodeGeminiAudio(samples.copyOf(3), "audio/pcm") }
        assertFails { decodeGeminiAudio(byteArrayOf(), "audio/pcm") }
        assertFails { decodeGeminiAudio(samples, "text/html") }
        assertFails { decodeGeminiAudio(samples, "audio/pcm;codec=float32") }
        assertFails { decodeGeminiAudio(samples, "audio/pcm;channels=2") }
        assertFails { decodeGeminiAudio(wave.copyOf(wave.size - 2), "audio/wav") }
        assertFails { decodeGeminiAudio(samples, "audio/wav") }
    }

    @Test fun everyAudioPartIsReadAndOnlyTheLastIsMarkedLast() = runBlocking {
        val part = """{"inlineData":{"data":"${Base64.encode(samples)}","mimeType":"audio/L16;codec=pcm;rate=24000"}}"""
        val response = """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"ignored"},$part,$part]}}]}"""
        val client = Client { response }
        val chunks = GeminiTTSProvider(client).generateSpeech(TTSProviderSetting.Gemini(), TTSRequest("Read both paragraphs.")).toList()
        assertEquals(2, chunks.size)
        assertFalse(chunks.first().isLast)
        assertTrue(chunks.last().isLast)
        chunks.forEach { assertArrayEquals(samples, it.data) }
    }

    @Test fun incompleteGenerationAndRefusalsNeverBecomeSuccessfulPartialSpeech() = runBlocking {
        val part = """{"inlineData":{"data":"${Base64.encode(samples)}","mimeType":"audio/pcm"}}"""
        for (response in listOf(
            """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[$part]}}]}""",
            """{"promptFeedback":{"blockReason":"SAFETY"}}""",
            """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"No speech"}]}}]}""",
        )) {
            val emitted = mutableListOf<me.rerere.tts.model.AudioChunk>()
            try {
                GeminiTTSProvider(Client { response }).generateSpeech(TTSProviderSetting.Gemini(), TTSRequest("Text")).collect { emitted += it }
                fail("Expected explicit failure")
            } catch (expected: IllegalArgumentException) {
                assertTrue(emitted.isEmpty())
            } catch (expected: IllegalStateException) {
                assertTrue(emitted.isEmpty())
            }
        }
    }

    @Test fun modelDiscoveryPaginatesAndOnlyOffersCompatibleGenerationMethods() = runBlocking {
        val client = Client { request ->
            if (request.url.endsWith("/models")) """{"models":[{"name":"models/gemini-2.5-flash-preview-tts","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"second page"}"""
            else """{"models":[{"name":"models/gemini-2.5-pro-preview-tts"},{"name":"models/gemini-3.8-flash-tts","supportedGenerationMethods":["interactions"]},{"name":"models/embedding","supportedGenerationMethods":["embedContent"]}]}"""
        }
        val models = GeminiTTSProvider(client).listModels(TTSProviderSetting.Gemini(apiKey = "test"))
        assertEquals(listOf("gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"), models.map { it.id })
        assertEquals(2, client.requests.size)
        assertTrue(client.requests.last().url.contains("pageToken=second%20page"))
    }

    @Test fun modelDiscoveryDoesNotSwallowCancellation() = runBlocking {
        val hanging = object : PlatformHttpClient {
            override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse = awaitCancellation()
            override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
        }
        try {
            withTimeout(50) { GeminiTTSProvider(hanging).listModels(TTSProviderSetting.Gemini(apiKey = "test")) }
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) { }
    }

    private inline fun assertFails(block: () -> Unit) {
        try { block(); fail("Expected invalid audio to fail") } catch (expected: IllegalArgumentException) { }
        catch (expected: IllegalStateException) { }
    }

    private class Client(private val response: (PlatformHttpRequest) -> String) : PlatformHttpClient {
        val requests = mutableListOf<PlatformHttpRequest>()
        override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
            requests += request
            return PlatformHttpResponse(statusCode = 200, headers = emptyMap(), body = response(request).encodeToByteArray())
        }
        override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
    }
}
