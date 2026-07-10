package me.rerere.rikkahub.ui.pages.setting.localstt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.local.InstalledSherpaModel
import me.rerere.asr.local.SherpaCatalog
import me.rerere.asr.local.SherpaDownload
import me.rerere.asr.local.SherpaDownloadManager
import me.rerere.asr.local.SherpaModelCatalog
import me.rerere.asr.local.SherpaModelConfig
import me.rerere.asr.local.SherpaModelInstall
import me.rerere.asr.local.SherpaModelMetadata
import me.rerere.asr.local.SherpaModelStore
import me.rerere.asr.local.SherpaSttRuntime
import me.rerere.rikkahub.data.datastore.SettingsStore

data class LocalSttUiState(
    val installed: List<InstalledSherpaModel> = emptyList(),
    val downloadable: List<SherpaModelMetadata> = emptyList(),
    val downloads: Map<String, SherpaDownload> = emptyMap(),
    val selectedModelId: String? = null,
)

class SettingLocalSttViewModel(
    private val store: SherpaModelStore,
    private val catalog: SherpaCatalog,
    private val downloadManager: SherpaDownloadManager,
    private val install: SherpaModelInstall,
    private val runtime: SherpaSttRuntime,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val catalogFlow = MutableStateFlow(SherpaModelCatalog())

    val uiState: StateFlow<LocalSttUiState> = combine(
        store.models,
        catalogFlow,
        downloadManager.downloads,
        settingsStore.settingsFlow,
    ) { installed, catalog, downloads, settings ->
        val installedIds = installed.mapTo(mutableSetOf()) { it.id }
        val selected = settings.sttModelId?.let { selectedId ->
            settings.providers.asSequence()
                .flatMap { it.models.asSequence() }
                .firstOrNull { it.id == selectedId }
                ?.modelId
        }
        LocalSttUiState(
            installed = installed,
            downloadable = catalog.models.filterNot { it.id in installedIds },
            downloads = downloads,
            selectedModelId = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalSttUiState())

    init {
        viewModelScope.launch {
            val current = catalog.catalog()
            catalogFlow.value = current
            store.reconcileWithCatalog(current)
        }
        viewModelScope.launch {
            combine(store.models, settingsStore.settingsFlow) { installed, _ -> installed }
                .collectLatest(::syncModelsToLocalProvider)
        }
    }

    fun download(model: SherpaModelMetadata) = downloadManager.download(model)

    fun cancelDownload(id: String) = downloadManager.cancel(id)

    fun dismissDownloadError(id: String) = downloadManager.dismissError(id)

    fun delete(model: InstalledSherpaModel) {
        viewModelScope.launch {
            runtime.unload()
            install.delete(model)
            store.remove(model.id)
        }
    }

    fun updateConfig(id: String, config: SherpaModelConfig) {
        viewModelScope.launch {
            runtime.unload()
            store.updateConfig(id, config)
        }
    }

    fun select(model: InstalledSherpaModel) {
        viewModelScope.launch {
            val settings = settingsStore.settingsFlow.value
            val currentLocal = settings.providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull()
            val aiModel = currentLocal?.models
                ?.firstOrNull { it.type == ModelType.STT && it.modelId == model.id }
                ?: model.toAiModel(null)
            val updatedLocal = (currentLocal ?: ProviderSetting.LiteRtLocal()).copy(
                models = (currentLocal?.models.orEmpty().filterNot {
                    it.type == ModelType.STT && it.modelId == model.id
                }) + aiModel
            )
            val providers = if (currentLocal == null) {
                settings.providers + updatedLocal
            } else {
                settings.providers.map { if (it is ProviderSetting.LiteRtLocal) updatedLocal else it }
            }
            settingsStore.update(settings.copy(providers = providers, sttModelId = aiModel.id))
        }
    }

    private suspend fun syncModelsToLocalProvider(installed: List<InstalledSherpaModel>) {
        val settings = settingsStore.settingsFlow.value
        val local = settings.providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull() ?: return
        val existingByModelId = local.models.associateBy { it.modelId }
        val sttModels = installed.map { model -> model.toAiModel(existingByModelId[model.id]) }
        val preservedModels = local.models.filter { it.type != ModelType.STT }
        val mergedModels = preservedModels + sttModels
        if (mergedModels == local.models) return
        val providers = settings.providers.map { provider ->
            if (provider is ProviderSetting.LiteRtLocal) provider.copy(models = mergedModels) else provider
        }
        val installedIds = installed.mapTo(mutableSetOf()) { it.id }
        val selectedLocalModel = settings.sttModelId?.let { selectedId ->
            local.models.firstOrNull { it.id == selectedId && it.type == ModelType.STT }
        }
        val selectedStillExists = selectedLocalModel == null || selectedLocalModel.modelId in installedIds
        settingsStore.update(
            settings.copy(
                providers = providers,
                sttModelId = settings.sttModelId.takeIf { selectedStillExists },
            )
        )
    }

    private fun InstalledSherpaModel.toAiModel(existing: Model?): Model = (existing ?: Model()).copy(
        modelId = id,
        displayName = displayName,
        type = ModelType.STT,
        inputModalities = listOf(Modality.AUDIO),
        outputModalities = listOf(Modality.TEXT),
        abilities = emptyList(),
        customIconUri = customIconUri,
    )
}
