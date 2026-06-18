package me.rerere.rikkahub.data.ai

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

interface GenerationRuntimeInfo {
    fun isToday(instant: Instant): Boolean

    fun episodicMemoryGroup(timestampMillis: Long): String
}

class AndroidGenerationRuntimeInfo(
    private val today: () -> LocalDate = LocalDate::now,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : GenerationRuntimeInfo {
    override fun isToday(instant: Instant): Boolean {
        return LocalDateTime.ofInstant(instant, zoneId()).toLocalDate() == today()
    }

    override fun episodicMemoryGroup(timestampMillis: Long): String {
        return episodicMemoryGroupForDate(
            memoryDate = Instant.ofEpochMilli(timestampMillis).atZone(zoneId()).toLocalDate(),
            today = today(),
        )
    }
}

internal fun episodicMemoryGroupForDate(
    memoryDate: LocalDate,
    today: LocalDate,
): String {
    val yesterday = today.minusDays(1)
    val lastWeek = today.minusWeeks(1)

    return when {
        memoryDate.isEqual(today) -> "Today"
        memoryDate.isEqual(yesterday) -> "Yesterday"
        memoryDate.isAfter(lastWeek) -> "This Week"
        else -> "Older"
    }
}
