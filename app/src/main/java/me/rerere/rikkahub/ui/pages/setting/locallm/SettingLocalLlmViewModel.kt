package me.rerere.rikkahub.ui.pages.setting.locallm

import android.content.Context
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
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.locallm.InstalledLocalModel
import me.rerere.locallm.LiteRtCatalog
import me.rerere.locallm.LiteRtEmbedder
import me.rerere.locallm.LiteRtRuntime
import me.rerere.locallm.LocalModelKind
import me.rerere.locallm.LocalDownload
import me.rerere.locallm.LocalDownloadManager
import me.rerere.locallm.LocalModelCatalog
import me.rerere.locallm.LocalModelConfig
import me.rerere.locallm.LocalModelMetadata
import me.rerere.locallm.LocalModelStore
import me.rerere.locallm.LocalRuntimeState
import me.rerere.locallm.MemoryGuard
import me.rerere.locallm.ModelInstall
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.inferFamilyEntry
import me.rerere.rikkahub.data.datastore.SettingsStore

data class LocalLlmUiState(
    val installed: List<InstalledLocalModel> = emptyList(),
    val downloadable: List<LocalModelMetadata> = emptyList(),
    val deviceRamGb: Int = 0,
    val downloads: Map<String, LocalDownload> = emptyMap(),
    /** In-flight or failed downloads started from the manual Hugging Face URL installer. */
    val importedDownloads: List<LocalDownload> = emptyList(),
    val runtime: LocalRuntimeState = LocalRuntimeState.Idle,
    /** Local chat model ids currently reserved for background tasks. */
    val backendModelIds: Set<String> = emptySet(),
    /** Installed model ids that have a newer revision available. */
    val updates: Set<String> = emptySet(),
)

