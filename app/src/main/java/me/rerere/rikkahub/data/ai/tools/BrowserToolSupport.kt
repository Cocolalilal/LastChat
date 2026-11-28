package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.rikkahub.data.ai.TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY
import me.rerere.rikkahub.data.browser.BrowserAction
import me.rerere.rikkahub.data.browser.BrowserActionInput
import me.rerere.rikkahub.data.browser.BrowserSecretsStore
import me.rerere.rikkahub.data.browser.BrowserTabPool
import me.rerere.rikkahub.data.browser.resolveDefaultProfileAndViewport
import me.rerere.rikkahub.data.datastore.AgentSecretWritePolicy
import me.rerere.rikkahub.data.datastore.BrowserSettings
import me.rerere.rikkahub.data.datastore.SettingsStore
import kotlin.uuid.Uuid

internal const val BROWSER_TOOL_NAME = "browser_use"

private const val COMPACT_BROWSER_DESCRIPTION =
    "Control a native multi-tab web browser. Actions: navigate, new_tab, close_tab, list_tabs, " +
        "screenshot (returns visual image), set_viewport, set_user_agent, click, type (supports {{SECRET:KEY}}), " +
        "hover, scroll, get_text, get_readable, get_page_info, get_backbone, find_elements, scroll_and_collect, " +
        "execute_js (async IIFE; await + return supported), wait_for_dom_stable, fetch, get_cookies, set_cookies, " +
        "hand_over, save_secret, save_cookies_as_secret, restore_cookies_from_secret."

private const val VERBOSE_BROWSER_DESCRIPTION =
    "Control a native multi-tab Android WebView browser (up to configured maxTabs) sharing persistent cookies. " +
        "Navigation & tabs: navigate(url, tab_id?), new_tab(url?), close_tab(tab_id?), list_tabs(). " +
        "Visual & viewport: screenshot(full_page?, tab_id?) captures the page visually; set_viewport(viewport_width, viewport_height, reset?) " +
        "overrides CSS layout size; set_user_agent(user_agent: desktop_chrome|mobile_chrome|custom, custom_user_agent?). " +
        "Interaction: click(selector or coordinate_x/coordinate_y), type(selector, text — supports {{SECRET:KEY}} local substitution), " +
        "hover(selector), scroll(direction: up|down, amount?, selector?). " +
        "DOM & reading: get_text(selector?), get_readable(), get_page_info(), get_backbone(max_depth?), find_elements(selector), " +
        "scroll_and_collect(item_selector?, scroll_count?, keywords?). " +
        "JS & network: execute_js(script) runs inside an async function wrapper (top-level return and await work), " +
        "wait_for_dom_stable(timeout?), fetch(url) sends an in-session fetch with WebView cookies, " +
        "get_cookies(keywords?, fuzzy?), set_cookies(cookies). " +
        "Human-in-the-loop: hand_over(reason, url?) opens the live browser for the user and waits until they tap Done. " +
        "Secrets: save_secret(key, value, domain?, note?, overwrite?), save_cookies_as_secret(key, keywords?, fuzzy?, overwrite?), " +
        "restore_cookies_from_secret(key, url?)."

