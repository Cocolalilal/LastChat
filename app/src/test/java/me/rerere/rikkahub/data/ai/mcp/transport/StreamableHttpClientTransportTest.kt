package me.rerere.rikkahub.data.ai.mcp.transport

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamableHttpClientTransportTest {
    @Test
    fun initializeResponseExposesNegotiatedProtocolVersion() {
        val message = McpJson.decodeFromString<JSONRPCMessage>(
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {
                "protocolVersion": "2025-03-26",
                "capabilities": {},
                "serverInfo": {
                  "name": "Test MCP",
                  "version": "1.0"
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("2025-03-26", negotiatedProtocolVersion(message))
    }
}
