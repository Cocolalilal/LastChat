package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import me.rerere.rikkahub.data.db.entity.MemoryConversationStateEntity

/** DAO for `memory_conversation_state` — the extraction watermark (Memory v2, plan §4.11, §6.1). */
@Dao
interface MemoryConversationStateDao {
    @Upsert
    suspend fun upsert(state: MemoryConversationStateEntity)

    @Query("SELECT * FROM memory_conversation_state WHERE conversation_id = :conversationId")
    suspend fun get(conversationId: String): MemoryConversationStateEntity?

    @Query("DELETE FROM memory_conversation_state WHERE conversation_id = :conversationId")
    suspend fun delete(conversationId: String)
}
