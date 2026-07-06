package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_episode` — scene-level events, the only prose-carrying nodes (Memory v2, plan §4.5).
 *
 * Episodes are always CHARACTER-scoped, scene-sized, capped (title ≤60, summary ≤400), and
 * compressible into GIST episodes under age/size pressure (§6.5). `event_start/event_end` are
 * wall-clock session time; in-fiction chronology lives in the summary text. A continuing scene
 * EXTENDs an existing episode rather than spawning a sibling (§6.3) — this is how episode spam dies.
 *
 * `reality` uses [MemReality]; `status` uses [MemStatus].
 */
@Entity(
    tableName = "memory_episode",
    indices = [
        Index(value = ["owner_assistant_id", "status", "event_start"]),
        Index(value = ["conversation_id"]),
        Index(value = ["frame_id"]),
        Index(value = ["embedding_model_id"]),
    ]
)
data class MemoryEpisodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String,
    @ColumnInfo(name = "title")
    val title: String, // ≤60
    @ColumnInfo(name = "summary")
    val summary: String, // ≤400
    @ColumnInfo(name = "frame_id")
    val frameId: String? = null,
    @ColumnInfo(name = "reality", defaultValue = "0")
    val reality: Int = MemReality.REAL,
    @ColumnInfo(name = "event_start")
    val eventStart: Long,
    @ColumnInfo(name = "event_end")
    val eventEnd: Long? = null,
    @ColumnInfo(name = "importance", defaultValue = "3")
    val importance: Int = 3,
    @ColumnInfo(name = "status", defaultValue = "1")
    val status: Int = MemStatus.ACTIVE,
    @ColumnInfo(name = "pinned", defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(name = "stability", defaultValue = "0.0")
    val stability: Double = 0.0,
    @ColumnInfo(name = "is_gist", defaultValue = "0")
    val isGist: Boolean = false, // 1 = compression product (§6.5 stage 8)
    @ColumnInfo(name = "conversation_id")
    val conversationId: String? = null, // survives its deletion
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
    @ColumnInfo(name = "last_accessed_at", defaultValue = "0")
    val lastAccessedAt: Long,
    @ColumnInfo(name = "times_retrieved", defaultValue = "0")
    val timesRetrieved: Int = 0,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null,
    @ColumnInfo(name = "embedding_model_id")
    val embeddingModelId: String? = null,
    @ColumnInfo(name = "extra", defaultValue = "")
    val extra: String = "",
)
