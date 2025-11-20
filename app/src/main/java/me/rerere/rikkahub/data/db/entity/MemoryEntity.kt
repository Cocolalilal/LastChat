package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MemoryEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("content")
    val content: String = "",
    @ColumnInfo("embedding")
    val embedding: String? = null, // JSON string of float array
    @ColumnInfo("type")
    val type: Int = 0, // 0: CORE, 1: EPISODIC
    @ColumnInfo("last_accessed_at")
    val lastAccessedAt: Long = System.currentTimeMillis(),
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

object MemoryType {
    const val CORE = 0
    const val EPISODIC = 1
}
