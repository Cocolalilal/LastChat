package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AskUserToolSupportTest {
    @Test
    fun parseAskUserQuestionnaire_normalizesLegacyOptionsAndAppliesLimits() {
        val questionnaire = parseAskUserQuestionnaire(
            """
            {
              "questions": [
                {
                  "id": "scope",
                  "question": "Which scope should I use?",
                  "options": [
                    "Short",
                    { "label": "Detailed", "description": "Cover every section." },
                    { "label": " " },
                    "Balanced",
                    "Ignored"
                  ]
                },
                { "id": "tone", "question": "What tone should I use?" },
                { "id": "format", "question": "Which format?" },
                { "id": "deadline", "question": "What deadline matters?" },
                { "id": "sources", "question": "How many sources?" },
                { "id": "extra", "question": "This should be trimmed" }
              ]
            }
            """.trimIndent()
        )

        assertNotNull(questionnaire)
        questionnaire ?: return

        assertEquals(5, questionnaire.questions.size)
        assertEquals("scope", questionnaire.questions.first().id)
        assertEquals(3, questionnaire.questions.first().options.size)
        assertEquals("Short", questionnaire.questions.first().options[0].label)
        assertEquals("Detailed", questionnaire.questions.first().options[1].label)
        assertEquals("Cover every section.", questionnaire.questions.first().options[1].description)
        assertEquals("Balanced", questionnaire.questions.first().options[2].label)
        assertEquals("sources", questionnaire.questions.last().id)
    }

    @Test
    fun normalizeAskUserAnswerPayload_acceptsLegacyMapAnswersAndFillsSkippedEntries() {
        val questionnaire = AskUserQuestionnaire(
            questions = listOf(
                AskUserQuestion(id = "scope", question = "Which scope?"),
                AskUserQuestion(id = "tone", question = "Which tone?"),
                AskUserQuestion(id = "deadline", question = "Which deadline?"),
            )
        )

        val payload = normalizeAskUserAnswerPayload(
            questionnaire = questionnaire,
            rawAnswer = """{"answers":{"scope":"Use the short version"},"dismissed":true}""",
        )

        assertEquals(true, payload.dismissed)
        assertEquals(3, payload.answers.size)
        assertEquals(
            AskUserAnswer(
                id = "scope",
                status = "answered",
                source = "custom",
                value = "Use the short version",
            ),
            payload.answers[0]
        )
        assertEquals(AskUserAnswer(id = "tone", status = "skipped"), payload.answers[1])
        assertEquals(AskUserAnswer(id = "deadline", status = "skipped"), payload.answers[2])
    }

    @Test
    fun normalizeAskUserAnswerPayload_preservesStructuredAnswersAndDefaultsInvalidEntriesToSkipped() {
        val questionnaire = AskUserQuestionnaire(
            questions = listOf(
                AskUserQuestion(id = "scope", question = "Which scope?"),
                AskUserQuestion(id = "tone", question = "Which tone?"),
                AskUserQuestion(id = "deadline", question = "Which deadline?"),
            )
        )

        val payload = normalizeAskUserAnswerPayload(
            questionnaire = questionnaire,
            rawAnswer = """
                {
                  "answers": [
                    { "id": "scope", "status": "answered", "source": "option", "value": "Detailed" },
                    { "id": "tone", "status": "skipped" },
                    { "id": "deadline", "status": "answered", "value": "" }
                  ],
                  "dismissed": false
                }
            """.trimIndent(),
        )

        assertEquals(false, payload.dismissed)
        assertEquals(
            AskUserAnswer(
                id = "scope",
                status = "answered",
                source = "option",
                value = "Detailed",
            ),
            payload.answers[0]
        )
        assertEquals(AskUserAnswer(id = "tone", status = "skipped"), payload.answers[1])
        assertEquals(AskUserAnswer(id = "deadline", status = "skipped"), payload.answers[2])
    }

    @Test
    fun findPendingAskUserToolCall_returnsEarliestPendingQuestionnaire() {
        val firstPending = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "call-1",
                    toolName = ASK_USER_TOOL_NAME,
                    arguments = """{"questions":[{"id":"scope","question":"Which scope?"}]}""",
                    approvalState = ToolApprovalState.Pending,
                )
            )
        )
        val answered = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "call-2",
                    toolName = ASK_USER_TOOL_NAME,
                    arguments = """{"questions":[{"id":"tone","question":"Which tone?"}]}""",
                    approvalState = ToolApprovalState.Answered("""{"answers":[],"dismissed":false}"""),
                )
            )
        )
        val secondPending = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "call-3",
                    toolName = ASK_USER_TOOL_NAME,
                    arguments = """{"questions":[{"id":"deadline","question":"Which deadline?"}]}""",
                    approvalState = ToolApprovalState.Pending,
                )
            )
        )

        val pending = listOf(answered, firstPending, secondPending).findPendingAskUserToolCall()

        assertNotNull(pending)
        assertEquals("call-1", pending?.toolCallId)
        assertEquals("scope", pending?.questionnaire?.questions?.single()?.id)
    }

    @Test
    fun parseAskUserQuestionnaire_recoversWrappedAndFencedPayloads() {
        val wrapped = parseAskUserQuestionnaire(
            """
            {
              "name": "ask_user",
              "arguments": {
                "questions": [
                  { "id": "scope", "question": "Which scope?" }
                ]
              }
            }
            """.trimIndent()
        )
        val fenced = parseAskUserQuestionnaire(
            """
            assistant to=ask_user
            ```json
            {"questions":[{"id":"tone","question":"Which tone?"}]}
            ```
            """.trimIndent()
        )

        assertEquals("scope", wrapped?.questions?.singleOrNull()?.id)
        assertEquals("tone", fenced?.questions?.singleOrNull()?.id)
    }

    @Test
    fun parseJsonElementWithRecovery_recoversFirstObjectFromConcatenatedToolArguments() {
        val parsed = parseJsonElementWithRecovery(
            """
            {"query":"Google AI Studio context caching free tier"}
            {"query":"DeepSeek API free credits prompt caching"}
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(
            "Google AI Studio context caching free tier",
            parsed?.jsonObject?.get("query")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun recoverInlineAskUserToolCall_replacesPrintedPayloadWithToolCall() {
        val message = UIMessage.assistant(
            """
            assistant to=ask_user
            ```json
            {"questions":[{"id":"deadline","question":"Which deadline matters?"}]}
            ```
            """.trimIndent()
        )

        val recovered = message.recoverInlineAskUserToolCall()

        assertNotNull(recovered)
        val toolCall = recovered?.getToolCalls()?.singleOrNull()
        assertNotNull(toolCall)
        assertEquals(ASK_USER_TOOL_NAME, toolCall?.toolName)
        assertTrue(recovered?.parts?.none { part -> part is UIMessagePart.Text } == true)
        assertEquals(
            "deadline",
            parseAskUserQuestionnaire(toolCall?.arguments.orEmpty())?.questions?.singleOrNull()?.id,
        )
    }

    @Test
    fun parseAskUserQuestionnaire_returnsNullForInvalidPayload() {
        assertNull(parseAskUserQuestionnaire("""{"questions":[{"id":"","question":""}]}"""))
        assertNull(parseAskUserQuestionnaire("""not-json"""))
    }
}
