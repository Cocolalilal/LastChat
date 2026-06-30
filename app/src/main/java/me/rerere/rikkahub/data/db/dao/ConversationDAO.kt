package me.rerere.rikkahub.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.repository.LightConversationEntity

data class AssistantCountResult(
    val assistantId: String,
    val count: Int
)

@Dao
interface ConversationDAO {
    @Query("SELECT * FROM conversationentity ORDER BY is_pinned DESC, update_at DESC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, is_consolidated as isConsolidated, is_fork as isFork FROM conversationentity ORDER BY update_at DESC")
    fun getAllLight(): Flow<List<LightConversationEntity>>

    @Query("SELECT * FROM conversationentity ORDER BY is_pinned DESC, update_at DESC")
    fun getAllPaging(): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE assistant_id = :assistantId ORDER BY is_pinned DESC, update_at DESC")
    fun getConversationsOfAssistant(assistantId: String): Flow<List<ConversationEntity>>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, is_consolidated as isConsolidated, is_fork as isFork FROM conversationentity WHERE assistant_id = :assistantId ORDER BY is_pinned DESC, update_at DESC")
    fun getConversationsOfAssistantPaging(assistantId: String): PagingSource<Int, LightConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE assistant_id = :assistantId ORDER BY is_pinned DESC, update_at DESC LIMIT :limit")
    suspend fun getRecentConversationsOfAssistant(assistantId: String, limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE (title LIKE '%' || :searchText || '%' OR nodes LIKE '%' || :searchText || '%') ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversations(searchText: String): Flow<List<ConversationEntity>>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, is_consolidated as isConsolidated, is_fork as isFork FROM conversationentity WHERE (title LIKE '%' || :searchText || '%' OR nodes LIKE '%' || :searchText || '%') ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversationsPaging(searchText: String): PagingSource<Int, LightConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE assistant_id = :assistantId AND (title LIKE '%' || :searchText || '%' OR nodes LIKE '%' || :searchText || '%') ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversationsOfAssistant(assistantId: String, searchText: String): Flow<List<ConversationEntity>>

    @Query("SELECT id, assistant_id as assistantId, title, is_pinned as isPinned, create_at as createAt, update_at as updateAt, is_consolidated as isConsolidated, is_fork as isFork FROM conversationentity WHERE assistant_id = :assistantId AND (title LIKE '%' || :searchText || '%' OR nodes LIKE '%' || :searchText || '%') ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversationsOfAssistantPaging(assistantId: String, searchText: String): PagingSource<Int, LightConversationEntity>

    @Query("SELECT * FROM conversationentity WHERE id = :id")
    fun getConversationFlowById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversationentity WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("DELETE FROM conversationentity")
    suspend fun deleteAll()

    @Query("SELECT * FROM conversationentity WHERE is_pinned = 1 ORDER BY update_at DESC")
    fun getPinnedConversations(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversationentity SET is_pinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: String, isPinned: Boolean)

    @Query("UPDATE conversationentity SET is_consolidated = :isConsolidated WHERE id = :id")
    suspend fun updateConsolidatedStatus(id: String, isConsolidated: Boolean)

    @Query("UPDATE conversationentity SET title = :title, update_at = :updateAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updateAt: Long)

    // Stats queries for MenuVM optimization
    @Query("SELECT COUNT(*) FROM conversationentity")
    fun getConversationCountFlow(): Flow<Int>

    @Query("SELECT DISTINCT date(create_at / 1000, 'unixepoch', 'localtime') as createDate FROM conversationentity ORDER BY createDate DESC")
    fun getDistinctCreateDatesFlow(): Flow<List<String>>

    @Query("SELECT assistant_id as assistantId, COUNT(*) as count FROM conversationentity GROUP BY assistant_id ORDER BY count DESC LIMIT 1")
    fun getMostActiveAssistantFlow(): Flow<AssistantCountResult?>

    // Get hour of day for each conversation's creation time (for time label calculation)
    @Query("SELECT CAST(strftime('%H', create_at / 1000, 'unixepoch', 'localtime') AS INTEGER) as hour FROM conversationentity")
    fun getConversationHoursFlow(): Flow<List<Int>>

    // Per-assistant chat count
    @Query("SELECT COUNT(*) FROM conversationentity WHERE assistant_id = :assistantId")
    fun getConversationCountByAssistantFlow(assistantId: String): Flow<Int>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM conversationentity
            WHERE (nodes LIKE '%"role":"USER"%' OR nodes LIKE '%"role":"user"%')
              AND (nodes LIKE '%"role":"ASSISTANT"%' OR nodes LIKE '%"role":"assistant"%')
            LIMIT 1
        )
        """
    )
    suspend fun hasUserAssistantConversation(): Boolean

    // Batch query for backfill tasks to prevent OOM
    @Query("SELECT * FROM conversationentity ORDER BY update_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getBackfillDataBatch(limit: Int, offset: Int): List<ConversationEntity>

    // Get most used model ID for an assistant using last_model_id column (no JSON parsing)
    @Query(
        """
        SELECT last_model_id FROM conversationentity
        WHERE assistant_id = :assistantId AND last_model_id IS NOT NULL AND last_model_id != ''
        GROUP BY last_model_id
        ORDER BY COUNT(*) DESC
        LIMIT 1
        """
    )
    suspend fun getMostUsedModelIdForAssistant(assistantId: String): String?
}
