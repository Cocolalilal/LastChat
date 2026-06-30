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

private const val PERPLEXITY_ENDPOINT = "https://api.perplexity.ai/search"
private const val TAG = "PerplexitySearchService"

object PerplexitySearchService : SearchService<SearchServiceOptions.PerplexityOptions> {
    override val name: String = "Perplexity"

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
        serviceOptions: SearchServiceOptions.PerplexityOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (serviceOptions.apiKey.isBlank()) {
                error("Perplexity API key is required")
            }

            val query = params["query"]?.jsonPrimitive?.content
                ?: error("query is required")

            val body = buildJsonObject {
                put("query", JsonPrimitive(query))
                put("max_results", JsonPrimitive(commonOptions.resultSize))
                serviceOptions.maxTokensPerPage?.let {
                    if (it > 0) {
                        put("max_tokens_per_page", JsonPrimitive(it))
                    }
                }
            }

            println("$TAG search: $body")

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = PERPLEXITY_ENDPOINT,
                    headers = mapOf(
                        "Authorization" to "Bearer ${serviceOptions.apiKey}",
                        "Content-Type" to "application/json"
                    ),
                    body = body.toString().encodeToByteArray()
                )
            )
            if (response.statusCode in 200..299) {
                val responseBody = json.decodeFromString<PerplexityResponse>(response.body.decodeToString())

                val items = responseBody.results
                    .filter { !it.title.isNullOrBlank() && !it.url.isNullOrBlank() }
                    .take(commonOptions.resultSize)
                    .map {
                        SearchResultItem(
                            title = it.title!!,
                            url = it.url!!,
                            text = it.snippet ?: it.text ?: ""
                        )
                    }

                return@withContext Result.success(
                    SearchResult(
                        answer = responseBody.answer,
                        items = items
                    )
                )
            } else {
                error("response failed #${response.statusCode}: ${response.body.decodeToString()}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.PerplexityOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Perplexity"))
    }

    @Serializable
    private data class PerplexityResponse(
        val answer: String? = null,
        val results: List<ResultItem> = emptyList()
    ) {
        @Serializable
        data class ResultItem(
            val title: String? = null,
            val url: String? = null,
            val snippet: String? = null,
            @SerialName("text") val text: String? = null,
        )
    }
}
