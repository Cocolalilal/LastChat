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

private const val TAG = "ElevenLabsTTSProvider"

class ElevenLabsTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.ElevenLabs> {
    override fun generateSpeech(
        providerSetting: TTSProviderSetting.ElevenLabs,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
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

    override suspend fun listModels(
        providerSetting: TTSProviderSetting.ElevenLabs
    ): List<TTSModelInfo> = withContext(me.rerere.tts.provider.ttsIoDispatcher) {
        if (providerSetting.apiKey.isBlank()) return@withContext emptyList()
        runCatching {
            val response = httpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = "https://api.elevenlabs.io/v1/models",
                    headers = mapOf(
                        "xi-api-key" to providerSetting.apiKey,
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
            ttsJson.parseToJsonElement(response.body.decodeToString())
                .jsonArrayOrNull
                .orEmpty()
                .mapNotNull { element ->
                    val item = element.jsonObjectOrNull ?: return@mapNotNull null
                    val id = item["model_id"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
                    val name = item["name"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty().ifBlank { id }
                    id.takeIf(String::isNotBlank)?.let { TTSModelInfo(id = it, displayName = name) }
                }
        }.getOrElse { e ->
            PlatformLog.e(TAG, "listModels error: ${e.message}")
            emptyList()
        }
    }
}
