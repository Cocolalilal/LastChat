package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_provenance` — the "why does this memory exist" story (Memory v2, plan §4.8).
 *
 * One row per originating pass; REINFORCE appends. The `excerpt` is a stored copy (≤300 chars) so
 * it survives message/conversation deletion (§11.2). This backs the node sheet (§14) and the
 * "Literal" chip.
 *
 * `rowKind` uses [MemProvenanceRowKind]; `classification` uses [MemClassification]. `messageIds` is
 * a JSON array string.
 */
@Entity(
    tableName = "memory_provenance",
    indices = [
        Index(value = ["row_kind", "row_id"]),
        Index(value = ["conversation_id"]),
    ]
)
data class MemoryProvenanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "row_kind")
    val rowKind: Int,
    @ColumnInfo(name = "row_id")
    val rowId: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String? = null,
    @ColumnInfo(name = "message_ids", defaultValue = "[]")
    val messageIds: String = "[]", // JSON array of message ids
    @ColumnInfo(name = "excerpt", defaultValue = "")
    val excerpt: String = "", // ≤300, stored copy
    @ColumnInfo(name = "rationale", defaultValue = "")
    val rationale: String = "", // model one-liner: why saved
    @ColumnInfo(name = "classification", defaultValue = "0")
    val classification: Int = MemClassification.LITERAL,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