class SettingLocalLlmViewModel(
    private val context: Context,
    private val store: LocalModelStore,
    private val catalog: LiteRtCatalog,
    private val downloadManager: LocalDownloadManager,
    private val runtime: LiteRtRuntime,
    private val install: ModelInstall,
    private val embedder: LiteRtEmbedder,
    private val settingsStore: SettingsStore,
    private val modelCatalogService: ModelCatalogService,
    private val secretKeyManager: me.rerere.rikkahub.data.datastore.SecretKeyManager,
) : ViewModel() {
    
    val catalogSnapshot = modelCatalogService.snapshotFlow

    private val catalogFlow = MutableStateFlow(LocalModelCatalog())
    private val deviceRamGb = MemoryGuard.deviceTotalRamGb(context)
    
    val huggingFaceToken = MutableStateFlow(secretKeyManager.getHuggingFaceToken() ?: "")

    fun updateHuggingFaceToken(token: String) {
        huggingFaceToken.value = token
        secretKeyManager.setHuggingFaceToken(token)
    }

    val uiState: StateFlow<LocalLlmUiState> = combine(
        store.models,
        catalogFlow,
        downloadManager.downloads,
        runtime.state,
        settingsStore.settingsFlow,
    ) { installed, cat, downloads, runtimeState, settings ->
        val installedIds = installed.map { it.id }.toSet()
        // Hide on-device embedding models where the device ABI can't run the RAG native libraries.
        val embeddingSupported = embedder.isSupported
        val downloadable = cat.models
            .filter { it.id !in installedIds }
            .filter { embeddingSupported || it.kind != LocalModelKind.EMBEDDING }
        val updates = installed.filter { inst ->
            cat.models.firstOrNull { it.id == inst.id }?.let { it.commitHash != inst.commitHash } == true
        }.map { it.id }.toSet()

        LocalLlmUiState(
            installed = installed,
            downloadable = downloadable,
            deviceRamGb = deviceRamGb,
            downloads = downloads,
            importedDownloads = downloads.values.filter { it.isImported },
            runtime = runtimeState,
            backendModelIds = settings.providers
                .filterIsInstance<ProviderSetting.LiteRtLocal>()
                .flatMap { it.models }
                .filter { it.type == ModelType.CHAT && it.backend }
                .map { it.modelId }
                .toSet(),
            updates = updates,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalLlmUiState(deviceRamGb = deviceRamGb))

    init {
        viewModelScope.launch {
            val currentCatalog = catalog.catalog()
            catalogFlow.value = currentCatalog
            store.reconcileWithCatalog(currentCatalog)
        }
        viewModelScope.launch {
            val refreshedCatalog = catalog.refresh()
            catalogFlow.value = refreshedCatalog
            store.reconcileWithCatalog(refreshedCatalog)
        }
        // Keep the user-managed local provider's model list in sync with what's installed on disk.
        // Including settings in this flow lets a re-added local provider immediately regain the
        // already installed files.
        viewModelScope.launch {
            combine(store.models, settingsStore.settingsFlow) { installed, _ -> installed }
                .collectLatest { syncModelsToSettings(it) }
        }
    }

    fun download(meta: LocalModelMetadata) = downloadManager.download(meta)

    fun update(id: String) {
        catalogFlow.value.models.firstOrNull { it.id == id }?.let {
            downloadManager.download(it, isUpdate = true)
        }
    }

    fun cancelDownload(id: String) = downloadManager.cancel(id)

    fun dismissDownloadError(id: String) = downloadManager.dismissError(id)

    /** Returns null on success, or an error key when the URL is invalid. */
    fun installFromUrl(url: String): String? {
        val spec = ModelInstall.parseImportUrl(url) ?: return "invalid_url"
        downloadManager.downloadFromUrl(url, spec)
        return null
    }

    fun delete(model: InstalledLocalModel) {
        viewModelScope.launch {
            if (runtime.currentModelId() == model.id) runtime.unload()
            install.delete(model)
            store.remove(model.id)
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { store.rename(id, name.trim().ifBlank { id }) }
    }

    fun setIcon(id: String, uri: String?) {
        viewModelScope.launch { store.setIcon(id, uri) }
    }

    fun updateConfig(id: String, config: LocalModelConfig) {
        viewModelScope.launch { store.updateConfig(id, config) }
    }

    fun moveInstalledModel(from: Int, to: Int) {
        viewModelScope.launch { store.move(from, to) }
    }

    fun setChatModelBackend(id: String, backend: Boolean) {
        viewModelScope.launch {
            val settings = settingsStore.settingsFlow.value
            val updatedProviders = settings.providers.map { provider ->
                if (provider is ProviderSetting.LiteRtLocal) {
                    provider.copy(
                        models = provider.models.map { model ->
                            if (model.modelId == id && model.type == ModelType.CHAT) {
                                model.copy(backend = backend)
                            } else {
                                model
                            }
                        }
                    )
                } else {
                    provider
                }
            }
            if (updatedProviders != settings.providers) {
                settingsStore.update(settings.copy(providers = updatedProviders))
            }
        }
    }

    private suspend fun syncModelsToSettings(installed: List<InstalledLocalModel>) {
        val settings = settingsStore.settingsFlow.value
        val catalogSnapshot = modelCatalogService.snapshotFlow.value
        val local = settings.providers.filterIsInstance<ProviderSetting.LiteRtLocal>().firstOrNull() ?: return
        val existingByModelId = local.models.associateBy { it.modelId }
        val newModels = installed.map { it.toAiModel(existingByModelId[it.id], catalogSnapshot) }
        if (newModels == local.models) return
        val updatedProviders = settings.providers.map {
            if (it is ProviderSetting.LiteRtLocal) it.copy(models = newModels) else it
        }
        settingsStore.update(settings.copy(providers = updatedProviders))
    }

    private fun InstalledLocalModel.toAiModel(existing: Model?, catalogSnapshot: ModelCatalogSnapshot?): Model {
        val iconUrl = catalogSnapshot?.inferFamilyEntry(displayName)?.iconUrl
        if (isEmbedding) {
            // Embedding models: TEXT→TEXT, no tool/reasoning abilities; selectable as an EMBEDDING model.
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
        val input = buildList {
            add(Modality.TEXT)
            if (supportsImage) add(Modality.IMAGE)
            if (supportsAudio) add(Modality.AUDIO)
        }
        val abilities = buildList {
            add(ModelAbility.TOOL) // prompt-engineered tool calling for all local models
            if (supportsThinking) add(ModelAbility.REASONING)
        }
        return (existing ?: Model()).copy(
            modelId = id,
            displayName = displayName,
            type = ModelType.CHAT,
            inputModalities = input,
            outputModalities = listOf(Modality.TEXT),
            abilities = abilities,
            iconUrl = iconUrl,
            customIconUri = customIconUri,
        )
    }
}
