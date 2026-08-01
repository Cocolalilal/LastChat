package me.rerere.rikkahub.data.datastore

import kotlinx.coroutines.flow.first
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.locallm.InstalledLocalModel
import me.rerere.locallm.effectiveRuntimeContextLength
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.inferFamilyEntry

/** Keeps LiteRT's selectable model metadata aligned with the context the runtime really loads. */
suspend fun syncInstalledLocalModelsToSettings(
    installed: List<InstalledLocalModel>,
    totalRamGb: Int,
    settingsStore: SettingsStore,
    catalogSnapshot: ModelCatalogSnapshot?,
) {
    val settings = settingsStore.settingsFlow.first { !it.init }
    val local = settings.providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull() ?: return
    val existingByModelId = local.models.associateBy { it.modelId }
    val newModels = local.models.filter { it.type == ModelType.STT } + installed.map { installedModel ->
        installedModel.toSettingsModel(existingByModelId[installedModel.id], catalogSnapshot, totalRamGb)
    }
    if (newModels == local.models) return
    settingsStore.update(
        settings.copy(
            providers = settings.providers.map { provider ->
                if (provider is ProviderSetting.LiteRtLocal) provider.copy(models = newModels) else provider
            }
        )
    )
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
        iconUrl = iconUrl,
        customIconUri = customIconUri,
    )
}
