package me.rerere.ai.generation

import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortableConversationQueriesTest {
    @Test
    fun pageSortsPinnedFirstAndHonorsOffset() = runBlocking {
        val store = InMemoryPortableConversationStore(
            listOf(
                record("old", assistantId = "a", title = "Old", updated = 1L, pinned = false),
                record("pinned", assistantId = "a", title = "Pinned", updated = 2L, pinned = true),
                record("new", assistantId = "a", title = "Castle walk", updated = 3L, pinned = false),
                record("other", assistantId = "b", title = "Other", updated = 4L, pinned = false),
            ),
        )
        val first = store.page(
            PortableConversationQuery(assistantId = "a", offset = 0, limit = 2),
        )
        assertEquals(3, first.totalCount)
        assertEquals(listOf("pinned", "new"), first.items.map { it.id })
        assertEquals(2, first.nextOffset)
        val second = store.page(
            PortableConversationQuery(assistantId = "a", offset = 2, limit = 2),
        )
        assertEquals(listOf("old"), second.items.map { it.id })
        assertNull(second.nextOffset)
    }

    @Test
    fun searchHighlightsSelectedMessageText() = runBlocking {
        val store = InMemoryPortableConversationStore(
            listOf(
                record(
                    id = "c1",
                    assistantId = "a",
                    title = "Castle",
                    updated = 10L,
                    text = "We walked to the lighthouse.",
                ),
            ),
        )
        val hits = store.searchMessages(
            PortableConversationQuery(assistantId = "a", query = "lighthouse", limit = 10),
        )
        assertEquals(1, hits.size)
        assertTrue(hits.single().snippet.contains("[lighthouse]"))
        assertEquals("c1", hits.single().conversationId)
    }

    @Test
    fun usageTotalsSumSelectedTokenUsage() = runBlocking {
        val store = InMemoryPortableConversationStore(
            listOf(
                record(
                    id = "c1",
                    assistantId = "a",
                    title = "Stats",
                    updated = 1L,
                    text = "hi",
                    usage = TokenUsage(promptTokens = 11, completionTokens = 7, cachedTokens = 2, totalTokens = 18),
                ),
            ),
        )
        val totals = store.usageTotals()
        assertEquals(1, totals.conversationCount)
        assertEquals(1, totals.messageCount)
        assertEquals(11, totals.inputTokens)
        assertEquals(7, totals.outputTokens)
        assertEquals(2, totals.cachedTokens)
    }

    @Test
    fun highlightSnippetReturnsNullWhenMissing() {
        assertNull(PortableConversationQueries.highlightSnippet("hello world", "lighthouse"))
        val snippet = PortableConversationQueries.highlightSnippet(
            "We walked to the lighthouse yesterday after lunch.",
            "lighthouse",
            radius = 12,
        )
        assertTrue(snippet!!.contains("[lighthouse]"))
        assertTrue(snippet.startsWith("..."))
        assertTrue(snippet.endsWith("..."))
    }

    private fun record(
        id: String,
        assistantId: String,
        title: String,
        updated: Long,
        pinned: Boolean = false,
        text: String = title,
        usage: TokenUsage? = null,
    ) = PortableConversationRecord(
        id = id,
        assistantId = assistantId,
        title = title,
        messageNodes = listOf(
            MessageNode.of(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(text)),
                    usage = usage,
                ),
            ),
        ),
        isPinned = pinned,
        updatedAtEpochMs = updated,
    )
}
