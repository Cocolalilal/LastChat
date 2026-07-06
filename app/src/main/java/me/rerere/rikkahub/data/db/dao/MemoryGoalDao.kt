package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import me.rerere.rikkahub.data.db.entity.MemoryGoalEntity

/** DAO for `memory_goal` — curiosity state (Memory v2, plan §4.10, §9). */
@Dao
interface MemoryGoalDao {
    @Upsert
    suspend fun upsert(goal: MemoryGoalEntity)

    @Query("SELECT * FROM memory_goal WHERE id = :id")
    suspend fun getGoal(id: String): MemoryGoalEntity?

    @Query("SELECT * FROM memory_goal WHERE owner_assistant_id = :assistantId AND state = :state")
    suspend fun getGoalsInState(assistantId: String, state: Int): List<MemoryGoalEntity>

    @Query("SELECT * FROM memory_goal WHERE owner_assistant_id = :assistantId")
    suspend fun getGoalsForAssistant(assistantId: String): List<MemoryGoalEntity>

    @Query("UPDATE memory_goal SET state = :state, resolved_at = :resolvedAt WHERE id = :id")
    suspend fun setState(id: String, state: Int, resolvedAt: Long?)

    @Query("DELETE FROM memory_goal WHERE owner_assistant_id = :assistantId")
    suspend fun deleteGoalsOfAssistant(assistantId: String)
}
