package me.rerere.search

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.http.urlEncode
import me.rerere.common.platform.PlatformLog
import me.rerere.search.SearchResult.SearchResultItem

internal suspend fun fetchOpenMeteoWeather(
    query: String,
    httpGet: suspend (String) -> String,
): KeylessFetch? {
    val geo = resolveWeatherGeo(query, httpGet) ?: return null
    val url = "https://api.open-meteo.com/v1/forecast" +
        "?latitude=${geo.latitude}&longitude=${geo.longitude}" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum" +
        "&forecast_days=5&timezone=auto"
    return parseOpenMeteoForecast(httpGet(url), geo)
}

internal suspend fun fetchWttrWeather(
    query: String,
    httpGet: suspend (String) -> String,
): KeylessFetch? {
    val location = extractWeatherLocation(query)
    val path = location?.urlEncode()?.takeIf { it.isNotBlank() }.orEmpty()
    val url = if (path.isBlank()) {
        "https://wttr.in/?format=j1"
    } else {
        "https://wttr.in/$path?format=j1"
    }
    return parseWttrForecast(httpGet(url))
}

internal suspend fun fetchGoogleNews(
    query: String,
    acceptLanguage: String,
    httpGet: suspend (String) -> String,
): List<SearchResultItem> {
    val (hl, gl, ceid) = googleNewsLocale(acceptLanguage)
    val url = if (isGenericNewsQuery(query)) {
        "https://news.google.com/rss?hl=${hl.urlEncode()}&gl=${gl.urlEncode()}&ceid=${ceid.urlEncode()}"
    } else {
        "https://news.google.com/rss/search?q=${query.urlEncode()}" +
            "&hl=${hl.urlEncode()}&gl=${gl.urlEncode()}&ceid=${ceid.urlEncode()}"
    }
    return parseGoogleNewsRss(httpGet(url))
}

internal suspend fun fetchQwant(
    kind: QwantKind,
    query: String,
    acceptLanguage: String,
    count: Int,
    httpGet: suspend (String) -> String,
): KeylessFetch {
    val locale = qwantLocale(acceptLanguage)
    val path = when (kind) {
        QwantKind.WEB -> "web"
        QwantKind.NEWS -> "news"
        QwantKind.IMAGES -> "images"
    }
    val url = "https://api.qwant.com/v3/search/$path?q=${query.urlEncode()}" +
        "&count=${count.coerceIn(1, 20)}&locale=${locale.urlEncode()}&offset=0&device=desktop&safesearch=1"
    val (items, images) = parseQwantResults(httpGet(url))
    return KeylessFetch(
        backend = "Qwant ${kind.label}",
        items = items,
        images = images,
    )
}

internal suspend fun fetchDuckDuckGoInstant(
    query: String,
    httpGet: suspend (String) -> String,
): KeylessFetch {
    val (answer, items) = parseDuckDuckGoInstantAnswer(
        httpGet("https://api.duckduckgo.com/?q=${query.urlEncode()}&format=json&no_html=1&skip_disambig=1")
    )
    return KeylessFetch(backend = "DuckDuckGo Instant Answer", items = items, answer = answer)
}

internal suspend fun fetchDuckDuckGoHtml(
    query: String,
    httpGet: suspend (String) -> String,
    news: Boolean = false,
): List<SearchResultItem> {
    val extra = if (news) "&iar=news" else ""
    return parseDuckDuckGoHtml(httpGet("https://html.duckduckgo.com/html/?q=${query.urlEncode()}$extra"))
}

internal suspend fun fetchDuckDuckGoLite(
    query: String,
    httpGet: suspend (String) -> String,
): List<SearchResultItem> {
    return parseDuckDuckGoLite(httpGet("https://lite.duckduckgo.com/lite/?q=${query.urlEncode()}"))
}

internal suspend fun fetchDuckDuckGoImages(
    query: String,
    httpGet: suspend (String) -> String,
): List<SearchResult.SearchResultImage> {
    val landing = httpGet("https://duckduckgo.com/?q=${query.urlEncode()}&iax=images&ia=images")
    val vqd = parseDuckDuckGoVqd(landing) ?: return emptyList()
    val json = httpGet(
        "https://duckduckgo.com/i.js?l=us-en&o=json&q=${query.urlEncode()}&vqd=${vqd.urlEncode()}&f=,,,&p=1"
    )
    return parseDuckDuckGoImages(json)
}

internal suspend fun fetchWikipedia(
    query: String,
    resultSize: Int,
    httpGet: suspend (String) -> String,
): List<SearchResultItem> {
    val limit = resultSize.coerceIn(1, 10)
    return parseWikipediaSearch(
        httpGet(
            "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${query.urlEncode()}" +
                "&utf8=1&format=json&srlimit=$limit"
        )
    )
}

internal suspend fun fetchWikimediaCommonsImages(
    query: String,
    httpGet: suspend (String) -> String,
): List<SearchResult.SearchResultImage> {
    val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
        "&gsrsearch=${query.urlEncode()}&gsrnamespace=6&gsrlimit=8" +
        "&prop=imageinfo&iiprop=url|mime|extmetadata&iiurlwidth=1280&format=json"
    return parseWikimediaCommonsImages(httpGet(url))
}

