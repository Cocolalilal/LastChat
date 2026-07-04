package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity

@Dao
interface MemoryActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: MemoryActivityEntity)

    @Update
    suspend fun update(row: MemoryActivityEntity)

    @Query("SELECT * FROM memory_activity WHERE id = :id")
    suspend fun getById(id: String): MemoryActivityEntity?

    @Query(
        """
        SELECT * FROM memory_activity
        WHERE scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId)
        ORDER BY at DESC
        LIMIT :limit
        """
    )
    fun observeVisible(assistantId: String, limit: Int): Flow<List<MemoryActivityEntity>>

    @Query("SELECT * FROM memory_activity WHERE state = :state ORDER BY at DESC")
    suspend fun getByState(state: String): List<MemoryActivityEntity>

    /**
     * Prune the activity feed for one scope down to the most recent [keep] rows. Runs during the
     * sleep pass so the table stays bounded.
     */
    @Query(
        """
        DELETE FROM memory_activity
        WHERE scope = :scope AND (:ownerId IS NULL OR owner_assistant_id = :ownerId)
          AND id NOT IN (
            SELECT id FROM memory_activity
            WHERE scope = :scope AND (:ownerId IS NULL OR owner_assistant_id = :ownerId)
            ORDER BY at DESC LIMIT :keep
          )
        """
    )
    suspend fun pruneScope(scope: Int, ownerId: String?, keep: Int)

    @Query("DELETE FROM memory_activity WHERE scope = 1 AND owner_assistant_id = :assistantId")
    suspend fun deleteForAssistant(assistantId: String)

    /** Suggestion rows (e.g. scope-promotion chips) still awaiting a user decision. */
    @Query(
        """
        SELECT * FROM memory_activity
        WHERE kind = :kind AND state = :state
          AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))
        ORDER BY at DESC
        """
    )
    suspend fun getPendingSuggestions(assistantId: String, kind: String, state: String): List<MemoryActivityEntity>

    /** Count of activity rows of a kind for a character since [since] — e.g. curiosity asks this week (§6.4). */
    @Query(
        """
        SELECT COUNT(*) FROM memory_activity
        WHERE kind = :kind AND at >= :since
          AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))
        """
    )
    suspend fun countKindSince(assistantId: String, kind: String, since: Long): Int

    /** Timestamp of the most recent row of a kind for a character, or null — drives the ≥72h ask gap. */
    @Query(
        """
        SELECT MAX(at) FROM memory_activity
        WHERE kind = :kind
          AND (scope = 0 OR (scope = 1 AND owner_assistant_id = :assistantId))
        """
    )
    suspend fun lastAtForKind(assistantId: String, kind: String): Long?

    @Query("DELETE FROM memory_activity")
    suspend fun deleteAll()
}
