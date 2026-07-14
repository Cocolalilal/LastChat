package me.rerere.lastchat.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.MessageChunk
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformPickedFile
import me.rerere.common.platform.PlatformPickedFileKind
import me.rerere.common.platform.SecureSettingsStore
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
enum class IosProviderType { OPENAI, GOOGLE, CLAUDE }

@Serializable
data class IosProviderPreferences(
    val type: IosProviderType = IosProviderType.OPENAI,
    val baseUrl: String = "https://api.openai.com/v1",
    val modelId: String = "gpt-4.1-mini",
)

@Serializable
enum class IosColorMode { SYSTEM, LIGHT, DARK }

@Serializable
data class IosAppearancePreferences(
    val themeId: String = "seafoam_mint",
    val colorMode: IosColorMode = IosColorMode.SYSTEM,
)

@Serializable
data class IosAssistantPreferences(
    val id: String = Uuid.random().toString(),
    val name: String = "Assistant",
    val systemPrompt: String = "",
)

@Serializable
data class IosConversation(
    val id: String = Uuid.random().toString(),
    val assistantId: String? = null,
    val title: String = "New chat",
    val messages: List<UIMessage> = emptyList(),
    val updatedAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
)

data class IosAppState(
    val loading: Boolean = true,
    val generating: Boolean = false,
    val conversations: List<IosConversation> = emptyList(),
    val selectedConversationId: String? = null,
    val provider: IosProviderPreferences = IosProviderPreferences(),
    val providerConfigurations: List<IosProviderPreferences> = listOf(IosProviderPreferences()),
    val appearance: IosAppearancePreferences = IosAppearancePreferences(),
    val assistants: List<IosAssistantPreferences> = listOf(IosAssistantPreferences(id = "default")),
    val selectedAssistantId: String? = "default",
    val pendingAttachments: List<PlatformPickedFile> = emptyList(),
    val hasApiKey: Boolean = false,
    val error: String? = null,
) {
    val selectedConversation: IosConversation?
        get() = conversations.firstOrNull { it.id == selectedConversationId }
    val assistant: IosAssistantPreferences
        get() = assistants.firstOrNull { it.id == selectedAssistantId }
            ?: assistants.firstOrNull()
            ?: IosAssistantPreferences(id = "default")
}

/**
 * Production-facing iOS state boundary. Conversation content is stored in the app container;
 * provider secrets are stored separately through Keychain-backed SecureSettingsStore.
 */
