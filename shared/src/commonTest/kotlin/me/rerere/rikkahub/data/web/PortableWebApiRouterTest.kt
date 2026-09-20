package me.rerere.rikkahub.data.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableWebApiRouterTest {
    private val source = PortableWebApiSource(
        webUiBundled = false,
        authRequired = true,
        conversationsListJson = """[{"id":"1","title":"Hello","assistantId":"a"}]""",
        bootstrapJson = """{"assistantId":"a","assistants":[],"conversations":[]}""",
        settingsJson = """{"themeId":"seafoam_mint","assistantId":"a"}""",
        conversationJson = { id -> if (id == "abc") """{"id":"abc","messages":[]}""" else null },
        staticAsset = { null },
        fileContent = { null },
        filePath = { null },
        onSendMessage = { _, _ -> PortableWebApiResponse.json(200, """{"ok":true}""") },
        onStop = { PortableWebApiResponse.json(200, """{"ok":true}""") },
    )

    @Test
    fun healthIsPublicAndConversationsRequireThePassword() {
        val health = handle("GET", "/api/health")
        assertEquals(200, health.statusCode)
        assertTrue(health.body.contains("\"ok\":true"))

        val denied = handle("GET", "/api/conversations")
        assertEquals(401, denied.statusCode)

        val allowed = handle("GET", "/api/conversations", authorization = "Bearer secret")
        assertEquals(200, allowed.statusCode)
        assertTrue(allowed.body.contains("\"id\":\"1\""))
    }

    @Test
    fun conversationLookupAndQueryTokenWork() {
        assertTrue(PortableWebApiRouter.authorized("pw", null, "pw"))
        assertFalse(PortableWebApiRouter.authorized("pw", "Bearer other", null))
        val found = handle(
            method = "GET",
            path = "/api/conversations/abc",
            query = mapOf("access_token" to "pw"),
            password = "pw",
        )
        assertEquals(200, found.statusCode)
        assertEquals("""{"id":"abc","messages":[]}""", found.body)
    }

    @Test
    fun authTokenAndSettingsStreamMatchTheSpaContract() {
        val denied = handle("POST", "/api/auth/token", body = """{"password":"nope"}""")
        assertEquals(401, denied.statusCode)

        val token = handle("POST", "/api/auth/token", body = """{"password":"secret"}""")
        assertEquals(200, token.statusCode)
        assertTrue(token.body.contains("\"token\":\"secret\""))
        assertTrue(token.body.contains("expiresAt"))

        val stream = handle("GET", "/api/settings/stream", authorization = "Bearer secret")
        assertTrue(stream.sse)
        assertEquals(PortableWebApiResponse.EVENT_STREAM, stream.contentType)
        assertTrue(stream.body.startsWith("event: update"))
        assertTrue(stream.body.contains("seafoam_mint"))
    }

    @Test
    fun sendStopAndFallbackHtmlAreRouted() {
        val sent = handle(
            method = "POST",
            path = "/api/conversations/abc/messages",
            authorization = "Bearer secret",
            body = """{"parts":[{"type":"text","text":"Hi"}]}""",
        )
        assertEquals(200, sent.statusCode)
        assertEquals("Hi", PortableWebApiRouter.extractMessageText("""{"parts":[{"type":"text","text":"Hi"}]}"""))

        val stopped = handle("POST", "/api/conversations/abc/stop", authorization = "Bearer secret")
        assertEquals(200, stopped.statusCode)

        val home = handle("GET", "/")
        assertEquals(PortableWebApiResponse.HTML, home.contentType)
        assertTrue(home.body.contains("__LASTCHAT_WEB_BOOT__"))
        assertTrue(home.body.contains("LastChat"))
    }

    @Test
    fun mediaUrlsBecomeAuthedFileRoutes() {
        val encoded = toWebMediaUrl("file:///tmp/voice.wav")
        assertTrue(encoded.startsWith("/api/files/content?uri="))
        assertTrue(encoded.contains("file"))
    }

    private fun handle(
        method: String,
        path: String,
        authorization: String? = null,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
        password: String? = "secret",
    ) = PortableWebApiRouter.handle(
        method = method,
        path = path,
        authorization = authorization,
        query = query,
        body = body,
        password = password,
        source = source,
    )
}
