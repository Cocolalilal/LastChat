package me.rerere.search

import kotlinx.coroutines.runBlocking
import me.rerere.search.SearchResult.SearchResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeylessSearchParsersTest {
    @Test
    fun parseDuckDuckGoInstantAnswerExtractsAbstractAndTopics() {
        val json = """
            {
              "Heading": "Paris",
              "AbstractText": "Capital of France",
              "AbstractURL": "https://en.wikipedia.org/wiki/Paris",
              "Answer": "",
              "RelatedTopics": [
                {"FirstURL": "https://en.wikipedia.org/wiki/Eiffel_Tower", "Text": "Eiffel Tower - Landmark"},
                {"Topics": [{"FirstURL": "https://en.wikipedia.org/wiki/Louvre", "Text": "Louvre"}]}
              ]
            }
        """.trimIndent()

        val (answer, items) = parseDuckDuckGoInstantAnswer(json)
        assertEquals("Capital of France", answer)
        assertEquals("https://en.wikipedia.org/wiki/Paris", items.first().url)
        assertTrue(items.any { it.url.contains("Eiffel_Tower") })
        assertTrue(items.any { it.url.contains("Louvre") })
    }

    @Test
    fun parseWikipediaSearchBuildsWikiUrls() {
        val json = """
            {"query":{"search":[{"title":"Weather","snippet":"<span>Sunny</span> days"}]}}
        """.trimIndent()
        val items = parseWikipediaSearch(json)
        assertEquals(1, items.size)
        assertEquals("Weather", items[0].title)
        assertEquals("https://en.wikipedia.org/wiki/Weather", items[0].url)
        assertEquals("Sunny days", items[0].text)
    }

    @Test
    fun parseDuckDuckGoHtmlExtractsResults() {
        val html = """
            <div class="result results_links">
              <a class="result__a" href="https://example.com/a">Example Title</a>
              <a class="result__snippet">Useful snippet</a>
            </div>
            </div>
        """.trimIndent()
        val items = parseDuckDuckGoHtml(html)
        assertEquals(
            listOf(SearchResultItem("Example Title", "https://example.com/a", "Useful snippet")),
            items,
        )
    }

    @Test
    fun searchKeylessMergesAndDedupesBackends() = runBlocking {
        val result = searchKeyless(
            query = "weather",
            resultSize = 5,
            httpGet = { url ->
                when {
                    url.contains("api.duckduckgo.com") -> """
                        {"Heading":"Weather","AbstractText":"Sunny","AbstractURL":"https://weather.example/ddg","Answer":"Sunny today","RelatedTopics":[]}
                    """.trimIndent()
                    url.contains("wikipedia.org") -> """
                        {"query":{"search":[{"title":"Weather","snippet":"Wiki weather"}]}}
                    """.trimIndent()
                    url.contains("html.duckduckgo.com") -> """
                        <div class="result"><a class="result__a" href="https://weather.example/ddg">Dup</a><a class="result__snippet">dup</a></div></div>
                    """.trimIndent()
                    else -> ""
                }
            },
            bingSearch = {
                listOf(SearchResultItem("Bing weather", "https://weather.example/bing", "Bing snippet"))
            },
        )
        assertEquals("Sunny today", result.answer)
        assertEquals(1, result.items.count { it.url.contains("weather.example/ddg") })
        assertTrue(result.items.any { it.url.contains("wikipedia") })
        assertTrue(result.items.any { it.url.contains("bing") })
    }
}
