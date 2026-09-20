package me.rerere.rikkahub.data.web

data class PortableWebApiResponse(
    val statusCode: Int,
    val contentType: String = JSON,
    val body: String = "",
    val bytes: ByteArray? = null,
    val sse: Boolean = false,
) {
    companion object {
        const val JSON = "application/json; charset=utf-8"
        const val HTML = "text/html; charset=utf-8"
        const val EVENT_STREAM = "text/event-stream"
        const val OCTET = "application/octet-stream"

        fun json(status: Int, body: String) = PortableWebApiResponse(statusCode = status, body = body)

        fun sse(event: String, data: String) = PortableWebApiResponse(
            statusCode = 200,
            contentType = EVENT_STREAM,
            body = "event: $event\ndata: ${data.replace("\n", "\ndata: ")}\n\n",
            sse = true,
        )

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
    ): PortableWebApiResponse {
        val queryToken = query["access_token"]
        val normalized = path.substringBefore('?').trimEnd('/').ifBlank { "/" }
        val get = method.equals("GET", ignoreCase = true)
        val post = method.equals("POST", ignoreCase = true)

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
        if (get && normalized == "/api/settings/stream") {
            return PortableWebApiResponse.sse("update", source.settingsJson)
        }
        if (get && normalized == "/api/conversations") {
            return PortableWebApiResponse.json(200, source.conversationsListJson)
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

        val streamMatch = CONVERSATION_STREAM.matchEntire(normalized)
        if (get && streamMatch != null) {
            val json = source.conversationJson(streamMatch.groupValues[1])
                ?: return PortableWebApiResponse.json(404, """{"error":"not_found","code":404}""")
            val payload = """{"type":"snapshot","seq":1,"conversation":$json,"serverTime":${nowEpochMs()}}"""
            return PortableWebApiResponse.sse("snapshot", payload)
        }
        val stopMatch = CONVERSATION_STOP.matchEntire(normalized)
        if (post && stopMatch != null) {
            return source.onStop(stopMatch.groupValues[1])
        }
        val sendMatch = CONVERSATION_MESSAGES.matchEntire(normalized)
        if (post && sendMatch != null) {
            return source.onSendMessage(sendMatch.groupValues[1], body.orEmpty())
        }
        val conversationMatch = CONVERSATION_PATH.matchEntire(normalized)
        if (get && conversationMatch != null) {
            val json = source.conversationJson(conversationMatch.groupValues[1])
                ?: return PortableWebApiResponse.json(404, """{"error":"not_found","code":404}""")
            return PortableWebApiResponse.json(200, json)
        }
        return PortableWebApiResponse.json(404, """{"error":"not_found","code":404}""")
    }

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
    private val FILE_PATH = Regex("^/api/files/path/(.+)$")
    private val PASSWORD_FIELD = Regex(""""password"\s*:\s*"((?:\\.|[^"\\])*)"""")
    private val TEXT_PART = Regex(""""type"\s*:\s*"text"\s*,\s*"text"\s*:\s*"((?:\\.|[^"\\])*)"""")
    private val OBJECT_ITEM = Regex("""\{[^{}]*\}""")
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

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
