package me.rerere.ai.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.provider.providers.openai.ResponseAPI
import me.rerere.ai.ui.ToolResultImage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.coalesceConsecutiveToolMessages
import me.rerere.ai.ui.stripEphemeralToolImagePayloads
import me.rerere.ai.util.KeyRoulette
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformJwtSigner
import me.rerere.common.platform.PlatformMediaEncoder
import me.rerere.common.platform.PlatformServerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMultimodalToolSerializationTest {

    private val httpClient = object : PlatformHttpClient {
        override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
            error("Network is not used by these unit tests")
        }

        override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
    }

    private val jwtSigner = object : PlatformJwtSigner {
        override fun signRs256(data: ByteArray, pkcs8PrivateKeyPem: String): ByteArray = ByteArray(0)
    }

    private val mediaEncoder = object : PlatformMediaEncoder {
        override fun encodeImage(url: String, withPrefix: Boolean): Result<String> {
            val prefix = if (withPrefix && !url.startsWith("data:")) "data:image/png;base64," else ""
            val payload = if (url.startsWith("data:") && !withPrefix) url.substringAfter(",") else url
            return Result.success(prefix + payload)
        }

        override fun encodeVideo(url: String, withPrefix: Boolean): Result<String> = Result.success(url)

        override fun encodeAudio(url: String, withPrefix: Boolean): Result<String> = Result.success(url)
    }

    private val samplePngDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

    private val sampleToolResultImage = ToolResultImage(
        url = samplePngDataUrl,
        mimeType = "image/png",
        title = "Sample Result",
        sourceUrl = "https://example.com/sample.png",
        markdownImage = "![Sample Result](https://example.com/sample.png)",
        originTool = "search_web",
    )

    @Test
    fun claudeProvider_combinesParallelToolResultsIntoSingleUserMessage_andNestsImages() {
        val provider = ClaudeProvider(httpClient, mediaEncoder)
        val toolMessage = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_text_only",
                    toolName = "search_web",
                    content = buildJsonObject { put("query", JsonPrimitive("kotlin")) },
                    arguments = buildJsonObject { },
                ),
                UIMessagePart.ToolResult(
                    toolCallId = "call_with_image",
                    toolName = "search_web",
                    content = buildJsonObject { put("query", JsonPrimitive("compose")) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        val request = method.invoke(
            provider,
            listOf(toolMessage),
            TextGenerationParams(model = Model(modelId = "claude-3-7-sonnet-20250219")),
            false,
        ) as JsonObject

        val messages = request["messages"]?.jsonArray ?: error("messages missing")
        // Parallel tool results MUST be grouped into a single user message
        assertEquals(1, messages.size)

        val userMsg = messages[0].jsonObject
        assertEquals("user", userMsg["role"]?.jsonPrimitive?.content)

        val content = userMsg["content"]?.jsonArray ?: error("content missing")
        assertEquals(2, content.size)

        // First tool_result: plain string content
        val part1 = content[0].jsonObject
        assertEquals("tool_result", part1["type"]?.jsonPrimitive?.content)
        assertEquals("call_text_only", part1["tool_use_id"]?.jsonPrimitive?.content)
        assertTrue(part1["content"]?.jsonPrimitive?.content?.contains("kotlin") == true)

        // Second tool_result: structured array with text provenance + image block
        val part2 = content[1].jsonObject
        assertEquals("tool_result", part2["type"]?.jsonPrimitive?.content)
        assertEquals("call_with_image", part2["tool_use_id"]?.jsonPrimitive?.content)

        val nestedParts = part2["content"]?.jsonArray ?: error("nested tool_result content must be array")
        assertEquals(2, nestedParts.size)

        val textBlock = nestedParts[0].jsonObject
        assertEquals("text", textBlock["type"]?.jsonPrimitive?.content)
        val text = textBlock["text"]?.jsonPrimitive?.content.orEmpty()
        assertTrue("Text must contain automated provenance", text.contains("AUTOMATED TOOL VISUAL OUTPUT"))
        assertTrue("Text must mention source tool", text.contains("search_web"))
        assertTrue("Text must include markdown syntax", text.contains("![Sample Result]"))

        val imageBlock = nestedParts[1].jsonObject
        assertEquals("image", imageBlock["type"]?.jsonPrimitive?.content)
        val source = imageBlock["source"]?.jsonObject ?: error("source missing")
        assertEquals("base64", source["type"]?.jsonPrimitive?.content)
        assertEquals("image/png", source["media_type"]?.jsonPrimitive?.content)
    }

    @Test
    fun googleProvider_gemini3_nestsPartsInsideFunctionResponse() {
        val provider = GoogleProvider(httpClient, mediaEncoder, jwtSigner)
        val toolMessage = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_g3",
                    toolName = "search_web",
                    content = buildJsonObject { put("status", JsonPrimitive("ok")) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = GoogleProvider::class.java.getDeclaredMethod(
            "buildContents",
            List::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        // isGemini3 = true
        val contents = method.invoke(provider, listOf(toolMessage), true) as JsonArray
        assertEquals(1, contents.size)

        val messageObj = contents[0].jsonObject
        val parts = messageObj["parts"]?.jsonArray ?: error("parts missing")
        assertEquals(1, parts.size)

        val fnResponse = parts[0].jsonObject["functionResponse"]?.jsonObject
            ?: error("functionResponse missing")
        assertEquals("search_web", fnResponse["name"]?.jsonPrimitive?.content)

        val responseObj = fnResponse["response"]?.jsonObject ?: error("response object missing")
        assertNotNull(responseObj["inspected_image_provenance"])

        val nestedParts = fnResponse["parts"]?.jsonArray ?: error("Gemini 3 parts array missing in functionResponse")
        assertEquals(1, nestedParts.size)
        val inlineData = nestedParts[0].jsonObject["inlineData"]?.jsonObject ?: error("inlineData missing")
        assertEquals("image/png", inlineData["mimeType"]?.jsonPrimitive?.content)
        assertEquals("Sample Result", inlineData["displayName"]?.jsonPrimitive?.content)
    }

    @Test
    fun googleProvider_gemini25_emitsSiblingInlineDataParts() {
        val provider = GoogleProvider(httpClient, mediaEncoder, jwtSigner)
        val toolMessage = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_g25",
                    toolName = "workspace_view_image",
                    content = buildJsonObject { put("status", JsonPrimitive("ok")) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = GoogleProvider::class.java.getDeclaredMethod(
            "buildContents",
            List::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        // isGemini3 = false
        val contents = method.invoke(provider, listOf(toolMessage), false) as JsonArray
        assertEquals(1, contents.size)

        val messageObj = contents[0].jsonObject
        val parts = messageObj["parts"]?.jsonArray ?: error("parts missing")
        // functionResponse + sibling text + sibling inline_data
        assertEquals(3, parts.size)

        assertNotNull(parts[0].jsonObject["functionResponse"])
        assertTrue(parts[1].jsonObject["text"]?.jsonPrimitive?.content?.contains("AUTOMATED TOOL") == true)
        val inlineData = parts[2].jsonObject["inline_data"]?.jsonObject ?: error("inline_data missing")
        assertEquals("image/png", inlineData["mime_type"]?.jsonPrimitive?.content)
    }

    @Test
    fun chatCompletionsAPI_emitsWireOnlyMultimodalUserMessageAfterTool() {
        val keyRoulette = KeyRoulette.default()
        val api = ChatCompletionsAPI(httpClient, keyRoulette, mediaEncoder)
        val toolMessage = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_openai",
                    toolName = "search_web",
                    content = buildJsonObject { put("found", JsonPrimitive(true)) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        val request = method.invoke(
            api,
            listOf(toolMessage),
            TextGenerationParams(model = Model(modelId = "gpt-4o", abilities = listOf(ModelAbility.TOOL))),
            ProviderSetting.OpenAI(apiKey = "key", baseUrl = "https://api.openai.com/v1"),
            false,
        ) as JsonObject

        val messages = request["messages"]?.jsonArray ?: error("messages missing")
        // role: "tool" followed by wire-only multimodal role: "user"
        assertEquals(2, messages.size)

        val toolMsg = messages[0].jsonObject
        assertEquals("tool", toolMsg["role"]?.jsonPrimitive?.content)
        assertEquals("call_openai", toolMsg["tool_call_id"]?.jsonPrimitive?.content)

        val userMsg = messages[1].jsonObject
        assertEquals("user", userMsg["role"]?.jsonPrimitive?.content)

        val userParts = userMsg["content"]?.jsonArray ?: error("user parts missing")
        assertEquals(2, userParts.size)
        assertEquals("text", userParts[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertTrue(userParts[0].jsonObject["text"]?.jsonPrimitive?.content?.contains("AUTOMATED TOOL") == true)

        val imagePart = userParts[1].jsonObject
        assertEquals("image_url", imagePart["type"]?.jsonPrimitive?.content)
        val imgObj = imagePart["image_url"]?.jsonObject ?: error("image_url missing")
        assertEquals(samplePngDataUrl, imgObj["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun responseAPI_emitsWireOnlyMultimodalUserInputAfterFunctionCallOutput() {
        val keyRoulette = KeyRoulette.default()
        val api = ResponseAPI(httpClient, mediaEncoder, keyRoulette)
        val toolMessage = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_resp",
                    toolName = "search_web",
                    content = buildJsonObject { put("found", JsonPrimitive(true)) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = ResponseAPI::class.java.getDeclaredMethod(
            "buildRequestBody",
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
            ProviderSetting.OpenAI::class.java,
        ).apply { isAccessible = true }

        val request = method.invoke(
            api,
            listOf(toolMessage),
            TextGenerationParams(model = Model(modelId = "gpt-4o")),
            false,
            ProviderSetting.OpenAI(apiKey = "key", baseUrl = "https://api.openai.com/v1"),
        ) as JsonObject

        val input = request["input"]?.jsonArray ?: error("input array missing")
        // function_call_output followed by wire-only role: "user"
        assertEquals(2, input.size)

        val outputItem = input[0].jsonObject
        assertEquals("function_call_output", outputItem["type"]?.jsonPrimitive?.content)
        assertEquals("call_resp", outputItem["call_id"]?.jsonPrimitive?.content)

        val userItem = input[1].jsonObject
        assertEquals("user", userItem["role"]?.jsonPrimitive?.content)
        val contentParts = userItem["content"]?.jsonArray ?: error("content missing")
        assertEquals(2, contentParts.size)
        assertEquals("input_text", contentParts[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertTrue(contentParts[0].jsonObject["text"]?.jsonPrimitive?.content?.contains("AUTOMATED TOOL") == true)
        assertEquals("input_image", contentParts[1].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals(samplePngDataUrl, contentParts[1].jsonObject["image_url"]?.jsonPrimitive?.content)
    }

    @Test
    fun claudeProvider_coalescesConsecutiveToolMessagesIntoSingleUserTurn() {
        val provider = ClaudeProvider(httpClient, mediaEncoder)
        val toolMsg1 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_1",
                    toolName = "search_web",
                    content = buildJsonObject { put("item", JsonPrimitive(1)) },
                    arguments = buildJsonObject { },
                )
            )
        )
        val toolMsg2 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_2",
                    toolName = "search_web",
                    content = buildJsonObject { put("item", JsonPrimitive(2)) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        val request = method.invoke(
            provider,
            listOf(toolMsg1, toolMsg2),
            TextGenerationParams(model = Model(modelId = "claude-3-7-sonnet-20250219")),
            false,
        ) as JsonObject

        val messages = request["messages"]?.jsonArray ?: error("messages missing")
        assertEquals("Consecutive TOOL messages must be coalesced into exactly 1 user message in Claude", 1, messages.size)

        val userMsg = messages[0].jsonObject
        assertEquals("user", userMsg["role"]?.jsonPrimitive?.content)
        val content = userMsg["content"]?.jsonArray ?: error("content missing")
        assertEquals("Content must contain both tool_result blocks", 2, content.size)
        assertEquals("call_1", content[0].jsonObject["tool_use_id"]?.jsonPrimitive?.content)
        assertEquals("call_2", content[1].jsonObject["tool_use_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun googleProvider_coalescesConsecutiveToolMessages_andIncludesId() {
        val provider = GoogleProvider(httpClient, mediaEncoder, jwtSigner)
        val toolMsg1 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "g_call_1",
                    toolName = "search_web",
                    content = buildJsonObject { put("res", JsonPrimitive("a")) },
                    arguments = buildJsonObject { },
                )
            )
        )
        val toolMsg2 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "g_call_2",
                    toolName = "workspace_view_image",
                    content = buildJsonObject { put("res", JsonPrimitive("b")) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = GoogleProvider::class.java.getDeclaredMethod(
            "buildContents",
            List::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        // Test Gemini 3
        val contentsG3 = method.invoke(provider, listOf(toolMsg1, toolMsg2), true) as JsonArray
        assertEquals("Gemini 3 contents must have size 1", 1, contentsG3.size)
        val partsG3 = contentsG3[0].jsonObject["parts"]?.jsonArray ?: error("parts missing")
        assertEquals(2, partsG3.size)
        val fn1 = partsG3[0].jsonObject["functionResponse"]?.jsonObject ?: error("fn1 missing")
        assertEquals("g_call_1", fn1["id"]?.jsonPrimitive?.content)
        assertEquals("search_web", fn1["name"]?.jsonPrimitive?.content)
        val fn2 = partsG3[1].jsonObject["functionResponse"]?.jsonObject ?: error("fn2 missing")
        assertEquals("g_call_2", fn2["id"]?.jsonPrimitive?.content)
        assertEquals("workspace_view_image", fn2["name"]?.jsonPrimitive?.content)

        // Test pre-Gemini 3 (Gemini 2.5)
        val contentsG25 = method.invoke(provider, listOf(toolMsg1, toolMsg2), false) as JsonArray
        assertEquals("Gemini 2.5 contents must have size 1", 1, contentsG25.size)
        val partsG25 = contentsG25[0].jsonObject["parts"]?.jsonArray ?: error("parts missing")
        // fn1 + fn2 + sibling text + sibling inline_data = 4
        assertEquals(4, partsG25.size)
        val fn1G25 = partsG25[0].jsonObject["functionResponse"]?.jsonObject ?: error("fn1G25 missing")
        assertEquals("g_call_1", fn1G25["id"]?.jsonPrimitive?.content)
        val fn2G25 = partsG25[1].jsonObject["functionResponse"]?.jsonObject ?: error("fn2G25 missing")
        assertEquals("g_call_2", fn2G25["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun chatCompletionsAPI_coalescesConsecutiveToolMessages_andAvoidsDoubleQuotingStringContent() {
        val keyRoulette = KeyRoulette.default()
        val api = ChatCompletionsAPI(httpClient, keyRoulette, mediaEncoder)
        val toolMsg1 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_string",
                    toolName = "search_web",
                    content = JsonPrimitive("plain text result\nline 2"),
                    arguments = buildJsonObject { },
                )
            )
        )
        val toolMsg2 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_img",
                    toolName = "search_web",
                    content = buildJsonObject { put("found", JsonPrimitive(true)) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        val request = method.invoke(
            api,
            listOf(toolMsg1, toolMsg2),
            TextGenerationParams(model = Model(modelId = "gpt-4o", abilities = listOf(ModelAbility.TOOL))),
            ProviderSetting.OpenAI(apiKey = "key", baseUrl = "https://api.openai.com/v1"),
            false,
        ) as JsonObject

        val messages = request["messages"]?.jsonArray ?: error("messages missing")
        // tool1, tool2, followed by single wire-only multimodal user message = 3
        assertEquals(3, messages.size)

        val tool1 = messages[0].jsonObject
        assertEquals("tool", tool1["role"]?.jsonPrimitive?.content)
        assertEquals("call_string", tool1["tool_call_id"]?.jsonPrimitive?.content)
        // Plain string content must NOT be double-quoted with escaped newlines
        assertEquals("plain text result\nline 2", tool1["content"]?.jsonPrimitive?.content)

        val tool2 = messages[1].jsonObject
        assertEquals("tool", tool2["role"]?.jsonPrimitive?.content)
        assertEquals("call_img", tool2["tool_call_id"]?.jsonPrimitive?.content)

        val userMsg = messages[2].jsonObject
        assertEquals("user", userMsg["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun responseAPI_coalescesConsecutiveToolMessages() {
        val keyRoulette = KeyRoulette.default()
        val api = ResponseAPI(httpClient, mediaEncoder, keyRoulette)
        val toolMsg1 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_r1",
                    toolName = "search_web",
                    content = JsonPrimitive("raw text output"),
                    arguments = buildJsonObject { },
                )
            )
        )
        val toolMsg2 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = "call_r2",
                    toolName = "search_web",
                    content = buildJsonObject { put("status", JsonPrimitive("ok")) },
                    arguments = buildJsonObject { },
                    inspectedImages = listOf(sampleToolResultImage),
                )
            )
        )

        val method = ResponseAPI::class.java.getDeclaredMethod(
            "buildRequestBody",
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
            ProviderSetting.OpenAI::class.java,
        ).apply { isAccessible = true }

        val request = method.invoke(
            api,
            listOf(toolMsg1, toolMsg2),
            TextGenerationParams(model = Model(modelId = "gpt-4o")),
            false,
            ProviderSetting.OpenAI(apiKey = "key", baseUrl = "https://api.openai.com/v1"),
        ) as JsonObject

        val input = request["input"]?.jsonArray ?: error("input array missing")
        // function_call_output 1, function_call_output 2, followed by single user message = 3
        assertEquals(3, input.size)
        assertEquals("call_r1", input[0].jsonObject["call_id"]?.jsonPrimitive?.content)
        assertEquals("raw text output", input[0].jsonObject["output"]?.jsonPrimitive?.content)
        assertEquals("call_r2", input[1].jsonObject["call_id"]?.jsonPrimitive?.content)
        assertEquals("user", input[2].jsonObject["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun coalesceConsecutiveToolMessages_mergesConsecutiveToolMessages() {
        val userMsg = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("hi")))
        val tool1 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult("c1", "tool1", buildJsonObject { }, buildJsonObject { })
            )
        )
        val tool2 = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult("c2", "tool2", buildJsonObject { }, buildJsonObject { })
            )
        )
        val asstMsg = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("done")))

        val merged = listOf(userMsg, tool1, tool2, asstMsg).coalesceConsecutiveToolMessages()
        assertEquals(3, merged.size)
        assertEquals(MessageRole.USER, merged[0].role)
        assertEquals(MessageRole.TOOL, merged[1].role)
        assertEquals(2, merged[1].parts.size)
        assertEquals("c1", (merged[1].parts[0] as UIMessagePart.ToolResult).toolCallId)
        assertEquals("c2", (merged[1].parts[1] as UIMessagePart.ToolResult).toolCallId)
        assertEquals(MessageRole.ASSISTANT, merged[2].role)
    }

    @Test
    fun stripEphemeralToolImagePayloads_clearsBase64Url() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult(
                        toolCallId = "c1",
                        toolName = "search_web",
                        content = buildJsonObject { },
                        arguments = buildJsonObject { },
                        inspectedImages = listOf(sampleToolResultImage),
                    )
                )
            )
        )

        val stripped = messages.stripEphemeralToolImagePayloads()
        val result = stripped[0].parts[0] as UIMessagePart.ToolResult
        val img = result.inspectedImages[0]
        assertEquals("", img.url)
        assertEquals("Sample Result", img.title)
        assertEquals("https://example.com/sample.png", img.sourceUrl)
        assertEquals("![Sample Result](https://example.com/sample.png)", img.markdownImage)
        assertEquals("image/png", img.mimeType)
        assertEquals("search_web", img.originTool)
    }
}
