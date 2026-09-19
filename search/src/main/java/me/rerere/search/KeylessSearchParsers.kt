package me.rerere.search

import me.rerere.common.text.unescapeHtml
import me.rerere.search.SearchResult.SearchResultItem

internal fun parseDuckDuckGoInstantAnswer(json: String): Pair<String?, List<SearchResultItem>> {
    val root = runCatching {
        SearchService.json.parseToJsonElement(json).jsonObjectOrEmpty()
    }.getOrNull() ?: return null to emptyList()

    val heading = root.string("Heading")
    val abstract = root.string("AbstractText")
    val abstractUrl = root.string("AbstractURL")
    val answer = root.string("Answer").ifBlank { abstract }.takeIf { it.isNotBlank() }

    val items = mutableListOf<SearchResultItem>()
    if (abstract.isNotBlank() && abstractUrl.isNotBlank()) {
        items += SearchResultItem(
            title = heading.ifBlank { abstractUrl },
            url = abstractUrl,
            text = abstract,
        )
    }
    root["RelatedTopics"].jsonArrayOrEmpty().forEach { topic ->
        val obj = topic.jsonObjectOrEmpty()
        obj.toRelatedTopic()?.let(items::add)
        obj["Topics"].jsonArrayOrEmpty().forEach { nested ->
            nested.jsonObjectOrEmpty().toRelatedTopic()?.let(items::add)
        }
    }
    return answer to items.distinctBy { it.url }
}

internal fun parseWikipediaSearch(json: String): List<SearchResultItem> {
    val root = runCatching {
        SearchService.json.parseToJsonElement(json).jsonObjectOrEmpty()
    }.getOrNull() ?: return emptyList()
    val search = root["query"].jsonObjectOrEmpty()["search"].jsonArrayOrEmpty()
    return search.mapNotNull { element ->
        val obj = element.jsonObjectOrEmpty()
        val title = obj.string("title")
        if (title.isBlank()) return@mapNotNull null
        val snippet = obj.string("snippet").cleanHtmlText()
        SearchResultItem(
            title = title,
            url = "https://en.wikipedia.org/wiki/" + title.replace(' ', '_'),
            text = snippet,
        )
    }
}

internal fun parseDuckDuckGoHtml(html: String): List<SearchResultItem> {
    val results = mutableListOf<SearchResultItem>()
    val resultBlocks = Regex(
        """<div[^>]*class="[^"]*\bresult\b[^"]*"[^>]*>.*?</div>\s*</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    for (block in resultBlocks.findAll(html).map { it.value }) {
        val link = RESULT_LINK_REGEX.find(block) ?: continue
        val url = link.groupValues.getOrNull(1).orEmpty().cleanHtmlText()
        val title = link.groupValues.getOrNull(2).orEmpty().removeTags().cleanHtmlText()
        if (url.isBlank() || title.isBlank()) continue
        if (url.contains("duckduckgo.com/y.js")) continue
        val snippet = RESULT_SNIPPET_REGEX.find(block)
            ?.groupValues?.getOrNull(1)
            .orEmpty()
            .removeTags()
            .cleanHtmlText()
        results += SearchResultItem(title = title, url = url, text = snippet)
    }
    return results
}

private fun kotlinx.serialization.json.JsonObject.toRelatedTopic(): SearchResultItem? {
    val url = string("FirstURL")
    val text = string("Text").cleanHtmlText()
    if (url.isBlank() || text.isBlank()) return null
    val title = text.substringBefore(" - ").ifBlank { text }
    return SearchResultItem(title = title, url = url, text = text)
}

private fun kotlinx.serialization.json.JsonElement?.jsonObjectOrEmpty(): kotlinx.serialization.json.JsonObject {
    return this as? kotlinx.serialization.json.JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())
}

private fun kotlinx.serialization.json.JsonElement?.jsonArrayOrEmpty(): List<kotlinx.serialization.json.JsonElement> {
    return (this as? kotlinx.serialization.json.JsonArray)?.toList().orEmpty()
}

private fun kotlinx.serialization.json.JsonObject.string(key: String): String {
    val primitive = this[key] as? kotlinx.serialization.json.JsonPrimitive ?: return ""
    return primitive.content
}

private fun String.removeTags(): String = replace(Regex("<[^>]+>"), " ")

private fun String.cleanHtmlText(): String {
    return unescapeHtml()
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private val RESULT_LINK_REGEX = Regex(
    """<a[^>]*class="[^"]*\bresult__a\b[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val RESULT_SNIPPET_REGEX = Regex(
    """<(?:a|div)[^>]*class="[^"]*\bresult__snippet\b[^"]*"[^>]*>(.*?)</(?:a|div)>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
