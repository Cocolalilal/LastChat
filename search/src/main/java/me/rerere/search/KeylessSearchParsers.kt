package me.rerere.search

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.common.http.jsonPrimitiveOrNull
import me.rerere.common.text.unescapeHtml
import me.rerere.search.SearchResult.SearchResultImage
import me.rerere.search.SearchResult.SearchResultItem

internal fun parseDuckDuckGoInstantAnswer(json: String): Pair<String?, List<SearchResultItem>> {
    val root = json.parseJsonObject() ?: return null to emptyList()

    val heading = root.string("Heading")
    val abstract = root.string("AbstractText")
    val abstractUrl = root.string("AbstractURL")
    val answer = root.string("Answer").ifBlank { abstract }.takeIf { it.isNotBlank() }

    val items = mutableListOf<SearchResultItem>()
    if (abstract.isNotBlank() && abstractUrl.isNotBlank()) {
        items += SearchResultItem(
            title = heading.ifBlank { abstractUrl },
            url = unwrapDuckDuckGoHref(abstractUrl),
            text = abstract,
            source = "DuckDuckGo",
        )
    }
    root["RelatedTopics"].asArray().forEach { topic ->
        val obj = topic.jsonObjectOrNull ?: return@forEach
        obj.toRelatedTopic()?.let(items::add)
        obj["Topics"].asArray().forEach { nested ->
            nested.jsonObjectOrNull?.toRelatedTopic()?.let(items::add)
        }
    }
    return answer to items.distinctBy { normalizeResultUrl(it.url) }
}

internal fun parseWikipediaSearch(json: String): List<SearchResultItem> {
    val root = json.parseJsonObject() ?: return emptyList()
    val search = root["query"].asObject()["search"].asArray()
    return search.mapNotNull { element ->
        val obj = element.jsonObjectOrNull ?: return@mapNotNull null
        val title = obj.string("title")
        if (title.isBlank()) return@mapNotNull null
        val snippet = obj.string("snippet").removeTags().cleanHtmlText()
        SearchResultItem(
            title = title,
            url = "https://en.wikipedia.org/wiki/" + title.replace(' ', '_'),
            text = snippet,
            source = "Wikipedia",
        )
    }
}

