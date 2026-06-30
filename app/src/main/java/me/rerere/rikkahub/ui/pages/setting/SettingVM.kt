package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.ModelCatalogStatus
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.ai.models.mergeCatalogIntoSettings
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.IconStorageManager
import me.rerere.common.platform.PlatformHttpClient

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager,
    private val context: Context,
    private val platformHttpClient: PlatformHttpClient,
    private val appStorageRepository: AppStorageRepository,
    private val modelCatalogService: ModelCatalogService,
    private val modelMetadataResolver: ModelMetadataResolver,
    private val memoryRepository: MemoryRepository,
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))
    val modelCatalogStatus: StateFlow<ModelCatalogStatus> = modelCatalogService.status
    val modelCatalogSnapshot: StateFlow<ModelCatalogSnapshot?> = modelCatalogService.snapshotFlow

    fun updateSettings(
        newSettings: Settings,
        afterPersist: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            val oldSettings = settings.value
            settingsStore.update(newSettings)
            afterPersist?.invoke()
            
            // Check if providers were removed and trigger icon cleanup
            val oldProviderIds = oldSettings.providers.map { it.id }.toSet()
            val newProviderIds = newSettings.providers.map { it.id }.toSet()
            
            if (oldProviderIds != newProviderIds) {
                // Providers changed, schedule cleanup in background
                launch(Dispatchers.IO) {
                    cleanupUnusedIcons(newSettings)
                }
            }
        }
    }
    
    /**
     * Clean up icons that are no longer used by any model/provider.
     */
    private fun cleanupUnusedIcons(settings: Settings) {
        val iconManager = IconStorageManager.getInstance(context, platformHttpClient)
        
        // Collect all icon keys that are still in use
        val usedKeys = mutableSetOf<String>()
        
        for (provider in settings.providers) {
            // Add provider icon key (for both light and dark mode)
            val providerSlug = provider.name.lowercase().replace(" ", "-").replace("_", "-")
            usedKeys.add(IconStorageManager.generateIconKey(providerSlug, null, true))
            usedKeys.add(IconStorageManager.generateIconKey(providerSlug, null, false))
            
            // Add model icon keys
            for (model in provider.models) {
                val modelName = model.displayName.ifBlank { model.modelId }
                usedKeys.add(IconStorageManager.generateIconKey(model.providerSlug, modelName, true))
                usedKeys.add(IconStorageManager.generateIconKey(model.providerSlug, modelName, false))
                
                // Also add URL-based keys if model has iconUrl
                if (!model.iconUrl.isNullOrBlank()) {
                    usedKeys.add("url_${model.iconUrl.hashCode()}_dark")
                    usedKeys.add("url_${model.iconUrl.hashCode()}_light")
                }
            }
        }
        
        iconManager.cleanupUnusedIcons(usedKeys)
    }

    fun cleanupFilesIfUnreferenced(
        fileRefs: Collection<String>,
        delayMs: Long = 0L,
    ) {
        if (fileRefs.isEmpty()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            appStorageRepository.deleteFilesIfUnreferenced(fileRefs)
        }
    }

    fun refreshModelCatalog(
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                val status = modelCatalogService.refreshCatalog()
                modelCatalogService.snapshotOrNull()?.let { snapshot ->
                    settingsStore.update(
                        mergeCatalogIntoSettings(
                            settings = settings.value,
                            snapshot = snapshot,
                            resolver = modelMetadataResolver,
                        )
                    )
                }
                status
            }.onSuccess {
                onSuccess()
            }.onFailure {
                onError(it)
            }
        }
    }

    fun regenerateMemoryEmbeddings(
        onComplete: (success: Int, failure: Int) -> Unit = { _, _ -> },
        onError: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                var success = 0
                var failure = 0
                settings.value.assistants.forEach { assistant ->
                    val (assistantSuccess, assistantFailure) = memoryRepository.regenerateEmbeddings(
                        assistantId = assistant.id.toString(),
                        onProgress = { _, _ -> },
                    )
                    success += assistantSuccess
                    failure += assistantFailure
                }
                success to failure
            }.onSuccess { (success, failure) ->
                withContext(Dispatchers.Main) {
                    onComplete(success, failure)
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    onError(error)
                }
            }
        }
    }
}
