package me.rerere.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private const val TAG = "LinkUpService"

object LinkUpService : SearchService<SearchServiceOptions.LinkUpOptions> {
    override val name: String = "LinkUp"

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

    override val scrapingParameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "url to scrape")
                })
            },
            required = listOf("url")
        )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.LinkUpOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val body = buildJsonObject {
                put("q", JsonPrimitive(query))
                put("depth", JsonPrimitive(serviceOptions.depth))
                put("outputType", JsonPrimitive("sourcedAnswer"))
                put("includeImages", JsonPrimitive("false"))
            }

            println("$TAG search: $query")

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = "https://api.linkup.so/v1/search",
                    headers = mapOf(
                        "Authorization" to "Bearer ${serviceOptions.apiKey}",
                        "Content-Type" to "application/json"
                    ),
                    body = body.toString().encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode in 200..299) {
                val responseBody = response.body.decodeToString().let {
                    json.decodeFromString<LinkUpSearchResponse>(it)
                }

                return@withContext Result.success(
                    SearchResult(
                        answer = responseBody.answer,
                        items = responseBody.sources.take(commonOptions.resultSize).map {
                            SearchResultItem(
                                title = it.name,
                                url = it.url,
                                text = it.snippet
                            )
                        }
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
        serviceOptions: SearchServiceOptions.LinkUpOptions
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = params["url"]?.jsonPrimitive?.content ?: error("url is required")
            val body = buildJsonObject {
                put("url", JsonPrimitive(url))
                put("includeRawHtml", JsonPrimitive(false))
                put("renderJs", JsonPrimitive(false))
                put("extractImages", JsonPrimitive(false))
            }

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = "https://api.linkup.so/v1/fetch",
                    headers = mapOf(
                        "Authorization" to "Bearer ${serviceOptions.apiKey}",
                        "Content-Type" to "application/json"
                    ),
                    body = body.toString().encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode in 200..299) {
                val responseBody = response.body.decodeToString().let {
                    json.decodeFromString<LinkUpFetchResponse>(it)
                }

                return@withContext Result.success(
                    ScrapedResult(
                        urls = listOf(
                            ScrapedResultUrl(
                                url = url,
                                content = responseBody.markdown
                            )
                        )
                    )
                )
            } else {
                error("response failed #${response.statusCode}: ${response.body.decodeToString()}")
            }
        }
    }

    @Serializable
    data class LinkUpSearchResponse(
        val answer: String,
        val sources: List<Source>
    )

    @Serializable
    data class Source(
        val name: String,
        val url: String,
        val snippet: String
    )

    @Serializable
    data class LinkUpFetchResponse(
        val markdown: String
    )
}
