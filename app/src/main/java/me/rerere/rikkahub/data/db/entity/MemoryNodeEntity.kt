package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single node in the human-like memory graph (schema v34).
 *
 * Nodes are append-only beliefs: nothing is ever edited in place. An "update" creates a new node
 * plus a `SUPERSEDES` [MemoryEdgeEntity]; the old node stays readable with status
 * [MemStatus.SUPERSEDED]. Deletion only ever happens via the scheduled decay/forgetting sweep.
 *
 * Every non-[MemNodeType.ENTITY] node attaches to one or more ENTITY hub nodes via `ABOUT` edges;
 * that attachment is what makes the graph the deduplication mechanism (see the dedup gate).
 */
@Entity(
    tableName = "memory_node",
    indices = [
        Index(value = ["scope", "status"]),
        Index(value = ["owner_assistant_id", "status", "type"]),
        Index(value = ["type", "status"]),
        Index(value = ["embedding_model_id"]),
    ]
)
data class MemoryNodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Uuid string, stable across export/import
    @ColumnInfo(name = "type")
    val type: Int, // MemNodeType
    @ColumnInfo(name = "scope")
    val scope: Int, // MemScope
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String? = null, // null iff scope == GLOBAL_USER
    @ColumnInfo(name = "content")
    val content: String, // canonical statement ("User studies CS at TU Wien")
    @ColumnInfo(name = "display_label")
    val displayLabel: String? = null, // short label for graph UI (entities: "TU Wien")
    @ColumnInfo(name = "importance")
    val importance: Int = 3, // 1..5, extraction-proposed, applier-clamped
    @ColumnInfo(name = "confidence")
    val confidence: Float = 1f, // 0..1
    @ColumnInfo(name = "sensitivity")
    val sensitivity: Int = MemSensitivity.NORMAL,
    @ColumnInfo(name = "status")
    val status: Int, // MemStatus
    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false, // user-set only; exempt from decay/compression
    @ColumnInfo(name = "reality")
    val reality: Int = MemReality.REAL,
    @ColumnInfo(name = "event_start")
    val eventStart: Long? = null, // EPISODE/GIST: when it happened
    @ColumnInfo(name = "event_end")
    val eventEnd: Long? = null, // null = point event
    @ColumnInfo(name = "valid_from")
    val validFrom: Long? = null, // FACT validity interval
    @ColumnInfo(name = "valid_until")
    val validUntil: Long? = null, // set = the fact ended (CLOSED), not wrong
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long, // when the system learned it
    @ColumnInfo(name = "last_confirmed_at")
    val lastConfirmedAt: Long,
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,
    @ColumnInfo(name = "times_reinforced")
    val timesReinforced: Int = 0,
    @ColumnInfo(name = "times_retrieved")
    val timesRetrieved: Int = 0,
    @ColumnInfo(name = "source")
    val source: Int, // MemSource
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null, // VectorUtils chunked-float format
    @ColumnInfo(name = "embedding_model_id")
    val embeddingModelId: String? = null,
    @ColumnInfo(name = "extra")
    val extra: String = "{}", // type-specific JSON payload (aliases, category, goal state, ...)
    /**
     * Set when this node carries an unresolved dedup-adjudication flag (§5.3 step 3): it is
     * "similar but different" to an existing node and awaits the sleep pass to decide
     * merge/supersede/coexist. Such nodes are held back from auto-promotion and injected only as
     * the newest of the flagged pair.
     */
    @ColumnInfo(name = "adjudication_pending", defaultValue = "0")
    val adjudicationPending: Boolean = false,
) {
    // Room does not need structural equality; ByteArray defaults are fine (mirrors MemoryEntity).
    override fun equals(other: Any?): Boolean = this === other || (other is MemoryNodeEntity && other.id == id)
    override fun hashCode(): Int = id.hashCode()
}
