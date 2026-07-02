package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified memory store for the human-like memory system.
 *
 * A memory item is either a FACT (stable, semantic knowledge) or an EVENT (episodic,
 * anchored to a point in time), and lives in one of two scopes: SHARED_USER (facts about
 * the real user, visible to every assistant unless it opts out) or CHARACTER (private to
 * one assistant).
 *
 * Updates never overwrite content: the old row is marked SUPERSEDED and points at its
 * replacement via [supersededBy], so the full "used to X, now Y" chain stays walkable.
 */
@Entity(
    tableName = "memory_item",
    indices = [
        Index(value = ["owner_assistant_id", "status", "kind"]),
        Index(value = ["scope", "status"]),
        Index(value = ["superseded_by"]),
        Index(value = ["conversation_id"]),
    ]
)
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "scope")
    val scope: Int, // MemoryScope
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String?, // null iff scope == SHARED_USER
    @ColumnInfo(name = "kind")
    val kind: Int, // MemoryKind
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "subject_tags")
    val subjectTags: String = "[]", // JSON array of lowercase strings ("about whom/what")
    @ColumnInfo(name = "importance")
    val importance: Int = 3, // 1..5
    @ColumnInfo(name = "confidence")
    val confidence: Float = 1f, // 0..1, extraction certainty
    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false, // user-set only; never decays
    @ColumnInfo(name = "status")
    val status: Int, // MemoryItemStatus
    @ColumnInfo(name = "superseded_by")
    val supersededBy: Int? = null,
    @ColumnInfo(name = "expiry_class")
    val expiryClass: Int = MemoryExpiryClass.PERMANENT,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null, // TEMPORARY items only
    @ColumnInfo(name = "event_at")
    val eventAt: Long? = null, // EVENT items only: when it happened
    @ColumnInfo(name = "source")
    val source: Int, // MemorySource
    @ColumnInfo(name = "source_assistant_id")
    val sourceAssistantId: String? = null, // which assistant wrote it (matters for SHARED items)
    @ColumnInfo(name = "conversation_id")
    val conversationId: String? = null,
    @ColumnInfo(name = "message_ids")
    val messageIds: String = "[]", // JSON array of UIMessage Uuid strings; informational, no FK
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "last_confirmed_at")
    val lastConfirmedAt: Long,
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,
    @ColumnInfo(name = "times_reinforced")
    val timesReinforced: Int = 0,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null, // chunked float arrays, VectorUtils binary format
    @ColumnInfo(name = "embedding_model_id")
    val embeddingModelId: String? = null,
)

object MemoryScope {
    const val SHARED_USER = 0
    const val CHARACTER = 1
}

object MemoryKind {
    const val FACT = 0
    const val EVENT = 1
}

object MemoryItemStatus {
    const val ACTIVE = 0
    const val PROVISIONAL = 1 // low-confidence; activated by reinforcement
    const val DORMANT = 2 // decayed; searchable but not injected
    const val SUPERSEDED = 3 // replaced by a newer version
    const val EXPIRED = 4 // temporary item past its window
    const val FORGOTTEN = 5 // grace state before actual deletion
}

object MemorySource {
    const val EXTRACTED = 0
    const val TOOL = 1
    const val MANUAL = 2
    const val IMPORTED_CORE = 3
    const val IMPORTED_EPISODE = 4
    const val MERGED = 5
}

object MemoryExpiryClass {
    const val PERMANENT = 0
    const val TEMPORARY = 1
}
