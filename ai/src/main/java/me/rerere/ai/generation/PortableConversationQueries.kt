package me.rerere.ai.generation

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

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
