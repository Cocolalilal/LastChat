package me.rerere.search

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `parses bing rss result markup`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>Example &amp; Result</title>
                  <link>https://example.com/search?a=1&amp;b=2</link>
                  <description>Useful&nbsp;snippet text.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        assertEquals(
            listOf(
                SearchResult.SearchResultItem(
                    title = "Example & Result",
                    url = "https://example.com/search?a=1&b=2",
                    text = "Useful snippet text.",
                )
            ),
            parseBingRssResults(xml)
        )
    }

    @Test
    fun `falls back to rss when bing html has no extractable results`() = runBlocking {
        val client = RecordingHttpClient(
            responses = listOf(
                """<html><body><main id="b_results"></main></body></html>""",
                """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>RSS Result</title>
                          <link>https://example.org/result</link>
                          <description>Fallback snippet.</description>
                        </item>
                      </channel>
                    </rss>
                """.trimIndent()
            )
        )

        val results = PlatformBingSearchClient(client)
            .search("https://www.bing.com/search?q=kotlin", "en-US,en")

        assertEquals(
            listOf(
                SearchResult.SearchResultItem(
                    title = "RSS Result",
                    url = "https://example.org/result",
                    text = "Fallback snippet.",
                )
            ),
            results
        )
        assertEquals(2, client.requests.size)
        assertEquals("https://www.bing.com/search?q=kotlin", client.requests[0].url)
        assertTrue(client.requests[1].url.contains("format=rss"))
    }

    private class RecordingHttpClient(
        private val responses: List<String>,
    ) : PlatformHttpClient {
        val requests = mutableListOf<PlatformHttpRequest>()

        override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
            requests += request
            return PlatformHttpResponse(
                statusCode = 200,
                body = responses.getOrElse(requests.lastIndex) { "" }.encodeToByteArray()
            )
        }

        override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
    }
}
