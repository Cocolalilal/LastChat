package me.rerere.asr.local

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

private val Context.sherpaModelDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "local_stt_models")

class SherpaModelStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val key = stringPreferencesKey("installed_models")

    val models: Flow<List<InstalledSherpaModel>> = context.sherpaModelDataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun current(): List<InstalledSherpaModel> =
        decode(context.sherpaModelDataStore.data.first()[key])

    suspend fun get(id: String): InstalledSherpaModel? = current().firstOrNull { it.id == id }

    suspend fun upsert(model: InstalledSherpaModel) = mutate { models ->
        val index = models.indexOfFirst { it.id == model.id }
        if (index < 0) models + model else models.toMutableList().apply { this[index] = model }
    }

    suspend fun remove(id: String) = mutate { it.filterNot { model -> model.id == id } }

    suspend fun updateConfig(id: String, config: SherpaModelConfig) = mutate { models ->
        models.map { if (it.id == id) it.copy(config = config) else it }
    }

    suspend fun reconcileWithCatalog(catalog: SherpaModelCatalog) = mutate { installed ->
        val metadata = catalog.models.associateBy { it.id }
        installed.map { model -> metadata[model.id]?.let(model::withCatalogMetadata) ?: model }
    }

    private suspend fun mutate(transform: (List<InstalledSherpaModel>) -> List<InstalledSherpaModel>) {
        context.sherpaModelDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(transform(decode(prefs[key])))
        }
    }

    private fun decode(raw: String?): List<InstalledSherpaModel> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<InstalledSherpaModel>>(raw) }
            .getOrDefault(emptyList())
    }
}
