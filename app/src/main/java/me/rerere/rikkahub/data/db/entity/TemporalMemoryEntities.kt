package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Memory v3 stores beliefs as temporal claims. The original conversation remains the evidence;
 * claims are a compact, replaceable projection over that evidence.
 */
@Entity(
    tableName = "memory_claim",
    indices = [
        Index(value = ["assistant_id", "status", "valid_from"]),
        Index(value = ["assistant_id", "subject", "predicate", "status"]),
        Index(value = ["source_conversation_id", "source_message_id"]),
        Index(value = ["legacy_memory_id"], unique = true),
        Index(value = ["embedding_model_id"]),
    ],
)
data class MemoryClaimEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    val subject: String = "user",
    val predicate: String = "notes",
    @ColumnInfo("object_value")
    val objectValue: String? = null,
    val statement: String,
    val kind: Int = MemoryClaimKind.DURATIVE,
    val reality: Int = MemoryReality.REAL,
    val frame: String? = null,
    val status: Int = MemoryClaimStatus.ACTIVE,
    val confidence: Float = 1f,
    val importance: Int = 3,
    val sensitivity: Int = MemorySensitivity.NORMAL,
    @ColumnInfo("valid_from")
    val validFrom: Long? = null,
    @ColumnInfo("valid_until")
    val validUntil: Long? = null,
    @ColumnInfo("observed_at")
    val observedAt: Long,
    @ColumnInfo("recorded_at")
    val recordedAt: Long,
    @ColumnInfo("last_confirmed_at")
    val lastConfirmedAt: Long = observedAt,
    @ColumnInfo("last_accessed_at")
    val lastAccessedAt: Long = recordedAt,
    @ColumnInfo("times_reinforced")
    val timesReinforced: Int = 0,
    @ColumnInfo("times_retrieved")
    val timesRetrieved: Int = 0,
    val source: Int = MemorySourceKind.EXTRACTED,
    @ColumnInfo("source_conversation_id")
    val sourceConversationId: String? = null,
    @ColumnInfo("source_message_id")
    val sourceMessageId: String? = null,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null,
    @ColumnInfo("embedding_model_id")
    val embeddingModelId: String? = null,
    @ColumnInfo("legacy_memory_id")
    val legacyMemoryId: Int? = null,
)

@Fts4
@Entity(tableName = "memory_claim_fts")
data class MemoryClaimFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val statement: String,
    val subject: String,
    val predicate: String,
    val objectValue: String,
)

@Entity(
    tableName = "memory_episode_v3",
    indices = [
        Index(value = ["assistant_id", "status", "event_start"]),
        Index(value = ["conversation_id", "scene_key"], unique = true),
        Index(value = ["embedding_model_id"]),
    ],
)
data class MemoryEpisodeV3Entity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    val title: String,
    val summary: String,
    @ColumnInfo("event_start")
    val eventStart: Long,
    @ColumnInfo("event_end")
    val eventEnd: Long? = null,
    val reality: Int = MemoryReality.REAL,
    val frame: String? = null,
    val importance: Int = 3,
    val status: Int = MemoryClaimStatus.ACTIVE,
    val confidence: Float = 1f,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("scene_key")
    val sceneKey: String,
    @ColumnInfo("source_message_ids")
    val sourceMessageIds: String,
    @ColumnInfo("recorded_at")
    val recordedAt: Long,
    @ColumnInfo("last_accessed_at")
    val lastAccessedAt: Long = recordedAt,
    @ColumnInfo("times_retrieved")
    val timesRetrieved: Int = 0,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null,
    @ColumnInfo("embedding_model_id")
    val embeddingModelId: String? = null,
    @ColumnInfo("legacy_episode_id")
    val legacyEpisodeId: Int? = null,
)

@Fts4
@Entity(tableName = "memory_episode_v3_fts")
data class MemoryEpisodeV3FtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val title: String,
    val summary: String,
)

@Entity(
    tableName = "memory_source_v3",
    indices = [
        Index(value = ["assistant_id", "conversation_id", "observed_at"]),
        Index(value = ["message_id"], unique = true),
    ],
)
data class MemorySourceV3Entity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("message_id")
    val messageId: String,
    val role: Int,
    val excerpt: String,
    @ColumnInfo("content_hash")
    val contentHash: String,
    @ColumnInfo("observed_at")
    val observedAt: Long,
    @ColumnInfo("selected_branch")
    val selectedBranch: Boolean = true,
)

@Fts4
@Entity(tableName = "memory_source_v3_fts")
data class MemorySourceV3FtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val excerpt: String,
)

@Entity(tableName = "memory_ingest_state")
data class MemoryIngestStateEntity(
    @PrimaryKey
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("last_message_id")
    val lastMessageId: String? = null,
    @ColumnInfo("last_message_hash")
    val lastMessageHash: String? = null,
    @ColumnInfo("last_processed_at")
    val lastProcessedAt: Long = 0,
    @ColumnInfo("schema_version")
    val schemaVersion: Int = 1,
)

@Entity(tableName = "memory_projection")
data class MemoryProjectionEntity(
    @PrimaryKey
    @ColumnInfo("assistant_id")
    val assistantId: String,
    val content: String,
    @ColumnInfo("source_revision")
    val sourceRevision: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

object MemoryClaimKind {
    const val DURATIVE = 0
    const val POINT = 1
    const val HABIT = 2
    const val PLAN = 3
}

object MemoryClaimStatus {
    const val PROVISIONAL = 0
    const val ACTIVE = 1
    const val SUPERSEDED = 2
    const val CLOSED = 3
    const val DORMANT = 4
}

object MemoryReality {
    const val REAL = 0
    const val FICTION = 1
    const val HYPOTHETICAL = 2
}

object MemorySensitivity {
    const val NORMAL = 0
    const val SENSITIVE = 1
}

object MemorySourceKind {
    const val EXTRACTED = 0
    const val TOOL = 1
    const val MANUAL = 2
    const val IMPORTED = 3
}
