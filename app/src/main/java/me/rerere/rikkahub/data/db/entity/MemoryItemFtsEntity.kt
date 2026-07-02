package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * External-content FTS4 index over [MemoryItemEntity] for keyword retrieval when no
 * embedding model is configured. Room generates the sync triggers automatically.
 */
@Fts4(contentEntity = MemoryItemEntity::class)
@Entity(tableName = "memory_item_fts")
data class MemoryItemFtsEntity(
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "subject_tags")
    val subjectTags: String,
)
