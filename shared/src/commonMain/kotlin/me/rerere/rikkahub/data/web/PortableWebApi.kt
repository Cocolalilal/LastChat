package me.rerere.rikkahub.data.web

data class PortableWebApiResponse(
    val statusCode: Int,
    val contentType: String = "application/json; charset=utf-8",
    val body: String,
)

/**
 * Password-gated JSON API used by the iOS local web server. Android continues to host
 * the full Ktor/React SPA; this router is the portable subset that iOS can serve
 * without embedding the web-ui assets.
 */
object PortableWebApiRouter {
    fun handle(
        method: String,
        path: String,
        authorization: String?,
        queryToken: String?,
        password: String?,
        conversationsJson: String,
        conversationJson: (String) -> String?,
    ): PortableWebApiResponse {
        val normalized = path.substringBefore('?').trimEnd('/').ifBlank { "/" }
        if (method.equals("GET", ignoreCase = true) && (normalized == "/" || normalized == "/api/health")) {
            return PortableWebApiResponse(
                statusCode = 200,
                body = """{"ok":true,"app":"LastChat","webUiBundled":false}""",
            )
        }
        if (!authorized(password, authorization, queryToken)) {
            return PortableWebApiResponse(statusCode = 401, body = """{"error":"unauthorized"}""")
        }
        if (method.equals("GET", ignoreCase = true) && normalized == "/api/conversations") {
            return PortableWebApiResponse(statusCode = 200, body = conversationsJson)
        }
        val conversationMatch = CONVERSATION_PATH.matchEntire(normalized)
        if (method.equals("GET", ignoreCase = true) && conversationMatch != null) {
            val json = conversationJson(conversationMatch.groupValues[1])
                ?: return PortableWebApiResponse(statusCode = 404, body = """{"error":"not_found"}""")
            return PortableWebApiResponse(statusCode = 200, body = json)
        }
        return PortableWebApiResponse(statusCode = 404, body = """{"error":"not_found"}""")
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

    private val CONVERSATION_PATH = Regex("^/api/conversations/([^/]+)$")
}
