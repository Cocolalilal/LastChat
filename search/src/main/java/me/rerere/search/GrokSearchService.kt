package me.rerere.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.json
import me.rerere.search.SearchService.Companion.platformHttpClient

private const val TAG = "GrokSearchService"
private const val GROK_ENDPOINT = "https://api.x.ai/v1/responses"

object GrokSearchService : SearchService<SearchServiceOptions.GrokOptions> {
    override val name: String = "Grok"

    override val parameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "The question to ask, can be a natural language question")
                })
            },
            required = listOf("query")
        )

    override val scrapingParameters: InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.GrokOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (serviceOptions.apiKey.isBlank()) {
                error("Grok API key is required")
            }

            val query = params["query"]?.jsonPrimitive?.content
                ?: error("query is required")

            val body = buildJsonObject {
                put("model", JsonPrimitive(serviceOptions.model))
                put("input", buildJsonArray {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put(
                            "content",
                            JsonPrimitive("You are a helpful search assistant. Search the web to find accurate and up-to-date information for the user's query. Provide a comprehensive answer with citations.")
                        )
                    })
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(query))
                    })
                })
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("web_search"))
                    })
                    add(buildJsonObject {
                        put("type", JsonPrimitive("x_search"))
                    })
                })
                put("store", JsonPrimitive(false))
            }

            println("$TAG search: $query")

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = GROK_ENDPOINT,
                    headers = mapOf(
                        "Authorization" to "Bearer ${serviceOptions.apiKey}",
                        "Content-Type" to "application/json"
                    ),
                    body = body.toString().encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode !in 200..299) {
                error("response failed #${response.statusCode}: ${response.body.decodeToString()}")
            }

            val responseBody = response.body.decodeToString().let {
                json.decodeFromString<GrokResponse>(it)
            }

            val messageOutput = responseBody.output.firstOrNull {
                it.type == "message" && it.role == "assistant"
            }
            val textContent = messageOutput?.content?.firstOrNull {
                it.type == "output_text"
            }

            val items = textContent?.annotations
                ?.filter { it.type == "url_citation" && !it.url.isNullOrBlank() }
                ?.distinctBy { it.url }
                ?.take(commonOptions.resultSize)
                ?.map { annotation ->
                    SearchResultItem(
                        title = annotation.title?.takeIf { it.isNotBlank() } ?: annotation.url.orEmpty(),
                        url = annotation.url.orEmpty(),
                        text = ""
                    )
                }
                .orEmpty()

            SearchResult(
                answer = textContent?.text,
                items = items
            )
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.GrokOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Grok"))
    }

    @Serializable
    private data class GrokResponse(
        val output: List<GrokOutputItem> = emptyList()
    )

    @Serializable
    private data class GrokOutputItem(
        val type: String,
        val role: String? = null,
        val content: List<GrokContent>? = null,
    )

    @Serializable
    private data class GrokContent(
        val type: String,
        val text: String? = null,
        val annotations: List<GrokAnnotation>? = null
    )

    @Serializable
    private data class GrokAnnotation(
        val type: String,
        val url: String? = null,
        val title: String? = null,
        @SerialName("start_index") val startIndex: Int? = null,
        @SerialName("end_index") val endIndex: Int? = null
    )
}
