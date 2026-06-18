package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GenerationRuntimeInfoTest {
    @Test
    fun `episodicMemoryGroupForDate preserves memory prompt labels`() {
        val today = LocalDate.of(2026, 6, 18)

        assertEquals("Today", episodicMemoryGroupForDate(today, today))
        assertEquals("Yesterday", episodicMemoryGroupForDate(today.minusDays(1), today))
        assertEquals("This Week", episodicMemoryGroupForDate(today.minusDays(6), today))
        assertEquals("Older", episodicMemoryGroupForDate(today.minusWeeks(1), today))
    }
}
