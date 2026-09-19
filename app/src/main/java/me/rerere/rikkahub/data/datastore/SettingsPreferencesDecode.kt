package me.rerere.rikkahub.data.datastore

import android.util.Log
import kotlinx.serialization.json.decodeFromJsonElement
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

private const val TAG = "SettingsDecode"

/**
 * Decode a JSON array one element at a time so a single unknown polymorphic
 * subtype (removed provider, old search backend, etc.) cannot wipe the rest
 * of settings — or the user's chats, which are keyed by assistant IDs stored here.
 */
internal inline fun <reified T> decodePreferenceList(json: String?): List<T>? {
    if (json.isNullOrBlank()) return null
    val element = runCatching { JsonInstant.parseToJsonElement(json) }.getOrElse {
        logDecodeError("Failed to parse preference JSON array for ${T::class.simpleName}", it)
        return null
    }
    val array = element.jsonArrayOrNull ?: return runCatching {
        JsonInstant.decodeFromString<List<T>>(json)
    }.getOrElse {
        logDecodeError("Failed to decode preference list ${T::class.simpleName}", it)
        null
    }
    return array.mapNotNull { item ->
        runCatching { JsonInstant.decodeFromJsonElement<T>(item) }.getOrElse {
            logDecodeError("Skipping invalid ${T::class.simpleName} entry", it)
            null
        }
    }
}

internal inline fun <reified T> decodePreferenceValue(json: String?, fallback: T): T {
    if (json.isNullOrBlank()) return fallback
    return runCatching { JsonInstant.decodeFromString<T>(json) }.getOrElse {
        logDecodeError("Failed to decode preference value ${T::class.simpleName}", it)
        fallback
    }
}

private fun logDecodeError(message: String, error: Throwable) {
    runCatching { Log.w(TAG, message, error) }
}

internal fun decodeUuidOrNull(raw: String?): Uuid? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Uuid.parse(raw) }.getOrNull()
}

internal fun decodeUuid(raw: String?, fallback: Uuid): Uuid = decodeUuidOrNull(raw) ?: fallback

/**
 * Re-attach stub assistants for conversation rows whose character was dropped from
 * DataStore (typical after a settings parse failure). Does not invent assistants
 * when there are no matching chats.
 */
internal fun Settings.withRecoveredAssistantsFromConversations(
    conversationAssistantIds: Collection<String>,
): Settings {
    val known = assistants.map { it.id }.toSet()
    val recovered = conversationAssistantIds
        .mapNotNull(::decodeUuidOrNull)
        .distinct()
        .filter { it !in known }
        .map { id ->
            Assistant(
                id = id,
                name = "Recovered",
            )
        }
    if (recovered.isEmpty()) return this
    val mergedAssistants = assistants + recovered
    val selectedExists = mergedAssistants.any { it.id == assistantId }
    return copy(
        assistants = mergedAssistants,
        assistantId = if (selectedExists) assistantId else recovered.first().id,
    )
}