class IosAppController(
    private val fileStore: PlatformFileStore,
    private val secureStore: SecureSettingsStore,
    private val providerManager: ProviderManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val mutableState = MutableStateFlow(IosAppState())
    private var generationJob: Job? = null
    private val persistMutex = Mutex()
    val state: StateFlow<IosAppState> = mutableState.asStateFlow()

    fun initialize() {
        if (!mutableState.value.loading) return
        scope.launch {
            val stored = fileStore.readBytes(STATE_PATH)?.decodeToString()?.let { encoded ->
                runCatching { json.decodeFromString<IosStoredState>(encoded) }.getOrNull()
            }
            val legacyAssistant = stored?.assistant ?: IosAssistantPreferences()
            val assistants = stored?.assistants.orEmpty().ifEmpty { listOf(legacyAssistant) }
            val selectedAssistantId = stored?.selectedAssistantId
                ?.takeIf { selected -> assistants.any { it.id == selected } }
                ?: assistants.first().id
            val conversations = stored?.conversations.orEmpty()
                .ifEmpty { listOf(IosConversation(assistantId = selectedAssistantId)) }
                .map { conversation ->
                    if (conversation.assistantId == null) {
                        conversation.copy(assistantId = selectedAssistantId)
                    } else {
                        conversation
                    }
                }
            val selectedConversationId = stored?.selectedConversationId
                ?.takeIf { selected -> conversations.any { it.id == selected } }
                ?: conversations.first().id
            val restoredAssistantId = conversations
                .firstOrNull { it.id == selectedConversationId }
                ?.assistantId
                ?.takeIf { id -> assistants.any { it.id == id } }
                ?: selectedAssistantId
            val selectedProvider = stored?.provider ?: IosProviderPreferences()
            val providerConfigurations = IosProviderType.entries.map { type ->
                stored?.providerConfigurations.orEmpty().firstOrNull { it.type == type }
                    ?: selectedProvider.takeIf { it.type == type }
                    ?: defaultProviderPreferences(type)
            }
            mutableState.value = IosAppState(
                loading = false,
                conversations = conversations,
                selectedConversationId = selectedConversationId,
                provider = selectedProvider,
                providerConfigurations = providerConfigurations,
                appearance = stored?.appearance ?: IosAppearancePreferences(),
                assistants = assistants,
                selectedAssistantId = restoredAssistantId,
                pendingAttachments = stored?.pendingAttachments.orEmpty().mapNotNull { attachment ->
                    if (!fileStore.exists(attachment.storagePath)) return@mapNotNull null
                    fileStore.localUrl(attachment.storagePath)?.let { localUrl ->
                        PlatformPickedFile(
                            storagePath = attachment.storagePath,
                            localUrl = localUrl,
                            displayName = attachment.displayName,
                            mimeType = attachment.mimeType,
                            kind = attachment.kind,
                        )
                    }
                },
                hasApiKey = secureStore.readString(apiKeyName(stored?.provider?.type ?: IosProviderType.OPENAI))
                    .isNullOrBlank().not(),
            )
        }
    }

    fun selectConversation(id: String) {
        mutableState.update { current ->
            val conversation = current.conversations.firstOrNull { it.id == id }
            current.copy(
                selectedConversationId = id,
                selectedAssistantId = conversation?.assistantId ?: current.selectedAssistantId,
                error = null,
            )
        }
        persistAsync()
    }

    fun newConversation() {
        val conversation = IosConversation(assistantId = mutableState.value.assistant.id)
        mutableState.update {
            it.copy(
                conversations = listOf(conversation) + it.conversations,
                selectedConversationId = conversation.id,
                error = null,
            )
        }
        persistAsync()
    }

    fun renameConversation(id: String, title: String) {
        updateConversation(id) { conversation ->
            conversation.copy(title = title.trim().ifBlank { "New chat" })
        }
        persistAsync()
    }

    fun deleteConversation(id: String) {
        mutableState.update { current ->
            var remaining = current.conversations.filterNot { it.id == id }
            var selectedConversationId = current.selectedConversationId
            if (current.selectedConversationId == id) {
                val replacement = remaining
                    .filter { it.assistantId == current.assistant.id }
                    .maxByOrNull { it.updatedAtEpochMs }
                    ?: IosConversation(assistantId = current.assistant.id).also {
                        remaining = listOf(it) + remaining
                    }
                selectedConversationId = replacement.id
            }
            current.copy(
                conversations = remaining,
                selectedConversationId = selectedConversationId,
                error = null,
            )
        }
        persistAsync()
    }

    fun saveProvider(type: IosProviderType, baseUrl: String, modelId: String, apiKey: String) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedModel = modelId.trim()
        if (normalizedBaseUrl.isBlank() || normalizedModel.isBlank()) {
            mutableState.update { it.copy(error = "Base URL and model ID are required.") }
            return
        }
        scope.launch {
            if (apiKey.isNotBlank()) secureStore.writeString(apiKeyName(type), apiKey.trim())
            val hasSavedKey = apiKey.isNotBlank() ||
                secureStore.readString(apiKeyName(type)).isNullOrBlank().not()
            mutableState.update {
                it.copy(
                    provider = IosProviderPreferences(type, normalizedBaseUrl, normalizedModel),
                    providerConfigurations = it.providerConfigurations
                        .filterNot { preferences -> preferences.type == type } +
                        IosProviderPreferences(type, normalizedBaseUrl, normalizedModel),
                    hasApiKey = hasSavedKey,
                    error = null,
                )
            }
            persist()
        }
    }

    fun clearApiKey() {
        scope.launch {
            secureStore.remove(apiKeyName(mutableState.value.provider.type))
            mutableState.update { it.copy(hasApiKey = false) }
        }
    }

    fun saveAppearance(themeId: String, colorMode: IosColorMode) {
        mutableState.update {
            it.copy(appearance = IosAppearancePreferences(themeId, colorMode))
        }
        persistAsync()
    }

    fun saveAssistant(name: String, systemPrompt: String) {
        mutableState.update { current ->
            val selectedId = current.assistant.id
            current.copy(
                assistants = current.assistants.map { assistant ->
                    if (assistant.id == selectedId) {
                        assistant.copy(
                            name = name.trim().ifBlank { "Assistant" },
                            systemPrompt = systemPrompt.trim(),
                        )
                    } else {
                        assistant
                    }
                },
            )
        }
        persistAsync()
    }

    fun newAssistant() {
        val assistant = IosAssistantPreferences(name = "New assistant")
        val conversation = IosConversation(assistantId = assistant.id)
        mutableState.update { current ->
            current.copy(
                assistants = current.assistants + assistant,
                selectedAssistantId = assistant.id,
                conversations = listOf(conversation) + current.conversations,
                selectedConversationId = conversation.id,
                error = null,
            )
        }
        persistAsync()
    }

    fun selectAssistant(id: String) {
        mutableState.update { current ->
            if (current.assistants.none { it.id == id }) return@update current
            val existingConversation = current.conversations
                .filter { it.assistantId == id }
                .maxByOrNull { it.updatedAtEpochMs }
            if (existingConversation != null) {
                current.copy(
                    selectedAssistantId = id,
                    selectedConversationId = existingConversation.id,
                    error = null,
                )
            } else {
                val conversation = IosConversation(assistantId = id)
                current.copy(
                    selectedAssistantId = id,
                    conversations = listOf(conversation) + current.conversations,
                    selectedConversationId = conversation.id,
                    error = null,
                )
            }
        }
        persistAsync()
    }

    fun deleteAssistant(id: String) {
        mutableState.update { current ->
            if (current.assistants.size <= 1 || current.assistants.none { it.id == id }) {
                return@update current
            }
            val remainingAssistants = current.assistants.filterNot { it.id == id }
            val replacement = remainingAssistants.first()
            val conversations = current.conversations.map { conversation ->
                if (conversation.assistantId == id) {
                    conversation.copy(assistantId = replacement.id)
                } else {
                    conversation
                }
            }
            val selectedConversation = conversations.firstOrNull { it.id == current.selectedConversationId }
            current.copy(
                assistants = remainingAssistants,
                selectedAssistantId = selectedConversation?.assistantId ?: replacement.id,
                conversations = conversations,
                error = null,
            )
        }
        persistAsync()
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    fun handlePickedFile(result: Result<PlatformPickedFile?>) {
        result.fold(
            onSuccess = { file ->
                if (file != null) {
                    mutableState.update { current ->
                        current.copy(
                            pendingAttachments = current.pendingAttachments + file,
                            error = null,
                        )
                    }
                    persistAsync()
                }
            },
            onFailure = { failure ->
                mutableState.update {
                    it.copy(error = failure.message ?: "The selected file could not be attached.")
                }
            },
        )
    }

    fun removePendingAttachment(storagePath: String) {
        mutableState.update { current ->
            current.copy(
                pendingAttachments = current.pendingAttachments.filterNot { it.storagePath == storagePath },
            )
        }
        scope.launch {
            fileStore.delete(storagePath)
            persist()
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
    }

    fun send(text: String) {
        val prompt = text.trim()
        val snapshot = mutableState.value
        val conversation = snapshot.selectedConversation ?: return
        if ((prompt.isEmpty() && snapshot.pendingAttachments.isEmpty()) || snapshot.generating) return
        generationJob = scope.launch {
            val preferences = snapshot.provider
            val apiKey = secureStore.readString(apiKeyName(preferences.type))
            if (apiKey.isNullOrBlank()) {
                mutableState.update { it.copy(error = "Configure an API key in Settings first.") }
                generationJob = null
                return@launch
            }
            val userParts = buildList {
                if (prompt.isNotEmpty()) add(UIMessagePart.Text(prompt))
                snapshot.pendingAttachments.forEach { attachment ->
                    add(when (attachment.kind) {
                        PlatformPickedFileKind.Image -> UIMessagePart.Image(attachment.localUrl)
                        PlatformPickedFileKind.Video -> UIMessagePart.Video(attachment.localUrl)
                        PlatformPickedFileKind.Audio -> UIMessagePart.Audio(attachment.localUrl)
                        PlatformPickedFileKind.Document -> UIMessagePart.Document(
                            url = attachment.localUrl,
                            fileName = attachment.displayName,
                            mime = attachment.mimeType,
                        )
                    })
                }
            }
            val userMessage = UIMessage(role = MessageRole.USER, parts = userParts)
            val assistantMessage = UIMessage.assistant("")
            updateConversation(conversation.id) { current ->
                current.copy(
                    title = if (current.messages.isEmpty()) {
                        prompt.ifBlank { snapshot.pendingAttachments.first().displayName }.take(48)
                    } else current.title,
                    messages = current.messages + userMessage + assistantMessage,
                )
            }
            mutableState.update {
                it.copy(generating = true, pendingAttachments = emptyList(), error = null)
            }
            persist()

            val model = Model(modelId = preferences.modelId, displayName = preferences.modelId)
            val systemMessages = snapshot.assistant.systemPrompt.takeIf { it.isNotBlank() }
                ?.let { listOf(UIMessage.system(it)) }.orEmpty()
            val requestMessages = systemMessages + conversation.messages + userMessage
            var lastCheckpointAt = Clock.System.now().toEpochMilliseconds()
            try {
                providerFlow(preferences, apiKey, model, requestMessages).collect { chunk ->
                    updateConversation(conversation.id) { current ->
                        val currentMessages = current.messages
                        val last = currentMessages.lastOrNull()
                        if (last?.role != MessageRole.ASSISTANT) current else current.copy(
                            messages = currentMessages.dropLast(1) + (last + chunk),
                        )
                    }
                    val now = Clock.System.now().toEpochMilliseconds()
                    if (now - lastCheckpointAt >= STREAMING_CHECKPOINT_INTERVAL_MS) {
                        persist()
                        lastCheckpointAt = now
                    }
                }
            } catch (cancellation: CancellationException) {
                updateConversation(conversation.id) { current ->
                    val last = current.messages.lastOrNull()
                    if (last?.role == MessageRole.ASSISTANT && last.toText().isBlank()) {
                        current.copy(messages = current.messages.dropLast(1))
                    } else {
                        current
                    }
                }
                throw cancellation
            } catch (failure: Throwable) {
                mutableState.update {
                    it.copy(error = failure.message ?: "Generation failed")
                }
            } finally {
                mutableState.update { it.copy(generating = false) }
                persist()
                generationJob = null
            }
        }
    }

    private fun updateConversation(id: String, transform: (IosConversation) -> IosConversation) {
        mutableState.update { current ->
            current.copy(conversations = current.conversations.map { conversation ->
                if (conversation.id == id) {
                    transform(conversation).copy(updatedAtEpochMs = Clock.System.now().toEpochMilliseconds())
                } else conversation
            })
        }
    }

    private suspend fun providerFlow(
        preferences: IosProviderPreferences,
        apiKey: String,
        model: Model,
        messages: List<UIMessage>,
    ): kotlinx.coroutines.flow.Flow<MessageChunk> = when (preferences.type) {
        IosProviderType.OPENAI -> {
            val setting = ProviderSetting.OpenAI(
                name = "OpenAI compatible", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).streamText(
                setting, messages, TextGenerationParams(model = model),
            )
        }
        IosProviderType.GOOGLE -> {
            val setting = ProviderSetting.Google(
                name = "Google", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).streamText(
                setting, messages, TextGenerationParams(model = model),
            )
        }
        IosProviderType.CLAUDE -> {
            val setting = ProviderSetting.Claude(
                name = "Claude", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).streamText(
                setting, messages, TextGenerationParams(model = model),
            )
        }
    }

    private fun persistAsync() {
        scope.launch { persist() }
    }

    private suspend fun persist() = persistMutex.withLock {
        val snapshot = mutableState.value
        val stored = IosStoredState(
            conversations = snapshot.conversations,
            selectedConversationId = snapshot.selectedConversationId,
            provider = snapshot.provider,
            providerConfigurations = snapshot.providerConfigurations,
            appearance = snapshot.appearance,
            assistants = snapshot.assistants,
            selectedAssistantId = snapshot.selectedAssistantId,
            pendingAttachments = snapshot.pendingAttachments.map { attachment ->
                IosStoredPendingAttachment(
                    storagePath = attachment.storagePath,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                    kind = attachment.kind,
                )
            },
        )
        fileStore.writeBytes(STATE_PATH, json.encodeToString(stored).encodeToByteArray())
    }

    private companion object {
        const val STATE_PATH = "state/ios-app.json"
        const val STREAMING_CHECKPOINT_INTERVAL_MS = 1_000L
        fun apiKeyName(type: IosProviderType): String = "provider_apikey_ios_${type.name.lowercase()}"
        fun defaultProviderPreferences(type: IosProviderType): IosProviderPreferences = when (type) {
            IosProviderType.OPENAI -> IosProviderPreferences(type, "https://api.openai.com/v1", "gpt-4.1-mini")
            IosProviderType.GOOGLE -> IosProviderPreferences(type, "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash")
            IosProviderType.CLAUDE -> IosProviderPreferences(type, "https://api.anthropic.com/v1", "claude-sonnet-4-5")
        }
    }
}

@Serializable
private data class IosStoredState(
    val conversations: List<IosConversation> = emptyList(),
    val selectedConversationId: String? = null,
    val provider: IosProviderPreferences = IosProviderPreferences(),
    val providerConfigurations: List<IosProviderPreferences> = emptyList(),
    val appearance: IosAppearancePreferences = IosAppearancePreferences(),
    val assistants: List<IosAssistantPreferences> = emptyList(),
    val selectedAssistantId: String? = null,
    val assistant: IosAssistantPreferences? = null,
    val pendingAttachments: List<IosStoredPendingAttachment> = emptyList(),
)

@Serializable
private data class IosStoredPendingAttachment(
    val storagePath: String,
    val displayName: String,
    val mimeType: String,
    val kind: PlatformPickedFileKind,
)
