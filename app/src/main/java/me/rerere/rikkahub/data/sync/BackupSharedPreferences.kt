package me.rerere.rikkahub.data.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

@Serializable
data class SharedPreferencesSnapshot(
    val name: String,
    val entries: List<SharedPreferencesSnapshotEntry>,
)

@Serializable
data class SharedPreferencesSnapshotEntry(
    val key: String,
    val type: String,
    val value: JsonElement,
)

internal object SharedPreferencesSnapshotTypes {
    const val STRING = "string"
    const val BOOLEAN = "boolean"
    const val INT = "int"
    const val LONG = "long"
    const val FLOAT = "float"
    const val STRING_SET = "string_set"
}

internal fun buildSharedPreferencesSnapshot(
    storeName: String,
    entries: Map<String, *>,
): SharedPreferencesSnapshot {
    val snapshotEntries = entries.entries
        .sortedBy { it.key }
        .map { (key, value) ->
            when (value) {
                null -> SharedPreferencesSnapshotEntry(
                    key = key,
                    type = SharedPreferencesSnapshotTypes.STRING,
                    value = JsonNull,
                )

                is String -> SharedPreferencesSnapshotEntry(
                    key = key,
                    type = SharedPreferencesSnapshotTypes.STRING,
                    value = JsonPrimitive(value),
                )

                is Boolean -> SharedPreferencesSnapshotEntry(
                    key = key,
                    type = SharedPreferencesSnapshotTypes.BOOLEAN,
                    value = JsonPrimitive(value),
                )

                is Int -> SharedPreferencesSnapshotEntry(
                    key = key,
                    type = SharedPreferencesSnapshotTypes.INT,
                    value = JsonPrimitive(value),
                )

                is Long -> SharedPreferencesSnapshotEntry(
                    key = key,
                    type = SharedPreferencesSnapshotTypes.LONG,
                    value = JsonPrimitive(value),
                )

                is Float -> SharedPreferencesSnapshotEntry(
                    key = key,
                    type = SharedPreferencesSnapshotTypes.FLOAT,
                    value = JsonPrimitive(value),
                )

                is Set<*> -> {
                    val strings = value.mapNotNull { it as? String }
                    require(strings.size == value.size) {
                        "Unsupported SharedPreferences string set value for key $key"
                    }
                    SharedPreferencesSnapshotEntry(
                        key = key,
                        type = SharedPreferencesSnapshotTypes.STRING_SET,
                        value = JsonArray(strings.sorted().map(::JsonPrimitive)),
                    )
                }

                else -> {
                    throw IllegalArgumentException(
                        "Unsupported SharedPreferences type for key $key: ${value::class.qualifiedName ?: value::class.simpleName ?: "Unknown"}"
                    )
                }
            }
        }
    return SharedPreferencesSnapshot(
        name = storeName,
        entries = snapshotEntries,
    )
}

internal fun exportSharedPreferencesSnapshot(
    context: Context,
    storeName: String,
): SharedPreferencesSnapshot {
    val prefs = context.applicationContext.getSharedPreferences(storeName, Context.MODE_PRIVATE)
    return buildSharedPreferencesSnapshot(storeName, prefs.all)
}

internal fun restoreSharedPreferencesSnapshot(
    context: Context,
    snapshot: SharedPreferencesSnapshot,
) {
    val prefs = context.applicationContext.getSharedPreferences(snapshot.name, Context.MODE_PRIVATE)
    restoreSharedPreferencesSnapshot(prefs, snapshot)
}

internal fun restoreSharedPreferencesSnapshot(
    prefs: SharedPreferences,
    snapshot: SharedPreferencesSnapshot,
) {
    val editor = prefs.edit().clear()
    snapshot.entries.forEach { entry ->
        when (entry.type) {
            SharedPreferencesSnapshotTypes.STRING -> {
                editor.putString(entry.key, entry.value.jsonPrimitiveOrNull?.contentOrNull)
            }

            SharedPreferencesSnapshotTypes.BOOLEAN -> {
                val value = entry.value.jsonPrimitiveOrNull?.booleanOrNull
                    ?: error("Invalid boolean SharedPreferences snapshot for ${entry.key}")
                editor.putBoolean(entry.key, value)
            }

            SharedPreferencesSnapshotTypes.INT -> {
                val value = entry.value.jsonPrimitiveOrNull?.intOrNull
                    ?: error("Invalid int SharedPreferences snapshot for ${entry.key}")
                editor.putInt(entry.key, value)
            }

            SharedPreferencesSnapshotTypes.LONG -> {
                val value = entry.value.jsonPrimitiveOrNull?.longOrNull
                    ?: error("Invalid long SharedPreferences snapshot for ${entry.key}")
                editor.putLong(entry.key, value)
            }

            SharedPreferencesSnapshotTypes.FLOAT -> {
                val value = entry.value.jsonPrimitiveOrNull?.floatOrNull
                    ?: error("Invalid float SharedPreferences snapshot for ${entry.key}")
                editor.putFloat(entry.key, value)
            }

            SharedPreferencesSnapshotTypes.STRING_SET -> {
                val value = (entry.value as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
                    ?.toSet()
                    ?: error("Invalid string_set SharedPreferences snapshot for ${entry.key}")
                editor.putStringSet(entry.key, value)
            }

            else -> error("Unsupported SharedPreferences snapshot type: ${entry.type}")
        }
    }
    editor.commit()
}
