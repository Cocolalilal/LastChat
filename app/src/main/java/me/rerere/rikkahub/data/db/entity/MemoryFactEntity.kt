package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `memory_fact` — the relationship edges, i.e. the semantic layer (Memory v2, plan §4.3).
 *
 * A fact is a typed `subject —predicate→ object` relationship that is *addressable* like an edge
 * **and** carries all belief metadata (bi-temporal windows, forgetting-curve stability, provenance
 * counters). Exactly one of [objectId]/[objectValue] is set. The graph UI draws it as a labeled
 * edge; the digest serializes it as a triplet line (§6.2).
 *
 * Length caps (predicate ≤32, object_value ≤80, statement ≤160) are applier-enforced. Structural
 * dedup keys off `(subject_id, predicate, object)` after entity resolution (§6.4).
 *
 * `kind` uses [MemFactKind]; `scope` uses [MemScope]; `reality` uses [MemReality]; `status` uses
 * [MemStatus]; `sensitivity` uses [MemSensitivity]; `source` uses [MemSource]; `category` uses
 * [MemCategory].
 */
@Entity(
    tableName = "memory_fact",
    indices = [
        Index(value = ["subject_id", "predicate", "status"]),
        Index(value = ["object_id"]),
        Index(value = ["scope", "status"]),
        Index(value = ["owner_assistant_id", "status"]),
        Index(value = ["frame_id"]),
        Index(value = ["embedding_model_id"]),
    ]
)
data class MemoryFactEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "subject_id")
    val subjectId: String,
    @ColumnInfo(name = "predicate")
    val predicate: String, // ≤32, normalized snake_case verb phrase
    @ColumnInfo(name = "object_id")
    val objectId: String? = null, // → memory_node; XOR object_value
    @ColumnInfo(name = "object_value")
    val objectValue: String? = null, // literal ≤80; XOR object_id
    @ColumnInfo(name = "statement")
    val statement: String, // ≤160 human rendering for FTS/UI
    @ColumnInfo(name = "kind")
    val kind: Int,
    @ColumnInfo(name = "scope")
    val scope: Int,
    @ColumnInfo(name = "owner_assistant_id")
    val ownerAssistantId: String? = null,
    @ColumnInfo(name = "reality", defaultValue = "0")
    val reality: Int = MemReality.REAL,
    @ColumnInfo(name = "frame_id")
    val frameId: String? = null,
    @ColumnInfo(name = "importance", defaultValue = "3")
    val importance: Int = 3, // 1..5
    @ColumnInfo(name = "confidence", defaultValue = "0.0")
    val confidence: Double = 0.0,
    @ColumnInfo(name = "sensitivity", defaultValue = "0")
    val sensitivity: Int = MemSensitivity.NORMAL,
    @ColumnInfo(name = "category")
    val category: String? = null, // promotion whitelist input (§10)
    @ColumnInfo(name = "status", defaultValue = "0")
    val status: Int = MemStatus.PROVISIONAL,
    @ColumnInfo(name = "pinned", defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(name = "valid_from")
    val validFrom: Long? = null, // real-world validity window (durative facts)
    @ColumnInfo(name = "valid_until")
    val validUntil: Long? = null,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long, // belief time: learned
    @ColumnInfo(name = "expired_at")
    val expiredAt: Long? = null, // belief time: belief retracted
    @ColumnInfo(name = "last_confirmed_at", defaultValue = "0")
    val lastConfirmedAt: Long,
    @ColumnInfo(name = "last_accessed_at", defaultValue = "0")
    val lastAccessedAt: Long,
    @ColumnInfo(name = "times_reinforced", defaultValue = "0")
    val timesReinforced: Int = 0,
    @ColumnInfo(name = "times_retrieved", defaultValue = "0")
    val timesRetrieved: Int = 0,
    @ColumnInfo(name = "stability", defaultValue = "0.0")
    val stability: Double = 0.0, // forgetting-curve stability in days (§6.6)
    @ColumnInfo(name = "source", defaultValue = "0")
    val source: Int = MemSource.EXTRACTED,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null, // embedding of statement
    @ColumnInfo(name = "embedding_model_id")
    val embeddingModelId: String? = null,
    @ColumnInfo(name = "extra", defaultValue = "")
    val extra: String = "", // HABIT cadence, import payload, etc.
)
