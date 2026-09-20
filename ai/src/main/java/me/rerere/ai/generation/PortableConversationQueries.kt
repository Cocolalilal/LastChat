package me.rerere.ai.generation

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory list/search/usage helpers used by JSON/iOS stores and as the
 * default [PortableConversationStore] implementation. Room overrides with SQL.
 */
object PortableConversationQueries {
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_SEARCH_HITS = 100
    const val SNIPPET_RADIUS = 56

    fun sortForList(records: List<PortableConversationRecord>): List<PortableConversationRecord> {
        return records.sortedWith(
            compareByDescending<PortableConversationRecord> { it.isPinned }
                .thenByDescending { it.updatedAtEpochMs },
        )
    }

    fun matchesQuery(record: PortableConversationRecord, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        if (record.title.contains(needle, ignoreCase = true)) return true
        return record.messageNodes.any { node ->
            searchableText(node.currentMessage).contains(needle, ignoreCase = true)
        }
    }

    fun page(
        records: List<PortableConversationRecord>,
        query: PortableConversationQuery,
    ): PortableConversationPage {
        val filtered = sortForList(records)
            .filter { record ->
                (query.assistantId == null || record.assistantId == query.assistantId) &&
                    matchesQuery(record, query.query)
            }
        val offset = query.offset.coerceAtLeast(0)
        val limit = query.limit.coerceAtLeast(1)
        val window = filtered.drop(offset).take(limit)
        val items = if (query.includeMessages) {
            window
        } else {
            window.map { it.copy(messageNodes = emptyList()) }
        }
        val nextOffset = (offset + items.size).takeIf { it < filtered.size }
        return PortableConversationPage(
            items = items,
            totalCount = filtered.size,
            nextOffset = nextOffset,
        )
    }

