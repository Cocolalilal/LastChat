package me.rerere.ai.provider.providers

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.TextGenerationParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GoogleProviderToolsPayloadTest {
    @Test
    fun `buildGoogleToolsPayload preserves custom tools alongside builtin tools`() {
        val params = TextGenerationParams(
            model = Model(
                modelId = "gemini-3.1-pro-preview",
                abilities = listOf(ModelAbility.TOOL)
            ),
            tools = listOf(
                Tool(
                    name = "search_web",
                    description = "search the web",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("query", buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                })
                            },
                            required = listOf("query")
                        )
                    },
                    execute = { buildJsonObject {} }
                )
            ),
            builtInTools = setOf(BuiltInTools.Search, BuiltInTools.UrlContext)
        )

        val payload = buildGoogleToolsPayload(params)
        assertNotNull(payload)

        val tools = payload!!
        assertEquals(3, tools.size)
        assertEquals(
            "search_web",
            tools[0].jsonObject["functionDeclarations"]!!
                .jsonArray[0]
                .jsonObject["name"]!!
                .jsonPrimitive
                .content
        )
        assertEquals(true, tools[1].jsonObject.containsKey("google_search"))
        assertEquals(true, tools[2].jsonObject.containsKey("url_context"))
    }
}
