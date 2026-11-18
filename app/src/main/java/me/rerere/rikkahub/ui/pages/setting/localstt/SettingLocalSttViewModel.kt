package me.rerere.rikkahub.ui.pages.setting.localstt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
import me.rerere.rikkahub.data.datastore.syncInstalledSherpaModelsToSettings

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
    private val appScope: me.rerere.rikkahub.AppScope,
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
        // Ongoing publication belongs to LastChatApp, so leaving this page cannot stop it.
        viewModelScope.launch { syncInstalledSherpaModelsToSettings(store.current(), settingsStore) }
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

    fun rename(id: String, displayName: String) {
        viewModelScope.launch {
            store.rename(id, displayName.trim().ifBlank { id })
        }
    }

    fun reorderInstalledModels(ids: List<Any>) {
        appScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            store.reorder(ids.map { it.toString().removePrefix("sherpa-installed-") })
        }
    }

    fun moveInstalledModel(from: Int, to: Int) {
        viewModelScope.launch { store.move(from, to) }
    }

    fun select(model: InstalledSherpaModel) {
        viewModelScope.launch {
            syncInstalledSherpaModelsToSettings(store.current(), settingsStore, selectInstalledId = model.id)
        }
    }
}