    fun searchMessages(
        records: List<PortableConversationRecord>,
        query: String,
        limit: Int = MAX_SEARCH_HITS,
    ): List<PortableMessageSearchHit> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return sortForList(records).flatMap { record ->
            record.messageNodes.mapNotNull { node ->
                val message = node.currentMessage
                val snippet = highlightSnippet(searchableText(message), needle) ?: return@mapNotNull null
                PortableMessageSearchHit(
                    conversationId = record.id,
                    conversationTitle = record.title.ifBlank { "New chat" },
                    nodeId = node.id.toString(),
                    messageId = message.id.toString(),
                    snippet = snippet,
                    updatedAtEpochMs = record.updatedAtEpochMs,
                )
            }
        }.take(limit.coerceAtLeast(0))
    }

    fun usageTotals(records: List<PortableConversationRecord>): PortableUsageTotals {
        var messages = 0L
        var input = 0L
        var output = 0L
        var cached = 0L
        records.forEach { record ->
            record.messageNodes.forEach { node ->
                val usage = node.currentMessage.usage ?: return@forEach
                messages += 1
                input += usage.promptTokens.toLong()
                output += usage.completionTokens.toLong()
                cached += usage.cachedTokens.toLong()
            }
        }
        return PortableUsageTotals(
            conversationCount = records.size.toLong(),
            messageCount = messages,
            inputTokens = input,
            outputTokens = output,
            cachedTokens = cached,
        )
    }

    fun isoToday(
        clock: Clock = Clock.System,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String = clock.now().toLocalDateTime(timeZone).date.toString()

    fun nowEpochMs(clock: Clock = Clock.System): Long = clock.now().toEpochMilliseconds()

    fun isoDateFromEpoch(
        epochMs: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String? {
        if (epochMs <= 0L) return null
        return Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).date.toString()
    }

    fun rollingWindowStart(today: LocalDate): LocalDate {
        return LocalDate(today.year, today.month, 1).minus(DatePeriod(months = 11))
    }

    fun incrementDailyActivity(
        existing: List<PortableDailyActivity>,
        date: String,
        timestampEpochMs: Long,
    ): List<PortableDailyActivity> {
        val current = existing.firstOrNull { it.date == date }
        val updated = PortableDailyActivity(
            date = date,
            messageCount = (current?.messageCount ?: 0) + 1,
            lastMessageEpochMs = maxOf(current?.lastMessageEpochMs ?: 0L, timestampEpochMs),
        )
        return (existing.filterNot { it.date == date } + updated).sortedBy { it.date }
    }

    fun mergeDailyActivity(
        existing: List<PortableDailyActivity>,
        incoming: List<PortableDailyActivity>,
    ): List<PortableDailyActivity> {
        val merged = existing.associateBy { it.date }.toMutableMap()
        incoming.forEach { entry ->
            val current = merged[entry.date]
            merged[entry.date] = if (current == null) {
                entry
            } else {
                PortableDailyActivity(
                    date = entry.date,
                    messageCount = maxOf(current.messageCount, entry.messageCount),
                    lastMessageEpochMs = maxOf(current.lastMessageEpochMs, entry.lastMessageEpochMs),
                )
            }
        }
        return merged.values.sortedBy { it.date }
    }

    fun dailyActivityFromConversations(
        records: List<PortableConversationRecord>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<PortableDailyActivity> {
        val counts = linkedMapOf<String, PortableDailyActivity>()
        records.forEach { record ->
            val fallback = isoDateFromEpoch(
                epochMs = record.createdAtEpochMs.takeIf { it > 0L } ?: record.updatedAtEpochMs,
                timeZone = timeZone,
            )
            val selectedDates = record.messageNodes.map { node ->
                node.currentMessage.createdAt.date.toString()
            }
            val dates = selectedDates.ifEmpty { listOfNotNull(fallback) }
            dates.forEach { date ->
                val current = counts[date]
                counts[date] = PortableDailyActivity(
                    date = date,
                    messageCount = (current?.messageCount ?: 0) + 1,
                    lastMessageEpochMs = maxOf(
                        current?.lastMessageEpochMs ?: 0L,
                        record.updatedAtEpochMs,
                    ),
                )
            }
        }
        return counts.values.sortedBy { it.date }
    }

    /**
     * Shared 12-month statistics page semantics:
     * conversations and tokens come from live chats in the window; message count
     * is max(daily-activity in window, selected messages in window) so heatmap
     * sends survive deletion on every host.
     */
    fun displayUsageTotals(
        records: List<PortableConversationRecord>,
        activity: List<PortableDailyActivity>,
        today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): PortableUsageTotals {
        val windowStart = rollingWindowStart(today)
        val conversationCount = records.count { record ->
            val date = parseIsoDate(
                isoDateFromEpoch(
                    epochMs = record.createdAtEpochMs.takeIf { it > 0L } ?: record.updatedAtEpochMs,
                    timeZone = timeZone,
                ),
            ) ?: return@count false
            !date.isBefore(windowStart) && !date.isAfter(today)
        }.toLong()
        var selectedMessages = 0L
        var input = 0L
        var output = 0L
        var cached = 0L
        records.forEach { record ->
            val fallback = parseIsoDate(
                isoDateFromEpoch(
                    epochMs = record.createdAtEpochMs.takeIf { it > 0L } ?: record.updatedAtEpochMs,
                    timeZone = timeZone,
                ),
            )
            record.messageNodes.forEach { node ->
                val message = node.currentMessage
                val date = message.createdAt.date
                if (date.isBefore(windowStart) || date.isAfter(today)) return@forEach
                selectedMessages += 1
                val usage = message.usage ?: return@forEach
                input += usage.promptTokens.toLong()
                output += usage.completionTokens.toLong()
                cached += usage.cachedTokens.toLong()
            }
            if (record.messageNodes.isEmpty() && fallback != null &&
                !fallback.isBefore(windowStart) && !fallback.isAfter(today)
            ) {
                selectedMessages += 1
            }
        }
        val activityMessages = activity.sumOf { entry ->
            val date = parseIsoDate(entry.date) ?: return@sumOf 0L
            if (!date.isBefore(windowStart) && !date.isAfter(today)) {
                entry.messageCount.toLong()
            } else {
                0L
            }
        }
        return PortableUsageTotals(
            conversationCount = conversationCount,
            messageCount = maxOf(activityMessages, selectedMessages),
            inputTokens = input,
            outputTokens = output,
            cachedTokens = cached,
        )
    }

    fun parseIsoDate(raw: String?): LocalDate? {
        val value = raw?.trim().orEmpty()
        if (value.length < 10) return null
        return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }

    private fun LocalDate.isBefore(other: LocalDate): Boolean = this < other

    private fun LocalDate.isAfter(other: LocalDate): Boolean = this > other


    fun searchableText(message: UIMessage): String {
        return message.parts.joinToString("\n") { part ->
            when (part) {
                is UIMessagePart.Text -> part.text
                is UIMessagePart.Document -> listOf(part.fileName, part.mime).joinToString(" ")
                is UIMessagePart.Image -> part.url
                is UIMessagePart.Video -> part.url
                is UIMessagePart.Audio -> part.url
                else -> ""
            }
        }
    }

    fun highlightSnippet(source: String, query: String, radius: Int = SNIPPET_RADIUS): String? {
        if (source.isBlank() || query.isBlank()) return null
        val flattened = source.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (flattened.isBlank()) return null
        val matchIndex = flattened.indexOf(query, ignoreCase = true)
        if (matchIndex < 0) return null
        val start = (matchIndex - radius).coerceAtLeast(0)
        val end = (matchIndex + query.length + radius).coerceAtMost(flattened.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < flattened.length) "..." else ""
        val before = flattened.substring(start, matchIndex)
        val match = flattened.substring(matchIndex, matchIndex + query.length)
        val after = flattened.substring(matchIndex + query.length, end)
        return prefix + before + "[" + match + "]" + after + suffix
    }

    fun referencedFilePaths(records: List<PortableConversationRecord>): Set<String> {
        val paths = linkedSetOf<String>()
        records.forEach { record ->
            record.messageNodes.forEach { node ->
                node.messages.forEach { message ->
                    message.parts.forEach { part ->
                        val url = when (part) {
                            is UIMessagePart.Image -> part.url
                            is UIMessagePart.Video -> part.url
                            is UIMessagePart.Audio -> part.url
                            is UIMessagePart.Document -> part.url
                            else -> null
                        }
                        collectPath(url)?.let(paths::add)
                    }
                }
            }
        }
        return paths
    }

    private fun collectPath(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        val withoutQuery = trimmed.substringBefore('?')
        return when {
            withoutQuery.startsWith("file://") -> withoutQuery.removePrefix("file://")
            withoutQuery.startsWith("/") -> withoutQuery.trimStart('/')
            '/' in withoutQuery && !withoutQuery.contains("://") -> withoutQuery.trimStart('/')
            else -> null
        }
    }
}
