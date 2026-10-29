package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_summary")
data class MemorySummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "assistant_id")
    val assistantId: String,

    @ColumnInfo(name = "summary_json")
    val summaryJson: String = "",

    @ColumnInfo(name = "raw_markdown")
    val rawMarkdown: String = "",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "token_count")
    val tokenCount: Int = 0,

    @ColumnInfo(name = "source_hash")
    val sourceHash: String? = null,
)
