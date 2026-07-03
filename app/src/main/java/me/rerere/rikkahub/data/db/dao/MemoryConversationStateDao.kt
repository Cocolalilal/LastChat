package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MemoryConversationStateEntity

@Dao
interface MemoryConversationStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: MemoryConversationStateEntity)

    @Query("SELECT * FROM memory_conversation_state WHERE conversation_id = :conversationId LIMIT 1")
    suspend fun get(conversationId: String): MemoryConversationStateEntity?

    /** The message-id anchor of the watermark; resolve to an index via [me.rerere.rikkahub.data.memory.MemoryWatermark]. */
    @Query("SELECT extracted_up_to_message_id FROM memory_conversation_state WHERE conversation_id = :conversationId LIMIT 1")
    suspend fun getAnchorMessageId(conversationId: String): String?

    /** Conversations of an assistant touched since [sinceMillis] — feeds the pending-tail digest. */
    @Query("SELECT * FROM memory_conversation_state WHERE assistant_id = :assistantId AND last_extract_at >= :sinceMillis")
    suspend fun getRecentForAssistant(assistantId: String, sinceMillis: Long): List<MemoryConversationStateEntity>

    @Query("DELETE FROM memory_conversation_state WHERE conversation_id = :conversationId")
    suspend fun delete(conversationId: String)

    @Query("DELETE FROM memory_conversation_state WHERE assistant_id = :assistantId")
    suspend fun deleteForAssistant(assistantId: String)
}
