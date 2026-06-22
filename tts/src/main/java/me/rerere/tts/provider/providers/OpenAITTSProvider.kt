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
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "OpenAITTSProvider"

private val TTS_MODEL_PATTERNS = listOf(
    "tts", "audio", "speech", "orpheus", "playai", "voice", "sonic", "speak"
)

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

    override suspend fun listModels(
        providerSetting: TTSProviderSetting.OpenAI
    ): List<TTSModelInfo> = withContext(Dispatchers.IO) {
        if (providerSetting.apiKey.isBlank()) return@withContext emptyList()
        runCatching {
            val response = httpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = "${providerSetting.baseUrl}/models",
                    headers = mapOf(
                        "Authorization" to "Bearer ${providerSetting.apiKey}",
                    ),
                )
            )
            if (response.statusCode !in 200..299) {
                PlatformLog.e(
                    TAG,
                    "listModels failed: ${response.statusCode} ${response.body.decodeToString()}"
                )
                return@withContext emptyList()
            }
            val body = response.body.decodeToString()
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") as? JSONArray
            val all = dataArray?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val id = item.optString("id", "")
                        if (id.isNotBlank()) {
                            add(TTSModelInfo(id = id, displayName = id))
                        }
                    }
                }
            } ?: emptyList()

            if (all.isEmpty()) return@withContext emptyList()

            val ttsMatches = all.filter { info ->
                val lower = info.id.lowercase()
                TTS_MODEL_PATTERNS.any { pattern -> lower.contains(pattern) }
            }
            if (ttsMatches.isNotEmpty()) ttsMatches else all
        }.getOrElse { e ->
            PlatformLog.e(TAG, "listModels error: ${e.message}")
            emptyList()
        }
    }
}
