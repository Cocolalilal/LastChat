package me.rerere.ai.generation

import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonFilePortableConversationStoreTest {
    @Test
    fun saveGetListAndDeleteRoundTrip() = runBlocking {
        var bytes: ByteArray? = null
        val store = JsonFilePortableConversationStore(
            loadBytes = { bytes },
            saveBytes = { bytes = it },
        )
        val record = PortableConversationRecord(
            id = "chat-1",
            assistantId = "asst",
            title = "Castle",
            messageNodes = listOf(MessageNode.of(UIMessage.user("hello"))),
            isPinned = true,
            updatedAtEpochMs = 42L,
            truncateIndex = 2,
            contextSummary = "Earlier we walked.",
            memoryLastMessageId = "msg-9",
        )
        store.save(record)
        assertEquals("Castle", store.get("chat-1")?.title)
        assertEquals(1, store.list().size)
        assertTrue((bytes?.size ?: 0) > 0)

        val reloaded = JsonFilePortableConversationStore(
            loadBytes = { bytes },
            saveBytes = { bytes = it },
        )
        val restored = reloaded.get("chat-1")
        assertEquals("Castle", restored?.title)
        assertEquals(2, restored?.truncateIndex)
        assertEquals("Earlier we walked.", restored?.contextSummary)
        assertEquals("msg-9", restored?.memoryLastMessageId)
        assertEquals(1, restored?.messageNodes?.size)

        reloaded.delete("chat-1")
        assertNull(reloaded.get("chat-1"))
        assertTrue(reloaded.list().isEmpty())
    }

    @Test
    fun listByAssistantOrdersAndLimits() = runBlocking {
        var bytes: ByteArray? = null
        val store = JsonFilePortableConversationStore(
            loadBytes = { bytes },
            saveBytes = { bytes = it },
        )
        store.save(
            PortableConversationRecord(
                id = "older",
                assistantId = "asst-a",
                title = "Older",
                messageNodes = emptyList(),
                updatedAtEpochMs = 10L,
            ),
        )
        store.save(
            PortableConversationRecord(
                id = "newer",
                assistantId = "asst-a",
                title = "Newer",
                messageNodes = emptyList(),
                updatedAtEpochMs = 20L,
            ),
        )
        store.save(
            PortableConversationRecord(
                id = "other",
                assistantId = "asst-b",
                title = "Other",
                messageNodes = emptyList(),
                updatedAtEpochMs = 30L,
            ),
        )
        val listed = store.listByAssistant("asst-a", limit = 1)
        assertEquals(listOf("newer"), listed.map { it.id })
        assertEquals(2, store.listByAssistant("asst-a").size)
        store.finalizeDeletion("newer")
        assertEquals(listOf("older"), store.listByAssistant("asst-a").map { it.id })
    }

    @Test
    fun dailyActivityPersistsAfterConversationDelete() = runBlocking {
        var bytes: ByteArray? = null
        val store = JsonFilePortableConversationStore(
            loadBytes = { bytes },
            saveBytes = { bytes = it },
        )
        store.save(
            PortableConversationRecord(
                id = "chat-1",
                assistantId = "asst",
                title = "Photo",
                messageNodes = emptyList(),
                updatedAtEpochMs = 1L,
            ),
        )
        store.recordDailyActivity(date = "2026-09-18", timestampEpochMs = 9L)
        store.delete("chat-1")
        val reloaded = JsonFilePortableConversationStore(
            loadBytes = { bytes },
            saveBytes = { bytes = it },
        )
        assertTrue(reloaded.list().isEmpty())
        assertEquals(1, reloaded.dailyActivity().single { it.date == "2026-09-18" }.messageCount)
    }
}
