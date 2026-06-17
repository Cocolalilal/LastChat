package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import org.json.JSONObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "QwenTTSProvider"

@OptIn(ExperimentalEncodingApi::class)
class QwenTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.Qwen> {
    override fun generateSpeech(
        providerSetting: TTSProviderSetting.Qwen,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("model", providerSetting.model)
            put("input", JSONObject().apply {
                put("text", request.text)
                put("voice", providerSetting.voice)
                put("language_type", providerSetting.languageType)
            })
        }

        PlatformLog.i(TAG, "generateSpeech: $requestBody")

        httpClient.streamEvents(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/services/aigc/multimodal-generation/generation",
                headers = mapOf(
                    "Authorization" to "Bearer ${providerSetting.apiKey}",
                    "Content-Type" to "application/json",
                    "X-DashScope-SSE" to "enable",
                ),
                body = requestBody.toString().encodeToByteArray(),
                mediaType = "application/json",
            )
        ).collect { event ->
            when (event) {
                is PlatformServerEvent.Open -> Unit
                PlatformServerEvent.Closed -> Unit
                is PlatformServerEvent.Event -> {
                    val result = parseSseData(event.data)
                    if (result != null) {
                        val (audioData, isLast) = result
                        emit(
                            AudioChunk(
                                data = audioData,
                                format = AudioFormat.PCM,
                                sampleRate = 24000,
                                isLast = isLast,
                                metadata = mapOf(
                                    "provider" to "qwen",
                                    "model" to providerSetting.model,
                                    "voice" to providerSetting.voice,
                                    "sampleRate" to "24000",
                                    "channels" to "1",
                                    "bitDepth" to "16"
                                )
                            )
                        )
                    }
                }

                is PlatformServerEvent.Failure -> {
                    val message = buildString {
                        append("Qwen TTS request failed")
                        event.statusCode?.let { statusCode -> append(": $statusCode") }
                        event.body?.let { body -> append(" $body") }
                        event.message?.let { failureMessage -> append(" $failureMessage") }
                    }
                    PlatformLog.e(TAG, message)
                    throw Exception(message)
                }
            }
        }
    }

    private fun parseSseData(data: String): Pair<ByteArray, Boolean>? {
        return try {
            val json = JSONObject(data)
            val output = json.optJSONObject("output") ?: return null
            val audio = output.optJSONObject("audio") ?: return null
            val audioBase64 = audio.optString("data", "")
            val finishReason = output.optString("finish_reason", "")

            if (audioBase64.isNotEmpty()) {
                val audioData = Base64.Default.decode(audioBase64)
                val isLast = finishReason == "stop"
                Pair(audioData, isLast)
            } else {
                null
            }
        } catch (e: Exception) {
            PlatformLog.e(TAG, "Failed to parse SSE data: $data ${e.message}")
            null
        }
    }
}
