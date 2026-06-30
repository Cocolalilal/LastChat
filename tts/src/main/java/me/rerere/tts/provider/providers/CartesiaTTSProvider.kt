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

private const val TAG = "CartesiaTTSProvider"

private const val CARTESIA_API_VERSION = "2026-03-01"

class CartesiaTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.Cartesia> {

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.Cartesia,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val outputFormat = JSONObject().apply {
            put("container", providerSetting.outputFormat)
            if (providerSetting.outputFormat == "mp3") {
                put("sample_rate", 44100)
                put("bit_rate", 128000)
            } else {
                put("encoding", "pcm_s16le")
                put("sample_rate", 24000)
            }
        }

        val requestBody = JSONObject().apply {
            put("model_id", providerSetting.modelId)
            put("transcript", request.text)
            put("voice", JSONObject().apply {
                put("mode", "id")
                put("id", providerSetting.voiceId)
            })
            put("language", providerSetting.language)
            put("output_format", outputFormat)
            put("generation_config", JSONObject().apply {
                put("speed", providerSetting.speed.toDouble())
                put("emotion", providerSetting.emotion)
            })
        }

        PlatformLog.i(
            TAG,
            "generateSpeech: model=${providerSetting.modelId}, " +
                "voiceId=${providerSetting.voiceId}, textLength=${request.text.length}"
        )

        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/tts/bytes",
                headers = mapOf(
                    "Authorization" to "Bearer ${providerSetting.apiKey}",
                    "Content-Type" to "application/json",
                    "Cartesia-Version" to CARTESIA_API_VERSION,
                ),
                body = requestBody.toString().encodeToByteArray(),
                mediaType = "application/json",
            )
        )

        if (response.statusCode !in 200..299) {
            val errorBody = response.body.decodeToString()
            PlatformLog.e(TAG, "TTS request failed: ${response.statusCode} $errorBody")
            throw Exception("Cartesia TTS failed: $errorBody")
        }

        val format = when (providerSetting.outputFormat.lowercase()) {
            "wav" -> AudioFormat.WAV
            "raw" -> AudioFormat.PCM
            else -> AudioFormat.MP3
        }

        emit(
            AudioChunk(
                data = response.body,
                format = format,
                isLast = true,
                metadata = mapOf(
                    "provider" to "cartesia",
                    "model" to providerSetting.modelId,
                    "voice" to providerSetting.voiceId
                )
            )
        )
    }

    override suspend fun listModels(
        providerSetting: TTSProviderSetting.Cartesia
    ): List<TTSModelInfo> = listOf(
        TTSModelInfo("sonic-3.5", "Sonic 3.5"),
        TTSModelInfo("sonic-3", "Sonic 3"),
        TTSModelInfo("sonic-latest", "Sonic Latest"),
    )

    suspend fun listVoices(
        providerSetting: TTSProviderSetting.Cartesia
    ): List<TTSModelInfo> = withContext(Dispatchers.IO) {
        if (providerSetting.apiKey.isBlank()) return@withContext emptyList()
        runCatching {
            val response = httpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = "${providerSetting.baseUrl}/voices",
                    headers = mapOf(
                        "Authorization" to "Bearer ${providerSetting.apiKey}",
                        "Cartesia-Version" to CARTESIA_API_VERSION,
                    ),
                )
            )
            if (response.statusCode !in 200..299) {
                PlatformLog.e(
                    TAG,
                    "listVoices failed: ${response.statusCode} ${response.body.decodeToString()}"
                )
                return@withContext emptyList()
            }
            val body = response.body.decodeToString()
            val data = JSONObject(body).optJSONArray("data") as? JSONArray
            data?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val id = item.optString("id", "")
                        val name = item.optString("name", id)
                        if (id.isNotBlank()) {
                            add(TTSModelInfo(id = id, displayName = name))
                        }
                    }
                }
            } ?: emptyList()
        }.getOrElse { e ->
            PlatformLog.e(TAG, "listVoices error: ${e.message}")
            emptyList()
        }
    }
}
