package me.rerere.rikkahub.ui.components.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTimelineParsingTest {
    @Test
    fun buildTimelineEntries_createsOcrEntryFromAnnotations() {
        val entries = buildTimelineEntries(
            parts = emptyList(),
            annotations = listOf(
                UIMessageAnnotation.OcrActivity(
                    source = UIMessageAnnotation.OcrActivity.Source.PDF,
                    fileName = "scan.pdf",
                    pageNumbers = listOf(2, 4)
                )
            ),
        )

        val entry = entries.single() as TimelineEntry.Ocr
        assertEquals(UIMessageAnnotation.OcrActivity.Source.PDF, entry.source)
        assertEquals("scan.pdf", entry.fileName)
        assertEquals(listOf(2, 4), entry.pageNumbers)
        assertFalse(entry.isInProgress)
    }

    @Test
    fun deriveActivityState_showsLiveOcrBeforeFirstAssistantToken() {
        val state = deriveActivityState(
            parts = emptyList(),
            annotations = listOf(
                UIMessageAnnotation.OcrActivity(
                    source = UIMessageAnnotation.OcrActivity.Source.IMAGE,
                    fileName = "diagram.png",
                )
            ),
            loading = true
        )

        assertEquals(ActivityState.Ocr, state)
    }

    @Test
    fun deriveActivityState_completedTurnIncludesOcrAsActivityCategory() {
        val state = deriveActivityState(
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "search-1",
                    toolName = "search_web",
                    arguments = """{"query":"ocr timeline"}"""
                )
            ),
            annotations = listOf(
                UIMessageAnnotation.OcrActivity(
                    source = UIMessageAnnotation.OcrActivity.Source.PDF,
                    fileName = "scan.pdf",
                    pageNumbers = listOf(1, 3)
                )
            ),
            loading = false
        )

        val completed = state as ActivityState.CompletedMultiple
        assertTrue(completed.activityTypes.contains(ActivityType.OCR))
        assertTrue(completed.activityTypes.contains(ActivityType.SEARCH))
    }

    @Test
    fun deriveActivityState_categorizesMemorySearchToolSeparately() {
        val liveState = deriveActivityState(
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "memory-search-1",
                    toolName = "search_memory",
                    arguments = """{"query":"Lisbon"}"""
                )
            ),
            loading = true
        )

        assertEquals(ActivityType.MEMORY_RECALL, (liveState as ActivityState.ToolUse).let {
            categorizeToolName(it.toolName)
        })

        val completedState = deriveActivityState(
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "memory-search-1",
                    toolName = "search_memory",
                    arguments = """{"query":"Lisbon"}"""
                ),
                UIMessagePart.ToolResult(
                    toolCallId = "memory-search-1",
                    toolName = "search_memory",
                    arguments = buildJsonObject { put("query", "Lisbon") },
                    content = buildJsonObject { put("summary", "I found one possible memory.") }
                )
            ),
            loading = false
        )

        assertEquals(ActivityType.MEMORY_RECALL, (completedState as ActivityState.CompletedSingle).type)
    }

    @Test
    fun buildTimelineEntries_keepsMemoryActionsWithInvalidJson() {
        val entries = buildTimelineEntries(
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "memory-1",
                    toolName = "create_memory",
                    arguments = "{not valid json"
                )
            )
        )

        val entry = entries.single() as TimelineEntry.MemoryAction
        assertEquals(MemoryOperation.CREATE, entry.operation)
        assertNull(entry.memoryId)
        assertNull(entry.content)
        assertTrue(entry.isLoading)
    }

    @Test
    fun buildTimelineEntries_bindsToolResultsByToolCallId() {
        val pythonResult = buildJsonObject {
            put("result", "1")
        }
        val pythonArguments = buildJsonObject {
            put("code", "print(1)")
        }

        val entries = buildTimelineEntries(
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "search-1",
                    toolName = "search_web",
                    arguments = """{"query":"kotlin"}"""
                ),
                UIMessagePart.ToolCall(
                    toolCallId = "python-1",
                    toolName = "eval_python",
                    arguments = """{"code":"print(1)"}"""
                ),
                UIMessagePart.ToolResult(
                    toolCallId = "python-1",
                    toolName = "eval_python",
                    content = pythonResult,
                    arguments = pythonArguments
                )
            )
        )

        val searchEntry = entries[0] as TimelineEntry.ToolCall
        val pythonEntry = entries[1] as TimelineEntry.ToolCall

        assertTrue(searchEntry.isLoading)
        assertNull(searchEntry.resultJson)

        assertFalse(pythonEntry.isLoading)
        assertEquals(pythonResult, pythonEntry.resultJson)
        assertEquals(pythonArguments, pythonEntry.argumentsJson)
    }

    @Test
    fun buildInitialTimelineFocus_completedTurnKeepsEntriesCollapsed() {
        val entries = listOf(
            TimelineEntry.Reasoning(
                id = "reasoning-1",
                content = "Reasoning",
                durationMs = 1_200
            ),
            TimelineEntry.ToolCall(
                id = "search-1",
                toolName = "search_web",
                displayName = "Searching web",
                argumentsText = """{"query":"kotlin"}""",
                resultText = null,
                argumentsJson = buildJsonObject { put("query", "kotlin") },
                resultJson = null
            ),
            TimelineEntry.ToolCall(
                id = "python-1",
                toolName = "eval_python",
                displayName = "Running Python",
                argumentsText = """{"code":"print(1)"}""",
                resultText = "1",
                argumentsJson = buildJsonObject { put("code", "print(1)") },
                resultJson = buildJsonObject { put("result", "1") }
            )
        )

        val focus = buildInitialTimelineFocus(
            entries = entries,
            openRequest = TimelineOpenRequest(
                focusType = ActivityType.PYTHON,
                openMode = TimelineOpenMode.Collapsed
            )
        )

        assertTrue(focus.expandedEntryIds.isEmpty())
        assertEquals(2, focus.scrollIndex)
    }

    @Test
    fun buildInitialTimelineFocus_prefersLiveReasoning() {
        val entries = listOf(
            TimelineEntry.ToolCall(
                id = "search-1",
                toolName = "search_web",
                displayName = "Searching web",
                argumentsText = """{"query":"kotlin"}""",
                resultText = null,
                argumentsJson = buildJsonObject { put("query", "kotlin") },
                resultJson = null,
                isLoading = true
            ),
            TimelineEntry.Reasoning(
                id = "reasoning-1",
                content = "Thinking",
                durationMs = 0,
                isInProgress = true
            )
        )

        val focus = buildInitialTimelineFocus(
            entries = entries,
            openRequest = TimelineOpenRequest(openMode = TimelineOpenMode.FocusCurrent)
        )

        assertEquals(setOf("reasoning-1"), focus.expandedEntryIds)
        assertEquals(1, focus.scrollIndex)
    }

    @Test
    fun buildInitialTimelineFocus_usesLiveToolWhenNoReasoning() {
        val entries = listOf(
            TimelineEntry.MemoryAction(
                id = "memory-1",
                toolName = "edit_memory",
                operation = MemoryOperation.EDIT,
                memoryId = 7,
                content = "After",
                previousContent = "Before",
                memoryType = 0,
                timestamp = 1L,
                isLoading = true
            )
        )

        val focus = buildInitialTimelineFocus(
            entries = entries,
            openRequest = TimelineOpenRequest(openMode = TimelineOpenMode.FocusCurrent)
        )

        assertEquals(setOf("memory-1"), focus.expandedEntryIds)
        assertEquals(0, focus.scrollIndex)
    }

    @Test
    fun getSkillChangeSummary_readsStructuredActivatedAndDisabledLists() {
        val entry = TimelineEntry.ToolCall(
            id = "skills-1",
            toolName = "manage_skills",
            displayName = "Manage skills",
            argumentsText = """{"operation":"set"}""",
            resultText = null,
            argumentsJson = buildJsonObject {
                put("operation", "set")
            },
            resultJson = buildJsonObject {
                put("activated", buildJsonArray {
                    add(buildJsonObject { put("name", "Search") })
                    add(buildJsonObject { put("id", "search-fallback") })
                })
                put("disabled", buildJsonArray {
                    add(JsonPrimitive("Code"))
                })
            }
        )

        val summary = getSkillChangeSummary(entry)

        assertEquals(listOf("Search", "search-fallback"), summary.activated)
        assertEquals(listOf("Code"), summary.disabled)
    }

    @Test
    fun parseAskUserTimelineState_readsQuestionnaireAnswers() {
        val arguments = buildJsonObject {
            put("questions", buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", "scope")
                        put("question", "Which scope should I use?")
                        put("options", buildJsonArray {
                            add(JsonPrimitive("Short"))
                            add(buildJsonObject {
                                put("label", "Detailed")
                                put("description", "Cover every section.")
                            })
                        })
                    }
                )
            })
        }
        val result = buildJsonObject {
            put("answers", buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", "scope")
                        put("status", "answered")
                        put("source", "option")
                        put("value", "Detailed")
                    }
                )
            })
            put("dismissed", false)
        }

        val state = parseAskUserTimelineState(
            TimelineEntry.ToolCall(
                id = "ask-user-1",
                toolName = "ask_user",
                displayName = "Asking a question",
                argumentsText = arguments.toString(),
                resultText = result.toString(),
                argumentsJson = arguments,
                resultJson = result
            )
        )

        assertNotNull(state)
        assertEquals(1, state?.questionnaire?.questions?.size)
        assertEquals("scope", state?.questionnaire?.questions?.single()?.id)
        assertEquals("Detailed", state?.payload?.answers?.single()?.value)
        assertEquals("option", state?.payload?.answers?.single()?.source)
    }

}
