package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChatEpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("content")
    val content: String, // Summary of the episode
    @ColumnInfo("embedding")
    val embedding: String? = null,
    @ColumnInfo("start_time")
    val startTime: Long,
    @ColumnInfo("end_time")
    val endTime: Long,
    @ColumnInfo("last_accessed_at")
    val lastAccessedAt: Long = System.currentTimeMillis()
)
