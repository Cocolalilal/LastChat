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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.sync.WebDavBackupItem
import me.rerere.rikkahub.data.sync.importer.CherryStudioProviderImporter
import me.rerere.rikkahub.data.sync.WebdavSync
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UiState
import java.io.File

private const val TAG = "BackupVM"

class BackupVM(
    private val settingsStore: SettingsStore,
    private val webdavSync: WebdavSync,
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
        val importedProviders = withContext(Dispatchers.IO) {
            val importProviders = arrayListOf<ProviderSetting>()

            val jsonElements = JsonInstant.parseToJsonElement(file.readText()).jsonObject
            val settingsObj = jsonElements["settings"]?.jsonObject
            if (settingsObj != null) {
                settingsObj["providers"]?.jsonObject?.let { providers ->
                    providers["openai"]?.jsonObject?.let { openai ->
                        val apiHost = openai["apiHost"]?.jsonPrimitive?.contentOrNull ?: "https://api.openai.com"
                        val apiKey = openai["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                        val models = openai["models"]?.jsonArray?.map { element ->
                            val modelId = element.jsonObject["modelId"]?.jsonPrimitive?.contentOrNull ?: ""
                            val capabilities = element.jsonObject["capabilities"]?.jsonArray
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?: emptyList()
                            Model(
                                modelId = modelId,
                                displayName = modelId,
                                inputModalities = buildList {
                                    if (capabilities.contains("vision")) add(Modality.IMAGE)
                                },
                                abilities = buildList {
                                    if (capabilities.contains("tool_use")) add(ModelAbility.TOOL)
                                    if (capabilities.contains("reasoning")) add(ModelAbility.REASONING)
                                }
                            )
                        } ?: emptyList()
                        if (apiKey.isNotBlank()) {
                            importProviders.add(
                                ProviderSetting.OpenAI(
                                    name = "OpenAI",
                                    baseUrl = "$apiHost/v1",
                                    apiKey = apiKey,
                                    models = models,
                                )
                            )
                        }
                    }
                    providers["claude"]?.jsonObject?.let { claude ->
                        val apiHost =
                            claude["apiHost"]?.jsonPrimitive?.contentOrNull ?: "https://api.anthropic.com"
                        val apiKey = claude["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (apiKey.isNotBlank()) {
                            importProviders.add(
                                ProviderSetting.Claude(
                                    name = "Claude",
                                    baseUrl = "$apiHost/v1",
                                    apiKey = apiKey,
                                )
                            )
                        }
                    }
                    providers["gemini"]?.jsonObject?.let { gemini ->
                        val apiHost = gemini["apiHost"]?.jsonPrimitive?.contentOrNull
                            ?: "https://generativelanguage.googleapis.com"
                        val apiKey = gemini["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (apiKey.isNotBlank()) {
                            importProviders.add(
                                ProviderSetting.Google(
                                    name = "Gemini",
                                    baseUrl = "$apiHost/v1beta",
                                    apiKey = apiKey,
                                )
                            )
                        }
                    }
                }
            }
            importProviders.toList()
        }

        if (importedProviders.isEmpty()) {
            throw IllegalArgumentException("No importable providers found in ChatBox export")
        }

        Log.i(TAG, "restoreFromChatBox: import ${importedProviders.size} providers: $importedProviders")
        settingsStore.update { current ->
            current.copy(
                providers = mergeImportedProviders(current.providers, importedProviders)
            )
        }
    }

    suspend fun restoreFromCherryStudio(file: File) {
        val importedProviders = withContext(Dispatchers.IO) {
            CherryStudioProviderImporter.importProviders(file)
        }

        if (importedProviders.isEmpty()) {
            throw IllegalArgumentException("No importable providers found in Cherry Studio backup")
        }

        Log.i(TAG, "restoreFromCherryStudio: import ${importedProviders.size} providers: $importedProviders")
        settingsStore.update { current ->
            current.copy(
                providers = mergeImportedProviders(current.providers, importedProviders)
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
        }
    }
}
