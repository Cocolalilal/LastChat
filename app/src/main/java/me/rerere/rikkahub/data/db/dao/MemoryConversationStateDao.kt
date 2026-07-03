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

    @Query("SELECT extracted_up_to_index FROM memory_conversation_state WHERE conversation_id = :conversationId LIMIT 1")
    suspend fun getWatermark(conversationId: String): Int?

    /**
     * Clamp the watermark down to [maxIndex] (branch switch / regenerate below the watermark, §7.3).
     * Never raises it, so it stays a monotonic-from-below floor of what has been read.
     */
    @Query("UPDATE memory_conversation_state SET extracted_up_to_index = MIN(extracted_up_to_index, :maxIndex) WHERE conversation_id = :conversationId")
    suspend fun clampWatermark(conversationId: String, maxIndex: Int)

    /** Conversations of an assistant touched since [sinceMillis] — feeds the pending-tail digest. */
    @Query("SELECT * FROM memory_conversation_state WHERE assistant_id = :assistantId AND last_extract_at >= :sinceMillis")
    suspend fun getRecentForAssistant(assistantId: String, sinceMillis: Long): List<MemoryConversationStateEntity>

    @Query("DELETE FROM memory_conversation_state WHERE conversation_id = :conversationId")
    suspend fun delete(conversationId: String)

    @Query("DELETE FROM memory_conversation_state WHERE assistant_id = :assistantId")
    suspend fun deleteForAssistant(assistantId: String)
}
