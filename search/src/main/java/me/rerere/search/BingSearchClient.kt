package me.rerere.search

import me.rerere.search.SearchResult.SearchResultItem

interface BingSearchClient {
    fun search(url: String, acceptLanguage: String): List<SearchResultItem>
}
