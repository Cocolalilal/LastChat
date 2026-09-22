package me.rerere.search

import me.rerere.common.http.urlDecode

internal enum class KeylessIntent {
    WEATHER,
    NEWS,
    SPORTS,
    IMAGES,
    ENCYCLOPEDIA,
    GENERAL,
}

internal fun classifyKeylessIntent(query: String, topicHint: String? = null): KeylessIntent {
    topicHint?.trim()?.lowercase()?.let { hint ->
        when (hint) {
            "weather", "forecast" -> return KeylessIntent.WEATHER
            "news", "headline", "headlines" -> return KeylessIntent.NEWS
            "sports", "sport", "scores" -> return KeylessIntent.SPORTS
            "images", "image", "photos", "photo" -> return KeylessIntent.IMAGES
            "encyclopedia", "wiki", "wikipedia" -> return KeylessIntent.ENCYCLOPEDIA
            "general", "web" -> return KeylessIntent.GENERAL
        }
    }

    val normalized = query.lowercase().trim()
    if (WEATHER_REGEX.containsMatchIn(normalized)) return KeylessIntent.WEATHER
    if (NEWS_REGEX.containsMatchIn(normalized)) return KeylessIntent.NEWS
    if (SPORTS_REGEX.containsMatchIn(normalized)) return KeylessIntent.SPORTS
    if (IMAGE_REGEX.containsMatchIn(normalized)) return KeylessIntent.IMAGES
    if (ENCYCLOPEDIA_REGEX.containsMatchIn(normalized)) return KeylessIntent.ENCYCLOPEDIA
    return KeylessIntent.GENERAL
}

internal fun extractWeatherLocation(query: String): String? {
    val trimmed = query.trim()
    val inMatch = WEATHER_LOCATION_IN_REGEX.find(trimmed)
    if (inMatch != null) {
        return sanitizeLocation(inMatch.groupValues[1])
    }
    val prefixMatch = WEATHER_LOCATION_PREFIX_REGEX.find(trimmed)
    if (prefixMatch != null) {
        return sanitizeLocation(prefixMatch.groupValues[1])
    }
    val stripped = trimmed.replace(WEATHER_WORD_REGEX, " ").replace(TIME_WORD_REGEX, " ")
    return sanitizeLocation(stripped)
}

internal fun isGenericNewsQuery(query: String): Boolean {
    val normalized = query.lowercase().trim()
    return GENERIC_NEWS_REGEX.matches(normalized)
}

internal fun isWikipediaUrl(url: String): Boolean {
    val host = url.substringAfter("://").substringBefore("/").lowercase()
    return host.contains("wikipedia.org") ||
        host.contains("wikidata.org") ||
        host.contains("wikimedia.org")
}

internal fun looksLikeImageUrl(url: String): Boolean {
    val clean = url.substringBefore('?').lowercase()
    return IMAGE_EXTENSION_REGEX.containsMatchIn(clean) ||
        clean.contains("/images/") ||
        clean.contains("upload.wikimedia.org") ||
        clean.contains("media.qwant.com")
}

internal fun unwrapDuckDuckGoHref(url: String): String {
    val absolute = when {
        url.startsWith("//") -> "https:$url"
        else -> url
    }
    val encoded = DUCKDUCKGO_UDDG_REGEX.find(absolute)?.groupValues?.getOrNull(1) ?: return absolute
    val decoded = runCatching { encoded.urlDecode() }.getOrNull().orEmpty()
    return decoded.ifBlank { absolute }
}

internal fun normalizeResultUrl(url: String): String {
    var value = unwrapDuckDuckGoHref(url.trim())
    if (value.startsWith("//")) value = "https:$value"
    return value.trimEnd('/').lowercase()
}

internal fun googleNewsLocale(acceptLanguage: String): Triple<String, String, String> {
    val primary = acceptLanguage.substringBefore(',').substringBefore(';').trim().ifBlank { "en-US" }
    val lang = primary.substringBefore('-').ifBlank { "en" }
    val region = primary.substringAfter('-', "").uppercase().ifBlank {
        when (lang.lowercase()) {
            "zh" -> "CN"
            "ja" -> "JP"
            "ko" -> "KR"
            "ar" -> "SA"
            "ru" -> "RU"
            else -> "US"
        }
    }
    val hl = if (primary.contains('-')) primary else "$lang-$region"
    return Triple(hl, region, "$region:$lang")
}

