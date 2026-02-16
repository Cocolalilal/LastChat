package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_node_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_node_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["source_node_id"]),
        Index(value = ["target_node_id"]),
        Index(value = ["source_node_id", "target_node_id", "relation_type"], unique = true),
    ]
)
data class MemoryEdgeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("source_node_id")
    val sourceNodeId: Int,
    @ColumnInfo("target_node_id")
    val targetNodeId: Int,
    @ColumnInfo("relation_type")
    val relationType: String, // "knows", "likes", "dislikes", "scheduled_for", "happened_at", "related_to", "feels_about", "owns", "part_of"
    @ColumnInfo("strength")
    val strength: Float = 1.0f, // 0.0-1.0, decays over time
    @ColumnInfo("description")
    val description: String = "",
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo("last_reinforced")
    val lastReinforced: Long = System.currentTimeMillis(),
    @ColumnInfo("episode_id")
    val episodeId: Int? = null,
)

object RelationType {
    const val KNOWS = "knows"
    const val LIKES = "likes"
    const val DISLIKES = "dislikes"
    const val SCHEDULED_FOR = "scheduled_for"
    const val HAPPENED_AT = "happened_at"
    const val RELATED_TO = "related_to"
    const val FEELS_ABOUT = "feels_about"
    const val OWNS = "owns"
    const val PART_OF = "part_of"
    const val SIMILAR_TO = "similar_to"
}
