package me.rerere.rikkahub.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HybridMemoryMigrationTest {
    private val databaseName = "hybrid-memory-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate35To36_preservesLegacyEpisodeAsColdDigestAndCreatesHybridTables() {
        helper.createDatabase(databaseName, 35).use { db -> seedLegacyEpisode(db) }

        helper.runMigrationsAndValidate(databaseName, 36, true, AppDatabase.MIGRATION_35_36).use { db ->
            db.query("SELECT summary, conversation_id, pinned, dismissed FROM memory_conversation_digest WHERE id='legacy-episode-42'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("The user adopted a cat named Luna.", cursor.getString(0))
                assertEquals("conversation-7", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
            }
            val expectedTables = setOf(
                "memory_document", "memory_document_revision", "memory_conversation_digest",
                "memory_search_row", "memory_graph_node", "memory_graph_edge",
                "memory_graph_provenance", "memory_graph_override", "memory_processing_state",
                "memory_conversion_state",
            )
            db.query("SELECT name FROM sqlite_master WHERE type IN ('table','view')").use { cursor ->
                val names = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertTrue(names.containsAll(expectedTables))
            }
        }
    }

    private fun seedLegacyEpisode(db: SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO ChatEpisodeEntity
                (id, assistant_id, content, embedding, start_time, end_time, last_accessed_at, significance, conversation_id, embedding_model_id, embedding_blob)
                VALUES (42, 'assistant-1', 'The user adopted a cat named Luna.', NULL, 1000, 2000, 2500, 8, 'conversation-7', NULL, NULL)
            """.trimIndent()
        )
    }
}
