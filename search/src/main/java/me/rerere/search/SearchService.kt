package me.rerere.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.common.platform.PlatformHttpClient

interface SearchService<T : SearchServiceOptions> {
    val name: String

    val parameters: InputSchema?

    val scrapingParameters: InputSchema?

    suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: T
    ): Result<SearchResult>

    suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: T
    ): Result<ScrapedResult>

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : SearchServiceOptions> getService(options: T): SearchService<T> {
            return when (options) {
                is SearchServiceOptions.TavilyOptions -> TavilySearchService
                is SearchServiceOptions.ExaOptions -> ExaSearchService
                is SearchServiceOptions.ZhipuOptions -> ZhipuSearchService
                is SearchServiceOptions.BingLocalOptions -> BingSearchService
                is SearchServiceOptions.SearXNGOptions -> SearXNGService
                is SearchServiceOptions.LinkUpOptions -> LinkUpService
                is SearchServiceOptions.BraveOptions -> BraveSearchService
                is SearchServiceOptions.MetasoOptions -> MetasoSearchService
                is SearchServiceOptions.OllamaOptions -> OllamaSearchService
                is SearchServiceOptions.PerplexityOptions -> PerplexitySearchService
                is SearchServiceOptions.FirecrawlOptions -> FirecrawlSearchService
                is SearchServiceOptions.JinaOptions -> JinaSearchService
                is SearchServiceOptions.BochaOptions -> BochaSearchService
                is SearchServiceOptions.NanoGPTOptions -> NanoGPTSearchService
                is SearchServiceOptions.GrokOptions -> GrokSearchService
            } as SearchService<T>
        }

        private var installedPlatformHttpClient: PlatformHttpClient? = null
        private var installedBingSearchClient: BingSearchClient? = null
        private var installedAcceptLanguageProvider: (() -> String)? = null

        fun installPlatformHttpClient(client: PlatformHttpClient) {
            installedPlatformHttpClient = client
        }

        fun installBingSearchClient(client: BingSearchClient) {
            installedBingSearchClient = client
        }

        fun installAcceptLanguageProvider(provider: () -> String) {
            installedAcceptLanguageProvider = provider
        }

        internal val platformHttpClient: PlatformHttpClient
            get() = installedPlatformHttpClient
                ?: error("SearchService PlatformHttpClient has not been installed")

        internal val bingSearchClient: BingSearchClient
            get() = installedBingSearchClient
                ?: error("SearchService BingSearchClient has not been installed")

        internal val acceptLanguage: String
            get() = installedAcceptLanguageProvider?.invoke() ?: "en-US,en"

        internal val json by lazy {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
    }
}
