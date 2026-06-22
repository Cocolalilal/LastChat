package me.rerere.tts.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSModelInfo
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import org.json.JSONObject

private const val TAG = "PlayHTTTSProvider"

class PlayHTTTSProvider(
    private val httpClient: PlatformHttpClient,
) : TTSProvider<TTSProviderSetting.PlayHT> {

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.PlayHT,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("text", request.text)
            put("voice", providerSetting.voice)
            put("voice_engine", providerSetting.voiceEngine)
            put("quality", providerSetting.quality)
            put("output_format", providerSetting.outputFormat)
            put("speed", providerSetting.speed.toDouble())
        }

        PlatformLog.i(
            TAG,
            "generateSpeech: voice=${providerSetting.voice}, " +
                "engine=${providerSetting.voiceEngine}, textLength=${request.text.length}"
        )

        var audioEmitted = false

        httpClient.streamEvents(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/tts",
                headers = mapOf(
                    "AUTHORIZATION" to "Bearer ${providerSetting.apiKey}",
                    "X_USER_ID" to providerSetting.userId,
                    "Content-Type" to "application/json",
                    "accept" to "text/event-stream",
                ),
                body = requestBody.toString().encodeToByteArray(),
                mediaType = "application/json",
            )
        ).collect { event ->
            when (event) {
                is PlatformServerEvent.Open -> PlatformLog.i(TAG, "SSE connection opened")

                is PlatformServerEvent.Event -> {
                    if (!audioEmitted) {
                        val data = event.data
                        runCatching {
                            val json = JSONObject(data)
                            val audioUrl = json.optString("url", "")
                                .ifBlank { json.optString("audio", "") }
                                .ifBlank { json.optString("output", "") }

                            if (audioUrl.startsWith("http")) {
                                val audioResponse = httpClient.execute(
                                    PlatformHttpRequest(
                                        method = "GET",
                                        url = audioUrl,
                                    )
                                )
                                if (audioResponse.statusCode in 200..299 && audioResponse.body.isNotEmpty()) {
                                    val format = when (providerSetting.outputFormat.lowercase()) {
                                        "wav" -> AudioFormat.WAV
                                        "ogg" -> AudioFormat.OGG
                                        "flac" -> AudioFormat.WAV
                                        "mulaw" -> AudioFormat.PCM
                                        else -> AudioFormat.MP3
                                    }
                                    emit(
                                        AudioChunk(
                                            data = audioResponse.body,
                                            format = format,
                                            isLast = true,
                                            metadata = mapOf(
                                                "provider" to "playht",
                                                "voice" to providerSetting.voice,
                                                "engine" to providerSetting.voiceEngine
                                            )
                                        )
                                    )
                                    audioEmitted = true
                                }
                            }
                        }
                    }
                }

                is PlatformServerEvent.Closed -> {
                    if (!audioEmitted) {
                        emit(
                            AudioChunk(
                                data = byteArrayOf(),
                                format = AudioFormat.MP3,
                                isLast = true,
                                metadata = mapOf("provider" to "playht", "error" to "no_audio")
                            )
                        )
                    }
                }

                is PlatformServerEvent.Failure -> {
                    val message = buildString {
                        append("PlayHT TTS failed")
                        event.statusCode?.let { append(": $it") }
                        event.body?.let { append(" $it") }
                        event.message?.let { append(" $it") }
                    }
                    PlatformLog.e(TAG, message)
                    throw Exception(message)
                }
            }
        }
    }

    override suspend fun listModels(
        providerSetting: TTSProviderSetting.PlayHT
    ): List<TTSModelInfo> = listOf(
        TTSModelInfo("PlayHT2.0", "PlayHT 2.0"),
        TTSModelInfo("PlayHT1.0", "PlayHT 1.0"),
    )
}
