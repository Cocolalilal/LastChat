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

private const val TAG = "OpenAITTSProvider"

class OpenAITTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.OpenAI> {
    override fun generateSpeech(
        providerSetting: TTSProviderSetting.OpenAI,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", "mp3") // Default to MP3
        }

        PlatformLog.i(
            TAG,
            "generateSpeech: model=${providerSetting.model}, " +
                "voice=${providerSetting.voice}, textLength=${request.text.length}"
        )

        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/audio/speech",
                headers = mapOf(
                    "Authorization" to "Bearer ${providerSetting.apiKey}",
                    "Content-Type" to "application/json",
                ),
                body = requestBody.toString().encodeToByteArray(),
                mediaType = "application/json",
            )
        )

        if (response.statusCode !in 200..299) {
            val errorBody = response.body.decodeToString()
            PlatformLog.e(TAG, "TTS request failed: ${response.statusCode} $errorBody")
            throw Exception("OpenAI TTS failed: $errorBody")
        }

        val audioData = response.body

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.MP3,
                isLast = true,
                metadata = mapOf(
                    "provider" to "openai",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice
                )
            )
        )
    }
}
