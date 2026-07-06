package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_activity` — append-only UI feed (Memory v2, plan §4.11).
 *
 * Every applier/sleep-pass action lands here so the Memory screen can show what happened and why.
 * `callsUsed` feeds the budget-transparency label ("4 API calls today"). Pruned to ~500 rows/scope.
 *
 * `kind` uses [MemActivityKind]; suggestion rows carry a [MemActivityState] in `state`. `rowRefs`
 * is a JSON array string of affected row ids.
 */
@Entity(
    tableName = "memory_activity",
    indices = [
        Index(value = ["scope", "owner_assistant_id", "at"]),
        Index(value = ["state"]),
    ]
)
data class MemoryActivityEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "at")
    val at: Long,
    @ColumnInfo(name = "scope")
    val scope: Int,
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String? = null,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "summary", defaultValue = "")
    val summary: String = "",
    @ColumnInfo(name = "row_refs", defaultValue = "[]")
    val rowRefs: String = "[]", // JSON array
    @ColumnInfo(name = "conversation_id")
    val conversationId: String? = null,
    @ColumnInfo(name = "state")
    val state: String? = null, // pending/accepted/dismissed/expired for suggestion rows
    @ColumnInfo(name = "calls_used")
    val callsUsed: Int? = null,
)
