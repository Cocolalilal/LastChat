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

        val paged = handle(
            method = "GET",
            path = "/api/conversations/paged",
            authorization = "Bearer secret",
            query = mapOf("offset" to "0", "limit" to "10", "query" to "Hello"),
        )
        assertEquals(200, paged.statusCode)
        assertTrue(paged.body.contains("\"id\":\"1\""))
        assertTrue(paged.body.contains("\"hasMore\":false"))

        val search = handle(
            method = "GET",
            path = "/api/conversations/search",
            authorization = "Bearer secret",
            query = mapOf("query" to "Hello"),
        )
        assertEquals(200, search.statusCode)
        assertTrue(search.body.contains("\"id\":\"1\""))
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
    fun spaMutationsAndLongLivedSseAreRouted() {
        val recording = mutableListOf<String>()
        val source = this.source.copy(
            conversationsInvalidateJson = { """{"assistantId":"a","timestamp":1}""" },
            actions = PortableWebApiActions(
                onCreateConversation = {
                    recording += "create"
                    PortableWebApiResponse.json(201, """{"id":"n","assistantId":"a"}""")
                },
                onEditMessage = { conversationId, messageId, _ ->
                    recording += "edit:$conversationId:$messageId"
                    PortableWebApiResponse.json(202, """{"status":"accepted"}""")
                },
                onForkConversation = { _, messageId ->
                    recording += "fork:$messageId"
                    PortableWebApiResponse.json(201, """{"conversationId":"forked"}""")
                },
                onRegenerate = { _, messageId ->
                    recording += "regen:$messageId"
                    PortableWebApiResponse.json(202, """{"status":"accepted"}""")
                },
                onToolApproval = { _, _ ->
                    recording += "approval"
                    PortableWebApiResponse.json(202, """{"status":"accepted"}""")
                },
                onUploadFiles = { files ->
                    recording += "upload:${files.size}"
                    PortableWebApiResponse.json(201, """{"files":[]}""")
                },
                onPinConversation = {
                    recording += "pin"
                    PortableWebApiResponse.json(200, """{"status":"updated"}""")
                },
                onDeleteConversation = {
                    recording += "delete"
                    PortableWebApiResponse.json(200, """{"status":"deleted"}""")
                },
            ),
        )
        fun routed(method: String, path: String, body: String? = null, bodyBytes: ByteArray? = null, contentType: String? = null) =
            PortableWebApiRouter.handle(
                method = method,
                path = path,
                authorization = "Bearer secret",
                query = emptyMap(),
                body = body,
                password = "secret",
                source = source,
                bodyBytes = bodyBytes,
                contentType = contentType,
            )

        val settings = routed("GET", "/api/settings/stream")
        assertTrue(settings.sse)
        assertEquals(PortableWebApiResponse.SETTINGS_SSE_HEARTBEAT_MS, settings.sseKeepAliveMs)
        assertTrue(settings.body.startsWith("event: update"))

        val conversationStream = routed("GET", "/api/conversations/abc/stream")
        assertTrue(conversationStream.sse)
        assertEquals(PortableWebApiResponse.CONVERSATION_SSE_HEARTBEAT_MS, conversationStream.sseKeepAliveMs)
        assertTrue(conversationStream.body.contains("\"type\":\"snapshot\""))

        val listStream = routed("GET", "/api/conversations/stream")
        assertTrue(listStream.sse)
        assertTrue(listStream.body.startsWith("event: invalidate"))

        assertEquals(201, routed("POST", "/api/conversations", "{}").statusCode)
        assertEquals(202, routed("POST", "/api/conversations/abc/messages/m1/edit", """{"parts":[{"type":"text","text":"Hi"}]}""").statusCode)
        assertEquals(201, routed("POST", "/api/conversations/abc/fork", """{"messageId":"m1"}""").statusCode)
        assertEquals(202, routed("POST", "/api/conversations/abc/regenerate", """{"messageId":"m1"}""").statusCode)
        assertEquals(202, routed("POST", "/api/conversations/abc/tool-approval", """{"approved":true}""").statusCode)
        assertEquals(200, routed("POST", "/api/conversations/abc/pin").statusCode)
        assertEquals(200, routed("DELETE", "/api/conversations/abc").statusCode)

        val boundary = "----testboundary"
        val multipart = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"files\"; filename=\"note.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n" +
                "hello\r\n" +
                "--$boundary--\r\n"
            ).encodeToByteArray()
        val uploaded = routed(
            method = "POST",
            path = "/api/files/upload",
            bodyBytes = multipart,
            contentType = "multipart/form-data; boundary=$boundary",
        )
        assertEquals(201, uploaded.statusCode)
        assertEquals(
            listOf("create", "edit:abc:m1", "fork:m1", "regen:m1", "approval", "pin", "delete", "upload:1"),
            recording,
        )
        assertEquals("Hi", PortableWebApiRouter.jsonString("""{"parts":[{"type":"text","text":"Hi"}]}""", "text") ?: PortableWebApiRouter.extractMessageText("""{"parts":[{"type":"text","text":"Hi"}]}"""))
    }

    @Test
    fun remainingSpaSettingsAndConversationMutationsAreRouted() {
        val recording = mutableListOf<String>()
        val source = this.source.copy(
            actions = PortableWebApiActions(
                onRenameConversation = { id, title ->
                    recording += "title:$id:$title"
                    PortableWebApiResponse.json(200, """{"status":"updated"}""")
                },
                onMoveConversation = { id, assistantId ->
                    recording += "move:$id:$assistantId"
                    PortableWebApiResponse.json(200, """{"status":"updated"}""")
                },
                onUpdateSkills = { id, skillIds ->
                    recording += "skills:$id:${skillIds.joinToString()}"
                    PortableWebApiResponse.json(200, """{"status":"updated"}""")
                },
                onDeleteMessage = { conversationId, messageId ->
                    recording += "delmsg:$conversationId:$messageId"
                    PortableWebApiResponse.json(200, """{"status":"deleted"}""")
                },
                onSelectNode = { conversationId, nodeId, selectIndex ->
                    recording += "node:$conversationId:$nodeId:$selectIndex"
                    PortableWebApiResponse.json(202, """{"status":"accepted"}""")
                },
                onSelectAssistant = { assistantId ->
                    recording += "assistant:$assistantId"
                    PortableWebApiResponse.json(200, """{"status":"ok"}""")
                },
                onSelectModel = { body ->
                    recording += "model:$body"
                    PortableWebApiResponse.json(200, """{"status":"ok"}""")
                },
                onThinkingBudget = { body ->
                    recording += "budget:$body"
                    PortableWebApiResponse.json(200, """{"status":"ok"}""")
                },
                onFavoriteModels = { body ->
                    recording += "fav:$body"
                    PortableWebApiResponse.json(200, """{"status":"ok"}""")
                },
                onDeleteFile = { id ->
                    recording += "delfile:$id"
                    PortableWebApiResponse.json(200, """{"status":"deleted"}""")
                },
                onSearchEnabled = { enabled ->
                    recording += "search:$enabled"
                    PortableWebApiResponse.json(200, """{"status":"ok"}""")
                },
            ),
        )
        fun routed(method: String, path: String, body: String? = null) =
            PortableWebApiRouter.handle(
                method = method,
                path = path,
                authorization = "Bearer secret",
                query = emptyMap(),
                body = body,
                password = "secret",
                source = source,
            )

        assertEquals(200, routed("POST", "/api/conversations/abc/title", """{"title":"Hello"}""").statusCode)
        assertEquals(200, routed("POST", "/api/conversations/abc/move", """{"assistantId":"a2"}""").statusCode)
        assertEquals(200, routed("POST", "/api/conversations/abc/skills", """{"skillIds":["s1","s2"]}""").statusCode)
        assertEquals(200, routed("DELETE", "/api/conversations/abc/messages/m1").statusCode)
        assertEquals(202, routed("POST", "/api/conversations/abc/nodes/n1/select", """{"selectIndex":2}""").statusCode)
        assertEquals(200, routed("POST", "/api/settings/assistant", """{"assistantId":"a2"}""").statusCode)
        assertEquals(200, routed("POST", "/api/settings/assistant/model", """{"modelId":"gpt"}""").statusCode)
        assertEquals(200, routed("POST", "/api/settings/assistant/thinking-budget", """{"thinkingBudget":128}""").statusCode)
        assertEquals(200, routed("POST", "/api/settings/favorite-models", """{"modelIds":["m1"]}""").statusCode)
        assertEquals(200, routed("POST", "/api/settings/search/enabled", """{"enabled":true}""").statusCode)
        assertEquals(200, routed("DELETE", "/api/files/42").statusCode)
        assertEquals(
            listOf(
                "title:abc:Hello",
                "move:abc:a2",
                "skills:abc:s1, s2",
                "delmsg:abc:m1",
                "node:abc:n1:2",
                "assistant:a2",
                "model:{\"modelId\":\"gpt\"}",
                "budget:{\"thinkingBudget\":128}",
                "fav:{\"modelIds\":[\"m1\"]}",
                "search:true",
                "delfile:42",
            ),
            recording,
        )
    }

    @Test
    fun multipartParserKeepsBinaryPayloads() {
        val boundary = "----bin"
        val payload = byteArrayOf(0, 1, 2, 255.toByte())
        val header = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"files\"; filename=\"a.bin\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            ).encodeToByteArray()
        val footer = "\r\n--$boundary--\r\n".encodeToByteArray()
        val body = header + payload + footer
        val files = parseMultipartFiles(body, "multipart/form-data; boundary=$boundary")
        assertEquals(1, files.size)
        assertEquals("a.bin", files[0].fileName)
        assertTrue(files[0].bytes.contentEquals(payload))
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