internal suspend fun fetchWikipediaPageImages(
    query: String,
    httpGet: suspend (String) -> String,
): List<SearchResult.SearchResultImage> {
    val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search" +
        "&gsrsearch=${query.urlEncode()}&gsrlimit=6" +
        "&prop=pageimages|info&inprop=url&piprop=original|thumbnail&pithumbsize=800&format=json"
    return parseWikipediaPageImages(httpGet(url))
}

internal suspend fun fetchJinaSearch(
    query: String,
    httpGet: suspend (String) -> String,
): KeylessFetch? {
    val body = httpGet("https://s.jina.ai/${query.urlEncode()}")
    if (body.length < 80) return null
    if (body.startsWith("{") && body.contains("\"code\"")) return null
    val snippet = body.take(1800).trim()
    if (snippet.isBlank()) return null
    return KeylessFetch(
        backend = "Jina Search",
        items = listOf(
            SearchResultItem(
                title = "Jina search digest",
                url = "https://s.jina.ai/${query.urlEncode()}",
                text = snippet,
                source = "Jina",
            )
        ),
        answer = snippet,
    )
}

internal suspend fun fetchFirecrawlKeyless(
    query: String,
    intent: KeylessIntent,
    resultSize: Int,
    httpPostJson: suspend (String, String) -> KeylessHttpResponse,
): KeylessFetch? {
    val sources = firecrawlKeylessSources(intent) ?: return null
    val body = buildJsonObject {
        put("query", query)
        put("limit", resultSize.coerceIn(1, 10))
        put(
            "sources",
            buildJsonArray {
                sources.forEach { add(it) }
            },
        )
    }.toString()
    val response = httpPostJson(FIRECRAWL_KEYLESS_SEARCH_URL, body)
    if (isFirecrawlKeylessSkippableStatus(response.statusCode)) {
        PlatformLog.w(
            "KeylessSearch",
            "Firecrawl Keyless search skipped HTTP ${response.statusCode}",
        )
        return null
    }
    if (response.statusCode !in 200..299) {
        error("Firecrawl Keyless search HTTP ${response.statusCode}")
    }
    return parseFirecrawlKeylessSearch(response.body)
}

internal suspend fun scrapeWithFirecrawlKeyless(
    url: String,
    httpPostJson: suspend (String, String) -> KeylessHttpResponse,
): ScrapedResult? {
    require(url.startsWith("http://") || url.startsWith("https://")) { "url must be http(s)" }
    val body = buildJsonObject {
        put("url", url)
        put("onlyMainContent", true)
        put(
            "formats",
            buildJsonArray {
                add("markdown")
            },
        )
    }.toString()
    val response = httpPostJson(FIRECRAWL_KEYLESS_SCRAPE_URL, body)
    if (isFirecrawlKeylessSkippableStatus(response.statusCode)) {
        PlatformLog.w(
            "KeylessSearch",
            "Firecrawl Keyless scrape skipped HTTP ${response.statusCode}",
        )
        return null
    }
    if (response.statusCode !in 200..299) {
        error("Firecrawl Keyless scrape HTTP ${response.statusCode}")
    }
    return parseFirecrawlKeylessScrape(response.body, url)
        ?: error("Firecrawl Keyless scrape returned empty markdown")
}

internal suspend fun scrapeKeyless(
    url: String,
    httpPostJson: suspend (String, String) -> KeylessHttpResponse,
    httpGet: suspend (String) -> String,
): ScrapedResult {
    val firecrawl = runCatching { scrapeWithFirecrawlKeyless(url, httpPostJson) }
        .onFailure { PlatformLog.w("KeylessSearch", "Firecrawl Keyless scrape failed: ${it.message}") }
        .getOrNull()
    if (firecrawl != null) return firecrawl
    return scrapeWithJinaReader(url, httpGet)
}

internal suspend fun scrapeWithJinaReader(
    url: String,
    httpGet: suspend (String) -> String,
): ScrapedResult {
    require(url.startsWith("http://") || url.startsWith("https://")) { "url must be http(s)" }
    val body = httpGet("https://r.jina.ai/$url")
    val content = body.trim()
    require(content.length > 40) { "scrape returned empty content" }
    return ScrapedResult(
        urls = listOf(
            ScrapedResultUrl(
                url = url,
                content = content.take(12_000),
            )
        )
    )
}

private suspend fun resolveWeatherGeo(
    query: String,
    httpGet: suspend (String) -> String,
): KeylessGeo? {
    val location = extractWeatherLocation(query)
    if (!location.isNullOrBlank()) {
        val geocoded = parseOpenMeteoGeocoding(
            httpGet(
                "https://geocoding-api.open-meteo.com/v1/search?name=${location.urlEncode()}" +
                    "&count=1&language=en&format=json"
            )
        )
        if (geocoded != null) return geocoded
    }
    return parseGeoJs(httpGet("https://get.geojs.io/v1/ip/geo.json"))
}

internal enum class QwantKind(val label: String) {
    WEB("Web"),
    NEWS("News"),
    IMAGES("Images"),
}
