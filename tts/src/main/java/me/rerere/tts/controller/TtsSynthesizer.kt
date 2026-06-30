package me.rerere.tts.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.android.TTSManager

/**
 * Bridge TTS provider flow to a single audio buffer.
 */
class TtsSynthesizer(
    private val ttsManager: TTSManager
) {
    suspend fun synthesize(
        setting: TTSProviderSetting,
        chunk: TtsChunk
    ): TTSResponse = withContext(Dispatchers.IO) {
        collectToResponse(
            ttsManager.generateSpeech(setting, TTSRequest(text = chunk.text))
        )
    }

    private suspend fun collectToResponse(flow: Flow<AudioChunk>): TTSResponse {
        var format: AudioFormat? = null
        var sampleRate: Int? = null
        val buffers = mutableListOf<ByteArray>()
        var totalSize = 0
        flow.collect { chunk ->
            if (format == null) format = chunk.format
            if (sampleRate == null) sampleRate = chunk.sampleRate
            buffers += chunk.data
            totalSize += chunk.data.size
        }
        return TTSResponse(
            audioData = buffers.concatToByteArray(totalSize),
            format = format ?: AudioFormat.MP3,
            sampleRate = sampleRate
        )
    }

    private fun List<ByteArray>.concatToByteArray(totalSize: Int): ByteArray {
        val output = ByteArray(totalSize)
        var offset = 0
        for (buffer in this) {
            buffer.copyInto(output, destinationOffset = offset)
            offset += buffer.size
        }
        return output
    }
}

