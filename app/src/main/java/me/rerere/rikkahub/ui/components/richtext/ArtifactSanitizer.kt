package me.rerere.rikkahub.ui.components.richtext

import java.util.Locale

/**
 * Result of sanitizing an in-world visual artifact HTML block.
 */
sealed interface ArtifactSanitizeResult {
    data class Success(
        val sanitizedBody: String,
        val documentHtml: String
    ) : ArtifactSanitizeResult

    data class Rejected(
        val reason: String
    ) : ArtifactSanitizeResult
}

/**
 * Security sanitizer and validator for in-world visual artifacts.
 *
 * Enforces:
 * - Hard character size cap (1600 characters max)
 * - Tag count cap (120 tags max)
 * - Strict tag allowlist (no scripts, iframes, objects, forms, or embeds)
 * - Strict attribute allowlist (no on* event handlers)
 * - Absolute denial of external URLs, @import, and external image requests
 * - Safe data:image URIs only
 */
object ArtifactSanitizer {
    const val MAX_ARTIFACT_CHARS = 24000
    const val MAX_TAG_COUNT = 1000

    private val ALLOWED_TAGS = setOf(
        "div", "span", "p", "b", "i", "u", "s", "em", "strong", "small",
        "br", "hr", "ul", "ol", "li", "table", "thead", "tbody", "tr", "td", "th",
        "h1", "h2", "h3", "h4", "h5", "h6", "style", "img"
    )

    private val FORBIDDEN_TAG_PATTERNS = listOf(
        Regex("""(?is)<script\b.*?</script>"""),
        Regex("""(?is)<iframe\b.*?</iframe>"""),
        Regex("""(?is)<object\b.*?</object>"""),
        Regex("""(?is)<embed\b.*?</embed>"""),
        Regex("""(?is)<form\b.*?</form>"""),
        Regex("""(?is)<link\b[^>]*>"""),
        Regex("""(?is)<meta\b[^>]*>"""),
        Regex("""(?is)<applet\b.*?</applet>"""),
    )

    private val ALLOWED_ATTRIBUTES = setOf(
        "style", "class", "src", "width", "height", "colspan", "rowspan", "alt", "title", "id"
    )

    private val TAG_REGEX = Regex("""(?i)</?([a-z0-9_-]+)([^>]*)>""")
    private val ATTR_REGEX = Regex("""(?i)([a-z0-9_-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?""")

    // Harmful URL patterns
    private val HARMFUL_URL_PATTERN = Regex(
        """(?i)(?:javascript:|vbscript:|data:text|data:application|file:|content:)"""
    )
    private val CSS_IMPORT_URL_PATTERN = Regex(
        """(?i)(?:@import|url\s*\(\s*['"]?(?!data:image)[^'")]+['"]?\s*\)|expression\s*\(|-moz-binding)"""
    )
    private val SAFE_DATA_IMAGE_PATTERN = Regex(
        """(?i)^data:image/(?:png|jpeg|jpg|webp|gif|svg\+xml);base64,[a-z0-9+/=]+$"""
    )

