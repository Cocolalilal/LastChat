package me.rerere.search

import kotlinx.coroutines.runBlocking
import me.rerere.search.SearchResult.SearchResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
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
            listOf(
                SearchResultItem("Example Title", "https://example.com/a", "Useful snippet", source = "DuckDuckGo")
            ),
            items,
        )
    }

    @Test
    fun parseDuckDuckGoLiteUnwrapsResultsAndSnippets() {
        val html = """
            <tr>
              <td>
                <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs&amp;rut=abc" class='result-link'>Kotlin Docs</a>
              </td>
            </tr>
            <tr>
              <td class='result-snippet'>Official documentation</td>
            </tr>
        """.trimIndent()
        val items = parseDuckDuckGoLite(html)
        assertEquals("https://kotlinlang.org/docs", items.single().url)
        assertEquals("Kotlin Docs", items.single().title)
        assertEquals("Official documentation", items.single().text)
    }

    @Test
    fun parseWikipediaPageImagesUsesOriginalSource() {
        val json = """
            {"query":{"pages":{"1":{
              "title":"Saturn",
              "fullurl":"https://en.wikipedia.org/wiki/Saturn",
              "original":{"source":"https://upload.wikimedia.org/wikipedia/commons/saturn.png"},
              "thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/saturn.png"}
            }}}}
        """.trimIndent()
        val images = parseWikipediaPageImages(json)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/saturn.png", images.single().url)
        assertEquals("Saturn", images.single().title)
        assertTrue(images.single().markdownImage.startsWith("!["))
    }

    @Test
    fun parseDuckDuckGoHtmlSkipsCaptchaChallengePages() {
        val html = """
            <div class="anomaly-modal__title">Unfortunately, bots use DuckDuckGo too.</div>
            <div>Select all squares containing a duck:</div>
        """.trimIndent()
        assertTrue(parseDuckDuckGoHtml(html).isEmpty())
    }

    @Test
    fun parseDuckDuckGoHtmlUnwrapsRedirectUrls() {
        val html = """
            <div class="result">
              <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fweather.example%2Flive">Live weather</a>
              <a class="result__snippet">Current conditions</a>
            </div>
            </div>
        """.trimIndent()
        val items = parseDuckDuckGoHtml(html)
        assertEquals("https://weather.example/live", items.single().url)
    }

    @Test
    fun parseOpenMeteoForecastBuildsStructuredAnswer() {
        val geo = KeylessGeo("Paris", "France", 48.85, 2.35)
        val json = """
            {
              "current": {
                "temperature_2m": 18.2,
                "apparent_temperature": 17.0,
                "relative_humidity_2m": 62,
                "weather_code": 2,
                "wind_speed_10m": 12.4
              },
              "daily": {
                "time": ["2026-09-20"],
                "temperature_2m_max": [21.0],
                "temperature_2m_min": [14.0],
                "precipitation_sum": [0.0],
                "weather_code": [2]
              }
            }
        """.trimIndent()
        val fetch = requireNotNull(parseOpenMeteoForecast(json, geo))
        assertTrue(fetch.answer.orEmpty().contains("18.2°C"))
        assertTrue(fetch.answer.orEmpty().contains("Partly cloudy"))
        assertTrue(fetch.items.single().url.contains("open-meteo.com"))
        assertFalse(fetch.items.any { isWikipediaUrl(it.url) })
    }

    @Test
    fun parseGoogleNewsRssExtractsDatedItems() {
        val xml = """
            <?xml version="1.0"?>
            <rss><channel>
              <item>
                <title>Storm reaches coast - BBC</title>
                <link>https://news.example/storm</link>
                <pubDate>Sun, 20 Sep 2026 10:00:00 GMT</pubDate>
                <source url="https://bbc.com">BBC</source>
                <description>Heavy rain overnight</description>
              </item>
            </channel></rss>
        """.trimIndent()
        val items = parseGoogleNewsRss(xml)
        assertEquals("Storm reaches coast - BBC", items.single().title)
        assertEquals("https://news.example/storm", items.single().url)
        assertEquals("BBC", items.single().source)
        assertEquals("Sun, 20 Sep 2026 10:00:00 GMT", items.single().publishedAt)
        assertTrue(items.single().text.contains("Heavy rain overnight"))
    }

    @Test
    fun parseQwantResultsWalksNestedWebAndImages() {
        val json = """
            {
              "status": "success",
              "data": {
                "result": {
                  "items": {
                    "mainline": [
                      {
                        "type": "web",
                        "items": [
                          {"title": "Live blog", "url": "https://news.example/live", "desc": "Updates", "source": "Reuters"}
                        ]
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        val (items, images) = parseQwantResults(json)
        assertEquals("https://news.example/live", items.single().url)
        assertEquals("Reuters", items.single().source)
        assertTrue(images.isEmpty())
    }

    @Test
    fun parseWikimediaCommonsImagesSkipsNonImages() {
        val json = """
            {
              "query": {
                "pages": {
                  "1": {
                    "title": "File:Cat.jpg",
                    "imageinfo": [{
                      "url": "https://upload.wikimedia.org/wikipedia/commons/cat.jpg",
                      "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/cat.jpg",
                      "descriptionurl": "https://commons.wikimedia.org/wiki/File:Cat.jpg",
                      "mime": "image/jpeg"
                    }]
                  },
                  "2": {
                    "title": "File:Notes.pdf",
                    "imageinfo": [{
                      "url": "https://upload.wikimedia.org/wikipedia/commons/notes.pdf",
                      "mime": "application/pdf"
                    }]
                  }
                }
              }
            }
        """.trimIndent()
        val images = parseWikimediaCommonsImages(json)
        assertEquals(1, images.size)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/cat.jpg", images.single().url)
        assertTrue(images.single().markdownImage.contains(images.single().url))
    }
}

class KeylessIntentTest {
    @Test
    fun classifiesWeatherNewsImagesAndEncyclopedia() {
        assertEquals(KeylessIntent.WEATHER, classifyKeylessIntent("weather in Tokyo"))
        assertEquals(KeylessIntent.WEATHER, classifyKeylessIntent("what's the temperature"))
        assertEquals(KeylessIntent.NEWS, classifyKeylessIntent("what's the news today"))
        assertEquals(KeylessIntent.NEWS, classifyKeylessIntent("latest headlines"))
        assertEquals(KeylessIntent.SPORTS, classifyKeylessIntent("who won the game last night"))
        assertEquals(KeylessIntent.IMAGES, classifyKeylessIntent("pictures of the Eiffel Tower"))
        assertEquals(KeylessIntent.ENCYCLOPEDIA, classifyKeylessIntent("who is Ada Lovelace"))
        assertEquals(KeylessIntent.GENERAL, classifyKeylessIntent("kotlin coroutines"))
    }

    @Test
    fun topicHintOverridesQueryText() {
        assertEquals(KeylessIntent.WEATHER, classifyKeylessIntent("tokyo", "weather"))
        assertEquals(KeylessIntent.IMAGES, classifyKeylessIntent("cats", "images"))
    }

    @Test
    fun extractsWeatherLocation() {
        assertEquals("Tokyo", extractWeatherLocation("weather in Tokyo"))
        assertEquals("New York", extractWeatherLocation("New York forecast today"))
        assertEquals(null, extractWeatherLocation("weather today"))
    }
}

class KeylessRouterTest {
    @Test
    fun weatherQueryUsesForecastNotWikipediaPrimary() = runBlocking {
        val result = searchKeyless(
            query = "weather in Paris",
            resultSize = 5,
            httpGet = { url ->
                when {
                    url.contains("geocoding-api.open-meteo.com") -> """
                        {"results":[{"name":"Paris","latitude":48.85,"longitude":2.35,"country":"France"}]}
                    """.trimIndent()
                    url.contains("api.open-meteo.com/v1/forecast") -> """
                        {"current":{"temperature_2m":18.0,"apparent_temperature":17.0,"relative_humidity_2m":60,
                         "weather_code":1,"wind_speed_10m":10},"daily":{"time":["2026-09-20"],
                         "temperature_2m_max":[21.0],"temperature_2m_min":[14.0],"precipitation_sum":[0],
                         "weather_code":[1]}}
                    """.trimIndent()
                    url.contains("wikipedia.org") -> """
                        {"query":{"search":[{"title":"Weather","snippet":"Wiki weather"}]}}
                    """.trimIndent()
                    url.contains("html.duckduckgo.com") -> """
                        <div class="result"><a class="result__a" href="https://weather.example/live">Live</a>
                        <a class="result__snippet">radar</a></div></div>
                    """.trimIndent()
                    else -> ""
                }
            },
            bingSearch = { emptyList() },
        )
        assertEquals("weather", result.intent)
        assertTrue(result.answer!!.contains("18°C") || result.answer!!.contains("18.0°C"))
        assertTrue(result.items.first().url.contains("open-meteo.com"))
        assertTrue(result.usedBackends.contains("Open-Meteo"))
        assertFalse(result.usedBackends.contains("Wikipedia"))
        assertTrue(result.items.none { it.url.contains("wikipedia.org") } || result.items.first().source == "Open-Meteo")
    }

    @Test
    fun newsQueryKeepsNonWikipediaResults() = runBlocking {
        val result = searchKeyless(
            query = "what's the news today",
            resultSize = 5,
            httpGet = { url ->
                when {
                    url.contains("news.google.com/rss") -> """
                        <rss><channel>
                          <item>
                            <title>Election update - Reuters</title>
                            <link>https://reuters.example/election</link>
                            <pubDate>Sun, 20 Sep 2026 09:00:00 GMT</pubDate>
                            <source>Reuters</source>
                            <description>Counting continues</description>
                          </item>
                        </channel></rss>
                    """.trimIndent()
                    url.contains("wikipedia.org") -> """
                        {"query":{"search":[{"title":"News","snippet":"Wiki news"}]}}
                    """.trimIndent()
                    else -> ""
                }
            },
            bingSearch = { emptyList() },
        )
        assertEquals("news", result.intent)
        assertTrue(result.items.any { it.url.contains("reuters.example") })
        assertTrue(result.items.first().url.contains("reuters.example"))
        assertFalse(result.items.all { isWikipediaUrl(it.url) })
        assertFalse(result.usedBackends.contains("Wikipedia"))
    }

    @Test
    fun imageQueryReturnsMarkdownImages() = runBlocking {
        val result = searchKeyless(
            query = "pictures of saturn",
            resultSize = 5,
            httpGet = { url ->
                when {
                    url.contains("api.qwant.com/v3/search/images") -> """
                        {"data":{"result":{"items":[
                          {"title":"Saturn","url":"https://nasa.example/saturn","media":"https://cdn.example/saturn.jpg"}
                        ]}}}
                    """.trimIndent()
                    url.contains("commons.wikimedia.org") -> """
                        {"query":{"pages":{"1":{"title":"File:Saturn.jpg","imageinfo":[
                          {"url":"https://upload.wikimedia.org/wikipedia/commons/saturn.jpg","mime":"image/jpeg",
                           "descriptionurl":"https://commons.wikimedia.org/wiki/File:Saturn.jpg"}
                        ]}}}}
                    """.trimIndent()
                    else -> ""
                }
            },
            bingSearch = { emptyList() },
        )
        assertEquals("images", result.intent)
        assertTrue(result.images.isNotEmpty())
        assertTrue(result.images.any { it.url.endsWith(".jpg") })
        assertTrue(result.images.all { it.markdownImage.contains(it.url) })
        assertNotNull(result.note)
    }

    @Test
    fun generalQueryDoesNotReturnWikipediaOnlyWhenWebHitsExist() = runBlocking {
        val result = searchKeyless(
            query = "kotlin coroutines",
            resultSize = 5,
            httpGet = { url ->
                when {
                    url.contains("api.qwant.com/v3/search/web") -> """
                        {"data":{"result":{"items":{"mainline":[{"type":"web","items":[
                          {"title":"Guide","url":"https://kotlinlang.org/docs/coroutines","desc":"Official docs"}
                        ]}]}}}}
                    """.trimIndent()
                    url.contains("wikipedia.org") -> """
                        {"query":{"search":[{"title":"Coroutine","snippet":"Wiki coroutine"}]}}
                    """.trimIndent()
                    else -> ""
                }
            },
            bingSearch = {
                listOf(SearchResultItem("Bing", "https://developer.example/coroutines", "snippet"))
            },
        )
        assertEquals("general", result.intent)
        assertTrue(result.items.any { it.url.contains("kotlinlang.org") })
        assertTrue(result.items.first().url.contains("kotlinlang.org") || result.items.first().url.contains("developer.example"))
        assertTrue(result.items.count { isWikipediaUrl(it.url) } < result.items.size)
    }

    @Test
    fun weatherQueryErrorsWhenOnlyWikipediaSucceeds() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                searchKeyless(
                    query = "weather in Paris",
                    resultSize = 5,
                    httpGet = { url ->
                        when {
                            url.contains("wikipedia.org") -> """
                                {"query":{"search":[{"title":"Weather","snippet":"Wiki weather"}]}}
                            """.trimIndent()
                            else -> ""
                        }
                    },
                    bingSearch = { emptyList() },
                )
            }
        }
    }

    @Test
    fun mergeDedupesNearIdenticalUrlsAndRanksWikipediaLast() {
        val merged = mergeKeylessResults(
            query = "kotlin",
            intent = KeylessIntent.GENERAL,
            resultSize = 5,
            fetches = listOf(
                KeylessFetch(
                    "Wikipedia",
                    items = listOf(
                        SearchResultItem("Coroutine", "https://en.wikipedia.org/wiki/Coroutine", "wiki")
                    ),
                ),
                KeylessFetch(
                    "Qwant Web",
                    items = listOf(
                        SearchResultItem("Docs", "https://kotlinlang.org/docs", "official"),
                        SearchResultItem("Docs dup", "https://kotlinlang.org/docs/", "dup"),
                    ),
                ),
            ),
            usedBackends = listOf("Qwant Web", "Wikipedia"),
        )
        assertEquals(listOf("https://kotlinlang.org/docs", "https://en.wikipedia.org/wiki/Coroutine"), merged.items.map { it.url })
    }
}
