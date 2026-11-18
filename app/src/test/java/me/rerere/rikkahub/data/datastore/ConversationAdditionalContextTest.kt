package me.rerere.rikkahub.data.datastore

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ConversationAdditionalContextTest {
    @Test fun bothPreviouslyShippedSchemasMigrateWithoutLosingChats() {
        val context = RuntimeEnvironment.getApplication()
        val relative = "schemas/me.rerere.rikkahub.data.db.AppDatabase/39.json"
        val schemaFile = File(relative).takeIf { it.exists() } ?: File("app/$relative")
        val schema = JsonInstant.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        for (version in listOf(39, 40)) {
            val name = "context-migration-$version-${Uuid.random()}"
            val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
            val id = Uuid.random().toString()
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                for (entity in schema.getValue("entities").jsonArray) {
                    val table = entity.jsonObject
                    val tableName = table.getValue("tableName").jsonPrimitive.content
                    old.execSQL(table.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tableName))
                    table["indices"]?.jsonArray?.forEach { index ->
                        old.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tableName))
                    }
                }
                schema.getValue("setupQueries").jsonArray.forEach { old.execSQL(it.jsonPrimitive.content) }
                if (version == 40) old.execSQL("CREATE INDEX index_MemoryEntity_assistant_id ON MemoryEntity(assistant_id)")
                old.execSQL("INSERT INTO ConversationEntity(id,title,nodes,create_at,update_at) VALUES(?, 'Keep this chat', '[]', 1, 2)", arrayOf(id))
                old.version = version
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_39_41, AppDatabase.MIGRATION_40_41)
                .allowMainThreadQueries().build()
            try {
                val sql = db.openHelper.writableDatabase
                assertEquals(41, sql.version)
                sql.query("SELECT title, enabled_additional_context_ids FROM ConversationEntity WHERE id = ?", arrayOf(id)).use {
                    check(it.moveToFirst())
                    assertEquals("Keep this chat", it.getString(0))
                    assertEquals("", it.getString(1)) // Existing chats inherit character assignments.
                }
                for (selection in listOf("[]", "[\"${Uuid.random()}\"]")) {
                    sql.execSQL("UPDATE ConversationEntity SET enabled_additional_context_ids = ? WHERE id = ?", arrayOf(selection, id))
                    sql.query("SELECT enabled_additional_context_ids FROM ConversationEntity WHERE id = ?", arrayOf(id)).use {
                        check(it.moveToFirst())
                        assertEquals(selection, it.getString(0))
                    }
                }
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
    }
}
