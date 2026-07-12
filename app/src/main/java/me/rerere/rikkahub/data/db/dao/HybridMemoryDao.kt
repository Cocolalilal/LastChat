package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.*

@Dao
interface HybridMemoryDao {
    @Query("SELECT * FROM memory_document WHERE assistant_id = :assistantId ORDER BY kind")
    fun observeDocuments(assistantId: String): Flow<List<MemoryDocumentEntity>>

    @Query("SELECT * FROM memory_document WHERE assistant_id = :assistantId ORDER BY kind")
    suspend fun getDocuments(assistantId: String): List<MemoryDocumentEntity>

    @Query("SELECT * FROM memory_document WHERE assistant_id = :assistantId AND kind = :kind LIMIT 1")
    suspend fun getDocument(assistantId: String, kind: String): MemoryDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: MemoryDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: MemoryDocumentRevisionEntity)

    @Query("SELECT * FROM memory_document_revision WHERE document_id = :documentId ORDER BY revision DESC")
    fun observeRevisions(documentId: String): Flow<List<MemoryDocumentRevisionEntity>>

    @Query("SELECT * FROM memory_document_revision WHERE document_id = :documentId ORDER BY revision DESC")
    suspend fun getRevisions(documentId: String): List<MemoryDocumentRevisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreRevisions(revisions: List<MemoryDocumentRevisionEntity>)

    @Query("SELECT * FROM memory_conversation_digest WHERE assistant_id = :assistantId AND dismissed = 0 ORDER BY pinned DESC, recorded_at DESC")
    fun observeDigests(assistantId: String): Flow<List<MemoryConversationDigestEntity>>

    @Query("SELECT * FROM memory_conversation_digest WHERE assistant_id = :assistantId AND dismissed = 0 ORDER BY pinned DESC, recorded_at DESC")
    suspend fun getDigests(assistantId: String): List<MemoryConversationDigestEntity>

    @Query("SELECT * FROM memory_graph_node WHERE assistant_id = :assistantId")
    suspend fun getAllNodes(assistantId: String): List<MemoryGraphNodeEntity>

    @Query("SELECT * FROM memory_graph_edge WHERE assistant_id = :assistantId")
    suspend fun getAllEdges(assistantId: String): List<MemoryGraphEdgeEntity>

    @Query("SELECT * FROM memory_graph_provenance WHERE graph_id IN (SELECT id FROM memory_graph_node WHERE assistant_id = :assistantId) OR graph_id IN (SELECT id FROM memory_graph_edge WHERE assistant_id = :assistantId)")
    suspend fun getAllProvenance(assistantId: String): List<MemoryGraphProvenanceEntity>

    @Query("SELECT * FROM memory_graph_override WHERE assistant_id = :assistantId")
    suspend fun getAllOverrides(assistantId: String): List<MemoryGraphOverrideEntity>

    @Query("SELECT * FROM memory_graph_override WHERE assistant_id = :assistantId ORDER BY created_at")
    fun observeOverrides(assistantId: String): Flow<List<MemoryGraphOverrideEntity>>

    @Query("SELECT * FROM memory_graph_provenance WHERE graph_id = :graphId ORDER BY created_at DESC")
    suspend fun getGraphProvenance(graphId: String): List<MemoryGraphProvenanceEntity>

    @Query("SELECT * FROM memory_conversion_state WHERE assistant_id = :assistantId")
    suspend fun getConversionStates(assistantId: String): List<MemoryConversionStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDigest(digest: MemoryConversationDigestEntity)

    @Query("UPDATE memory_conversation_digest SET pinned = :pinned WHERE id = :id")
    suspend fun setDigestPinned(id: String, pinned: Boolean)

    @Query("UPDATE memory_conversation_digest SET dismissed = 1 WHERE id = :id")
    suspend fun dismissDigest(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSearchRow(row: MemorySearchRowEntity): Long

    @Query("SELECT * FROM memory_search_row WHERE source_kind = :sourceKind AND source_ref_id = :sourceRefId LIMIT 1")
    suspend fun findSearchRow(sourceKind: String, sourceRefId: String): MemorySearchRowEntity?

    @Query("UPDATE memory_search_row SET assistant_id=:assistantId, conversation_id=:conversationId, message_id=:messageId, node_id=:nodeId, version_tag=:versionTag, speaker=:speaker, frame=:frame, event_start=:eventStart, event_end=:eventEnd, recorded_at=:recordedAt, active=:active, text=:text WHERE row_id=:rowId")
    suspend fun updateSearchRow(rowId: Long, assistantId: String, conversationId: String?, messageId: String?, nodeId: String?, versionTag: String?, speaker: String?, frame: String?, eventStart: Long, eventEnd: Long, recordedAt: Long, active: Boolean, text: String)

    @Query("UPDATE memory_search_row SET active = 0 WHERE conversation_id = :conversationId AND source_kind = 'RAW_CHAT'")
    suspend fun deactivateConversationRawRows(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFts(row: MemorySearchFtsEntity)

    @Query("DELETE FROM memory_search_fts WHERE rowid = :rowId")
    suspend fun deleteFts(rowId: Long)

    @Query("DELETE FROM memory_search_row WHERE source_kind = :sourceKind AND source_ref_id = :sourceRefId")
    suspend fun deleteSearchRow(sourceKind: String, sourceRefId: String)

    @Query("SELECT memory_search_row.* FROM memory_search_row JOIN memory_search_fts ON memory_search_row.row_id = memory_search_fts.rowid WHERE memory_search_fts MATCH :match AND memory_search_row.assistant_id = :assistantId AND memory_search_row.active = 1 ORDER BY memory_search_row.recorded_at DESC LIMIT :limit")
    suspend fun search(assistantId: String, match: String, limit: Int): List<MemorySearchRowEntity>

    @Query("SELECT * FROM memory_search_row WHERE assistant_id = :assistantId AND active = 1 AND recorded_at BETWEEN :start AND :end ORDER BY recorded_at DESC LIMIT :limit")
    suspend fun searchByTime(assistantId: String, start: Long, end: Long, limit: Int): List<MemorySearchRowEntity>

    @Query("SELECT * FROM memory_search_row WHERE assistant_id = :assistantId AND active = 1 AND source_kind IN (:sourceKinds) ORDER BY recorded_at DESC LIMIT :limit")
    suspend fun recentByKinds(assistantId: String, sourceKinds: List<String>, limit: Int): List<MemorySearchRowEntity>

    @Query("SELECT * FROM memory_graph_edge WHERE assistant_id = :assistantId AND (subject_id IN (:nodeIds) OR object_id IN (:nodeIds)) LIMIT :limit")
    suspend fun getEdgesForNodes(assistantId: String, nodeIds: List<String>, limit: Int): List<MemoryGraphEdgeEntity>

    @Query("SELECT * FROM memory_search_row WHERE assistant_id = :assistantId AND source_ref_id IN (:sourceIds) AND active = 1 LIMIT :limit")
    suspend fun getSearchRowsForSources(assistantId: String, sourceIds: List<String>, limit: Int): List<MemorySearchRowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: MemoryGraphNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdge(edge: MemoryGraphEdgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvenance(provenance: MemoryGraphProvenanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: MemoryGraphOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvenance(items: List<MemoryGraphProvenanceEntity>)

    @Query("SELECT * FROM memory_graph_node WHERE assistant_id = :assistantId AND hidden = 0")
    fun observeNodes(assistantId: String): Flow<List<MemoryGraphNodeEntity>>

    @Query("SELECT * FROM memory_graph_edge WHERE assistant_id = :assistantId AND hidden = 0")
    fun observeEdges(assistantId: String): Flow<List<MemoryGraphEdgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProcessingState(state: MemoryProcessingStateEntity)

    @Query("SELECT * FROM memory_processing_state WHERE conversation_id = :conversationId")
    suspend fun getProcessingState(conversationId: String): MemoryProcessingStateEntity?

    @Query("SELECT * FROM memory_processing_state WHERE assistant_id = :assistantId ORDER BY indexed_at DESC")
    fun observeProcessingStates(assistantId: String): Flow<List<MemoryProcessingStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversionState(state: MemoryConversionStateEntity)

    @Query("SELECT * FROM memory_conversion_state WHERE assistant_id = :assistantId AND direction = :direction LIMIT 1")
    suspend fun getConversionState(assistantId: String, direction: String): MemoryConversionStateEntity?

    @Query("SELECT * FROM memory_search_row WHERE assistant_id = :assistantId AND recorded_at > :after AND active = 1 ORDER BY recorded_at DESC LIMIT :limit")
    suspend fun getChangedSearchRows(assistantId: String, after: Long, limit: Int = 200): List<MemorySearchRowEntity>

    @Query("SELECT * FROM memory_search_row WHERE assistant_id = :assistantId AND source_kind != 'RAW_CHAT' AND (embedding_model_id IS NULL OR embedding_model_id != :modelId) ORDER BY recorded_at DESC LIMIT :limit")
    suspend fun getRowsNeedingEmbedding(assistantId: String, modelId: String, limit: Int): List<MemorySearchRowEntity>

    @Query("UPDATE memory_search_row SET embedding_blob = :embedding, embedding_model_id = :modelId WHERE row_id = :rowId")
    suspend fun updateSearchEmbedding(rowId: Long, embedding: ByteArray, modelId: String)

    @Query("UPDATE memory_document SET embedding_blob = :embedding, embedding_model_id = :modelId WHERE id = :id")
    suspend fun updateDocumentEmbedding(id: String, embedding: ByteArray, modelId: String)

    @Query("UPDATE memory_conversation_digest SET embedding_blob = :embedding, embedding_model_id = :modelId WHERE id = :id")
    suspend fun updateDigestEmbedding(id: String, embedding: ByteArray, modelId: String)

    @Query("UPDATE memory_graph_node SET embedding_blob = :embedding, embedding_model_id = :modelId WHERE id = :id")
    suspend fun updateNodeEmbedding(id: String, embedding: ByteArray, modelId: String)

    @Query("UPDATE memory_graph_edge SET embedding_blob = :embedding, embedding_model_id = :modelId WHERE id = :id")
    suspend fun updateEdgeEmbedding(id: String, embedding: ByteArray, modelId: String)

    @Query("DELETE FROM memory_search_fts WHERE rowid IN (SELECT row_id FROM memory_search_row WHERE conversation_id = :conversationId AND source_kind = 'RAW_CHAT')")
    suspend fun deleteConversationRawFts(conversationId: String)

    @Query("DELETE FROM memory_search_row WHERE conversation_id = :conversationId AND source_kind = 'RAW_CHAT'")
    suspend fun deleteConversationRawRows(conversationId: String)

    @Query("UPDATE memory_conversation_digest SET source_available = 0 WHERE conversation_id = :conversationId")
    suspend fun markDigestSourceUnavailable(conversationId: String)

    @Query("UPDATE memory_graph_provenance SET source_available = 0 WHERE conversation_id = :conversationId")
    suspend fun markGraphSourceUnavailable(conversationId: String)

    @Query("DELETE FROM memory_processing_state WHERE conversation_id = :conversationId")
    suspend fun deleteConversationProcessingState(conversationId: String)

    @Transaction
    suspend fun onConversationDeleted(conversationId: String) {
        deleteConversationRawFts(conversationId)
        deleteConversationRawRows(conversationId)
        markDigestSourceUnavailable(conversationId)
        markGraphSourceUnavailable(conversationId)
        deleteConversationProcessingState(conversationId)
    }

    @Query("DELETE FROM memory_search_fts WHERE rowid IN (SELECT row_id FROM memory_search_row WHERE assistant_id = :assistantId)")
    suspend fun deleteAssistantFts(assistantId: String)

    @Query("DELETE FROM memory_search_row WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantSearchRows(assistantId: String)

    @Query("DELETE FROM memory_document_revision WHERE document_id IN (SELECT id FROM memory_document WHERE assistant_id = :assistantId)")
    suspend fun deleteAssistantRevisions(assistantId: String)

    @Query("DELETE FROM memory_document WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantDocuments(assistantId: String)

    @Query("DELETE FROM memory_conversation_digest WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantDigests(assistantId: String)

    @Query("DELETE FROM memory_graph_provenance WHERE graph_id IN (SELECT id FROM memory_graph_node WHERE assistant_id = :assistantId) OR graph_id IN (SELECT id FROM memory_graph_edge WHERE assistant_id = :assistantId)")
    suspend fun deleteAssistantProvenance(assistantId: String)

    @Query("DELETE FROM memory_graph_override WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantOverrides(assistantId: String)

    @Query("DELETE FROM memory_graph_edge WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantEdges(assistantId: String)

    @Query("DELETE FROM memory_graph_node WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantNodes(assistantId: String)

    @Query("DELETE FROM memory_processing_state WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantProcessingState(assistantId: String)

    @Query("UPDATE memory_processing_state SET processed_at = 0, last_error = NULL WHERE assistant_id = :assistantId")
    suspend fun resetAssistantProcessingState(assistantId: String)

    @Query("DELETE FROM memory_conversion_state WHERE assistant_id = :assistantId")
    suspend fun deleteAssistantConversionState(assistantId: String)

    @Transaction
    suspend fun deleteAssistantData(assistantId: String) {
        deleteAssistantFts(assistantId)
        deleteAssistantSearchRows(assistantId)
        deleteAssistantRevisions(assistantId)
        deleteAssistantDocuments(assistantId)
        deleteAssistantDigests(assistantId)
        deleteAssistantProvenance(assistantId)
        deleteAssistantOverrides(assistantId)
        deleteAssistantEdges(assistantId)
        deleteAssistantNodes(assistantId)
        deleteAssistantProcessingState(assistantId)
        deleteAssistantConversionState(assistantId)
    }
}
