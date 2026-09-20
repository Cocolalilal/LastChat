package me.rerere.ai.generation

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortableSpontaneousMessagingTest {
    @Test
    fun activeHoursWrapMidnight() {
        assertTrue(
            PortableSpontaneousMessaging.isWithinActiveHours(
                currentHour = 23,
                startHour = 22,
                endHour = 7,
            ),
        )
        assertFalse(
            PortableSpontaneousMessaging.isWithinActiveHours(
                currentHour = 8,
                startHour = 22,
                endHour = 7,
            ),
        )
        assertTrue(
            PortableSpontaneousMessaging.isWithinActiveHours(
                currentHour = 12,
                startHour = 7,
                endHour = 7,
            ),
        )
    }

    @Test
    fun pickCandidateAvoidsLastSenderWhenPossible() {
        val selected = PortableSpontaneousMessaging.pickCandidate(
            candidates = listOf(
                PortableSpontaneousCandidate("a", 1L),
                PortableSpontaneousCandidate("b", 2L),
            ),
            lastSenderAssistantId = "a",
            random = Random(0),
        )
        assertEquals("b", selected?.assistantId)
        assertNull(
            PortableSpontaneousMessaging.pickCandidate(
                candidates = emptyList(),
                lastSenderAssistantId = null,
                random = Random(0),
            ),
        )
    }

    @Test
    fun parseResponseReadsJsonAndIgnoresProse() {
        val parsed = PortableSpontaneousMessaging.parseResponse(
            """
            Sure.
            {"send": true, "reason": "check in", "relation": "recent_chat", "title": "Hi", "content": "Hello there"}
            """.trimIndent(),
        )
        assertEquals(true, parsed?.shouldSend)
        assertEquals("check in", parsed?.reason)
        assertEquals("Hi", parsed?.title)
        assertEquals("Hello there", parsed?.content)
        assertEquals(PortableSpontaneousRelation.RECENT_CHAT, parsed?.relation)
        assertNull(PortableSpontaneousMessaging.parseResponse("not json"))
    }

    @Test
    fun quietUntilIncludesIntervalAndJitter() {
        val until = PortableSpontaneousMessaging.computeGlobalQuietUntil(1_000L, Random(1))
        assertTrue(until >= 1_000L + PortableSpontaneousMessaging.WORK_INTERVAL_MS)
        assertTrue(
            until <= 1_000L + PortableSpontaneousMessaging.WORK_INTERVAL_MS +
                PortableSpontaneousMessaging.GLOBAL_JITTER_MS,
        )
    }
}
