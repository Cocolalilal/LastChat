package me.rerere.rikkahub.data.ai

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeAwarenessPromptTest {
    private val zoneId: ZoneId = ZoneId.of("Europe/Zurich")

    @Test
    fun returnsNullWhenDisabled() {
        val block = buildTimeAwarenessBlock(
            enabled = false,
            fullMessages = emptyList(),
            retainedMessages = emptyList(),
            runtimeInfo = runtimeAt(2026, 3, 6, 12, 0)
        )

        assertNull(block)
    }

    @Test
    fun includesFirstMessageHintWhenNoPreviousMessageExists() {
        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = listOf(messageAt("2026-03-06T12:00:00")),
            retainedMessages = listOf(messageAt("2026-03-06T12:00:00")),
            runtimeInfo = runtimeAt(2026, 3, 6, 12, 1)
        )

        assertNotNull(block)
        assertTrue(block!!.contains("Local timestamp: 2026-03-06 12:01 CET"))
        assertTrue(block.contains("Weekday: Friday"))
        assertTrue(block.contains("Month: March"))
        assertTrue(block.contains("Year: 2026"))
        assertTrue(block.contains("Conversation state: This appears to be the first visible message"))
    }

    @Test
    fun underThresholdGapOmitsSignificantPauseNote() {
        val messages = listOf(
            messageAt("2026-03-06T12:00:00"),
            messageAt("2026-03-06T12:12:00")
        )

        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = messages,
            retainedMessages = messages,
            runtimeInfo = runtimeAt(2026, 3, 6, 12, 12)
        )

        assertNotNull(block)
        assertFalse(block!!.contains("since the last message"))
    }

    @Test
    fun thresholdGapRoundsToTensAndAddsHumanPhrase() {
        val messages = listOf(
            messageAt("2026-03-06T11:26:00"),
            messageAt("2026-03-06T12:00:00")
        )

        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = messages,
            retainedMessages = messages,
            runtimeInfo = runtimeAt(2026, 3, 6, 12, 0)
        )

        assertNotNull(block)
        assertTrue(block!!.contains("About 30 minutes have passed since the last message."))
    }

    @Test
    fun hourScaleUsesHumanFriendlyWording() {
        val messages = listOf(
            messageAt("2026-03-06T10:45:00"),
            messageAt("2026-03-06T12:00:00")
        )

        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = messages,
            retainedMessages = messages,
            runtimeInfo = runtimeAt(2026, 3, 6, 12, 0)
        )

        assertNotNull(block)
        assertTrue(block!!.contains("About 1 hour has passed since the last message."))
    }

    @Test
    fun multiHourGapsCanIncludeHoursAndMinutes() {
        val messages = listOf(
            messageAt("2026-03-06T10:27:00"),
            messageAt("2026-03-06T12:00:00")
        )

        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = messages,
            retainedMessages = messages,
            runtimeInfo = runtimeAt(2026, 3, 6, 12, 0)
        )

        assertNotNull(block)
        assertTrue(block!!.contains("About 1 hour and 30 minutes have passed since the last message."))
    }

    @Test
    fun usesCurrentMessageTimestampInsteadOfGenerationTimeForGap() {
        val messages = listOf(
            messageAt("2026-03-06T11:20:00"),
            messageAt("2026-03-06T12:00:00")
        )

        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = messages,
            retainedMessages = messages,
            runtimeInfo = runtimeAt(2026, 3, 6, 13, 10)
        )

        assertNotNull(block)
        assertTrue(block!!.contains("About 40 minutes have passed since the last message."))
        assertFalse(block.contains("About 1 hour and 50 minutes have passed since the last message."))
    }

    @Test
    fun skipsToolOnlyMessagesWhenFindingPreviousConversationMessage() {
        val messages = listOf(
            assistantMessageAt("2026-03-06T12:00:00"),
            toolCallAt("2026-03-06T12:22:00"),
            toolResultAt("2026-03-06T12:24:00"),
            messageAt("2026-03-06T12:40:00")
        )

        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = messages,
            retainedMessages = messages,
            runtimeInfo = runtimeAt(2026, 3, 6, 12, 41)
        )

        assertNotNull(block)
        assertTrue(block!!.contains("About 40 minutes have passed since the last message."))
        assertFalse(block.contains("About 20 minutes have passed since the last message."))
    }

    @Test
    fun detectsDayMonthAndYearRollovers() {
        val messages = listOf(
            messageAt("2025-12-31T23:30:00"),
            messageAt("2026-01-01T00:30:00")
        )

        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = messages,
            retainedMessages = messages,
            runtimeInfo = runtimeAt(2026, 1, 1, 0, 30)
        )

        assertNotNull(block)
        assertTrue(block!!.contains("Local day changed since the previous message."))
        assertTrue(block.contains("Local month changed since the previous message."))
        assertTrue(block.contains("Local year changed since the previous message."))
    }

    @Test
    fun dateBasedGapUsesDaysWeeksMonthsAndYears() {
        val now = runtimeAt(2026, 3, 6, 12, 0)

        val fiveDays = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = listOf(messageAt("2026-03-01T12:00:00"), messageAt("2026-03-06T12:00:00")),
            retainedMessages = listOf(messageAt("2026-03-01T12:00:00"), messageAt("2026-03-06T12:00:00")),
            runtimeInfo = now
        )
        val twoWeeks = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = listOf(messageAt("2026-02-20T12:00:00"), messageAt("2026-03-06T12:00:00")),
            retainedMessages = listOf(messageAt("2026-02-20T12:00:00"), messageAt("2026-03-06T12:00:00")),
            runtimeInfo = now
        )
        val threeMonths = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = listOf(messageAt("2025-12-01T12:00:00"), messageAt("2026-03-06T12:00:00")),
            retainedMessages = listOf(messageAt("2025-12-01T12:00:00"), messageAt("2026-03-06T12:00:00")),
            runtimeInfo = now
        )
        val oneYear = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = listOf(messageAt("2025-03-01T12:00:00"), messageAt("2026-03-06T12:00:00")),
            retainedMessages = listOf(messageAt("2025-03-01T12:00:00"), messageAt("2026-03-06T12:00:00")),
            runtimeInfo = now
        )

        assertTrue(fiveDays!!.contains("About 5 days have passed since the last message."))
        assertTrue(twoWeeks!!.contains("About 2 weeks have passed since the last message."))
        assertTrue(threeMonths!!.contains("About 3 months have passed since the last message."))
        assertTrue(oneYear!!.contains("About 1 year has passed since the last message."))
    }

    @Test
    fun retainedTimelineNotesAreCappedToThreeNewestEntries() {
        val messages = listOf(
            messageAt("2026-03-06T08:00:00"),
            messageAt("2026-03-06T09:00:00"),
            messageAt("2026-03-06T10:00:00"),
            messageAt("2026-03-06T11:00:00"),
            messageAt("2026-03-06T12:00:00"),
            messageAt("2026-03-06T13:00:00")
        )

        val block = buildTimeAwarenessBlock(
            enabled = true,
            fullMessages = messages,
            retainedMessages = messages,
            runtimeInfo = runtimeAt(2026, 3, 6, 13, 30)
        )

        assertNotNull(block)
        assertTrue(block!!.contains("Recent retained timeline notes:"))
        assertEquals(3, "- Before".toRegex().findAll(block).count())
        assertFalse(block.contains("Before 2026-03-06 09:00 CET"))
        assertTrue(block.contains("Before 2026-03-06 10:00 CET"))
        assertTrue(block.contains("about 1 hour has passed"))
    }

    @Test
    fun olderAssistantJsonDefaultsTimeAwarenessToFalse() {
        val assistant = Json.decodeFromString<Assistant>(
            """
            {
              "id": "00000000-0000-0000-0000-000000000001",
              "name": "Legacy Assistant"
            }
            """.trimIndent()
        )

        assertFalse(assistant.enableTimeAwareness)
    }

    private fun runtimeAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): TimeAwarenessRuntimeInfo {
        return AndroidTimeAwarenessRuntimeInfo.from(
            ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId)
        )
    }

    private fun messageAt(timestamp: String): UIMessage = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text("test")),
        createdAt = LocalDateTime.parse(timestamp)
    )

    private fun assistantMessageAt(timestamp: String): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text("test")),
        createdAt = LocalDateTime.parse(timestamp)
    )

    private fun toolCallAt(timestamp: String): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.ToolCall(
                toolCallId = "call-1",
                toolName = "search_web",
                arguments = "{}"
            )
        ),
        createdAt = LocalDateTime.parse(timestamp)
    )

    private fun toolResultAt(timestamp: String): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.ToolResult(
                toolCallId = "call-1",
                toolName = "search_web",
                content = JsonPrimitive("ok"),
                arguments = JsonPrimitive("{}")
            )
        ),
        createdAt = LocalDateTime.parse(timestamp)
    )
}
