package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "GeminiTTSProvider"

@OptIn(ExperimentalEncodingApi::class)
class GeminiTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.Gemini> {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class GeminiTTSResponse(
        val candidates: List<Candidate>
    )

    @Serializable
    data class Candidate(
        val content: Content
    )

    @Serializable
    data class Content(
        val parts: List<Part>
    )

    @Serializable
    data class Part(
        val inlineData: InlineData
    )

    @Serializable
    data class InlineData(
        val data: String,
        val mimeType: String
    )

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.Gemini,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", request.text)
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray {
                    add(JsonPrimitive("AUDIO"))
                })
                put("speechConfig", buildJsonObject {
                    put("voiceConfig", buildJsonObject {
                        put("prebuiltVoiceConfig", buildJsonObject {
                            put("voiceName", providerSetting.voiceName)
                        })
                    })
                })
            })
            put("model", providerSetting.model)
        }

        PlatformLog.i(
            TAG,
            "generateSpeech: model=${providerSetting.model}, " +
                "voice=${providerSetting.voiceName}, textLength=${request.text.length}"
        )

        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/models/${providerSetting.model}:generateContent",
                headers = mapOf(
                    "x-goog-api-key" to providerSetting.apiKey,
                    "Content-Type" to "application/json",
                ),
                body = requestBody.toString().encodeToByteArray(),
                mediaType = "application/json",
            )
        )

        if (response.statusCode !in 200..299) {
            val errorBody = response.body.decodeToString()
            PlatformLog.e(TAG, "TTS request failed: ${response.statusCode} $errorBody")
            throw Exception("Gemini TTS failed: $errorBody")
        }

        val responseJson = response.body.decodeToString()
        val geminiResponse = json.decodeFromString<GeminiTTSResponse>(responseJson)

        if (geminiResponse.candidates.isEmpty() ||
            geminiResponse.candidates[0].content.parts.isEmpty()
        ) {
            throw Exception("No audio data returned from Gemini TTS")
        }

        val audioBase64 = geminiResponse.candidates[0].content.parts[0].inlineData.data
        val audioData = Base64.Default.decode(audioBase64)

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.PCM,
                sampleRate = 24000, // Gemini TTS returns 24kHz 16-bit mono PCM
                isLast = true,
                metadata = mapOf(
                    "provider" to "gemini",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voiceName,
                    "sampleRate" to "24000",
                    "channels" to "1",
                    "bitDepth" to "16"
                )
            )
        )
    }

    override suspend fun listModels(
        providerSetting: TTSProviderSetting.Gemini
    ): List<TTSModelInfo> = withContext(me.rerere.tts.provider.ttsIoDispatcher) {
        if (providerSetting.apiKey.isBlank()) return@withContext emptyList()
        runCatching {
            val response = httpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = "${providerSetting.baseUrl}/models",
                    headers = mapOf(
                        "x-goog-api-key" to providerSetting.apiKey,
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
            ttsJson.parseToJsonElement(body)
                .jsonObjectOrNull
                ?.get("models")
                ?.jsonArrayOrNull
                .orEmpty()
                .mapNotNull { element ->
                    val rawName = element.jsonObjectOrNull
                        ?.get("name")
                        ?.jsonPrimitiveOrNull
                        ?.contentOrNull
                        .orEmpty()
                    val id = rawName.removePrefix("models/").removePrefix("tunedModels/")
                    val lower = id.lowercase()
                    id.takeIf {
                        it.isNotBlank() &&
                            (lower.contains("tts") || lower.contains("speech") || lower.endsWith("-tts"))
                    }?.let { TTSModelInfo(id = it, displayName = it) }
                }
        }.getOrElse { e ->
            PlatformLog.e(TAG, "listModels error: ${e.message}")
            emptyList()
        }
    }
}
