package me.rerere.search

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.http.urlEncode
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import me.rerere.search.SearchResult.SearchResultImage
import me.rerere.search.SearchResult.SearchResultItem

object KeylessSearchService : SearchService<SearchServiceOptions.KeylessOptions> {
    override val name: String = "Keyless"

    val backends: List<KeylessBackendInfo> = listOf(
        KeylessBackendInfo(
            "Firecrawl Keyless",
            "Primary web, news, and image search plus markdown scrape — no Firecrawl API key",
            "Free-tier / rate limits skipped automatically; falls through to other backends",
        ),
        KeylessBackendInfo("Open-Meteo", "Weather forecasts and structured conditions"),
        KeylessBackendInfo("wttr.in", "Weather fallback, including IP-based location"),
        KeylessBackendInfo("Google News RSS", "Headlines, recent events, and dated snippets"),
        KeylessBackendInfo("Qwant", "Keyless web, news, and image search"),
        KeylessBackendInfo("DuckDuckGo", "Web, news, instant answers, and images"),
        KeylessBackendInfo("Bing", "General web fallback"),
        KeylessBackendInfo("Wikimedia Commons", "Stable inline image URLs"),
        KeylessBackendInfo("Wikipedia", "Encyclopedia text and page images — never the sole source for weather or live news"),
        KeylessBackendInfo("Jina Reader", "Optional full-page scrape fallback without an API key"),
    )

    override val parameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Search query. Use natural language for weather, news, sports, or images.")
                })
                put("topic", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Optional intent hint: weather, news, sports, images, encyclopedia, or general"
                    )
                })
            },
            required = listOf("query")
        )

    override val scrapingParameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "http(s) URL to scrape as markdown")
                })
            },
            required = listOf("url")
        )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.KeylessOptions
    ): Result<SearchResult> = withContext(searchIoDispatcher) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            require(query.isNotBlank()) { "query is required" }
            val topic = params["topic"]?.jsonPrimitive?.contentOrNull
            searchKeyless(query, commonOptions.resultSize, topic)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.KeylessOptions
    ): Result<ScrapedResult> = withContext(searchIoDispatcher) {
        runCatching {
            val url = params["url"]?.jsonPrimitive?.content ?: error("url is required")
            scrapeKeyless(url, ::keylessHttpPostJson, ::keylessHttpGet)
        }
    }
}

internal const val KEYLESS_BACKEND_TIMEOUT_MS = 8_000L
internal const val KEYLESS_MAX_IMAGES = 6

