package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeFtsEntity

/**
 * Reads and writes for [MemoryNodeEntity] plus its standalone FTS mirror.
 *
 * The applier is the only production writer; the raw upsert/delete methods are exposed so it can
 * keep node rows and the FTS index consistent inside a single transaction.
 */
@Dao
interface MemoryNodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: MemoryNodeEntity)

    @Update
    suspend fun update(node: MemoryNodeEntity)

    @Query("SELECT * FROM memory_node WHERE id = :id")
    suspend fun getById(id: String): MemoryNodeEntity?

    @Query("SELECT * FROM memory_node WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MemoryNodeEntity>

    @Query("DELETE FROM memory_node WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memory_node WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    // ----- scope-filtered reads (privacy is enforced in SQL, never in prompts) -----

    /**
     * All nodes visible to [assistantId]: its own CHARACTER-scoped nodes plus the shared
     * GLOBAL_USER layer. This is the canonical visibility predicate for every read path.
     */
    @Query(
        """
        SELECT * FROM memory_node
        WHERE status IN (:statuses)
          AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))
        """
    )
    suspend fun getVisibleWithStatuses(assistantId: String, statuses: List<Int>): List<MemoryNodeEntity>

    @Query(
        """
        SELECT * FROM memory_node
        WHERE type = :type
          AND status IN (:statuses)
          AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))
        """
    )
    suspend fun getVisibleByType(assistantId: String, type: Int, statuses: List<Int>): List<MemoryNodeEntity>

    @Query(
        """
        SELECT * FROM memory_node
        WHERE type = 0
          AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))
        """
    )
    suspend fun getVisibleEntities(assistantId: String): List<MemoryNodeEntity>

    @Query(
        """
        SELECT * FROM memory_node
        WHERE type = 2 AND status IN (:statuses)
          AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))
        ORDER BY COALESCE(event_start, recorded_at) DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentEpisodes(assistantId: String, statuses: List<Int>, limit: Int): List<MemoryNodeEntity>

    // ----- sweep / maintenance reads -----

    @Query("SELECT * FROM memory_node WHERE status = :status AND pinned = 0")
    suspend fun getByStatus(status: Int): List<MemoryNodeEntity>

    /**
     * All nodes (any scope) in the given statuses — the store-wide input to the sleep pass's
     * deterministic decay/expiry/size stages. The pass groups the result by scope and mutates each
     * group under its own per-scope write lock.
     */
    @Query("SELECT * FROM memory_node WHERE status IN (:statuses)")
    suspend fun getAllByStatuses(statuses: List<Int>): List<MemoryNodeEntity>

    /**
     * Count of live nodes flagged for dedup adjudication (§5.3 step 3). Drives the sleep pass's
     * opportunistic-sooner scheduling: a large backlog means merges/contradictions are piling up.
     */
    @Query("SELECT COUNT(*) FROM memory_node WHERE adjudication_pending = 1 AND status IN (0, 1)")
    suspend fun countPendingAdjudications(): Int

    @Query(
        "SELECT COUNT(*) FROM memory_node WHERE type != 0 AND scope = :scope AND (:ownerId IS NULL OR owner_assistant_id = :ownerId) AND status IN (1, 0)"
    )
    suspend fun countActiveNonEntity(scope: Int, ownerId: String?): Int

    @Query("SELECT * FROM memory_node WHERE embedding_model_id IS NULL OR embedding_model_id != :modelId")
    suspend fun getNeedingEmbedding(modelId: String): List<MemoryNodeEntity>

    // ----- deletion cascades (character wipe / conversation forget) -----

    @Query("SELECT id FROM memory_node WHERE scope = 1 AND owner_assistant_id = :assistantId")
    suspend fun getCharacterNodeIds(assistantId: String): List<String>

    @Query("DELETE FROM memory_node WHERE scope = 1 AND owner_assistant_id = :assistantId")
    suspend fun deleteCharacterNodes(assistantId: String)

    // ----- observation for UI -----

    @Query(
        """
        SELECT * FROM memory_node
        WHERE status IN (0, 1, 2, 4)
          AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))
        ORDER BY importance DESC, last_confirmed_at DESC
        """
    )
    fun observeVisible(assistantId: String): Flow<List<MemoryNodeEntity>>

    // ----- FTS mirror (kept in sync explicitly by the applier) -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(row: MemoryNodeFtsEntity)

    @Query("DELETE FROM memory_node_fts WHERE node_id = :nodeId")
    suspend fun deleteFtsByNode(nodeId: String)

    @Query("DELETE FROM memory_node_fts WHERE node_id IN (:nodeIds)")
    suspend fun deleteFtsByNodes(nodeIds: List<String>)

    /**
     * Node ids whose FTS content/label matches the given FTS query, filtered to visible + searchable
     * statuses. The FTS table is referenced directly (not aliased) so the SQLite `MATCH` operator
     * resolves it correctly inside the join.
     */
    @Query(
        """
        SELECT n.id FROM memory_node_fts
        JOIN memory_node AS n ON n.id = memory_node_fts.node_id
        WHERE memory_node_fts MATCH :ftsQuery
          AND n.status IN (:statuses)
          AND (n.scope = 0 OR (n.scope = 1 AND n.owner_assistant_id = :assistantId))
        LIMIT :limit
        """
    )
    suspend fun searchFts(assistantId: String, ftsQuery: String, statuses: List<Int>, limit: Int): List<String>
}
