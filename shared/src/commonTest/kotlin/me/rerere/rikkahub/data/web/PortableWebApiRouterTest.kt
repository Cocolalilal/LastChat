package me.rerere.rikkahub.data.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableWebApiRouterTest {
    @Test
    fun healthIsPublicAndConversationsRequireThePassword() {
        val health = PortableWebApiRouter.handle(
            method = "GET",
            path = "/api/health",
            authorization = null,
            queryToken = null,
            password = "secret",
            conversationsJson = "[]",
            conversationJson = { null },
        )
        assertEquals(200, health.statusCode)
        assertTrue(health.body.contains("\"ok\":true"))

        val denied = PortableWebApiRouter.handle(
            method = "GET",
            path = "/api/conversations",
            authorization = null,
            queryToken = null,
            password = "secret",
            conversationsJson = "[]",
            conversationJson = { null },
        )
        assertEquals(401, denied.statusCode)

        val allowed = PortableWebApiRouter.handle(
            method = "GET",
            path = "/api/conversations",
            authorization = "Bearer secret",
            queryToken = null,
            password = "secret",
            conversationsJson = """[{"id":"1"}]""",
            conversationJson = { null },
        )
        assertEquals(200, allowed.statusCode)
        assertEquals("""[{"id":"1"}]""", allowed.body)
    }

    @Test
    fun conversationLookupAndQueryTokenWork() {
        assertTrue(PortableWebApiRouter.authorized("pw", null, "pw"))
        assertFalse(PortableWebApiRouter.authorized("pw", "Bearer other", null))
        val found = PortableWebApiRouter.handle(
            method = "GET",
            path = "/api/conversations/abc",
            authorization = null,
            queryToken = "pw",
            password = "pw",
            conversationsJson = "[]",
            conversationJson = { id -> if (id == "abc") """{"id":"abc"}""" else null },
        )
        assertEquals(200, found.statusCode)
        assertEquals("""{"id":"abc"}""", found.body)
    }
}
