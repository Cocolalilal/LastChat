package me.rerere.common.http

data class ParsedUrlParts(
    val scheme: String,
    val host: String,
    val encodedPath: String,
)

fun String.urlHostOrNull(): String? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null

    val withoutScheme = trimmed.substringAfter("://", trimmed)
    val authority = withoutScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .trim()

    if (authority.isBlank()) return null

    val host = if (authority.startsWith("[")) {
        authority.substringAfter('[').substringBefore(']')
    } else {
        authority.substringBefore(':')
    }

    return host.lowercase().takeIf { it.isNotBlank() }
}

fun String.urlPartsOrNull(): ParsedUrlParts? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null

    val scheme = trimmed.substringBefore("://", "").lowercase()
    if (scheme != "http" && scheme != "https") {
        return null
    }

    val withoutScheme = trimmed.substringAfter("://")
    val normalizedHost = trimmed.urlHostOrNull() ?: return null
    if (normalizedHost.any { it.isWhitespace() }) return null
    val pathStart = withoutScheme.indexOf('/').takeIf { it >= 0 } ?: return ParsedUrlParts(
        scheme = scheme,
        host = normalizedHost,
        encodedPath = "/",
    )
    val encodedPath = withoutScheme
        .substring(pathStart)
        .substringBefore('?')
        .substringBefore('#')
        .ifBlank { "/" }

    return ParsedUrlParts(
        scheme = scheme,
        host = normalizedHost,
        encodedPath = encodedPath,
    )
}

fun String.replaceUrlEncodedPathOrNull(encodedPath: String): String? {
    val trimmed = trim()
    if (trimmed.urlPartsOrNull() == null) return null

    val withoutScheme = trimmed.substringAfter("://")
    val authorityEnd = withoutScheme
        .indexOfAny(charArrayOf('/', '?', '#'))
        .takeIf { it >= 0 }
        ?: withoutScheme.length
    val authority = withoutScheme.substring(0, authorityEnd)
    if (authority.isBlank() || authority.any { it.isWhitespace() }) return null

    val suffixStart = withoutScheme
        .indexOfAny(charArrayOf('?', '#'), startIndex = authorityEnd)
        .takeIf { it >= 0 }
    val suffix = suffixStart?.let { withoutScheme.substring(it) }.orEmpty()
    val replacementPath = encodedPath
        .ifBlank { "/" }
        .let { path -> if (path.startsWith('/')) path else "/$path" }

    return "${trimmed.substringBefore("://")}://$authority$replacementPath$suffix"
}
