package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
     * Used for streak calculation
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
     * Increment message count for a date or insert if not exists
     */
    @Query("""
        INSERT INTO daily_activity (date, message_count, last_message_time)
        VALUES (:date, 1, :timestamp)
        ON CONFLICT(date) DO UPDATE SET
            message_count = message_count + 1,
            last_message_time = :timestamp
    """)
    suspend fun recordActivity(date: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Get activity for the last 7 days (for weekly messages graph)
     */
    @Query("SELECT * FROM daily_activity WHERE date >= :startDate ORDER BY date ASC")
    fun getWeeklyActivityFlow(startDate: String): Flow<List<DailyActivityEntity>>
    
    /**
     * Insert a date without incrementing count (for migration)
     */
    @Query("""
        INSERT OR IGNORE INTO daily_activity (date, message_count, last_message_time)
        VALUES (:date, 1, :timestamp)
    """)
    suspend fun insertDateIfNotExists(date: String, timestamp: Long)
}
