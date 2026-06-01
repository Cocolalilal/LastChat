package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for tracking daily message activity for the statistics heatmap.
 * This table persists independently of conversations, so aggregate activity
 * stays useful even when chats are deleted.
 */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String, // ISO format: YYYY-MM-DD
    
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 1,
    
    @ColumnInfo(name = "last_message_time")
    val lastMessageTime: Long = System.currentTimeMillis()
)
