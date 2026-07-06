package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_node` — the graph nodes (Memory v2, plan §4.1; the plan calls this table `memory_entity`,
 * renamed to `memory_node` to avoid a Room codegen identifier clash with the legacy `MemoryEntity`
 * table, which stays until v35 — see AppDatabase v34 note).
 *
 * A node is a *thing* (person, place, activity, fictional prop) carrying a short label. Facts about
 * it live on [MemoryFactEntity] edges, not here. Length caps (name ≤64, summary ≤240) are enforced
 * by the applier, not the schema.
 *
 * Canonical rows: a pinned GLOBAL [MemEntityKind.USER_SELF] node (one per store) and a pinned
 * CHARACTER [MemEntityKind.CHARACTER_SELF] node per assistant are ensured on first enable, so
 * first/second-person references resolve before any resolution logic runs (§4.1, §6.4).
 *
 * `kind` uses [MemEntityKind]; `scope` uses [MemScope]; `reality` uses [MemReality]; `status` uses
 * [MemStatus] (entities only ever ACTIVE/DORMANT).
 */
@Entity(
    tableName = "memory_node",
    indices = [
        Index(value = ["scope", "status"]),
        Index(value = ["owner_assistant_id", "status", "kind"]),
        Index(value = ["embedding_model_id"]),
    ]
)
data class MemoryNodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "scope")
    val scope: Int,
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String? = null, // null iff GLOBAL_USER
    @ColumnInfo(name = "name")
    val name: String, // canonical label, ≤64 chars
    @ColumnInfo(name = "kind")
    val kind: Int,
    @ColumnInfo(name = "reality", defaultValue = "0")
    val reality: Int = MemReality.REAL,
    @ColumnInfo(name = "summary")
    val summary: String? = null, // ≤240 chars, refreshed by sleep pass; null OK
    @ColumnInfo(name = "pinned", defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(name = "status", defaultValue = "1")
    val status: Int = MemStatus.ACTIVE,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "last_accessed_at", defaultValue = "0")
    val lastAccessedAt: Long,
    @ColumnInfo(name = "times_retrieved", defaultValue = "0")
    val timesRetrieved: Int = 0,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null, // VectorUtils format (existing codec)
    @ColumnInfo(name = "embedding_model_id")
    val embeddingModelId: String? = null,
    @ColumnInfo(name = "extra", defaultValue = "")
    val extra: String = "", // kind-specific JSON payload
)
