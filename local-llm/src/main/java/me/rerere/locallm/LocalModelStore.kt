package me.rerere.locallm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File

private val Context.localModelDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_llm_models")

/**
 * Reactive persistence for the set of downloaded on-device models and their per-model config.
 * Stored as a single JSON blob in a dedicated Preferences DataStore (independent of the app's main
 * settings so it never bloats the settings normalization path).
 */
class LocalModelStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val key = stringPreferencesKey("installed_models")

    val models: Flow<List<InstalledLocalModel>> =
        context.localModelDataStore.data.map { prefs ->
            decode(prefs[key])
        }

    suspend fun current(): List<InstalledLocalModel> =
        decode(context.localModelDataStore.data.first()[key])

    suspend fun upsert(model: InstalledLocalModel) = mutate { list ->
        val idx = list.indexOfFirst { it.id == model.id }
        if (idx >= 0) list.toMutableList().apply { this[idx] = model }
        else list + model
    }

    suspend fun reconcileWithCatalog(catalog: LocalModelCatalog) = mutate { list ->
        val byId = catalog.models.associateBy { it.id }
        list.map { installed ->
            val meta = byId[installed.id] ?: return@map installed
            val tokenizerPath = installed.tokenizerPath ?: meta.tokenizerFile
                ?.let { File(context.filesDir, "local_models/$it").absolutePath }
                ?.takeIf { File(it).exists() }
            installed.withCatalogMetadata(meta, tokenizerPath)
        }
    }

    suspend fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }

    suspend fun updateConfig(id: String, config: LocalModelConfig) =
        update(id) { it.copy(config = config) }

    suspend fun updateRuntimeFlags(id: String, transform: (LocalModelRuntimeFlags) -> LocalModelRuntimeFlags) =
        update(id) { it.copy(runtimeFlags = transform(it.runtimeFlags)) }

    suspend fun rename(id: String, displayName: String) =
        update(id) { it.copy(displayName = displayName) }

    suspend fun setIcon(id: String, customIconUri: String?) =
        update(id) { it.copy(customIconUri = customIconUri) }

    suspend fun get(id: String): InstalledLocalModel? = current().firstOrNull { it.id == id }

    private suspend fun update(id: String, transform: (InstalledLocalModel) -> InstalledLocalModel) =
        mutate { list -> list.map { if (it.id == id) transform(it) else it } }

    private suspend fun mutate(transform: (List<InstalledLocalModel>) -> List<InstalledLocalModel>) {
        context.localModelDataStore.edit { prefs ->
            val updated = transform(decode(prefs[key]))
            prefs[key] = json.encodeToString(updated)
        }
    }

    private fun decode(raw: String?): List<InstalledLocalModel> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<InstalledLocalModel>>(raw) }.getOrDefault(emptyList())
    }
}
