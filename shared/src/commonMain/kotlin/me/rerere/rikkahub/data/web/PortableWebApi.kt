package me.rerere.rikkahub.data.web

data class PortableWebRequest(
    val method: String,
    val path: String,
    val authorization: String?,
    val query: Map<String, String>,
    val body: String?,
    val bodyBytes: ByteArray? = null,
    val contentType: String? = null,
)

data class PortableMultipartFile(
    val fieldName: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class PortableWebApiResponse(
    val statusCode: Int,
    val contentType: String = JSON,
    val body: String = "",
    val bytes: ByteArray? = null,
    val sse: Boolean = false,
    val sseKeepAliveMs: Long? = null,
) {
    companion object {
        const val JSON = "application/json; charset=utf-8"
        const val HTML = "text/html; charset=utf-8"
        const val EVENT_STREAM = "text/event-stream"
        const val OCTET = "application/octet-stream"
        const val SVG = "image/svg+xml"
        const val SETTINGS_SSE_HEARTBEAT_MS = 15_000L
        const val CONVERSATION_SSE_HEARTBEAT_MS = 1_000L

        fun json(status: Int, body: String) = PortableWebApiResponse(statusCode = status, body = body)

        fun sse(event: String, data: String, keepAliveMs: Long? = null) = PortableWebApiResponse(
            statusCode = 200,
            contentType = EVENT_STREAM,
            body = formatSse(event, data),
            sse = true,
            sseKeepAliveMs = keepAliveMs,
        )

        fun formatSse(event: String, data: String): String =
            "event: $event\ndata: ${data.replace("\n", "\ndata: ")}\n\n"

        const val SSE_HEARTBEAT = ": heartbeat\n\n"

        fun html(body: String) = PortableWebApiResponse(
            statusCode = 200,
            contentType = HTML,
            body = body,
        )

        fun bytes(contentType: String, bytes: ByteArray) = PortableWebApiResponse(
            statusCode = 200,
            contentType = contentType,
            bytes = bytes,
        )
    }
}

data class PortableWebApiActions(
    val onCreateConversation: (body: String) -> PortableWebApiResponse = { notImplemented() },
    val onDeleteConversation: (id: String) -> PortableWebApiResponse = { notImplemented() },
    val onPinConversation: (id: String) -> PortableWebApiResponse = { notImplemented() },
    val onRegenerateTitle: (id: String) -> PortableWebApiResponse = { notImplemented() },
    val onContextRefresh: (id: String) -> PortableWebApiResponse = { notImplemented() },
    val onRenameConversation: (id: String, title: String) -> PortableWebApiResponse = { _, _ -> notImplemented() },
    val onMoveConversation: (id: String, assistantId: String) -> PortableWebApiResponse = { _, _ -> notImplemented() },
    val onUpdateSkills: (id: String, skillIds: List<String>) -> PortableWebApiResponse = { _, _ -> notImplemented() },
    val onEditMessage: (conversationId: String, messageId: String, body: String) -> PortableWebApiResponse =
        { _, _, _ -> notImplemented() },
    val onForkConversation: (conversationId: String, messageId: String) -> PortableWebApiResponse =
        { _, _ -> notImplemented() },
    val onDeleteMessage: (conversationId: String, messageId: String) -> PortableWebApiResponse =
        { _, _ -> notImplemented() },
    val onSelectNode: (conversationId: String, nodeId: String, selectIndex: Int) -> PortableWebApiResponse =
        { _, _, _ -> notImplemented() },
    val onRegenerate: (conversationId: String, messageId: String) -> PortableWebApiResponse =
        { _, _ -> notImplemented() },
    val onToolApproval: (conversationId: String, body: String) -> PortableWebApiResponse =
        { _, _ -> notImplemented() },
    val onSelectAssistant: (assistantId: String) -> PortableWebApiResponse = { notImplemented() },
    val onSelectModel: (body: String) -> PortableWebApiResponse = { notImplemented() },
    val onThinkingBudget: (body: String) -> PortableWebApiResponse = { notImplemented() },
    val onUpdateMcp: (body: String) -> PortableWebApiResponse = { notImplemented() },
    val onUpdateInjections: (body: String) -> PortableWebApiResponse = { notImplemented() },
    val onSearchEnabled: (enabled: Boolean) -> PortableWebApiResponse = { notImplemented() },
    val onSearchService: (index: Int) -> PortableWebApiResponse = { notImplemented() },
    val onBuiltInTool: (body: String) -> PortableWebApiResponse = { notImplemented() },
    val onFavoriteModels: (body: String) -> PortableWebApiResponse = { notImplemented() },
    val onUploadFiles: (files: List<PortableMultipartFile>) -> PortableWebApiResponse = { notImplemented() },
    val onDeleteFile: (id: String) -> PortableWebApiResponse = { notImplemented() },
)

private fun notImplemented() =
    PortableWebApiResponse.json(501, """{"error":"not_implemented","code":501}""")

data class PortableWebApiSource(
    val webUiBundled: Boolean,
    val authRequired: Boolean,
    val conversationsListJson: String,
    val bootstrapJson: String,
    val settingsJson: String,
    val conversationJson: (String) -> String?,
    val staticAsset: (path: String) -> PortableWebApiResponse?,
    val fileContent: (uri: String) -> PortableWebApiResponse?,
    val filePath: (relativePath: String) -> PortableWebApiResponse?,
    val onSendMessage: (conversationId: String, body: String) -> PortableWebApiResponse,
    val onStop: (conversationId: String) -> PortableWebApiResponse,
    val conversationsInvalidateJson: () -> String = { """{"assistantId":"","timestamp":0}""" },
    val actions: PortableWebApiActions = PortableWebApiActions(),
)

/**
 * Password-gated JSON + static-asset router shared by the iOS Ktor host.
 * Android still owns the full Ktor module in `:app`; this is the portable subset
 * that can serve the same React web-ui once assets are bundled.
 */
object PortableWebApiRouter {
    fun handle(
        method: String,
        path: String,
        authorization: String?,
        query: Map<String, String>,
        body: String?,
        password: String?,
        source: PortableWebApiSource,
        bodyBytes: ByteArray? = null,
        contentType: String? = null,
    ): PortableWebApiResponse {
        val queryToken = query["access_token"]
        val normalized = path.substringBefore('?').trimEnd('/').ifBlank { "/" }
        val get = method.equals("GET", ignoreCase = true)
        val post = method.equals("POST", ignoreCase = true)
        val delete = method.equals("DELETE", ignoreCase = true)
        val actions = source.actions

        if (get && normalized == "/api/health") {
            return PortableWebApiResponse.json(
                200,
                """{"ok":true,"app":"LastChat","webUiBundled":${source.webUiBundled}}""",
            )
        }

        if (post && normalized == "/api/auth/token") {
            return issueToken(password, body)
        }

        if (get && !normalized.startsWith("/api")) {
            val assetPath = when {
                normalized == "/" -> "index.html"
                normalized.startsWith("/") -> normalized.removePrefix("/")
                else -> normalized
            }
            val asset = source.staticAsset(assetPath)
            if (asset != null) {
                return if (assetPath == "index.html" && asset.body.isNotEmpty()) {
                    PortableWebApiResponse.html(injectBootConfig(asset.body, source.authRequired))
                } else {
                    asset
                }
            }
            if (normalized == "/" || !assetPath.contains('.')) {
                return if (source.webUiBundled) {
                    source.staticAsset("index.html")
                        ?.let { PortableWebApiResponse.html(injectBootConfig(it.body, source.authRequired)) }
                        ?: PortableWebApiResponse.html(injectBootConfig(fallbackIndexHtml(), source.authRequired))
                } else {
                    PortableWebApiResponse.html(injectBootConfig(fallbackIndexHtml(), source.authRequired))
                }
            }
        }

        if (!authorized(password, authorization, queryToken)) {
            return PortableWebApiResponse.json(401, """{"error":"unauthorized","code":401}""")
        }

        if (get && normalized == "/api/bootstrap") {
            return PortableWebApiResponse.json(200, source.bootstrapJson)
        }
        if (get && normalized == "/api/ai-icon") {
            return PortableWebApiResponse(
                statusCode = 200,
                contentType = PortableWebApiResponse.SVG,
                body = PLACEHOLDER_AI_ICON_SVG,
            )
        }
        if (get && normalized == "/api/settings/stream") {
            return PortableWebApiResponse.sse(
                event = "update",
                data = source.settingsJson,
                keepAliveMs = PortableWebApiResponse.SETTINGS_SSE_HEARTBEAT_MS,
            )
        }
        if (post && normalized == "/api/settings/assistant/model") {
            return actions.onSelectModel(body.orEmpty())
        }
        if (post && normalized == "/api/settings/assistant/thinking-budget") {
            return actions.onThinkingBudget(body.orEmpty())
        }
        if (post && normalized == "/api/settings/assistant/mcp") {
            return actions.onUpdateMcp(body.orEmpty())
        }
        if (post && normalized == "/api/settings/assistant/injections") {
            return actions.onUpdateInjections(body.orEmpty())
        }
        if (post && normalized == "/api/settings/assistant") {
            return actions.onSelectAssistant(jsonString(body.orEmpty(), "assistantId").orEmpty())
        }
        if (post && normalized == "/api/settings/search/enabled") {
            return actions.onSearchEnabled(jsonBoolean(body.orEmpty(), "enabled") == true)
        }
        if (post && normalized == "/api/settings/search/service") {
            return actions.onSearchService(jsonInt(body.orEmpty(), "index") ?: 0)
        }
        if (post && normalized == "/api/settings/model/built-in-tool") {
            return actions.onBuiltInTool(body.orEmpty())
        }
        if (post && normalized == "/api/settings/favorite-models") {
            return actions.onFavoriteModels(body.orEmpty())
        }
        if (get && normalized == "/api/conversations/stream") {
            return PortableWebApiResponse.sse(
                event = "invalidate",
                data = source.conversationsInvalidateJson(),
                keepAliveMs = PortableWebApiResponse.SETTINGS_SSE_HEARTBEAT_MS,
            )
        }
        if (get && normalized == "/api/conversations") {
            return PortableWebApiResponse.json(200, source.conversationsListJson)
        }
        if (post && normalized == "/api/conversations") {
            return actions.onCreateConversation(body.orEmpty())
        }
        if (get && normalized == "/api/conversations/paged") {
            return PortableWebApiResponse.json(
                200,
                """{"items":${source.conversationsListJson},"nextOffset":null,"hasMore":false}""",
            )
        }
        if (get && normalized == "/api/conversations/search") {
            val q = query["q"].orEmpty()
            return PortableWebApiResponse.json(200, filterConversations(source.conversationsListJson, q))
        }
        if (post && normalized == "/api/files/upload") {
            return actions.onUploadFiles(parseMultipartFiles(bodyBytes, contentType))
        }
        if (get && normalized == "/api/files/content") {
            val uri = query["uri"].orEmpty()
            return source.fileContent(uri)
                ?: PortableWebApiResponse.json(404, """{"error":"not_found","code":404}""")
        }
        val filePathMatch = FILE_PATH.matchEntire(normalized)
        if (get && filePathMatch != null) {
            return source.filePath(filePathMatch.groupValues[1])
                ?: PortableWebApiResponse.json(404, """{"error":"not_found","code":404}""")
        }
        val fileDeleteMatch = FILE_ID.matchEntire(normalized)
        if (delete && fileDeleteMatch != null) {
            return actions.onDeleteFile(fileDeleteMatch.groupValues[1])
        }

        val streamMatch = CONVERSATION_STREAM.matchEntire(normalized)
        if (get && streamMatch != null) {
            val json = source.conversationJson(streamMatch.groupValues[1])
                ?: return PortableWebApiResponse.json(404, """{"error":"not_found","code":404}""")
            val now = nowEpochMs()
            val payload = """{"type":"snapshot","seq":$now,"conversation":$json,"serverTime":$now}"""
            return PortableWebApiResponse.sse(
                event = "snapshot",
                data = payload,
                keepAliveMs = PortableWebApiResponse.CONVERSATION_SSE_HEARTBEAT_MS,
            )
        }
        val stopMatch = CONVERSATION_STOP.matchEntire(normalized)
        if (post && stopMatch != null) {
            return source.onStop(stopMatch.groupValues[1])
        }
        val editMatch = CONVERSATION_EDIT.matchEntire(normalized)
        if (post && editMatch != null) {
            return actions.onEditMessage(editMatch.groupValues[1], editMatch.groupValues[2], body.orEmpty())
        }
        val deleteMessageMatch = CONVERSATION_DELETE_MESSAGE.matchEntire(normalized)
        if (delete && deleteMessageMatch != null) {
            return actions.onDeleteMessage(deleteMessageMatch.groupValues[1], deleteMessageMatch.groupValues[2])
        }
        val sendMatch = CONVERSATION_MESSAGES.matchEntire(normalized)
        if (post && sendMatch != null) {
            return source.onSendMessage(sendMatch.groupValues[1], body.orEmpty())
        }
        val nodeSelectMatch = CONVERSATION_NODE_SELECT.matchEntire(normalized)
        if (post && nodeSelectMatch != null) {
            return actions.onSelectNode(
                nodeSelectMatch.groupValues[1],
                nodeSelectMatch.groupValues[2],
                jsonInt(body.orEmpty(), "selectIndex") ?: 0,
            )
        }
        val toolApprovalMatch = CONVERSATION_TOOL_APPROVAL.matchEntire(normalized)
        if (post && toolApprovalMatch != null) {
            return actions.onToolApproval(toolApprovalMatch.groupValues[1], body.orEmpty())
        }
        val forkMatch = CONVERSATION_FORK.matchEntire(normalized)
        if (post && forkMatch != null) {
            return actions.onForkConversation(
                forkMatch.groupValues[1],
                jsonString(body.orEmpty(), "messageId").orEmpty(),
            )
        }
        val regenerateMatch = CONVERSATION_REGENERATE.matchEntire(normalized)
        if (post && regenerateMatch != null) {
            return actions.onRegenerate(
                regenerateMatch.groupValues[1],
                jsonString(body.orEmpty(), "messageId").orEmpty(),
            )
        }
        val pinMatch = CONVERSATION_PIN.matchEntire(normalized)
        if (post && pinMatch != null) {
            return actions.onPinConversation(pinMatch.groupValues[1])
        }
        val regenerateTitleMatch = CONVERSATION_REGENERATE_TITLE.matchEntire(normalized)
        if (post && regenerateTitleMatch != null) {
            return actions.onRegenerateTitle(regenerateTitleMatch.groupValues[1])
        }
        val contextRefreshMatch = CONVERSATION_CONTEXT_REFRESH.matchEntire(normalized)
        if (post && contextRefreshMatch != null) {
            return actions.onContextRefresh(contextRefreshMatch.groupValues[1])
        }
        val titleMatch = CONVERSATION_TITLE.matchEntire(normalized)
        if (post && titleMatch != null) {
            return actions.onRenameConversation(
                titleMatch.groupValues[1],
                jsonString(body.orEmpty(), "title").orEmpty(),
            )
        }
        val moveMatch = CONVERSATION_MOVE.matchEntire(normalized)
        if (post && moveMatch != null) {
            return actions.onMoveConversation(
                moveMatch.groupValues[1],
                jsonString(body.orEmpty(), "assistantId").orEmpty(),
            )
        }
        val skillsMatch = CONVERSATION_SKILLS.matchEntire(normalized)
        if (post && skillsMatch != null) {
            return actions.onUpdateSkills(skillsMatch.groupValues[1], jsonStringList(body.orEmpty(), "skillIds"))
        }
        val conversationMatch = CONVERSATION_PATH.matchEntire(normalized)
        if (get && conversationMatch != null) {
            val json = source.conversationJson(conversationMatch.groupValues[1])
                ?: return PortableWebApiResponse.json(404, """{"error":"not_found","code":404}""")
            return PortableWebApiResponse.json(200, json)
        }
        if (delete && conversationMatch != null) {
            return actions.onDeleteConversation(conversationMatch.groupValues[1])
        }
        return PortableWebApiResponse.json(404, """{"error":"not_found","code":404}""")
    }

    fun handle(request: PortableWebRequest, password: String?, source: PortableWebApiSource): PortableWebApiResponse =
        handle(
            method = request.method,
            path = request.path,
            authorization = request.authorization,
            query = request.query,
            body = request.body,
            password = password,
            source = source,
            bodyBytes = request.bodyBytes,
            contentType = request.contentType,
        )

    internal fun authorized(password: String?, authorization: String?, queryToken: String?): Boolean {
        val expected = password?.takeIf { it.isNotBlank() } ?: return true
        val bearer = authorization
            ?.trim()
            ?.removePrefix("Bearer")
            ?.trim()
            ?.removePrefix("Basic")
            ?.trim()
        return bearer == expected || queryToken == expected
    }

    fun injectBootConfig(html: String, authRequired: Boolean): String {
        val script = """<script>window.__LASTCHAT_WEB_BOOT__={"authRequired":$authRequired};</script>"""
        return when {
            html.contains("</head>", ignoreCase = true) ->
                html.replace("</head>", "$script</head>", ignoreCase = true)
            html.contains("<body>", ignoreCase = true) ->
                html.replace("<body>", "<body>$script", ignoreCase = true)
            else -> script + html
        }
    }

    fun guessAssetContentType(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "css" -> "text/css; charset=utf-8"
            "html", "htm" -> PortableWebApiResponse.HTML
            "ico" -> "image/x-icon"
            "js", "mjs" -> "text/javascript; charset=utf-8"
            "json", "map" -> PortableWebApiResponse.JSON
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "txt" -> "text/plain; charset=utf-8"
            "webp" -> "image/webp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            else -> PortableWebApiResponse.OCTET
        }
    }

    fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', missingDelimiterValue = "").ifBlank { return@mapNotNull null }
            val value = percentDecode(pair.substringAfter('=', missingDelimiterValue = ""))
            key to value
        }.toMap()
    }

    fun extractMessageText(body: String): String {
        val texts = TEXT_PART.findAll(body).map { it.groupValues[1] }.toList()
        if (texts.isNotEmpty()) return texts.joinToString("\n")
        val passwordLike = """"text"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
        return unescapeJson(passwordLike.orEmpty())
    }

    private fun issueToken(password: String?, body: String?): PortableWebApiResponse {
        val submitted = PASSWORD_FIELD.find(body.orEmpty())?.groupValues?.getOrNull(1).orEmpty()
            .let(::unescapeJson)
        val expected = password?.takeIf { it.isNotBlank() }
        if (expected != null && submitted != expected) {
            return PortableWebApiResponse.json(401, """{"error":"unauthorized","code":401}""")
        }
        val token = expected ?: submitted.ifBlank { "anonymous" }
        val expiresAt = nowEpochMs() + THIRTY_DAYS_MS
        return PortableWebApiResponse.json(200, """{"token":"$token","expiresAt":$expiresAt}""")
    }

    private fun filterConversations(listJson: String, query: String): String {
        if (query.isBlank()) return listJson
        val needle = query.lowercase()
        val items = OBJECT_ITEM.findAll(listJson).map { it.value }.filter { item ->
            item.lowercase().contains(needle)
        }.toList()
        return items.joinToString(prefix = "[", postfix = "]")
    }

    fun jsonString(body: String, key: String): String? {
        val quoted = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""").find(body)
            ?.groupValues?.getOrNull(1)
        return quoted?.let(::unescapeJson)
    }

    fun jsonBoolean(body: String, key: String): Boolean? {
        val raw = Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""").find(body)
            ?.groupValues?.getOrNull(1) ?: return null
        return raw == "true"
    }

    fun jsonInt(body: String, key: String): Int? =
        Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+)""").find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun jsonStringList(body: String, key: String): List<String> {
        val array = Regex(""""${Regex.escape(key)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(body)?.groupValues?.getOrNull(1) ?: return emptyList()
        return Regex(""""((?:\\.|[^"\\])*)"""").findAll(array).map { unescapeJson(it.groupValues[1]) }.toList()
    }

    private fun unescapeJson(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    private fun percentDecode(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val hex = value.substring(index + 1, index + 3)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    append(decoded.toChar())
                    index += 3
                    continue
                }
            }
            append(if (char == '+') ' ' else char)
            index++
        }
    }

    private fun nowEpochMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun fallbackIndexHtml(): String = FALLBACK_INDEX

    private val CONVERSATION_PATH = Regex("^/api/conversations/([^/]+)$")
    private val CONVERSATION_STREAM = Regex("^/api/conversations/([^/]+)/stream$")
    private val CONVERSATION_STOP = Regex("^/api/conversations/([^/]+)/stop$")
    private val CONVERSATION_MESSAGES = Regex("^/api/conversations/([^/]+)/messages$")
    private val CONVERSATION_EDIT = Regex("^/api/conversations/([^/]+)/messages/([^/]+)/edit$")
    private val CONVERSATION_DELETE_MESSAGE = Regex("^/api/conversations/([^/]+)/messages/([^/]+)$")
    private val CONVERSATION_NODE_SELECT = Regex("^/api/conversations/([^/]+)/nodes/([^/]+)/select$")
    private val CONVERSATION_TOOL_APPROVAL = Regex("^/api/conversations/([^/]+)/tool-approval$")
    private val CONVERSATION_FORK = Regex("^/api/conversations/([^/]+)/fork$")
    private val CONVERSATION_REGENERATE = Regex("^/api/conversations/([^/]+)/regenerate$")
    private val CONVERSATION_PIN = Regex("^/api/conversations/([^/]+)/pin$")
    private val CONVERSATION_REGENERATE_TITLE = Regex("^/api/conversations/([^/]+)/regenerate-title$")
    private val CONVERSATION_CONTEXT_REFRESH = Regex("^/api/conversations/([^/]+)/context-refresh$")
    private val CONVERSATION_TITLE = Regex("^/api/conversations/([^/]+)/title$")
    private val CONVERSATION_MOVE = Regex("^/api/conversations/([^/]+)/move$")
    private val CONVERSATION_SKILLS = Regex("^/api/conversations/([^/]+)/skills$")
    private val FILE_PATH = Regex("^/api/files/path/(.+)$")
    private val FILE_ID = Regex("^/api/files/([^/]+)$")
    private val PASSWORD_FIELD = Regex(""""password"\s*:\s*"((?:\\.|[^"\\])*)"""")
    private val TEXT_PART = Regex(""""type"\s*:\s*"text"\s*,\s*"text"\s*:\s*"((?:\\.|[^"\\])*)"""")
    private val OBJECT_ITEM = Regex("""\{[^{}]*\}""")
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    private const val PLACEHOLDER_AI_ICON_SVG =
        """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="#7a9bb8"/></svg>"""

    private val FALLBACK_INDEX = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>LastChat</title>
  <style>
    :root { color-scheme: light dark; font-family: ui-sans-serif, system-ui, sans-serif; }
    body { margin: 0; background: #111; color: #eee; }
    main { display: grid; grid-template-columns: 280px 1fr; min-height: 100vh; }
    aside, section { padding: 16px; }
    aside { border-right: 1px solid #333; overflow: auto; }
    button, input { font: inherit; }
    .chat { display: flex; flex-direction: column; gap: 12px; max-width: 720px; }
    .msg { padding: 10px 12px; border-radius: 16px; background: #1c1c1c; }
    .msg.user { background: #274690; align-self: flex-end; }
    form { display: flex; gap: 8px; margin-top: 16px; }
    input { flex: 1; padding: 10px 12px; border-radius: 999px; border: 1px solid #444; background: #181818; color: inherit; }
  </style>
</head>
<body>
<main>
  <aside>
    <h1>LastChat</h1>
    <p id="status">Connecting…</p>
    <div id="list"></div>
  </aside>
  <section>
    <h2 id="title">Select a conversation</h2>
    <div id="messages" class="chat"></div>
    <form id="composer">
      <input id="prompt" placeholder="Message" autocomplete="off"/>
      <button type="submit">Send</button>
    </form>
  </section>
</main>
<script>
const tokenKey = "rikkahub:web-auth";
function token() {
  try { return JSON.parse(localStorage.getItem(tokenKey) || "null")?.token; } catch { return null; }
}
async function api(path, opts = {}) {
  const headers = Object.assign({"content-type":"application/json"}, opts.headers || {});
  const t = token();
  if (t) headers.Authorization = "Bearer " + t;
  const res = await fetch("/api/" + path, Object.assign({}, opts, { headers }));
  if (res.status === 401) {
    const password = prompt("Web password") || "";
    const auth = await fetch("/api/auth/token", { method:"POST", headers:{"content-type":"application/json"}, body: JSON.stringify({ password }) });
    if (!auth.ok) throw new Error("unauthorized");
    const body = await auth.json();
    localStorage.setItem(tokenKey, JSON.stringify(body));
    return api(path, opts);
  }
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}
let activeId = null;
async function refreshList() {
  const items = await api("conversations");
  document.getElementById("status").textContent = items.length + " chats";
  document.getElementById("list").innerHTML = items.map((item) =>
    "<p><button data-id=\"" + item.id + "\">" + (item.title || "New chat") + "</button></p>"
  ).join("");
  document.querySelectorAll("button[data-id]").forEach((button) => {
    button.onclick = () => openChat(button.dataset.id);
  });
}
async function openChat(id) {
  activeId = id;
  const conversation = await api("conversations/" + id);
  document.getElementById("title").textContent = conversation.title || "New chat";
  const nodes = conversation.messages || [];
  const messages = nodes.flatMap((node) => {
    const selected = node.messages?.[node.selectIndex] || node.messages?.[0];
    return selected ? [selected] : [];
  });
  document.getElementById("messages").innerHTML = messages.map((message) => {
    const text = (message.parts || []).filter((part) => part.type === "text").map((part) => part.text).join("\n");
    return "<div class=\"msg " + (message.role === "user" ? "user" : "") + "\">" +
      text.replace(/[<>&]/g, function(c) { return ({"<":"&lt;",">":"&gt;","&":"&amp;"})[c]; }) +
      "</div>";
  }).join("");
}
document.getElementById("composer").onsubmit = async (event) => {
  event.preventDefault();
  if (!activeId) return;
  const input = document.getElementById("prompt");
  const text = input.value.trim();
  if (!text) return;
  input.value = "";
  await api("conversations/" + activeId + "/messages", {
    method: "POST",
    body: JSON.stringify({ parts: [{ type: "text", text }] }),
  });
  setTimeout(() => openChat(activeId), 800);
};
refreshList().catch((error) => { document.getElementById("status").textContent = error.message; });
</script>
</body>
</html>
"""
}

