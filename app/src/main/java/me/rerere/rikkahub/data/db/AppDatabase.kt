package me.rerere.rikkahub.data.db

import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheDAO
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.EmbeddingCacheEntity
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant

@Database(
    entities = [ConversationEntity::class, MemoryEntity::class, GenMediaEntity::class, ChatEpisodeEntity::class, EmbeddingCacheEntity::class],
    version = 20,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = Migration_8_9::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        // 11->12 was manual migration in companion object
        AutoMigration(from = 13, to = 14),
        // 14->16 is manual migration
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
    ]
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memoryDao(): MemoryDAO

    abstract fun genMediaDao(): GenMediaDAO

    abstract fun chatEpisodeDao(): ChatEpisodeDAO

    abstract fun embeddingCacheDao(): EmbeddingCacheDAO

    companion object {
        const val TAG = "AppDatabase"
        
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 11 to 12")
                // Add columns to MemoryEntity
                db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN embedding TEXT")
                db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN type INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN last_accessed_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")

                // Create ChatEpisodeEntity table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ChatEpisodeEntity` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `assistant_id` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `embedding` TEXT, 
                        `start_time` INTEGER NOT NULL, 
                        `end_time` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 12 to 13")
                db.execSQL("ALTER TABLE ChatEpisodeEntity ADD COLUMN last_accessed_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_16 = object : Migration(14, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 14 to 16")
                
                // 1. Handle ChatEpisodeEntity
                // Create new table with correct schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ChatEpisodeEntity_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `assistant_id` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `embedding` TEXT, 
                        `start_time` INTEGER NOT NULL, 
                        `end_time` INTEGER NOT NULL, 
                        `last_accessed_at` INTEGER NOT NULL DEFAULT 0, 
                        `significance` INTEGER NOT NULL DEFAULT 5, 
                        `conversation_id` TEXT DEFAULT ''
                    )
                    """.trimIndent()
                )

                // Check if conversation_id exists in old table
                val cursor = db.query("PRAGMA table_info(ChatEpisodeEntity)")
                var hasConversationId = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "conversation_id") {
                        hasConversationId = true
                        break
                    }
                }
                cursor.close()

                // Copy data
                if (hasConversationId) {
                    db.execSQL(
                        """
                        INSERT INTO ChatEpisodeEntity_new (id, assistant_id, content, embedding, start_time, end_time, last_accessed_at, significance, conversation_id)
                        SELECT id, assistant_id, content, embedding, start_time, end_time, last_accessed_at, significance, conversation_id FROM ChatEpisodeEntity
                        """.trimIndent()
                    )
                } else {
                    db.execSQL(
                        """
                        INSERT INTO ChatEpisodeEntity_new (id, assistant_id, content, embedding, start_time, end_time, last_accessed_at, significance)
                        SELECT id, assistant_id, content, embedding, start_time, end_time, last_accessed_at, significance FROM ChatEpisodeEntity
                        """.trimIndent()
                    )
                }

                // Drop old and rename new
                db.execSQL("DROP TABLE ChatEpisodeEntity")
                db.execSQL("ALTER TABLE ChatEpisodeEntity_new RENAME TO ChatEpisodeEntity")

                // 2. Handle MemoryEntity (Ensure columns exist)
                // We can't easily check column existence and add IF NOT EXISTS in SQLite in one go, 
                // but we can catch exceptions or check pragma.
                // Since we are migrating to 16, let's ensure the schema is correct.
                // The safest way for MemoryEntity if we suspect issues is to recreate it too, 
                // but for now let's assume it's mostly fine or just needs columns.
                // However, since we are doing a manual migration, let's be safe and check/add columns if needed.
                
                val memoryColumns = mutableListOf<String>()
                val memCursor = db.query("PRAGMA table_info(MemoryEntity)")
                while (memCursor.moveToNext()) {
                    memoryColumns.add(memCursor.getString(1))
                }
                memCursor.close()

                if (!memoryColumns.contains("type")) {
                    db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN type INTEGER NOT NULL DEFAULT 0")
                }
                if (!memoryColumns.contains("last_accessed_at")) {
                    db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN last_accessed_at INTEGER NOT NULL DEFAULT 0")
                }
                if (!memoryColumns.contains("created_at")) {
                    db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
    }
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}

val Migration_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ... (existing migration code) ...
        Log.i(AppDatabase.TAG, "migrate: start migrate from 6 to 7")
        db.beginTransaction()
        try {
            // 创建新表结构（不包含messages列）
            db.execSQL(
                """
                CREATE TABLE ConversationEntity_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    assistant_id TEXT NOT NULL DEFAULT '0950e2dc-9bd5-4801-afa3-aa887aa36b4e',
                    title TEXT NOT NULL,
                    nodes TEXT NOT NULL,
                    usage TEXT,
                    create_at INTEGER NOT NULL,
                    update_at INTEGER NOT NULL,
                    truncate_index INTEGER NOT NULL DEFAULT -1
                )
            """.trimIndent()
            )

            // 获取所有对话记录并转换数据
            val cursor =
                db.query("SELECT id, assistant_id, title, messages, usage, create_at, update_at, truncate_index FROM ConversationEntity")
            val updates = mutableListOf<Array<Any?>>()

            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val assistantId = cursor.getString(1)
                val title = cursor.getString(2)
                val messagesJson = cursor.getString(3)
                val usage = cursor.getString(4)
                val createAt = cursor.getLong(5)
                val updateAt = cursor.getLong(6)
                val truncateIndex = cursor.getInt(7)

                try {
                    // 尝试解析旧格式的消息列表 List<UIMessage>
                    val oldMessages = JsonInstant.decodeFromString<List<UIMessage>>(messagesJson)

                    // 转换为新格式 List<MessageNode>
                    val newMessages = oldMessages.map { message ->
                        MessageNode.of(message)
                    }

                    // 序列化新格式
                    val newMessagesJson = JsonInstant.encodeToString(newMessages)
                    updates.add(
                        arrayOf(
                            id,
                            assistantId,
                            title,
                            newMessagesJson,
                            usage,
                            createAt,
                            updateAt,
                            truncateIndex
                        )
                    )
                } catch (e: Exception) {
                    // 如果解析失败，可能已经是新格式或者数据损坏，跳过
                    error("Failed to migrate messages for conversation $id: ${e.message}")
                }
            }
            cursor.close()

            // 批量插入数据到新表
            updates.forEach { values ->
                db.execSQL(
                    "INSERT INTO ConversationEntity_new (id, assistant_id, title, nodes, usage, create_at, update_at, truncate_index) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    values
                )
            }

            // 删除旧表
            db.execSQL("DROP TABLE ConversationEntity")

            // 重命名新表
            db.execSQL("ALTER TABLE ConversationEntity_new RENAME TO ConversationEntity")

            db.setTransactionSuccessful()

            Log.i(AppDatabase.TAG, "migrate: migrate from 6 to 7 success (${updates.size} conversations updated)")
        } finally {
            db.endTransaction()
        }
    }
}

@DeleteColumn(tableName = "ConversationEntity", columnName = "usage")
class Migration_8_9 : AutoMigrationSpec
