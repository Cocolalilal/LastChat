package me.rerere.common.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlParsingTest {
    @Test
    fun urlPartsRequireHttpSchemeAndExposeHostPath() {
        val parts = "https://Api.OpenAI.com/v1beta?keep=true".urlPartsOrNull()

        assertEquals("https", parts?.scheme)
        assertEquals("api.openai.com", parts?.host)
        assertEquals("/v1beta", parts?.encodedPath)
        assertNull("api.openai.com/v1".urlPartsOrNull())
    }

    @Test
    fun normalizeHttpUrlAddsHttpsWhenSchemeIsMissing() {
        assertEquals("https://api.openai.com/v1", "api.openai.com/v1".normalizeHttpUrl())
        assertEquals("http://127.0.0.1:8188", "127.0.0.1:8188".normalizeHttpUrl())
        assertEquals("https://openrouter.ai/api/v1", "  https://openrouter.ai/api/v1  ".normalizeHttpUrl())
        assertNull("".normalizeHttpUrl())
        assertNull("   ".normalizeHttpUrl())
        assertNull("not a url".normalizeHttpUrl())
    }

    @Test
    fun requireHttpUrlRejectsBlankAndSchemeLessJunk() {
        try {
            "".requireHttpUrl("Provider base URL")
            throw AssertionError("expected blank URL to fail")
        } catch (error: IllegalArgumentException) {
            assertEquals("Provider base URL must be an absolute http or https URL, got: ''", error.message)
        }
    }

    @Test
    fun replaceUrlEncodedPathPreservesAuthorityAndSuffix() {
        val replaced = "https://example.com:8443/old/path?x=1#section"
            .replaceUrlEncodedPathOrNull("/v1")

        assertEquals("https://example.com:8443/v1?x=1#section", replaced)
    }
}