fun toWebMediaUrl(url: String): String {
    val lower = url.lowercase()
    return if (
        lower.startsWith("file://") ||
        lower.startsWith("content://") ||
        lower.startsWith("android.resource://")
    ) {
        "/api/files/content?uri=" + percentEncode(url)
    } else {
        url
    }
}

internal fun percentEncode(value: String): String = buildString {
    val bytes = value.encodeToByteArray()
    for (byte in bytes) {
        val unsigned = byte.toInt() and 0xFF
        val char = unsigned.toChar()
        if (char.isLetterOrDigit() || char in "-._~") {
            append(char)
        } else {
            append('%')
            append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

fun parseMultipartFiles(body: ByteArray?, contentType: String?): List<PortableMultipartFile> {
    if (body == null || body.isEmpty() || contentType.isNullOrBlank()) return emptyList()
    val boundaryToken = contentType.substringAfter("boundary=", missingDelimiterValue = "")
        .substringBefore(';')
        .trim()
        .trim('"')
    if (boundaryToken.isBlank()) return emptyList()
    val delimiter = "\r\n--$boundaryToken".encodeToByteArray()
    val first = "--$boundaryToken".encodeToByteArray()
    val parts = mutableListOf<ByteArray>()
    var cursor = indexOfBytes(body, first, 0)
    if (cursor < 0) return emptyList()
    cursor += first.size
    while (cursor < body.size) {
        val next = indexOfBytes(body, delimiter, cursor)
        val end = if (next < 0) body.size else next
        parts += body.copyOfRange(cursor, end)
        if (next < 0) break
        cursor = next + delimiter.size
    }
    val headerSep = "\r\n\r\n".encodeToByteArray()
    return parts.mapNotNull { part ->
        val trimmed = part.dropWhile { it == '\r'.code.toByte() || it == '\n'.code.toByte() }.toByteArray()
        if (trimmed.size >= 2 && trimmed[0] == '-'.code.toByte() && trimmed[1] == '-'.code.toByte()) {
            return@mapNotNull null
        }
        val sep = indexOfBytes(trimmed, headerSep, 0)
        if (sep < 0) return@mapNotNull null
        val headers = trimmed.copyOfRange(0, sep).decodeToString()
        if (!headers.contains("filename=", ignoreCase = true)) return@mapNotNull null
        var payload = trimmed.copyOfRange(sep + headerSep.size, trimmed.size)
        if (payload.size >= 2 && payload[payload.size - 2] == '\r'.code.toByte() && payload[payload.size - 1] == '\n'.code.toByte()) {
            payload = payload.copyOfRange(0, payload.size - 2)
        }
        val disposition = headers.lineSequence()
            .firstOrNull { it.startsWith("Content-Disposition", ignoreCase = true) }
            .orEmpty()
        val fieldName = Regex("""name="([^"]+)"""").find(disposition)?.groupValues?.getOrNull(1) ?: "files"
        val fileName = Regex("""filename="([^"]+)"""").find(disposition)?.groupValues?.getOrNull(1) ?: "file"
        val mime = headers.lineSequence()
            .firstOrNull { it.startsWith("Content-Type", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?: "application/octet-stream"
        PortableMultipartFile(fieldName = fieldName, fileName = fileName, mimeType = mime, bytes = payload)
    }
}

private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, start: Int): Int {
    if (needle.isEmpty() || haystack.size - start < needle.size) return -1
    val last = haystack.size - needle.size
    var index = start
    while (index <= last) {
        var match = true
        var offset = 0
        while (offset < needle.size) {
            if (haystack[index + offset] != needle[offset]) {
                match = false
                break
            }
            offset++
        }
        if (match) return index
        index++
    }
    return -1
}
