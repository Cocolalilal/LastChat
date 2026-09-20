package me.rerere.asr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.common.http.MultipartFormBody
import me.rerere.common.http.MultipartFormPart
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog

private const val TAG = "CloudSpeech"

data class CloudSpeechTranscriptionRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val wavBytes: ByteArray,
    val language: String? = null,
    val prompt: String? = null,
)

object CloudSpeechTranscription {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun transcribe(
        httpClient: PlatformHttpClient,
        request: CloudSpeechTranscriptionRequest,
    ): String {
        val base = request.baseUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "STT base URL is required" }
        require(request.apiKey.isNotBlank()) { "STT API key is required" }
        require(request.model.isNotBlank()) { "STT model is required" }
        require(request.wavBytes.isNotEmpty()) { "Recorded audio is empty" }

        val parts = buildList {
            add(
                MultipartFormPart.file(
                    name = "file",
                    filename = "speech.wav",
                    bytes = request.wavBytes,
                    contentType = "audio/wav",
                ),
            )
            add(MultipartFormPart.text("model", request.model))
            add(MultipartFormPart.text("response_format", "json"))
            request.language?.takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }?.let { language ->
                add(MultipartFormPart.text("language", language))
            }
            request.prompt?.takeIf(String::isNotBlank)?.let { prompt ->
                add(MultipartFormPart.text("prompt", prompt))
            }
        }
        val encoded = MultipartFormBody.encode(parts)
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "$base/audio/transcriptions",
                headers = mapOf("Authorization" to "Bearer ${request.apiKey.trim()}"),
                body = encoded.body,
                mediaType = encoded.contentType,
            ),
        )
        val body = response.body.decodeToString()
        if (response.statusCode !in 200..299) {
            PlatformLog.w(TAG, "STT failed HTTP ${response.statusCode}: ${body.take(500)}")
            error(errorMessage(body) ?: "Speech transcription failed: HTTP ${response.statusCode}")
        }
        val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val text = parsed.string("text")?.trim().orEmpty()
        require(text.isNotBlank()) { "The speech provider returned an empty transcript" }
        return text
    }

    private fun errorMessage(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val error = root["error"] as? JsonObject
        return error.string("message") ?: root.string("message")
    }

    private fun JsonObject?.string(key: String): String? {
        val primitive = this?.get(key) as? JsonPrimitive ?: return null
        return primitive.contentOrNull
    }
}
