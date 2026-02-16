package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity

@Dao
interface TimelineEventDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TimelineEventEntity): Long

    @Update
    suspend fun update(event: TimelineEventEntity)

    @Query("SELECT * FROM TimelineEventEntity WHERE id = :id")
    suspend fun getById(id: Int): TimelineEventEntity?

    @Query("SELECT * FROM TimelineEventEntity WHERE assistant_id = :assistantId ORDER BY scheduled_at ASC")
    suspend fun getAllEvents(assistantId: String): List<TimelineEventEntity>

    @Query("SELECT * FROM TimelineEventEntity WHERE assistant_id = :assistantId ORDER BY scheduled_at ASC")
    fun getAllEventsFlow(assistantId: String): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM TimelineEventEntity WHERE assistant_id = :assistantId AND event_type = 'upcoming' AND (scheduled_at IS NULL OR scheduled_at > :now) ORDER BY scheduled_at ASC")
    suspend fun getUpcomingEvents(assistantId: String, now: Long = System.currentTimeMillis()): List<TimelineEventEntity>

    @Query("SELECT * FROM TimelineEventEntity WHERE assistant_id = :assistantId AND event_type = 'ongoing' ORDER BY scheduled_at ASC")
    suspend fun getOngoingEvents(assistantId: String): List<TimelineEventEntity>

    @Query("SELECT * FROM TimelineEventEntity WHERE assistant_id = :assistantId AND event_type IN ('upcoming', 'ongoing', 'recurring') ORDER BY scheduled_at ASC")
    suspend fun getActiveEvents(assistantId: String): List<TimelineEventEntity>

    @Query("SELECT * FROM TimelineEventEntity WHERE assistant_id = :assistantId AND event_type IN ('upcoming', 'ongoing', 'recurring') ORDER BY scheduled_at ASC")
    fun getActiveEventsFlow(assistantId: String): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM TimelineEventEntity WHERE assistant_id = :assistantId AND event_type = 'upcoming' AND scheduled_at IS NOT NULL AND scheduled_at <= :now")
    suspend fun getOverdueEvents(assistantId: String, now: Long = System.currentTimeMillis()): List<TimelineEventEntity>

    @Query("SELECT * FROM TimelineEventEntity WHERE node_id = :nodeId")
    suspend fun getEventsForNode(nodeId: Int): List<TimelineEventEntity>

    @Query("SELECT COUNT(*) FROM TimelineEventEntity WHERE assistant_id = :assistantId AND event_type IN ('upcoming', 'ongoing', 'recurring')")
    suspend fun getActiveEventCount(assistantId: String): Int

    @Query("SELECT COUNT(*) FROM TimelineEventEntity WHERE assistant_id = :assistantId AND event_type IN ('upcoming', 'ongoing', 'recurring')")
    fun getActiveEventCountFlow(assistantId: String): Flow<Int>

    @Query("UPDATE TimelineEventEntity SET event_type = :newType, completed_at = :completedAt, last_checked = :now WHERE id = :id")
    suspend fun updateEventStatus(id: Int, newType: String, completedAt: Long? = null, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM TimelineEventEntity WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM TimelineEventEntity WHERE assistant_id = :assistantId")
    suspend fun deleteAllForAssistant(assistantId: String)
}
