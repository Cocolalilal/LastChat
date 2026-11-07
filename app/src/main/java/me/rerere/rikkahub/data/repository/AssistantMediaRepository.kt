package me.rerere.rikkahub.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.AssistantMediaCategory
import me.rerere.rikkahub.data.model.AssistantMediaItem
import me.rerere.rikkahub.data.service.TmdbService
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AssistantMediaRepo"

class AssistantMediaRepository(
    private val context: Context,
    private val tmdbService: TmdbService,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val mediaDirectory: File
        get() = File(context.filesDir, "assistant_media").apply {
            if (!exists()) mkdirs()
        }

    private val mutexMap = ConcurrentHashMap<String, Mutex>()
    private val cacheMap = ConcurrentHashMap<String, MutableStateFlow<List<AssistantMediaItem>>>()

    private fun getMutex(assistantId: String): Mutex =
        mutexMap.computeIfAbsent(assistantId) { Mutex() }

    private fun getFile(assistantId: String): File =
        File(mediaDirectory, "$assistantId.json")

    fun getMediaFlow(assistantId: String): StateFlow<List<AssistantMediaItem>> {
        return cacheMap.computeIfAbsent(assistantId) {
            val initial = loadFromFile(assistantId)
            MutableStateFlow(initial)
        }.asStateFlow()
    }

    fun getItem(assistantId: String, tmdbId: Long, mediaType: String): AssistantMediaItem? {
        val list = getMediaFlow(assistantId).value
        return list.firstOrNull { it.tmdbId == tmdbId && it.mediaType == mediaType }
    }

    private fun loadFromFile(assistantId: String): List<AssistantMediaItem> {
        val file = getFile(assistantId)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<AssistantMediaItem>>(file.readText())
        }.getOrElse { e ->
            Log.e(TAG, "Failed to load assistant media for $assistantId", e)
            emptyList()
        }
    }

    private suspend fun saveToFile(assistantId: String, items: List<AssistantMediaItem>) = withContext(Dispatchers.IO) {
        val mutex = getMutex(assistantId)
        mutex.withLock {
            runCatching {
                val file = getFile(assistantId)
                val tempFile = File(mediaDirectory, "$assistantId.json.tmp")
                tempFile.writeText(json.encodeToString(items))
                if (tempFile.exists()) {
                    tempFile.renameTo(file)
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to save assistant media for $assistantId", e)
            }
        }
    }

    suspend fun upsertItem(assistantId: String, item: AssistantMediaItem) {
        val flow = cacheMap.computeIfAbsent(assistantId) { MutableStateFlow(loadFromFile(assistantId)) }
        val current = flow.value.toMutableList()
        val index = current.indexOfFirst { it.tmdbId == item.tmdbId && it.mediaType == item.mediaType }
        val updatedItem = item.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) {
            current[index] = updatedItem
        } else {
            current.add(0, updatedItem)
        }
        flow.value = current
        saveToFile(assistantId, current)
    }

    suspend fun updateStatus(assistantId: String, tmdbId: Long, mediaType: String, status: String) {
        val flow = cacheMap.computeIfAbsent(assistantId) { MutableStateFlow(loadFromFile(assistantId)) }
        val current = flow.value.toMutableList()
        val index = current.indexOfFirst { it.tmdbId == tmdbId && it.mediaType == mediaType }
        if (index >= 0) {
            val item = current[index]
            current[index] = item.copy(status = status, updatedAt = System.currentTimeMillis())
            flow.value = current
            saveToFile(assistantId, current)
        }
    }

    suspend fun toggleEpisodeWatched(assistantId: String, tmdbId: Long, season: Int, episode: Int) {
        val flow = cacheMap.computeIfAbsent(assistantId) { MutableStateFlow(loadFromFile(assistantId)) }
        val current = flow.value.toMutableList()
        val index = current.indexOfFirst { it.tmdbId == tmdbId && it.mediaType == "tv" }
        if (index >= 0) {
            val item = current[index]
            val epKey = "s${season}e${episode}"
            val newSet = item.watchedEpisodes.toMutableSet()
            if (newSet.contains(epKey)) {
                newSet.remove(epKey)
            } else {
                newSet.add(epKey)
            }
            val nextStatus = if (item.status == AssistantMediaCategory.PLAN_TO_WATCH.name) {
                AssistantMediaCategory.WATCHING.name
            } else {
                item.status
            }
            current[index] = item.copy(
                watchedEpisodes = newSet,
                currentSeason = season,
                currentEpisode = episode,
                status = nextStatus,
                updatedAt = System.currentTimeMillis()
            )
            flow.value = current
            saveToFile(assistantId, current)
        }
    }

    suspend fun markSeasonWatched(assistantId: String, tmdbId: Long, season: Int, episodeCount: Int, watched: Boolean) {
        val flow = cacheMap.computeIfAbsent(assistantId) { MutableStateFlow(loadFromFile(assistantId)) }
        val current = flow.value.toMutableList()
        val index = current.indexOfFirst { it.tmdbId == tmdbId && it.mediaType == "tv" }
        if (index >= 0) {
            val item = current[index]
            val newSet = item.watchedEpisodes.toMutableSet()
            for (ep in 1..episodeCount) {
                val epKey = "s${season}e${ep}"
                if (watched) newSet.add(epKey) else newSet.remove(epKey)
            }
            current[index] = item.copy(
                watchedEpisodes = newSet,
                updatedAt = System.currentTimeMillis()
            )
            flow.value = current
            saveToFile(assistantId, current)
        }
    }

    suspend fun deleteItem(assistantId: String, tmdbId: Long, mediaType: String) {
        val flow = cacheMap.computeIfAbsent(assistantId) { MutableStateFlow(loadFromFile(assistantId)) }
        val current = flow.value.toMutableList()
        val removed = current.removeAll { it.tmdbId == tmdbId && it.mediaType == mediaType }
        if (removed) {
            flow.value = current
            saveToFile(assistantId, current)
        }
    }

    fun getCountsSummary(assistantId: String): String {
        val items = getMediaFlow(assistantId).value
        val watching = items.count { it.status == AssistantMediaCategory.WATCHING.name }
        val plan = items.count { it.status == AssistantMediaCategory.PLAN_TO_WATCH.name }
        return when {
            items.isEmpty() -> "Track shows & movies with your companion"
            watching > 0 && plan > 0 -> "$watching Watching · $plan Plan to Watch"
            watching > 0 -> "$watching Watching"
            plan > 0 -> "$plan Plan to Watch"
            else -> "${items.size} in library"
        }
    }
}
