package me.rerere.rikkahub.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpOAuthDiscoveryTest {
    @Test
    fun recognizesNotionForItsAppCallbackCompatibilityPath() {
        assertTrue(isNotionMcpResource("https://mcp.notion.com/mcp"))
        assertTrue(isNotionMcpResource("https://MCP.NOTION.COM/mcp"))
        assertFalse(isNotionMcpResource("https://mcp.linear.app/mcp"))
    }

    @Test
    fun readsQuotedResourceMetadataFromChallenge() {
        val result = parseMcpResourceMetadataHeader(
            mapOf(
                "WWW-Authenticate" to listOf(
                    "Bearer realm=\"OAuth\", resource_metadata=\"https://mcp.example.com/.well-known/oauth-protected-resource/mcp\", error=\"invalid_token\""
                )
            )
        )

        assertEquals(
            "https://mcp.example.com/.well-known/oauth-protected-resource/mcp",
            result,
        )
    }

    @Test
    fun readsUnquotedResourceMetadataAndHeaderNameCaseInsensitively() {
        val result = parseMcpResourceMetadataHeader(
            mapOf(
                "Www-Authenticate" to listOf(
                    "Bearer resource_metadata=https://mcp.example.com/.well-known/oauth-protected-resource"
                )
            )
        )

        assertEquals(
            "https://mcp.example.com/.well-known/oauth-protected-resource",
            result,
        )
    }

    @Test
    fun rejectsInsecureResourceMetadataLocation() {
        val result = parseMcpResourceMetadataHeader(
            mapOf("WWW-Authenticate" to listOf("Bearer resource_metadata=\"http://example.com/metadata\""))
        )

        assertNull(result)
    }
}
