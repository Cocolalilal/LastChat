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
import me.rerere.rikkahub.data.db.dao.ChatAttachmentDao
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.ConversationAttachmentRefDao
import me.rerere.rikkahub.data.db.dao.DailyActivityDAO
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheDAO
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.dao.UsageStatsDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.db.entity.ChatAttachmentEntity
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.ConversationAttachmentRefEntity
import me.rerere.rikkahub.data.db.entity.DailyActivityEntity
import me.rerere.rikkahub.data.db.entity.EmbeddingCacheEntity
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.UsageStatsEntity
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Database(
    entities = [ConversationEntity::class, MemoryEntity::class, GenMediaEntity::class, ChatEpisodeEntity::class, EmbeddingCacheEntity::class, DailyActivityEntity::class, UsageStatsEntity::class, ChatAttachmentEntity::class, ConversationAttachmentRefEntity::class, WorkspaceEntity::class],
    version = 33,
    autoMigrations = [
        AutoMigration(from = 30, to = 31),
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
        AutoMigration(from = 20, to = 21), // Adds context_summary, context_summary_up_to_index, last_prune_time, last_prune_message_count, last_refresh_time to ConversationEntity
        AutoMigration(from = 21, to = 22), // Adds DailyActivityEntity table for activity heatmap stats
        // 22->23 is manual migration (MIGRATION_22_23)
        // 23->24 is manual migration (MIGRATION_23_24) - adds usage_stats table
        // 24->25 is manual migration (MIGRATION_24_25) - adds chat attachment catalog tables
        // 25->26 is manual migration (MIGRATION_25_26) - adds is_fork to conversation table
        // 26->27 is manual migration (MIGRATION_26_27) - adds local model install registry
        // 27->28 is manual migration (MIGRATION_27_28) - adds local model progress and metadata fields
        // 28->29 is manual migration (MIGRATION_28_29) - drops removed local model install registry
        // 29->30 is manual migration (MIGRATION_29_30) - adds per-chat lorebook overrides
        // 31->32 is manual migration (MIGRATION_31_32) - adds embedding_blob columns
        // 32->33 is manual migration (MIGRATION_32_33) - adds last_model_id to conversation table
    ]
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun chatAttachmentDao(): ChatAttachmentDao

    abstract fun conversationAttachmentRefDao(): ConversationAttachmentRefDao

    abstract fun memoryDao(): MemoryDAO

    abstract fun genMediaDao(): GenMediaDAO

    abstract fun chatEpisodeDao(): ChatEpisodeDAO

    abstract fun embeddingCacheDao(): EmbeddingCacheDAO

    abstract fun dailyActivityDao(): DailyActivityDAO

    abstract fun usageStatsDao(): UsageStatsDAO

    abstract fun workspaceDao(): WorkspaceDAO

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
                // Check if table exists before altering (table is created in MIGRATION_11_12)
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='ChatEpisodeEntity'")
                val tableExists = cursor.count > 0
                cursor.close()
                if (tableExists) {
                    // Check if column already exists
                    val columnCursor = db.query("PRAGMA table_info(ChatEpisodeEntity)")
                    var hasColumn = false
                    while (columnCursor.moveToNext()) {
                        if (columnCursor.getString(1) == "last_accessed_at") {
                            hasColumn = true
                            break
                        }
                    }
                    columnCursor.close()
                    if (!hasColumn) {
                        db.execSQL("ALTER TABLE ChatEpisodeEntity ADD COLUMN last_accessed_at INTEGER NOT NULL DEFAULT 0")
                    }
                }
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

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 22 to 23")
                val cursor = db.query("SELECT id, nodes FROM ConversationEntity")

                var updateCount = 0
                db.beginTransaction()
                try {
                    val statement = db.compileStatement("UPDATE ConversationEntity SET nodes = ? WHERE id = ?")
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val nodes = cursor.getString(1)
                        val newNodes = migrateLegacyNodesJson(nodes)
                        if (newNodes != nodes) {
                            statement.bindString(1, newNodes)
                            statement.bindString(2, id)
                            statement.execute()
                            statement.clearBindings()
                            updateCount++
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                    cursor.close()
                }
                Log.i(TAG, "migrate: migrate from 22 to 23 success ($updateCount conversations updated)")
            }

            private fun migrateLegacyNodesJson(json: String): String {
                try {
                    val element = JsonInstant.parseToJsonElement(json)
                    if (element !is JsonArray) return json

                    val newArray = buildJsonArray {
                        element.jsonArray.forEach { node ->
                            if (node !is JsonObject) {
                                add(node)
                                return@forEach
                            }
                            add(buildJsonObject {
                                node.entries.forEach { (key, value) ->
                                    if (key == "messages" && value is JsonArray) {
                                        put("messages", buildJsonArray {
                                            value.jsonArray.forEach { message ->
                                                if (message !is JsonObject) {
                                                    add(message)
                                                    return@forEach
                                                }
                                                add(buildJsonObject {
                                                    message.entries.forEach { (msgKey, msgValue) ->
                                                        if (msgKey == "parts" && msgValue is JsonArray) {
                                                            put("parts", buildJsonArray {
                                                                msgValue.jsonArray.forEach { part ->
                                                                    if (part !is JsonObject) {
                                                                        add(part)
                                                                        return@forEach
                                                                    }
                                                                    val type = part["type"]?.jsonPrimitive?.content
                                                                    if (type == "me.rerere.ai.ui.UIMessagePart.Thinking") {
                                                                        add(buildJsonObject {
                                                                            put("type", "me.rerere.ai.ui.UIMessagePart.Reasoning")
                                                                            part.entries.forEach { (partKey, partValue) ->
                                                                                when (partKey) {
                                                                                    "type" -> { /* skip, already added */ }
                                                                                    "thinking" -> put("reasoning", partValue)
                                                                                    else -> put(partKey, partValue)
                                                                                }
                                                                            }
                                                                        })
                                                                    } else {
                                                                        add(part)
                                                                    }
                                                                }
                                                            })
                                                        } else {
                                                            put(msgKey, msgValue)
                                                        }
                                                    }
                                                })
                                            }
                                        })
                                    } else {
                                        put(key, value)
                                    }
                                }
                            })
                        }
                    }
                    return newArray.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return json
                }
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 23 to 24")
                // Create usage_stats table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `usage_stats` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `total_conversations` INTEGER NOT NULL DEFAULT 0,
                        `total_messages` INTEGER NOT NULL DEFAULT 0,
                        `input_tokens` INTEGER NOT NULL DEFAULT 0,
                        `output_tokens` INTEGER NOT NULL DEFAULT 0,
                        `cached_tokens` INTEGER NOT NULL DEFAULT 0,
                        `app_launches` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                // Seed initial row
                db.execSQL("INSERT OR IGNORE INTO usage_stats (id, total_conversations, total_messages, input_tokens, output_tokens, cached_tokens, app_launches) VALUES (1, 0, 0, 0, 0, 0, 0)")
                
                // Seed total_conversations from existing conversation count
                db.execSQL("UPDATE usage_stats SET total_conversations = (SELECT COUNT(*) FROM ConversationEntity) WHERE id = 1")
                
                // Seed total_messages from daily_activity if available
                db.execSQL("UPDATE usage_stats SET total_messages = COALESCE((SELECT SUM(message_count) FROM daily_activity), 0) WHERE id = 1")
                
                Log.i(TAG, "migrate: migrate from 23 to 24 success")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 24 to 25")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_attachment` (
                        `id` TEXT NOT NULL,
                        `file_path` TEXT NOT NULL DEFAULT '',
                        `display_name` TEXT NOT NULL DEFAULT '',
                        `sha256` TEXT NOT NULL,
                        `mime` TEXT NOT NULL DEFAULT '',
                        `kind` TEXT NOT NULL,
                        `size_bytes` INTEGER NOT NULL DEFAULT 0,
                        `width` INTEGER,
                        `height` INTEGER,
                        `ocr_text` TEXT,
                        `ocr_status` TEXT NOT NULL DEFAULT 'NONE',
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `deleted` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_attachment_file_path` ON `chat_attachment` (`file_path`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_attachment_sha256` ON `chat_attachment` (`sha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_attachment_kind` ON `chat_attachment` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_attachment_deleted` ON `chat_attachment` (`deleted`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversation_attachment_ref` (
                        `conversation_id` TEXT NOT NULL,
                        `attachment_id` TEXT NOT NULL,
                        PRIMARY KEY(`conversation_id`, `attachment_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_attachment_ref_attachment_id` ON `conversation_attachment_ref` (`attachment_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_attachment_ref_conversation_id` ON `conversation_attachment_ref` (`conversation_id`)")
                Log.i(TAG, "migrate: migrate from 24 to 25 success")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 25 to 26")
                db.execSQL("ALTER TABLE ConversationEntity ADD COLUMN is_fork INTEGER NOT NULL DEFAULT 0")
                Log.i(TAG, "migrate: migrate from 25 to 26 success")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 26 to 27")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_model_install` (
                        `catalog_id` TEXT NOT NULL,
                        `repo_id` TEXT NOT NULL,
                        `revision` TEXT NOT NULL,
                        `model_id` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `file_paths_json` TEXT NOT NULL DEFAULT '[]',
                        `download_size_bytes` INTEGER NOT NULL DEFAULT 0,
                        `installed_size_bytes` INTEGER NOT NULL DEFAULT 0,
                        `checksum` TEXT NOT NULL DEFAULT '',
                        `status` TEXT NOT NULL,
                        `runtime_backend` TEXT NOT NULL,
                        `supported_abis_json` TEXT NOT NULL DEFAULT '[]',
                        `min_sdk` INTEGER NOT NULL DEFAULT 0,
                        `minimum_ram_bytes` INTEGER NOT NULL DEFAULT 0,
                        `recommended_ram_bytes` INTEGER NOT NULL DEFAULT 0,
                        `delegate_info` TEXT NOT NULL DEFAULT '',
                        `safe_for_background` INTEGER NOT NULL DEFAULT 0,
                        `last_error` TEXT NOT NULL DEFAULT '',
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`catalog_id`)
                    )
                    """.trimIndent()
                )
                Log.i(TAG, "migrate: migrate from 26 to 27 success")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 27 to 28")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN entry_json TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN estimated_installed_size_bytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN provenance TEXT NOT NULL DEFAULT 'CURATED'")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN download_access TEXT NOT NULL DEFAULT 'PUBLIC'")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN current_file TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN bytes_downloaded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN bytes_total INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN progress_percent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN bytes_per_second INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN eta_seconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_model_install ADD COLUMN source_uri TEXT NOT NULL DEFAULT ''")
                Log.i(TAG, "migrate: migrate from 27 to 28 success")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 28 to 29")
                db.execSQL("DROP TABLE IF EXISTS `local_model_install`")
                Log.i(TAG, "migrate: migrate from 28 to 29 success")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 29 to 30")
                db.execSQL("ALTER TABLE ConversationEntity ADD COLUMN enabled_lorebook_ids TEXT NOT NULL DEFAULT ''")
                Log.i(TAG, "migrate: migrate from 29 to 30 success")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 31 to 32")
                db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN embedding_blob BLOB")
                db.execSQL("ALTER TABLE ChatEpisodeEntity ADD COLUMN embedding_blob BLOB")
                db.execSQL("ALTER TABLE embedding_cache ADD COLUMN embedding_blob BLOB")
                Log.i(TAG, "migrate: migrate from 31 to 32 success")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "migrate: start migrate from 32 to 33")
                db.execSQL("ALTER TABLE ConversationEntity ADD COLUMN last_model_id TEXT NOT NULL DEFAULT ''")
                Log.i(TAG, "migrate: migrate from 32 to 33 success")
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
