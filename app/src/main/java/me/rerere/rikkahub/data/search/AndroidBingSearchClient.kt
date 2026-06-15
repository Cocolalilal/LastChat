package me.rerere.rikkahub.data.search

import me.rerere.search.BingSearchClient
import me.rerere.search.SearchResult.SearchResultItem
import org.jsoup.Jsoup

class AndroidBingSearchClient : BingSearchClient {
    override fun search(url: String, acceptLanguage: String): List<SearchResultItem> {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
            )
            .header("Accept-Language", acceptLanguage)
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .referrer("https://www.bing.com/")
            .cookie("SRCHHPGUSR", "ULSR=1")
            .timeout(15000)
            .get()

        val results = mutableListOf<SearchResultItem>()

        doc.select("li.b_algo").forEach { element ->
            val title = element.select("h2").text()
            val link = element.select("h2 > a").attr("href")
            val snippet = element.select(".b_caption p, .b_lineclamp2, .b_lineclamp3, .b_lineclamp4").text()
            if (title.isNotBlank() && link.isNotBlank()) {
                results.add(SearchResultItem(title = title, url = link, text = snippet))
            }
        }

        if (results.isEmpty()) {
            doc.select("div.b_algo").forEach { element ->
                val title = element.select("h2 a, a h2").text()
                val link = element.select("a[href]").first()?.attr("href") ?: ""
                val snippet = element.select("p").text()
                if (title.isNotBlank() && link.isNotBlank()) {
                    results.add(SearchResultItem(title = title, url = link, text = snippet))
                }
            }
        }

        return results
    }
}
