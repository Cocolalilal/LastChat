package me.rerere.rikkahub.data.datastore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ContextLimitSource
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.local.InstalledSherpaModel
import me.rerere.locallm.InstalledLocalModel
import me.rerere.locallm.effectiveRuntimeContextLength
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.inferFamilyEntry

/**
 * If any on-device model is installed, the Local provider must exist so it shows on the
 * providers screen. An empty install list never creates a provider (that would undo an
 * intentional delete).
 */
fun Settings.withSyncedLocalProviderModels(
    llmAndEmbeddingModels: List<Model>,
    sttModels: List<Model>,
): Settings {
    val mergedModels = sttModels + llmAndEmbeddingModels
    val current = providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull()
    if (current == null) {
        if (mergedModels.isEmpty()) return this
        return copy(providers = providers + ProviderSetting.LiteRtLocal(models = mergedModels))
    }
    if (current.models == mergedModels) return this
    return copy(
        providers = providers.map { provider ->
            if (provider is ProviderSetting.LiteRtLocal) provider.copy(models = mergedModels) else provider
        }
    )
}

/** Reconciles only STT entries; all other models and provider edits come from this settings value. */
internal fun Settings.withInstalledSherpaModels(
    installed: List<InstalledSherpaModel>,
    selectInstalledId: String? = null,
): Settings {
    val local = providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull()
    if (local == null && installed.isEmpty()) return this
    val existing = local?.models.orEmpty().filter { it.type == ModelType.STT }.associateBy { it.modelId }
    val models = installed.map { model ->
        (existing[model.id] ?: Model()).copy(
            modelId = model.id,
            displayName = model.displayName,
            type = ModelType.STT,
            inputModalities = listOf(Modality.AUDIO),
            outputModalities = listOf(Modality.TEXT),
            abilities = emptyList(),
            customIconUri = model.customIconUri,
        )
    }
    val removedSelection = existing.values.any { previous ->
        previous.id == sttModelId && models.none { it.id == previous.id }
    }
    val selectedId = models.firstOrNull { it.modelId == selectInstalledId }?.id
        ?: sttModelId.takeUnless { removedSelection }
    return withSyncedLocalProviderModels(
        llmAndEmbeddingModels = local?.models.orEmpty().filter { it.type != ModelType.STT },
        sttModels = models,
    ).copy(sttModelId = selectedId)
}

/** Publish completed speech installs even when the local-model settings screen is closed. */
suspend fun syncInstalledSherpaModelsToSettings(
    installed: List<InstalledSherpaModel>,
    settingsStore: SettingsStore,
    selectInstalledId: String? = null,
) {
    val settings = settingsStore.settingsFlow.first { !it.init }
    if (settings.withInstalledSherpaModels(installed, selectInstalledId) == settings) return
    settingsStore.update { latest -> latest.withInstalledSherpaModels(installed, selectInstalledId) }
}

/** App-lifetime registry publication, independent of any screen or ViewModel subscription. */
internal suspend fun collectInstalledSherpaModelsToSettings(
    installedModels: Flow<List<InstalledSherpaModel>>,
    settingsStore: SettingsStore,
    onFailure: (Throwable) -> Unit = {},
) {
    combine(installedModels, settingsStore.settingsFlow.filter { !it.init }) { installed, _ -> installed }
        .conflate()
        .collect { installed ->
            try {
                syncInstalledSherpaModelsToSettings(installed, settingsStore)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onFailure(error)
            }
        }
}

/** Keeps LiteRT's selectable model metadata aligned with the context the runtime really loads. */
suspend fun syncInstalledLocalModelsToSettings(
    installed: List<InstalledLocalModel>,
    totalRamGb: Int,
    settingsStore: SettingsStore,
    catalogSnapshot: ModelCatalogSnapshot?,
) {
    val settings = settingsStore.settingsFlow.first { !it.init }
    val local = settings.providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull()
    if (local == null && installed.isEmpty()) return
    val existingByModelId = local?.models.orEmpty().associateBy { it.modelId }
    val llmModels = installed.map { installedModel ->
        installedModel.toSettingsModel(existingByModelId[installedModel.id], catalogSnapshot, totalRamGb)
    }
    val sttModels = local?.models.orEmpty().filter { it.type == ModelType.STT }
    val updated = settings.withSyncedLocalProviderModels(
        llmAndEmbeddingModels = llmModels,
        sttModels = sttModels,
    )
    if (updated.providers == settings.providers) return
    // Apply on the latest settings, recomputed under the store lock, so an edit made between the
    // read above and this write isn't overwritten.
    settingsStore.update { latest ->
        val latestLocal = latest.providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull()
        val latestById = latestLocal?.models.orEmpty().associateBy { it.modelId }
        latest.withSyncedLocalProviderModels(
            llmAndEmbeddingModels = installed.map { m ->
                m.toSettingsModel(latestById[m.id], catalogSnapshot, totalRamGb)
            },
            sttModels = latestLocal?.models.orEmpty().filter { it.type == ModelType.STT },
        ).clearMissingModelReferences()
    }
}

private fun InstalledLocalModel.toSettingsModel(
    existing: Model?,
    catalogSnapshot: ModelCatalogSnapshot?,
    totalRamGb: Int,
): Model {
    val iconUrl = catalogSnapshot?.inferFamilyEntry(displayName)?.iconUrl
    if (isEmbedding) {
        return (existing ?: Model()).copy(
            modelId = id,
            displayName = displayName,
            type = ModelType.EMBEDDING,
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.TEXT),
            abilities = emptyList(),
            iconUrl = iconUrl,
            customIconUri = customIconUri,
        )
    }
    return (existing ?: Model()).copy(
        modelId = id,
        displayName = displayName,
        type = ModelType.CHAT,
        inputModalities = buildList {
            add(Modality.TEXT)
            if (supportsImage) add(Modality.IMAGE)
            if (supportsAudio) add(Modality.AUDIO)
        },
        outputModalities = listOf(Modality.TEXT),
        abilities = buildList {
            add(ModelAbility.TOOL)
            if (supportsThinking) add(ModelAbility.REASONING)
        },
        contextWindowTokens = effectiveRuntimeContextLength(totalRamGb),
        contextLimitSource = ContextLimitSource.RUNTIME,
        iconUrl = iconUrl,
        customIconUri = customIconUri,
    )
}
