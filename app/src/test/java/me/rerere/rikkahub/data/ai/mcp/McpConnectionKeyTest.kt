package me.rerere.rikkahub.data.ai.mcp

import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import me.rerere.common.http.normalizeHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConnectionKeyTest {
    private val base = McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(name = "demo"),
        url = "https://example.com/mcp",
    )

    @Test
    fun toolMetadataDoesNotAffectConnectionKey() {
        val withTools = base.copy(
            commonOptions = base.commonOptions.copy(
                tools = listOf(McpTool(name = "search", enable = false))
            )
        )

        assertEquals(base.connectionKey(false), withTools.connectionKey(false))
    }

    @Test
    fun connectionParametersAffectConnectionKey() {
        assertNotEquals(
            base.connectionKey(false),
            base.copy(url = "https://example.com/other").connectionKey(false),
        )
        assertNotEquals(
            base.connectionKey(false),
            McpServerConfig.SseTransportServer(
                id = base.id,
                commonOptions = base.commonOptions,
                url = base.url,
            ).connectionKey(false),
        )
        assertNotEquals(
            base.connectionKey(false),
            base.copy(
                commonOptions = base.commonOptions.copy(headers = listOf("X-API-Key" to "secret"))
            ).connectionKey(false),
        )
    }

    @Test
    fun oauthCredentialsTriggerReconnectUnlessManualAuthorizationWins() {
        assertNotEquals(base.connectionKey(false), base.connectionKey(true))

        val manualAuth = base.copy(
            commonOptions = base.commonOptions.copy(
                headers = listOf("Authorization" to "Bearer manual")
            )
        )
        assertEquals(manualAuth.connectionKey(false), manualAuth.connectionKey(true))
    }

    @Test
    fun invalidOAuthTokenErrorsAreRecognizedThroughWrappedCauses() {
        assertTrue(StreamableHttpError(401, """{"error":"invalid_token"}""").isInvalidOAuthToken())
        assertTrue(
            IllegalStateException(
                "MCP failed",
                IllegalArgumentException("Bearer error=invalid_token"),
            ).isInvalidOAuthToken()
        )
        assertFalse(StreamableHttpError(500, "server error").isInvalidOAuthToken())
    }

    @Test
    fun blankAndSchemeLessMcpUrlsAreRejectedBeforeConnect() {
        assertEquals(null, "".normalizeHttpUrl())
        assertEquals(null, "   ".normalizeHttpUrl())
        assertEquals("https://mcp.example.com/mcp", "mcp.example.com/mcp".normalizeHttpUrl())
        assertEquals("https://example.com/mcp", base.endpointUrl.normalizeHttpUrl())
    }
}
