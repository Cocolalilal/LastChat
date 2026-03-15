package me.rerere.rikkahub.service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun describeElapsedTimeUsesHumanFriendlyUnits() {
        assertEquals("less than a minute", SpontaneousMessaging.describeElapsedTime(30_000L))
        assertEquals("5 minutes", SpontaneousMessaging.describeElapsedTime(5 * 60_000L))
        assertEquals("2 hours", SpontaneousMessaging.describeElapsedTime(2 * 60 * 60_000L))
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
    fun parseResponseReadsJsonWithSurroundingText() {
        val parsed = SpontaneousMessaging.parseResponse(
            """
            Here you go:
            {"send":true,"reason":"timely","relation":"recent_chat","title":"Checking in","content":"Hey, I was thinking about you."}
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertTrue(parsed!!.shouldSend)
        assertEquals("timely", parsed.reason)
        assertEquals(SpontaneousMessageRelation.RECENT_CHAT, parsed.relation)
        assertEquals("Checking in", parsed.title)
        assertEquals("Hey, I was thinking about you.", parsed.content)
    }

    @Test
    fun parseResponseLeavesRelationNullWhenValueIsUnknown() {
        val parsed = SpontaneousMessaging.parseResponse(
            """{"send":true,"reason":"timely","relation":"maybe","content":"Hi"}"""
        )

        assertNotNull(parsed)
        assertNull(parsed!!.relation)
    }

    @Test
    fun parseResponseReturnsNullForMalformedJson() {
        val parsed = SpontaneousMessaging.parseResponse("{\"send\":true,\"content\":")

        assertNull(parsed)
    }
}
