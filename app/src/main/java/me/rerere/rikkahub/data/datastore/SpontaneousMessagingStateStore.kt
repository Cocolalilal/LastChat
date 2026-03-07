package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

class SpontaneousMessagingStateStore(context: Context) {
    companion object {
        private const val PREFS_NAME = "spontaneous_messaging_state"
        private const val KEY_GLOBAL_QUIET_UNTIL = "global_quiet_until"
        private const val KEY_LAST_SENDER_ASSISTANT_ID = "last_sender_assistant_id"
        private const val KEY_EVENT_RECORDS = "event_records"
        private const val MAX_EVENT_RECORDS = 32
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val globalQuietUntil: Long
        get() = prefs.getLong(KEY_GLOBAL_QUIET_UNTIL, 0L)

    val lastSenderAssistantId: Uuid?
        get() = prefs.getString(KEY_LAST_SENDER_ASSISTANT_ID, null)?.let { value ->
            runCatching { Uuid.parse(value) }.getOrNull()
        }

    fun updateDeliveryState(globalQuietUntil: Long, assistantId: Uuid?) {
        prefs.edit()
            .putLong(KEY_GLOBAL_QUIET_UNTIL, globalQuietUntil)
            .apply {
                if (assistantId != null) {
                    putString(KEY_LAST_SENDER_ASSISTANT_ID, assistantId.toString())
                } else {
                    remove(KEY_LAST_SENDER_ASSISTANT_ID)
                }
            }
            .apply()
    }

    fun getFallbackConversation(eventId: String): Uuid? {
        return loadEventRecords()
            .firstOrNull { it.eventId == eventId }
            ?.fallbackConversationId
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    }

    fun rememberFallbackConversation(eventId: String, conversationId: Uuid) {
        val now = System.currentTimeMillis()
        val updated = loadEventRecords()
            .filterNot { it.eventId == eventId }
            .let { records ->
                listOf(
                    SpontaneousNotificationEventRecord(
                        eventId = eventId,
                        fallbackConversationId = conversationId.toString(),
                        updatedAt = now,
                    )
                ) + records
            }
            .take(MAX_EVENT_RECORDS)
        saveEventRecords(updated)
    }

    private fun loadEventRecords(): List<SpontaneousNotificationEventRecord> {
        val stored = prefs.getString(KEY_EVENT_RECORDS, null) ?: return emptyList()
        return runCatching {
            JsonInstant.decodeFromString<List<SpontaneousNotificationEventRecord>>(stored)
        }.getOrDefault(emptyList())
    }

    private fun saveEventRecords(records: List<SpontaneousNotificationEventRecord>) {
        prefs.edit()
            .putString(KEY_EVENT_RECORDS, JsonInstant.encodeToString(records))
            .apply()
    }
}

@Serializable
data class SpontaneousNotificationEventRecord(
    val eventId: String,
    val fallbackConversationId: String? = null,
    val updatedAt: Long = 0L,
)
