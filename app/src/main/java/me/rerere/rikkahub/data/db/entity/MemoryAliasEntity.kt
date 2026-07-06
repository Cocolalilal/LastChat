package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_alias` — alternative labels for a [MemoryNodeEntity] (Memory v2, plan §4.2).
 *
 * A dedicated table (not JSON-in-extra) because alias lookup is the first step of both entity
 * resolution and query expansion, so it must be an indexed query, not a JSON scan. The user's
 * display name and nicknames land on the `user` node; "uni" lands on "TU Wien". Aliases are
 * multilingual by design (§19.6) — the cross-language bridge for entity matching.
 *
 * `source` uses [MemSource].
 */
@Entity(
    tableName = "memory_alias",
    indices = [
        Index(value = ["normalized", "entity_id"], unique = true),
        Index(value = ["normalized", "scope", "owner_assistant_id"]),
        Index(value = ["entity_id"]),
    ]
)
data class MemoryAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "alias")
    val alias: String, // ≤64
    @ColumnInfo(name = "normalized")
    val normalized: String, // lowercased/trimmed, indexed
    @ColumnInfo(name = "scope")
    val scope: Int,
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "source", defaultValue = "0")
    val source: Int = MemSource.EXTRACTED,
)
