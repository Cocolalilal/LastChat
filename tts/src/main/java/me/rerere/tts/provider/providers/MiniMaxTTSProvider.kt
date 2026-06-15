package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

private const val TAG = "MiniMaxTTSProvider"

@Serializable
private data class MiniMaxResponseData(
    val audio: String,
    val status: Int,
    val ced: String
)

@Serializable
private data class MiniMaxResponse(
    val data: MiniMaxResponseData
)

class MiniMaxTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.MiniMax> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.MiniMax,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("text", request.text)
            put("stream", true)
            put("output_format", "hex")
            put("stream_options", buildJsonObject {
                put("exclude_aggregated_audio", true)
            })
            put("voice_setting", buildJsonObject {
                put("voice_id", providerSetting.voiceId)
                put("emotion", providerSetting.emotion)
                put("speed", providerSetting.speed)
            })
        }

        PlatformLog.i(
            TAG,
            "generateSpeech: model=${providerSetting.model}, " +
                "voice=${providerSetting.voiceId}, emotion=${providerSetting.emotion}, " +
                "textLength=${request.text.length}"
        )

        var hasEmittedAudio = false

        httpClient.streamEvents(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/t2a_v2",
                headers = mapOf(
                    "Authorization" to "Bearer ${providerSetting.apiKey}",
                    "Content-Type" to "application/json",
                ),
                body = json.encodeToString(requestBody).encodeToByteArray(),
                mediaType = "application/json",
            )
        ).collect {
            when (it) {
                is PlatformServerEvent.Open -> PlatformLog.i(TAG, "SSE connection opened")
                is PlatformServerEvent.Event -> {
                    try {
                        val data = json.decodeFromString<MiniMaxResponse>(it.data)

                        // Convert hex string to bytes
                        val audioBytes = hexStringToBytes(data.data.audio)

                        emit(
                            AudioChunk(
                                data = audioBytes,
                                format = AudioFormat.MP3, // MiniMax returns MP3 format
                                sampleRate = 32000, // Default sample rate from MiniMax
                                isLast = false, // Will be set to true on last chunk
                                metadata = mapOf(
                                    "provider" to "minimax",
                                    "model" to providerSetting.model,
                                    "voice" to providerSetting.voiceId,
                                    "status" to data.data.status.toString(),
                                    "ced" to data.data.ced
                                )
                            )
                        )
                        hasEmittedAudio = true
                    } catch (e: Exception) {
                        PlatformLog.e(TAG, "Failed to process audio chunk: ${e.message}")
                    }
                }

                is PlatformServerEvent.Closed -> {
                    PlatformLog.i(TAG, "SSE connection closed")
                    // Emit final chunk if we haven't already
                    if (hasEmittedAudio) {
                        emit(
                            AudioChunk(
                                data = byteArrayOf(), // Empty data for last chunk
                                format = AudioFormat.MP3,
                                sampleRate = 32000,
                                isLast = true,
                                metadata = mapOf("provider" to "minimax")
                            )
                        )
                    }
                }

                is PlatformServerEvent.Failure -> {
                    val message = buildString {
                        append("MiniMax TTS streaming failed")
                        it.statusCode?.let { statusCode -> append(": $statusCode") }
                        it.body?.let { body -> append(" $body") }
                        it.message?.let { failureMessage -> append(" $failureMessage") }
                    }
                    PlatformLog.e(TAG, message)
                    throw Exception(message)
                }
            }
        }
    }
}

private fun hexStringToBytes(hexString: String): ByteArray {
    val cleanHex = hexString.replace("\\s+".toRegex(), "")
    val length = cleanHex.length

    // Check for even number of characters
    if (length % 2 != 0) {
        throw IllegalArgumentException("Hex string must have even number of characters")
    }

    val bytes = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        val hexByte = cleanHex.substring(i, i + 2)
        bytes[i / 2] = hexByte.toInt(16).toByte()
    }
    return bytes
}
