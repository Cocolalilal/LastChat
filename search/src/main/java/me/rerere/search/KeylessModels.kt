package me.rerere.search

import me.rerere.search.SearchResult.SearchResultImage
import me.rerere.search.SearchResult.SearchResultItem

data class KeylessBackendInfo(
    val name: String,
    val role: String,
    val status: String = "No API key · skipped automatically if unreachable",
)

internal data class KeylessGeo(
    val name: String,
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String
        get() = listOf(name, country).filter { it.isNotBlank() }.joinToString(", ")
}

internal data class KeylessFetch(
    val backend: String,
    val items: List<SearchResultItem> = emptyList(),
    val images: List<SearchResultImage> = emptyList(),
    val answer: String? = null,
) {
    val isUseful: Boolean
        get() = items.isNotEmpty() || images.isNotEmpty() || !answer.isNullOrBlank()
}

internal fun searchResultImage(
    url: String,
    title: String = "",
    thumbnailUrl: String? = null,
    sourcePageUrl: String? = null,
): SearchResultImage {
    val alt = title.ifBlank { "Image" }.replace("]", "\\]")
    return SearchResultImage(
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
        sourcePageUrl = sourcePageUrl?.takeIf { it.isNotBlank() },
        markdownImage = "![$alt]($url)",
    )
}

internal fun SearchResultItem.withSource(source: String?, publishedAt: String? = this.publishedAt): SearchResultItem {
    return copy(
        source = source?.takeIf { it.isNotBlank() },
        publishedAt = publishedAt?.takeIf { it.isNotBlank() },
    )
}
