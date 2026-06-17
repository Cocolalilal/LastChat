package me.rerere.ai.util

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class MemoryTimeLabelsTest {
    private val utc = TimeZone.UTC

    @Test
    fun fuzzyMemoryAgeLabelUsesHumanBuckets() {
        val now = Instant.parse("2026-05-23T12:00:00Z").toEpochMilliseconds()

        assertEquals(
            "earlier today",
            fuzzyMemoryAgeLabel(Instant.parse("2026-05-23T08:00:00Z").toEpochMilliseconds(), now, utc)
        )
        assertEquals(
            "yesterday",
            fuzzyMemoryAgeLabel(Instant.parse("2026-05-22T08:00:00Z").toEpochMilliseconds(), now, utc)
        )
        assertEquals(
            "a few days ago",
            fuzzyMemoryAgeLabel(Instant.parse("2026-05-20T08:00:00Z").toEpochMilliseconds(), now, utc)
        )
        assertEquals(
            "a couple months ago",
            fuzzyMemoryAgeLabel(Instant.parse("2026-03-12T08:00:00Z").toEpochMilliseconds(), now, utc)
        )
        assertEquals(
            "a long time ago",
            fuzzyMemoryAgeLabel(Instant.parse("2024-01-01T08:00:00Z").toEpochMilliseconds(), now, utc)
        )
    }

    @Test
    fun fuzzyMemoryAgeLabelHandlesInvalidAndFutureTimestamps() {
        val now = Instant.parse("2026-05-23T12:00:00Z").toEpochMilliseconds()

        assertEquals("some time ago", fuzzyMemoryAgeLabel(0L, now, utc))
        assertEquals("earlier today", fuzzyMemoryAgeLabel(now + 86_400_000L, now, utc))
    }
}