internal fun buildBrowserToolDefinitionPreview(browserSettings: BrowserSettings): LocalToolDefinitionPreview {
    val compact = browserSettings.useCompactToolSchema
    val description = if (compact) COMPACT_BROWSER_DESCRIPTION else VERBOSE_BROWSER_DESCRIPTION
    val schema = InputSchema.Obj(
        properties = buildJsonObject {
            put("tool_title", buildJsonObject {
                put("type", "string")
                put("description", if (compact) "5-10 word summary in user's language" else "A clear 5-10 word summary of this browser step in the user's language")
            })
            put("action", buildJsonObject {
                put("type", "string")
                put("description", "Browser action to perform")
                putJsonArray("enum") {
                    BrowserAction.allValues.forEach { add(JsonPrimitive(it)) }
                }
            })
            put("url", buildJsonObject {
                put("type", "string")
                put("description", if (compact) "Target URL (navigate/fetch/new_tab/hand_over)" else "URL to navigate to, fetch with session cookies, open in a new tab, or restore cookies for")
            })
            put("selector", buildJsonObject {
                put("type", "string")
                put("description", if (compact) "CSS selector (click/type/hover/get_text/scroll/find_elements)" else "CSS selector of the target element for click, type, hover, get_text, find_elements, or scrollable container for scroll")
            })
            put("text", buildJsonObject {
                put("type", "string")
                put("description", if (compact) "Text to type; supports {{SECRET:KEY}}" else "Text to type into selector; supports {{SECRET:KEY}} local placeholder injection without exposing secret values")
            })
            put("coordinate_x", buildJsonObject {
                put("type", "integer")
                put("description", "Viewport X px for click")
            })
            put("coordinate_y", buildJsonObject {
                put("type", "integer")
                put("description", "Viewport Y px for click")
            })
            put("direction", buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("up")); add(JsonPrimitive("down")) }
            })
            put("amount", buildJsonObject {
                put("type", "integer")
                put("description", "Scroll px (default 500)")
            })
            put("script", buildJsonObject {
                put("type", "string")
                put("description", if (compact) "Async JS body for execute_js; await/return supported" else "JavaScript body for execute_js; runs inside an async IIFE where await and top-level return are supported")
            })
            put("user_agent", buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("desktop_chrome"))
                    add(JsonPrimitive("mobile_chrome"))
                    add(JsonPrimitive("custom"))
                }
            })
            put("custom_user_agent", buildJsonObject {
                put("type", "string")
                put("description", "Custom User-Agent when user_agent=custom")
            })
            put("max_depth", buildJsonObject {
                put("type", "integer")
                put("description", "Max tree depth for get_backbone (default 5)")
            })
            put("tab_id", buildJsonObject {
                put("type", "integer")
                put("description", "Target tab ID (default most recent)")
            })
            put("viewport_width", buildJsonObject {
                put("type", "integer")
                put("description", "CSS width px for set_viewport")
            })
            put("viewport_height", buildJsonObject {
                put("type", "integer")
                put("description", "CSS height px for set_viewport")
            })
            put("reset", buildJsonObject {
                put("type", "boolean")
                put("description", "set_viewport: true clears session override")
            })
            put("keywords", buildJsonObject {
                put("type", "string")
                put("description", if (compact) "Space/comma keywords (get_cookies/scroll_and_collect/save_cookies_as_secret)" else "Space- or comma-separated filter keywords for get_cookies, save_cookies_as_secret, or scroll_and_collect")
            })
            put("fuzzy", buildJsonObject {
                put("type", "boolean")
                put("description", "Substring cookie name matching (get_cookies/save_cookies_as_secret)")
            })
            put("item_selector", buildJsonObject {
                put("type", "string")
                put("description", "Item CSS selector for scroll_and_collect")
            })
            put("scroll_count", buildJsonObject {
                put("type", "integer")
                put("description", "Scroll steps for scroll_and_collect (max 20)")
            })
            put("timeout", buildJsonObject {
                put("type", "integer")
                put("description", "Seconds to wait for wait_for_dom_stable (default 10)")
            })
            put("full_page", buildJsonObject {
                put("type", "boolean")
                put("description", if (compact) "screenshot: capture full scrollHeight (cap 32768px)" else "When true on screenshot, stretch viewport to full document.scrollHeight (capped at 32768px)")
            })
            put("cookies", buildJsonObject {
                put("type", "string")
                put("description", if (compact) "set_cookies: JSON array of {name,value,domain?,path?,secure?,httpOnly?,expires?}" else "set_cookies: JSON array (or JSON-encoded string) of {name, value, domain?, path?, secure?, http_only?/httpOnly?, expires?/expirationDate?}")
            })
            put("reason", buildJsonObject {
                put("type", "string")
                put("description", "Short explanation for hand_over in user's language")
            })
            put("key", buildJsonObject {
                put("type", "string")
                put("description", "Secret key [A-Za-z][A-Za-z0-9_]* for save_secret/save_cookies_as_secret/restore_cookies_from_secret")
            })
            put("value", buildJsonObject {
                put("type", "string")
                put("description", "Secret value for save_secret")
            })
            put("domain", buildJsonObject {
                put("type", "string")
                put("description", "Optional domain for save_secret/save_cookies_as_secret")
            })
            put("note", buildJsonObject {
                put("type", "string")
                put("description", "Optional note for save_secret/save_cookies_as_secret")
            })
            put("overwrite", buildJsonObject {
                put("type", "boolean")
                put("description", "Allow replacing an existing secret key (default false)")
            })
        },
        required = listOf("tool_title", "action"),
    )
    return LocalToolDefinitionPreview(
        name = BROWSER_TOOL_NAME,
        description = description,
        schema = schema,
    )
}

