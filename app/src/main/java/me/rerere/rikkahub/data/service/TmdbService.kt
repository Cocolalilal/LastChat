package me.rerere.rikkahub.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class TmdbService(
    private val okHttpClient: OkHttpClient,
    private val settingsStore: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Public default read key for basic discovery fallback when user hasn't set their own
    private val defaultBearerToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJkMGIwZjgyMWU2MWJkNTU5MmRhN2ZhODg5YTMxMTViYyIsIm5iZiI6MTczMDk5ODk3MC4xNjk2Niwic3ViIjoiNjc0MzY3YjA5MzU1YzMwMDE3YTRlZDMwIiwic2NvcGVzIjpbImFwaV9yZWFkIl0sInZlcnNpb24iOjF9.e-9FknJqD_WjK1K-3JdZ4Q6QzQY7yG8r1S-hJ8t1Q"

    private fun getAuthHeader(): String {
        val userToken = settingsStore.current.tmdbSetting.userAccessToken.trim()
        val token = userToken.ifBlank { defaultBearerToken }
        return "Bearer $token"
    }

    private fun getResolutionPrefix(isBackdrop: Boolean): String {
        val setting = settingsStore.current.tmdbSetting
        return if (isBackdrop) setting.backdropResolution else setting.posterResolution
    }

    suspend fun searchMedia(
        query: String,
        type: String = "multi",
        page: Int = 1,
        includeAdult: Boolean? = null
    ): JsonElement = withContext(Dispatchers.IO) {
        val adultFlag = includeAdult ?: settingsStore.current.tmdbSetting.includeAdult
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = if (type in listOf("movie", "tv")) type else "multi"
        val url = "https://api.themoviedb.org/3/search/$endpoint?query=$encoded&page=$page&include_adult=$adultFlag"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader())
            .header("Accept", "application/json")
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val results = parsed["results"]?.jsonArray ?: JsonArray(emptyList())

                val resPrefix = getResolutionPrefix(false)
                val backPrefix = getResolutionPrefix(true)

                val mapped = buildJsonArray {
                    results.take(10).forEach { item ->
                        val obj = item.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
                        val mediaType = obj["media_type"]?.jsonPrimitive?.contentOrNull ?: endpoint
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull
                            ?: obj["name"]?.jsonPrimitive?.contentOrNull
                            ?: "Unknown"
                        val overview = obj["overview"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val posterPath = obj["poster_path"]?.jsonPrimitive?.contentOrNull
                        val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.contentOrNull
                        val vote = obj["vote_average"]?.jsonPrimitive?.contentOrNull ?: "0.0"
                        val release = obj["release_date"]?.jsonPrimitive?.contentOrNull
                            ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull
                            ?: ""
                        val year = release.take(4)

                        add(buildJsonObject {
                            put("id", id)
                            put("media_type", mediaType)
                            put("title", title)
                            put("year", year)
                            put("rating", vote)
                            put("overview", overview.take(240))
                            if (posterPath != null) {
                                put("poster", "https://image.tmdb.org/t/p/$resPrefix$posterPath")
                            }
                            if (backdropPath != null) {
                                put("backdrop", "https://image.tmdb.org/t/p/$backPrefix$backdropPath")
                            }
                        })
                    }
                }
                buildJsonObject {
                    put("total_results", parsed["total_results"]?.jsonPrimitive?.intOrNull ?: mapped.size)
                    put("results", mapped)
                }
            }
        }.getOrElse { e ->
            buildJsonObject { put("error", e.message ?: "Failed to search TMDB") }
        }
    }

    suspend fun getDetails(id: Int, type: String = "tv"): JsonElement = withContext(Dispatchers.IO) {
        val endpoint = if (type.lowercase() == "movie") "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$endpoint/$id?append_to_response=credits"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader())
            .header("Accept", "application/json")
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body).jsonObject

                val title = obj["title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: "Unknown"
                val overview = obj["overview"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val vote = obj["vote_average"]?.jsonPrimitive?.contentOrNull ?: "0.0"
                val posterPath = obj["poster_path"]?.jsonPrimitive?.contentOrNull
                val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.contentOrNull
                val genres = obj["genres"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull } ?: emptyList()
                val release = obj["release_date"]?.jsonPrimitive?.contentOrNull
                    ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val epCount = obj["number_of_episodes"]?.jsonPrimitive?.intOrNull
                val seasonCount = obj["number_of_seasons"]?.jsonPrimitive?.intOrNull
                val status = obj["status"]?.jsonPrimitive?.contentOrNull.orEmpty()

                val resPrefix = getResolutionPrefix(false)
                val backPrefix = getResolutionPrefix(true)

                buildJsonObject {
                    put("id", id)
                    put("media_type", endpoint)
                    put("title", title)
                    put("year", release.take(4))
                    put("rating", vote)
                    put("status", status)
                    if (epCount != null) put("number_of_episodes", epCount)
                    if (seasonCount != null) put("number_of_seasons", seasonCount)
                    put("genres", JsonArray(genres.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    put("overview", overview)
                    if (posterPath != null) {
                        put("poster", "https://image.tmdb.org/t/p/$resPrefix$posterPath")
                    }
                    if (backdropPath != null) {
                        put("backdrop", "https://image.tmdb.org/t/p/$backPrefix$backdropPath")
                    }
                }
            }
        }.getOrElse { e ->
            buildJsonObject { put("error", e.message ?: "Failed to fetch TMDB details") }
        }
    }

    suspend fun getImages(id: Int, type: String = "tv"): JsonElement = withContext(Dispatchers.IO) {
        val endpoint = if (type.lowercase() == "movie") "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$endpoint/$id/images?include_image_language=en,null"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader())
            .header("Accept", "application/json")
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject

                val posters = parsed["posters"]?.jsonArray ?: JsonArray(emptyList())
                val backdrops = parsed["backdrops"]?.jsonArray ?: JsonArray(emptyList())

                val resPrefix = getResolutionPrefix(false)
                val backPrefix = getResolutionPrefix(true)

                val posterUrls = buildJsonArray {
                    posters.take(8).forEach { p ->
                        val path = p.jsonObject["file_path"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        add(kotlinx.serialization.json.JsonPrimitive("https://image.tmdb.org/t/p/$resPrefix$path"))
                    }
                }

                val backdropUrls = buildJsonArray {
                    backdrops.take(8).forEach { b ->
                        val path = b.jsonObject["file_path"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        add(kotlinx.serialization.json.JsonPrimitive("https://image.tmdb.org/t/p/$backPrefix$path"))
                    }
                }

                buildJsonObject {
                    put("id", id)
                    put("media_type", endpoint)
                    put("posters", posterUrls)
                    put("backdrops", backdropUrls)
                }
            }
        }.getOrElse { e ->
            buildJsonObject { put("error", e.message ?: "Failed to fetch images from TMDB") }
        }
    }

    suspend fun getRecommendations(id: Int, type: String = "tv"): JsonElement = withContext(Dispatchers.IO) {
        val endpoint = if (type.lowercase() == "movie") "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$endpoint/$id/recommendations"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader())
            .header("Accept", "application/json")
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val results = parsed["results"]?.jsonArray ?: JsonArray(emptyList())

                val resPrefix = getResolutionPrefix(false)
                val mapped = buildJsonArray {
                    results.take(6).forEach { item ->
                        val obj = item.jsonObject
                        val recId = obj["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull
                            ?: obj["name"]?.jsonPrimitive?.contentOrNull
                            ?: "Unknown"
                        val vote = obj["vote_average"]?.jsonPrimitive?.contentOrNull ?: "0.0"
                        val posterPath = obj["poster_path"]?.jsonPrimitive?.contentOrNull
                        val release = obj["release_date"]?.jsonPrimitive?.contentOrNull
                            ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull
                            ?: ""

                        add(buildJsonObject {
                            put("id", recId)
                            put("media_type", endpoint)
                            put("title", title)
                            put("year", release.take(4))
                            put("rating", vote)
                            if (posterPath != null) {
                                put("poster", "https://image.tmdb.org/t/p/$resPrefix$posterPath")
                            }
                        })
                    }
                }
                buildJsonObject { put("recommendations", mapped) }
            }
        }.getOrElse { e ->
            buildJsonObject { put("error", e.message ?: "Failed to fetch recommendations from TMDB") }
        }
    }

    suspend fun manageWatchlist(action: String, id: Int? = null, type: String = "tv"): JsonElement = withContext(Dispatchers.IO) {
        val userToken = settingsStore.current.tmdbSetting.userAccessToken.trim()
        val accountId = settingsStore.current.tmdbSetting.accountId.trim()

        if (userToken.isBlank() || accountId.isBlank()) {
            return@withContext buildJsonObject {
                put("error", "TMDB account not connected. Please authorize or add your v4 User Access Token in Settings -> Plugins -> Movies & TV.")
            }
        }

        val endpoint = if (type.lowercase() == "movie") "movie" else "tv"

        if (action.lowercase() in listOf("add", "remove")) {
            val mediaId = id ?: return@withContext buildJsonObject { put("error", "id is required for add/remove") }
            val isWatchlist = action.lowercase() == "add"
            val url = "https://api.themoviedb.org/3/account/$accountId/watchlist"
            val bodyJson = """{"media_type":"$endpoint","media_id":$mediaId,"watchlist":$isWatchlist}"""

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $userToken")
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    val resBody = response.body?.string().orEmpty()
                    buildJsonObject {
                        put("success", response.isSuccessful)
                        put("action", action)
                        put("id", mediaId)
                        put("media_type", endpoint)
                        put("status_code", response.code)
                    }
                }
            }.getOrElse { e ->
                buildJsonObject { put("error", e.message ?: "Watchlist mutation failed") }
            }
        } else {
            // Read watchlist
            val url = "https://api.themoviedb.org/4/account/$accountId/$endpoint/watchlist"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $userToken")
                .header("Accept", "application/json")
                .build()

            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    val resBody = response.body?.string().orEmpty()
                    val parsed = json.parseToJsonElement(resBody).jsonObject
                    parsed
                }
            }.getOrElse { e ->
                buildJsonObject { put("error", e.message ?: "Failed to read watchlist") }
            }
        }
    }
}
