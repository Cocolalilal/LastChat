package me.rerere.lastchat.ios

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.search.SearchServiceOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosSearchPreferencesTest {
    @Test
    fun everyExposedProviderMapsToItsPortableSearchOptions() {
        IosSearchProviderType.entries.forEach { type ->
            val options = type.toOptions("secret")
            when (type) {
                IosSearchProviderType.BING -> assertIs<SearchServiceOptions.BingLocalOptions>(options)
                IosSearchProviderType.TAVILY -> assertIs<SearchServiceOptions.TavilyOptions>(options)
                IosSearchProviderType.EXA -> assertIs<SearchServiceOptions.ExaOptions>(options)
                IosSearchProviderType.BRAVE -> assertIs<SearchServiceOptions.BraveOptions>(options)
                IosSearchProviderType.PERPLEXITY -> assertIs<SearchServiceOptions.PerplexityOptions>(options)
                IosSearchProviderType.FIRECRAWL -> assertIs<SearchServiceOptions.FirecrawlOptions>(options)
                IosSearchProviderType.JINA -> assertIs<SearchServiceOptions.JinaOptions>(options)
                IosSearchProviderType.LINKUP -> assertIs<SearchServiceOptions.LinkUpOptions>(options)
                IosSearchProviderType.ZHIPU -> assertIs<SearchServiceOptions.ZhipuOptions>(options)
                IosSearchProviderType.METASO -> assertIs<SearchServiceOptions.MetasoOptions>(options)
                IosSearchProviderType.BOCHA -> assertIs<SearchServiceOptions.BochaOptions>(options)
                IosSearchProviderType.OLLAMA -> assertIs<SearchServiceOptions.OllamaOptions>(options)
                IosSearchProviderType.GROK -> assertIs<SearchServiceOptions.GrokOptions>(options)
                IosSearchProviderType.NANOGPT -> assertIs<SearchServiceOptions.NanoGPTOptions>(options)
            }
        }
    }

    @Test
    fun onlyKeylessBingSkipsKeychainRequirement() {
        assertFalse(IosSearchProviderType.BING.requiresApiKey())
        IosSearchProviderType.entries.filterNot { it == IosSearchProviderType.BING }
            .forEach { assertTrue(it.requiresApiKey(), it.displayName()) }
    }

    @Test
    fun persistedPreferencesContainNoSearchSecret() {
        val encoded = Json.encodeToString(
            IosSearchPreferences(
                enabled = true,
                provider = IosSearchProviderType.TAVILY,
                resultSize = 7,
            ),
        )
        assertFalse(encoded.contains("secret", ignoreCase = true))
        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertEquals(7, Json.decodeFromString<IosSearchPreferences>(encoded).resultSize)
    }
}
