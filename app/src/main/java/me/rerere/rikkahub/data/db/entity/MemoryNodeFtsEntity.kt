package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * Standalone FTS4 index over memory node text (schema v34).
 *
 * Deliberately NOT an external-content FTS: the applier is the single writer and keeps this table
 * in sync explicitly (insert on create, delete-and-reinsert on content change, delete on removal),
 * so there are no Room-generated triggers to reason about. FTS is the primary retrieval/dedup
 * index; embeddings are only an accelerator, so a zero-config user still gets full recall.
 *
 * `node_id` is stored as a plain column (not the FTS rowid) because node ids are TEXT Uuids.
 */
@Entity(tableName = "memory_node_fts")
@Fts4
data class MemoryNodeFtsEntity(
    @ColumnInfo(name = "node_id")
    val nodeId: String,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "display_label")
    val displayLabel: String,
)
