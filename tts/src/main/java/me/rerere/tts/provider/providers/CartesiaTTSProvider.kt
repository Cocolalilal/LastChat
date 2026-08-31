package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.common.http.jsonPrimitiveOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSModelInfo
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

private const val TAG = "CartesiaTTSProvider"

private const val CARTESIA_API_VERSION = "2026-03-01"

class CartesiaTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.Cartesia> {

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.Cartesia,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val outputFormat = buildJsonObject {
            put("container", providerSetting.outputFormat)
            if (providerSetting.outputFormat == "mp3") {
                put("sample_rate", 44100)
                put("bit_rate", 128000)
            } else {
                put("encoding", "pcm_s16le")
                put("sample_rate", 24000)
            }
        }

        val requestBody = buildJsonObject {
            put("model_id", providerSetting.modelId)
            put("transcript", request.text)
            put("voice", buildJsonObject {
                put("mode", "id")
                put("id", providerSetting.voiceId)
            })
            put("language", providerSetting.language)
            put("output_format", outputFormat)
            put("generation_config", buildJsonObject {
                put("speed", providerSetting.speed.toTtsJsonNumber())
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
    ): List<TTSModelInfo> = withContext(me.rerere.tts.provider.ttsIoDispatcher) {
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
            ttsJson.parseToJsonElement(body)
                .jsonObjectOrNull
                ?.get("data")
                ?.jsonArrayOrNull
                .orEmpty()
                .mapNotNull { element ->
                    val item = element.jsonObjectOrNull ?: return@mapNotNull null
                    val id = item["id"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
                    val name = item["name"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty().ifBlank { id }
                    id.takeIf(String::isNotBlank)?.let { TTSModelInfo(id = it, displayName = name) }
                }
        }.getOrElse { e ->
            PlatformLog.e(TAG, "listVoices error: ${e.message}")
            emptyList()
        }
    }
}
