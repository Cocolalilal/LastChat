package me.rerere.ai.generation

import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.platform.PlatformFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableChatStorageMaintenanceTest {
    @Test
    fun deletesUnreferencedUploadFilesAndKeepsLinkedImages() = runBlocking {
        val store = InMemoryPortableConversationStore(
            listOf(
                PortableConversationRecord(
                    id = "c1",
                    assistantId = "a",
                    title = "Photo",
                    messageNodes = listOf(
                        MessageNode.of(
                            UIMessage(
                                role = MessageRole.USER,
                                parts = listOf(UIMessagePart.Image(url = "uploads/keep.png")),
                            ),
                        ),
                    ),
                    updatedAtEpochMs = 1L,
                ),
            ),
        )
        val files = MemoryPlatformFileStore(
            mapOf(
                "uploads/keep.png" to byteArrayOf(1),
                "uploads/orphan.png" to byteArrayOf(2),
                "images/stale.jpg" to byteArrayOf(3),
            ),
        )
        val result = PortableChatStorageMaintenance.run(store, files)
        assertEquals(3, result.scanned)
        assertEquals(2, result.deleted)
        assertTrue(files.exists("uploads/keep.png"))
        assertFalse(files.exists("uploads/orphan.png"))
        assertFalse(files.exists("images/stale.jpg"))
    }
}

private class MemoryPlatformFileStore(
    initial: Map<String, ByteArray>,
) : PlatformFileStore {
    private val files = initial.toMutableMap()

    override suspend fun readBytes(path: String): ByteArray? = files[path.trimStart('/')]

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
        files[path.trimStart('/')] = bytes
    }

    override suspend fun delete(path: String): Boolean = files.remove(path.trimStart('/')) != null

    override suspend fun exists(path: String): Boolean = files.containsKey(path.trimStart('/'))

    override suspend fun lastModified(path: String): Long? = if (exists(path)) 1L else null

    override suspend fun listFiles(path: String): List<String> {
        val prefix = path.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        return files.keys.filter { it.startsWith(prefix) }
    }
}
