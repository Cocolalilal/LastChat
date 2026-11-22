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

    @Query("SELECT * FROM memory_summary ORDER BY updated_at DESC")
    fun getAllSummariesFlow(): Flow<List<MemorySummaryEntity>>

    @Query("SELECT * FROM memory_summary ORDER BY updated_at DESC")
    suspend fun getAllSummaries(): List<MemorySummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: MemorySummaryEntity)

    @Query("DELETE FROM memory_summary WHERE assistant_id = :assistantId")
    suspend fun delete(assistantId: String)

    @Query("SELECT * FROM memory_summary WHERE assistant_id = :assistantId")
    suspend fun getSummaryOnce(assistantId: String): MemorySummaryEntity?

    @Query(
        """
        UPDATE memory_summary
        SET summary_json = :summaryJson,
            raw_markdown = :rawMarkdown,
            updated_at = :updatedAt,
            token_count = :tokenCount,
            revision = revision + 1,
            last_manual_edit_at = :lastManualEditAt,
            dirty_since = 0,
            last_synced_at = :lastSyncedAt
        WHERE assistant_id = :assistantId AND revision = :expectedRevision
        """
    )
    suspend fun updateIfRevisionMatches(
        assistantId: String,
        summaryJson: String,
        rawMarkdown: String,
        updatedAt: Long,
        tokenCount: Int,
        expectedRevision: Long,
        lastManualEditAt: Long,
        lastSyncedAt: Long,
    ): Int

    @Query(
        """
        UPDATE memory_summary
        SET dirty_since = CASE WHEN dirty_since = 0 THEN :dirtySince ELSE dirty_since END
        WHERE assistant_id = :assistantId
        """
    )
    suspend fun markDirty(assistantId: String, dirtySince: Long)

    @Query(
        """
        UPDATE memory_summary
        SET dirty_since = 0, last_synced_at = :syncedAt
        WHERE assistant_id = :assistantId
        """
    )
    suspend fun clearDirty(assistantId: String, syncedAt: Long)

    @Query("SELECT raw_markdown FROM memory_summary WHERE assistant_id = :assistantId")
    suspend fun getRawMarkdown(assistantId: String): String?

    @Query(
        """
        UPDATE memory_summary
        SET raw_markdown = :rawMarkdown,
            updated_at = :updatedAt
        WHERE assistant_id = :assistantId
        """
    )
    suspend fun updateRawMarkdown(assistantId: String, rawMarkdown: String, updatedAt: Long)
}
