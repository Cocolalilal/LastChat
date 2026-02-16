package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["node_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["assistant_id", "event_type"]),
        Index(value = ["node_id"]),
        Index(value = ["scheduled_at"]),
    ]
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("node_id")
    val nodeId: Int,
    @ColumnInfo("event_type")
    val eventType: String, // "upcoming", "ongoing", "completed", "recurring"
    @ColumnInfo("scheduled_at")
    val scheduledAt: Long? = null,
    @ColumnInfo("completed_at")
    val completedAt: Long? = null,
    @ColumnInfo("recurrence_rule")
    val recurrenceRule: String? = null, // "daily", "weekly:mon,wed", "monthly:15"
    @ColumnInfo("description")
    val description: String = "",
    @ColumnInfo("last_checked")
    val lastChecked: Long = System.currentTimeMillis(),
)

object EventType {
    const val UPCOMING = "upcoming"
    const val ONGOING = "ongoing"
    const val COMPLETED = "completed"
    const val RECURRING = "recurring"
}
