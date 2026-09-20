package me.rerere.rikkahub.data.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import kotlin.uuid.Uuid

@Serializable
enum class PortableMcpTransport {
    @SerialName("sse")
    SSE,

    @SerialName("streamable_http")
    STREAMABLE_HTTP,
}

@Serializable
data class PortableMcpTool(
    val name: String,
    val description: String? = null,
    val enable: Boolean = true,
    val inputSchema: InputSchema? = null,
)

@Serializable
data class PortableMcpServer(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val url: String = "",
    val enable: Boolean = true,
    val transport: PortableMcpTransport = PortableMcpTransport.STREAMABLE_HTTP,
    val headers: List<Pair<String, String>> = emptyList(),
    val tools: List<PortableMcpTool> = emptyList(),
)

class PortableMcpClient(
    private val httpClient: PlatformHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    suspend fun listTools(server: PortableMcpServer): List<PortableMcpTool> {
        val session = openSession(server)
        return try {
            val result = session.request("tools/list", buildJsonObject {})
            parseToolList(result)
        } finally {
            session.close()
        }
    }

    suspend fun callTool(
        server: PortableMcpServer,
        toolName: String,
        arguments: JsonObject,
    ): JsonElement {
        val session = openSession(server)
        return try {
            session.request(
                "tools/call",
                buildJsonObject {
                    put("name", toolName)
                    put("arguments", arguments)
                },
            )
        } finally {
            session.close()
        }
    }

    private suspend fun openSession(server: PortableMcpServer): McpSession {
        require(server.url.isNotBlank()) { "MCP server URL is required" }
        val session = McpSession(server)
        session.request(
            "initialize",
            buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                put("capabilities", buildJsonObject {})
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", "LastChat")
                        put("version", "1.4.6")
                    },
                )
            },
        )
        session.notify("notifications/initialized", buildJsonObject {})
        return session
    }

    private inner class McpSession(
        private val server: PortableMcpServer,
    ) {
        private var nextId = 1
        private var sessionId: String? = null

        suspend fun request(method: String, params: JsonObject): JsonElement {
            val id = nextId++
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", params)
            }
            val (status, headers, body) = post(payload, notification = false)
            headers["mcp-session-id"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { sessionId = it }
            if (status !in 200..299) {
                error("MCP $method failed: HTTP $status ${body.take(300)}")
            }
            val root = decodeObject(body)
            root["error"]?.let { errorNode ->
                val message = (errorNode as? JsonObject)?.string("message") ?: errorNode.toString()
                error("MCP $method error: $message")
            }
            return root["result"] ?: JsonObject(emptyMap())
        }

        suspend fun notify(method: String, params: JsonObject) {
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                put("params", params)
            }
            val (status, headers, _) = post(payload, notification = true)
            headers["mcp-session-id"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { sessionId = it }
            if (status !in 200..299 && status != 202) {
                PlatformLog.w(TAG, "MCP notify $method failed: HTTP $status")
            }
        }

        suspend fun close() {
            val id = sessionId ?: return
            runCatching {
                httpClient.execute(
                    PlatformHttpRequest(
                        method = "DELETE",
                        url = server.url,
                        headers = requestHeaders() + mapOf("Mcp-Session-Id" to id),
                    ),
                )
            }
        }

        private suspend fun post(
            payload: JsonObject,
            notification: Boolean,
        ): Triple<Int, Map<String, List<String>>, String> {
            val accept = if (notification) "application/json" else "application/json, text/event-stream"
            val response = httpClient.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = server.url,
                    headers = requestHeaders() + mapOf("Accept" to accept),
                    body = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(),
                    mediaType = "application/json",
                ),
            )
            val contentType = response.headers.entries
                .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                ?.value
                ?.firstOrNull()
                .orEmpty()
            val body = if (contentType.contains("text/event-stream")) {
                decodeSseData(response.body.decodeToString())
            } else {
                response.body.decodeToString()
            }
            return Triple(response.statusCode, response.headers, body)
        }

        private fun requestHeaders(): Map<String, String> = buildMap {
            server.headers.forEach { (name, value) ->
                if (name.isNotBlank()) put(name, value)
            }
            sessionId?.let { put("Mcp-Session-Id", it) }
        }
    }

    private fun parseToolList(result: JsonElement): List<PortableMcpTool> {
        val tools = (result as? JsonObject)?.get("tools") as? JsonArray ?: return emptyList()
        return tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PortableMcpTool(
                name = name,
                description = obj.string("description"),
                inputSchema = obj["inputSchema"]?.let(::inputSchemaFromJson),
            )
        }
    }

    private fun inputSchemaFromJson(element: JsonElement): InputSchema? {
        val obj = element as? JsonObject ?: return null
        val properties = obj["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val required = (obj["required"] as? JsonArray)?.mapNotNull { item ->
            (item as? JsonPrimitive)?.contentOrNull
        }
        return InputSchema.Obj(properties = properties, required = required)
    }

    private fun decodeObject(body: String): JsonObject {
        val element = json.parseToJsonElement(body.trim().ifBlank { "{}" })
        return element as? JsonObject ?: error("MCP returned a non-object JSON payload")
    }

    private fun decodeSseData(body: String): String {
        val dataLines = body.lineSequence()
            .map { it.trimEnd() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trimStart() }
            .filter { it.isNotBlank() && it != "[DONE]" }
            .toList()
        return dataLines.lastOrNull() ?: body
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        private const val TAG = "PortableMcp"
        const val PROTOCOL_VERSION = "2024-11-05"
    }
}

fun PortableMcpServer.withDiscoveredTools(tools: List<PortableMcpTool>): PortableMcpServer {
    val previous = this.tools.associateBy { it.name }
    return copy(
        tools = tools.map { discovered ->
            val existing = previous[discovered.name]
            discovered.copy(enable = existing?.enable ?: true)
        },
    )
}
