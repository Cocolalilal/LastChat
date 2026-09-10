package me.rerere.rikkahub.service

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookAtScreenToolTest {

    @Test
    fun createLookAtScreenTool_hasCorrectMetadataAndAutoApproval() {
        val model = Model(inputModalities = listOf(Modality.TEXT, Modality.IMAGE))
        val tool = createLookAtScreenTool(model)

        assertEquals("look_at_screen", tool.name)
        assertEquals(ToolApprovalMode.Auto, tool.approvalMode)
        assertTrue(tool.description.contains("proactively", ignoreCase = true))
        assertTrue(tool.description.contains("ambiguous references", ignoreCase = true))
        assertTrue(tool.description.contains("this", ignoreCase = true))
    }

    @Test
    fun createLookAtScreenTool_systemPromptGuidesProactiveAndCharacterBehavior() {
        val model = Model(inputModalities = listOf(Modality.TEXT, Modality.IMAGE))
        val tool = createLookAtScreenTool(model)

        val initialMessages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("What does this mean?")),
            )
        )

        val prompt = tool.systemPrompt(model, initialMessages)

        assertTrue("Should include tool header", prompt.contains("## Tool: look_at_screen"))
        assertTrue("Should emphasize ambiguous/missing context", prompt.contains("Ambiguous or Missing Context"))
        assertTrue("Should guide character persona handling", prompt.contains("Character Persona Integration"))
        assertTrue("Should guide not breaking character or asking user", prompt.contains("Do NOT break character"))
        assertTrue(
            "Should indicate screenshot has not been inspected yet",
            prompt.contains("The summon-time screenshot has not been inspected yet"),
        )
        assertFalse(
            "Should not mention running locally or on-device",
            prompt.contains("on-device", ignoreCase = true) || prompt.contains("locally", ignoreCase = true),
        )
    }

    @Test
    fun createLookAtScreenTool_systemPromptReflectsPriorScreenRead() {
        val model = Model(inputModalities = listOf(Modality.TEXT, Modality.IMAGE))
        val tool = createLookAtScreenTool(model)

        val followUpMessages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("What is this?")),
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.ToolCall(
                        toolCallId = "call_1",
                        toolName = "look_at_screen",
                        arguments = "{}",
                    )
                ),
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult(
                        toolCallId = "call_1",
                        toolName = "look_at_screen",
                        content = kotlinx.serialization.json.buildJsonObject {
                            put("note", kotlinx.serialization.json.JsonPrimitive("Screenshot attached"))
                        },
                        arguments = kotlinx.serialization.json.buildJsonObject { },
                    )
                ),
            ),
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("How do I fix it?")),
            ),
        )

        val prompt = tool.systemPrompt(model, followUpMessages)

        assertTrue(
            "Should reflect that screenshot was already inspected",
            prompt.contains("A screenshot has already been inspected in earlier turns"),
        )
    }

    @Test
    fun createLookAtScreenTool_returnsNoteWhenNoScreenshotAvailable() = runBlocking {
        val model = Model(inputModalities = listOf(Modality.TEXT, Modality.IMAGE))
        val tool = createLookAtScreenTool(model)

        // Clear screen holder to ensure no screenshot is available
        me.rerere.rikkahub.service.assist.AssistScreenHolder.clear()

        val result = tool.execute(kotlinx.serialization.json.buildJsonObject { })
        val note = result.jsonObject["note"]?.jsonPrimitive?.content
        assertEquals("No screenshot is available.", note)
    }
}
