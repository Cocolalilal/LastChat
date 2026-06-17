package me.rerere.rikkahub.data.search

import me.rerere.search.BingSearchClient
import me.rerere.search.PlatformBingSearchClient
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.common.platform.PlatformHttpClient

class AndroidBingSearchClient(
    httpClient: PlatformHttpClient,
) : BingSearchClient {
    private val delegate = PlatformBingSearchClient(httpClient)

    override suspend fun search(url: String, acceptLanguage: String): List<SearchResultItem> {
        return delegate.search(url, acceptLanguage)
    }
}