internal fun qwantLocale(acceptLanguage: String): String {
    val primary = acceptLanguage.substringBefore(',').substringBefore(';').trim().ifBlank { "en-US" }
    val lang = primary.substringBefore('-').ifBlank { "en" }
    val region = primary.substringAfter('-', "").uppercase().ifBlank { "US" }
    return "${lang}_${region}"
}

private fun sanitizeLocation(raw: String): String? {
    val cleaned = raw
        .replace(TIME_WORD_REGEX, " ")
        .replace(Regex("[?!.]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', '.', ':', '-', '?', '!')
    if (cleaned.isBlank()) return null
    if (cleaned.length < 2) return null
    if (GENERIC_LOCATION_WORDS.contains(cleaned.lowercase())) return null
    return cleaned
}

private val WEATHER_REGEX = Regex(
    """\b(weather|forecast|temperature|humidity|uv\s*index|feels\s+like|wind\s+speed|""" +
        """rain(?:y|ing)?|snow(?:y|ing)?|meteo|气温|天气|天気予報?|날씨|погода|الطقس)\b""",
    RegexOption.IGNORE_CASE,
)
private val NEWS_REGEX = Regex(
    """\b(news|headline|headlines|breaking|current events?|latest (?:news|headlines)|""" +
        """what(?:'s|s| is) happening|what happened (?:today|yesterday|last night|this week|recently|tonight)|""" +
        """今日新闻|新闻|ニュース|뉴스|новост|أخبار)\b""",
    RegexOption.IGNORE_CASE,
)
private val SPORTS_REGEX = Regex(
    """\b(score(?:s|board)?|who won|final score|box score|standings|kickoff|""" +
        """fixture|match result|vs\.?|比分|スコア)\b""",
    RegexOption.IGNORE_CASE,
)
private val IMAGE_REGEX = Regex(
    """\b(images?|pictures?|photos?|logo|illustration|screenshot|""" +
        """what does .+ look like|show me (?:a |an )?(?:picture|photo|image))\b""",
    RegexOption.IGNORE_CASE,
)
private val ENCYCLOPEDIA_REGEX = Regex(
    """^\s*(who is|who was|what is|what are|define|definition of|meaning of|history of|biography of)\b""",
    RegexOption.IGNORE_CASE,
)
private val WEATHER_LOCATION_IN_REGEX = Regex(
    """(?:weather|forecast|temperature|rain|snow|humidity)\s+(?:in|for|at|near)\s+(.+)$""",
    RegexOption.IGNORE_CASE,
)
private val WEATHER_LOCATION_PREFIX_REGEX = Regex(
    """^(.+?)\s+(?:weather|forecast|temperature)\b""",
    RegexOption.IGNORE_CASE,
)
private val WEATHER_WORD_REGEX = Regex(
    """\b(weather|forecast|temperature|humidity|rain(?:y|ing)?|snow(?:y|ing)?|wind|uv|index|meteo)\b""",
    RegexOption.IGNORE_CASE,
)
private val TIME_WORD_REGEX = Regex(
    """\b(today|tonight|tomorrow|this week|next week|now|currently|right now|hourly|daily|weekend)\b""",
    RegexOption.IGNORE_CASE,
)
private val GENERIC_NEWS_REGEX = Regex(
    """^(?:the |today'?s |latest |breaking )?(?:news|headlines|current events)(?: today| now)?[?.!]*$""",
    RegexOption.IGNORE_CASE,
)
private val GENERIC_LOCATION_WORDS = setOf(
    "here", "there", "outside", "my area", "my city", "this area", "home", "local", "the", "a", "an"
)
private val DUCKDUCKGO_UDDG_REGEX = Regex("""[?&]uddg=([^&]+)""", RegexOption.IGNORE_CASE)
private val IMAGE_EXTENSION_REGEX = Regex("""\.(?:jpe?g|png|gif|webp|avif|bmp)(?:$|\?)""", RegexOption.IGNORE_CASE)
