package me.rerere.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.http.urlEncode
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.json
import me.rerere.search.SearchService.Companion.platformHttpClient

private const val TAG = "BraveSearchService"

object BraveSearchService : SearchService<SearchServiceOptions.BraveOptions> {
    override val name: String = "Brave"

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
        serviceOptions: SearchServiceOptions.BraveOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://api.search.brave.com/res/v1/web/search" +
                    "?q=${query.urlEncode()}" +
                    "&count=${commonOptions.resultSize}"

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = url,
                    headers = mapOf(
                        "Accept" to "application/json",
                        "X-Subscription-Token" to serviceOptions.apiKey
                    )
                )
            )
            if (response.statusCode in 200..299) {
                val responseBody = response.body.decodeToString()
                val searchResponse = json.decodeFromString<BraveSearchResponse>(responseBody)

                val items = searchResponse.web?.results?.map { result ->
                    SearchResultItem(
                        title = result.title,
                        url = result.url,
                        text = result.description ?: ""
                    )
                } ?: emptyList()

                return@withContext Result.success(
                    SearchResult(
                        answer = null,
                        items = items
                    )
                )
            } else {
                error("Brave search failed with code ${response.statusCode}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BraveOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Brave"))
    }

    @Serializable
    data class BraveSearchResponse(
        val type: String? = null,
        val web: WebResults? = null,
    )

    @Serializable
    data class WebResults(
        val type: String? = null,
        val results: List<WebResult>? = null,
    )

    @Serializable
    data class WebResult(
        val type: String,
        val title: String,
        val url: String,
        val description: String? = null,
    )
}
