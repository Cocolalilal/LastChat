package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.GraphEntityEntity
import me.rerere.rikkahub.data.db.entity.GraphMemoryEntity
import me.rerere.rikkahub.data.db.entity.GraphMemoryEntityLinkEntity
import me.rerere.rikkahub.data.db.entity.GraphMemoryHistoryEntity
import me.rerere.rikkahub.data.db.entity.GraphMemorySourceEntity
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryEngineStateEntity
import me.rerere.rikkahub.data.db.entity.MemorySuppressionEntity
import me.rerere.rikkahub.data.db.entity.MemoryTransferConflictEntity
import me.rerere.rikkahub.data.db.entity.MemoryTransferJobEntity
import me.rerere.rikkahub.data.db.entity.MemoryTransferLinkEntity
import me.rerere.rikkahub.data.db.entity.SessionMemoryCursorEntity
import me.rerere.rikkahub.data.db.entity.MemoryScopeMessageEntity
import me.rerere.rikkahub.data.db.entity.GraphEmbeddingCacheEntity

@Dao
interface MemoryGraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: GraphMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemories(memories: List<GraphMemoryEntity>)

    @Query("SELECT * FROM graph_memories WHERE id = :id")
    suspend fun getMemory(id: String): GraphMemoryEntity?

    @Query("""
        SELECT * FROM graph_memories
        WHERE assistant_id = :assistantId AND scope_kind = :scopeKind
        AND (:conversationId IS NULL OR conversation_id = :conversationId)
        AND (:showExpired = 1 OR expiration_at IS NULL OR expiration_at > :now)
        ORDER BY created_at DESC
    """)
    suspend fun getMemories(
        assistantId: String,
        scopeKind: String,
        conversationId: String?,
        showExpired: Boolean,
        now: Long,
    ): List<GraphMemoryEntity>

    @Query("SELECT * FROM graph_memories WHERE assistant_id = :assistantId AND scope_kind = 'ASSISTANT' ORDER BY created_at DESC")
    fun observeMemories(assistantId: String): Flow<List<GraphMemoryEntity>>

    @Query("DELETE FROM graph_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM graph_memories WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantMemories(assistantId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntity(entity: GraphEntityEntity)

    @Query("""
        SELECT * FROM graph_entities WHERE assistant_id = :assistantId AND scope_kind = :scopeKind
        AND (:conversationId IS NULL OR conversation_id = :conversationId)
        ORDER BY canonical_name COLLATE NOCASE
    """)
    suspend fun getEntities(assistantId: String, scopeKind: String, conversationId: String?): List<GraphEntityEntity>

    @Query("SELECT * FROM graph_entities WHERE assistant_id = :assistantId AND scope_kind = 'ASSISTANT' ORDER BY canonical_name COLLATE NOCASE")
    fun observeEntities(assistantId: String): Flow<List<GraphEntityEntity>>

    @Query("SELECT * FROM graph_entities WHERE id = :id")
    suspend fun getEntity(id: String): GraphEntityEntity?

    @Query("DELETE FROM graph_entities WHERE id = :id")
    suspend fun deleteEntity(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: GraphMemoryEntityLinkEntity)

    @Query("SELECT * FROM graph_memory_entity_links")
    fun observeLinks(): Flow<List<GraphMemoryEntityLinkEntity>>

    @Query("SELECT * FROM graph_memory_entity_links WHERE memory_id = :memoryId")
    suspend fun getLinksForMemory(memoryId: String): List<GraphMemoryEntityLinkEntity>

    @Query("SELECT * FROM graph_memory_entity_links WHERE entity_id = :entityId")
    suspend fun getLinksForEntity(entityId: String): List<GraphMemoryEntityLinkEntity>

    @Query("DELETE FROM graph_memory_entity_links WHERE memory_id = :memoryId")
    suspend fun deleteLinksForMemory(memoryId: String)

    @Query("DELETE FROM graph_memory_entity_links WHERE entity_id = :entityId")
    suspend fun deleteLinksForEntity(entityId: String)

    @Insert
    suspend fun insertSource(source: GraphMemorySourceEntity)

    @Query("SELECT * FROM graph_memory_sources WHERE memory_id = :memoryId ORDER BY observed_at DESC")
    suspend fun getSources(memoryId: String): List<GraphMemorySourceEntity>

    @Query("DELETE FROM graph_memory_sources WHERE memory_id = :memoryId")
    suspend fun deleteSources(memoryId: String)

    @Insert
    suspend fun insertHistory(history: GraphMemoryHistoryEntity)

    @Query("SELECT * FROM graph_memory_history WHERE memory_id = :memoryId ORDER BY created_at DESC")
    suspend fun getHistory(memoryId: String): List<GraphMemoryHistoryEntity>

    @Insert
    suspend fun insertActivity(activity: MemoryActivityEntity)

    @Query("SELECT * FROM memory_activity WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT :limit")
    fun observeActivity(assistantId: String, limit: Int = 200): Flow<List<MemoryActivityEntity>>

    @Query("SELECT COUNT(*) FROM memory_activity WHERE assistant_id = :assistantId AND model_id IS NOT NULL AND created_at >= :since")
    fun observeModelOperations(assistantId: String, since: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngineState(state: MemoryEngineStateEntity)

    @Query("SELECT * FROM memory_engine_state WHERE assistant_id = :assistantId AND engine_id = :engineId")
    fun observeEngineState(assistantId: String, engineId: String): Flow<MemoryEngineStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransferJob(job: MemoryTransferJobEntity)

    @Query("SELECT * FROM memory_transfer_jobs WHERE assistant_id = :assistantId ORDER BY updated_at DESC LIMIT 1")
    fun observeLatestTransfer(assistantId: String): Flow<MemoryTransferJobEntity?>

    @Query("SELECT * FROM memory_transfer_jobs WHERE id = :id")
    suspend fun getTransferJob(id: String): MemoryTransferJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransferLink(link: MemoryTransferLinkEntity)

    @Query("SELECT * FROM memory_transfer_links WHERE assistant_id = :assistantId AND source_engine = :sourceEngine AND target_engine = :targetEngine")
    suspend fun getTransferLinks(assistantId: String, sourceEngine: String, targetEngine: String): List<MemoryTransferLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(conflict: MemoryTransferConflictEntity)

    @Query("SELECT * FROM memory_transfer_conflicts WHERE assistant_id = :assistantId AND resolved = 0 ORDER BY created_at")
    fun observeOpenConflicts(assistantId: String): Flow<List<MemoryTransferConflictEntity>>

    @Update
    suspend fun updateConflict(conflict: MemoryTransferConflictEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuppression(suppression: MemorySuppressionEntity)

    @Query("SELECT * FROM memory_suppressions WHERE assistant_id = :assistantId")
    suspend fun getSuppressions(assistantId: String): List<MemorySuppressionEntity>

    @Query("DELETE FROM memory_suppressions WHERE assistant_id = :assistantId AND kind = 'ENTITY'")
    suspend fun clearEntitySuppressions(assistantId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessionCursor(cursor: SessionMemoryCursorEntity)

    @Query("SELECT * FROM session_memory_cursors WHERE conversation_id = :conversationId")
    suspend fun getSessionCursor(conversationId: String): SessionMemoryCursorEntity?

    @Query("DELETE FROM session_memory_cursors WHERE conversation_id = :conversationId")
    suspend fun deleteSessionCursor(conversationId: String)

    @Insert
    suspend fun insertScopeMessages(messages: List<MemoryScopeMessageEntity>)

    @Query("""
        SELECT * FROM memory_scope_messages
        WHERE assistant_id = :assistantId AND scope_kind = :scopeKind
        AND (:conversationId IS NULL OR conversation_id = :conversationId)
        ORDER BY observed_at DESC, id DESC LIMIT :limit
    """)
    suspend fun getRecentScopeMessages(
        assistantId: String,
        scopeKind: String,
        conversationId: String?,
        limit: Int = 10,
    ): List<MemoryScopeMessageEntity>

    @Query("""
        DELETE FROM memory_scope_messages WHERE id NOT IN (
            SELECT id FROM memory_scope_messages
            WHERE assistant_id = :assistantId AND scope_kind = :scopeKind
            AND (:conversationId IS NULL OR conversation_id = :conversationId)
            ORDER BY observed_at DESC, id DESC LIMIT :keep
        ) AND assistant_id = :assistantId AND scope_kind = :scopeKind
        AND (:conversationId IS NULL OR conversation_id = :conversationId)
    """)
    suspend fun trimScopeMessages(assistantId: String, scopeKind: String, conversationId: String?, keep: Int = 10)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGraphEmbedding(embedding: GraphEmbeddingCacheEntity)

    @Query("SELECT * FROM graph_embedding_cache WHERE owner_id = :ownerId AND owner_kind = :ownerKind AND model_id = :modelId")
    suspend fun getGraphEmbedding(ownerId: String, ownerKind: String, modelId: String): GraphEmbeddingCacheEntity?

    @Query("DELETE FROM graph_embedding_cache WHERE owner_id = :ownerId")
    suspend fun deleteGraphEmbeddings(ownerId: String)

    @Transaction
    suspend fun deleteMemoryGraph(id: String) {
        deleteLinksForMemory(id)
        deleteSources(id)
        deleteMemory(id)
    }
}
