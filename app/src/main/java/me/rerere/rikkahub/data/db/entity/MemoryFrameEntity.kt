package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_frame` — modes/storylines per character (Memory v2, plan §4.7).
 *
 * Produced by the character profile generator (§8.1) or detected by extraction. Facts and episodes
 * carry `frame_id`; retrieval labels them; the graph UI clusters by them. A character without mode
 * structure gets a single default frame and the system degrades to frameless gracefully.
 *
 * `source` uses [MemFrameSource]; `status` uses [MemStatus] (frames orphaned by a profile
 * regeneration go DORMANT, never deleted — §19.7).
 */
@Entity(
    tableName = "memory_frame",
    indices = [
        Index(value = ["owner_assistant_id", "status"]),
    ]
)
data class MemoryFrameEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String,
    @ColumnInfo(name = "label")
    val label: String, // ≤40
    @ColumnInfo(name = "descriptor", defaultValue = "")
    val descriptor: String = "", // ≤200
    @ColumnInfo(name = "source", defaultValue = "0")
    val source: Int = MemFrameSource.PROFILE,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "status", defaultValue = "1")
    val status: Int = MemStatus.ACTIVE,
)
