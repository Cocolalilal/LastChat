package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity

@Dao
interface MemoryEdgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(edge: MemoryEdgeEntity)

    @Query("SELECT * FROM memory_edge WHERE from_id = :nodeId OR to_id = :nodeId")
    suspend fun getTouching(nodeId: String): List<MemoryEdgeEntity>

    @Query("SELECT * FROM memory_edge WHERE from_id = :nodeId")
    suspend fun getOutgoing(nodeId: String): List<MemoryEdgeEntity>

    @Query("SELECT * FROM memory_edge WHERE to_id = :nodeId")
    suspend fun getIncoming(nodeId: String): List<MemoryEdgeEntity>

    /** `ABOUT` edges (type 0) pointing at any of [entityIds] — the 1-hop dedup neighborhood seed. */
    @Query("SELECT * FROM memory_edge WHERE type = 0 AND to_id IN (:entityIds)")
    suspend fun getAboutEdgesForEntities(entityIds: List<String>): List<MemoryEdgeEntity>

    @Query("SELECT * FROM memory_edge WHERE from_id IN (:nodeIds) OR to_id IN (:nodeIds)")
    suspend fun getTouchingAny(nodeIds: List<String>): List<MemoryEdgeEntity>

    @Query("DELETE FROM memory_edge WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memory_edge WHERE from_id = :nodeId OR to_id = :nodeId")
    suspend fun deleteTouching(nodeId: String)

    @Query("DELETE FROM memory_edge WHERE from_id IN (:nodeIds) OR to_id IN (:nodeIds)")
    suspend fun deleteTouchingAny(nodeIds: List<String>)
}
