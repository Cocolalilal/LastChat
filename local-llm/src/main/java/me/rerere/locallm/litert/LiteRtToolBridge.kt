package me.rerere.locallm.litert

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool

/**
 * Bridges LastChat [Tool]s into LiteRT-LM tool declarations.
 *
 * Execution stays in LastChat's own tool loop (`automaticToolCalling = false`): we only hand the model
 * the tool *schema* so it knows what it can call; the model's tool calls come back on
 * `Message.toolCalls` and are executed by [me.rerere.rikkahub.data.ai.GenerationHandler] exactly like
 * network providers. Hence [OpenApiTool.execute] here is never invoked.
 */
internal object LiteRtToolBridge {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun toToolProviders(tools: List<Tool>): List<ToolProvider> =
        tools.map { tool(LastChatOpenApiTool(it)) }

    /** Builds the `{name, description, parameters:{type:object, properties, required}}` schema. */
    fun describe(tool: Tool): String {
        val schema = tool.parameters()
        val parameters: JsonObject = when (schema) {
            is InputSchema.Obj -> buildJsonObject {
                put("type", "object")
                put("properties", schema.properties)
                schema.required?.let { req ->
                    put("required", JsonArray(req.map { JsonPrimitive(it) }))
                }
            }
            null -> buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(emptyMap()))
            }
        }
        val root = buildJsonObject {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", parameters)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private class LastChatOpenApiTool(private val tool: Tool) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = describe(tool)

        // Never called: LastChat executes tools itself (automaticToolCalling = false).
        override fun execute(argumentsJson: String): String = "{}"
    }
}
