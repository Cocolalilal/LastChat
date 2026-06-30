package me.rerere.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.json
import me.rerere.search.SearchService.Companion.platformHttpClient

private const val TAG = "NanoGPTSearchService"

/**
 * NanoGPT Search Service
 * 
 * Provides AI-powered web search using NanoGPT's Linkup-based search API.
 * 
 * Features:
 * - Search depth: standard ($0.006) or deep ($0.06) for comprehensive research
 * - Output types: searchResults, sourcedAnswer, or structured
 * - Date filtering with fromDate/toDate
 * - Domain filtering with includeDomains/excludeDomains
 * - Image inclusion in results
 * - Web scraping with optional stealth mode
 */
object NanoGPTSearchService : SearchService<SearchServiceOptions.NanoGPTOptions> {
    override val name: String = "NanoGPT"

    override val parameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search query")
                })
                put("depth", buildJsonObject {
                    put("type", "string")
                    put("description", "search depth: 'standard' (faster, cheaper) or 'deep' (more comprehensive, for research)")
                    put("enum", buildJsonArray {
                        add("standard")
                        add("deep")
                    })
                })
                put("fromDate", buildJsonObject {
                    put("type", "string")
                    put("description", "filter results from this date (YYYY-MM-DD format)")
                })
                put("toDate", buildJsonObject {
                    put("type", "string")
                    put("description", "filter results until this date (YYYY-MM-DD format)")
                })
                put("includeDomains", buildJsonObject {
                    put("type", "array")
                    put("description", "only search these domains (e.g., ['reuters.com', 'bbc.com'])")
                    put("items", buildJsonObject {
                        put("type", "string")
                    })
                })
                put("excludeDomains", buildJsonObject {
                    put("type", "array")
                    put("description", "exclude these domains from search results")
                    put("items", buildJsonObject {
                        put("type", "string")
                    })
                })
            },
            required = listOf("query")
        )

    override val scrapingParameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("urls", buildJsonObject {
                    put("type", "array")
                    put("description", "list of URLs to scrape (max 5)")
                    put("items", buildJsonObject {
                        put("type", "string")
                    })
                })
            },
            required = listOf("urls")
        )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.NanoGPTOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            
            // Get optional parameters
            val depth = params["depth"]?.jsonPrimitive?.contentOrNull ?: serviceOptions.depth
            val fromDate = params["fromDate"]?.jsonPrimitive?.contentOrNull
            val toDate = params["toDate"]?.jsonPrimitive?.contentOrNull
            val includeDomains = params["includeDomains"]?.jsonArray?.map { it.jsonPrimitive.content }
            val excludeDomains = params["excludeDomains"]?.jsonArray?.map { it.jsonPrimitive.content }

            // Validate depth
            if (depth !in listOf("standard", "deep")) {
                error("depth must be one of 'standard' or 'deep'")
            }

            val body = buildJsonObject {
                put("query", query)
                put("depth", depth)
                put("outputType", serviceOptions.outputType)
                put("includeImages", serviceOptions.includeImages)
                
                // Add optional date filters
                fromDate?.let { put("fromDate", it) }
                toDate?.let { put("toDate", it) }
                
                // Add optional domain filters
                includeDomains?.let { domains ->
                    put("includeDomains", buildJsonArray {
                        domains.forEach { add(it) }
                    })
                }
                excludeDomains?.let { domains ->
                    put("excludeDomains", buildJsonArray {
                        domains.forEach { add(it) }
                    })
                }
            }

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = "https://nano-gpt.com/api/web",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "x-api-key" to serviceOptions.apiKey
                    ),
                    body = body.toString().encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode in 200..299) {
                val responseBody = response.body.decodeToString()
                
                // Handle different output types
                when (serviceOptions.outputType) {
                    "sourcedAnswer" -> {
                        val parsed = json.decodeFromString<SourcedAnswerResponse>(responseBody)
                        return@withContext Result.success(
                            SearchResult(
                                answer = parsed.data.answer,
                                items = parsed.data.sources.map { source ->
                                    SearchResultItem(
                                        title = source.name,
                                        url = source.url,
                                        text = source.snippet
                                    )
                                }
                            )
                        )
                    }
                    else -> {
                        // Default: searchResults
                        val parsed = json.decodeFromString<SearchResultsResponse>(responseBody)
                        return@withContext Result.success(
                            SearchResult(
                                items = parsed.data
                                    .filter { it.type == "text" }
                                    .map { item ->
                                        SearchResultItem(
                                            title = item.title,
                                            url = item.url,
                                            text = item.content ?: ""
                                        )
                                    }
                            )
                        )
                    }
                }
            } else {
                val errorBody = response.body.decodeToString()
                error("NanoGPT search failed #${response.statusCode}: $errorBody")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.NanoGPTOptions
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        runCatching {
            // Support both single url and urls array
            val urls = params["urls"]?.jsonArray?.map { it.jsonPrimitive.content }
                ?: params["url"]?.jsonPrimitive?.content?.let { listOf(it) }
                ?: error("urls is required")

            if (urls.size > 5) {
                error("Maximum 5 URLs per request")
            }

            val body = buildJsonObject {
                put("urls", buildJsonArray {
                    urls.forEach { add(it) }
                })
                put("stealthMode", serviceOptions.stealthMode)
            }

            val response = platformHttpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = "https://nano-gpt.com/api/scrape-urls",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "x-api-key" to serviceOptions.apiKey
                    ),
                    body = body.toString().encodeToByteArray(),
                    mediaType = "application/json"
                )
            )
            if (response.statusCode in 200..299) {
                val parsed = json.decodeFromString<ScrapeResponse>(response.body.decodeToString())
                return@withContext Result.success(
                    ScrapedResult(
                        urls = parsed.results
                            .filter { it.success }
                            .map { result ->
                                ScrapedResultUrl(
                                    url = result.url,
                                    content = result.markdown ?: result.content ?: "",
                                    metadata = ScrapedResultMetadata(
                                        title = result.title
                                    )
                                )
                            }
                    )
                )
            } else {
                val errorBody = response.body.decodeToString()
                error("NanoGPT scrape failed #${response.statusCode}: $errorBody")
            }
        }
    }

    // Response models for searchResults output type
    @Serializable
    data class SearchResultsResponse(
        val data: List<SearchDataItem>,
        val metadata: SearchMetadata
    )

    @Serializable
    data class SearchDataItem(
        val type: String,
        val title: String,
        val url: String,
        val content: String? = null,
        val imageUrl: String? = null
    )

    @Serializable
    data class SearchMetadata(
        val query: String,
        val depth: String,
        val outputType: String,
        val timestamp: String,
        val cost: Double
    )

    // Response models for sourcedAnswer output type
    @Serializable
    data class SourcedAnswerResponse(
        val data: SourcedAnswerData,
        val metadata: SearchMetadata
    )

    @Serializable
    data class SourcedAnswerData(
        val answer: String,
        val sources: List<SourceItem>
    )

    @Serializable
    data class SourceItem(
        val name: String,
        val url: String,
        val snippet: String
    )

    // Response models for scraping
    @Serializable
    data class ScrapeResponse(
        val results: List<ScrapeResultItem>,
        val summary: ScrapeSummary
    )

    @Serializable
    data class ScrapeResultItem(
        val url: String,
        val success: Boolean,
        val title: String? = null,
        val content: String? = null,
        val markdown: String? = null,
        val error: String? = null
    )

    @Serializable
    data class ScrapeSummary(
        val requested: Int,
        val processed: Int,
        val successful: Int,
        val failed: Int,
        val totalCost: Double,
        val stealthModeUsed: Boolean
    )
}
