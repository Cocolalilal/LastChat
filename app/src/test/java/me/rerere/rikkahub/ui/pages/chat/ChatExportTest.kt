package me.rerere.rikkahub.ui.pages.chat

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatExportTest {
    @Test
    fun markdownExport_defaultsExcludeReasoningToolsAndTroubleshooting() {
        val messages = exportMessages()

        val text = buildMarkdownExportText(testConversation(), messages)

        assertTrue(text.contains("Visible answer"))
        assertFalse(text.contains("private chain"))
        assertFalse(text.contains("Search Web"))
        assertFalse(text.contains("Troubleshooting"))
        assertFalse(text.contains("statusCode"))
    }

    @Test
    fun markdownExport_togglesReasoningToolsAndTroubleshootingIndependently() {
        val messages = exportMessages()

        val reasoningOnly = buildMarkdownExportText(
            testConversation(),
            messages,
            ChatExportOptions(includeReasoning = true)
        )
        assertTrue(reasoningOnly.contains("private chain"))
        assertFalse(reasoningOnly.contains("statusCode"))

        val toolsOnly = buildMarkdownExportText(
            testConversation(),
            messages,
            ChatExportOptions(includeToolCalls = true)
        )
        assertTrue(toolsOnly.contains("Activity"))
        assertTrue(toolsOnly.contains("search_web") || toolsOnly.contains("Searching web"))
        assertFalse(toolsOnly.contains("Troubleshooting"))

        val troubleshootingOnly = buildMarkdownExportText(
            testConversation(),
            messages,
            ChatExportOptions(includeTroubleshooting = true)
        )
        assertTrue(troubleshootingOnly.contains("Troubleshooting"))
        assertTrue(troubleshootingOnly.contains("statusCode"))
        assertFalse(troubleshootingOnly.contains("private chain"))
    }

    @Test
    fun buildExportTurns_pairsToolResultWithAssistantTurn() {
        val turns = buildExportTurns(exportMessages())

        assertEquals(2, turns.size)
        assertEquals(MessageRole.USER, turns[0].role)
        assertEquals(MessageRole.ASSISTANT, turns[1].role)
        assertTrue(turns[1].parts.any { it is UIMessagePart.ToolCall })
        assertTrue(turns[1].parts.any { it is UIMessagePart.ToolResult })
    }

    @Test
    fun troubleshootingDiagnostics_detectNestedErrorStatusFields() {
        val turn = buildExportTurns(exportMessages()).last()

        val diagnostics = collectTroubleshootingDiagnostics(turn)

        assertTrue(diagnostics.any { it.label.endsWith(".statusCode") && it.value.contains("429") })
        assertTrue(diagnostics.any { it.label.endsWith(".error") && it.value.contains("rate_limited") })
        assertTrue(diagnostics.any { it.isError })
    }

    @Test
    fun troubleshootingRedactionRemovesSecretLookingValues() {
        val raw = """{"Authorization":"Bearer sk-proj-secretvalue1234567890","api_key":"sk-test-secret"}"""

        val redacted = redactTroubleshootingText(raw)
        val redactedValue = redactTroubleshootingValue("api_key", "sk-test-secret")

        assertFalse(redacted.contains("sk-proj-secretvalue"))
        assertFalse(redacted.contains("sk-test-secret"))
        assertEquals("[redacted]", redactedValue)
    }

    private fun exportMessages(): List<UIMessage> {
        val call = UIMessagePart.ToolCall(
            toolCallId = "call-search",
            toolName = "search_web",
            arguments = """{"query":"compose export"}""",
            approvalState = ToolApprovalState.Approved
        )
        val result = UIMessagePart.ToolResult(
            toolCallId = "call-search",
            toolName = "search_web",
            arguments = buildJsonObject {
                put("query", "compose export")
            },
            content = buildJsonObject {
                put("statusCode", 429)
                put("error", "rate_limited")
                put("message", "Too many requests")
                put("token", "sk-proj-secretvalue12345678901234567890")
            }
        )
        return listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("Question"))
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Reasoning("private chain"),
                    call,
                    UIMessagePart.Text("Visible answer"),
                    metadataPart()
                ),
                usage = me.rerere.ai.core.TokenUsage(promptTokens = 2, completionTokens = 3, totalTokens = 5),
                generationDurationMs = 1200,
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(result)
            )
        )
    }

    private fun metadataPart(): UIMessagePart.Text {
        return UIMessagePart.Text(
            text = "",
            metadata = buildJsonObject {
                put("code", JsonPrimitive("diagnostic-code"))
            }
        )
    }

    private fun testConversation(): Conversation {
        return Conversation(
            assistantId = Uuid.random(),
            title = "Export Test",
            messageNodes = emptyList()
        )
    }
}
