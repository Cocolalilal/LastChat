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
        val requestBody = buildJsonObject {
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
    ): List<TTSModelInfo> = withContext(me.rerere.tts.provider.ttsIoDispatcher) {
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
            val dataArray = ttsJson.parseToJsonElement(body)
                .jsonObjectOrNull
                ?.get("data")
                ?.jsonArrayOrNull
            val all = dataArray.orEmpty().mapNotNull { element ->
                val id = element.jsonObjectOrNull
                    ?.get("id")
                    ?.jsonPrimitiveOrNull
                    ?.contentOrNull
                    .orEmpty()
                id.takeIf(String::isNotBlank)?.let { TTSModelInfo(id = it, displayName = it) }
            }

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
