package me.rerere.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches available STT models from an OpenAI-compatible provider's `/models` endpoint.
 *
 * For OpenRouter, appends `?output_modalities=transcription` to filter to STT models only.
 * For all other providers, returns every model id (the caller may further filter).
 *
 * Returns a list of model id strings sorted alphabetically. On any error returns an
 * empty list — the caller should fall back to a free-text model field.
 */
suspend fun fetchSttModels(
    httpClient: OkHttpClient,
    baseUrl: String,
    apiKey: String,
): List<String> = withContext(Dispatchers.IO) {
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
                    if (id.isNotEmpty()) add(id)
                }
            }.sorted()
        }
    }.getOrDefault(emptyList())
}
