package me.rerere.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class SttModel(
    val id: String,
    val name: String,
    val isEmbedding: Boolean,
    val isImage: Boolean,
    val isAudio: Boolean
)

/**
 * Fetches available STT models from an OpenAI-compatible provider's `/models` endpoint.
 *
 * For OpenRouter, appends `?output_modalities=transcription` to filter to STT models only.
 * For all other providers, returns every model id (the caller may further filter).
 */
suspend fun fetchSttModels(
    httpClient: OkHttpClient,
    baseUrl: String,
    apiKey: String,
): List<SttModel> = withContext(Dispatchers.IO) {
    runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val url = if (cleanBase.contains("openrouter.ai", ignoreCase = true)) {
            "$cleanBase/models?output_modalities=transcription"
        } else {
            "$cleanBase/models"
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return@use emptyList()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@use emptyList()
            val data = json.optJSONArray("data") ?: return@use emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val modelObj = data.optJSONObject(i) ?: continue
                    val id = modelObj.optString("id", "")
                    if (id.isEmpty()) continue
                    
                    val name = modelObj.optString("name", id).ifBlank { id }
                    val architecture = modelObj.optJSONObject("architecture")
                    
                    var isEmbedding = id.contains("embed", ignoreCase = true)
                    var isImage = false
                    var isAudio = false
                    
                    if (architecture != null) {
                        val modality = architecture.optString("modality", "")
                        if (modality.contains("embedding", ignoreCase = true)) isEmbedding = true
                        
                        val inMod = architecture.optJSONArray("input_modalities")
                        val outMod = architecture.optJSONArray("output_modalities")
                        
                        val allModalities = buildSet {
                            if (inMod != null) {
                                for (j in 0 until inMod.length()) add(inMod.optString(j, "").lowercase())
                            }
                            if (outMod != null) {
                                for (j in 0 until outMod.length()) add(outMod.optString(j, "").lowercase())
                            }
                        }
                        
                        if (allModalities.any { it.contains("embedding") }) isEmbedding = true
                        if (allModalities.any { it.contains("image") }) isImage = true
                        if (allModalities.any { it.contains("audio") || it.contains("transcription") }) isAudio = true
                    } else {
                        // Fallback heuristics based on ID if architecture info is missing
                        if (id.contains("whisper", ignoreCase = true) || id.contains("audio", ignoreCase = true) || id.contains("stt", ignoreCase = true) || id.contains("speech", ignoreCase = true)) {
                            isAudio = true
                        }
                        if (id.contains("vision", ignoreCase = true) || id.contains("image", ignoreCase = true)) {
                            isImage = true
                        }
                    }
                    
                    add(SttModel(id, name, isEmbedding, isImage, isAudio))
                }
            }.sortedBy { it.id }
        }
    }.getOrDefault(emptyList())
}
