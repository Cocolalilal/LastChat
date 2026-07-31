package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.uuid.Uuid

class MemorySearchServiceTest {
    @Test
    fun fuzzyMemoryAgeLabelUsesHumanBuckets() {
        val now = Instant.parse("2026-05-23T12:00:00Z").toEpochMilli()

        assertEquals("earlier today", fuzzyMemoryAgeLabel(Instant.parse("2026-05-23T08:00:00Z").toEpochMilli(), now))
        assertEquals("yesterday", fuzzyMemoryAgeLabel(Instant.parse("2026-05-22T08:00:00Z").toEpochMilli(), now))
        assertEquals("a few days ago", fuzzyMemoryAgeLabel(Instant.parse("2026-05-20T08:00:00Z").toEpochMilli(), now))
        assertEquals("a couple months ago", fuzzyMemoryAgeLabel(Instant.parse("2026-03-12T08:00:00Z").toEpochMilli(), now))
        assertEquals("a long time ago", fuzzyMemoryAgeLabel(Instant.parse("2024-01-01T08:00:00Z").toEpochMilli(), now))
    }

    @Test
    fun findConversationRecallSpansBuildsSmallWindowAroundKeyword() {
        val conversation = Conversation(
            id = Uuid.parse("00000000-0000-0000-0000-000000000301"),
            assistantId = Uuid.parse("00000000-0000-0000-0000-000000000302"),
            title = "Trip planning",
            createAt = Instant.parse("2026-05-20T08:00:00Z"),
            updateAt = Instant.parse("2026-05-20T09:00:00Z"),
            messageNodes = listOf(
                UIMessage.user("hello").toMessageNode(),
                UIMessage.assistant("hi").toMessageNode(),
                UIMessage.user("I want to visit Lisbon in June").toMessageNode(),
                UIMessage.assistant("Lisbon in June sounds warm and bright.").toMessageNode(),
                UIMessage.user("Also remember I prefer trains.").toMessageNode(),
            )
        )

        val spans = findConversationRecallSpans(conversation, "Lisbon train", maxSpans = 1, radius = 1)

        assertEquals(1, spans.size)
        assertEquals(2, spans.single().messageIndex)
        assertTrue(buildFallbackRecallSummary(spans.single()).contains("Lisbon"))
        assertFalse(buildFallbackRecallSummary(spans.single()).contains("hello"))
    }

    @Test
    fun memorySearchTokensPreserveUserTermsAndExpandRecallClues() {
        val tokens = memorySearchTokens("under the bed test memory")

        assertTrue(tokens.contains("under"))
        assertTrue(tokens.contains("beneath"))
        assertTrue(tokens.contains("bed"))
        assertTrue(tokens.contains("test"))
        assertTrue(tokens.contains("memory"))
    }

    @Test
    fun scoreMemorySearchTextMatchesUnderBedSceneWithDifferentWording() {
        val score = scoreMemorySearchText(
            text = "You startled me when you crawled out from beneath your bed.",
            query = "under the bed test memory"
        )

        assertTrue(score > 0)
    }

    @Test
    fun deterministicRecallQueriesKeepOriginalAndAddUsefulVariants() {
        val queries = buildDeterministicMemoryRecallQueries("body measurements weight bust waist hips")
            .map { it.text }

        assertEquals("body measurements weight bust waist hips", queries.first())
        assertTrue(queries.any { it.contains("chest") || it.contains("breast") })
        assertTrue(queries.any { it.contains("weight") && it.contains("waist") })
    }

    @Test
    fun parseMemorySearchTimeRangeUnderstandsRoughSpans() {
        val now = Instant.parse("2026-05-26T12:00:00Z").toEpochMilli()

        val lastMonth = parseMemorySearchTimeRange("last month", now)
        val fourMonthsAgo = parseMemorySearchTimeRange("4 months ago", now)

        assertEquals("last month", lastMonth?.label)
        assertTrue(lastMonth?.contains(Instant.parse("2026-04-15T12:00:00Z").toEpochMilli()) == true)
        assertTrue(lastMonth?.contains(Instant.parse("2026-05-15T12:00:00Z").toEpochMilli()) == false)
        assertEquals("4 months ago", fourMonthsAgo?.label)
    }

    @Test
    fun parseMemorySearchTimeRangeCarriesLastDayIntoEarlyMorning() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 5, 26).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val lastDay = parseMemorySearchTimeRange("last day", now)

        assertEquals("last day", lastDay?.label)
        assertTrue(lastDay?.contains(LocalDate.of(2026, 5, 25).atTime(23, 45).atZone(zone).toInstant().toEpochMilli()) == true)
        assertTrue(lastDay?.contains(LocalDate.of(2026, 5, 26).atTime(6, 59).atZone(zone).toInstant().toEpochMilli()) == true)
        assertTrue(lastDay?.contains(LocalDate.of(2026, 5, 26).atTime(7, 1).atZone(zone).toInstant().toEpochMilli()) == false)
    }

    @Test
    fun parseMemorySearchTimeRangeKeepsOtherFramesSoftAtBoundaries() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 5, 26).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val yesterday = parseMemorySearchTimeRange("yesterday", now)
        val lastTwoDays = parseMemorySearchTimeRange("last 2 days", now)
        val lastWeek = parseMemorySearchTimeRange("last week", now)
        val lastMonth = parseMemorySearchTimeRange("last month", now)

        assertTrue(yesterday?.contains(LocalDate.of(2026, 5, 26).atTime(6, 30).atZone(zone).toInstant().toEpochMilli()) == true)
        assertTrue(lastTwoDays?.contains(LocalDate.of(2026, 5, 24).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()) == true)
        assertTrue(lastTwoDays?.contains(LocalDate.of(2026, 5, 24).atStartOfDay(zone).minusMinutes(1).toInstant().toEpochMilli()) == false)
        assertTrue(lastWeek?.contains(LocalDate.of(2026, 5, 25).atTime(LocalTime.of(5, 0)).atZone(zone).toInstant().toEpochMilli()) == true)
        assertTrue(lastMonth?.contains(LocalDate.of(2026, 5, 1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()) == true)
    }

    @Test
    fun findConversationRecallSpansUsesOnlySelectedMessageVersions() {
        val conversation = Conversation(
            id = Uuid.parse("00000000-0000-0000-0000-000000000401"),
            assistantId = Uuid.parse("00000000-0000-0000-0000-000000000402"),
            title = "Old scene",
            createAt = Instant.parse("2026-05-18T08:00:00Z"),
            updateAt = Instant.parse("2026-05-18T09:00:00Z"),
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(
                        UIMessage.user("I hid under your bed and spooked you."),
                        UIMessage.user("Nothing relevant here."),
                    ),
                    selectIndex = 1
                )
            )
        )

        val spans = findConversationRecallSpans(conversation, "under the bed test memory", maxSpans = 1)

        assertTrue(spans.isEmpty())
    }

    @Test
    fun shouldRegisterMemorySearchToolRequiresMemoryAndToggle() {
        assertFalse(shouldRegisterMemorySearchTool(Assistant(enableMemory = false, enableMemorySearchTool = true)))
        assertFalse(shouldRegisterMemorySearchTool(Assistant(enableMemory = true, enableMemorySearchTool = false)))
        assertTrue(shouldRegisterMemorySearchTool(Assistant(enableMemory = true, enableMemorySearchTool = true)))
    }
}
