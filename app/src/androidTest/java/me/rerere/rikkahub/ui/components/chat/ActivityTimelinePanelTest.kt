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
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ActivityTimelinePanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatMessageTurn_completedTimelineOpensCollapsedAndRefocuses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reasoningMarker = "reasoning-expansion-marker"
        val longReasoning = "r".repeat(180) + reasoningMarker
        val searchSourcesLabel = context.getString(R.string.activity_timeline_sources, 1)

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
        composeRule.onNodeWithText(searchSourcesLabel).assertDoesNotExist()

        composeRule.onNodeWithTag("timeline_entry_tool_search-call").performClick()
        composeRule.onNodeWithText(searchSourcesLabel).assertExists()

        composeRule.onNodeWithTag("activity_pill_reasoning").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("activity_timeline_panel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("activity_timeline_panel").assertExists()
        composeRule.onNodeWithText(searchSourcesLabel).assertDoesNotExist()
        composeRule.onNodeWithText(reasoningMarker, substring = true).assertDoesNotExist()

        composeRule.onNodeWithTag("timeline_entry_reasoning_0").performClick()
        composeRule.onNodeWithText(reasoningMarker, substring = true).assertExists()

        composeRule.onNodeWithTag("activity_pill_reasoning").performClick()
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
    fun activityTimelinePanel_rendersSearchAndMemoryDetailsInline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val searchSourcesLabel = context.getString(R.string.activity_timeline_sources, 1)
        val memoryEditLabel = context.getString(R.string.chat_message_tool_edit_memory)
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
        composeRule.onNodeWithText(searchSourcesLabel).assertDoesNotExist()
        composeRule.onNodeWithTag("timeline_entry_search-1").performClick()
        composeRule.onNodeWithText(searchSourcesLabel).assertExists()

        composeRule.onNodeWithTag("timeline_entry_memory-1").performClick()
        composeRule.onNodeWithText(beforeLabel).assertExists()
        composeRule.onNodeWithText(afterLabel).assertExists()
        composeRule.onNodeWithText(revertLabel).assertExists()
    }
}
