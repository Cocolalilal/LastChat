package me.rerere.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "SearXNGService"

@OptIn(ExperimentalEncodingApi::class)
object SearXNGService : SearchService<SearchServiceOptions.SearXNGOptions> {
    override val name: String = "SearXNG"

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
        serviceOptions: SearchServiceOptions.SearXNGOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(serviceOptions.url.isNotBlank()) {
                "SearXNG URL cannot be empty"
            }

            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = buildSearchUrl(
                baseUrl = serviceOptions.url,
                query = query,
                engines = serviceOptions.engines,
                language = serviceOptions.language
            )
            val headers = buildMap {
                if (serviceOptions.username.isNotBlank() && serviceOptions.password.isNotBlank()) {
                    put("Authorization", basicAuth(serviceOptions.username, serviceOptions.password))
                }
            }

            println("$TAG search: $url")

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = url,
                    headers = headers
                )
            )
            if (response.statusCode in 200..299) {
                val bodyRaw = response.body.decodeToString()
                val searchResponse = runCatching {
                    json.decodeFromString<SearXNGResponse>(bodyRaw)
                }.onFailure {
                    it.printStackTrace()
                    println("SearXNG response body: $bodyRaw")
                    error("Failed to decode SearXNG response: ${it.message}")
                }.getOrThrow()

                val items = searchResponse.results
                    .take(commonOptions.resultSize)
                    .map { result ->
                        SearchResultItem(
                            title = result.title,
                            url = result.url,
                            text = result.content
                        )
                    }

                return@withContext Result.success(SearchResult(items = items))
            } else {
                val errorBody = response.body.decodeToString()
                println("SearXNG API error: ${response.statusCode} - $errorBody")
                error("SearXNG request failed with status ${response.statusCode}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.SearXNGOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for SearXNG"))
    }

    @Serializable
    data class SearXNGResponse(
        @SerialName("query")
        val query: String,
        @SerialName("number_of_results")
        val numberOfResults: Int,
        @SerialName("results")
        val results: List<SearXNGResult>,
    )

    @Serializable
    data class SearXNGResult(
        @SerialName("url")
        val url: String,
        @SerialName("title")
        val title: String,
        @SerialName("content")
        val content: String,
        @SerialName("thumbnail")
        val thumbnail: String? = null,
        @SerialName("engine")
        val engine: String,
        @SerialName("template")
        val template: String,
        @SerialName("parsed_url")
        val parsedUrl: List<String> = emptyList(),
        @SerialName("img_src")
        val imgSrc: String? = null,
        @SerialName("priority")
        val priority: String? = null,
        @SerialName("engines")
        val engines: List<String> = emptyList(),
        @SerialName("positions")
        val positions: List<Int> = emptyList(),
        @SerialName("score")
        val score: Double = 0.0,
        @SerialName("category")
        val category: String = "",
        @SerialName("publishedDate")
        val publishedDate: String? = null,
        @SerialName("iframe_src")
        val iframeSrc: String? = null
    )

    private fun buildSearchUrl(
        baseUrl: String,
        query: String,
        engines: String,
        language: String
    ): String {
        val extraParams = buildList {
            if (engines.isNotBlank()) {
                add("engines=${engines.urlEncode()}")
            }
            if (language.isNotBlank()) {
                add("language=${language.urlEncode()}")
            }
        }
        return buildString {
            append(baseUrl.trimEnd('/'))
            append("/search?q=")
            append(query.urlEncode())
            append("&format=json")
            extraParams.forEach { param ->
                append('&')
                append(param)
            }
        }
    }

    private fun basicAuth(username: String, password: String): String {
        val credentials = "$username:$password".toByteArray(Charsets.ISO_8859_1)
        return "Basic ${Base64.Default.encode(credentials)}"
    }
}
