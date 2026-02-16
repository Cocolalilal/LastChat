package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity

@Dao
interface MemoryNodeDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: MemoryNodeEntity): Long

    @Update
    suspend fun update(node: MemoryNodeEntity)

    @Query("SELECT * FROM MemoryNodeEntity WHERE id = :id")
    suspend fun getById(id: Int): MemoryNodeEntity?

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND status != 'archived' ORDER BY last_mentioned DESC")
    suspend fun getActiveNodes(assistantId: String): List<MemoryNodeEntity>

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND status != 'archived' ORDER BY last_mentioned DESC")
    fun getActiveNodesFlow(assistantId: String): Flow<List<MemoryNodeEntity>>

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId ORDER BY last_mentioned DESC")
    fun getAllNodesFlow(assistantId: String): Flow<List<MemoryNodeEntity>>

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND node_type = :nodeType AND status != 'archived' ORDER BY last_mentioned DESC")
    suspend fun getByType(assistantId: String, nodeType: String): List<MemoryNodeEntity>

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND node_type = :nodeType AND status != 'archived' ORDER BY last_mentioned DESC")
    fun getByTypeFlow(assistantId: String, nodeType: String): Flow<List<MemoryNodeEntity>>

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND status != 'archived' ORDER BY importance DESC LIMIT :limit")
    suspend fun getTopByImportance(assistantId: String, limit: Int): List<MemoryNodeEntity>

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND LOWER(name) = LOWER(:name) AND status != 'archived' LIMIT 1")
    suspend fun findByName(assistantId: String, name: String): MemoryNodeEntity?

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND LOWER(name) LIKE '%' || LOWER(:query) || '%' AND status != 'archived'")
    suspend fun searchByName(assistantId: String, query: String): List<MemoryNodeEntity>

    @Query("SELECT * FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND embedding IS NOT NULL AND status != 'archived'")
    suspend fun getNodesWithEmbeddings(assistantId: String): List<MemoryNodeEntity>

    @Query("SELECT COUNT(*) FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND status != 'archived'")
    suspend fun getActiveNodeCount(assistantId: String): Int

    @Query("SELECT COUNT(*) FROM MemoryNodeEntity WHERE assistant_id = :assistantId AND status != 'archived'")
    fun getActiveNodeCountFlow(assistantId: String): Flow<Int>

    @Query("DELETE FROM MemoryNodeEntity WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM MemoryNodeEntity WHERE assistant_id = :assistantId")
    suspend fun deleteAllForAssistant(assistantId: String)

    @Query("UPDATE MemoryNodeEntity SET status = 'archived' WHERE id = :id")
    suspend fun archive(id: Int)

    @Query("UPDATE MemoryNodeEntity SET last_mentioned = :time, mention_count = mention_count + 1 WHERE id = :id")
    suspend fun touchNode(id: Int, time: Long = System.currentTimeMillis())
}
