package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "graph_memories",
    indices = [
        Index(value = ["assistant_id", "scope_kind", "conversation_id"]),
        Index(value = ["assistant_id", "created_at"]),
        Index(value = ["content_hash"]),
    ],
)
data class GraphMemoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("scope_kind") val scopeKind: String,
    @ColumnInfo("conversation_id") val conversationId: String? = null,
    val content: String,
    @ColumnInfo("content_hash") val contentHash: String,
    @ColumnInfo("lemmatized_text") val lemmatizedText: String,
    @ColumnInfo("attributed_to") val attributedTo: String? = null,
    val origin: String,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
    @ColumnInfo("expiration_at") val expirationAt: Long? = null,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null,
    @ColumnInfo("embedding_model_id") val embeddingModelId: String? = null,
    @ColumnInfo("source_revision") val sourceRevision: String? = null,
)

@Entity(
    tableName = "graph_entities",
    indices = [
        Index(value = ["assistant_id", "scope_kind", "conversation_id"]),
        Index(value = ["assistant_id", "normalized_name"]),
    ],
)
data class GraphEntityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("scope_kind") val scopeKind: String,
    @ColumnInfo("conversation_id") val conversationId: String? = null,
    @ColumnInfo("canonical_name") val canonicalName: String,
    @ColumnInfo("normalized_name") val normalizedName: String,
    @ColumnInfo("entity_type") val entityType: String,
    @ColumnInfo("aliases_json") val aliasesJson: String = "[]",
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB)
    val embeddingBlob: ByteArray? = null,
    @ColumnInfo("embedding_model_id") val embeddingModelId: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "graph_memory_entity_links",
    primaryKeys = ["memory_id", "entity_id"],
    indices = [Index("entity_id")],
)
data class GraphMemoryEntityLinkEntity(
    @ColumnInfo("memory_id") val memoryId: String,
    @ColumnInfo("entity_id") val entityId: String,
    val confidence: Float = 1f,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(
    tableName = "graph_memory_sources",
    indices = [Index("memory_id"), Index("conversation_id"), Index("message_id")],
)
data class GraphMemorySourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("memory_id") val memoryId: String,
    @ColumnInfo("source_type") val sourceType: String,
    @ColumnInfo("source_id") val sourceId: String? = null,
    @ColumnInfo("conversation_id") val conversationId: String? = null,
    @ColumnInfo("message_id") val messageId: String? = null,
    val speaker: String? = null,
    val excerpt: String,
    @ColumnInfo("observed_at") val observedAt: Long,
)

@Entity(tableName = "graph_memory_history", indices = [Index("memory_id")])
data class GraphMemoryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("memory_id") val memoryId: String,
    val event: String,
    @ColumnInfo("previous_content") val previousContent: String? = null,
    @ColumnInfo("new_content") val newContent: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    val actor: String? = null,
)

@Entity(tableName = "memory_engine_state", primaryKeys = ["assistant_id", "engine_id"])
data class MemoryEngineStateEntity(
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("engine_id") val engineId: String,
    val status: String = "READY",
    @ColumnInfo("last_synced_at") val lastSyncedAt: Long = 0,
    @ColumnInfo("source_watermark") val sourceWatermark: String? = null,
    @ColumnInfo("last_error") val lastError: String? = null,
    @ColumnInfo("last_error_model") val lastErrorModel: String? = null,
)

@Entity(
    tableName = "memory_transfer_jobs",
    indices = [Index(value = ["assistant_id", "state"])],
)
data class MemoryTransferJobEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("source_engine") val sourceEngine: String,
    @ColumnInfo("target_engine") val targetEngine: String,
    val state: String,
    val stage: String,
    val processed: Int = 0,
    val total: Int = 0,
    @ColumnInfo("checkpoint_json") val checkpointJson: String = "{}",
    @ColumnInfo("last_error") val lastError: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "memory_transfer_links",
    primaryKeys = ["assistant_id", "source_engine", "source_id", "target_engine"],
    indices = [Index(value = ["assistant_id", "target_engine", "target_id"])],
)
data class MemoryTransferLinkEntity(
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("source_engine") val sourceEngine: String,
    @ColumnInfo("source_id") val sourceId: String,
    @ColumnInfo("target_engine") val targetEngine: String,
    @ColumnInfo("target_id") val targetId: String,
    @ColumnInfo("source_hash") val sourceHash: String,
    @ColumnInfo("updated_at") val updatedAt: Long,
)

@Entity(tableName = "memory_transfer_conflicts", indices = [Index(value = ["assistant_id", "resolved"])])
data class MemoryTransferConflictEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("job_id") val jobId: String,
    @ColumnInfo("source_id") val sourceId: String,
    @ColumnInfo("target_id") val targetId: String?,
    @ColumnInfo("source_content") val sourceContent: String,
    @ColumnInfo("target_content") val targetContent: String?,
    @ColumnInfo("manual_edit") val manualEdit: Boolean,
    val resolved: Boolean = false,
    val resolution: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(
    tableName = "memory_activity",
    indices = [Index(value = ["assistant_id", "created_at"])],
)
data class MemoryActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("object_id") val objectId: String? = null,
    @ColumnInfo("object_kind") val objectKind: String? = null,
    val event: String,
    val summary: String,
    val origin: String,
    @ColumnInfo("model_id") val modelId: String? = null,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(
    tableName = "memory_suppressions",
    indices = [Index(value = ["assistant_id", "normalized_value"])],
)
data class MemorySuppressionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    val kind: String,
    @ColumnInfo("normalized_value") val normalizedValue: String,
    val reason: String,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(tableName = "session_memory_cursors")
data class SessionMemoryCursorEntity(
    @PrimaryKey @ColumnInfo("conversation_id") val conversationId: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("last_message_fingerprint") val lastMessageFingerprint: String? = null,
    @ColumnInfo("pending_count") val pendingCount: Int = 0,
    @ColumnInfo("last_queued_at") val lastQueuedAt: Long = 0,
    @ColumnInfo("last_processed_at") val lastProcessedAt: Long = 0,
)

@Entity(
    tableName = "memory_scope_messages",
    indices = [Index(value = ["assistant_id", "scope_kind", "conversation_id", "observed_at"])],
)
data class MemoryScopeMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("scope_kind") val scopeKind: String,
    @ColumnInfo("conversation_id") val conversationId: String? = null,
    val role: String,
    val content: String,
    @ColumnInfo("message_id") val messageId: String? = null,
    @ColumnInfo("observed_at") val observedAt: Long,
)

@Entity(
    tableName = "graph_embedding_cache",
    primaryKeys = ["owner_id", "owner_kind", "model_id"],
    indices = [Index("model_id")],
)
data class GraphEmbeddingCacheEntity(
    @ColumnInfo("owner_id") val ownerId: String,
    @ColumnInfo("owner_kind") val ownerKind: String,
    @ColumnInfo("model_id") val modelId: String,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray,
    @ColumnInfo("created_at") val createdAt: Long,
)
