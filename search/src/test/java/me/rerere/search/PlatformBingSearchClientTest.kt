package me.rerere.search

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformBingSearchClientTest {
    @Test
    fun `parses primary bing result markup`() {
        val html = """
            <ol>
              <li class="b_algo">
                <h2><a href="https://example.com?a=1&amp;b=2">Example &amp; Result</a></h2>
                <div class="b_caption"><p>Useful&nbsp;snippet text.</p></div>
              </li>
            </ol>
        """.trimIndent()

        assertEquals(
            listOf(
                SearchResult.SearchResultItem(
                    title = "Example & Result",
                    url = "https://example.com?a=1&b=2",
                    text = "Useful snippet text.",
                )
            ),
            parseBingResults(html)
        )
    }

    @Test
    fun `falls back to div bing result markup`() {
        val html = """
            <section>
              <div class="b_algo">
                <a href="https://example.org"><h2>Fallback Result</h2></a>
                <p>Fallback snippet.</p>
              </div>
            </section>
        """.trimIndent()

        assertEquals(
            listOf(
                SearchResult.SearchResultItem(
                    title = "Fallback Result",
                    url = "https://example.org",
                    text = "Fallback snippet.",
                )
            ),
            parseBingResults(html)
        )
    }
}
