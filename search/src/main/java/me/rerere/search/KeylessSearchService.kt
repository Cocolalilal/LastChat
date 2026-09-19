package me.rerere.search

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.http.urlEncode
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.search.SearchResult.SearchResultItem

object KeylessSearchService : SearchService<SearchServiceOptions.KeylessOptions> {
    override val name: String = "Keyless"

    val backends: List<String> = listOf(
        "DuckDuckGo Instant Answer",
        "Wikipedia",
        "DuckDuckGo HTML",
        "Bing",
    )

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
        serviceOptions: SearchServiceOptions.KeylessOptions
    ): Result<SearchResult> = withContext(searchIoDispatcher) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            require(query.isNotBlank()) { "query is required" }
            searchKeyless(query, commonOptions.resultSize)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.KeylessOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Keyless search"))
    }
}

internal suspend fun searchKeyless(
    query: String,
    resultSize: Int,
    httpGet: suspend (String) -> String = ::keylessHttpGet,
    bingSearch: suspend (String) -> List<SearchResultItem> = ::keylessBingSearch,
): SearchResult = coroutineScope {
    val ddgIa = async {
        runCatching {
            parseDuckDuckGoInstantAnswer(
                httpGet("https://api.duckduckgo.com/?q=${query.urlEncode()}&format=json&no_html=1&skip_disambig=1")
            )
        }.getOrDefault(null to emptyList())
    }
    val wikipedia = async {
        runCatching {
            parseWikipediaSearch(
                httpGet(
                    "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${query.urlEncode()}" +
                        "&utf8=1&format=json&srlimit=${resultSize.coerceIn(1, 10)}"
                )
            )
        }.getOrDefault(emptyList())
    }
    val ddgHtml = async {
        runCatching {
            parseDuckDuckGoHtml(
                httpGet("https://html.duckduckgo.com/html/?q=${query.urlEncode()}")
            )
        }.getOrDefault(emptyList())
    }
    val bing = async {
        runCatching {
            bingSearch("https://www.bing.com/search?q=${query.urlEncode()}")
        }.getOrDefault(emptyList())
    }

    val (answer, iaItems) = ddgIa.await()
    val merged = LinkedHashMap<String, SearchResultItem>()
    fun addAll(items: List<SearchResultItem>) {
        items.forEach { item ->
            val key = item.url.trim().trimEnd('/')
            if (key.isNotBlank()) merged.putIfAbsent(key, item)
        }
    }
    addAll(iaItems)
    addAll(wikipedia.await())
    addAll(ddgHtml.await())
    addAll(bing.await())
    SearchResult(
        answer = answer,
        items = merged.values.take(resultSize.coerceAtLeast(1)),
    )
}

private suspend fun keylessHttpGet(url: String): String {
    val response = SearchService.platformHttpClient.execute(
        PlatformHttpRequest(
            method = "GET",
            url = url,
            headers = mapOf(
                "User-Agent" to KEYLESS_USER_AGENT,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7",
                "Accept-Language" to SearchService.acceptLanguage,
            )
        )
    )
    if (response.statusCode !in 200..299) {
        error("Keyless backend HTTP ${response.statusCode} for $url")
    }
    return response.body.decodeToString()
}

private suspend fun keylessBingSearch(url: String): List<SearchResultItem> {
    return SearchService.bingSearchClient.search(url, SearchService.acceptLanguage)
}

private const val KEYLESS_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