internal suspend fun searchKeyless(
    query: String,
    resultSize: Int,
    topic: String? = null,
    acceptLanguage: String = SearchService.acceptLanguage,
    httpGet: suspend (String) -> String = ::keylessHttpGet,
    httpPostJson: suspend (String, String) -> KeylessHttpResponse = ::keylessHttpPostJson,
    bingSearch: suspend (String) -> List<SearchResultItem> = ::keylessBingSearch,
    backendTimeoutMs: Long = KEYLESS_BACKEND_TIMEOUT_MS,
): SearchResult = coroutineScope {
    val intent = classifyKeylessIntent(query, topic)

    suspend fun <T> backend(name: String, block: suspend () -> T?): T? {
        val value = withTimeoutOrNull(backendTimeoutMs) {
            runCatching { block() }
                .onFailure { PlatformLog.w(TAG, "$name failed: ${it.message}") }
                .getOrNull()
        }
        if (value == null) {
            PlatformLog.w(TAG, "$name timed out or failed")
        }
        return value
    }

    fun List<KeylessFetch?>.useful(): List<KeylessFetch> = mapNotNull { fetch ->
        fetch?.takeIf { it.isUseful }
    }

    val firecrawlJob = async {
        if (intent == KeylessIntent.WEATHER) return@async emptyList()
        listOfNotNull(
            backend("Firecrawl Keyless") {
                fetchFirecrawlKeyless(query, intent, resultSize, httpPostJson)
            },
        )
    }
    val weatherJob = async {
        if (intent != KeylessIntent.WEATHER) return@async emptyList()
        val openMeteo = backend("Open-Meteo") { fetchOpenMeteoWeather(query, httpGet) }
        if (openMeteo?.isUseful == true) {
            listOf(openMeteo)
        } else {
            listOfNotNull(backend("wttr.in") { fetchWttrWeather(query, httpGet) }).useful()
        }
    }
    val newsJob = async {
        if (intent != KeylessIntent.NEWS && intent != KeylessIntent.SPORTS) return@async emptyList()
        listOf(
            KeylessFetch(
                "Google News RSS",
                items = backend("Google News RSS") { fetchGoogleNews(query, acceptLanguage, httpGet) }.orEmpty(),
            ),
            backend("Qwant News") {
                fetchQwant(QwantKind.NEWS, query, acceptLanguage, resultSize, httpGet)
            },
            KeylessFetch(
                "DuckDuckGo News",
                items = backend("DuckDuckGo News") { fetchDuckDuckGoHtml(query, httpGet, news = true) }.orEmpty(),
            ),
        ).useful()
    }
    val webJob = async {
        if (intent == KeylessIntent.IMAGES) return@async emptyList()
        val html = backend("DuckDuckGo HTML") { fetchDuckDuckGoHtml(query, httpGet) }.orEmpty()
        val lite = backend("DuckDuckGo Lite") { fetchDuckDuckGoLite(query, httpGet) }.orEmpty()
        val includeEncyclopedia = intent == KeylessIntent.ENCYCLOPEDIA || intent == KeylessIntent.GENERAL
        listOf(
            backend("Qwant Web") {
                fetchQwant(QwantKind.WEB, query, acceptLanguage, resultSize, httpGet)
            },
            KeylessFetch("DuckDuckGo HTML", items = html),
            KeylessFetch("DuckDuckGo Lite", items = lite),
            KeylessFetch(
                "Bing",
                items = backend("Bing") {
                    bingSearch("https://www.bing.com/search?q=${query.urlEncode()}")
                }.orEmpty(),
            ),
            if (includeEncyclopedia) {
                backend("DuckDuckGo Instant Answer") { fetchDuckDuckGoInstant(query, httpGet) }
            } else {
                null
            },
            if (includeEncyclopedia) {
                KeylessFetch(
                    "Wikipedia",
                    items = backend("Wikipedia") { fetchWikipedia(query, resultSize, httpGet) }.orEmpty(),
                )
            } else {
                null
            },
            if (intent == KeylessIntent.NEWS || intent == KeylessIntent.GENERAL) {
                backend("Jina Search") { fetchJinaSearch(query, httpGet) }
            } else {
                null
            },
        ).useful()
    }
    val imageJob = async {
        val wantImages = intent == KeylessIntent.IMAGES ||
            intent == KeylessIntent.NEWS ||
            intent == KeylessIntent.ENCYCLOPEDIA ||
            intent == KeylessIntent.GENERAL
        if (!wantImages) return@async emptyList()
        listOf(
            backend("Qwant Images") {
                fetchQwant(QwantKind.IMAGES, query, acceptLanguage, KEYLESS_MAX_IMAGES, httpGet)
            },
            KeylessFetch(
                "DuckDuckGo Images",
                images = backend("DuckDuckGo Images") { fetchDuckDuckGoImages(query, httpGet) }.orEmpty(),
            ),
            KeylessFetch(
                "Wikimedia Commons",
                images = backend("Wikimedia Commons") { fetchWikimediaCommonsImages(query, httpGet) }.orEmpty(),
            ),
            KeylessFetch(
                "Wikipedia Images",
                images = backend("Wikipedia Images") { fetchWikipediaPageImages(query, httpGet) }.orEmpty(),
            ),
        ).useful()
    }

    val fetches = firecrawlJob.await() + weatherJob.await() + newsJob.await() + webJob.await() + imageJob.await()
    mergeKeylessResults(
        query = query,
        intent = intent,
        resultSize = resultSize,
        fetches = fetches,
        usedBackends = fetches.map { it.backend }.distinct(),
    )
}

