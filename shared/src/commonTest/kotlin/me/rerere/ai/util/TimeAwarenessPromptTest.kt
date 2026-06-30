package me.rerere.ai.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeAwarenessPromptTest {
    private val timeZone = TimeZone.of("Europe/Zurich")

    @Test
    fun returnsNullWhenDisabled() {
        val block = buildTimeAwarenessPromptBlock(
            enabled = false,
            fullMessageTimes = emptyList(),
            retainedMessageTimes = emptyList(),
            now = LocalDateTime(2026, 3, 6, 12, 0),
            timeZone = timeZone,
            timeZoneId = "Europe/Zurich",
            timeZoneShortName = "CET"
        )

        assertNull(block)
    }

    @Test
    fun includesFirstMessageHintWhenNoPreviousMessageExists() {
        val block = buildTimeAwarenessPromptBlock(
            enabled = true,
            fullMessageTimes = listOf(LocalDateTime.parse("2026-03-06T12:00:00")),
            retainedMessageTimes = listOf(LocalDateTime.parse("2026-03-06T12:00:00")),
            now = LocalDateTime(2026, 3, 6, 12, 1),
            timeZone = timeZone,
            timeZoneId = "Europe/Zurich",
            timeZoneShortName = "CET"
        )

        assertNotNull(block)
        assertTrue(block.contains("Local timestamp: 2026-03-06 12:01 CET"))
        assertTrue(block.contains("Weekday: Friday"))
        assertTrue(block.contains("Month: March"))
        assertTrue(block.contains("Year: 2026"))
        assertTrue(block.contains("Timezone: Europe/Zurich (CET)"))
        assertTrue(block.contains("Conversation state: This appears to be the first visible message"))
    }

    @Test
    fun thresholdGapRoundsToTensAndAddsHumanPhrase() {
        val block = blockFor(
            "2026-03-06T11:26:00",
            "2026-03-06T12:00:00"
        )

        assertTrue(block.contains("About 30 minutes have passed since the last message."))
    }

    @Test
    fun underThresholdGapOmitsSignificantPauseNote() {
        val block = blockFor(
            "2026-03-06T12:00:00",
            "2026-03-06T12:12:00"
        )

        assertFalse(block.contains("since the last message"))
    }

    @Test
    fun hourScaleUsesHumanFriendlyWording() {
        val block = blockFor(
            "2026-03-06T10:45:00",
            "2026-03-06T12:00:00"
        )

        assertTrue(block.contains("About 1 hour has passed since the last message."))
    }

    @Test
    fun multiHourGapsCanIncludeHoursAndMinutes() {
        val block = blockFor(
            "2026-03-06T10:27:00",
            "2026-03-06T12:00:00"
        )

        assertTrue(block.contains("About 1 hour and 30 minutes have passed since the last message."))
    }

    @Test
    fun usesCurrentMessageTimestampInsteadOfGenerationTimeForGap() {
        val messages = listOf(
            LocalDateTime.parse("2026-03-06T11:20:00"),
            LocalDateTime.parse("2026-03-06T12:00:00")
        )

        val block = buildTimeAwarenessPromptBlock(
            enabled = true,
            fullMessageTimes = messages,
            retainedMessageTimes = messages,
            now = LocalDateTime(2026, 3, 6, 13, 10),
            timeZone = timeZone,
            timeZoneId = "Europe/Zurich",
            timeZoneShortName = "CET"
        )

        assertNotNull(block)
        assertTrue(block.contains("About 40 minutes have passed since the last message."))
        assertFalse(block.contains("About 1 hour and 50 minutes have passed since the last message."))
    }

    @Test
    fun detectsDayMonthAndYearRollovers() {
        val block = buildTimeAwarenessPromptBlock(
            enabled = true,
            fullMessageTimes = listOf(
                LocalDateTime.parse("2025-12-31T23:30:00"),
                LocalDateTime.parse("2026-01-01T00:30:00")
            ),
            retainedMessageTimes = emptyList(),
            now = LocalDateTime(2026, 1, 1, 0, 30),
            timeZone = timeZone,
            timeZoneId = "Europe/Zurich",
            timeZoneShortName = "CET"
        )

        assertNotNull(block)
        assertTrue(block.contains("Local day changed since the previous message."))
        assertTrue(block.contains("Local month changed since the previous message."))
        assertTrue(block.contains("Local year changed since the previous message."))
    }

    @Test
    fun dateBasedGapUsesDaysWeeksMonthsAndYears() {
        val now = LocalDateTime(2026, 3, 6, 12, 0)
        val fiveDays = blockFor("2026-03-01T12:00:00", "2026-03-06T12:00:00", now)
        val twoWeeks = blockFor("2026-02-20T12:00:00", "2026-03-06T12:00:00", now)
        val threeMonths = blockFor("2025-12-01T12:00:00", "2026-03-06T12:00:00", now)
        val oneYear = blockFor("2025-03-01T12:00:00", "2026-03-06T12:00:00", now)

        assertTrue(fiveDays.contains("About 5 days have passed since the last message."))
        assertTrue(twoWeeks.contains("About 2 weeks have passed since the last message."))
        assertTrue(threeMonths.contains("About 3 months have passed since the last message."))
        assertTrue(oneYear.contains("About 1 year has passed since the last message."))
    }

    @Test
    fun retainedTimelineNotesAreCappedToThreeNewestEntries() {
        val messages = listOf(
            "2026-03-06T08:00:00",
            "2026-03-06T09:00:00",
            "2026-03-06T10:00:00",
            "2026-03-06T11:00:00",
            "2026-03-06T12:00:00",
            "2026-03-06T13:00:00"
        ).map(LocalDateTime::parse)

        val block = buildTimeAwarenessPromptBlock(
            enabled = true,
            fullMessageTimes = messages,
            retainedMessageTimes = messages,
            now = LocalDateTime(2026, 3, 6, 13, 30),
            timeZone = timeZone,
            timeZoneId = "Europe/Zurich",
            timeZoneShortName = "CET"
        )

        assertNotNull(block)
        assertTrue(block.contains("Recent retained timeline notes:"))
        assertEquals(3, "- Before".toRegex().findAll(block).count())
        assertFalse(block.contains("Before 2026-03-06 09:00 CET"))
        assertTrue(block.contains("Before 2026-03-06 10:00 CET"))
        assertTrue(block.contains("about 1 hour has passed"))
    }

    private fun blockFor(
        previous: String,
        current: String,
        now: LocalDateTime = LocalDateTime.parse(current)
    ): String {
        val messages = listOf(LocalDateTime.parse(previous), LocalDateTime.parse(current))
        return buildTimeAwarenessPromptBlock(
            enabled = true,
            fullMessageTimes = messages,
            retainedMessageTimes = messages,
            now = now,
            timeZone = timeZone,
            timeZoneId = "Europe/Zurich",
            timeZoneShortName = "CET"
        ) ?: error("Expected time awareness block")
    }
}
