package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.decodeFromString
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConnectionPresetsTest {
    @Test
    fun curatedConnectionsHaveUniqueIdsAndSecureEndpoints() {
        assertEquals(
            POPULAR_MCP_CONNECTIONS.size,
            POPULAR_MCP_CONNECTIONS.map { it.id }.distinct().size,
        )
        assertTrue(POPULAR_MCP_CONNECTIONS.all { it.url.startsWith("https://") })
        assertTrue(POPULAR_MCP_CONNECTIONS.all { it.name.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun oauthPresetStartsDisabledUntilSignInCompletes() {
        val notion = POPULAR_MCP_CONNECTIONS.first { it.id == "notion" }
        val config = notion.createConfig()

        assertEquals(McpAuthMode.OAUTH, config.commonOptions.authMode)
        assertEquals("notion", config.commonOptions.presetId)
        assertFalse(config.commonOptions.enable)
    }

    @Test
    fun legacyConfigDefaultsToCustomHeaders() {
        val config = JsonInstant.decodeFromString<McpServerConfig>(
            """
            {
              "type": "streamable_http",
              "id": "00000000-0000-0000-0000-000000000001",
              "commonOptions": {
                "enable": true,
                "name": "Legacy",
                "headers": [],
                "tools": []
              },
              "url": "https://example.com/mcp"
            }
            """.trimIndent()
        )

        assertEquals(McpAuthMode.CUSTOM_HEADERS, config.commonOptions.authMode)
    }
}
