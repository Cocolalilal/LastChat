package me.rerere.rikkahub.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntentResolverTest {
    @Test
    fun `buildRawSharePayload keeps text-only shares intact`() {
        val payload = buildRawSharePayload(
            text = "Hello from OCR",
            subject = null,
            mimeType = "text/plain",
            streamUris = emptyList()
        )

        assertEquals("Hello from OCR", payload.text)
        assertNull(payload.subject)
        assertEquals("text/plain", payload.mimeType)
        assertEquals(emptyList<String>(), payload.streamUris)
    }

    @Test
    fun `buildResolvedShareText falls back to subject when text is blank`() {
        assertEquals(
            "Shared subject",
            buildResolvedShareText(text = "", subject = "Shared subject")
        )
    }

    @Test
    fun `combineSharedStreamUris keeps send multiple order`() {
        val combined = combineSharedStreamUris(
            singleStreamUri = null,
            multipleStreamUris = listOf("content://one", "content://two"),
            clipDataUris = emptyList()
        )

        assertEquals(listOf("content://one", "content://two"), combined)
    }

    @Test
    fun `combineSharedStreamUris supports clipdata only shares`() {
        val combined = combineSharedStreamUris(
            singleStreamUri = null,
            multipleStreamUris = emptyList(),
            clipDataUris = listOf("content://clip-only")
        )

        assertEquals(listOf("content://clip-only"), combined)
    }

    @Test
    fun `buildRawSharePayload removes duplicate stream uris`() {
        val payload = buildRawSharePayload(
            text = "",
            subject = null,
            mimeType = "*/*",
            streamUris = listOf("content://dup", "content://dup", "content://other")
        )

        assertEquals(listOf("content://dup", "content://other"), payload.streamUris)
    }

    @Test
    fun `empty share payload stays empty and safe`() {
        val payload = buildRawSharePayload(
            text = null,
            subject = "   ",
            mimeType = null,
            streamUris = listOf("", "   ")
        )

        assertEquals("", payload.text)
        assertNull(payload.subject)
        assertNull(payload.mimeType)
        assertEquals(emptyList<String>(), payload.streamUris)
    }

    @Test
    fun `fallback html extraction keeps page title description and readable text`() {
        val html = """
            <html>
              <head>
                <title>Example &amp; Test</title>
                <meta property="og:description" content="A &lt;useful&gt; summary">
                <style>.hidden { display: none; }</style>
              </head>
              <body>
                <main>
                  <h1>Heading</h1>
                  <p>First paragraph&nbsp;here.</p>
                  <script>ignored()</script>
                  <blockquote>Quoted text</blockquote>
                </main>
              </body>
            </html>
        """.trimIndent()

        assertEquals("Example & Test", extractHtmlTitle(html))
        assertEquals("A <useful> summary", extractHtmlMetaContent(html, setOf("og:description")))
        assertEquals(
            listOf("Heading", "First paragraph here.", "Quoted text").joinToString("\n\n"),
            extractReadableHtmlText(html)
        )
    }
}
