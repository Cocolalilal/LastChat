package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryEntity

@Dao
interface MemoryDAO {
    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId")
    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun getMemoryCountOfAssistant(assistantId: String): Int

    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId ORDER BY last_accessed_at DESC, created_at DESC LIMIT :limit")
    suspend fun getMemoriesOfAssistantLimited(assistantId: String, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity WHERE id = :id")
    suspend fun getMemoryById(id: Int): MemoryEntity?

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memoryentity WHERE id = :id")
    suspend fun deleteMemory(id: Int)

    @Query("DELETE FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun deleteMemoriesOfAssistant(assistantId: String)

    @Query("DELETE FROM memoryentity WHERE assistant_id = :assistantId AND id = :id")
    suspend fun deleteById(assistantId: String, id: Int)

    @Query("UPDATE memoryentity SET content = :content WHERE assistant_id = :assistantId AND id = :id")
    suspend fun updateContentById(assistantId: String, id: Int, content: String)

    suspend fun getMemoriesByAssistant(assistantId: String): List<MemoryEntity> =
        getMemoriesOfAssistant(assistantId)

    suspend fun insert(memory: MemoryEntity): Long =
        insertMemory(memory)

    @Query(
        """
        SELECT * FROM memoryentity
        WHERE assistant_id = :assistantId AND id IN (:ids)
        ORDER BY created_at DESC
        """
    )
    suspend fun getMemoriesByIds(assistantId: String, ids: List<Int>): List<MemoryEntity>

    suspend fun getMemoriesByIdsScoped(assistantId: String, ids: List<Int>): List<MemoryEntity> =
        getMemoriesByIds(assistantId, ids)

    @Query("SELECT id FROM memoryentity WHERE assistant_id = :assistantId")
    fun getMemoryIdsFlow(assistantId: String): Flow<List<Int>>

    @Query("SELECT id FROM memoryentity WHERE assistant_id = :assistantId AND id IN (:ids)")
    suspend fun getExistingIds(assistantId: String, ids: List<Int>): List<Int>

    suspend fun filterValidMemoryIds(assistantId: String, ids: List<Int>): List<Int> =
        getExistingIds(assistantId, ids)

    @Query(
        """
        SELECT * FROM memoryentity
        WHERE assistant_id = :assistantId AND created_at > :sinceTimestamp
        ORDER BY created_at ASC
        """
    )
    suspend fun getMemoriesCreatedAfter(assistantId: String, sinceTimestamp: Long): List<MemoryEntity>
}
