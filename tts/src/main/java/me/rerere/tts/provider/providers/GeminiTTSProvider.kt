package me.rerere.tts.provider.providers

import kotlinx.coroutines.CancellationException
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
import me.rerere.common.http.urlEncode
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSModelInfo
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TTSVoice
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
        val candidates: List<Candidate> = emptyList(),
        val promptFeedback: PromptFeedback? = null
    )

    @Serializable
    data class PromptFeedback(val blockReason: String? = null)

    @Serializable
    data class Candidate(
        val content: Content? = null,
        val finishReason: String? = null,
        val finishMessage: String? = null,
    )

    @Serializable
    data class Content(
        val parts: List<Part> = emptyList()
    )

    @Serializable
    data class Part(
        val inlineData: InlineData? = null
    )

    @Serializable
    data class InlineData(
        val data: String,
        val mimeType: String = "audio/L16;codec=pcm;rate=24000"
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
                        if (usesModernGeminiVoices(providerSetting.model)) {
                            put("voice", providerSetting.voiceName)
                        } else {
                            put("prebuiltVoiceConfig", buildJsonObject {
                                put("voiceName", providerSetting.voiceName)
                            })
                        }
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
                url = "${providerSetting.baseUrl.trimEnd('/')}/models/${providerSetting.model.removePrefix("models/")}:generateContent",
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

        val candidate = geminiResponse.candidates.firstOrNull()
            ?: error("Gemini returned no speech: ${geminiResponse.promptFeedback?.blockReason ?: "no candidate"}")
        require(candidate.finishReason == null || candidate.finishReason == "STOP") {
            "Gemini speech was not completed: ${candidate.finishReason}. ${candidate.finishMessage.orEmpty()}"
        }
        val parts = candidate.content?.parts.orEmpty().mapNotNull { it.inlineData }
        require(parts.isNotEmpty()) { "No audio data returned from Gemini TTS" }
        // Decode all parts before publishing anything: malformed/truncated responses must not
        // enter the playlist as successful partial speech.
        val chunks = parts.map { part -> decodeGeminiAudio(Base64.Default.decode(part.data), part.mimeType) }
        val first = chunks.first()
        require(chunks.all { it.format == first.format && it.sampleRate == first.sampleRate }) {
            "Gemini changed audio format within one speech response"
        }
        require(chunks.size == 1 || first.format == AudioFormat.PCM) {
            "Gemini returned multiple compressed audio containers"
        }
        chunks.forEachIndexed { index, chunk ->
            emit(chunk.copy(isLast = index == chunks.lastIndex, metadata = chunk.metadata + mapOf(
                "provider" to "gemini", "model" to providerSetting.model, "voice" to providerSetting.voiceName,
            )))
        }
    }

    /** Google's live prebuilt catalog, including voices added after this app was released. */
    suspend fun listVoices(providerSetting: TTSProviderSetting.Gemini): List<TTSVoice> =
        withContext(me.rerere.tts.provider.ttsIoDispatcher) {
            require(providerSetting.apiKey.isNotBlank()) { "A Gemini API key is required to fetch voices" }
            val voices = mutableListOf<TTSVoice>()
            val visitedPages = mutableSetOf<String>()
            var pageToken: String? = null
            do {
                val page = pageToken?.let { "&page_token=${it.urlEncode()}" }.orEmpty()
                val response = httpClient.execute(PlatformHttpRequest(
                    method = "GET",
                    url = "${providerSetting.baseUrl.trimEnd('/')}/voices?type=prebuilt&page_size=1000$page",
                    headers = mapOf("x-goog-api-key" to providerSetting.apiKey),
                ))
                check(response.statusCode in 200..299) { "Gemini voice discovery failed: ${response.statusCode}" }
                val body = ttsJson.parseToJsonElement(response.body.decodeToString()).jsonObjectOrNull
                    ?: error("Invalid Gemini voices response")
                val entries = body["voices"]?.jsonArrayOrNull ?: error("Gemini voices response has no voices list")
                entries.forEach { entry ->
                    val voice = entry.jsonObjectOrNull ?: return@forEach
                    fun field(key: String) = voice[key]?.jsonPrimitiveOrNull?.contentOrNull
                    val type = field("type")?.lowercase()
                    if (type != null && type != "prebuilt" && type != "voice_type_prebuilt") return@forEach
                    val id = (field("id") ?: field("name")?.removePrefix("voices/"))
                        ?.takeIf(String::isNotBlank) ?: return@forEach
                    voices += TTSVoice(
                        name = (field("display_name") ?: field("displayName"))?.takeIf(String::isNotBlank) ?: id,
                        providerVoiceId = id,
                        locale = field("language_code") ?: field("languageCode"),
                        model = providerSetting.model,
                    )
                }
                pageToken = (body["next_page_token"] ?: body["nextPageToken"])
                    ?.jsonPrimitiveOrNull?.contentOrNull?.takeIf(String::isNotBlank)
                check(pageToken == null || visitedPages.add(pageToken)) { "Gemini repeated a voice page token" }
            } while (pageToken != null)
            voices.distinctBy { it.providerVoiceId }
        }

    override suspend fun listModels(
        providerSetting: TTSProviderSetting.Gemini
    ): List<TTSModelInfo> = withContext(me.rerere.tts.provider.ttsIoDispatcher) {
        if (providerSetting.apiKey.isBlank()) return@withContext emptyList()
        try {
            val models = mutableListOf<TTSModelInfo>()
            val visitedPages = mutableSetOf<String>()
            var pageToken: String? = null
            do {
                val query = pageToken?.let { "?pageToken=${it.urlEncode()}" }.orEmpty()
                val response = httpClient.execute(PlatformHttpRequest(
                    method = "GET", url = "${providerSetting.baseUrl.trimEnd('/')}/models$query",
                    headers = mapOf("x-goog-api-key" to providerSetting.apiKey),
                ))
                check(response.statusCode in 200..299) { "Gemini model discovery failed: ${response.statusCode}" }
                val body = ttsJson.parseToJsonElement(response.body.decodeToString()).jsonObjectOrNull
                    ?: error("Invalid Gemini models response")
                body["models"]?.jsonArrayOrNull.orEmpty().forEach { element ->
                    val model = element.jsonObjectOrNull ?: return@forEach
                    val id = model["name"]?.jsonPrimitiveOrNull?.contentOrNull
                        ?.removePrefix("models/")?.removePrefix("tunedModels/") ?: return@forEach
                    val methods = model["supportedGenerationMethods"]?.jsonArrayOrNull
                        ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
                    val lower = id.lowercase()
                    if ((lower.contains("tts") || lower.contains("speech")) &&
                        (methods == null || "generateContent" in methods)) {
                        models += TTSModelInfo(id, model["displayName"]?.jsonPrimitiveOrNull?.contentOrNull ?: id)
                    }
                }
                pageToken = body["nextPageToken"]?.jsonPrimitiveOrNull?.contentOrNull?.takeIf(String::isNotBlank)
                check(pageToken == null || visitedPages.add(pageToken)) { "Gemini repeated a model page token" }
            } while (pageToken != null)
            models.distinctBy { it.id }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PlatformLog.e(TAG, "listModels error: ${error.message}")
            emptyList()
        }
    }
}

/** Older preview models use the original prebuiltVoiceConfig wire format. */
internal fun usesModernGeminiVoices(model: String): Boolean {
    val version = Regex("^gemini-(\\d+)(?:\\.(\\d+))?").find(model.removePrefix("models/")) ?: return false
    val major = version.groupValues[1].toIntOrNull() ?: return false
    val minor = version.groupValues[2].toIntOrNull() ?: 0
    return major > 3 || (major == 3 && minor >= 8)
}
