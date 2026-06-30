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

object BochaSearchService : SearchService<SearchServiceOptions.BochaOptions> {
    override val name: String = "Bocha"

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
        serviceOptions: SearchServiceOptions.BochaOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")

            val body = buildJsonObject {
                put("query", JsonPrimitive(query))
                put("summary", JsonPrimitive(serviceOptions.summary))
                put("count", JsonPrimitive(commonOptions.resultSize))
            }

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = "https://api.bochaai.com/v1/web-search",
                    headers = mapOf(
                        "Authorization" to "Bearer ${serviceOptions.apiKey}",
                        "Content-Type" to "application/json"
                    ),
                    body = json.encodeToString(body).encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode in 200..299) {
                val bodyRaw = response.body.decodeToString()
                val bochaResponse = runCatching {
                    json.decodeFromString<BochaResponse>(bodyRaw)
                }.onFailure {
                    it.printStackTrace()
                    println(bodyRaw)
                    error("Failed to decode response: $bodyRaw")
                }.getOrThrow()

                if (bochaResponse.code != 200) {
                    error("Bocha API error: ${bochaResponse.msg ?: "Unknown error"}")
                }

                return@withContext Result.success(
                    SearchResult(
                        items = bochaResponse.data?.webPages?.value?.map {
                            SearchResultItem(
                                title = it.name,
                                url = it.url,
                                text = it.summary ?: it.snippet,
                            )
                        } ?: emptyList()
                    )
                )
            } else {
                println(response.body.decodeToString())
                error("response failed #${response.statusCode}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BochaOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Bocha"))
    }

    @Serializable
    data class BochaResponse(
        @SerialName("code")
        val code: Int,
        @SerialName("log_id")
        val logId: String? = null,
        @SerialName("msg")
        val msg: String? = null,
        @SerialName("data")
        val data: BochaData? = null
    )

    @Serializable
    data class BochaData(
        @SerialName("_type")
        val type: String? = null,
        @SerialName("queryContext")
        val queryContext: BochaQueryContext? = null,
        @SerialName("webPages")
        val webPages: BochaWebPages? = null,
    )

    @Serializable
    data class BochaQueryContext(
        @SerialName("originalQuery")
        val originalQuery: String
    )

    @Serializable
    data class BochaWebPages(
        @SerialName("webSearchUrl")
        val webSearchUrl: String? = null,
        @SerialName("totalEstimatedMatches")
        val totalEstimatedMatches: Long? = null,
        @SerialName("value")
        val value: List<BochaWebPage> = emptyList(),
        @SerialName("someResultsRemoved")
        val someResultsRemoved: Boolean? = null
    )

    @Serializable
    data class BochaWebPage(
        @SerialName("id")
        val id: String? = null,
        @SerialName("name")
        val name: String,
        @SerialName("url")
        val url: String,
        @SerialName("displayUrl")
        val displayUrl: String? = null,
        @SerialName("snippet")
        val snippet: String,
        @SerialName("summary")
        val summary: String? = null,
        @SerialName("siteName")
        val siteName: String? = null,
        @SerialName("siteIcon")
        val siteIcon: String? = null,
        @SerialName("dateLastCrawled")
        val dateLastCrawled: String? = null,
        @SerialName("cachedPageUrl")
        val cachedPageUrl: String? = null,
        @SerialName("language")
        val language: String? = null,
        @SerialName("isFamilyFriendly")
        val isFamilyFriendly: Boolean? = null,
        @SerialName("isNavigational")
        val isNavigational: Boolean? = null
    )
}
