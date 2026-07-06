package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_goal` — curiosity state, deliberately *not* in the graph (Memory v2, plan §4.10).
 *
 * Goals are process state, not memory; keeping them out of the node/edge tables keeps the graph a
 * graph (lesson from attempt 1, which had GOAL nodes cluttering the store). `web_draft` holds an
 * *unconfirmed* search result that becomes memory only after the user confirms in chat (§9).
 *
 * `state` uses [MemGoalState]. `entityIds` is a JSON array string.
 */
@Entity(
    tableName = "memory_goal",
    indices = [
        Index(value = ["owner_assistant_id", "state"]),
    ]
)
data class MemoryGoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String,
    @ColumnInfo(name = "question")
    val question: String, // ≤200
    @ColumnInfo(name = "value_note", defaultValue = "")
    val valueNote: String = "", // ≤200, shown verbatim in the UI as the explanation
    @ColumnInfo(name = "entity_ids", defaultValue = "[]")
    val entityIds: String = "[]", // JSON array
    @ColumnInfo(name = "state", defaultValue = "0")
    val state: Int = MemGoalState.OPEN,
    @ColumnInfo(name = "web_draft")
    val webDraft: String? = null, // unconfirmed search result
    @ColumnInfo(name = "asked_at")
    val askedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    @ColumnInfo(name = "extra", defaultValue = "")
    val extra: String = "",
)
