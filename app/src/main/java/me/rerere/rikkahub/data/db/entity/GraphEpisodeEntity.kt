package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(value = ["assistant_id", "end_time"]),
        Index(value = ["conversation_id"]),
    ]
)
data class GraphEpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("conversation_id")
    val conversationId: String? = null,
    @ColumnInfo("content")
    val content: String, // Summary text
    @ColumnInfo("significance")
    val significance: Int = 5, // 1-10
    @ColumnInfo("start_time")
    val startTime: Long,
    @ColumnInfo("end_time")
    val endTime: Long,
    @ColumnInfo("embedding")
    val embedding: String? = null,
    @ColumnInfo("embedding_model_id")
    val embeddingModelId: String? = null,
    @ColumnInfo("node_ids")
    val nodeIds: String = "[]", // JSON array of Int node IDs mentioned in this episode
    @ColumnInfo("edge_ids")
    val edgeIds: String = "[]", // JSON array of Int edge IDs created/updated in this episode
)
