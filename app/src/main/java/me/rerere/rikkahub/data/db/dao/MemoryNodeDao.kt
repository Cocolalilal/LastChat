package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryAliasEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity

/**
 * DAO for `memory_node` nodes and their `memory_alias` labels (Memory v2, plan §4.1–4.2).
 *
 * Scope filtering is the caller's responsibility per read path (§10):
 * `scope = GLOBAL_USER OR (scope = CHARACTER AND owner_assistant_id = :thisAssistant)`.
 */
@Dao
interface MemoryNodeDao {
    // --- entities ---

    @Upsert
    suspend fun upsertEntity(entity: MemoryNodeEntity)

    @Upsert
    suspend fun upsertEntities(entities: List<MemoryNodeEntity>)

    @Query("SELECT * FROM memory_node WHERE id = :id")
    suspend fun getEntity(id: String): MemoryNodeEntity?

    @Query("SELECT * FROM memory_node WHERE id IN (:ids)")
    suspend fun getEntities(ids: List<String>): List<MemoryNodeEntity>

    @Query(
        "SELECT * FROM memory_node " +
            "WHERE scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId) " +
            "ORDER BY last_accessed_at DESC"
    )
    fun observeVisibleEntities(assistantId: String): Flow<List<MemoryNodeEntity>>

    @Query("UPDATE memory_node SET status = :status WHERE id = :id")
    suspend fun setEntityStatus(id: String, status: Int)

    @Delete
    suspend fun deleteEntity(entity: MemoryNodeEntity)

    @Query("DELETE FROM memory_node WHERE id = :id")
    suspend fun deleteEntityById(id: String)

    @Query("DELETE FROM memory_node WHERE scope = 1 AND owner_assistant_id = :assistantId")
    suspend fun deleteEntitiesOfAssistant(assistantId: String)

    // --- aliases ---

    @Upsert
    suspend fun upsertAlias(alias: MemoryAliasEntity)

    @Upsert
    suspend fun upsertAliases(aliases: List<MemoryAliasEntity>)

    @Query("SELECT * FROM memory_alias WHERE entity_id = :entityId")
    suspend fun getAliasesFor(entityId: String): List<MemoryAliasEntity>

    /** First step of entity resolution / query expansion: normalized alias lookup within scope. */
    @Query(
        "SELECT * FROM memory_alias " +
            "WHERE normalized = :normalized " +
            "AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))"
    )
    suspend fun findByNormalized(normalized: String, assistantId: String): List<MemoryAliasEntity>

    @Query("DELETE FROM memory_alias WHERE entity_id = :entityId")
    suspend fun deleteAliasesFor(entityId: String)

    @Query("DELETE FROM memory_alias WHERE scope = 1 AND owner_assistant_id = :assistantId")
    suspend fun deleteAliasesOfAssistant(assistantId: String)
}
