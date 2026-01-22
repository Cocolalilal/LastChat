package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.utils.IconStorageManager
import okhttp3.OkHttpClient

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager,
    private val context: Context,
    private val okHttpClient: OkHttpClient
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            val oldSettings = settings.value
            settingsStore.update(newSettings)
            
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
        val iconManager = IconStorageManager.getInstance(context, okHttpClient)
        
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
}
