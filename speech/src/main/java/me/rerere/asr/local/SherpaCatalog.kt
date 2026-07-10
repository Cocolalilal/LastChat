package me.rerere.asr.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class SherpaCatalog(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: SherpaModelCatalog? = null

    suspend fun catalog(): SherpaModelCatalog = withContext(Dispatchers.IO) {
        cached ?: context.assets.open("sherpa_stt_catalog.json")
            .bufferedReader()
            .use { json.decodeFromString<SherpaModelCatalog>(it.readText()) }
            .also { cached = it }
    }

    suspend fun metadataFor(id: String): SherpaModelMetadata? =
        catalog().models.firstOrNull { it.id == id }
}
