package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.decodeFromString
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(17, POPULAR_MCP_CONNECTIONS.size)
        assertTrue(
            POPULAR_MCP_CONNECTIONS.all {
                it.authMode == McpAuthMode.OAUTH || it.authMode == McpAuthMode.NONE
            }
        )
        assertEquals(13, POPULAR_MCP_CONNECTIONS.count { it.authMode == McpAuthMode.OAUTH })
        assertTrue(POPULAR_MCP_CONNECTIONS.none { it.id in setOf("stripe", "paypal") })
        assertTrue(POPULAR_MCP_CONNECTIONS.all { it.iconUri.startsWith("icons/") && it.iconUri.endsWith(".svg") })
        assertTrue(
            setOf("figma", "supabase", "hugging-face").all { addedId ->
                POPULAR_MCP_CONNECTIONS.any { it.id == addedId && it.authMode == McpAuthMode.OAUTH }
            }
        )
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

    @Test
    fun findMcpConnectionPresetMatchesPresetOrReturnsNull() {
        val notion = POPULAR_MCP_CONNECTIONS.first { it.id == "notion" }
        val config = notion.createConfig()

        // Match by server config
        assertEquals("notion", findMcpConnectionPreset(config)?.id)
        assertEquals("icons/notion.svg", findMcpConnectionPreset(config)?.iconUri)

        // Match by preset ID
        assertEquals("linear", findMcpConnectionPreset(presetId = "linear")?.id)

        // Match by URL
        assertEquals("figma", findMcpConnectionPreset(presetId = null, url = "https://mcp.figma.com/mcp")?.id)
        assertEquals("figma", findMcpConnectionPreset(presetId = null, url = "https://mcp.figma.com/mcp/")?.id)

        // Match by Name
        assertEquals("Canva", findMcpConnectionPreset(presetId = null, url = null, name = "canva")?.name)

        // Custom unknown server returns null
        val customConfig = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(name = "My Custom MCP"),
            url = "https://custom.example.com/mcp",
        )
        assertNull(findMcpConnectionPreset(customConfig))
        assertEquals("https://custom.example.com/mcp", customConfig.endpointUrl)
    }
}
