package me.rerere.rikkahub.data.datastore

import kotlinx.coroutines.flow.first
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ContextLimitSource
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.locallm.RETIRED_EMBEDDING_MODEL_ID
import me.rerere.locallm.TEXT_EMBEDDING_MODEL_ID
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
    val mergedModels = sttModels + llmAndEmbeddingModels.filterNot { it.modelId == RETIRED_EMBEDDING_MODEL_ID }
    val current = providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull()
    if (current == null) {
        if (mergedModels.isEmpty()) return this
        return copy(providers = providers + ProviderSetting.LiteRtLocal(models = mergedModels))
    }
    if (current.models == mergedModels) return this
    val retiredIds = current.models.filter { it.modelId == RETIRED_EMBEDDING_MODEL_ID }.map { it.id }.toSet()
    val replacement = mergedModels.firstOrNull {
        it.modelId == TEXT_EMBEDDING_MODEL_ID && it.type == ModelType.EMBEDDING
    }?.id ?: DISABLED_MODEL_ID
    return copy(
        providers = providers.map { provider ->
            if (provider is ProviderSetting.LiteRtLocal) provider.copy(models = mergedModels) else provider
        },
        // A retired local selection must never fall through to a configured cloud embedder.
        // Keep the replacement's own UUID so old vectors are not reused for a different model.
        embeddingModelId = if (embeddingModelId in retiredIds) replacement else embeddingModelId,
        assistants = assistants.map { assistant ->
            if (assistant.embeddingModelId in retiredIds) assistant.copy(embeddingModelId = replacement) else assistant
        },
        favoriteModels = favoriteModels.filterNot { it in retiredIds },
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
    val activeInstalls = installed.filterNot { it.id == RETIRED_EMBEDDING_MODEL_ID }
    val existingByModelId = local?.models.orEmpty().associateBy { it.modelId }
    val llmModels = activeInstalls.map { installedModel ->
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
            llmAndEmbeddingModels = activeInstalls.map { m ->
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
