package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.EmbeddingCacheEntity

data class EmbeddingCacheModelStats(
    val modelId: String,
    val count: Int,
    val estimatedBytes: Long,
)

@Dao
interface EmbeddingCacheDAO {
    
    /**
     * Get a cached embedding for a specific memory and model.
     */
    @Query("SELECT * FROM embedding_cache WHERE memory_id = :memoryId AND memory_type = :memoryType AND model_id = :modelId LIMIT 1")
    suspend fun getEmbedding(memoryId: Int, memoryType: Int, modelId: String): EmbeddingCacheEntity?
    
    /**
     * Insert or replace an embedding in the cache.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: EmbeddingCacheEntity)
    
    /**
     * Check if an embedding exists for a specific memory and model.
     */
    @Query("SELECT COUNT(*) > 0 FROM embedding_cache WHERE memory_id = :memoryId AND memory_type = :memoryType AND model_id = :modelId")
    suspend fun hasEmbedding(memoryId: Int, memoryType: Int, modelId: String): Boolean
    
    /**
     * Get all embeddings for a specific model (useful for cleanup when model is deleted).
     */
    @Query("SELECT * FROM embedding_cache WHERE model_id = :modelId")
    suspend fun getEmbeddingsByModel(modelId: String): List<EmbeddingCacheEntity>
    
    /**
     * Delete all embeddings for a specific model.
     */
    @Query("DELETE FROM embedding_cache WHERE model_id = :modelId")
    suspend fun deleteByModelId(modelId: String)
    
    /**
     * Delete all embeddings for a specific memory.
     */
    @Query("DELETE FROM embedding_cache WHERE memory_id = :memoryId AND memory_type = :memoryType")
    suspend fun deleteByMemoryId(memoryId: Int, memoryType: Int)
    
    /**
     * Count embeddings for a specific model (for statistics).
     */
    @Query("SELECT COUNT(*) FROM embedding_cache WHERE model_id = :modelId")
    suspend fun countEmbeddingsByModel(modelId: String): Int
    
    /**
     * Get all cached embeddings (for debugging).
     */
    @Query("SELECT * FROM embedding_cache")
    suspend fun getAllEmbeddings(): List<EmbeddingCacheEntity>

    @Query("SELECT model_id AS modelId, COUNT(*) AS count, COALESCE(SUM(LENGTH(embedding)), 0) AS estimatedBytes FROM embedding_cache GROUP BY model_id ORDER BY count DESC")
    suspend fun getModelStats(): List<EmbeddingCacheModelStats>

    @Query("DELETE FROM embedding_cache WHERE model_id NOT IN (:activeModelIds)")
    suspend fun deleteExceptModelIds(activeModelIds: List<String>): Int

    @Query("DELETE FROM embedding_cache")
    suspend fun deleteAllEmbeddings(): Int
}
