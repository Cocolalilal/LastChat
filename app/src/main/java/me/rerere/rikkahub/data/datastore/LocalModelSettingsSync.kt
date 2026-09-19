package me.rerere.rikkahub.data.datastore

import kotlinx.coroutines.flow.first
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ContextLimitSource
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
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
    settingsStore.update(updated.clearMissingModelReferences())
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
