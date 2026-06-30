package me.rerere.tts.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", request.text)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("AUDIO")
                })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
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
    ): List<TTSModelInfo> = withContext(Dispatchers.IO) {
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
            val json = JSONObject(body)
            val arr = json.optJSONArray("models") as? JSONArray
            arr?.let { a ->
                buildList {
                    for (i in 0 until a.length()) {
                        val item = a.optJSONObject(i) ?: continue
                        val rawName = item.optString("name", "")
                        val id = rawName.removePrefix("models/").removePrefix("tunedModels/")
                        val lower = id.lowercase()
                        if (lower.contains("tts") || lower.contains("speech") || lower.endsWith("-tts")) {
                            add(TTSModelInfo(id = id, displayName = id))
                        }
                    }
                }
            } ?: emptyList()
        }.getOrElse { e ->
            PlatformLog.e(TAG, "listModels error: ${e.message}")
            emptyList()
        }
    }
}
