package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_mention` — episode ↔ entity connectivity (Memory v2, plan §4.6).
 *
 * This is what makes episodes part of the graph (spreading activation traverses it) without giving
 * them prose edges. `weight` biases traversal toward the entities central to the scene.
 */
@Entity(
    tableName = "memory_mention",
    indices = [
        Index(value = ["episode_id"]),
        Index(value = ["entity_id"]),
    ]
)
data class MemoryMentionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "episode_id")
    val episodeId: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "weight", defaultValue = "1.0")
    val weight: Double = 1.0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