internal fun mergeKeylessResults(
    query: String,
    intent: KeylessIntent,
    resultSize: Int,
    fetches: List<KeylessFetch>,
    usedBackends: List<String>,
): SearchResult {
    val liveItems = LinkedHashMap<String, SearchResultItem>()
    val wikiItems = LinkedHashMap<String, SearchResultItem>()
    val images = LinkedHashMap<String, SearchResultImage>()
    var answer: String? = null

    fun addItem(item: SearchResultItem, encyclopedia: Boolean) {
        val key = normalizeResultUrl(item.url)
        if (key.isBlank()) return
        if (encyclopedia || isWikipediaUrl(item.url)) {
            wikiItems.putIfAbsent(key, item)
        } else {
            liveItems.putIfAbsent(key, item)
        }
    }

    fun addImage(image: SearchResultImage) {
        val key = normalizeResultUrl(image.url)
        if (key.isBlank()) return
        images.putIfAbsent(key, image)
    }

    fetches.forEach { fetch ->
        val encyclopediaBackend = fetch.backend == "Wikipedia" || fetch.backend == "DuckDuckGo Instant Answer"
        fetch.items.forEach { addItem(it, encyclopediaBackend && isWikipediaUrl(it.url)) }
        fetch.images.forEach(::addImage)
        if (answer.isNullOrBlank() && !fetch.answer.isNullOrBlank()) {
            if (intent == KeylessIntent.WEATHER && fetch.backend !in WEATHER_ANSWER_BACKENDS) {
                return@forEach
            }
            if (intent == KeylessIntent.NEWS && encyclopediaBackend) {
                return@forEach
            }
            answer = fetch.answer
        }
    }

    val ranked = liveItems.values + wikiItems.values.filterKeysNotIn(liveItems)
    val limit = resultSize.coerceAtLeast(1)
    val items = ranked.take(limit)
    val imageList = images.values.take(KEYLESS_MAX_IMAGES)

    val hasLive = liveItems.isNotEmpty() || (intent == KeylessIntent.WEATHER && !answer.isNullOrBlank())
    if (intent in LIVE_INTENTS && !hasLive) {
        error(
            "Keyless search could not reach live ${intent.name.lowercase()} sources for \"$query\". " +
                "Wikipedia-only results were discarded."
        )
    }
    if (items.isEmpty() && answer.isNullOrBlank() && imageList.isEmpty()) {
        error("Keyless search returned no results for \"$query\"")
    }

    return SearchResult(
        answer = answer,
        items = items,
        images = imageList,
        intent = intent.name.lowercase(),
        usedBackends = usedBackends,
        note = if (imageList.isNotEmpty()) {
            "Include images[].markdown_image on its own line in your reply so photos render inline."
        } else {
            null
        },
    )
}

private fun Collection<SearchResultItem>.filterKeysNotIn(
    liveItems: Map<String, SearchResultItem>,
): List<SearchResultItem> {
    return filter { normalizeResultUrl(it.url) !in liveItems }
}

private suspend fun keylessHttpGet(url: String): String {
    val response = SearchService.platformHttpClient.execute(
        PlatformHttpRequest(
            method = "GET",
            url = url,
            headers = mapOf(
                "User-Agent" to keylessUserAgentFor(url),
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

private suspend fun keylessHttpPostJson(url: String, jsonBody: String): KeylessHttpResponse {
    val response = SearchService.platformHttpClient.execute(
        PlatformHttpRequest(
            method = "POST",
            url = url,
            headers = mapOf(
                "User-Agent" to KEYLESS_APP_USER_AGENT,
                "Accept" to "application/json",
                "Content-Type" to "application/json",
            ),
            body = jsonBody.encodeToByteArray(),
            mediaType = "application/json",
        )
    )
    return KeylessHttpResponse(
        statusCode = response.statusCode,
        body = response.body.decodeToString(),
    )
}

private suspend fun keylessBingSearch(url: String): List<SearchResultItem> {
    return SearchService.bingSearchClient.search(url, SearchService.acceptLanguage)
}

private fun keylessUserAgentFor(url: String): String {
    return if (
        url.contains("open-meteo.com") ||
        url.contains("wttr.in") ||
        url.contains("geojs.io") ||
        url.contains("commons.wikimedia.org") ||
        url.contains("wikipedia.org")
    ) {
        KEYLESS_APP_USER_AGENT
    } else {
        KEYLESS_BROWSER_USER_AGENT
    }
}

private val LIVE_INTENTS = setOf(KeylessIntent.WEATHER, KeylessIntent.NEWS, KeylessIntent.SPORTS)
private val WEATHER_ANSWER_BACKENDS = setOf("Open-Meteo", "wttr.in")
private const val TAG = "KeylessSearch"
private const val KEYLESS_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
private const val KEYLESS_APP_USER_AGENT =
    "LastChat/1.4.6 (keyless-search; https://github.com/Cocolalilal/LastChat)"
