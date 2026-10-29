package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactSanitizerTest {

    @Test
    fun `detects valid artifact candidates`() {
        val validNote = """
            <div class="art-note" style="background:#fff; color:#000;">
              <h3>Handwritten Note</h3>
            </div>
        """.trimIndent()
        assertTrue(ArtifactSanitizer.isArtifactCandidate(validNote))

        val plainCode = "console.log('hello world');"
        assertFalse(ArtifactSanitizer.isArtifactCandidate(plainCode))

        val fullHtmlDoc = "<!DOCTYPE html><html><body><div>text</div></body></html>"
        assertFalse(ArtifactSanitizer.isArtifactCandidate(fullHtmlDoc))
    }

    @Test
    fun `sanitizes scripts and event handlers`() {
        val maliciousHtml = """
            <div class="test" onclick="alert('pwned')" onmouseover="evil()">
              <script>alert('xss');</script>
              <p>Safe content</p>
            </div>
        """.trimIndent()

        val result = ArtifactSanitizer.sanitize(maliciousHtml)
        assertTrue(result is ArtifactSanitizeResult.Success)
        val success = result as ArtifactSanitizeResult.Success

        assertFalse(success.sanitizedBody.contains("<script"))
        assertFalse(success.sanitizedBody.contains("alert"))
        assertFalse(success.sanitizedBody.contains("onclick"))
        assertFalse(success.sanitizedBody.contains("onmouseover"))
        assertTrue(success.sanitizedBody.contains("Safe content"))
    }

    @Test
    fun `strips external urls and imports in styles`() {
        val maliciousCss = """
            <div style="background-image: url('http://evil.com/leak'); color: red; @import url('http://evil.com/font');">
              <style>
                .leak { background: url(https://evil.com/x); }
              </style>
              <span>Protected text</span>
            </div>
        """.trimIndent()

        val result = ArtifactSanitizer.sanitize(maliciousCss)
        assertTrue(result is ArtifactSanitizeResult.Success)
        val success = result as ArtifactSanitizeResult.Success

        assertFalse(success.sanitizedBody.contains("http://evil.com"))
        assertFalse(success.sanitizedBody.contains("https://evil.com"))
        assertFalse(success.sanitizedBody.contains("@import"))
        assertTrue(success.sanitizedBody.contains("Protected text"))
    }

    @Test
    fun `rejects oversized artifact blocks`() {
        val hugeHtml = "<div style=\"color:red;\">" + "A".repeat(2000) + "</div>"
        val result = ArtifactSanitizer.sanitize(hugeHtml)
        assertTrue(result is ArtifactSanitizeResult.Rejected)
    }

    @Test
    fun `preserves valid notebook homework artifact`() {
        val notebook = """
            <div class="art-notebook" style="background:#fbfbf6; color:#172a3a; transform: rotate(-2deg);">
              <div class="title">CH. 7 — Electrophilic Aromatic Substitution</div>
              <p>Step 1: e⁻ rich ring attacks E⁺ → arenium ion</p>
            </div>
        """.trimIndent()

        val result = ArtifactSanitizer.sanitize(notebook)
        assertTrue(result is ArtifactSanitizeResult.Success)
        val success = result as ArtifactSanitizeResult.Success

        assertTrue(success.sanitizedBody.contains("art-notebook"))
        assertTrue(success.sanitizedBody.contains("Electrophilic Aromatic Substitution"))
        assertTrue(success.documentHtml.contains("AndroidInterface.onHeightChanged"))
    }
}
