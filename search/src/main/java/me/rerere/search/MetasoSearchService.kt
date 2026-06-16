package me.rerere.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.json
import me.rerere.search.SearchService.Companion.platformHttpClient

object MetasoSearchService : SearchService<SearchServiceOptions.MetasoOptions> {
    override val name: String = "Metaso"

    override val parameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override val scrapingParameters: InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.MetasoOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")

            val requestBody = buildJsonObject {
                put("q", JsonPrimitive(query))
                put("scope", JsonPrimitive("webpage"))
                put("size", JsonPrimitive(commonOptions.resultSize))
                put("includeSummary", JsonPrimitive(false))
            }

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = "https://metaso.cn/api/v1/search",
                    headers = mapOf(
                        "Authorization" to "Bearer ${serviceOptions.apiKey}",
                        "Accept" to "application/json",
                        "Content-Type" to "application/json"
                    ),
                    body = requestBody.toString().encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode in 200..299) {
                val bodyRaw = response.body.decodeToString()
                val searchResponse = runCatching {
                    json.decodeFromString<MetasoSearchResponse>(bodyRaw)
                }.onFailure {
                    it.printStackTrace()
                    println("Failed to decode Metaso response: $bodyRaw")
                    error("Failed to decode response: $bodyRaw")
                }.getOrThrow()

                return@withContext Result.success(
                    SearchResult(
                        items = searchResponse.webpages.map { webpage ->
                            SearchResultItem(
                                title = webpage.title,
                                url = webpage.link,
                                text = webpage.snippet ?: ""
                            )
                        }
                    )
                )
            } else {
                val errorBody = response.body.decodeToString()
                println("Metaso search failed with code ${response.statusCode}: $errorBody")
                error("Search request failed with code ${response.statusCode}: $errorBody")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.MetasoOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Metaso"))
    }

    @Serializable
    data class MetasoSearchResponse(
        @SerialName("credits")
        val credits: Int,
        @SerialName("searchParameters")
        val searchParameters: MetasoSearchParameters,
        @SerialName("webpages")
        val webpages: List<MetasoWebpage>
    )

    @Serializable
    data class MetasoSearchParameters(
        @SerialName("q")
        val query: String,
        @SerialName("scope")
        val scope: String,
        @SerialName("size")
        val size: Int,
    )

    @Serializable
    data class MetasoWebpage(
        @SerialName("title")
        val title: String,
        @SerialName("link")
        val link: String,
        @SerialName("score")
        val score: String,
        @SerialName("snippet")
        val snippet: String?,
        @SerialName("summary")
        val summary: String?,
        @SerialName("position")
        val position: Int,
        @SerialName("date")
        val date: String,
    )
}
