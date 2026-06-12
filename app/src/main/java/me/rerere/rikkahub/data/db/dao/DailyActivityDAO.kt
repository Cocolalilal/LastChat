package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.DailyActivityEntity

@Dao
interface DailyActivityDAO {
    /**
     * Record activity for a specific date.
     * If the date already exists, update the message count and timestamp.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: DailyActivityEntity)
    
    /**
     * Get activity for a specific date
     */
    @Query("SELECT * FROM daily_activity WHERE date = :date")
    suspend fun getActivityForDate(date: String): DailyActivityEntity?
    
    /**
     * Get all activity dates ordered by date descending (most recent first)
     * Used to deduplicate historical activity backfills.
     */
    @Query("SELECT date FROM daily_activity ORDER BY date DESC")
    fun getAllDatesFlow(): Flow<List<String>>
    
    /**
     * Check if there is activity for a specific date
     */
    @Query("SELECT EXISTS(SELECT 1 FROM daily_activity WHERE date = :date)")
    fun hasActivityForDateFlow(date: String): Flow<Boolean>
    
    /**
     * Get activity for a specific date as a Flow
     */
    @Query("SELECT * FROM daily_activity WHERE date = :date")
    fun getActivityForDateFlow(date: String): Flow<DailyActivityEntity?>
    
    /**
     * Increment message count for a date or insert if not exists.
     *
     * Kept as INSERT OR IGNORE + UPDATE for compatibility with older/vendor SQLite builds.
     */
    @Transaction
    suspend fun recordActivity(date: String, timestamp: Long = System.currentTimeMillis()) {
        insertActivityCounterIfMissing(date, timestamp)
        incrementActivityCounter(date, timestamp)
    }

    @Query("""
        INSERT OR IGNORE INTO daily_activity (date, message_count, last_message_time)
        VALUES (:date, 0, :timestamp)
    """)
    suspend fun insertActivityCounterIfMissing(date: String, timestamp: Long)

    @Query("""
        UPDATE daily_activity
        SET
            message_count = message_count + 1,
            last_message_time = CASE
                WHEN last_message_time < :timestamp THEN :timestamp
                ELSE last_message_time
            END
        WHERE date = :date
    """)
    suspend fun incrementActivityCounter(date: String, timestamp: Long)
    
    /**
     * Get activity for the last 7 days (for weekly messages graph)
     */
    @Query("SELECT * FROM daily_activity WHERE date >= :startDate ORDER BY date ASC")
    fun getWeeklyActivityFlow(startDate: String): Flow<List<DailyActivityEntity>>
    
    /**
     * Get all activity entries (for heatmap display)
     */
    @Query("SELECT * FROM daily_activity ORDER BY date ASC")
    fun getAllActivityFlow(): Flow<List<DailyActivityEntity>>
    
    /**
     * Get the total message count across all days (persistent count)
     */
    @Query("SELECT COALESCE(SUM(message_count), 0) FROM daily_activity")
    fun getTotalMessageCountFlow(): Flow<Long>

    /**
     * Backfill/import-safe insert for historical activity reconstruction.
     * Uses OR IGNORE for broad SQLite compatibility.
     */
    @Query("""
        INSERT OR IGNORE INTO daily_activity (date, message_count, last_message_time)
        VALUES (:date, :count, :timestamp)
    """)
    suspend fun insertBackfilledActivityIfMissing(date: String, count: Int, timestamp: Long)

    /**
     * Merge backfilled activity by keeping max values.
     * Split query avoids fragile UPSERT syntax across old SQLite versions.
     */
    @Query("""
        UPDATE daily_activity
        SET
            message_count = CASE WHEN message_count < :count THEN :count ELSE message_count END,
            last_message_time = CASE WHEN last_message_time < :timestamp THEN :timestamp ELSE last_message_time END
        WHERE date = :date
    """)
    suspend fun mergeBackfilledActivity(date: String, count: Int, timestamp: Long)
}
