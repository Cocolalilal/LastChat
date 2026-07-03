package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MemoryProvenanceEntity

@Dao
interface MemoryProvenanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: MemoryProvenanceEntity)

    @Query("SELECT * FROM memory_provenance WHERE node_id = :nodeId ORDER BY created_at ASC")
    suspend fun getForNode(nodeId: String): List<MemoryProvenanceEntity>

    @Query("SELECT node_id FROM memory_provenance WHERE conversation_id = :conversationId")
    suspend fun getNodeIdsForConversation(conversationId: String): List<String>

    /** Raw `message_ids` JSON arrays for a conversation; the union of these is the processed set. */
    @Query("SELECT message_ids FROM memory_provenance WHERE conversation_id = :conversationId")
    suspend fun getMessageIdsJsonForConversation(conversationId: String): List<String>

    @Query("SELECT COUNT(*) FROM memory_provenance WHERE node_id = :nodeId")
    suspend fun countForNode(nodeId: String): Int

    /** Node ids whose provenance row count exceeds [maxRows] — candidates for the "keep first + last 3" cap (§9). */
    @Query("SELECT node_id FROM memory_provenance GROUP BY node_id HAVING COUNT(*) > :maxRows")
    suspend fun nodesExceedingProvenance(maxRows: Int): List<String>

    @Query("DELETE FROM memory_provenance WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM memory_provenance WHERE node_id = :nodeId")
    suspend fun deleteForNode(nodeId: String)

    @Query("DELETE FROM memory_provenance WHERE node_id IN (:nodeIds)")
    suspend fun deleteForNodes(nodeIds: List<String>)
}
