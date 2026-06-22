package me.rerere.tts.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSModelInfo
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import org.json.JSONObject

private const val TAG = "FishAudioTTSProvider"

class FishAudioTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.FishAudio> {

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.FishAudio,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("text", request.text)
            if (providerSetting.referenceId.isNotBlank()) {
                put("reference_id", providerSetting.referenceId)
            }
            put("temperature", providerSetting.temperature.toDouble())
            put("top_p", providerSetting.topP.toDouble())
            put("format", providerSetting.format)
            put("prosody", JSONObject().apply {
                put("speed", providerSetting.speed.toDouble())
            })
            put("normalize", true)
            put("chunk_length", 300)
        }

        PlatformLog.i(
            TAG,
            "generateSpeech: model=${providerSetting.model}, " +
                "referenceId=${providerSetting.referenceId}, textLength=${request.text.length}"
        )

        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/v1/tts",
                headers = mapOf(
                    "Authorization" to "Bearer ${providerSetting.apiKey}",
                    "Content-Type" to "application/json",
                    "model" to providerSetting.model,
                ),
                body = requestBody.toString().encodeToByteArray(),
                mediaType = "application/json",
            )
        )

        if (response.statusCode !in 200..299) {
            val errorBody = response.body.decodeToString()
            PlatformLog.e(TAG, "TTS request failed: ${response.statusCode} $errorBody")
            throw Exception("Fish Audio TTS failed: $errorBody")
        }

        val format = when (providerSetting.format.lowercase()) {
            "wav" -> AudioFormat.WAV
            "pcm" -> AudioFormat.PCM
            "opus" -> AudioFormat.OPUS
            else -> AudioFormat.MP3
        }

        emit(
            AudioChunk(
                data = response.body,
                format = format,
                isLast = true,
                metadata = mapOf(
                    "provider" to "fishaudio",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.referenceId
                )
            )
        )
    }

    override suspend fun listModels(
        providerSetting: TTSProviderSetting.FishAudio
    ): List<TTSModelInfo> = listOf(
        TTSModelInfo("s2-pro", "Fish Audio S2 Pro"),
        TTSModelInfo("s1", "Fish Audio S1"),
    )
}
