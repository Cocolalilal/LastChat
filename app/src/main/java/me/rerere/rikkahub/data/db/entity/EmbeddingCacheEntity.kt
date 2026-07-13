package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache for memory embeddings, keyed by (memoryId, memoryType, modelId).
 * This allows embeddings to persist across model switches.
 * When switching back to a previously-used model, the cached embedding is reused.
 */
@Entity(
    tableName = "embedding_cache",
    indices = [Index(value = ["memory_id", "memory_type", "model_id"], unique = true)]
)
data class EmbeddingCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "memory_id") val memoryId: Int, // positive for core, negative for episodes
    @ColumnInfo(name = "memory_type") val memoryType: Int, // 0 = CORE, 1 = EPISODIC
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "embedding") val embedding: String, // JSON list of floats
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
