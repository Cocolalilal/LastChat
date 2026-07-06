package me.rerere.rikkahub.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the Memory v2 store foundation migration: Room v33 → v34 (plan §16.1).
 *
 * v34 is a purely additive [androidx.room.AutoMigration] adding the §4 graph-store tables; the
 * legacy `MemoryEntity`/`ChatEpisodeEntity` tables are untouched. This test creates a v33 database
 * from the committed schema, runs the auto-migration, validates it against `34.json`, and confirms
 * the new tables materialised (including the FTS4 virtual table).
 */
@RunWith(AndroidJUnit4::class)
class MemoryGraphMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate33To34AddsMemoryGraphTables() {
        // Create the database at v33 with the last legacy schema and close it.
        helper.createDatabase(TEST_DB, 33).close()

        // Run (auto-)migrations up to 34 and validate against the exported 34.json schema.
        val db = helper.runMigrationsAndValidate(TEST_DB, 34, /* validateDroppedTables = */ true)

        try {
            val newTables = listOf(
                "memory_node",
                "memory_alias",
                "memory_fact",
                "memory_fact_link",
                "memory_episode",
                "memory_mention",
                "memory_frame",
                "memory_provenance",
                "memory_fts",
                "memory_goal",
                "memory_activity",
                "memory_budget_ledger",
                "memory_conversation_state",
                "memory_store_meta",
            )
            for (table in newTables) {
                db.query(
                    "SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name = ?",
                    arrayOf(table),
                ).use { cursor ->
                    assertTrue("Expected migrated table `$table` to exist", cursor.moveToFirst())
                }
            }

            // The legacy memory table must survive untouched.
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
                arrayOf("MemoryEntity"),
            ).use { cursor ->
                assertTrue("Legacy MemoryEntity table must survive v34", cursor.moveToFirst())
            }
        } finally {
            db.close()
        }
    }

    companion object {
        private const val TEST_DB = "memory-graph-migration-test"
    }
}
