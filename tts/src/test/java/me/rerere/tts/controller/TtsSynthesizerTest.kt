package me.rerere.tts.controller

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TtsSpeechGenerator
import org.junit.Assert.*
import org.junit.Test

class TtsSynthesizerTest {
    private fun synthesizer(vararg parts: AudioChunk) = TtsSynthesizer(object : TtsSpeechGenerator {
        override fun generateSpeech(providerSetting: TTSProviderSetting, request: TTSRequest): Flow<AudioChunk> =
            flowOf(*parts)
    })

    @Test fun combinesAllAudioPartsPreservingRateAndMetadata() = runBlocking {
        val result = synthesizer(
            AudioChunk(byteArrayOf(1, 0), AudioFormat.PCM, 48_000, metadata = mapOf("channels" to "1")),
            AudioChunk(byteArrayOf(2, 0), AudioFormat.PCM, 48_000),
            AudioChunk(byteArrayOf(), AudioFormat.PCM, isLast = true),
        ).synthesize(TTSProviderSetting.Gemini(), TtsChunk(index = 0, text = "Both parts"))
        assertArrayEquals(byteArrayOf(1, 0, 2, 0), result.audioData)
        assertEquals(48_000, result.sampleRate)
        assertEquals("1", result.metadata["channels"])
    }

    @Test fun emptyResponsesAndFormatChangesAreFailuresInsteadOfSilentChunksOrNoise() = runBlocking {
        val invalid = listOf(
            synthesizer(),
            synthesizer(AudioChunk(byteArrayOf(), AudioFormat.PCM)),
            synthesizer(AudioChunk(byteArrayOf(1, 0), AudioFormat.PCM, 24_000),
                AudioChunk(byteArrayOf(2, 0), AudioFormat.PCM, 48_000)),
            synthesizer(AudioChunk(byteArrayOf(1, 0), AudioFormat.PCM, 24_000),
                AudioChunk(byteArrayOf(2, 0), AudioFormat.MP3)),
        )
        invalid.forEach { instance ->
            try {
                instance.synthesize(TTSProviderSetting.Gemini(), TtsChunk(index = 0, text = "Text"))
                fail("Invalid audio must not enter the playlist")
            } catch (expected: IllegalArgumentException) { }
            catch (expected: IllegalStateException) { }
        }
    }
}
