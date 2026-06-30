package me.rerere.rikkahub.ui.components.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ActivityTimelinePanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deriveActivityState_blankToolNameWithStreamingPythonArgumentsShowsPython() {
        val state = deriveActivityState(
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "",
                    toolName = "",
                    arguments = """{"code":"print('hello')"""
                )
            ),
            loading = true
        )

        assertTrue(state is ActivityState.ToolUse)
        val toolState = state as ActivityState.ToolUse
        assertEquals(ActivityType.PYTHON, categorizeToolName(toolState.toolName))
    }

    @Test
    fun buildTimelineEntries_usesUniqueIdsForBlankOrRepeatedToolCallIds() {
        val entries = buildTimelineEntries(
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "",
                    toolName = "search_web",
                    arguments = """{"query":"first"}"""
                ),
                UIMessagePart.ToolCall(
                    toolCallId = "",
                    toolName = "search_web",
                    arguments = """{"query":"second"}"""
                ),
                UIMessagePart.ToolCall(
                    toolCallId = "repeat",
                    toolName = "eval_python",
                    arguments = """{"code":"print(1)"}"""
                ),
                UIMessagePart.ToolCall(
                    toolCallId = "repeat",
                    toolName = "eval_python",
                    arguments = """{"code":"print(2)"}"""
                )
            ),
            loading = true
        )

        assertEquals(entries.size, entries.map { it.id }.toSet().size)
    }

    @Test
    fun chatMessageTurn_completedTimelineOpensCollapsedAndRefocuses() {
        val reasoningMarker = "reasoning-expansion-marker"
        val longReasoning = "r".repeat(180) + reasoningMarker

        val assistantNode = MessageNode.of(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Reasoning(
                        reasoning = longReasoning,
                        createdAt = Clock.System.now() - 2.seconds,
                        finishedAt = Clock.System.now()
                    ),
                    UIMessagePart.ToolCall(
                        toolCallId = "search-call",
                        toolName = "search_web",
                        arguments = """{"query":"inline timeline"}"""
                    ),
                    UIMessagePart.Text("Answer text")
                )
            )
        )
        val toolNode = MessageNode.of(
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult(
                        toolCallId = "search-call",
                        toolName = "search_web",
                        arguments = buildJsonObject {
                            put("query", "inline timeline")
                        },
                        content = buildJsonObject {
                            put("answer", "Search answer")
                            put(
                                "items",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("title", "Example")
                                            put("url", "https://example.com")
                                            put("text", "Example snippet")
                                        }
                                    )
                                )
                            )
                        }
                    )
                )
            )
        )
        val group = MessageTurnGroup(
            nodes = listOf(assistantNode, toolNode),
            role = MessageRole.ASSISTANT
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    ChatMessageTurn(
                        group = group,
                        isLastTurn = false,
                        onCitationClick = {},
                        loading = false,
                        showRegenerate = false
                    )
                }
            }
        }

        composeRule.onNodeWithTag("activity_timeline_panel").assertDoesNotExist()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_pill_search").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("activity_pill_search").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_timeline_panel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("activity_timeline_panel").assertExists()
        composeRule.onNodeWithText("inline timeline").assertExists()
        composeRule.onNodeWithText("example.com").assertExists()

        composeRule.onNodeWithTag("activity_pill_reasoning").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_timeline_panel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("activity_timeline_panel").assertExists()
        composeRule.onNodeWithText(reasoningMarker, substring = true).assertExists()

        composeRule.onNodeWithTag("activity_timeline_panel").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_timeline_panel").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun chatMessageTurn_liveTimelineOpensFocusedCurrentActivity() {
        val reasoningMarker = "live-reasoning-marker"
        val assistantNode = MessageNode.of(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Reasoning(
                        reasoning = reasoningMarker,
                        createdAt = Clock.System.now() - 2.seconds,
                        finishedAt = null
                    )
                )
            )
        )
        val group = MessageTurnGroup(
            nodes = listOf(assistantNode),
            role = MessageRole.ASSISTANT
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    ChatMessageTurn(
                        group = group,
                        isLastTurn = true,
                        onCitationClick = {},
                        loading = true,
                        showRegenerate = false
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_pill_reasoning").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("activity_pill_reasoning").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_timeline_panel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(reasoningMarker, substring = true).assertExists()
    }

    @Test
    fun chatMessageTurn_liveOcrTimelineOpensFocusedCurrentActivity() {
        val assistantNode = MessageNode.of(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = emptyList(),
                annotations = listOf(
                    UIMessageAnnotation.OcrActivity(
                        source = UIMessageAnnotation.OcrActivity.Source.PDF,
                        fileName = "scan.pdf",
                        pageNumbers = listOf(2, 4)
                    )
                )
            )
        )
        val group = MessageTurnGroup(
            nodes = listOf(assistantNode),
            role = MessageRole.ASSISTANT
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    ChatMessageTurn(
                        group = group,
                        isLastTurn = true,
                        onCitationClick = {},
                        loading = true,
                        showRegenerate = false
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_pill_ocr").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("activity_pill_ocr").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_timeline_panel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("scan.pdf").assertExists()
        composeRule.onNodeWithText("2, 4", substring = true).assertExists()
    }

    @Test
    fun chatMessageTurn_completedTimelineIncludesOcrAlongsideOtherActivities() {
        val assistantNode = MessageNode.of(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Reasoning(
                        reasoning = "Reasoned about OCR results",
                        createdAt = Clock.System.now() - 2.seconds,
                        finishedAt = Clock.System.now()
                    ),
                    UIMessagePart.ToolCall(
                        toolCallId = "search-call",
                        toolName = "search_web",
                        arguments = """{"query":"ocr timeline"}"""
                    ),
                    UIMessagePart.Text("Answer text")
                ),
                annotations = listOf(
                    UIMessageAnnotation.OcrActivity(
                        source = UIMessageAnnotation.OcrActivity.Source.PDF,
                        fileName = "scan.pdf",
                        pageNumbers = listOf(1, 3)
                    )
                )
            )
        )
        val toolNode = MessageNode.of(
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult(
                        toolCallId = "search-call",
                        toolName = "search_web",
                        arguments = buildJsonObject {
                            put("query", "ocr timeline")
                        },
                        content = buildJsonObject {
                            put("answer", "Search answer")
                        }
                    )
                )
            )
        )
        val group = MessageTurnGroup(
            nodes = listOf(assistantNode, toolNode),
            role = MessageRole.ASSISTANT
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    ChatMessageTurn(
                        group = group,
                        isLastTurn = false,
                        onCitationClick = {},
                        loading = false,
                        showRegenerate = false
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_pill_ocr").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("activity_pill_ocr").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_timeline_panel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("scan.pdf").assertExists()
        composeRule.onNodeWithText("1, 3", substring = true).assertExists()
    }

    @Test
    fun activityTimelinePanel_rendersSearchAndMemoryDetailsInline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val beforeLabel = context.getString(R.string.activity_timeline_before)
        val afterLabel = context.getString(R.string.activity_timeline_after)
        val revertLabel = context.getString(R.string.activity_timeline_revert)

        composeRule.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    ActivityTimelinePanel(
                        entries = listOf(
                            TimelineEntry.ToolCall(
                                id = "search-1",
                                toolName = "search_web",
                                displayName = "Searching web",
                                argumentsText = """{"query":"inline details"}""",
                                resultText = "Search answer",
                                argumentsJson = buildJsonObject {
                                    put("query", "inline details")
                                },
                                resultJson = buildJsonObject {
                                    put("answer", "Search answer")
                                    put(
                                        "items",
                                        JsonArray(
                                            listOf(
                                                buildJsonObject {
                                                    put("title", "Example")
                                                    put("url", "https://example.com")
                                                    put("text", "Example snippet")
                                                }
                                            )
                                        )
                                    )
                                }
                            ),
                            TimelineEntry.MemoryAction(
                                id = "memory-1",
                                toolName = "edit_memory",
                                operation = MemoryOperation.EDIT,
                                memoryId = 7,
                                content = "After memory",
                                previousContent = "Before memory",
                                memoryType = 0,
                                timestamp = 123L
                            )
                        ),
                        initialOpenRequest = TimelineOpenRequest(
                            focusType = ActivityType.SEARCH,
                            openMode = TimelineOpenMode.Collapsed
                        ),
                        assistantId = "assistant-id",
                        memoryActions = TimelineMemoryActions(
                            findDeletedIds = { emptySet() },
                            updateContent = { _, _ -> },
                            deleteMemory = { },
                            restoreMemory = { },
                            revertMemory = { _, _ -> }
                        )
                    )
                }
            }
        }

        composeRule.onNodeWithTag("activity_timeline_panel").assertExists()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_timeline_panel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("inline details").assertExists()
        composeRule.onNodeWithText("example.com").assertExists()

        composeRule.onNodeWithText(beforeLabel).assertExists()
        composeRule.onNodeWithText(afterLabel).assertExists()
        composeRule.onNodeWithText(revertLabel).assertExists()
    }

    @Test
    fun activityTimelinePanel_rendersMemoryRecallPreviewInline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val memoryRecallLabel = context.getString(R.string.activity_timeline_tool_search_memory)
        val recallSummary = "I found one possible memory about the Lisbon train plan."

        composeRule.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    ActivityTimelinePanel(
                        entries = listOf(
                            TimelineEntry.ToolCall(
                                id = "memory-search-1",
                                toolName = "search_memory",
                                displayName = "Recalling memories",
                                argumentsText = """{"query":"Lisbon train"}""",
                                resultText = recallSummary,
                                argumentsJson = buildJsonObject {
                                    put("query", "Lisbon train")
                                },
                                resultJson = buildJsonObject {
                                    put("summary", recallSummary)
                                    put("source", "past_chat")
                                }
                            )
                        ),
                        initialOpenRequest = TimelineOpenRequest(
                            focusType = ActivityType.MEMORY_RECALL,
                            openMode = TimelineOpenMode.Collapsed
                        )
                    )
                }
            }
        }

        composeRule.onNodeWithTag("activity_timeline_panel").assertExists()
        composeRule.onNodeWithText(memoryRecallLabel).assertExists()
        composeRule.onNodeWithText(recallSummary).assertExists()
    }
}
