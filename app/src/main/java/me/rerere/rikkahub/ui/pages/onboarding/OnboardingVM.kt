package me.rerere.rikkahub.ui.pages.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
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
import me.rerere.rikkahub.ui.pages.setting.components.withSpecialProviderPresets
import me.rerere.search.SearchServiceOptions
import kotlin.uuid.Uuid

class OnboardingVM(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val modelCatalogService: ModelCatalogService,
    private val modelMetadataResolver: ModelMetadataResolver,
) : ViewModel() {
    internal var accountSetupDraft: ProviderSetting.OpenAI? = null
    internal var resumeAccountSetup: Boolean = false
    private var localProviderBeforeSetup: ProviderSetting.LiteRtLocal? = null

    val settings: StateFlow<Settings> = settingsStore.settingsFlow

    val modelCatalogSnapshot: StateFlow<ModelCatalogSnapshot?> = modelCatalogService.snapshotFlow

    fun providerPresets(snapshot: ModelCatalogSnapshot?): List<ProviderPreset> {
        return (snapshot?.toProviderPresets() ?: emptyList())
            .withSpecialProviderPresets()
            .filterNot { it.type == ProviderSetting.ComfyUI::class }
            .prioritizeSetupProviders()
    }

    fun skipSetup(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsStore.update { it.copy(setupCompleted = true) }
            }
            onDone()
        }
    }

    fun beginLocalSetup(provider: ProviderSetting.LiteRtLocal, onReady: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val current = settings.value
                localProviderBeforeSetup = current.providers
                    .filterIsInstance<ProviderSetting.LiteRtLocal>()
                    .firstOrNull()
                val localProvider = localProviderBeforeSetup ?: provider
                settingsStore.update { current -> current.copy(
                        providers = listOf(localProvider) +
                            current.providers.filterNot { it is ProviderSetting.LiteRtLocal },
                    ) }
            }
            onReady()
        }
    }

    fun cancelLocalSetup(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val current = settings.value
                val restoredLocalProvider = localProviderBeforeSetup
                settingsStore.update { current -> current.copy(
                        providers = listOfNotNull(restoredLocalProvider) +
                            current.providers.filterNot { it is ProviderSetting.LiteRtLocal },
                    ) }
                localProviderBeforeSetup = null
            }
            onDone()
        }
    }

    fun completeLocalSetup(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val syncedSettings = withTimeoutOrNull(5_000) {
                    settingsStore.settingsFlow.first { current ->
                        current.providers
                            .filterIsInstance<ProviderSetting.LiteRtLocal>()
                            .firstOrNull()
                            ?.models
                            ?.any { it.type == ModelType.CHAT } == true
                    }
                } ?: settingsStore.settingsFlow.value
                val localProvider = syncedSettings.providers
                    .filterIsInstance<ProviderSetting.LiteRtLocal>()
                    .firstOrNull()
                val chatModel = localProvider?.models?.firstOrNull { it.type == ModelType.CHAT }
                val visionModel = localProvider?.models?.firstOrNull {
                    it.type == ModelType.CHAT && Modality.IMAGE in it.inputModalities
                }
                val embeddingModel = localProvider?.models?.firstOrNull {
                    it.type == ModelType.EMBEDDING
                }

                settingsStore.update { syncedSettings -> syncedSettings.copy(
                        setupCompleted = true,
                        chatModelId = chatModel?.id ?: syncedSettings.chatModelId,
                        titleModelId = chatModel?.id ?: syncedSettings.titleModelId,
                        summarizerModelId = chatModel?.id ?: syncedSettings.summarizerModelId,
                        ocrModelId = visionModel?.id ?: syncedSettings.ocrModelId,
                        embeddingModelId = embeddingModel?.id ?: syncedSettings.embeddingModelId,
                    ) }
                localProviderBeforeSetup = null
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
                val configuredProvider = provider.copyProvider(
                    enabled = true,
                    models = selectedModels,
                )
                settingsStore.update { current -> current.copy(
                    setupCompleted = true,
                    providers = listOf(configuredProvider),
                    chatModelId = roleModels.chat ?: current.chatModelId,
                    titleModelId = roleModels.title ?: current.titleModelId,
                    summarizerModelId = roleModels.summarizer,
                    ocrModelId = roleModels.ocr ?: current.ocrModelId,
                ) }
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
            is ProviderSetting.LiteRtLocal -> provider
        }
    }

    fun presetToProvider(preset: ProviderPreset): ProviderSetting {
        return preset.toProviderSetting()
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


}

/** Account providers and OpenCode are available before the bundled catalog finishes loading. */
internal fun List<ProviderPreset>.prioritizeSetupProviders(): List<ProviderPreset> {
    fun priority(preset: ProviderPreset): Int = when {
        me.rerere.ai.provider.providers.opencode.OpenCodeEndpoint.from(preset.baseUrl)?.go == true -> 0
        preset.subscriptionAccount == me.rerere.ai.provider.SubscriptionAccountType.CHATGPT -> 1
        preset.subscriptionAccount == me.rerere.ai.provider.SubscriptionAccountType.GROK -> 2
        else -> 3
    }
    return sortedBy(::priority).map { preset ->
        val rank = priority(preset)
        if (rank < 3) preset.copy(setupRecommended = true, setupOrder = rank - 100) else preset
    }
}

data class SetupRoleModels(
    val chat: Uuid? = null,
    val title: Uuid? = null,
    val summarizer: Uuid? = null,
    val ocr: Uuid? = null,
)
