package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity

/** DAO for the append-only `memory_activity` feed (Memory v2, plan §4.11). */
@Dao
interface MemoryActivityDao {
    @Insert
    suspend fun insert(activity: MemoryActivityEntity)

    @Update
    suspend fun update(activity: MemoryActivityEntity)

    @Query(
        "SELECT * FROM memory_activity " +
            "WHERE scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId) " +
            "ORDER BY at DESC LIMIT :limit"
    )
    fun observeRecent(assistantId: String, limit: Int): Flow<List<MemoryActivityEntity>>

    @Query("SELECT * FROM memory_activity WHERE state = :state ORDER BY at DESC")
    suspend fun getByState(state: String): List<MemoryActivityEntity>

    /** Prune to ~[keep] most-recent rows per scope (§4.11). `IS` matches NULL for GLOBAL rows. */
    @Query(
        "DELETE FROM memory_activity WHERE id IN (" +
            "SELECT id FROM memory_activity " +
            "WHERE scope = :scope AND owner_assistant_id IS :assistantId " +
            "ORDER BY at DESC LIMIT -1 OFFSET :keep)"
    )
    suspend fun prune(scope: Int, assistantId: String?, keep: Int)
}
