package me.rerere.rikkahub.service

import androidx.work.Data
import androidx.work.workDataOf
import java.util.UUID

internal object ScheduledMessageWorkSpec {
    const val KEY_ASSISTANT_ID = "assistantId"
    const val KEY_CONVERSATION_ID = "conversationId"
    const val KEY_REASON = "reason"
    const val KEY_CREATED_AT = "createdAt"
    const val KEY_SCHEDULED_AT = "scheduledAt"

    const val UNIQUE_WORK_PREFIX = "scheduled_message"
    const val MAX_NOTIFICATION_PERMISSION_RETRIES = 3

    fun buildUniqueWorkName(
        assistantId: String,
        conversationId: String,
        reason: String,
        scheduledAtMillis: Long
    ): String {
        val fingerprint = "$assistantId|$conversationId|${reason.trim()}|$scheduledAtMillis"
        val stableId = UUID.nameUUIDFromBytes(fingerprint.toByteArray(Charsets.UTF_8))
        return "$UNIQUE_WORK_PREFIX:$stableId"
    }

    fun buildInputData(
        assistantId: String,
        conversationId: String,
        reason: String,
        createdAtMillis: Long,
        scheduledAtMillis: Long
    ): Data {
        return workDataOf(
            KEY_ASSISTANT_ID to assistantId,
            KEY_CONVERSATION_ID to conversationId,
            KEY_REASON to reason,
            KEY_CREATED_AT to createdAtMillis,
            KEY_SCHEDULED_AT to scheduledAtMillis
        )
    }
}
