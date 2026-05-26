package me.rerere.rikkahub.ui.pages.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.pages.setting.components.ProviderPreset
import me.rerere.rikkahub.ui.pages.setting.components.toProviderPresets
import me.rerere.rikkahub.ui.pages.setting.components.toProviderSetting
import me.rerere.search.SearchServiceOptions
import kotlin.uuid.Uuid

class OnboardingVM(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val modelCatalogService: ModelCatalogService,
    private val modelMetadataResolver: ModelMetadataResolver,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    val modelCatalogSnapshot: StateFlow<ModelCatalogSnapshot?> = modelCatalogService.snapshotFlow

    fun providerPresets(snapshot: ModelCatalogSnapshot?): List<ProviderPreset> {
        return snapshot?.toProviderPresets() ?: emptyList()
    }

    fun skipSetup(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsStore.update(settings.value.copy(setupCompleted = true))
            }
            onDone()
        }
    }

    fun completeGuided(preset: ProviderPreset, apiKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val keyedProvider = providerWithKey(preset.toProviderSetting(), apiKey)
                val providerModels = fetchProviderModels(keyedProvider)
                val setupModels = providerModels.orderedSetupModels(preset.setupModelIds)
                val configuredProvider = keyedProvider
                    .copyProvider(models = setupModels)
                val nextSettings = applyDefaults(
                    settings = settings.value,
                    provider = configuredProvider,
                    defaults = preset.setupDefaults,
                    setupSearchService = preset.setupSearchService,
                    apiKey = apiKey,
                ).copy(setupCompleted = true)
                settingsStore.update(nextSettings)
            }
            onDone()
        }
    }

    fun saveManualProvider(
        provider: ProviderSetting,
        selectedModels: List<Model>,
        roleModels: SetupRoleModels,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val configuredProvider = provider.copyProvider(models = selectedModels)
                val current = settings.value
                val nextSettings = current.copy(
                    setupCompleted = true,
                    providers = listOf(configuredProvider),
                    chatModelId = roleModels.chat ?: current.chatModelId,
                    titleModelId = roleModels.title ?: current.titleModelId,
                    summarizerModelId = roleModels.summarizer,
                    ocrModelId = roleModels.ocr ?: current.ocrModelId,
                )
                settingsStore.update(nextSettings)
            }
            onDone()
        }
    }

    fun fetchModels(provider: ProviderSetting, onResult: (List<Model>) -> Unit) {
        viewModelScope.launch {
            val models = withContext(Dispatchers.IO) {
                fetchProviderModels(provider)
            }
            onResult(models)
        }
    }

    fun providerWithKey(provider: ProviderSetting, apiKey: String): ProviderSetting {
        return when (provider) {
            is ProviderSetting.OpenAI -> provider.copy(apiKey = apiKey)
            is ProviderSetting.Google -> provider.copy(apiKey = apiKey)
            is ProviderSetting.Claude -> provider.copy(apiKey = apiKey)
            is ProviderSetting.ComfyUI -> provider
        }
    }

    fun presetToProvider(preset: ProviderPreset): ProviderSetting {
        return preset.toProviderSetting()
    }

    private fun applyDefaults(
        settings: Settings,
        provider: ProviderSetting,
        defaults: me.rerere.rikkahub.data.ai.models.CatalogSetupDefaults?,
        setupSearchService: String?,
        apiKey: String,
    ): Settings {
        val modelsById = provider.models.associateBy { it.modelId }
        val searchServices = when (setupSearchService?.lowercase()) {
            "ollama" -> listOf(SearchServiceOptions.OllamaOptions(apiKey = apiKey)) +
                settings.searchServices.filterNot { it is SearchServiceOptions.OllamaOptions }
            else -> settings.searchServices
        }
        fun modelIdFor(apiId: String?): Uuid? {
            return apiId?.let(modelsById::get)?.id
        }
        return settings.copy(
            providers = listOf(provider),
            chatModelId = modelIdFor(defaults?.chat) ?: settings.chatModelId,
            titleModelId = modelIdFor(defaults?.title) ?: settings.titleModelId,
            summarizerModelId = modelIdFor(defaults?.summarizer) ?: settings.summarizerModelId,
            ocrModelId = modelIdFor(defaults?.ocr) ?: settings.ocrModelId,
            searchServices = searchServices,
            searchServiceSelected = if (setupSearchService?.lowercase() == "ollama") 0 else settings.searchServiceSelected,
        )
    }

    private suspend fun fetchProviderModels(provider: ProviderSetting): List<Model> {
        return runCatching {
            providerManager.getProviderByType(provider)
                .listModels(provider)
                .map { model ->
                    modelMetadataResolver.applyToModel(model, providerHint = provider)
                }
                .sortedBy { model -> model.displayName.ifBlank { model.modelId } }
        }.getOrElse {
            emptyList()
        }
    }

    private fun List<Model>.orderedSetupModels(setupModelIds: List<String>): List<Model> {
        if (setupModelIds.isEmpty()) return this
        val modelsById = associateBy { it.modelId }
        return setupModelIds.mapNotNull(modelsById::get)
    }
}

data class SetupRoleModels(
    val chat: Uuid? = null,
    val title: Uuid? = null,
    val summarizer: Uuid? = null,
    val ocr: Uuid? = null,
)