internal fun buildBrowserSystemPrompt(
    browserSettings: BrowserSettings,
    secretsStore: BrowserSecretsStore?,
    hasWebSearch: Boolean,
): String = buildString {
    val (defaultProfile, defaultViewport) = browserSettings.resolveDefaultProfileAndViewport()
    appendLine("## Agent Browser (`browser_use`)")
    appendLine("- Active defaults: view=${browserSettings.defaultViewMode.name.lowercase()} (${defaultProfile.value}, ${if (defaultViewport.first > 0) "${defaultViewport.first}x${defaultViewport.second}" else "fit screen"}), maxTabs=${browserSettings.maxTabs}.")
    appendLine("- Visual inspection: call `screenshot` when you need to inspect layout, images, buttons, or rendered state. Always provide `tool_title` (5–10 words in the user's language).")

    if (!browserSettings.fullAutonomyMode && browserSettings.autoHandoffOnSecretFields && browserSettings.allowAgentHandOverAction) {
        appendLine("- Autonomy & Hand-Over: NEVER ask the user to log in using plain prose, and NEVER try to type passwords, OTPs, or payment codes directly. Whenever a page requires login, 2FA, CAPTCHA, or payment, call `browser_use(action=\"hand_over\", reason=\"...\")` immediately so the live browser sheet opens for the user and resumes when they tap Done.")
    } else {
        appendLine("- Autonomous Auth Mode: You may sign in directly on login forms using saved `{{SECRET:KEY}}` placeholders in `type` or `restore_cookies_from_secret(key)`. Only call `hand_over` (if enabled) when blocked by a CAPTCHA or interactive 2FA challenge.")
    }

    val manifest = secretsStore?.formatPromptManifest() ?: "Saved secrets: none."
    appendLine("- $manifest")
    when (browserSettings.agentSecretWritePolicy) {
        AgentSecretWritePolicy.ASK_BEFORE_SAVING ->
            appendLine("- Secret saving: you may call `save_secret` or `save_cookies_as_secret` to persist logins/sessions (requires user confirmation on card; pass `overwrite=true` only when replacing an existing key).")
        AgentSecretWritePolicy.AUTO_SAVE ->
            appendLine("- Secret saving: you may call `save_secret` or `save_cookies_as_secret` to persist credentials or authenticated sessions directly (pass `overwrite=true` to update an existing key).")
        AgentSecretWritePolicy.READ_ONLY -> Unit
    }

    appendLine("- Android Google OAuth Guardrail: Google permanently blocks sign-in inside Android WebViews (`accounts.google.com`, `disallowed_useragent`, \"browser is not secure\"). Do NOT retry Google Login/OAuth walls in `browser_use` — instead give the user a clickable Markdown link `[Open in Chrome](https://accounts.google.com/...)` and ask them to finish in Chrome Custom Tabs.")

    if (browserSettings.enableSearchToBrowserEscalationHint && hasWebSearch) {
        appendLine("- Search vs Browser: Use `search_web` for fast factual lookups, news, and quick queries. Escalate to `browser_use` for JavaScript-rendered SPAs, interactive flows, multi-step forms, authenticated pages, or visual layout inspection.")
    }
}.trimEnd()

internal fun buildBrowserTool(
    browserTabPool: BrowserTabPool,
    settingsStore: SettingsStore,
    conversationId: Uuid,
    modelId: String? = null,
    hasWebSearch: Boolean = false,
): Tool {
    val initialSettings = settingsStore.settingsFlow.value.browserSettings
    val preview = buildBrowserToolDefinitionPreview(initialSettings)
    return Tool(
        name = preview.name,
        description = preview.description,
        parameters = {
            buildBrowserToolDefinitionPreview(settingsStore.settingsFlow.value.browserSettings).schema
        },
        approvalMode = ToolApprovalMode.Auto,
        systemPrompt = { _, _ ->
            buildBrowserSystemPrompt(
                browserSettings = settingsStore.settingsFlow.value.browserSettings,
                secretsStore = browserTabPool.secretsStore,
                hasWebSearch = hasWebSearch,
            )
        },
        execute = { args ->
            val input = BrowserActionInput.parse(args)
                ?: return@Tool buildJsonObject {
                    put("success", false)
                    put("error", "Invalid browser_use arguments. 'action' must be one of: ${BrowserAction.allValues.joinToString(", ")}")
                }
            browserTabPool.setSession(conversationId.toString())
            browserTabPool.setActiveModelId(modelId)
            val result = browserTabPool.execute(input)
            buildJsonObject {
                put("success", result.success)
                put("text", result.text)
                result.pageURL?.let { put("page_url", it) }
                result.tabId?.let { put("tab_id", it) }
                result.imageFilePath?.let { put("image_file_path", it) }
                if (result.handoffRequested) put("handoff_requested", true)
                result.fetchedFileName?.let { put("fetched_file_name", it) }
                result.fetchedFileData?.let { put("fetched_bytes", it.size) }
                if (!result.base64Image.isNullOrBlank()) {
                    putJsonArray(TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY) {
                        add(
                            buildJsonObject {
                                put("data_url", "data:image/jpeg;base64,${result.base64Image}")
                                put("mime_type", "image/jpeg")
                                result.pageURL?.let { put("title", it) }
                                result.imageFilePath?.let { put("source_url", it) }
                                put("origin_tool", BROWSER_TOOL_NAME)
                            }
                        )
                    }
                }
            }
        },
    )
}
