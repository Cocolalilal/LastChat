package me.rerere.rikkahub.ui.pages.backup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.sync.WebDavBackupItem
import me.rerere.rikkahub.data.sync.importer.ChatboxImporter
import me.rerere.rikkahub.data.sync.importer.CherryStudioProviderImporter
import me.rerere.rikkahub.data.sync.WebdavSync
import me.rerere.rikkahub.utils.UiState
import java.io.File

private const val TAG = "BackupVM"

class BackupVM(
    private val settingsStore: SettingsStore,
    private val webdavSync: WebdavSync,
    private val modelMetadataResolver: ModelMetadataResolver,
    private val conversationRepository: me.rerere.rikkahub.data.repository.ConversationRepository,
) : ViewModel() {
    val settings = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings.dummy()
    )

    val webDavBackupItems = MutableStateFlow<UiState<List<WebDavBackupItem>>>(UiState.Idle)

    init {
        loadBackupFileItems()
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun loadBackupFileItems() {
        viewModelScope.launch {
            runCatching {
                webDavBackupItems.emit(UiState.Loading)
                webDavBackupItems.emit(
                    value = UiState.Success(
                        data = webdavSync.listBackupFiles(
                            webDavConfig = settings.value.webDavConfig
                        ).sortedByDescending { it.lastModified }
                    )
                )
            }.onFailure {
                webDavBackupItems.emit(UiState.Error(it))
            }
        }
    }

    suspend fun testWebDav() {
        webdavSync.testWebdav(settings.value.webDavConfig)
    }

    suspend fun backup() {
        webdavSync.backupToWebDav(settings.value.webDavConfig)
    }

    suspend fun restore(item: WebDavBackupItem): WebdavSync.RestoreResult {
        return webdavSync.restoreFromWebDav(webDavConfig = settings.value.webDavConfig, item = item)
    }

    suspend fun deleteWebDavBackupFile(item: WebDavBackupItem) {
        webdavSync.deleteWebDavBackupFile(settings.value.webDavConfig, item)
    }

    suspend fun exportToFile(): File {
        return webdavSync.prepareBackupFile(settings.value.webDavConfig.copy())
    }

    suspend fun restoreFromLocalFile(file: File): WebdavSync.RestoreResult {
        return webdavSync.restoreFromLocalFile(file, settings.value.webDavConfig)
    }

    suspend fun getAssistantsSnapshot(): List<Assistant> {
        return settingsStore.settingsFlow.first().assistants
    }
    
    fun restartApp(context: android.content.Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = android.content.Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        kotlin.system.exitProcess(0)
    }

    suspend fun restoreFromChatBox(file: File) {
        val imported = withContext(Dispatchers.IO) {
            ChatboxImporter.import(file)
        }

        val resolvedProviders = imported.providers.map(modelMetadataResolver::applyToProvider)

        if (resolvedProviders.isEmpty() && imported.conversations.isEmpty() && imported.assistants.isEmpty()) {
            throw IllegalArgumentException("No importable data found in ChatBox export")
        }

        imported.conversations.forEach { conversation ->
            conversationRepository.insertConversation(conversation)
        }

        Log.i(TAG, "restoreFromChatBox: import ${resolvedProviders.size} providers, ${imported.conversations.size} conversations, ${imported.assistants.size} assistants")
        
        settingsStore.update { current ->
            var updated = current
            if (resolvedProviders.isNotEmpty()) {
                updated = updated.copy(
                    providers = mergeImportedProviders(current.providers, resolvedProviders)
                )
            }
            if (imported.assistants.isNotEmpty()) {
                updated = updated.copy(
                    assistants = current.assistants + imported.assistants
                )
            }
            updated
        }
    }

    suspend fun restoreFromCherryStudio(file: File) {
        val importedProviders = withContext(Dispatchers.IO) {
            CherryStudioProviderImporter.importProviders(file)
        }

        val resolvedProviders = importedProviders.map(modelMetadataResolver::applyToProvider)

        if (resolvedProviders.isEmpty()) {
            throw IllegalArgumentException("No importable providers found in Cherry Studio backup")
        }

        Log.i(TAG, "restoreFromCherryStudio: import ${resolvedProviders.size} providers: $resolvedProviders")
        settingsStore.update { current ->
            current.copy(
                providers = mergeImportedProviders(current.providers, resolvedProviders)
            )
        }
    }

    private fun mergeImportedProviders(
        existingProviders: List<ProviderSetting>,
        importedProviders: List<ProviderSetting>,
    ): List<ProviderSetting> {
        val importedKeys = importedProviders.map(::providerImportKey).toSet()
        return importedProviders.distinctBy(::providerImportKey) +
            existingProviders.filterNot { providerImportKey(it) in importedKeys }
    }

    private fun providerImportKey(provider: ProviderSetting): String {
        return when (provider) {
            is ProviderSetting.OpenAI -> "openai|${provider.baseUrl}|${provider.apiKey}"
            is ProviderSetting.Google -> "google|${provider.baseUrl}|${provider.apiKey}"
            is ProviderSetting.Claude -> "claude|${provider.baseUrl}|${provider.apiKey}"
            is ProviderSetting.ComfyUI -> "comfyui|${provider.baseUrl}|${provider.workflowJson.hashCode()}"
        }
    }
}
