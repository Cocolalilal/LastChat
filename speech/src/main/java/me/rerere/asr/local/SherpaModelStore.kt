package me.rerere.asr.local

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun clear() = mutate { emptyList() }

    suspend fun reorder(ids: List<String>) = mutate { models ->
        val rank = ids.withIndex().associate { it.value to it.index }
        models.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }

    suspend fun move(from: Int, to: Int) = mutate { models ->
        if (from !in models.indices || to !in models.indices || from == to) return@mutate models
        models.toMutableList().apply { add(to, removeAt(from)) }
    }

    suspend fun rename(id: String, displayName: String) = mutate { models ->
        models.map { model ->
            if (model.id == id) model.copy(displayName = displayName) else model
        }
    }

    suspend fun updateConfig(id: String, config: SherpaModelConfig) = mutate { models ->
        models.map { if (it.id == id) it.copy(config = config) else it }
    }

    suspend fun reconcileWithCatalog(catalog: SherpaModelCatalog) = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "local_stt/models")
        // A promotion interrupted between the two renames can leave the prior install here.
        root.listFiles().orEmpty().filter { it.isDirectory && it.name.endsWith(".previous") }.forEach { backup ->
            val target = File(root, backup.name.removeSuffix(".previous"))
            if (!target.exists()) backup.renameTo(target)
            else backup.deleteRecursively()
        }
        mutate { installed ->
            val metadata = catalog.models.associateBy { it.id }
            val recovered = root.listFiles().orEmpty()
                .filter { it.isDirectory && !it.name.endsWith(".installing") && !it.name.endsWith(".previous") }
                .mapNotNull { directory ->
                    val meta = metadata[directory.name]
                    val manifest = runCatching {
                        json.decodeFromString<InstalledSherpaModel>(File(directory, SherpaModelInstall.INSTALL_MANIFEST).readText())
                    }.getOrNull()?.takeIf { it.id == directory.name }
                    val model = if (meta != null) {
                        InstalledSherpaModel(id = meta.id, displayName = meta.name, family = meta.family,
                            languages = meta.languages, streaming = meta.streaming, onlineModelType = meta.onlineModelType,
                            directoryPath = directory.absolutePath, revision = manifest?.revision ?: meta.revision,
                            files = meta.files.mapValues { (_, relative) -> File(directory, relative).absolutePath },
                            sizeInBytes = meta.files.values.sumOf { File(directory, it).length() },
                            config = manifest?.config ?: meta.defaultConfig)
                    } else manifest?.takeIf { it.directoryPath == directory.absolutePath }
                    model?.takeIf { candidate -> complete(candidate) && (meta?.downloads.orEmpty().all { (role, source) ->
                        candidate.files[role]?.let(::File)?.length() == source.sizeBytes
                    }) }
                }
            val recoveredById = recovered.associateBy { it.id }
            val result = installed.mapNotNull { prior ->
                val recoveredModel = recoveredById[prior.id]
                when {
                    recoveredModel != null -> recoveredModel.copy(displayName = prior.displayName,
                        config = prior.config, customIconUri = prior.customIconUri)
                    complete(prior) && metadata[prior.id]?.downloads.orEmpty().all { (role, source) ->
                        prior.files[role]?.let(::File)?.length() == source.sizeBytes
                    } -> prior
                    else -> null
                }
            }
            val knownIds = result.mapTo(mutableSetOf()) { it.id }
            result + recovered.filterNot { it.id in knownIds }
        }
    }

    private fun complete(model: InstalledSherpaModel): Boolean {
        val roles = when (model.family) {
            SherpaModelFamily.WHISPER, SherpaModelFamily.MOONSHINE ->
                listOf(SherpaFileRole.ENCODER, SherpaFileRole.DECODER, SherpaFileRole.TOKENS)
            SherpaModelFamily.SENSE_VOICE -> listOf(SherpaFileRole.MODEL, SherpaFileRole.TOKENS)
            SherpaModelFamily.ONLINE_TRANSDUCER ->
                listOf(SherpaFileRole.ENCODER, SherpaFileRole.DECODER, SherpaFileRole.JOINER, SherpaFileRole.TOKENS)
        }
        return roles.all { role -> model.files[role]?.let(::File)?.let { it.isFile && it.length() > 0 } == true }
    }

    private suspend fun mutate(transform: (List<InstalledSherpaModel>) -> List<InstalledSherpaModel>) {
        context.sherpaModelDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(transform(decode(prefs[key])))
        }
    }

    private fun decode(raw: String?): List<InstalledSherpaModel> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            (json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { value ->
                runCatching { json.decodeFromJsonElement(InstalledSherpaModel.serializer(), value) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}
