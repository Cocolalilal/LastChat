package me.rerere.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SearchModelsSharedTest {
    @Test
    fun searchOptionsExposeDefaultsInCommonCode() {
        val defaultOptions = SearchServiceOptions.DEFAULT
        val tavilyOptions = SearchServiceOptions.TavilyOptions(apiKey = "key")

        assertIs<SearchServiceOptions.KeylessOptions>(defaultOptions)
        assertEquals("Tavily", SearchServiceOptions.TYPES[tavilyOptions::class])
        assertEquals("advanced", tavilyOptions.depth)
    }

    @Test
    fun searchResultsAreConstructibleInCommonCode() {
        val result = SearchResult(
            answer = "summary",
            items = listOf(
                SearchResult.SearchResultItem(
                    title = "LastChat",
                    url = "https://example.com",
                    text = "Portable search result"
                )
            ),
            images = listOf(
                SearchResult.SearchResultImage(
                    url = "https://example.com/photo.jpg",
                    title = "Photo",
                    markdownImage = "![Photo](https://example.com/photo.jpg)",
                )
            ),
        )
        val scraped = ScrapedResult(
            urls = listOf(
                ScrapedResultUrl(
                    url = "https://example.com",
                    content = "content",
                    metadata = ScrapedResultMetadata(title = "Example")
                )
            )
        )

        assertEquals("summary", result.answer)
        assertEquals("LastChat", result.items.single().title)
        assertEquals("https://example.com/photo.jpg", result.images.single().url)
        assertEquals("Example", scraped.urls.single().metadata?.title)
    }
}

