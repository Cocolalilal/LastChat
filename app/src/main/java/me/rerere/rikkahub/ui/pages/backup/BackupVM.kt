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
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
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
        var importedConversations = 0
        val importedProviders = withContext(Dispatchers.IO) {
            val importProviders = arrayListOf<ProviderSetting>()
            val newAssistants = mutableListOf<me.rerere.rikkahub.data.model.Assistant>()
            val copilotIdToAssistantId = mutableMapOf<String, kotlin.uuid.Uuid>()

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

            // Parse Copilots (Assistants)
            jsonElements["myCopilots"]?.jsonArray?.forEach { element ->
                try {
                    val copilotObj = element.jsonObject
                    val copilotId = copilotObj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val name = copilotObj["name"]?.jsonPrimitive?.contentOrNull ?: "Imported Copilot"
                    val description = copilotObj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    val config = copilotObj["config"]?.jsonObject
                    val systemPrompt = config?.get("systemPrompt")?.jsonPrimitive?.contentOrNull ?: ""
                    
                    val assistantId = kotlin.uuid.Uuid.random()
                    copilotIdToAssistantId[copilotId] = assistantId
                    
                    val assistant = me.rerere.rikkahub.data.model.Assistant(
                        id = assistantId,
                        name = name,
                        systemPrompt = systemPrompt
                    )
                    newAssistants.add(assistant)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse copilot", e)
                }
            }

            // Parse conversations
            jsonElements.forEach { (key, element) ->
                if (key.startsWith("session:")) {
                    try {
                        val sessionObj = element.jsonObject
                        val messagesArray = sessionObj["messages"]?.jsonArray ?: return@forEach
                        val title = sessionObj["name"]?.jsonPrimitive?.contentOrNull ?: "Chatbox Import"
                        val copilotId = sessionObj["copilotId"]?.jsonPrimitive?.contentOrNull
                        
                        val mappedAssistantId = copilotIdToAssistantId[copilotId] 
                            ?: me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
                        
                        val uiMessages = messagesArray.mapNotNull { msgElement ->
                            val msgObj = msgElement.jsonObject
                            val roleStr = msgObj["role"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "user"
                            val content = msgObj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                            
                            val role = when (roleStr) {
                                "user" -> me.rerere.ai.core.MessageRole.USER
                                "assistant" -> me.rerere.ai.core.MessageRole.ASSISTANT
                                "system" -> me.rerere.ai.core.MessageRole.SYSTEM
                                else -> me.rerere.ai.core.MessageRole.USER
                            }
                            
                            if (content.isNotBlank()) {
                                me.rerere.ai.ui.UIMessage(
                                    id = kotlin.uuid.Uuid.random(),
                                    role = role,
                                    parts = listOf(me.rerere.ai.ui.UIMessagePart.Text(content))
                                )
                            } else {
                                null
                            }
                        }
                        
                        if (uiMessages.isNotEmpty()) {
                            val conversation = me.rerere.rikkahub.data.model.Conversation(
                                id = kotlin.uuid.Uuid.random(),
                                assistantId = mappedAssistantId,
                                title = title,
                                messageNodes = uiMessages.map { 
                                    me.rerere.rikkahub.data.model.MessageNode(
                                        messages = listOf(it), 
                                        selectIndex = 0
                                    ) 
                                }
                            )
                            conversationRepository.insertConversation(conversation)
                            importedConversations++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse session $key", e)
                    }
                }
            }
            
            Triple(importProviders.toList(), importedConversations, newAssistants)
        }

        val (importedProvidersList, importedConversationsCount, newAssistantsList) = importedProviders
        val resolvedProviders = importedProvidersList.map(modelMetadataResolver::applyToProvider)

        if (resolvedProviders.isEmpty() && importedConversationsCount == 0 && newAssistantsList.isEmpty()) {
            throw IllegalArgumentException("No importable data found in ChatBox export")
        }

        Log.i(TAG, "restoreFromChatBox: import ${resolvedProviders.size} providers, $importedConversationsCount conversations, ${newAssistantsList.size} assistants")
        
        settingsStore.update { current ->
            var updated = current
            if (resolvedProviders.isNotEmpty()) {
                updated = updated.copy(
                    providers = mergeImportedProviders(current.providers, resolvedProviders)
                )
            }
            if (newAssistantsList.isNotEmpty()) {
                updated = updated.copy(
                    assistants = current.assistants + newAssistantsList
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
            is ProviderSetting.Local -> "local|${provider.id}"
        }
    }
}
