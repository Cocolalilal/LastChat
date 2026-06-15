package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import org.json.JSONObject

private const val TAG = "ElevenLabsTTSProvider"

class ElevenLabsTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.ElevenLabs> {
    override fun generateSpeech(
        providerSetting: TTSProviderSetting.ElevenLabs,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("text", request.text)
            put("model_id", providerSetting.modelId)
        }

        PlatformLog.i(TAG, "generateSpeech: voiceId=${providerSetting.voiceId}, model=${providerSetting.modelId}")

        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "https://api.elevenlabs.io/v1/text-to-speech/${providerSetting.voiceId}",
                headers = mapOf(
                    "xi-api-key" to providerSetting.apiKey,
                    "Content-Type" to "application/json",
                    "Accept" to "audio/mpeg",
                ),
                body = requestBody.toString().encodeToByteArray(),
                mediaType = "application/json",
            )
        )

        if (response.statusCode !in 200..299) {
            val errorBody = response.body.decodeToString()
            PlatformLog.e(TAG, "TTS request failed: ${response.statusCode} $errorBody")
            throw Exception("ElevenLabs TTS failed: $errorBody")
        }

        val audioData = response.body

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.MP3,
                isLast = true,
                metadata = mapOf(
                    "provider" to "elevenlabs",
                    "model" to providerSetting.modelId,
                    "voice" to providerSetting.voiceId
                )
            )
        )
    }
}
