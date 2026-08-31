package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryConsolidationPolicyTest {
    @Test
    fun trivialChatsDoNotSpendAConsolidationCall() {
        assertEquals(
            MemoryConsolidationDecision.NotWorthwhile,
            decideMemoryConsolidation(
                meaningfulMessageCount = MIN_CONSOLIDATION_MESSAGES - 1,
                idleMillis = 0L,
                configuredDelayMinutes = 30,
            ),
        )
    }

    @Test
    fun mediumChatsWaitForInactivity() {
        assertEquals(
            MemoryConsolidationDecision.Schedule(delayMillis = 20 * 60_000L),
            decideMemoryConsolidation(
                meaningfulMessageCount = MIN_CONSOLIDATION_MESSAGES,
                idleMillis = 10 * 60_000L,
                configuredDelayMinutes = 30,
            ),
        )
    }

    @Test
    fun substantialChatsConsolidateImmediately() {
        assertEquals(
            MemoryConsolidationDecision.Schedule(delayMillis = 0L),
            decideMemoryConsolidation(
                meaningfulMessageCount = IMMEDIATE_CONSOLIDATION_MESSAGES,
                idleMillis = 0L,
                configuredDelayMinutes = 30,
            ),
        )
    }
}
