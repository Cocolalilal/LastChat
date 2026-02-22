package me.rerere.rikkahub.data.ai.memory

import android.util.Log
import me.rerere.rikkahub.data.db.dao.MemoryNodeDAO
import me.rerere.rikkahub.data.db.dao.TimelineEventDAO
import me.rerere.rikkahub.data.db.entity.EventType
import me.rerere.rikkahub.data.db.entity.NodeStatus
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Manages temporal awareness: sweeps overdue events,
 * promotes upcoming events, and handles recurring patterns.
 */
class TimelineManager(
    private val timelineEventDAO: TimelineEventDAO,
    private val nodeDAO: MemoryNodeDAO,
) {
    companion object {
        private const val TAG = "TimelineManager"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    /**
     * Sweep timeline: mark overdue upcoming events as completed,
     * check recurring events, and update node statuses accordingly.
     */
    suspend fun sweepTimeline(assistantId: String) {
        val now = System.currentTimeMillis()

        // 1. Auto-complete overdue upcoming events
        val overdueEvents = timelineEventDAO.getOverdueEvents(assistantId, now)
        for (event in overdueEvents) {
            timelineEventDAO.updateEventStatus(event.id, EventType.COMPLETED, completedAt = now)

            // Also update the linked node status
            val node = nodeDAO.getById(event.nodeId)
            if (node != null && node.status == NodeStatus.ACTIVE) {
                nodeDAO.update(node.copy(status = NodeStatus.COMPLETED))
            }

            Log.i(TAG, "Auto-completed overdue event: ${event.description}")
        }

        // 2. Handle recurring events: recreate next occurrence
        val recurringEvents = timelineEventDAO.getActiveEvents(assistantId)
            .filter { it.eventType == EventType.RECURRING && it.scheduledAt != null && it.scheduledAt <= now }

        for (event in recurringEvents) {
            val nextOccurrence = calculateNextOccurrence(event)
            if (nextOccurrence != null) {
                timelineEventDAO.update(event.copy(
                    scheduledAt = nextOccurrence,
                    lastChecked = now,
                ))
                Log.i(TAG, "Rescheduled recurring event: ${event.description}")
            }
        }

        Log.i(TAG, "Timeline sweep complete: ${overdueEvents.size} expired, ${recurringEvents.size} recurring")
    }

    /**
     * Create a timeline event from extracted data.
     * Deduplicates: skips if an event with similar description already exists on the same node
     * within the last 24 hours.
     */
    suspend fun createEventFromExtraction(
        assistantId: String,
        nodeId: Int,
        eventType: String,
        description: String,
        scheduledDateStr: String? = null,
        recurrenceRule: String? = null,
    ): Int {
        // Deduplication: check for existing similar event on same node
        val recentEvents = timelineEventDAO.getEventsForNode(nodeId)
        val isDuplicate = recentEvents.any { existing ->
            existing.description.equals(description, ignoreCase = true) ||
            (existing.description.length > 10 && description.length > 10 &&
                (existing.description.contains(description, ignoreCase = true) ||
                 description.contains(existing.description, ignoreCase = true)))
        }
        if (isDuplicate) {
            Log.d(TAG, "Skipping duplicate timeline event: $description")
            return 0
        }

        val scheduledAt = scheduledDateStr?.let { parseDateString(it) }

        val event = TimelineEventEntity(
            assistantId = assistantId,
            nodeId = nodeId,
            eventType = eventType,
            scheduledAt = scheduledAt,
            recurrenceRule = recurrenceRule,
            description = description,
        )

        return timelineEventDAO.insert(event).toInt()
    }

    /**
     * Get a formatted string of active timeline events for prompt injection.
     */
    suspend fun getActiveTimelineContext(assistantId: String): String {
        val events = timelineEventDAO.getActiveEvents(assistantId)
        if (events.isEmpty()) return ""

        return buildString {
            appendLine("## Active Timeline")
            for (event in events) {
                val node = nodeDAO.getById(event.nodeId)
                val nodeName = node?.name ?: "Unknown"
                val icon = when (event.eventType) {
                    EventType.UPCOMING -> "⏳"
                    EventType.ONGOING -> "🔄"
                    EventType.RECURRING -> "🔁"
                    else -> "📌"
                }
                append("- $icon **$nodeName**: ${event.description}")
                if (event.scheduledAt != null) {
                    append(" (${DATE_FORMAT.format(java.util.Date(event.scheduledAt))})")
                }
                appendLine()
            }
        }
    }

    private fun calculateNextOccurrence(event: TimelineEventEntity): Long? {
        val rule = event.recurrenceRule ?: return null
        val baseTime = event.scheduledAt ?: return null

        return when {
            rule == "daily" -> baseTime + 24 * 60 * 60 * 1000L
            rule.startsWith("weekly") -> baseTime + 7 * 24 * 60 * 60 * 1000L
            rule.startsWith("monthly") -> baseTime + 30L * 24 * 60 * 60 * 1000L
            rule.startsWith("yearly") -> baseTime + 365L * 24 * 60 * 60 * 1000L
            else -> null
        }
    }

    private fun parseDateString(dateStr: String): Long? {
        return try {
            DATE_FORMAT.parse(dateStr)?.time
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date: $dateStr", e)
            null
        }
    }
}
