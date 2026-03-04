package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.UsageStatsEntity

@Dao
interface UsageStatsDAO {
    /**
     * Get the usage stats row as a Flow (reactive updates).
     */
    @Query("SELECT * FROM usage_stats WHERE id = 1")
    fun getStatsFlow(): Flow<UsageStatsEntity?>

    /**
     * Get the usage stats row (suspend, one-shot).
     */
    @Query("SELECT * FROM usage_stats WHERE id = 1")
    suspend fun getStats(): UsageStatsEntity?

    /**
     * Initialize the stats row if it doesn't exist.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initIfEmpty(stats: UsageStatsEntity = UsageStatsEntity())

    /**
     * Increment the total conversations counter.
     */
    @Query("UPDATE usage_stats SET total_conversations = total_conversations + 1 WHERE id = 1")
    suspend fun incrementConversations()

    /**
     * Increment the total messages counter by a given amount.
     */
    @Query("UPDATE usage_stats SET total_messages = total_messages + :count WHERE id = 1")
    suspend fun incrementMessages(count: Int = 1)

    /**
     * Add token usage to the cumulative counters.
     */
    @Query("""
        UPDATE usage_stats SET 
            input_tokens = input_tokens + :inputTokens,
            output_tokens = output_tokens + :outputTokens,
            cached_tokens = cached_tokens + :cachedTokens
        WHERE id = 1
    """)
    suspend fun addTokenUsage(inputTokens: Long, outputTokens: Long, cachedTokens: Long)

    /**
     * Increment the app launches counter.
     */
    @Query("UPDATE usage_stats SET app_launches = app_launches + 1 WHERE id = 1")
    suspend fun incrementAppLaunches()

    /**
     * Overwrite core counters from a trusted backfill source.
     * app_launches is intentionally untouched.
     */
    @Query("""
        UPDATE usage_stats SET
            total_conversations = :totalConversations,
            total_messages = :totalMessages,
            input_tokens = :inputTokens,
            output_tokens = :outputTokens,
            cached_tokens = :cachedTokens
        WHERE id = 1
    """)
    suspend fun overwriteCoreStats(
        totalConversations: Long,
        totalMessages: Long,
        inputTokens: Long,
        outputTokens: Long,
        cachedTokens: Long
    )
}
