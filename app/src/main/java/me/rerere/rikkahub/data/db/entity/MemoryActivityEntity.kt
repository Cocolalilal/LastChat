package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only feed backing the Memory Center activity timeline and the in-chat "memories updated"
 * pill (schema v34). Every extraction/sleep/import/wipe action lands a row here.
 *
 * Rows with a non-null [state] are suggestion rows (e.g. a scope-promotion chip) whose lifecycle is
 * pending → accepted/dismissed/expired. Pruned to ~500 rows per scope by the sleep pass.
 */
@Entity(
    tableName = "memory_activity",
    indices = [
        Index(value = ["scope", "at"]),
        Index(value = ["owner_assistant_id", "at"]),
    ]
)
data class MemoryActivityEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Uuid string
    @ColumnInfo(name = "at")
    val at: Long,
    @ColumnInfo(name = "scope")
    val scope: Int, // MemScope
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String? = null,
    @ColumnInfo(name = "kind")
    val kind: String, // MemActivityKind
    @ColumnInfo(name = "summary")
    val summary: String = "",
    @ColumnInfo(name = "node_ids")
    val nodeIds: String = "[]", // JSON array of node id strings
    @ColumnInfo(name = "conversation_id")
    val conversationId: String? = null,
    @ColumnInfo(name = "state")
    val state: String? = null, // MemActivityState for suggestion rows, else null
)
