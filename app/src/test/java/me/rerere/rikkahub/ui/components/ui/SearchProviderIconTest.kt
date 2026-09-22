package me.rerere.rikkahub.ui.components.ui

import me.rerere.search.SearchServiceOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchProviderIconTest {
    @Test
    fun keylessUsesMaterialGlobeNotABrandName() {
        assertTrue(isKeylessSearchProvider("Keyless"))
        assertTrue(isKeylessSearchProvider("keyless"))
        assertTrue(isKeylessSearchProvider("Search", SearchServiceOptions.KeylessOptions()))
        assertFalse(isKeylessSearchProvider("Firecrawl"))
        assertFalse(isKeylessSearchProvider("Firecrawl", SearchServiceOptions.FirecrawlOptions()))
        assertFalse(isKeylessSearchProvider("Bing"))
        assertFalse(isKeylessSearchProvider("DuckDuckGo"))
        assertFalse(isKeylessSearchProvider("Wikipedia"))
        assertFalse(isKeylessSearchProvider("SearXNG"))
    }
}
