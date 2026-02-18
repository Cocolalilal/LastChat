package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity

@Dao
interface MemoryEdgeDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(edge: MemoryEdgeEntity): Long

    @Update
    suspend fun update(edge: MemoryEdgeEntity)

    @Query("SELECT * FROM MemoryEdgeEntity WHERE id = :id")
    suspend fun getById(id: Int): MemoryEdgeEntity?

    @Query("SELECT * FROM MemoryEdgeEntity WHERE assistant_id = :assistantId")
    suspend fun getAllEdges(assistantId: String): List<MemoryEdgeEntity>

    @Query("SELECT * FROM MemoryEdgeEntity WHERE assistant_id = :assistantId")
    fun getAllEdgesFlow(assistantId: String): Flow<List<MemoryEdgeEntity>>

    @Query("SELECT * FROM MemoryEdgeEntity WHERE source_node_id = :nodeId OR target_node_id = :nodeId")
    suspend fun getEdgesForNode(nodeId: Int): List<MemoryEdgeEntity>

    @Query("SELECT * FROM MemoryEdgeEntity WHERE source_node_id IN (:nodeIds) OR target_node_id IN (:nodeIds)")
    suspend fun getEdgesForNodes(nodeIds: List<Int>): List<MemoryEdgeEntity>

    @Query("SELECT * FROM MemoryEdgeEntity WHERE source_node_id = :nodeId")
    suspend fun getOutgoingEdges(nodeId: Int): List<MemoryEdgeEntity>

    @Query("SELECT * FROM MemoryEdgeEntity WHERE target_node_id = :nodeId")
    suspend fun getIncomingEdges(nodeId: Int): List<MemoryEdgeEntity>

    @Query("SELECT * FROM MemoryEdgeEntity WHERE source_node_id = :sourceId AND target_node_id = :targetId AND relation_type = :relationType LIMIT 1")
    suspend fun findEdge(sourceId: Int, targetId: Int, relationType: String): MemoryEdgeEntity?

    @Query("SELECT * FROM MemoryEdgeEntity WHERE assistant_id = :assistantId AND relation_type = :relationType")
    suspend fun getByRelationType(assistantId: String, relationType: String): List<MemoryEdgeEntity>

    @Query("SELECT * FROM MemoryEdgeEntity WHERE assistant_id = :assistantId AND strength < :threshold")
    suspend fun getWeakEdges(assistantId: String, threshold: Float): List<MemoryEdgeEntity>

    @Query("SELECT COUNT(*) FROM MemoryEdgeEntity WHERE assistant_id = :assistantId")
    suspend fun getEdgeCount(assistantId: String): Int

    @Query("SELECT COUNT(*) FROM MemoryEdgeEntity WHERE assistant_id = :assistantId")
    fun getEdgeCountFlow(assistantId: String): Flow<Int>

    @Query("UPDATE MemoryEdgeEntity SET strength = :strength, last_reinforced = :time WHERE id = :id")
    suspend fun reinforceEdge(id: Int, strength: Float, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM MemoryEdgeEntity WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM MemoryEdgeEntity WHERE assistant_id = :assistantId")
    suspend fun deleteAllForAssistant(assistantId: String)

    @Query("DELETE FROM MemoryEdgeEntity WHERE strength < :threshold AND assistant_id = :assistantId")
    suspend fun deleteWeakEdges(assistantId: String, threshold: Float): Int
}
