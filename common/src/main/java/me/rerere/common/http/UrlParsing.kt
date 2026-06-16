package me.rerere.common.http

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
