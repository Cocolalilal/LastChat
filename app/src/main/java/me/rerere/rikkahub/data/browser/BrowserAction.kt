package me.rerere.rikkahub.data.browser

import me.rerere.rikkahub.data.datastore.BrowserSettings
import me.rerere.rikkahub.data.datastore.BrowserViewMode

/**
 * All 26 browser actions available to the agent in LastChat.
 */
enum class BrowserAction(val value: String) {
    // Navigation & Tabs (4)
    NAVIGATE("navigate"),
    NEW_TAB("new_tab"),
    CLOSE_TAB("close_tab"),
    LIST_TABS("list_tabs"),

    // Visual & Viewport (3)
    SCREENSHOT("screenshot"),
    SET_VIEWPORT("set_viewport"),
    SET_USER_AGENT("set_user_agent"),

    // Interaction (4)
    CLICK("click"),
    TYPE("type"),
    HOVER("hover"),
    SCROLL("scroll"),

    // DOM & Reading (6)
    GET_TEXT("get_text"),
    GET_READABLE("get_readable"),
    GET_PAGE_INFO("get_page_info"),
    GET_BACKBONE("get_backbone"),
    FIND_ELEMENTS("find_elements"),
    SCROLL_AND_COLLECT("scroll_and_collect"),

    // JS, Sync & Network (5)
    EXECUTE_JS("execute_js"),
    WAIT_FOR_DOM_STABLE("wait_for_dom_stable"),
    FETCH("fetch"),
    GET_COOKIES("get_cookies"),
    SET_COOKIES("set_cookies"),

    // Human-in-the-Loop (1)
    HAND_OVER("hand_over"),

    // Secrets & Session Persistence (3)
    SAVE_SECRET("save_secret"),
    SAVE_COOKIES_AS_SECRET("save_cookies_as_secret"),
    RESTORE_COOKIES_FROM_SECRET("restore_cookies_from_secret"),

    // Session UI Control (1)
    CLOSE_BROWSER("close_browser");

    val opensNewPage: Boolean get() = this in opensNewPageActions

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromString(s: String): BrowserAction? = map[s.trim()]

        val visualChangeActions: Set<BrowserAction> = setOf(
            NAVIGATE, CLICK, SCROLL, HOVER, TYPE
        )

        val opensNewPageActions: Set<BrowserAction> = setOf(
            NAVIGATE
        )

        val allValues: List<String> = entries.map { it.value }
    }
}

enum class ScrollDirection(val value: String) {
    UP("up"),
    DOWN("down");

    companion object {
        fun fromString(s: String): ScrollDirection? = entries.find { it.value.equals(s.trim(), ignoreCase = true) }
    }
}

enum class UserAgentProfile(val value: String) {
    MOBILE_CHROME("mobile_chrome"),
    DESKTOP_CHROME("desktop_chrome"),
    CUSTOM("custom");

    val userAgentString: String?
        get() = when (this) {
            MOBILE_CHROME -> "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
            DESKTOP_CHROME -> "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
            CUSTOM -> null
        }

    val displayName: String
        get() = when (this) {
            MOBILE_CHROME -> "Mobile Chrome"
            DESKTOP_CHROME -> "Desktop Chrome"
            CUSTOM -> "Custom"
        }

    val viewportSize: Pair<Int, Int>
        get() = when (this) {
            DESKTOP_CHROME -> 1280 to 800
            MOBILE_CHROME, CUSTOM -> 412 to 915
        }

    companion object {
        fun fromString(s: String): UserAgentProfile? = entries.find { it.value.equals(s.trim(), ignoreCase = true) }
    }
}

fun BrowserSettings.resolveDefaultProfileAndViewport(): Pair<UserAgentProfile, Pair<Int, Int>> {
    return when (defaultViewMode) {
        BrowserViewMode.DESKTOP -> UserAgentProfile.DESKTOP_CHROME to (1280 to 800)
        BrowserViewMode.MOBILE -> UserAgentProfile.MOBILE_CHROME to (412 to 915)
        BrowserViewMode.FIT_SCREEN -> {
            val profile = if (customUserAgent.isNotBlank()) UserAgentProfile.CUSTOM else UserAgentProfile.MOBILE_CHROME
            profile to (0 to 0)
        }
        BrowserViewMode.CUSTOM -> {
            val profile = if (customUserAgent.isNotBlank()) UserAgentProfile.CUSTOM else UserAgentProfile.DESKTOP_CHROME
            profile to (customViewportWidth.coerceIn(200, 4096) to customViewportHeight.coerceIn(200, 4096))
        }
    }
}
