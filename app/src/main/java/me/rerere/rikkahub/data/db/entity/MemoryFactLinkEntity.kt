package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_fact_link` — belief-chain metadata between facts (Memory v2, plan §4.4).
 *
 * Keeps the entity graph clean: facts link entities; fact-links link *beliefs*. The UI's "history"
 * timeline walks [MemFactLinkType.SUPERSEDES] chains here; a pending [MemFactLinkType.CONTRADICTS]
 * link makes injection prefer the newer side (§7).
 *
 * `type` uses [MemFactLinkType].
 */
@Entity(
    tableName = "memory_fact_link",
    indices = [
        Index(value = ["fact_id"]),
        Index(value = ["other_fact_id"]),
    ]
)
data class MemoryFactLinkEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "fact_id")
    val factId: String,
    @ColumnInfo(name = "other_fact_id")
    val otherFactId: String,
    @ColumnInfo(name = "type")
    val type: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
