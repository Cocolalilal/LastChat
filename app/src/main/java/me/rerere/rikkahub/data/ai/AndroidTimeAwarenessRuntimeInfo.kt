package me.rerere.rikkahub.data.ai

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

internal object AndroidTimeAwarenessRuntimeInfo {
    fun now(): TimeAwarenessRuntimeInfo {
        return from(ZonedDateTime.now())
    }

    fun from(now: ZonedDateTime): TimeAwarenessRuntimeInfo {
        val zoneId = now.zone
        return TimeAwarenessRuntimeInfo(
            now = now.toLocalDateTime().toKotlinLocalDateTime(),
            timeZone = TimeZone.of(zoneId.id),
            timeZoneId = zoneId.id,
            timeZoneShortName = zoneId.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
        )
    }
}
