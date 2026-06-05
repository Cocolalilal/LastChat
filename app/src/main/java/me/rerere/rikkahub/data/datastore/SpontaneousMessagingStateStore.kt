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
        private const val KEY_CONSUMED_EVENT_RECORDS = "consumed_event_records"
        private const val MAX_CONSUMED_EVENT_RECORDS = 64
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

    fun isEventConsumed(eventId: String): Boolean {
        return loadConsumedEventRecords().any { it.eventId == eventId }
    }

    fun getConsumedEventRecord(eventId: String): ConsumedSpontaneousEventRecord? {
        return loadConsumedEventRecords().firstOrNull { it.eventId == eventId }
    }

    fun markEventConsumed(
        eventId: String,
        conversationId: Uuid? = null,
        assistantId: Uuid? = null,
        persistenceMode: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val updated = loadConsumedEventRecords()
            .filterNot { it.eventId == eventId }
            .let { records ->
                listOf(
                    ConsumedSpontaneousEventRecord(
                        eventId = eventId,
                        updatedAt = now,
                        conversationId = conversationId?.toString(),
                        assistantId = assistantId?.toString(),
                        persistenceMode = persistenceMode,
                    )
                ) + records
            }
            .take(MAX_CONSUMED_EVENT_RECORDS)
        saveConsumedEventRecords(updated)
    }

    private fun loadConsumedEventRecords(): List<ConsumedSpontaneousEventRecord> {
        val stored = prefs.getString(KEY_CONSUMED_EVENT_RECORDS, null) ?: return emptyList()
        return runCatching {
            JsonInstant.decodeFromString<List<ConsumedSpontaneousEventRecord>>(stored)
        }.getOrDefault(emptyList())
    }

    private fun saveConsumedEventRecords(records: List<ConsumedSpontaneousEventRecord>) {
        prefs.edit()
            .putString(KEY_CONSUMED_EVENT_RECORDS, JsonInstant.encodeToString(records))
            .apply()
    }
}

@Serializable
data class ConsumedSpontaneousEventRecord(
    val eventId: String,
    val updatedAt: Long = 0L,
    val conversationId: String? = null,
    val assistantId: String? = null,
    val persistenceMode: String? = null,
)
