package me.rerere.rikkahub.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * On-device v33 → v34 migration test (§12.1, §13 test matrix).
 *
 * Builds a *real* historical v33 database from the committed exported schema, seeds a legacy
 * `MemoryEntity` row, then runs the actual `AutoMigration(33→34)` and asserts:
 *  1. the migration applies cleanly and the resulting schema validates against `34.json`
 *     (`validateDroppedTables = true`);
 *  2. the new graph-memory tables exist and are queryable;
 *  3. pre-existing legacy data survives untouched (the migration is purely additive — the legacy
 *     tables are kept read-only for the import worker, not dropped).
 *
 * This is the §13 matrix item that was previously only statically verified against the schema JSON.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migrate33To34_addsGraphMemoryTables_andKeepsLegacyData() {
        // 1. Create the v33 database and seed a legacy core memory row.
        helper.createDatabase(TEST_DB, 33).use { db ->
            db.execSQL(
                """
                INSERT INTO MemoryEntity
                    (id, assistant_id, content, embedding, embedding_blob, embedding_model_id,
                     type, last_accessed_at, created_at)
                VALUES
                    (1, '00000000-0000-0000-0000-000000000001', 'User studies CS at TU Wien',
                     NULL, NULL, '', 0, 0, 1700000000000)
                """.trimIndent()
            )
        }

        // 2. Run the real AutoMigration(33→34) and validate the schema matches 34.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 34, true)

        // 3a. The new graph-memory tables exist and are queryable (empty is fine).
        for (table in NEW_MEMORY_TABLES) {
            db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue("expected new table $table to exist after migration", cursor.moveToFirst())
                assertEquals("new table $table should start empty", 0, cursor.getInt(0))
            }
        }

        // 3b. The legacy row survived the additive migration.
        db.query("SELECT content FROM MemoryEntity WHERE id = 1").use { cursor ->
            assertTrue("legacy MemoryEntity row must survive the migration", cursor.moveToFirst())
            assertEquals("User studies CS at TU Wien", cursor.getString(0))
        }
    }

    companion object {
        private const val TEST_DB = "migration-test-appdatabase"

        /** The tables §3 adds in v34; each must exist and be queryable post-migration. */
        private val NEW_MEMORY_TABLES = listOf(
            "memory_node",
            "memory_edge",
            "memory_provenance",
            "memory_node_fts",
            "memory_activity",
            "memory_budget_ledger",
            "memory_store_meta",
            "memory_conversation_state",
        )
    }
}
