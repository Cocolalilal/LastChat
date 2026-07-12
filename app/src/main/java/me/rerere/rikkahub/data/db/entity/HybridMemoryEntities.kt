package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "memory_document",
    indices = [Index(value = ["assistant_id", "kind"], unique = true)],
)
@Serializable data class MemoryDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    val kind: String,
    val content: String = "",
    @ColumnInfo("char_limit") val charLimit: Int = 3000,
    val revision: Long = 0,
    @ColumnInfo("updated_at") val updatedAt: Long = 0,
    @ColumnInfo("embedding", defaultValue = "") val embedding: String? = null,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray? = null,
    @ColumnInfo(name = "embedding_model_id", defaultValue = "") val embeddingModelId: String? = null,
)

@Entity(
    tableName = "memory_document_revision",
    indices = [Index(value = ["document_id", "revision"], unique = true)],
)
@Serializable data class MemoryDocumentRevisionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("document_id") val documentId: String,
    val revision: Long,
    val content: String,
    val diff: String = "",
    val reason: String = "",
    val source: String = "automatic",
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(
    tableName = "memory_conversation_digest",
    indices = [
        Index(value = ["assistant_id", "recorded_at"]),
        Index(value = ["conversation_id"], unique = true),
    ],
)
@Serializable data class MemoryConversationDigestEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("conversation_id") val conversationId: String?,
    val summary: String,
    @ColumnInfo("open_threads") val openThreads: String = "[]",
    val importance: Int = 3,
    val pinned: Boolean = false,
    val dismissed: Boolean = false,
    @ColumnInfo("source_available") val sourceAvailable: Boolean = true,
    @ColumnInfo("branch_signature") val branchSignature: String = "",
    @ColumnInfo("event_start") val eventStart: Long = 0,
    @ColumnInfo("event_end") val eventEnd: Long = 0,
    @ColumnInfo("recorded_at") val recordedAt: Long,
    @ColumnInfo("last_reinforced_at") val lastReinforcedAt: Long = 0,
    @ColumnInfo("embedding", defaultValue = "") val embedding: String? = null,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray? = null,
    @ColumnInfo(name = "embedding_model_id", defaultValue = "") val embeddingModelId: String? = null,
)

@Entity(
    tableName = "memory_search_row",
    indices = [
        Index(value = ["source_kind", "source_ref_id"], unique = true),
        Index(value = ["assistant_id", "active", "recorded_at"]),
        Index(value = ["conversation_id", "active"]),
    ],
)
@Serializable data class MemorySearchRowEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo("row_id") val rowId: Long = 0,
    @ColumnInfo("source_kind") val sourceKind: String,
    @ColumnInfo("source_ref_id") val sourceRefId: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("conversation_id") val conversationId: String? = null,
    @ColumnInfo("message_id") val messageId: String? = null,
    @ColumnInfo("node_id") val nodeId: String? = null,
    @ColumnInfo("version_tag") val versionTag: String? = null,
    val speaker: String? = null,
    val frame: String? = null,
    @ColumnInfo("event_start") val eventStart: Long = 0,
    @ColumnInfo("event_end") val eventEnd: Long = 0,
    @ColumnInfo("recorded_at") val recordedAt: Long = 0,
    val active: Boolean = true,
    val text: String,
    @ColumnInfo("embedding", defaultValue = "") val embedding: String? = null,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray? = null,
    @ColumnInfo(name = "embedding_model_id", defaultValue = "") val embeddingModelId: String? = null,
)

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"],
)
@Entity(tableName = "memory_search_fts")
@Serializable data class MemorySearchFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val text: String,
)

@Entity(
    tableName = "memory_graph_node",
    indices = [Index(value = ["assistant_id", "normalized_label"])],
)
@Serializable data class MemoryGraphNodeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    val label: String,
    @ColumnInfo("normalized_label") val normalizedLabel: String,
    val kind: String,
    val summary: String? = null,
    val hidden: Boolean = false,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray? = null,
    @ColumnInfo(name = "embedding_model_id", defaultValue = "") val embeddingModelId: String? = null,
    val frame: String = "real",
    val importance: Int = 3,
    val confidence: Float = 0.5f,
)

@Entity(
    tableName = "memory_graph_edge",
    indices = [
        Index(value = ["assistant_id", "subject_id", "predicate"]),
        Index(value = ["object_id"]),
    ],
)
@Serializable data class MemoryGraphEdgeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("subject_id") val subjectId: String,
    val predicate: String,
    @ColumnInfo("object_id") val objectId: String? = null,
    @ColumnInfo("object_value") val objectValue: String? = null,
    val statement: String,
    val confidence: Float = 0.5f,
    val importance: Int = 3,
    @ColumnInfo("event_start") val eventStart: Long = 0,
    @ColumnInfo("event_end") val eventEnd: Long = 0,
    val hidden: Boolean = false,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray? = null,
    @ColumnInfo(name = "embedding_model_id", defaultValue = "") val embeddingModelId: String? = null,
    val frame: String = "real",
)

@Entity(
    tableName = "memory_graph_provenance",
    indices = [Index(value = ["graph_kind", "graph_id"]), Index(value = ["conversation_id"])],
)
@Serializable data class MemoryGraphProvenanceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("graph_kind") val graphKind: String,
    @ColumnInfo("graph_id") val graphId: String,
    @ColumnInfo("conversation_id") val conversationId: String? = null,
    @ColumnInfo("message_id") val messageId: String? = null,
    val excerpt: String = "",
    @ColumnInfo("source_available") val sourceAvailable: Boolean = true,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(
    tableName = "memory_graph_override",
    indices = [Index(value = ["assistant_id", "target_kind", "target_id"])],
)
@Serializable data class MemoryGraphOverrideEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("target_kind") val targetKind: String,
    @ColumnInfo("target_id") val targetId: String,
    val operation: String,
    val payload: String = "{}",
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(tableName = "memory_processing_state")
@Serializable data class MemoryProcessingStateEntity(
    @PrimaryKey @ColumnInfo("conversation_id") val conversationId: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("branch_signature") val branchSignature: String = "",
    @ColumnInfo("indexed_at") val indexedAt: Long = 0,
    @ColumnInfo("processed_at") val processedAt: Long = 0,
    @ColumnInfo("last_error") val lastError: String? = null,
)

@Entity(
    tableName = "memory_conversion_state",
    primaryKeys = ["assistant_id", "direction"],
)
@Serializable data class MemoryConversionStateEntity(
    @ColumnInfo("assistant_id") val assistantId: String,
    val direction: String,
    @ColumnInfo("source_revision") val sourceRevision: Long = 0,
    @ColumnInfo("converted_at") val convertedAt: Long = 0,
)
