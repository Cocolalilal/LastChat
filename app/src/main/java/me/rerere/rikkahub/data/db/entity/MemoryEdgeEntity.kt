package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A directed, weighted edge between two [MemoryNodeEntity] rows (schema v34).
 *
 * `ABOUT` edges are the dedup backbone (fact/episode/habit → entity hub). `SUPERSEDES` edges chain
 * corrected beliefs; `CONTRADICTS` flags conflicts for the sleep pass; `RELATES_TO` carries generic
 * co-mention associations that feed 1-hop retrieval expansion.
 *
 * Edges are cascade-deleted with their endpoints by the applier (Room has no FKs here to avoid
 * migration coupling — the applier is the only writer and enforces referential cleanup).
 */
@Entity(
    tableName = "memory_edge",
    indices = [
        Index(value = ["from_id"]),
        Index(value = ["to_id"]),
        Index(value = ["type", "from_id"]),
    ]
)
data class MemoryEdgeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Uuid string
    @ColumnInfo(name = "from_id")
    val fromId: String,
    @ColumnInfo(name = "to_id")
    val toId: String,
    @ColumnInfo(name = "type")
    val type: Int, // MemEdgeType
    @ColumnInfo(name = "weight")
    val weight: Float = 1f,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "extra")
    val extra: String = "{}",
) {
    override fun equals(other: Any?): Boolean = this === other || (other is MemoryEdgeEntity && other.id == id)
    override fun hashCode(): Int = id.hashCode()
}
