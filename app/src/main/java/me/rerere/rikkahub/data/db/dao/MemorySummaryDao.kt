package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemorySummaryEntity

@Dao
interface MemorySummaryDao {
    @Query("SELECT * FROM memory_summary WHERE assistant_id = :assistantId")
    fun getSummaryFlow(assistantId: String): Flow<MemorySummaryEntity?>

    @Query("SELECT * FROM memory_summary WHERE assistant_id = :assistantId")
    suspend fun getSummary(assistantId: String): MemorySummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: MemorySummaryEntity)

    @Query("DELETE FROM memory_summary WHERE assistant_id = :assistantId")
    suspend fun delete(assistantId: String)
}
