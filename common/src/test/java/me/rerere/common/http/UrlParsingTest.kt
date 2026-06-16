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
    fun replaceUrlEncodedPathPreservesAuthorityAndSuffix() {
        val replaced = "https://example.com:8443/old/path?x=1#section"
            .replaceUrlEncodedPathOrNull("/v1")

        assertEquals("https://example.com:8443/v1?x=1#section", replaced)
    }
}
