package me.rerere.search

import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.text.unescapeHtml
import me.rerere.search.SearchResult.SearchResultItem

class PlatformBingSearchClient(
    private val httpClient: PlatformHttpClient,
) : BingSearchClient {
    override suspend fun search(url: String, acceptLanguage: String): List<SearchResultItem> {
        val htmlResponse = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = url,
                headers = bingHeaders(
                    accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    acceptLanguage = acceptLanguage,
                )
            )
        )
        val htmlResults = parseBingResults(htmlResponse.body.decodeToString())
        if (htmlResults.isNotEmpty()) {
            return htmlResults
        }

        val rssResponse = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = url.withBingRssFormat(),
                headers = bingHeaders(
                    accept = "application/rss+xml,application/xml;q=0.9,text/xml;q=0.8,*/*;q=0.7",
                    acceptLanguage = acceptLanguage,
                )
            )
        )
        return parseBingRssResults(rssResponse.body.decodeToString())
    }
}

internal fun parseBingResults(html: String): List<SearchResultItem> {
    val results = mutableListOf<SearchResultItem>()
    html.findElementsWithClass("li", "b_algo").forEach { element ->
        element.extractBingResult()?.let(results::add)
    }
    if (results.isEmpty()) {
        html.findElementsWithClass("div", "b_algo").forEach { element ->
            element.extractBingResult()?.let(results::add)
        }
    }
    return results
}

internal fun parseBingRssResults(xml: String): List<SearchResultItem> {
    return ITEM_REGEX.findAll(xml)
        .mapNotNull { match ->
            val item = match.groupValues[1]
            val title = item.extractXmlTag("title").cleanHtmlText()
            val link = item.extractXmlTag("link").cleanHtmlText()
            val description = item.extractXmlTag("description").cleanHtmlText()
            if (title.isBlank() || link.isBlank()) {
                null
            } else {
                SearchResultItem(
                    title = title,
                    url = link,
                    text = description,
                )
            }
        }
        .toList()
}

private fun String.extractBingResult(): SearchResultItem? {
    val heading = H2_REGEX.find(this)?.value.orEmpty()
    val link = heading.extractAttribute("href")
        ?: extractAttribute("href")
        ?: return null
    val title = heading.removeTags().cleanHtmlText()
    if (title.isBlank() || link.isBlank()) return null

    val snippet = SNIPPET_REGEX.findAll(this)
        .map { it.value.removeTags().cleanHtmlText() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

    return SearchResultItem(title = title, url = link.cleanHtmlText(), text = snippet)
}

private fun String.findElementsWithClass(tag: String, className: String): List<String> {
    val openTag = Regex("<$tag\\b[^>]*>", HTML_REGEX_OPTIONS)
    val results = mutableListOf<String>()
    for (match in openTag.findAll(this)) {
        val tagText = match.value
        val classes = tagText.extractAttribute("class")
            ?.split(Regex("\\s+"))
            .orEmpty()
        if (className !in classes) continue

        val close = indexOf("</$tag>", startIndex = match.range.last + 1, ignoreCase = true)
        if (close != -1) {
            results.add(substring(match.range.first, close + tag.length + 3))
        }
    }
    return results
}

private fun String.withBingRssFormat(): String {
    val formatRegex = Regex("([?&])format=[^&]*", RegexOption.IGNORE_CASE)
    if (formatRegex.containsMatchIn(this)) {
        return replace(formatRegex, "$1format=rss")
    }
    val separator = if (contains("?")) "&" else "?"
    return "$this${separator}format=rss"
}

private fun String.extractAttribute(name: String): String? {
    val regex = Regex("""\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""", HTML_REGEX_OPTIONS)
    val match = regex.find(this) ?: return null
    return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.unescapeHtml()
}

private fun String.extractXmlTag(name: String): String {
    val regex = Regex("<${Regex.escape(name)}\\b[^>]*>(.*?)</${Regex.escape(name)}>", HTML_REGEX_OPTIONS)
    return regex.find(this)?.groupValues?.getOrNull(1).orEmpty()
}

private fun String.removeTags(): String {
    return replace(Regex("<[^>]+>"), " ")
}

private fun String.cleanHtmlText(): String {
    return unescapeHtml()
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun bingHeaders(
    accept: String,
    acceptLanguage: String,
): Map<String, String> = mapOf(
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept" to accept,
    "Accept-Language" to acceptLanguage,
    "Accept-Encoding" to "gzip, deflate",
    "Connection" to "keep-alive",
    "Upgrade-Insecure-Requests" to "1",
    "Referer" to "https://www.bing.com/",
    "Cookie" to "SRCHHPGUSR=ULSR=1",
)

private val HTML_REGEX_OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
private val ITEM_REGEX = Regex("<item\\b[^>]*>(.*?)</item>", HTML_REGEX_OPTIONS)
private val H2_REGEX = Regex("<h2\\b[^>]*>.*?</h2>", HTML_REGEX_OPTIONS)
private val SNIPPET_REGEX = Regex(
    """<(?:p\b[^>]*|[^>]*class\s*=\s*(?:"[^"]*\bb_(?:caption|lineclamp[234])\b[^"]*"|'[^']*\bb_(?:caption|lineclamp[234])\b[^']*')[^>]*)>.*?</(?:p|div)>""",
    HTML_REGEX_OPTIONS
)
