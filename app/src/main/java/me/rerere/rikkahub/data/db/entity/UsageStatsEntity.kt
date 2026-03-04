package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row accumulator table for persistent usage statistics.
 * Values only increase (never decrease), so stats survive chat deletion.
 */
@Entity(tableName = "usage_stats")
data class UsageStatsEntity(
    @PrimaryKey
    val id: Int = 1, // Always 1 — single row
    
    @ColumnInfo(name = "total_conversations")
    val totalConversations: Long = 0L,
    
    @ColumnInfo(name = "total_messages")
    val totalMessages: Long = 0L,
    
    @ColumnInfo(name = "input_tokens")
    val inputTokens: Long = 0L,
    
    @ColumnInfo(name = "output_tokens")
    val outputTokens: Long = 0L,
    
    @ColumnInfo(name = "cached_tokens")
    val cachedTokens: Long = 0L,
    
    @ColumnInfo(name = "app_launches")
    val appLaunches: Long = 0L
)
