package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import me.rerere.rikkahub.data.db.entity.MemoryFrameEntity

/** DAO for `memory_frame` modes/storylines (Memory v2, plan §4.7). */
@Dao
interface MemoryFrameDao {
    @Upsert
    suspend fun upsertFrame(frame: MemoryFrameEntity)

    @Upsert
    suspend fun upsertFrames(frames: List<MemoryFrameEntity>)

    @Query("SELECT * FROM memory_frame WHERE id = :id")
    suspend fun getFrame(id: String): MemoryFrameEntity?

    @Query("SELECT * FROM memory_frame WHERE owner_assistant_id = :assistantId AND status = :status")
    suspend fun getFramesForAssistant(assistantId: String, status: Int): List<MemoryFrameEntity>

    @Query("UPDATE memory_frame SET status = :status WHERE id = :id")
    suspend fun setFrameStatus(id: String, status: Int)

    @Query("DELETE FROM memory_frame WHERE owner_assistant_id = :assistantId")
    suspend fun deleteFramesOfAssistant(assistantId: String)
}
