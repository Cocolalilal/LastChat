package me.rerere.rikkahub.data.browser

import kotlinx.serialization.json.JsonElement
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parsed input for a browser_use tool call across all 26 actions.
 */
data class BrowserActionInput(
    val action: BrowserAction,
    val toolTitle: String? = null,
    val url: String? = null,
    val selector: String? = null,
    val text: String? = null,
    val coordinateX: Int? = null,
    val coordinateY: Int? = null,
    val direction: ScrollDirection? = null,
    val amount: Int? = null,
    val script: String? = null,
    val userAgent: UserAgentProfile? = null,
    val customUserAgent: String? = null,
    val maxDepth: Int? = null,
    val tabId: Int? = null,
    val viewportWidth: Int? = null,
    val viewportHeight: Int? = null,
    val reset: Boolean = false,
    val keywords: List<String>? = null,
    val fuzzy: Boolean = false,
    val itemSelector: String? = null,
    val scrollCount: Int? = null,
    val timeoutMs: Int? = null,
    val fullPage: Boolean = false,
    val cookies: List<Map<String, Any?>>? = null,
    val reason: String? = null,
    // Secrets & session persistence
    val key: String? = null,
    val value: String? = null,
    val domain: String? = null,
    val note: String? = null,
    val overwrite: Boolean = false,
) {
    companion object {
        fun parse(element: JsonElement): BrowserActionInput? = parse(element.toString())

        fun parse(json: String): BrowserActionInput? {
            return try {
                val obj = JSONObject(json)
                val actionStr = obj.optString("action", "")
                val action = BrowserAction.fromString(actionStr) ?: return null

                BrowserActionInput(
                    action = action,
                    toolTitle = obj.optString("tool_title").ifBlank { null },
                    url = obj.optString("url").ifEmpty { null },
                    selector = obj.optString("selector").ifEmpty { null },
                    text = if (obj.has("text") && !obj.isNull("text")) obj.optString("text") else null,
                    coordinateX = if (obj.has("coordinate_x") && !obj.isNull("coordinate_x")) obj.optInt("coordinate_x") else null,
                    coordinateY = if (obj.has("coordinate_y") && !obj.isNull("coordinate_y")) obj.optInt("coordinate_y") else null,
                    direction = obj.optString("direction").ifEmpty { null }?.let { ScrollDirection.fromString(it) },
                    amount = if (obj.has("amount") && !obj.isNull("amount")) obj.optInt("amount") else null,
                    script = obj.optString("script").ifEmpty { null },
                    userAgent = obj.optString("user_agent").ifEmpty { null }?.let { UserAgentProfile.fromString(it) },
                    customUserAgent = obj.optString("custom_user_agent").ifEmpty { null },
                    maxDepth = if (obj.has("max_depth") && !obj.isNull("max_depth")) obj.optInt("max_depth") else null,
                    tabId = if (obj.has("tab_id") && !obj.isNull("tab_id")) obj.optInt("tab_id") else null,
                    viewportWidth = if (obj.has("viewport_width") && !obj.isNull("viewport_width")) obj.optInt("viewport_width") else null,
                    viewportHeight = if (obj.has("viewport_height") && !obj.isNull("viewport_height")) obj.optInt("viewport_height") else null,
                    reset = obj.optBoolean("reset", false),
                    keywords = parseKeywords(obj),
                    fuzzy = obj.optBoolean("fuzzy", false),
                    itemSelector = obj.optString("item_selector").ifEmpty { null },
                    scrollCount = if (obj.has("scroll_count") && !obj.isNull("scroll_count")) obj.optInt("scroll_count") else null,
                    timeoutMs = parseTimeoutMs(obj),
                    fullPage = obj.optBoolean("full_page", false),
                    cookies = parseCookies(obj),
                    reason = obj.optString("reason").ifBlank { null },
                    key = obj.optString("key").ifBlank { null },
                    value = if (obj.has("value") && !obj.isNull("value")) obj.optString("value") else null,
                    domain = obj.optString("domain").ifBlank { null },
                    note = obj.optString("note").ifBlank { null },
                    overwrite = obj.optBoolean("overwrite", false),
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun parseTimeoutMs(obj: JSONObject): Int? {
            if (!obj.has("timeout") || obj.isNull("timeout")) return null
            val raw = obj.optInt("timeout", -1)
            if (raw <= 0) return null
            // Schema documents timeout in seconds (default 10), while legacy callers may pass ms.
            return if (raw <= 120) raw * 1000 else raw
        }

        private fun parseKeywords(obj: JSONObject): List<String>? {
            if (!obj.has("keywords") || obj.isNull("keywords")) return null
            val arr = obj.optJSONArray("keywords")
            if (arr != null) {
                return (0 until arr.length())
                    .mapNotNull { arr.optString(it).trim().takeIf(String::isNotBlank) }
                    .takeIf { it.isNotEmpty() }
            }
            val str = obj.optString("keywords", "").trim()
            if (str.isEmpty()) return null
            return str.split(Regex("\\s+|,"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
        }

        /**
         * `cookies` may arrive either as a real JSON array or as a JSON-encoded
         * STRING like `"[{\"name\":...}]"`. Accept both.
         */
        private fun parseCookies(obj: JSONObject): List<Map<String, Any?>>? {
            if (!obj.has("cookies") || obj.isNull("cookies")) return null
            val arr: JSONArray? = obj.optJSONArray("cookies")
                ?: obj.optString("cookies").takeIf { it.isNotBlank() }
                    ?.let { runCatching { JSONArray(it) }.getOrNull() }
            arr ?: return null
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { co ->
                    co.keys().asSequence().associateWith { k ->
                        if (co.isNull(k)) null else co.get(k)
                    }
                }
            }.takeIf { it.isNotEmpty() }
        }
    }
}
