package me.rerere.lastchat.ios.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosBackupDbTest {
    private val database = IosSqliteFile(IOS_BACKUP_DB_FIXTURE)

    @Test
    fun readsConversationsIncludingOverflowPayloads() {
        val rows = mutableListOf<Map<String, String?>>()
        assertTrue(database.readTable("ConversationEntity") { rows.add(it) })

        assertEquals(2, rows.size)

        val small = rows.first { it["title"] == "Small chat" }
        assertEquals("c1".repeat(16), small["id"])
        assertEquals("a1".repeat(32), small["assistant_id"])
        assertEquals("1", small["is_pinned"])
        assertEquals("1700000100000", small["update_at"])
        assertNotNull(small["nodes"])

        val big = rows.first { it["title"] == "Big chat" }
        val nodes = big["nodes"].orEmpty()
        assertTrue(nodes.length > 4096, "fixture payload should span overflow pages")
        assertTrue(nodes.contains("story"))
        assertEquals("0", big["is_pinned"])
    }

    @Test
    fun parsesNodesJsonFromFixtureRows() {
        val warnings = mutableListOf<String>()
        val conversations = mutableListOf<me.rerere.lastchat.ios.IosConversation>()
        database.readTable("ConversationEntity") { row ->
            IosBackupDataImporter.mapConversationRow(row, warnings)?.let(conversations::add)
        }

        assertEquals(2, conversations.size)
        assertTrue(warnings.isEmpty())
        val big = conversations.first { it.title == "Big chat" }
        assertTrue(big.currentMessages.any { it.toText().startsWith("story") })
    }

    @Test
    fun readsMemoriesWithNullAndBlobColumns() {
        val rows = mutableListOf<Map<String, String?>>()
        assertTrue(database.readTable("MemoryEntity") { rows.add(it) })

        assertEquals(2, rows.size)
        val withEmbedding = rows.first { it["content"] == "User likes tea" }
        // INTEGER PRIMARY KEY columns store NULL and surface the rowid instead
        assertEquals("1", withEmbedding["id"])
        assertEquals("0", withEmbedding["type"])
        assertEquals("1700000000000", withEmbedding["created_at"])
        // embedding is a TEXT json array, embedding_blob is a BLOB and surfaces as null
        assertEquals("[0.1,0.2]", withEmbedding["embedding"])
        assertNull(withEmbedding["embedding_blob"])

        val blank = rows.first { it["type"] == "1" }
        assertEquals("2", blank["id"])
        assertNull(blank["embedding"])
        assertNull(blank["embedding_model_id"])
    }

    @Test
    fun rejectsNonSqliteBytes() {
        val result = runCatching { IosSqliteFile("definitely not a db".encodeToByteArray()) }
        assertTrue(result.isFailure)
    }
}