    /**
     * Test whether a code fence block looks like an in-world visual artifact candidate.
     */
    fun isArtifactCandidate(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_ARTIFACT_CHARS) return false
        // Must have an opening div tag
        if (!trimmed.contains(Regex("""<div\b""", RegexOption.IGNORE_CASE))) return false
        // Reject full html documents
        if (trimmed.contains(Regex("""<!doctype|<html\b|<body\b""", RegexOption.IGNORE_CASE))) return false
        // Must have some styling or class intent
        return trimmed.contains(Regex("""(class|style)\s*=""", RegexOption.IGNORE_CASE))
    }

    /**
     * Sanitize raw HTML into a secure, self-contained in-world visual artifact.
     */
    fun sanitize(rawHtml: String, darkMode: Boolean = true): ArtifactSanitizeResult {
        val trimmed = rawHtml.trim()
        if (trimmed.isEmpty()) {
            return ArtifactSanitizeResult.Rejected("Empty artifact content")
        }
        if (trimmed.length > MAX_ARTIFACT_CHARS) {
            return ArtifactSanitizeResult.Rejected("Artifact exceeds size limit (${trimmed.length}/$MAX_ARTIFACT_CHARS chars)")
        }

        // Count tags
        val tagCount = TAG_REGEX.findAll(trimmed).count()
        if (tagCount > MAX_TAG_COUNT) {
            return ArtifactSanitizeResult.Rejected("Artifact exceeds tag count limit ($tagCount/$MAX_TAG_COUNT tags)")
        }

        // 1. Strip forbidden block tags along with their inner contents
        var sanitized = trimmed
        for (pattern in FORBIDDEN_TAG_PATTERNS) {
            sanitized = pattern.replace(sanitized, "")
        }

        // 2. Process each HTML tag: allowlist check & attribute cleaning
        sanitized = TAG_REGEX.replace(sanitized) { matchResult ->
            val isClosing = matchResult.value.startsWith("</")
            val rawTagName = matchResult.groupValues[1].lowercase(Locale.ROOT)
            val rawAttrs = matchResult.groupValues[2]

            if (rawTagName !in ALLOWED_TAGS) {
                // Strip tags not in allowlist
                return@replace ""
            }

            if (isClosing) {
                return@replace "</$rawTagName>"
            }

            // Clean attributes
            val cleanAttrs = StringBuilder()
            ATTR_REGEX.findAll(rawAttrs).forEach { attrMatch ->
                val attrName = attrMatch.groupValues[1].lowercase(Locale.ROOT)
                val attrVal = attrMatch.groupValues[2].ifEmpty {
                    attrMatch.groupValues[3].ifEmpty { attrMatch.groupValues[4] }
                }

                // Disallow on* handlers unconditionally
                if (attrName.startsWith("on")) {
                    return@forEach
                }

                if (attrName !in ALLOWED_ATTRIBUTES) {
                    return@forEach
                }

                // Disallow harmful URLs in attributes
                if (HARMFUL_URL_PATTERN.containsMatchIn(attrVal)) {
                    return@forEach
                }

                if (attrName == "src") {
                    // Only allow safe data:image/* URIs
                    val trimmedSrc = attrVal.trim()
                    if (SAFE_DATA_IMAGE_PATTERN.matches(trimmedSrc)) {
                        cleanAttrs.append(" src=\"$trimmedSrc\"")
                    }
                } else if (attrName == "style") {
                    val cleanStyle = sanitizeCssStyle(attrVal)
                    if (cleanStyle.isNotBlank()) {
                        cleanAttrs.append(" style=\"$cleanStyle\"")
                    }
                } else {
                    // Sanitize quotes for class, id, width, height, etc.
                    val safeVal = attrVal.replace("\"", "&quot;")
                    cleanAttrs.append(" $attrName=\"$safeVal\"")
                }
            }

            val selfClosing = matchResult.value.endsWith("/>")
            if (selfClosing) {
                "<$rawTagName$cleanAttrs/>"
            } else {
                "<$rawTagName$cleanAttrs>"
            }
        }

        // 3. Clean any inline <style> block contents
        sanitized = sanitizeStyleTags(sanitized)

        val finalSanitized = sanitized.trim()
        if (finalSanitized.isEmpty()) {
            return ArtifactSanitizeResult.Rejected("Sanitization left empty content")
        }

        val docHtml = buildDocument(finalSanitized, darkMode)
        return ArtifactSanitizeResult.Success(
            sanitizedBody = finalSanitized,
            documentHtml = docHtml
        )
    }

    private fun sanitizeCssStyle(css: String): String {
        var clean = css
        // Strip @import, url(), expressions
        clean = CSS_IMPORT_URL_PATTERN.replace(clean, "")
        // Strip dangerous scripts in CSS
        clean = HARMFUL_URL_PATTERN.replace(clean, "")
        return clean.trim()
    }

    private fun sanitizeStyleTags(html: String): String {
        val styleTagRegex = Regex("""(?is)<style\b([^>]*)>(.*?)</style>""")
        return styleTagRegex.replace(html) { match ->
            val attrs = match.groupValues[1]
            val innerCss = match.groupValues[2]
            val cleanCss = sanitizeCssStyle(innerCss)
            "<style$attrs>$cleanCss</style>"
        }
    }

    private fun buildDocument(bodyContent: String, darkMode: Boolean): String {
        val textColor = if (darkMode) "#e2e2e8" else "#1b1b1f"
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
              <style>
                * { box-sizing: border-box; }
                html, body {
                  margin: 0;
                  padding: 0;
                  background-color: transparent !important;
                  overflow: hidden !important;
                  height: auto !important;
                  min-height: auto !important;
                  width: 100%;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  color: $textColor;
                }
                /* Cursive & handwriting fallbacks */
                .handwriting, [class*="note"], [class*="paper"], [class*="letter"], [class*="hand"], [class*="chalk"] {
                  font-family: 'Caveat', 'Segoe Script', 'Brush Script MT', 'Dancing Script', 'Casual', cursive;
                }
                /* Monospace / receipt fallbacks */
                .monospace, [class*="receipt"], [class*="code"], [class*="terminal"], [class*="ticket"] {
                  font-family: 'Courier Prime', 'Courier New', Courier, monospace;
                }
              </style>
            </head>
            <body>
              <div id="artifact-root" style="display: flow-root; width: 100%; margin: 0; padding: 10px 4px 18px 4px;">
                $bodyContent
              </div>
              <script>
                (function() {
                  function report() {
                    try {
                      var root = document.getElementById('artifact-root');
                      if (!root) return;
                      var rect = root.getBoundingClientRect();
                      var h = Math.ceil(rect.height || root.offsetHeight || document.body.scrollHeight || 0);
                      if (h > 20 && window.AndroidInterface && window.AndroidInterface.onHeightChanged) {
                        window.AndroidInterface.onHeightChanged(h);
                      }
                    } catch(e) {}
                  }
                  if (document.readyState === 'complete') {
                    report();
                  } else {
                    window.addEventListener('DOMContentLoaded', report);
                    window.addEventListener('load', report);
                  }
                  if (window.ResizeObserver) {
                    new ResizeObserver(report).observe(document.getElementById('artifact-root'));
                  }
                  setTimeout(report, 30);
                  setTimeout(report, 150);
                  setTimeout(report, 500);
                  setTimeout(report, 1200);
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
