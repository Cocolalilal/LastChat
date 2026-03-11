package me.rerere.rikkahub.service

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random
import kotlin.uuid.Uuid

class SpontaneousMessagingTest {
    @Test
    fun activeHoursSupportsDaytimeWindow() {
        assertTrue(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 14,
                startHour = 9,
                endHour = 18,
            )
        )
        assertFalse(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 19,
                startHour = 9,
                endHour = 18,
            )
        )
    }

    @Test
    fun activeHoursSupportsWraparoundWindow() {
        assertTrue(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 23,
                startHour = 22,
                endHour = 6,
            )
        )
        assertTrue(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 3,
                startHour = 22,
                endHour = 6,
            )
        )
        assertFalse(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 12,
                startHour = 22,
                endHour = 6,
            )
        )
    }

    @Test
    fun sameStartAndEndMeansAllDay() {
        assertTrue(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 2,
                startHour = 8,
                endHour = 8,
            )
        )
        assertTrue(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 18,
                startHour = 8,
                endHour = 8,
            )
        )
    }

    @Test
    fun pickCandidateAvoidsLastSenderWhenAlternativeExists() {
        val lastSenderId = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val otherId = Uuid.parse("00000000-0000-0000-0000-000000000002")

        val selected = SpontaneousMessaging.pickCandidate(
            candidates = listOf(
                SpontaneousCandidate(assistantId = lastSenderId, lastNotificationTime = 100L),
                SpontaneousCandidate(assistantId = otherId, lastNotificationTime = 200L),
            ),
            lastSenderAssistantId = lastSenderId,
            random = Random(42),
        )

        assertNotNull(selected)
        assertEquals(otherId, selected!!.assistantId)
    }

    @Test
    fun pickCandidateFallsBackToOnlyCandidate() {
        val onlyId = Uuid.parse("00000000-0000-0000-0000-000000000003")

        val selected = SpontaneousMessaging.pickCandidate(
            candidates = listOf(
                SpontaneousCandidate(assistantId = onlyId, lastNotificationTime = 100L),
            ),
            lastSenderAssistantId = onlyId,
            random = Random(42),
        )

        assertNotNull(selected)
        assertEquals(onlyId, selected!!.assistantId)
    }

    @Test
    fun globalQuietUntilAddsBaseIntervalAndBoundedJitter() {
        val now = 1_000_000L
        val quietUntil = SpontaneousMessaging.computeGlobalQuietUntil(now, Random(7))
        val minExpected = now + (SPONTANEOUS_WORK_INTERVAL_MINUTES * 60_000L)
        val maxExpected = minExpected + (SPONTANEOUS_GLOBAL_JITTER_MINUTES * 60_000L)

        assertTrue(quietUntil in minExpected..maxExpected)
    }

    @Test
    fun determineRelationUsesRecentUserAwaitingReplyConversation() {
        val now = Instant.parse("2026-03-11T12:00:00Z")
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(MessageNode.of(UIMessage.user("Hey"))),
            updateAt = now.minusSeconds(60 * 60),
        )

        assertEquals(
            SpontaneousMessageRelation.RECENT_CHAT,
            SpontaneousMessaging.determineRelation(
                conversation = conversation,
                nowMillis = now.toEpochMilli(),
            )
        )
    }

    @Test
    fun determineRelationUsesUnrelatedWhenAssistantSpokeLast() {
        val now = Instant.parse("2026-03-11T12:00:00Z")
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(MessageNode.of(UIMessage.assistant("Hey there"))),
            updateAt = now.minusSeconds(60 * 60),
        )

        assertEquals(
            SpontaneousMessageRelation.UNRELATED,
            SpontaneousMessaging.determineRelation(
                conversation = conversation,
                nowMillis = now.toEpochMilli(),
            )
        )
    }

    @Test
    fun determineRelationUsesUnrelatedWhenConversationIsStale() {
        val now = Instant.parse("2026-03-11T12:00:00Z")
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(MessageNode.of(UIMessage.user("Still there?"))),
            updateAt = now.minusSeconds(25 * 60 * 60),
        )

        assertEquals(
            SpontaneousMessageRelation.UNRELATED,
            SpontaneousMessaging.determineRelation(
                conversation = conversation,
                nowMillis = now.toEpochMilli(),
            )
        )
    }

    @Test
    fun determineRelationUsesUnrelatedWithoutConversation() {
        assertEquals(
            SpontaneousMessageRelation.UNRELATED,
            SpontaneousMessaging.determineRelation(
                conversation = null,
                nowMillis = Instant.parse("2026-03-11T12:00:00Z").toEpochMilli(),
            )
        )
    }

    @Test
    fun parseResponseReadsJsonWithSurroundingText() {
        val parsed = SpontaneousMessaging.parseResponse(
            """
            Here you go:
            {"send":true,"reason":"timely","title":"Checking in","content":"Hey, I was thinking about you."}
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertTrue(parsed!!.shouldSend)
        assertEquals("timely", parsed.reason)
        assertEquals("Checking in", parsed.title)
        assertEquals("Hey, I was thinking about you.", parsed.content)
    }

    @Test
    fun parseResponseIgnoresUnknownRelationField() {
        val parsed = SpontaneousMessaging.parseResponse(
            """{"send":true,"reason":"timely","relation":"maybe","content":"Hi"}"""
        )

        assertNotNull(parsed)
        assertEquals("Hi", parsed!!.content)
    }

    @Test
    fun parseResponseReturnsNullForMalformedJson() {
        val parsed = SpontaneousMessaging.parseResponse("{\"send\":true,\"content\":")

        assertNull(parsed)
    }
}
