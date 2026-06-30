package me.rerere.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.json
import me.rerere.search.SearchService.Companion.platformHttpClient

object JinaSearchService : SearchService<SearchServiceOptions.JinaOptions> {
    override val name: String = "Jina"

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
        serviceOptions: SearchServiceOptions.JinaOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")

            val body = buildJsonObject {
                put("q", query)
            }

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = "https://s.jina.ai/",
                    headers = mapOf(
                        "Authorization" to "Bearer ${serviceOptions.apiKey}",
                        "Accept" to "application/json",
                        "Content-Type" to "application/json"
                    ),
                    body = body.toString().encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode in 200..299) {
                val responseData = response.body.decodeToString().let {
                    json.decodeFromString<JinaSearchResponse>(it)
                }

                return@withContext Result.success(
                    SearchResult(
                        items = responseData.data.take(commonOptions.resultSize).map {
                            SearchResultItem(
                                title = it.title,
                                url = it.url,
                                text = it.description
                            )
                        }
                    )
                )
            } else {
                error("response failed #${response.statusCode}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.JinaOptions
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = params["url"]?.jsonPrimitive?.content ?: error("urls is required")

            val body = buildJsonObject {
                put("url", url)
            }

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = "https://r.jina.ai/",
                    headers = mapOf(
                        "Authorization" to "Bearer ${serviceOptions.apiKey}",
                        "Accept" to "application/json",
                        "Content-Type" to "application/json",
                        "X-Return-Format" to "markdown"
                    ),
                    body = body.toString().encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode !in 200..299) {
                error("response failed for url $url #${response.statusCode}")
            }
            val responseData = response.body.decodeToString().let {
                json.decodeFromString<JinaScrapeResponse>(it)
            }

            ScrapedResult(
                urls = listOf(
                    ScrapedResultUrl(
                        url = responseData.data.url,
                        content = responseData.data.content,
                        metadata = ScrapedResultMetadata(
                            title = responseData.data.title,
                            description = responseData.data.description
                        )
                    )
                )
            )
        }
    }

    @Serializable
    data class JinaSearchResponse(
        val code: Int,
        val status: Int,
        val data: List<JinaSearchResultItem>
    )

    @Serializable
    data class JinaSearchResultItem(
        val title: String,
        val url: String,
        val description: String,
        val content: String = "",
        val usage: JinaUsage? = null
    )

    @Serializable
    data class JinaUsage(
        val tokens: Int
    )

    @Serializable
    data class JinaScrapeResponse(
        val code: Int,
        val status: Int,
        val data: JinaScrapeData
    )

    @Serializable
    data class JinaScrapeData(
        val title: String,
        val description: String = "",
        val url: String,
        val content: String,
        val publishedTime: String? = null,
        val usage: JinaUsage? = null
    )
}