internal fun parseDuckDuckGoHtml(html: String): List<SearchResultItem> {
    if (html.contains("anomaly-modal") || html.contains("Select all squares containing a duck")) {
        return emptyList()
    }
    val results = mutableListOf<SearchResultItem>()
    val resultBlocks = Regex(
        """<div[^>]*class="[^"]*\bresult\b[^"]*"[^>]*>.*?</div>\s*</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    for (block in resultBlocks.findAll(html).map { it.value }) {
        val link = RESULT_LINK_REGEX.find(block) ?: continue
        val url = unwrapDuckDuckGoHref(link.groupValues.getOrNull(1).orEmpty().cleanHtmlText())
        val title = link.groupValues.getOrNull(2).orEmpty().removeTags().cleanHtmlText()
        if (url.isBlank() || title.isBlank()) continue
        if (url.contains("duckduckgo.com/y.js")) continue
        val snippet = RESULT_SNIPPET_REGEX.find(block)
            ?.groupValues?.getOrNull(1)
            .orEmpty()
            .removeTags()
            .cleanHtmlText()
        results += SearchResultItem(title = title, url = url, text = snippet, source = "DuckDuckGo")
    }
    return results
}

internal fun parseDuckDuckGoLite(html: String): List<SearchResultItem> {
    val results = mutableListOf<SearchResultItem>()
    val rows = LITE_LINK_REGEX.findAll(html).toList()
    val snippets = LITE_SNIPPET_REGEX.findAll(html).map { it.groupValues[1].removeTags().cleanHtmlText() }.toList()
    rows.forEachIndexed { index, match ->
        val url = unwrapDuckDuckGoHref(match.groupValues[1].cleanHtmlText())
        val title = match.groupValues[2].removeTags().cleanHtmlText()
        if (url.isBlank() || title.isBlank()) return@forEachIndexed
        if (url.contains("duckduckgo.com")) return@forEachIndexed
        val snippet = snippets.getOrNull(index).orEmpty()
        results += SearchResultItem(title = title, url = url, text = snippet, source = "DuckDuckGo")
    }
    return results
}

internal fun parseDuckDuckGoVqd(html: String): String? {
    return VQD_REGEX.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
}

internal fun parseDuckDuckGoImages(json: String): List<SearchResultImage> {
    val root = json.parseJsonObject() ?: return emptyList()
    val results = root["results"].asArray().ifEmpty { root["items"].asArray() }
    return results.mapNotNull { element ->
        val obj = element.jsonObjectOrNull ?: return@mapNotNull null
        val imageUrl = obj.string("image").ifBlank { obj.string("thumbnail") }
        if (!isUsableImageUrl(imageUrl)) return@mapNotNull null
        searchResultImage(
            url = imageUrl,
            title = obj.string("title"),
            thumbnailUrl = obj.string("thumbnail").ifBlank { null },
            sourcePageUrl = obj.string("url").ifBlank { null },
        )
    }
}

internal fun parseOpenMeteoGeocoding(json: String): KeylessGeo? {
    val root = json.parseJsonObject() ?: return null
    val first = root["results"].asArray().firstOrNull()?.jsonObjectOrNull ?: return null
    val name = first.string("name")
    val lat = first.double("latitude") ?: return null
    val lon = first.double("longitude") ?: return null
    if (name.isBlank()) return null
    return KeylessGeo(
        name = name,
        country = first.string("country").ifBlank { first.string("admin1") },
        latitude = lat,
        longitude = lon,
    )
}

internal fun parseOpenMeteoForecast(json: String, geo: KeylessGeo): KeylessFetch? {
    val root = json.parseJsonObject() ?: return null
    val current = root["current"].asObject()
    val daily = root["daily"].asObject()
    val temp = current.double("temperature_2m")
    val feels = current.double("apparent_temperature")
    val humidity = current.double("relative_humidity_2m")
    val wind = current.double("wind_speed_10m")
    val code = current.int("weather_code")
    if (temp == null && code == null) return null

    val nowLine = buildString {
        append("Now: ")
        if (temp != null) append("${temp.oneDecimal()}°C")
        if (feels != null) append(" (feels ${feels.oneDecimal()}°C)")
        append(", ${wmoWeatherDescription(code)}")
        if (humidity != null) append(". Humidity ${humidity.toInt()}%")
        if (wind != null) append(". Wind ${wind.oneDecimal()} km/h")
        append('.')
    }

    val dates = daily["time"].asArray().map { it.primitiveContent() }
    val maxTemps = daily["temperature_2m_max"].asArray().map { it.primitiveContent() }
    val minTemps = daily["temperature_2m_min"].asArray().map { it.primitiveContent() }
    val precip = daily["precipitation_sum"].asArray().map { it.primitiveContent() }
    val codes = daily["weather_code"].asArray().map { it.primitiveContent().toIntOrNull() }
    val forecastLines = dates.mapIndexed { index, date ->
        val high = maxTemps.getOrNull(index)
        val low = minTemps.getOrNull(index)
        val rain = precip.getOrNull(index)
        val condition = wmoWeatherDescription(codes.getOrNull(index))
        "- $date: ${low ?: "?"}–${high ?: "?"}°C, $condition, ${rain ?: "0"} mm"
    }

    val answer = buildString {
        append("**Weather — ${geo.displayName}**\n")
        append(nowLine)
        if (forecastLines.isNotEmpty()) {
            append("\nForecast:\n")
            append(forecastLines.joinToString("\n"))
        }
        append("\nSource: Open-Meteo (no API key). Coordinates ${geo.latitude}, ${geo.longitude}.")
    }

    val item = SearchResultItem(
        title = "Weather in ${geo.displayName}",
        url = "https://open-meteo.com/",
        text = nowLine,
        source = "Open-Meteo",
    )
    return KeylessFetch(backend = "Open-Meteo", items = listOf(item), answer = answer)
}

internal fun parseWttrForecast(json: String): KeylessFetch? {
    val root = json.parseJsonObject() ?: return null
    val current = root["current_condition"].asArray().firstOrNull()?.jsonObjectOrNull ?: return null
    val area = root["nearest_area"].asArray().firstOrNull()?.jsonObjectOrNull
    val place = area?.let { obj ->
        val name = obj["areaName"].asArray().firstOrNull().asObject().string("value")
        val country = obj["country"].asArray().firstOrNull().asObject().string("value")
        listOf(name, country).filter { it.isNotBlank() }.joinToString(", ")
    }.orEmpty().ifBlank { "your area" }

    val tempC = current.string("temp_C")
    val feels = current.string("FeelsLikeC")
    val desc = current["weatherDesc"].asArray().firstOrNull().asObject().string("value").ifBlank {
        current.string("weatherDesc")
    }
    val humidity = current.string("humidity")
    val wind = current.string("windspeedKmph")
    if (tempC.isBlank() && desc.isBlank()) return null

    val nowLine = buildString {
        append("Now: ")
        if (tempC.isNotBlank()) append("${tempC}°C")
        if (feels.isNotBlank()) append(" (feels ${feels}°C)")
        if (desc.isNotBlank()) append(", $desc")
        if (humidity.isNotBlank()) append(". Humidity $humidity%")
        if (wind.isNotBlank()) append(". Wind $wind km/h")
        append('.')
    }

    val days = root["weather"].asArray().take(5).mapNotNull { element ->
        val day = element.jsonObjectOrNull ?: return@mapNotNull null
        val date = day.string("date")
        val max = day.string("maxtempC")
        val min = day.string("mintempC")
        val hourlyDesc = day["hourly"].asArray()
            .mapNotNull { it.jsonObjectOrNull }
            .getOrNull(4)
            ?.get("weatherDesc").asArray()
            .firstOrNull().asObject()
            .string("value")
        if (date.isBlank()) null
        else "- $date: ${min.ifBlank { "?" }}–${max.ifBlank { "?" }}°C" +
            if (hourlyDesc.isNotBlank()) ", $hourlyDesc" else ""
    }

    val answer = buildString {
        append("**Weather — $place**\n")
        append(nowLine)
        if (days.isNotEmpty()) {
            append("\nForecast:\n")
            append(days.joinToString("\n"))
        }
        append("\nSource: wttr.in (no API key).")
    }
    val item = SearchResultItem(
        title = "Weather in $place",
        url = "https://wttr.in/",
        text = nowLine,
        source = "wttr.in",
    )
    return KeylessFetch(backend = "wttr.in", items = listOf(item), answer = answer)
}

internal fun parseGeoJs(json: String): KeylessGeo? {
    val root = json.parseJsonObject() ?: return null
    val lat = root.double("latitude") ?: return null
    val lon = root.double("longitude") ?: return null
    val name = root.string("city").ifBlank { root.string("region") }.ifBlank { "your area" }
    return KeylessGeo(
        name = name,
        country = root.string("country"),
        latitude = lat,
        longitude = lon,
    )
}

internal fun parseGoogleNewsRss(xml: String): List<SearchResultItem> {
    return ITEM_REGEX.findAll(xml).mapNotNull { match ->
        val item = match.groupValues[1]
        val title = item.extractXmlTag("title").cleanHtmlText()
        val link = item.extractXmlTag("link").cleanHtmlText()
        if (title.isBlank() || link.isBlank()) return@mapNotNull null
        val published = item.extractXmlTag("pubDate").cleanHtmlText()
        val source = item.extractXmlTag("source").cleanHtmlText()
        val description = item.extractXmlTag("description").removeTags().cleanHtmlText()
        val text = listOfNotNull(
            source.takeIf { it.isNotBlank() },
            published.takeIf { it.isNotBlank() },
            description.takeIf { it.isNotBlank() },
        ).joinToString(" — ")
        SearchResultItem(
            title = title,
            url = link,
            text = text,
            source = source.ifBlank { "Google News" },
            publishedAt = published.ifBlank { null },
        )
    }.toList()
}

internal fun parseQwantResults(json: String): Pair<List<SearchResultItem>, List<SearchResultImage>> {
    val root = json.parseJsonObject() ?: return emptyList<SearchResultItem>() to emptyList()
    val items = LinkedHashMap<String, SearchResultItem>()
    val images = LinkedHashMap<String, SearchResultImage>()
    walkQwant(root, items, images)
    return items.values.toList() to images.values.toList()
}

internal fun parseWikimediaCommonsImages(json: String): List<SearchResultImage> {
    val root = json.parseJsonObject() ?: return emptyList()
    val pages = root["query"].asObject()["pages"].asObject()
    return pages.values.mapNotNull { element ->
        val page = element.jsonObjectOrNull ?: return@mapNotNull null
        val info = page["imageinfo"].asArray().firstOrNull()?.jsonObjectOrNull ?: return@mapNotNull null
        val mime = info.string("mime")
        if (mime.isNotBlank() && !mime.startsWith("image/")) return@mapNotNull null
        val url = info.string("url").ifBlank { info.string("thumburl") }
        if (!isUsableImageUrl(url)) return@mapNotNull null
        val title = page.string("title").removePrefix("File:")
        searchResultImage(
            url = url,
            title = title,
            thumbnailUrl = info.string("thumburl").ifBlank { null },
            sourcePageUrl = info.string("descriptionurl").ifBlank { null },
        )
    }
}

internal fun parseFirecrawlKeylessSearch(json: String): KeylessFetch? {
    val root = json.parseJsonObject() ?: return null
    val success = root["success"]?.jsonPrimitiveOrNull?.content?.toBooleanStrictOrNull() ?: true
    if (!success) return null
    val data = root["data"]?.jsonObjectOrNull?.takeIf { it.isNotEmpty() } ?: root
    val items = LinkedHashMap<String, SearchResultItem>()
    val images = LinkedHashMap<String, SearchResultImage>()

    data["web"].asArray().forEach { element ->
        val obj = element.jsonObjectOrNull ?: return@forEach
        val url = obj.string("url")
        val title = obj.string("title").ifBlank { url }
        if (url.isBlank() || title.isBlank()) return@forEach
        items.putAbsent(
            normalizeResultUrl(url),
            SearchResultItem(
                title = title,
                url = url,
                text = obj.string("description").ifBlank { obj.string("markdown") }.asSearchSnippet(),
                source = "Firecrawl",
            ),
        )
    }
    data["news"].asArray().forEach { element ->
        val obj = element.jsonObjectOrNull ?: return@forEach
        val url = obj.string("url")
        val title = obj.string("title").ifBlank { url }
        if (url.isBlank() || title.isBlank()) return@forEach
        val snippet = obj.string("snippet").ifBlank { obj.string("description") }.asSearchSnippet()
        val date = obj.string("date")
        items.putAbsent(
            normalizeResultUrl(url),
            SearchResultItem(
                title = title,
                url = url,
                text = listOfNotNull(
                    date.takeIf { it.isNotBlank() },
                    snippet.takeIf { it.isNotBlank() },
                ).joinToString(" — "),
                source = "Firecrawl",
                publishedAt = date.ifBlank { null },
            ),
        )
    }
    data["images"].asArray().forEach { element ->
        val obj = element.jsonObjectOrNull ?: return@forEach
        val imageUrl = obj.string("imageUrl").ifBlank { obj.string("url") }
        if (!isUsableImageUrl(imageUrl)) return@forEach
        images.putAbsent(
            normalizeResultUrl(imageUrl),
            searchResultImage(
                url = imageUrl,
                title = obj.string("title"),
                thumbnailUrl = obj.string("thumbnailUrl").ifBlank { null },
                sourcePageUrl = obj.string("url").ifBlank { null },
            ),
        )
    }

    if (items.isEmpty() && images.isEmpty()) return null
    return KeylessFetch(
        backend = FIRECRAWL_KEYLESS_BACKEND,
        items = items.values.toList(),
        images = images.values.toList(),
    )
}

internal fun parseFirecrawlKeylessScrape(json: String, url: String): ScrapedResult? {
    val root = json.parseJsonObject() ?: return null
    val success = root["success"]?.jsonPrimitiveOrNull?.content?.toBooleanStrictOrNull() ?: true
    if (!success) return null
    val data = root["data"].asObject()
    val markdown = data.string("markdown").trim()
    if (markdown.length <= 40) return null
    val title = data["metadata"].asObject().string("title").ifBlank { null }
    val description = data["metadata"].asObject().string("description").ifBlank { null }
    val language = data["metadata"].asObject().string("language").ifBlank { null }
    return ScrapedResult(
        urls = listOf(
            ScrapedResultUrl(
                url = url,
                content = markdown.take(12_000),
                metadata = if (title != null || description != null || language != null) {
                    ScrapedResultMetadata(
                        title = title,
                        description = description,
                        language = language,
                    )
                } else {
                    null
                },
            )
        )
    )
}

internal fun isFirecrawlKeylessSkippableStatus(statusCode: Int): Boolean {
    return statusCode == 401 ||
        statusCode == 402 ||
        statusCode == 403 ||
        statusCode == 408 ||
        statusCode == 429 ||
        statusCode >= 500
}

internal fun firecrawlKeylessSources(intent: KeylessIntent): List<String>? {
    return when (intent) {
        KeylessIntent.WEATHER -> null
        KeylessIntent.NEWS, KeylessIntent.SPORTS -> listOf("news", "web", "images")
        KeylessIntent.IMAGES -> listOf("images")
        KeylessIntent.ENCYCLOPEDIA, KeylessIntent.GENERAL -> listOf("web", "images")
    }
}

internal fun parseWikipediaPageImages(json: String): List<SearchResultImage> {
    val root = json.parseJsonObject() ?: return emptyList()
    val pages = root["query"].asObject()["pages"].asObject()
    return pages.values.mapNotNull { element ->
        val page = element.jsonObjectOrNull ?: return@mapNotNull null
        val original = page["original"].asObject().string("source")
        val thumb = page["thumbnail"].asObject().string("source")
        val url = original.ifBlank { thumb }
        if (!isUsableImageUrl(url)) return@mapNotNull null
        val title = page.string("title")
        val pageUrl = page.string("fullurl").ifBlank {
            "https://en.wikipedia.org/wiki/" + title.replace(' ', '_')
        }
        searchResultImage(
            url = url,
            title = title,
            thumbnailUrl = thumb.ifBlank { null },
            sourcePageUrl = pageUrl,
        )
    }
}

internal fun wmoWeatherDescription(code: Int?): String {
    return when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Slight rain"
        63 -> "Moderate rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Slight snow"
        73 -> "Moderate snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Slight rain showers"
        81 -> "Moderate rain showers"
        82 -> "Violent rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown conditions"
    }
}

private fun walkQwant(
    element: JsonElement?,
    items: MutableMap<String, SearchResultItem>,
    images: MutableMap<String, SearchResultImage>,
) {
    when (element) {
        is JsonObject -> {
            val title = element.string("title")
            val url = element.string("url")
            val media = element.string("media").ifBlank { element.string("thumbnail") }
            val desc = element.string("desc").ifBlank { element.string("description") }
            val date = element.string("date").ifBlank { element.string("publishedAt") }
            val source = element.string("source").ifBlank { element.string("domain") }
            val isResult = title.isNotBlank() && (url.isNotBlank() || media.isNotBlank())
            if (isResult) {
                if (isUsableImageUrl(media) || looksLikeImageUrl(url)) {
                    val imageUrl = if (isUsableImageUrl(media)) media else url
                    images.putAbsent(
                        normalizeResultUrl(imageUrl),
                        searchResultImage(
                            url = imageUrl,
                            title = title,
                            thumbnailUrl = element.string("thumbnail").ifBlank { null },
                            sourcePageUrl = url.ifBlank { null },
                        )
                    )
                } else if (url.isNotBlank()) {
                    items.putAbsent(
                        normalizeResultUrl(url),
                        SearchResultItem(
                            title = title,
                            url = url,
                            text = listOfNotNull(
                                source.takeIf { it.isNotBlank() },
                                date.takeIf { it.isNotBlank() },
                                desc.takeIf { it.isNotBlank() },
                            ).joinToString(" — "),
                            source = source.ifBlank { "Qwant" },
                            publishedAt = date.ifBlank { null },
                        )
                    )
                }
                return
            }
            element.values.forEach { child -> walkQwant(child, items, images) }
        }
        is JsonArray -> element.forEach { child -> walkQwant(child, items, images) }
        else -> Unit
    }
}

private fun JsonObject.toRelatedTopic(): SearchResultItem? {
    val url = unwrapDuckDuckGoHref(string("FirstURL"))
    val text = string("Text").cleanHtmlText()
    if (url.isBlank() || text.isBlank()) return null
    val title = text.substringBefore(" - ").ifBlank { text }
    return SearchResultItem(title = title, url = url, text = text, source = "DuckDuckGo")
}

private fun String.parseJsonObject(): JsonObject? {
    return runCatching {
        SearchService.json.parseToJsonElement(this).jsonObjectOrNull
    }.getOrNull()
}

private fun JsonElement?.asObject(): JsonObject = this?.jsonObjectOrNull ?: JsonObject(emptyMap())

private fun JsonElement?.asArray(): List<JsonElement> = this?.jsonArrayOrNull?.toList().orEmpty()

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitiveOrNull?.content.orEmpty()
}

private fun JsonObject.double(key: String): Double? {
    return this[key]?.jsonPrimitiveOrNull?.content?.toDoubleOrNull()
}

private fun JsonObject.int(key: String): Int? {
    return this[key]?.jsonPrimitiveOrNull?.content?.toIntOrNull()
}

private fun JsonElement?.primitiveContent(): String {
    return (this as? JsonPrimitive)?.content.orEmpty()
}

private fun String.removeTags(): String = replace(Regex("<[^>]+>"), " ")

private fun String.cleanHtmlText(): String {
    return unescapeHtml()
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.extractXmlTag(name: String): String {
    val regex = Regex(
        "<${Regex.escape(name)}\\b[^>]*>(.*?)</${Regex.escape(name)}>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    return regex.find(this)?.groupValues?.getOrNull(1).orEmpty()
}

private fun isUsableImageUrl(url: String): Boolean {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return false
    if (url.contains("duckduckgo.com/y.js")) return false
    val path = url.substringBefore('?').lowercase()
    if (path.endsWith(".svg") || path.endsWith(".pdf") || path.endsWith(".ogg")) return false
    return true
}

private fun Double.oneDecimal(): String {
    val rounded = kotlin.math.round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun String.asSearchSnippet(maxChars: Int = 800): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    if (compact.length <= maxChars) return compact
    return compact.take(maxChars).trimEnd() + "…"
}

internal const val FIRECRAWL_KEYLESS_BACKEND = "Firecrawl Keyless"
internal const val FIRECRAWL_KEYLESS_SEARCH_URL = "https://api.firecrawl.dev/v2/search"
internal const val FIRECRAWL_KEYLESS_SCRAPE_URL = "https://api.firecrawl.dev/v2/scrape"

private val RESULT_LINK_REGEX = Regex(
    """<a[^>]*class="[^"]*\bresult__a\b[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val RESULT_SNIPPET_REGEX = Regex(
    """<(?:a|div)[^>]*class="[^"]*\bresult__snippet\b[^"]*"[^>]*>(.*?)</(?:a|div)>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val LITE_LINK_REGEX = Regex(
    """<a[^>]*rel="nofollow"[^>]*href="([^"]+)"[^>]*class=['"][^'"]*\bresult-link\b[^'"]*['"][^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val LITE_SNIPPET_REGEX = Regex(
    """<td[^>]*class=['"][^'"]*\bresult-snippet\b[^'"]*['"][^>]*>(.*?)</td>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val VQD_REGEX = Regex("""\bvqd['"]?\s*[:=]\s*['"]?([^"'&\s]+)""")
private val ITEM_REGEX = Regex(
    "<item\\b[^>]*>(.*?)</item>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

internal fun <K, V> MutableMap<K, V>.putAbsent(key: K, value: V) {
    if (key !in this) this[key] = value
}
