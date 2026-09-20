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
}
