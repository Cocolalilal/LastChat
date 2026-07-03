package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The "why does this memory exist" record for a [MemoryNodeEntity] (schema v34).
 *
 * One row is written per originating pass: the initial extraction, and again each time a REINFORCE
 * op appends new evidence. It stores a short excerpt of the source text and the model's one-line
 * rationale so wrong extractions are visible and correctable in the UI.
 */
@Entity(
    tableName = "memory_provenance",
    indices = [
        Index(value = ["node_id"]),
        Index(value = ["conversation_id"]),
    ]
)
data class MemoryProvenanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Uuid string
    @ColumnInfo(name = "node_id")
    val nodeId: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String? = null,
    @ColumnInfo(name = "message_ids")
    val messageIds: String = "[]", // JSON array of UIMessage Uuid strings; informational, no FK
    @ColumnInfo(name = "excerpt")
    val excerpt: String = "", // <=300 chars of source text
    @ColumnInfo(name = "rationale")
    val rationale: String = "", // model's one-liner: why this was saved
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
