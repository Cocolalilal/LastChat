package me.rerere.lastchat.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.ai.memory.MemoryVectorMath
import me.rerere.ai.memory.PortableMemoryChunker
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ImageGenerationMethod
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.withComfyDefaults
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformPickedFile
import me.rerere.common.platform.PlatformPickedFileKind
import me.rerere.common.platform.SecureSettingsStore
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.rikkahub.data.memory.MemoryExtractionEnvelope
import me.rerere.rikkahub.data.memory.MemoryOperationType
import me.rerere.rikkahub.data.memory.RecallKind
import me.rerere.rikkahub.data.memory.SourceMessage
import me.rerere.rikkahub.data.memory.TemporalRecallItem
import me.rerere.rikkahub.data.memory.TemporalRecallPacket
import me.rerere.rikkahub.data.memory.buildTemporalMemoryExtractionPrompt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import me.rerere.lastchat.ios.models.*
import me.rerere.lastchat.ios.backup.IosBackupManager

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
    val themeId: String = "ios",
    val colorMode: IosColorMode = IosColorMode.SYSTEM,
    val usePhoneSystemFont: Boolean = false,
    val showAssistantBubbles: Boolean = true,
    val fontSizeRatio: Float = 1.0f,
    val rpStyleRules: List<IosRpStyleRule> = emptyList(),
)

@Serializable
data class IosRpStyleRule(
    val id: String = Uuid.random().toString(),
    val pattern: String = "*",
    val colorHex: String = "#808080",
    val enabled: Boolean = true,
)

@Serializable
data class IosAssistantPreferences(
    val id: String = Uuid.random().toString(),
    val name: String = "Assistant",
    val systemPrompt: String = "",
    val memoryMode: IosMemoryMode = IosMemoryMode.OFF,
    val embeddingProviderType: IosProviderType = IosProviderType.OPENAI,
    val embeddingModelId: String = "text-embedding-3-small",
    val ragSimilarityThreshold: Float = 0.45f,
    val ragLimit: Int = 10,
    val localTools: Set<IosLocalToolOption> = emptySet(),
)

@Serializable
enum class IosLocalToolOption { JAVASCRIPT, NOTIFICATIONS, TTS, ASK_USER, IMAGE_GENERATION }

@Serializable
enum class IosMemoryMode { OFF, BASIC, SEARCHABLE, ADAPTIVE }

@Serializable
data class IosMemoryRecord(
    val id: Int,
    val assistantId: String,
    val content: String,
    val type: Int = 0,
    val embeddings: List<List<Float>>? = null,
    val embeddingModelId: String? = null,
    val timestampEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    val significance: Int? = null,
    val claimSubject: String? = null,
    val claimPredicate: String? = null,
    val claimObject: String? = null,
    val confidence: Float = 1f,
    val reality: String = "real",
    val active: Boolean = true,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null,
    val sceneKey: String? = null,
)

@Serializable
enum class IosSearchProviderType {
    BING, TAVILY, EXA, BRAVE, PERPLEXITY, FIRECRAWL, JINA, LINKUP,
    ZHIPU, METASO, BOCHA, OLLAMA, GROK, NANOGPT,
}

@Serializable
data class IosSearchPreferences(
    val enabled: Boolean = false,
    val provider: IosSearchProviderType = IosSearchProviderType.BING,
    val resultSize: Int = 5,
)

@Serializable
enum class IosTtsProviderType {
    OPENAI, GEMINI, MINIMAX, ELEVENLABS, QWEN, FISH_AUDIO, CARTESIA, PLAY_HT,
}

/** Persistable TTS configuration. Credentials are deliberately absent and live in Keychain. */
@Serializable
data class IosTtsPreferences(
    val enabled: Boolean = false,
    val type: IosTtsProviderType = IosTtsProviderType.OPENAI,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini-tts",
    val voice: String = "alloy",
    val secondary: String = "",
    val language: String = "Auto",
    val emotion: String = "neutral",
    val speed: Float = 1.0f,
)

@Serializable
data class IosImageGenerationPreferences(
    val enabled: Boolean = false,
    val providerType: IosImageProviderType = IosImageProviderType.OPENAI,
    val modelId: String = "gpt-image-1",
    val method: ImageGenerationMethod = ImageGenerationMethod.DIFFUSION,
    val comfyUi: IosComfyUiPreferences = IosComfyUiPreferences(),
)

@Serializable
enum class IosImageProviderType { OPENAI, GOOGLE, COMFY_UI }

@Serializable
data class IosComfyUiPreferences(
    val baseUrl: String = "http://127.0.0.1:8188",
    val workflowJson: String = "",
    val promptNodeId: String = "",
    val promptInputName: String = "text",
    val modelNodeId: String = "",
    val modelInputName: String = "ckpt_name",
)

@Serializable
data class IosGeneratedImage(
    val uri: String,
    val path: String,
    val prompt: String,
    val modelName: String,
    val createdAtEpochMs: Long,
)

@Serializable
data class IosConversation(
    val id: String = Uuid.random().toString(),
    val assistantId: String? = null,
    val title: String = "New chat",
    val messages: List<UIMessage> = emptyList(),
    val updatedAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    val memoryLastMessageId: String? = null,
    val isPinned: Boolean = false,
)

@Serializable
data class IosScheduledMessage(
    val id: String = Uuid.random().toString(),
    val assistantId: String,
    val conversationId: String,
    val reason: String,
    val createdAtEpochMs: Long,
    val scheduledAtEpochMs: Long,
    val nextAttemptEpochMs: Long = scheduledAtEpochMs,
    val attemptCount: Int = 0,
)

@Serializable
data class IosNotificationRecord(
    val title: String,
    val content: String,
    val postTimeEpochMs: Long,
)

@Serializable
data class IosAskUserOption(val label: String, val description: String? = null)

@Serializable
data class IosAskUserQuestion(
    val id: String,
    val question: String,
    val options: List<IosAskUserOption> = emptyList(),
)

@Serializable
data class IosPendingQuestionnaire(
    val toolCallId: String,
    val conversationId: String,
    val questions: List<IosAskUserQuestion>,
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
    val search: IosSearchPreferences = IosSearchPreferences(),
    val memories: List<IosMemoryRecord> = emptyList(),
    val tts: IosTtsPreferences = IosTtsPreferences(),
    val imageGeneration: IosImageGenerationPreferences = IosImageGenerationPreferences(),
    val generatedImages: List<IosGeneratedImage> = emptyList(),
    val imageGenerating: Boolean = false,
    val scheduledMessages: List<IosScheduledMessage> = emptyList(),
    val notificationHistory: List<IosNotificationRecord> = emptyList(),
    val pendingQuestionnaire: IosPendingQuestionnaire? = null,
    val pendingAttachments: List<PlatformPickedFile> = emptyList(),
    val hasApiKey: Boolean = false,
    val hasSearchApiKey: Boolean = false,
    val hasTtsApiKey: Boolean = false,
    val ttsSpeaking: Boolean = false,
    val ttsPlaybackState: PlaybackState = PlaybackState(),
    val skills: List<IosSkill> = emptyList(),
    val lorebooks: List<IosLorebook> = emptyList(),
    val mcpServers: List<IosMcpServerConfig> = emptyList(),
    val webDavConfig: IosWebDavConfig = IosWebDavConfig(),
    val error: String? = null,
) {
    val selectedConversation: IosConversation?
        get() = conversations.firstOrNull { it.id == selectedConversationId }
    val assistant: IosAssistantPreferences
        get() = assistants.firstOrNull { it.id == selectedAssistantId }
            ?: assistants.firstOrNull()
            ?: IosAssistantPreferences(id = "default")
    val assistantMemories: List<IosMemoryRecord>
        get() = memories.filter { it.assistantId == assistant.id && (it.active || it.type == 1) }
}

/**
 * Production-facing iOS state boundary. Conversation content is stored in the app container;
 * provider secrets are stored separately through Keychain-backed SecureSettingsStore.
 */
class IosAppController(
    private val fileStore: PlatformFileStore,
    private val secureStore: SecureSettingsStore,
    private val httpClient: PlatformHttpClient,
    private val javaScriptExecutor: IosJavaScriptExecutor,
    private val providerManager: ProviderManager,
    private val ttsController: TtsController,
    private val notificationPlatform: IosLocalNotificationPlatform,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val mutableState = MutableStateFlow(IosAppState())
    private var generationJob: Job? = null
    private var imageGenerationJob: Job? = null
    private val adaptiveMemoryJobs = mutableMapOf<String, Job>()
    private var scheduleAdaptiveBackground: ((Long) -> Unit)? = null
    private var cancelAdaptiveBackground: (() -> Unit)? = null
    private val scheduledMessageJobs = mutableMapOf<String, Job>()
    private val askUserWaiters = mutableMapOf<String, CompletableDeferred<JsonObject>>()
    private var currentExecutingToolCallId: String? = null
    private var scheduleMessageBackground: ((Long) -> Unit)? = null
    private var cancelMessageBackground: (() -> Unit)? = null
    private val persistMutex = Mutex()
    private val adaptiveMemoryMutex = Mutex()
    private val scheduledMessageMutex = Mutex()
    val state: StateFlow<IosAppState> = mutableState.asStateFlow()

    init {
        scope.launch {
            ttsController.isSpeaking.collect { speaking ->
                mutableState.update { it.copy(ttsSpeaking = speaking) }
            }
        }
        scope.launch {
            ttsController.playbackState.collect { playback ->
                mutableState.update { it.copy(ttsPlaybackState = playback) }
            }
        }
        scope.launch {
            ttsController.error.collect { error ->
                if (!error.isNullOrBlank()) mutableState.update { it.copy(error = error) }
            }
        }
    }

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
            val ttsPreferences = stored?.tts ?: IosTtsPreferences()
            val ttsApiKey = secureStore.readString(ttsApiKeyName(ttsPreferences.type)).orEmpty()
            mutableState.value = IosAppState(
                loading = false,
                conversations = conversations,
                selectedConversationId = selectedConversationId,
                provider = selectedProvider,
                providerConfigurations = providerConfigurations,
                appearance = stored?.appearance ?: IosAppearancePreferences(),
                assistants = assistants,
                selectedAssistantId = restoredAssistantId,
                search = stored?.search ?: IosSearchPreferences(),
                memories = stored?.memories.orEmpty(),
                skills = stored?.skills.orEmpty(),
                lorebooks = stored?.lorebooks.orEmpty(),
                mcpServers = stored?.mcpServers.orEmpty(),
                webDavConfig = stored?.webDavConfig ?: IosWebDavConfig(),
                tts = ttsPreferences,
                imageGeneration = stored?.imageGeneration ?: IosImageGenerationPreferences(),
                generatedImages = stored?.generatedImages.orEmpty(),
                scheduledMessages = stored?.scheduledMessages.orEmpty(),
                notificationHistory = stored?.notificationHistory.orEmpty(),
                pendingQuestionnaire = stored?.pendingQuestionnaire,
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
                hasSearchApiKey = hasSearchApiKey(stored?.search ?: IosSearchPreferences()),
                hasTtsApiKey = ttsApiKey.isNotBlank(),
            )
            ttsController.setProvider(
                ttsPreferences.takeIf { it.enabled && ttsApiKey.isNotBlank() }
                    ?.toProviderSetting(ttsApiKey)
            )
            conversations
                .filter { conversation ->
                    assistants.firstOrNull { it.id == conversation.assistantId }?.memoryMode ==
                        IosMemoryMode.ADAPTIVE
                }
                .forEach { scheduleAdaptiveMemory(it.id) }
            refreshAdaptiveBackgroundSchedule()
            mutableState.value.scheduledMessages.forEach(::scheduleMessageInProcess)
            refreshScheduledMessageBackgroundSchedule()
        }
    }

    fun installAdaptiveBackgroundScheduler(
        schedule: (earliestBeginEpochMs: Long) -> Unit,
        cancel: () -> Unit,
    ) {
        scheduleAdaptiveBackground = schedule
        cancelAdaptiveBackground = cancel
        refreshAdaptiveBackgroundSchedule()
    }

    fun runAdaptiveBackgroundMaintenance(completion: (Boolean) -> Unit) {
        scope.launch {
            val snapshot = mutableState.value
            val conversationIds = snapshot.conversations
                .filter { conversation ->
                    snapshot.assistants.firstOrNull { it.id == conversation.assistantId }
                        ?.memoryMode == IosMemoryMode.ADAPTIVE &&
                        pendingAdaptiveMessages(conversation).isNotEmpty()
                }
                .map(IosConversation::id)
            var succeeded = true
            conversationIds.forEach { conversationId ->
                adaptiveMemoryJobs.remove(conversationId)?.cancel()
                val result = runCatching {
                    adaptiveMemoryMutex.withLock { consolidateAdaptiveMemory(conversationId) }
                }
                if (result.isFailure) succeeded = false
            }
            refreshAdaptiveBackgroundSchedule()
            completion(succeeded)
        }
    }

    fun installScheduledMessageBackgroundScheduler(
        schedule: (earliestBeginEpochMs: Long) -> Unit,
        cancel: () -> Unit,
    ) {
        scheduleMessageBackground = schedule
        cancelMessageBackground = cancel
        refreshScheduledMessageBackgroundSchedule()
    }

    fun runScheduledMessageBackgroundMaintenance(completion: (Boolean) -> Unit) {
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val due = mutableState.value.scheduledMessages.filter { it.nextAttemptEpochMs <= now }
            var succeeded = true
            due.forEach { scheduled ->
                scheduledMessageJobs.remove(scheduled.id)?.cancel()
                runCatching { deliverScheduledMessage(scheduled.id) }
                    .onFailure {
                        succeeded = false
                        recordScheduledMessageFailure(scheduled.id)
                    }
            }
            persist()
            refreshScheduledMessageBackgroundSchedule()
            completion(succeeded)
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
        refreshAdaptiveBackgroundSchedule()
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

    fun togglePinConversation(id: String) {
        updateConversation(id) { conversation ->
            conversation.copy(isPinned = !conversation.isPinned)
        }
        persistAsync()
    }

    fun deleteConversation(id: String) {
        adaptiveMemoryJobs.remove(id)?.cancel()
        mutableState.value.pendingQuestionnaire?.takeIf { it.conversationId == id }?.let { pending ->
            askUserWaiters.remove(pending.toolCallId)?.cancel()
        }
        mutableState.value.scheduledMessages
            .filter { it.conversationId == id }
            .forEach { scheduledMessageJobs.remove(it.id)?.cancel() }
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
                scheduledMessages = current.scheduledMessages.filterNot { it.conversationId == id },
                pendingQuestionnaire = current.pendingQuestionnaire?.takeUnless { it.conversationId == id },
                error = null,
            )
        }
        persistAsync()
        refreshAdaptiveBackgroundSchedule()
        refreshScheduledMessageBackgroundSchedule()
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

    fun selectDefaultModel(type: IosProviderType, modelId: String) {
        val configuration = mutableState.value.providerConfigurations
            .firstOrNull { it.type == type }
            ?: return
        val normalizedModelId = modelId.trim()
        if (normalizedModelId.isBlank()) return
        scope.launch {
            val selected = configuration.copy(modelId = normalizedModelId)
            val hasSavedKey = secureStore.readString(apiKeyName(type)).isNullOrBlank().not()
            mutableState.update { current ->
                current.copy(
                    provider = selected,
                    providerConfigurations = current.providerConfigurations
                        .filterNot { it.type == type } + selected,
                    hasApiKey = hasSavedKey,
                    error = null,
                )
            }
            persist()
        }
    }

    fun saveSearch(
        provider: IosSearchProviderType,
        enabled: Boolean,
        resultSize: Int,
        apiKey: String,
    ) {
        scope.launch {
            val existingKey = secureStore.readString(searchApiKeyName(provider))
            if (enabled && provider.requiresApiKey() && apiKey.isBlank() && existingKey.isNullOrBlank()) {
                mutableState.update {
                    it.copy(error = "Configure the ${provider.displayName()} search API key first.")
                }
                return@launch
            }
            if (apiKey.isNotBlank()) {
                secureStore.writeString(searchApiKeyName(provider), apiKey.trim())
            }
            val preferences = IosSearchPreferences(
                enabled = enabled,
                provider = provider,
                resultSize = resultSize.coerceIn(1, 10),
            )
            mutableState.update {
                it.copy(
                    search = preferences,
                    hasSearchApiKey = hasSearchApiKey(preferences),
                    error = null,
                )
            }
            persist()
        }
    }

    fun toggleSearch(enabled: Boolean) {
        saveSearch(
            provider = mutableState.value.search.provider,
            enabled = enabled,
            resultSize = mutableState.value.search.resultSize,
            apiKey = "",
        )
    }

    fun clearSearchApiKey() {
        scope.launch {
            secureStore.remove(searchApiKeyName(mutableState.value.search.provider))
            mutableState.update { it.copy(hasSearchApiKey = false) }
        }
    }

    fun saveTts(
        preferences: IosTtsPreferences,
        apiKey: String,
    ) {
        scope.launch {
            runCatching {
                require(preferences.baseUrl.isNotBlank()) { "TTS base URL is required" }
                require(preferences.model.isNotBlank()) { "TTS model is required" }
                require(preferences.voice.isNotBlank()) { "TTS voice is required" }
                require(preferences.speed in 0.5f..2.0f) { "TTS speed must be between 0.5 and 2" }
                val keyName = ttsApiKeyName(preferences.type)
                val existingKey = secureStore.readString(keyName).orEmpty()
                require(!preferences.enabled || apiKey.isNotBlank() || existingKey.isNotBlank()) {
                    "A Keychain API key is required before enabling TTS"
                }
                if (apiKey.isNotBlank()) secureStore.writeString(keyName, apiKey)
                val effectiveKey = apiKey.ifBlank { existingKey }
                ttsController.setProvider(
                    preferences.takeIf { it.enabled }?.toProviderSetting(effectiveKey)
                )
                mutableState.update {
                    it.copy(
                        tts = preferences,
                        hasTtsApiKey = effectiveKey.isNotBlank(),
                        error = null,
                    )
                }
                persist()
            }.onFailure { failure ->
                mutableState.update { it.copy(error = failure.message ?: "Unable to save TTS settings") }
            }
        }
    }

    fun clearTtsApiKey() {
        scope.launch {
            secureStore.remove(ttsApiKeyName(mutableState.value.tts.type))
            ttsController.stop()
            ttsController.setProvider(null)
            mutableState.update {
                it.copy(
                    tts = it.tts.copy(enabled = false),
                    hasTtsApiKey = false,
                    error = null,
                )
            }
            persist()
        }
    }

    fun saveImageGeneration(preferences: IosImageGenerationPreferences) {
        val modelId = preferences.modelId.trim()
        if (preferences.enabled && modelId.isBlank()) {
            mutableState.update { it.copy(error = "Select an image generation model first.") }
            return
        }
        if (
            preferences.enabled &&
            preferences.providerType == IosImageProviderType.COMFY_UI &&
            preferences.comfyUi.workflowJson.isBlank()
        ) {
            mutableState.update { it.copy(error = "Import a ComfyUI API workflow JSON first.") }
            return
        }
        mutableState.update {
            it.copy(
                imageGeneration = preferences.copy(
                    modelId = modelId,
                    method = if (preferences.providerType == IosImageProviderType.COMFY_UI) {
                        ImageGenerationMethod.DIFFUSION
                    } else {
                        preferences.method
                    },
                    comfyUi = preferences.comfyUi.copy(
                        baseUrl = preferences.comfyUi.baseUrl.trim(),
                        workflowJson = preferences.comfyUi.workflowJson.trim(),
                        promptNodeId = preferences.comfyUi.promptNodeId.trim(),
                        promptInputName = preferences.comfyUi.promptInputName.trim().ifBlank { "text" },
                        modelNodeId = preferences.comfyUi.modelNodeId.trim(),
                        modelInputName = preferences.comfyUi.modelInputName.trim().ifBlank { "ckpt_name" },
                    ),
                ),
                error = null,
            )
        }
        persistAsync()
    }

    fun generateImages(
        prompt: String,
        aspectRatio: String,
        count: Int = 1,
        inputImage: PlatformPickedFile? = null,
    ) {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isBlank() || mutableState.value.imageGenerating) return
        imageGenerationJob = scope.launch {
            mutableState.update { it.copy(imageGenerating = true, error = null) }
            try {
                generateToolImages(buildJsonObject {
                    put("prompt", normalizedPrompt)
                    put("aspect_ratio", aspectRatio)
                    put("count", count.coerceIn(1, 4))
                }, inputImage)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                mutableState.update { it.copy(error = failure.message ?: "Image generation failed") }
            } finally {
                mutableState.update { it.copy(imageGenerating = false) }
                imageGenerationJob = null
            }
        }
    }

    fun cancelImageGeneration() {
        imageGenerationJob?.cancel()
    }

    fun deleteGeneratedImage(path: String) {
        scope.launch {
            fileStore.delete(path)
            mutableState.update { current ->
                current.copy(generatedImages = current.generatedImages.filterNot { it.path == path })
            }
            persist()
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!mutableState.value.tts.enabled || !mutableState.value.hasTtsApiKey) {
            mutableState.update { it.copy(error = "Enable and configure TTS in Settings first") }
            return
        }
        ttsController.speak(text)
    }

    fun stopTts() = ttsController.stop()
    fun pauseTts() = ttsController.pause()
    fun resumeTts() = ttsController.resume()
    fun fastForwardTts(ms: Long = 5_000L) = ttsController.fastForward(ms)
    fun setTtsSpeed(speed: Float) = ttsController.setSpeed(speed)

    fun saveAppearance(themeId: String, colorMode: IosColorMode) {
        mutableState.update {
            it.copy(
                appearance = it.appearance.copy(
                    themeId = themeId,
                    colorMode = colorMode,
                )
            )
        }
        persistAsync()
    }

    fun saveUiCustomization(showAssistantBubbles: Boolean, fontSizeRatio: Float) {
        mutableState.update {
            it.copy(
                appearance = it.appearance.copy(
                    showAssistantBubbles = showAssistantBubbles,
                    fontSizeRatio = fontSizeRatio.coerceIn(0.5f, 2.0f),
                )
            )
        }
        persistAsync()
    }

    fun saveFontSettings(usePhoneSystemFont: Boolean) {
        mutableState.update {
            it.copy(
                appearance = it.appearance.copy(
                    usePhoneSystemFont = usePhoneSystemFont,
                )
            )
        }
        persistAsync()
    }

    fun saveRpStyleRules(rules: List<IosRpStyleRule>) {
        mutableState.update {
            it.copy(
                appearance = it.appearance.copy(
                    rpStyleRules = rules.mapNotNull { rule ->
                        val pattern = rule.pattern.trim()
                        val colorHex = normalizeIosColorHex(rule.colorHex) ?: return@mapNotNull null
                        rule.copy(pattern = pattern, colorHex = colorHex).takeIf {
                            pattern.isNotEmpty()
                        }
                    },
                )
            )
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

    fun saveLocalTools(tools: Set<IosLocalToolOption>) {
        val requestedNotifications = IosLocalToolOption.NOTIFICATIONS in tools &&
            IosLocalToolOption.NOTIFICATIONS !in mutableState.value.assistant.localTools
        mutableState.update { current ->
            val assistantId = current.assistant.id
            current.copy(
                assistants = current.assistants.map { assistant ->
                    if (assistant.id == assistantId) assistant.copy(localTools = tools) else assistant
                },
                error = null,
            )
        }
        persistAsync()
        if (requestedNotifications) {
            scope.launch {
                if (!notificationPlatform.requestAuthorization()) {
                    mutableState.update { it.copy(error = "Notification permission was denied") }
                }
            }
        }
    }

    fun saveMemorySettings(
        mode: IosMemoryMode,
        embeddingProviderType: IosProviderType,
        embeddingModelId: String,
        similarityThreshold: Float,
        limit: Int,
    ) {
        val normalizedModel = embeddingModelId.trim()
        if ((mode == IosMemoryMode.SEARCHABLE || mode == IosMemoryMode.ADAPTIVE) && normalizedModel.isBlank()) {
            mutableState.update { it.copy(error = "An embedding model is required for searchable memory") }
            return
        }
        mutableState.update { current ->
            val assistantId = current.assistant.id
            current.copy(
                assistants = current.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            memoryMode = mode,
                            embeddingProviderType = embeddingProviderType,
                            embeddingModelId = normalizedModel,
                            ragSimilarityThreshold = similarityThreshold.coerceIn(0f, 1f),
                            ragLimit = limit.coerceIn(1, 20),
                        )
                    } else assistant
                },
                error = null,
            )
        }
        persistAsync()
        if (mode == IosMemoryMode.SEARCHABLE || mode == IosMemoryMode.ADAPTIVE) {
            regenerateMemoryEmbeddings()
            if (mode == IosMemoryMode.ADAPTIVE) {
                val assistantId = mutableState.value.assistant.id
                mutableState.value.conversations
                    .filter { it.assistantId == assistantId }
                    .forEach { scheduleAdaptiveMemory(it.id) }
            }
        } else {
            val assistantId = mutableState.value.assistant.id
            mutableState.value.conversations.filter { it.assistantId == assistantId }.forEach {
                adaptiveMemoryJobs.remove(it.id)?.cancel()
            }
        }
        refreshAdaptiveBackgroundSchedule()
    }

    fun addMemory(content: String) {
        val normalized = content.trim()
        if (normalized.isBlank()) return
        scope.launch {
            runCatching { addMemoryInternal(mutableState.value.assistant, normalized) }
                .onFailure { failure ->
                    mutableState.update { it.copy(error = failure.message ?: "Unable to add memory") }
                }
        }
    }

    fun updateMemory(id: Int, content: String) {
        val normalized = content.trim()
        if (normalized.isBlank()) return
        scope.launch {
            updateMemoryInternal(id, normalized, mutableState.value.assistant)
        }
    }

    fun deleteMemory(id: Int) {
        scope.launch { deleteMemoryInternal(id, mutableState.value.assistant.id) }
    }

    fun regenerateMemoryEmbeddings() {
        scope.launch {
            val assistant = mutableState.value.assistant
            val records = mutableState.value.memories.filter { it.assistantId == assistant.id }
            records.forEach { record ->
                val embedding = runCatching { embedMemory(record.content, assistant) }.getOrNull()
                if (embedding != null) {
                    mutableState.update { current ->
                        current.copy(memories = current.memories.map {
                            if (it.id == record.id && it.assistantId == assistant.id) {
                                it.copy(
                                    embeddings = embedding,
                                    embeddingModelId = assistant.embeddingModelId,
                                )
                            } else it
                        })
                    }
                }
            }
            persist()
        }
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
        refreshAdaptiveBackgroundSchedule()
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
        mutableState.value.conversations.filter { it.assistantId == id }.forEach {
            adaptiveMemoryJobs.remove(it.id)?.cancel()
        }
        mutableState.value.scheduledMessages
            .filter { it.assistantId == id }
            .forEach { scheduledMessageJobs.remove(it.id)?.cancel() }
        mutableState.value.pendingQuestionnaire?.takeIf { pending ->
            mutableState.value.conversations.firstOrNull { it.id == pending.conversationId }?.assistantId == id
        }?.let { pending -> askUserWaiters.remove(pending.toolCallId)?.cancel() }
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
                memories = current.memories.filterNot { it.assistantId == id },
                scheduledMessages = current.scheduledMessages.filterNot { it.assistantId == id },
                pendingQuestionnaire = current.pendingQuestionnaire?.takeUnless { pending ->
                    current.conversations.firstOrNull { it.id == pending.conversationId }?.assistantId == id
                },
                error = null,
            )
        }
        persistAsync()
        refreshAdaptiveBackgroundSchedule()
        refreshScheduledMessageBackgroundSchedule()
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

    fun submitQuestionnaire(
        selectedOptions: Map<String, String>,
        customAnswers: Map<String, String>,
        dismissed: Boolean,
    ) {
        val pending = mutableState.value.pendingQuestionnaire ?: return
        val payload = buildAskUserAnswerPayload(pending, selectedOptions, customAnswers, dismissed)
        val waiter = askUserWaiters[pending.toolCallId]
        if (waiter != null) {
            waiter.complete(payload)
        } else {
            scope.launch { resumeRecoveredQuestionnaire(pending, payload) }
        }
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
            val selectedMemories = runCatching {
                selectMemories(snapshot.assistant, prompt)
            }.getOrElse { emptyList() }
            val memoryPrompt = buildMemoryPrompt(
                memories = selectedMemories,
                includeToolGuide = snapshot.assistant.memoryMode != IosMemoryMode.OFF,
            )
            val injections = resolvePromptInjections(
                assistant = snapshot.assistant,
                skills = snapshot.skills,
                lorebooks = snapshot.lorebooks,
                recentMessages = conversation.messages + userMessage,
            )
            val beforeSystem = injections[IosInjectionPosition.BEFORE_SYSTEM].orEmpty().joinToString("\n\n")
            val afterSystem = injections[IosInjectionPosition.AFTER_SYSTEM].orEmpty().joinToString("\n\n")
            val afterMemory = injections[IosInjectionPosition.AFTER_MEMORY].orEmpty().joinToString("\n\n")
            val beforeMessages = injections[IosInjectionPosition.BEFORE_MESSAGES].orEmpty().joinToString("\n\n")

            val stableSystemPrompt = buildList {
                if (beforeSystem.isNotBlank()) add(beforeSystem)
                snapshot.assistant.systemPrompt.takeIf(String::isNotBlank)?.let(::add)
                if (afterSystem.isNotBlank()) add(afterSystem)
                if (snapshot.assistant.memoryMode == IosMemoryMode.BASIC && memoryPrompt.isNotBlank()) {
                    add(memoryPrompt)
                }
                if (afterMemory.isNotBlank()) add(afterMemory)
            }.joinToString("\n\n")
            val systemMessages = buildList {
                if (stableSystemPrompt.isNotBlank()) add(UIMessage.system(stableSystemPrompt))
                if (beforeMessages.isNotBlank()) add(UIMessage.system(beforeMessages))
            }
            val requestUserMessage = if (
                snapshot.assistant.memoryMode == IosMemoryMode.SEARCHABLE ||
                snapshot.assistant.memoryMode == IosMemoryMode.ADAPTIVE
            ) {
                userMessage.copy(
                    parts = if (memoryPrompt.isBlank()) userMessage.parts else {
                        listOf(UIMessagePart.Text("<system>\n$memoryPrompt\n</system>\n\n")) + userMessage.parts
                    },
                )
            } else userMessage
            val requestMessages = systemMessages + conversation.messages + requestUserMessage
            var generationSucceeded = false
            try {
                val tools = listOfNotNull(buildSearchTool(snapshot.search)) +
                    buildMemoryTools(snapshot.assistant) +
                    buildLocalTools(snapshot.assistant, conversation.id) +
                    buildMcpTools(snapshot.mcpServers)
                val toolGuide = tools.joinToString("\n") { it.systemPrompt(model, requestMessages) }.trim()
                val providerRequestMessages = systemMessages +
                    toolGuide.takeIf(String::isNotBlank)?.let { listOf(UIMessage.system(it)) }.orEmpty() +
                    requestMessages.drop(systemMessages.size)
                runProviderToolLoop(
                    preferences,
                    apiKey,
                    model,
                    conversation.id,
                    tools,
                    providerRequestMessages,
                )
                generationSucceeded = true
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
                if (generationSucceeded && snapshot.assistant.memoryMode == IosMemoryMode.ADAPTIVE) {
                    scheduleAdaptiveMemory(conversation.id)
                }
                generationJob = null
            }
        }
    }

    fun deleteTurn(turnStartIndex: Int, turnEndIndex: Int) {
        val conversation = mutableState.value.selectedConversation ?: return
        updateConversation(conversation.id) { current ->
            val newMessages = current.messages.toMutableList()
            val validEnd = turnEndIndex.coerceAtMost(newMessages.lastIndex)
            if (turnStartIndex in newMessages.indices && turnStartIndex <= validEnd) {
                for (i in validEnd downTo turnStartIndex) {
                    if (i in newMessages.indices) newMessages.removeAt(i)
                }
            }
            current.copy(messages = newMessages)
        }
        scope.launch { persist() }
    }

    fun editMessage(index: Int, newText: String) {
        val conversation = mutableState.value.selectedConversation ?: return
        updateConversation(conversation.id) { current ->
            val newMessages = current.messages.toMutableList()
            if (index in newMessages.indices) {
                val msg = newMessages[index]
                val newParts = msg.parts.map { part ->
                    if (part is UIMessagePart.Text) UIMessagePart.Text(newText) else part
                }
                newMessages[index] = msg.copy(parts = newParts)
            }
            current.copy(messages = newMessages)
        }
        scope.launch { persist() }
    }

    fun forkConversation(upToMessageIndex: Int) {
        val snapshot = mutableState.value
        val conversation = snapshot.selectedConversation ?: return
        val forkMessages = conversation.messages.take((upToMessageIndex + 1).coerceAtMost(conversation.messages.size))
        val newConv = IosConversation(
            id = Uuid.random().toString(),
            assistantId = conversation.assistantId,
            title = "Fork: ${conversation.title}",
            messages = forkMessages,
        )
        mutableState.update {
            it.copy(
                conversations = listOf(newConv) + it.conversations,
                selectedConversationId = newConv.id,
            )
        }
        scope.launch { persist() }
    }

    fun regenerate() {
        val snapshot = mutableState.value
        val conversation = snapshot.selectedConversation ?: return
        if (snapshot.generating) return
        val lastAssistantIdx = conversation.messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (lastAssistantIdx == -1) return
        val userPrompt = conversation.messages.take(lastAssistantIdx).lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()
        updateConversation(conversation.id) { current ->
            current.copy(messages = current.messages.take(lastAssistantIdx))
        }
        scope.launch { persist() }
        send(userPrompt)
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

    private suspend fun runProviderToolLoop(
        preferences: IosProviderPreferences,
        apiKey: String,
        model: Model,
        conversationId: String,
        tools: List<Tool>,
        initialProviderMessages: List<UIMessage>,
    ) {
        var providerMessages = initialProviderMessages
        var toolStep = 0
        var lastCheckpointAt = Clock.System.now().toEpochMilliseconds()
        while (true) {
            providerFlow(preferences, apiKey, model, providerMessages, tools).collect { chunk ->
                updateConversation(conversationId) { current ->
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
            val assistantResponse = mutableState.value.conversations
                .firstOrNull { it.id == conversationId }
                ?.messages
                ?.lastOrNull()
                ?: break
            val toolCalls = assistantResponse.getToolCalls()
            if (toolCalls.isEmpty()) break
            if (++toolStep > MAX_TOOL_STEPS) error("Too many tool-use steps")
            val results = toolCalls.map { toolCall ->
                val tool = tools.firstOrNull { it.name == toolCall.toolName }
                val arguments = runCatching { json.parseToJsonElement(toolCall.arguments) }
                    .getOrElse { JsonObject(emptyMap()) }
                UIMessagePart.ToolResult(
                    toolCallId = toolCall.toolCallId,
                    toolName = toolCall.toolName,
                    arguments = arguments,
                    metadata = toolCall.metadata,
                    content = runCatching {
                        requireNotNull(tool) { "Tool ${toolCall.toolName} not found" }
                        currentExecutingToolCallId = toolCall.toolCallId
                        try {
                            tool.execute(arguments)
                        } finally {
                            currentExecutingToolCallId = null
                        }
                    }.getOrElse { failure ->
                        buildJsonObject { put("error", JsonPrimitive(failure.message ?: "Tool failed")) }
                    },
                )
            }
            val toolMessage = UIMessage(role = MessageRole.TOOL, parts = results)
            updateConversation(conversationId) { current ->
                current.copy(messages = current.messages + toolMessage + UIMessage.assistant(""))
            }
            providerMessages = providerMessages + assistantResponse + toolMessage
            persist()
        }
    }

    private suspend fun providerFlow(
        preferences: IosProviderPreferences,
        apiKey: String,
        model: Model,
        messages: List<UIMessage>,
        tools: List<Tool>,
    ): kotlinx.coroutines.flow.Flow<MessageChunk> = when (preferences.type) {
        IosProviderType.OPENAI -> {
            val setting = ProviderSetting.OpenAI(
                name = "OpenAI compatible", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).streamText(
                setting, messages, TextGenerationParams(model = model, tools = tools),
            )
        }
        IosProviderType.GOOGLE -> {
            val setting = ProviderSetting.Google(
                name = "Google", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).streamText(
                setting, messages, TextGenerationParams(model = model, tools = tools),
            )
        }
        IosProviderType.CLAUDE -> {
            val setting = ProviderSetting.Claude(
                name = "Claude", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).streamText(
                setting, messages, TextGenerationParams(model = model, tools = tools),
            )
        }
    }

    private fun scheduleAdaptiveMemory(conversationId: String) {
        val snapshot = mutableState.value
        val conversation = snapshot.conversations.firstOrNull { it.id == conversationId } ?: return
        val assistant = snapshot.assistants.firstOrNull { it.id == conversation.assistantId } ?: return
        if (assistant.memoryMode != IosMemoryMode.ADAPTIVE) return
        val pendingCount = pendingAdaptiveMessages(conversation).size
        if (pendingCount == 0) return
        refreshAdaptiveBackgroundSchedule()
        adaptiveMemoryJobs.remove(conversationId)?.cancel()
        adaptiveMemoryJobs[conversationId] = scope.launch {
            if (pendingCount < ADAPTIVE_MEMORY_MESSAGE_THRESHOLD) {
                delay(ADAPTIVE_MEMORY_INACTIVITY_MS)
            }
            adaptiveMemoryMutex.withLock {
                runCatching { consolidateAdaptiveMemory(conversationId) }
            }
            adaptiveMemoryJobs.remove(conversationId)
            refreshAdaptiveBackgroundSchedule()
        }
    }

    private fun refreshAdaptiveBackgroundSchedule() {
        val snapshot = mutableState.value
        if (snapshot.loading) return
        val now = Clock.System.now().toEpochMilliseconds()
        val candidates = snapshot.conversations.mapNotNull { conversation ->
            val assistant = snapshot.assistants.firstOrNull { it.id == conversation.assistantId }
                ?: return@mapNotNull null
            if (assistant.memoryMode != IosMemoryMode.ADAPTIVE) return@mapNotNull null
            val pendingCount = pendingAdaptiveMessages(conversation).size
            if (pendingCount == 0) return@mapNotNull null
            conversation.updatedAtEpochMs to pendingCount
        }
        val earliest = earliestAdaptiveMemoryDeadline(
            nowEpochMs = now,
            candidates = candidates,
            messageThreshold = ADAPTIVE_MEMORY_MESSAGE_THRESHOLD,
            inactivityMs = ADAPTIVE_MEMORY_INACTIVITY_MS,
        )
        if (earliest == null) {
            cancelAdaptiveBackground?.invoke()
        } else {
            scheduleAdaptiveBackground?.invoke(earliest)
        }
    }

    private fun scheduleMessageInProcess(scheduled: IosScheduledMessage) {
        scheduledMessageJobs.remove(scheduled.id)?.cancel()
        scheduledMessageJobs[scheduled.id] = scope.launch {
            delay(
                (scheduled.nextAttemptEpochMs - Clock.System.now().toEpochMilliseconds())
                    .coerceAtLeast(0L)
            )
            scheduledMessageJobs.remove(scheduled.id)
            try {
                deliverScheduledMessage(scheduled.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                recordScheduledMessageFailure(scheduled.id)
            }
            persist()
            refreshScheduledMessageBackgroundSchedule()
        }
    }

    private fun refreshScheduledMessageBackgroundSchedule() {
        val snapshot = mutableState.value
        if (snapshot.loading) return
        val earliest = earliestScheduledMessageDeadline(snapshot.scheduledMessages)
        if (earliest == null) {
            cancelMessageBackground?.invoke()
        } else {
            scheduleMessageBackground?.invoke(earliest)
        }
    }

    private suspend fun deliverScheduledMessage(id: String) = scheduledMessageMutex.withLock {
        val snapshot = mutableState.value
        val scheduled = snapshot.scheduledMessages.firstOrNull { it.id == id } ?: return@withLock
        val assistant = snapshot.assistants.firstOrNull { it.id == scheduled.assistantId }
        val conversation = snapshot.conversations.firstOrNull { it.id == scheduled.conversationId }
        if (assistant == null || conversation == null) {
            mutableState.update { current ->
                current.copy(scheduledMessages = current.scheduledMessages.filterNot { it.id == id })
            }
            return@withLock
        }

        val history = conversation.messages
            .asSequence()
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .filter { it.toContentText().isNotBlank() }
            .toList()
            .takeLast(10)
            .joinToString("\n") { message -> "${message.role.name}: ${message.toContentText()}" }
        val lastUserText = conversation.messages.lastOrNull { it.role == MessageRole.USER }
            ?.toContentText().orEmpty()
        val memoryContext = if (lastUserText.isBlank()) {
            "None"
        } else {
            runCatching { selectMemories(assistant, lastUserText) }
                .getOrDefault(emptyList())
                .joinToString("\n") { memory -> "- ${memory.content}" }
                .ifBlank { "None" }
        }
        val prompt = """
            You are ${assistant.name}.
            You scheduled a message to be sent to the user now.

            Reason for scheduling: "${scheduled.reason}"

            Recent Chat History:
            ${history.ifBlank { "None" }}

            Relevant Memories:
            $memoryContext

            Generate the content of the notification message now.
            - Be natural and conversational.
            - Directly address the reason.
            - Keep it concise (under 2 sentences if possible) as it is a notification.
            - Do NOT include quotes or prefixes like "Notification:". Just the content.
        """.trimIndent()
        val preferences = snapshot.provider
        val apiKey = secureStore.readString(apiKeyName(preferences.type)).orEmpty()
        require(apiKey.isNotBlank()) { "Configure the selected provider API key first." }
        val response = generateBackgroundText(
            preferences = preferences,
            apiKey = apiKey,
            model = Model(preferences.modelId, preferences.modelId),
            prompt = prompt,
            temperature = 0.7f,
            thinkingBudget = 0,
        )
        val content = response.choices.firstOrNull()?.message?.toContentText().orEmpty().trim()
        require(content.isNotBlank()) { "The scheduled message model returned no content." }
        val result = notificationPlatform.post(
            identifier = "${conversation.id}:scheduled:${scheduled.id}",
            title = assistant.name,
            content = content,
        )
        require(result.status == "success") { "The scheduled notification could not be posted." }
        recordNotification(assistant.name, content)
        mutableState.update { current ->
            current.copy(scheduledMessages = current.scheduledMessages.filterNot { it.id == id })
        }
    }

    private fun recordScheduledMessageFailure(id: String) {
        var retry: IosScheduledMessage? = null
        mutableState.update { current ->
            current.copy(
                scheduledMessages = current.scheduledMessages.map { scheduled ->
                    if (scheduled.id != id) return@map scheduled
                    val updated = scheduled.copy(
                        attemptCount = scheduled.attemptCount + 1,
                        nextAttemptEpochMs = Clock.System.now().toEpochMilliseconds() +
                            scheduledMessageRetryDelayMs(scheduled.attemptCount),
                    )
                    retry = updated
                    updated
                }
            )
        }
        retry?.let(::scheduleMessageInProcess)
    }

    private fun recordNotification(title: String, content: String) {
        val record = IosNotificationRecord(
            title = title,
            content = content,
            postTimeEpochMs = Clock.System.now().toEpochMilliseconds(),
        )
        mutableState.update { current ->
            current.copy(notificationHistory = (current.notificationHistory + record).takeLast(100))
        }
    }

    private suspend fun resumeRecoveredQuestionnaire(
        pending: IosPendingQuestionnaire,
        payload: JsonObject,
    ) {
        if (mutableState.value.generating) return
        val snapshot = mutableState.value
        val conversation = snapshot.conversations.firstOrNull { it.id == pending.conversationId } ?: return
        val assistant = snapshot.assistants.firstOrNull { it.id == conversation.assistantId } ?: return
        val toolCall = conversation.messages.asSequence()
            .flatMap { it.getToolCalls().asSequence() }
            .firstOrNull { it.toolCallId == pending.toolCallId && it.toolName == "ask_user" }
            ?: return
        val arguments = runCatching { json.parseToJsonElement(toolCall.arguments) }
            .getOrElse { JsonObject(emptyMap()) }
        val toolMessage = UIMessage(
            role = MessageRole.TOOL,
            parts = listOf(
                UIMessagePart.ToolResult(
                    toolCallId = toolCall.toolCallId,
                    toolName = toolCall.toolName,
                    arguments = arguments,
                    metadata = toolCall.metadata,
                    content = payload,
                )
            ),
        )
        updateConversation(conversation.id) { current ->
            current.copy(messages = current.messages + toolMessage + UIMessage.assistant(""))
        }
        mutableState.update { it.copy(generating = true, pendingQuestionnaire = null, error = null) }
        persist()
        generationJob = scope.launch {
            try {
                val preferences = mutableState.value.provider
                val apiKey = secureStore.readString(apiKeyName(preferences.type)).orEmpty()
                require(apiKey.isNotBlank()) { "Configure an API key in Settings first." }
                val tools = listOfNotNull(buildSearchTool(mutableState.value.search)) +
                    buildMemoryTools(assistant) + buildLocalTools(assistant, conversation.id)
                val systemMessages = assistant.systemPrompt.takeIf(String::isNotBlank)
                    ?.let { listOf(UIMessage.system(it)) }.orEmpty()
                val storedMessages = mutableState.value.conversations
                    .first { it.id == conversation.id }.messages.dropLast(1)
                val model = Model(preferences.modelId, preferences.modelId)
                val toolGuide = tools.joinToString("\n") {
                    it.systemPrompt(model, systemMessages + storedMessages)
                }.trim()
                runProviderToolLoop(
                    preferences = preferences,
                    apiKey = apiKey,
                    model = model,
                    conversationId = conversation.id,
                    tools = tools,
                    initialProviderMessages = systemMessages +
                        toolGuide.takeIf(String::isNotBlank)?.let { listOf(UIMessage.system(it)) }.orEmpty() +
                        storedMessages,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                mutableState.update { it.copy(error = failure.message ?: "Generation failed") }
            } finally {
                mutableState.update { it.copy(generating = false) }
                persist()
                if (assistant.memoryMode == IosMemoryMode.ADAPTIVE) scheduleAdaptiveMemory(conversation.id)
                generationJob = null
            }
        }
    }

    private fun pendingAdaptiveMessages(conversation: IosConversation): List<UIMessage> {
        val messages = conversation.messages.filter { message ->
            (message.role == MessageRole.USER || message.role == MessageRole.ASSISTANT) &&
                message.toContentText().isNotBlank()
        }
        val lastIndex = conversation.memoryLastMessageId?.let { id ->
            messages.indexOfLast { it.id.toString() == id }
        } ?: -1
        return if (lastIndex >= 0) messages.drop(lastIndex + 1) else messages.takeLast(MAX_PENDING_MEMORY_MESSAGES)
    }

    private suspend fun consolidateAdaptiveMemory(conversationId: String) {
        val snapshot = mutableState.value
        val conversation = snapshot.conversations.firstOrNull { it.id == conversationId } ?: return
        val assistant = snapshot.assistants.firstOrNull { it.id == conversation.assistantId } ?: return
        if (assistant.memoryMode != IosMemoryMode.ADAPTIVE) return
        val pendingMessages = pendingAdaptiveMessages(conversation)
        if (pendingMessages.isEmpty()) return
        val sourceMessages = pendingMessages.map { message ->
            SourceMessage(
                id = message.id.toString(),
                role = if (message.role == MessageRole.USER) 0 else 1,
                text = message.toContentText(),
                observedAt = message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            )
        }
        val existing = snapshot.memories
            .asSequence()
            .filter { it.assistantId == assistant.id }
            .sortedByDescending(IosMemoryRecord::timestampEpochMs)
            .take(10)
            .map { record ->
                TemporalRecallItem(
                    stableId = if (record.type == 1) "episode:${record.id}" else "claim:${record.id}",
                    text = record.content,
                    timestamp = record.timestampEpochMs,
                    score = 1f,
                    kind = when {
                        record.type == 1 -> RecallKind.EPISODE
                        record.active -> RecallKind.CURRENT_STATE
                        else -> RecallKind.HISTORICAL_FACT
                    },
                    confidence = record.confidence,
                )
            }
            .toList()
        val preferences = snapshot.provider
        val apiKey = secureStore.readString(apiKeyName(preferences.type)).orEmpty()
        if (apiKey.isBlank()) return
        val model = Model(preferences.modelId, preferences.modelId)
        val prompt = buildTemporalMemoryExtractionPrompt(sourceMessages, existing)
        val response = generateBackgroundText(preferences, apiKey, model, prompt)
        val text = response.choices.firstOrNull()?.message?.toContentText().orEmpty()
        val extraction = parseMemoryExtraction(text) ?: return
        applyAdaptiveExtraction(assistant, conversationId, sourceMessages, extraction)
        updateConversation(conversationId) { current ->
            current.copy(memoryLastMessageId = sourceMessages.last().id)
        }
        persist()
    }

    private suspend fun generateBackgroundText(
        preferences: IosProviderPreferences,
        apiKey: String,
        model: Model,
        prompt: String,
        temperature: Float = 0.1f,
        thinkingBudget: Int? = null,
    ): MessageChunk = when (preferences.type) {
        IosProviderType.OPENAI -> {
            val setting = ProviderSetting.OpenAI(
                name = "OpenAI compatible", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).generateText(
                setting, listOf(UIMessage.user(prompt)), TextGenerationParams(
                    model = model,
                    temperature = temperature,
                    thinkingBudget = thinkingBudget,
                ),
            )
        }
        IosProviderType.GOOGLE -> {
            val setting = ProviderSetting.Google(
                name = "Google", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).generateText(
                setting, listOf(UIMessage.user(prompt)), TextGenerationParams(
                    model = model,
                    temperature = temperature,
                    thinkingBudget = thinkingBudget,
                ),
            )
        }
        IosProviderType.CLAUDE -> {
            val setting = ProviderSetting.Claude(
                name = "Claude", apiKey = apiKey,
                baseUrl = preferences.baseUrl, models = listOf(model),
            )
            providerManager.getProviderByType(setting).generateText(
                setting, listOf(UIMessage.user(prompt)), TextGenerationParams(
                    model = model,
                    temperature = temperature,
                    thinkingBudget = thinkingBudget,
                ),
            )
        }
    }

    private fun parseMemoryExtraction(text: String): MemoryExtractionEnvelope? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.decodeFromString<MemoryExtractionEnvelope>(text.substring(start, end + 1))
        }.getOrNull()
    }

    private suspend fun applyAdaptiveExtraction(
        assistant: IosAssistantPreferences,
        conversationId: String,
        messages: List<SourceMessage>,
        extraction: MemoryExtractionEnvelope,
    ) {
        extraction.operations.take(MAX_MEMORY_OPERATIONS_PER_PASS).forEach { operation ->
            val statement = operation.statement.trim().take(MAX_MEMORY_STATEMENT_LENGTH)
            if (statement.isBlank()) return@forEach
            val subject = operation.subject.normalizedMemoryKey("user")
            val predicate = operation.predicate.normalizedMemoryKey("notes")
            val currentClaims = mutableState.value.memories.filter { record ->
                record.assistantId == assistant.id && record.type == 0 && record.active &&
                    record.claimSubject == subject && record.claimPredicate == predicate
            }
            val exact = currentClaims.firstOrNull { record ->
                record.content.normalizedMemoryText() == statement.normalizedMemoryText() ||
                    record.claimObject?.normalizedMemoryText() == operation.objectValue?.normalizedMemoryText()
            }
            val existing = operation.replacesClaimId?.toInt()?.let { replacementId ->
                mutableState.value.memories.firstOrNull {
                    it.id == replacementId && it.assistantId == assistant.id && it.type == 0
                }
            } ?: when (operation.op) {
                MemoryOperationType.SUPERSEDE, MemoryOperationType.CLOSE -> exact ?: currentClaims.firstOrNull()
                MemoryOperationType.ADD, MemoryOperationType.REINFORCE -> exact
            }
            val now = Clock.System.now().toEpochMilliseconds()
            when (operation.op) {
                MemoryOperationType.REINFORCE -> if (existing != null) {
                    mutableState.update { current ->
                        current.copy(memories = current.memories.map { record ->
                            if (record.id == existing.id && record.assistantId == assistant.id) {
                                record.copy(
                                    confidence = maxOf(record.confidence, operation.confidence.coerceIn(0f, 1f)),
                                    active = true,
                                    timestampEpochMs = now,
                                )
                            } else record
                        })
                    }
                }
                MemoryOperationType.CLOSE -> if (existing != null) {
                    mutableState.update { current ->
                        current.copy(memories = current.memories.map { record ->
                            if (record.id == existing.id && record.assistantId == assistant.id) {
                                record.copy(active = false, timestampEpochMs = operation.validUntil ?: now)
                            } else record
                        })
                    }
                }
                MemoryOperationType.SUPERSEDE -> {
                    if (existing != null) {
                        mutableState.update { current ->
                            current.copy(memories = current.memories.map { record ->
                                if (record.id == existing.id && record.assistantId == assistant.id) {
                                    record.copy(active = false, timestampEpochMs = operation.validFrom ?: now)
                                } else record
                            })
                        }
                    }
                    insertAdaptiveClaim(assistant, conversationId, messages, operation, subject, predicate, statement)
                }
                MemoryOperationType.ADD -> if (existing != null) {
                    mutableState.update { current ->
                        current.copy(memories = current.memories.map { record ->
                            if (record.id == existing.id && record.assistantId == assistant.id) {
                                record.copy(
                                    confidence = maxOf(record.confidence, operation.confidence.coerceIn(0f, 1f)),
                                    timestampEpochMs = now,
                                )
                            } else record
                        })
                    }
                } else {
                    insertAdaptiveClaim(assistant, conversationId, messages, operation, subject, predicate, statement)
                }
            }
        }
        extraction.episode?.let { episode ->
            val content = "${episode.title.trim().take(80)}: ${episode.summary.trim().take(600)}"
            if (content.isNotBlank()) {
                val embeddings = runCatching { embedMemory(content, assistant) }.getOrNull()
                mutableState.update { current ->
                    val existing = current.memories.firstOrNull {
                        it.assistantId == assistant.id && it.type == 1 &&
                            it.sourceConversationId == conversationId && it.sceneKey == episode.sceneKey.take(80)
                    }
                    val updated = IosMemoryRecord(
                        id = existing?.id ?: ((current.memories.maxOfOrNull(IosMemoryRecord::id) ?: 0) + 1),
                        assistantId = assistant.id,
                        content = content,
                        type = 1,
                        embeddings = embeddings ?: existing?.embeddings,
                        embeddingModelId = embeddings?.let { assistant.embeddingModelId } ?: existing?.embeddingModelId,
                        timestampEpochMs = episode.eventStart ?: messages.firstOrNull()?.observedAt
                            ?: Clock.System.now().toEpochMilliseconds(),
                        significance = episode.importance.coerceIn(1, 5),
                        confidence = 0.8f,
                        reality = episode.reality,
                        sourceConversationId = conversationId,
                        sceneKey = episode.sceneKey.take(80),
                    )
                    current.copy(
                        memories = if (existing == null) current.memories + updated else {
                            current.memories.map { if (it.id == existing.id) updated else it }
                        }
                    )
                }
            }
        }
    }

    private suspend fun insertAdaptiveClaim(
        assistant: IosAssistantPreferences,
        conversationId: String,
        messages: List<SourceMessage>,
        operation: me.rerere.rikkahub.data.memory.MemoryWriteOperation,
        subject: String,
        predicate: String,
        statement: String,
    ) {
        val embeddings = runCatching { embedMemory(statement, assistant) }.getOrNull()
        val observedAt = operation.sourceMessageId
            ?.let { id -> messages.firstOrNull { it.id == id }?.observedAt }
            ?: messages.lastOrNull()?.observedAt
            ?: Clock.System.now().toEpochMilliseconds()
        mutableState.update { current ->
            val record = IosMemoryRecord(
                id = (current.memories.maxOfOrNull(IosMemoryRecord::id) ?: 0) + 1,
                assistantId = assistant.id,
                content = statement,
                embeddings = embeddings,
                embeddingModelId = embeddings?.let { assistant.embeddingModelId },
                timestampEpochMs = observedAt,
                significance = operation.importance.coerceIn(1, 5),
                claimSubject = subject,
                claimPredicate = predicate,
                claimObject = operation.objectValue?.take(160),
                confidence = operation.confidence.coerceIn(0f, 1f),
                reality = operation.reality,
                active = operation.confidence >= 0.8f,
                sourceConversationId = conversationId,
                sourceMessageId = operation.sourceMessageId,
            )
            current.copy(memories = current.memories + record)
        }
    }

    private suspend fun addMemoryInternal(
        assistant: IosAssistantPreferences,
        content: String,
    ): IosMemoryRecord {
        val embeddings = if (assistant.memoryMode == IosMemoryMode.SEARCHABLE ||
            assistant.memoryMode == IosMemoryMode.ADAPTIVE
        ) {
            runCatching { embedMemory(content, assistant) }.getOrNull()
        } else null
        val record = IosMemoryRecord(
            id = (mutableState.value.memories.maxOfOrNull(IosMemoryRecord::id) ?: 0) + 1,
            assistantId = assistant.id,
            content = content,
            embeddings = embeddings,
            embeddingModelId = embeddings?.let { assistant.embeddingModelId },
        )
        mutableState.update { current ->
            current.copy(memories = current.memories + record, error = null)
        }
        persist()
        return record
    }

    private suspend fun updateMemoryInternal(
        id: Int,
        content: String,
        assistant: IosAssistantPreferences,
    ): IosMemoryRecord {
        val existing = mutableState.value.memories.firstOrNull {
            it.id == id && it.assistantId == assistant.id
        } ?: error("Memory not found")
        val embeddings = if (assistant.memoryMode == IosMemoryMode.SEARCHABLE ||
            assistant.memoryMode == IosMemoryMode.ADAPTIVE
        ) runCatching { embedMemory(content, assistant) }.getOrNull() else null
        val updated = existing.copy(
            content = content,
            embeddings = embeddings,
            embeddingModelId = embeddings?.let { assistant.embeddingModelId },
        )
        mutableState.update { current ->
            current.copy(
                memories = current.memories.map { if (it.id == id && it.assistantId == assistant.id) updated else it },
                error = null,
            )
        }
        persist()
        return updated
    }

    private suspend fun deleteMemoryInternal(id: Int, assistantId: String) {
        val exists = mutableState.value.memories.any {
            it.id == id && it.assistantId == assistantId && it.type == 0
        }
        require(exists) { "Memory not found" }
        mutableState.update { current ->
            current.copy(
                memories = current.memories.filterNot {
                    it.id == id && it.assistantId == assistantId && it.type == 0
                },
                error = null,
            )
        }
        persist()
    }

    private suspend fun embedMemory(
        content: String,
        assistant: IosAssistantPreferences,
    ): List<List<Float>> {
        val preferences = mutableState.value.providerConfigurations
            .firstOrNull { it.type == assistant.embeddingProviderType }
            ?: error("Configure the embedding provider first")
        val apiKey = secureStore.readString(apiKeyName(preferences.type)).orEmpty()
        require(apiKey.isNotBlank()) {
            "Configure the ${preferences.type.name.lowercase()} provider API key before using searchable memory"
        }
        val model = Model(
            modelId = assistant.embeddingModelId,
            displayName = assistant.embeddingModelId,
            type = ModelType.EMBEDDING,
        )
        val chunks = PortableMemoryChunker.chunkText(content)
        val embeddings = when (preferences.type) {
            IosProviderType.OPENAI -> {
                val setting = ProviderSetting.OpenAI(
                    name = "OpenAI compatible",
                    apiKey = apiKey,
                    baseUrl = preferences.baseUrl,
                    models = listOf(model),
                )
                providerManager.getProviderByType(setting).createEmbedding(setting, chunks, model)
            }
            IosProviderType.GOOGLE -> {
                val setting = ProviderSetting.Google(
                    name = "Google",
                    apiKey = apiKey,
                    baseUrl = preferences.baseUrl,
                    models = listOf(model),
                )
                providerManager.getProviderByType(setting).createEmbedding(setting, chunks, model)
            }
            IosProviderType.CLAUDE -> error("Claude does not provide an embedding endpoint")
        }
        require(embeddings.isNotEmpty()) { "The embedding provider returned no vectors" }
        return embeddings
    }

    private suspend fun selectMemories(
        assistant: IosAssistantPreferences,
        query: String,
    ): List<IosMemoryRecord> {
        val records = mutableState.value.memories.filter { it.assistantId == assistant.id }
        return when (assistant.memoryMode) {
            IosMemoryMode.OFF -> emptyList()
            IosMemoryMode.BASIC -> records
            IosMemoryMode.SEARCHABLE,
            IosMemoryMode.ADAPTIVE -> {
                val queryVectors = runCatching { embedMemory(query, assistant) }.getOrNull()
                    ?: return emptyList()
                val scored = records.mapNotNull { record ->
                    val vectors = if (record.embeddingModelId == assistant.embeddingModelId) {
                        record.embeddings
                    } else null
                    val effectiveVectors = vectors ?: runCatching {
                        embedMemory(record.content, assistant)
                    }.getOrNull()?.also { generated ->
                        mutableState.update { current ->
                            current.copy(memories = current.memories.map {
                                if (it.id == record.id && it.assistantId == assistant.id) {
                                    it.copy(
                                        embeddings = generated,
                                        embeddingModelId = assistant.embeddingModelId,
                                    )
                                } else it
                            })
                        }
                    } ?: return@mapNotNull null
                    val similarity = effectiveVectors.maxOfOrNull { memoryVector ->
                        queryVectors.maxOfOrNull { queryVector ->
                            MemoryVectorMath.cosineSimilarity(queryVector, memoryVector)
                        } ?: 0f
                    } ?: 0f
                    val keyword = MemoryVectorMath.keywordScore(query, record.content)
                    val score = ((similarity * 0.8f) + (keyword * 0.2f)) * 1.05f + 0.05f
                    if (score >= assistant.ragSimilarityThreshold) record to score else null
                }
                scored.sortedByDescending { it.second }.take(assistant.ragLimit).map { it.first }
            }
        }
    }

    private fun buildMemoryPrompt(memories: List<IosMemoryRecord>, includeToolGuide: Boolean): String {
        if (memories.isEmpty() && !includeToolGuide) return ""
        return buildString {
            append("## Memories\n")
            append("These are memories that you can reference in the future conversations.\n")
            if (memories.isNotEmpty()) {
                append("### Core Memories\n")
                memories.forEach { memory -> append("- [ID: ${memory.id}] ${memory.content}\n") }
            }
            if (includeToolGuide) {
                append(
                    """

                    ## Memory Tool
                    You are a stateless large language model; you cannot store memories internally. To remember information, use memory tools.
                    Create a memory when new durable user information appears, edit an existing relevant record instead of duplicating it, and delete records that are outdated.
                    Do not store sensitive information such as ethnicity, religious beliefs, sexual orientation, political views, sexual life, or criminal records.
                    """.trimIndent()
                )
            }
        }
    }

    private fun buildMemoryTools(assistant: IosAssistantPreferences): List<Tool> {
        if (assistant.memoryMode == IosMemoryMode.OFF) return emptyList()
        fun stringProperty(description: String) = buildJsonObject {
            put("type", "string")
            put("description", description)
        }
        val tools = mutableListOf(
            Tool(
                name = "create_memory",
                description = "Create a durable core memory for this assistant.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject { put("content", stringProperty("Information to remember")) },
                        required = listOf("content"),
                    )
                },
                execute = { arguments ->
                    val content = (arguments as? JsonObject)?.get("content")
                        ?.let { it as? JsonPrimitive }?.content?.trim().orEmpty()
                    require(content.isNotBlank()) { "content is required" }
                    val record = addMemoryInternal(assistant, content)
                    buildJsonObject { put("id", record.id); put("content", record.content) }
                },
            ),
            Tool(
                name = "edit_memory",
                description = "Update an existing core memory.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("id", buildJsonObject { put("type", "integer") })
                            put("content", stringProperty("Updated memory content"))
                        },
                        required = listOf("id", "content"),
                    )
                },
                execute = { arguments ->
                    val objectValue = arguments as? JsonObject ?: error("arguments must be an object")
                    val id = (objectValue["id"] as? JsonPrimitive)?.content?.toIntOrNull()
                        ?: error("id is required")
                    val content = (objectValue["content"] as? JsonPrimitive)?.content?.trim().orEmpty()
                    require(content.isNotBlank()) { "content is required" }
                    val existing = mutableState.value.memories.firstOrNull {
                        it.id == id && it.assistantId == assistant.id && it.type == 0
                    }
                        ?: error("Memory not found")
                    val updated = updateMemoryInternal(existing.id, content, assistant)
                    buildJsonObject { put("id", id); put("content", updated.content) }
                },
            ),
            Tool(
                name = "delete_memory",
                description = "Delete an outdated core memory.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject { put("id", buildJsonObject { put("type", "integer") }) },
                        required = listOf("id"),
                    )
                },
                execute = { arguments ->
                    val id = ((arguments as? JsonObject)?.get("id") as? JsonPrimitive)
                        ?.content?.toIntOrNull() ?: error("id is required")
                    deleteMemoryInternal(id, assistant.id)
                    buildJsonObject { put("deleted", id) }
                },
            ),
        )
        if (assistant.memoryMode == IosMemoryMode.SEARCHABLE || assistant.memoryMode == IosMemoryMode.ADAPTIVE) {
            tools += Tool(
                name = "search_memory",
                description = "Search this assistant's stored memories for relevant information.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject { put("query", stringProperty("What to recall")) },
                        required = listOf("query"),
                    )
                },
                execute = { arguments ->
                    val query = ((arguments as? JsonObject)?.get("query") as? JsonPrimitive)
                        ?.content?.trim().orEmpty()
                    require(query.isNotBlank()) { "query is required" }
                    json.encodeToJsonElement(selectMemories(assistant, query))
                },
            )
        }
        return tools
    }

    private fun buildLocalTools(
        assistant: IosAssistantPreferences,
        conversationId: String,
    ): List<Tool> = buildList {
        fun stringProperty(description: String) = buildJsonObject {
            put("type", "string")
            put("description", description)
        }
        if (IosLocalToolOption.JAVASCRIPT in assistant.localTools) {
            add(
                Tool(
                    name = "eval_javascript",
                    description = "Execute JavaScript code with QuickJS. If use this tool to calculate math, better to add `toFixed` to the code.",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("code", stringProperty("The JavaScript code to execute"))
                            },
                        )
                    },
                    execute = { arguments ->
                        val code = ((arguments as? JsonObject)?.get("code") as? JsonPrimitive)
                            ?.content
                            ?: error("code is required")
                        buildJsonObject {
                            put("result", javaScriptExecutor.execute(code))
                        }
                    },
                )
            )
        }
        if (IosLocalToolOption.NOTIFICATIONS in assistant.localTools) {
            add(
                Tool(
                    name = "send_notification",
                    description = "Send a notification to the user",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("title", buildJsonObject {
                                    put("type", "string")
                                    put("description", "Notification title")
                                })
                                put("content", buildJsonObject {
                                    put("type", "string")
                                    put("description", "Notification content")
                                })
                            },
                            required = listOf("title", "content"),
                        )
                    },
                    execute = { arguments ->
                        val objectValue = arguments as? JsonObject ?: error("arguments must be an object")
                        val title = (objectValue["title"] as? JsonPrimitive)?.content?.trim()
                            ?.ifBlank { "Notification" } ?: "Notification"
                        val content = (objectValue["content"] as? JsonPrimitive)?.content?.trim().orEmpty()
                        require(content.isNotBlank()) { "content is required" }
                        val result = notificationPlatform.post(
                            identifier = "$conversationId:${Uuid.random()}",
                            title = title,
                            content = content,
                        )
                        if (result.status == "success") recordNotification(title, content)
                        buildJsonObject { put("status", result.status) }
                    },
                )
            )
            add(
                Tool(
                    name = "schedule_message",
                    description = "Schedule a follow-up notification message after a delay. Delivery time is approximate and may vary with iOS background scheduling.",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("reason", buildJsonObject {
                                    put("type", "string")
                                    put("description", "The reason for scheduling this message (e.g., 'Remind user to drink water')")
                                })
                                put("delay_minutes", buildJsonObject {
                                    put("type", "integer")
                                    put("description", "Delay in minutes before sending the message")
                                })
                            },
                            required = listOf("reason", "delay_minutes"),
                        )
                    },
                    execute = { arguments ->
                        val objectValue = arguments as? JsonObject ?: error("arguments must be an object")
                        val reason = (objectValue["reason"] as? JsonPrimitive)?.content?.trim().orEmpty()
                        require(reason.isNotBlank()) { "reason is required" }
                        val delayMinutes = (objectValue["delay_minutes"] as? JsonPrimitive)
                            ?.content?.toLongOrNull()?.coerceAtLeast(0L) ?: 1L
                        val now = Clock.System.now().toEpochMilliseconds()
                        val scheduled = IosScheduledMessage(
                            assistantId = assistant.id,
                            conversationId = conversationId,
                            reason = reason,
                            createdAtEpochMs = now,
                            scheduledAtEpochMs = now + delayMinutes * 60_000L,
                        )
                        mutableState.update { current ->
                            current.copy(scheduledMessages = current.scheduledMessages + scheduled)
                        }
                        persist()
                        scheduleMessageInProcess(scheduled)
                        refreshScheduledMessageBackgroundSchedule()
                        buildJsonObject {
                            put("status", "success")
                            put("scheduled_at", Instant.fromEpochMilliseconds(scheduled.scheduledAtEpochMs).toString())
                            put("work_name", "scheduled-message-${scheduled.id}")
                        }
                    },
                )
            )
            add(
                Tool(
                    name = "get_notifications",
                    description = "Get recent notifications sent by LastChat on this device",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("limit", buildJsonObject {
                                    put("type", "integer")
                                    put("description", "Max number of notifications to retrieve (default 10)")
                                })
                            }
                        )
                    },
                    execute = { arguments ->
                        val limit = ((arguments as? JsonObject)?.get("limit") as? JsonPrimitive)
                            ?.content?.toIntOrNull()?.coerceIn(1, 100) ?: 10
                        val notifications = mutableState.value.notificationHistory
                            .takeLast(limit)
                            .asReversed()
                            .map { notification ->
                                buildJsonObject {
                                    put("package", "lastchat.rikkafork.cocolal.ios")
                                    put("title", notification.title)
                                    put("content", notification.content)
                                    put("time", notification.postTimeEpochMs)
                                }
                            }
                        buildJsonObject { put("notifications", JsonArray(notifications)) }
                    },
                )
            )
        }
        if (IosLocalToolOption.TTS in assistant.localTools) {
            add(
                Tool(
                    name = "text_to_speech",
                    description = "Read text aloud using the currently selected LastChat TTS provider. Use this when the user explicitly wants spoken output.",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("text", buildJsonObject {
                                    put("type", "string")
                                    put("description", "The text to speak aloud")
                                })
                            },
                            required = listOf("text"),
                        )
                    },
                    execute = { arguments ->
                        val text = ((arguments as? JsonObject)?.get("text") as? JsonPrimitive)
                            ?.content?.trim().orEmpty()
                        require(text.isNotBlank()) { "text is required" }
                        val current = mutableState.value
                        if (!current.tts.enabled || !current.hasTtsApiKey) {
                            buildJsonObject {
                                put("success", false)
                                put("error", "No TTS provider selected")
                            }
                        } else {
                            ttsController.speak(text)
                            buildJsonObject {
                                put("success", true)
                                put("provider", current.tts.type.displayName())
                            }
                        }
                    },
                )
            )
        }
        if (IosLocalToolOption.ASK_USER in assistant.localTools) {
            add(
                Tool(
                    name = "ask_user",
                    description = "Ask the user a short structured questionnaire when a clarification or tradeoff would genuinely help. Use this sparingly. Ask at most 5 questions, with up to 3 concise options per question. Each option may include a short description. Do not use this just for chit-chat.",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("questions", buildJsonObject {
                                    put("type", "array")
                                    put("description", "A short questionnaire for the user. Maximum 5 questions.")
                                    put("items", buildJsonObject {
                                        put("type", "object")
                                        put("properties", buildJsonObject {
                                            put("id", stringProperty("Stable question identifier"))
                                            put("question", stringProperty("The question to ask the user"))
                                            put("options", buildJsonObject {
                                                put("type", "array")
                                                put("description", "Up to 3 suggested replies")
                                                put("items", buildJsonObject {
                                                    put("type", "object")
                                                    put("properties", buildJsonObject {
                                                        put("label", stringProperty("Short reply option text"))
                                                        put("description", stringProperty("Optional one-sentence explanation"))
                                                    })
                                                    put("required", JsonArray(listOf(JsonPrimitive("label"))))
                                                })
                                            })
                                        })
                                        put("required", JsonArray(listOf(JsonPrimitive("id"), JsonPrimitive("question"))))
                                    })
                                })
                            },
                            required = listOf("questions"),
                        )
                    },
                    systemPrompt = { _, _ ->
                        """
                            ## tool: ask_user
                            - Use this only when a genuine clarification or meaningful tradeoff would improve your next answer.
                            - It is appropriate when the user's request is ambiguous, underspecified, or could reasonably go in multiple directions.
                            - Ask at most one questionnaire per turn.
                            - Keep it short: at most 5 questions, and at most 3 options per question.
                            - Options should be concise. Add a one-sentence description only when it helps the user distinguish them.
                            - Do not use this for small talk, routine confirmations, or information you can infer safely.
                        """.trimIndent()
                    },
                    approvalMode = ToolApprovalMode.RequiresApproval,
                    execute = { arguments ->
                        val questions = parseIosAskUserQuestions(arguments)
                        require(questions.isNotEmpty()) { "questions must contain at least one valid question" }
                        val pending = IosPendingQuestionnaire(
                            toolCallId = currentExecutingToolCallId
                                ?: error("ask_user is missing its tool call identifier"),
                            conversationId = conversationId,
                            questions = questions,
                        )
                        val waiter = CompletableDeferred<JsonObject>()
                        askUserWaiters[pending.toolCallId] = waiter
                        mutableState.update { it.copy(pendingQuestionnaire = pending) }
                        persist()
                        try {
                            waiter.await()
                        } finally {
                            askUserWaiters.remove(pending.toolCallId)
                            mutableState.update { current ->
                                if (current.pendingQuestionnaire?.toolCallId == pending.toolCallId) {
                                    current.copy(pendingQuestionnaire = null)
                                } else current
                            }
                        }
                    },
                )
            )
        }
        if (IosLocalToolOption.IMAGE_GENERATION in assistant.localTools) {
            add(
                Tool(
                    name = "generate_image",
                    description = "Generate an image with LastChat's selected image generation model and save it to the image gallery. Use this only when the user asks for an image. Improve vague user requests into a concrete visual prompt before calling.",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("prompt", stringProperty("Detailed visual prompt to generate"))
                                put("aspect_ratio", stringProperty("square, landscape, or portrait"))
                                put("count", buildJsonObject {
                                    put("type", "integer")
                                    put("description", "Number of images to generate, 1 to 4")
                                })
                            },
                            required = listOf("prompt"),
                        )
                    },
                    systemPrompt = { _, _ ->
                        """
                            ## Image generation tool
                            When the user asks you to create, draw, render, or generate an image, call `generate_image`.
                            - Rewrite short or vague requests into a richer visual prompt before calling the tool.
                            - After the tool returns, include each returned `markdown_image` in your reply.
                            - Do not call this tool for ordinary image analysis.
                        """.trimIndent()
                    },
                    execute = { arguments -> generateToolImages(arguments) },
                )
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun generateToolImages(
        arguments: JsonElement,
        inputImage: PlatformPickedFile? = null,
    ): JsonObject {
        val params = arguments as? JsonObject ?: error("arguments must be an object")
        val prompt = (params["prompt"] as? JsonPrimitive)?.content?.trim().orEmpty()
        require(prompt.isNotBlank()) { "prompt is required" }
        val aspectRatio = when ((params["aspect_ratio"] as? JsonPrimitive)?.content?.lowercase()) {
            "landscape", "wide" -> ImageAspectRatio.LANDSCAPE
            "portrait", "tall" -> ImageAspectRatio.PORTRAIT
            else -> ImageAspectRatio.SQUARE
        }
        val count = (params["count"] as? JsonPrimitive)?.content?.toIntOrNull()?.coerceIn(1, 4) ?: 1
        val snapshot = mutableState.value
        val imagePreferences = snapshot.imageGeneration
        require(imagePreferences.enabled) { "No image generation model selected" }
        val configuredModel = Model(
            modelId = imagePreferences.modelId,
            displayName = imagePreferences.modelId,
            type = ModelType.IMAGE,
            inputModalities = if (inputImage == null) {
                listOf(Modality.TEXT)
            } else {
                listOf(Modality.TEXT, Modality.IMAGE)
            },
            outputModalities = listOf(Modality.IMAGE),
            imageGenerationMethod = imagePreferences.method,
        )
        val model = if (imagePreferences.providerType == IosImageProviderType.COMFY_UI) {
            configuredModel.withComfyDefaults()
        } else {
            configuredModel
        }
        val providerSetting: ProviderSetting = when (imagePreferences.providerType) {
            IosImageProviderType.OPENAI -> {
                val providerPreferences = snapshot.providerConfigurations
                    .firstOrNull { it.type == IosProviderType.OPENAI }
                    ?: error("OpenAI image provider not found")
                val apiKey = secureStore.readString(apiKeyName(IosProviderType.OPENAI)).orEmpty()
                require(apiKey.isNotBlank()) { "Configure the OpenAI API key first" }
                ProviderSetting.OpenAI(
                name = "OpenAI compatible",
                apiKey = apiKey,
                baseUrl = providerPreferences.baseUrl,
                models = listOf(model),
            )
            }
            IosImageProviderType.GOOGLE -> {
                val providerPreferences = snapshot.providerConfigurations
                    .firstOrNull { it.type == IosProviderType.GOOGLE }
                    ?: error("Google image provider not found")
                val apiKey = secureStore.readString(apiKeyName(IosProviderType.GOOGLE)).orEmpty()
                require(apiKey.isNotBlank()) { "Configure the Google API key first" }
                ProviderSetting.Google(
                name = "Google",
                apiKey = apiKey,
                baseUrl = providerPreferences.baseUrl,
                models = listOf(model),
            )
            }
            IosImageProviderType.COMFY_UI -> ProviderSetting.ComfyUI(
                baseUrl = imagePreferences.comfyUi.baseUrl,
                workflowJson = imagePreferences.comfyUi.workflowJson,
                promptNodeId = imagePreferences.comfyUi.promptNodeId,
                promptInputName = imagePreferences.comfyUi.promptInputName,
                modelNodeId = imagePreferences.comfyUi.modelNodeId,
                modelInputName = imagePreferences.comfyUi.modelInputName,
                models = listOf(model),
            )
        }
        val provider = providerManager.getProviderByType(providerSetting)
        val items = when (model.imageGenerationMethod ?: ImageGenerationMethod.DIFFUSION) {
            ImageGenerationMethod.DIFFUSION -> provider.generateImage(
                providerSetting = providerSetting,
                params = ImageGenerationParams(
                    model = model,
                    prompt = prompt,
                    numOfImages = count,
                    aspectRatio = aspectRatio,
                ),
            ).items
            ImageGenerationMethod.MULTIMODAL -> {
                val parts = buildList {
                    if (inputImage != null) {
                        val bytes = fileStore.readBytes(inputImage.storagePath)
                            ?: error("The selected input image is no longer available")
                        add(
                            UIMessagePart.Image(
                                "data:${inputImage.mimeType};base64,${Base64.Default.encode(bytes)}"
                            )
                        )
                    }
                    add(UIMessagePart.Text(prompt))
                }
                val result = provider.generateText(
                    providerSetting = providerSetting,
                    messages = listOf(UIMessage(role = MessageRole.USER, parts = parts)),
                    params = TextGenerationParams(
                        model = model.copy(
                            outputModalities = (model.outputModalities + Modality.IMAGE).distinct()
                        ),
                        tools = emptyList(),
                    ),
                )
                result.choices.flatMap { choice ->
                    choice.message?.parts.orEmpty().mapNotNull { part ->
                        (part as? UIMessagePart.Image)?.let {
                            ImageGenerationItem(data = it.url, mimeType = imageMimeTypeFromDataUrl(it.url))
                        }
                    }
                }
            }
        }.take(count)
        require(items.isNotEmpty()) { "No images generated" }
        val saved = items.mapIndexed { index, item ->
            val bytes = readGeneratedImageBytes(item)
            val safeModel = imagePreferences.modelId.replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .take(48).ifBlank { "image" }
            val createdAt = Clock.System.now().toEpochMilliseconds()
            val extension = imageExtension(item.mimeType)
            val path = "images/${createdAt}_${safeModel}_tool_$index.$extension"
            fileStore.writeBytes(path, bytes)
            val uri = fileStore.localUrl(path) ?: error("Generated image URL is unavailable")
            IosGeneratedImage(uri, path, prompt, imagePreferences.modelId, createdAt)
        }
        mutableState.update { current ->
            current.copy(generatedImages = (current.generatedImages + saved).takeLast(500))
        }
        persist()
        return buildJsonObject {
            put("success", true)
            put("saved_to_gallery", true)
            put("images", JsonArray(saved.map { image ->
                buildJsonObject {
                    put("uri", image.uri)
                    put("path", image.path)
                    put("markdown_image", "![Generated image](${image.uri})")
                }
            }))
            put("note", "Include images[].markdown_image in your reply so the generated image appears in chat.")
        }
    }

    private suspend fun readGeneratedImageBytes(item: ImageGenerationItem): ByteArray {
        val source = item.data.trim()
        if (source.startsWith("http://") || source.startsWith("https://")) {
            val response = httpClient.execute(PlatformHttpRequest(method = "GET", url = source))
            require(response.statusCode in 200..299) {
                "Generated image download failed: HTTP ${response.statusCode}"
            }
            return response.body
        }
        val encoded = source.substringAfter("base64,", source).trim()
        return runCatching { Base64.Default.decode(encoded) }
            .getOrElse { error("The image provider returned unsupported image data") }
    }

    private suspend fun buildSearchTool(preferences: IosSearchPreferences): Tool? {
        if (!preferences.enabled) return null
        val apiKey = secureStore.readString(searchApiKeyName(preferences.provider)).orEmpty()
        if (preferences.provider.requiresApiKey() && apiKey.isBlank()) {
            error("Configure the ${preferences.provider.displayName()} search API key first.")
        }
        val options = preferences.provider.toOptions(apiKey)
        val service = SearchService.getService(options)
        return Tool(
            name = "search_web",
            description = "Search the web for current, factual, or externally verifiable information.",
            parameters = { service.parameters },
            execute = { arguments ->
                val params = arguments as? JsonObject ?: error("Search arguments must be an object")
                val result = service.search(
                    params = params,
                    commonOptions = SearchCommonOptions(preferences.resultSize),
                    serviceOptions = options,
                ).getOrThrow()
                json.encodeToJsonElement(result)
            },
        )
    }

    private suspend fun hasSearchApiKey(preferences: IosSearchPreferences): Boolean {
        if (!preferences.provider.requiresApiKey()) return true
        return secureStore.readString(searchApiKeyName(preferences.provider)).isNullOrBlank().not()
    }

    // Skills Management
    fun saveSkill(skill: IosSkill) {
        mutableState.update { current ->
            val index = current.skills.indexOfFirst { it.id == skill.id }
            val updated = if (index >= 0) {
                current.skills.toMutableList().apply { set(index, skill) }
            } else {
                current.skills + skill
            }
            current.copy(skills = updated)
        }
        persistAsync()
    }

    fun deleteSkill(id: String) {
        mutableState.update { current ->
            current.copy(skills = current.skills.filterNot { it.id == id })
        }
        persistAsync()
    }

    fun toggleSkill(id: String, enabled: Boolean) {
        mutableState.update { current ->
            current.copy(skills = current.skills.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
        persistAsync()
    }

    // Lorebooks Management
    fun saveLorebook(lorebook: IosLorebook) {
        mutableState.update { current ->
            val index = current.lorebooks.indexOfFirst { it.id == lorebook.id }
            val updated = if (index >= 0) {
                current.lorebooks.toMutableList().apply { set(index, lorebook) }
            } else {
                current.lorebooks + lorebook
            }
            current.copy(lorebooks = updated)
        }
        persistAsync()
    }

    fun deleteLorebook(id: String) {
        mutableState.update { current ->
            current.copy(lorebooks = current.lorebooks.filterNot { it.id == id })
        }
        persistAsync()
    }

    fun toggleLorebook(id: String, enabled: Boolean) {
        mutableState.update { current ->
            current.copy(lorebooks = current.lorebooks.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
        persistAsync()
    }

    fun saveLorebookEntry(lorebookId: String, entry: IosLorebookEntry) {
        mutableState.update { current ->
            val updated = current.lorebooks.map { lb ->
                if (lb.id == lorebookId) {
                    val eIndex = lb.entries.indexOfFirst { it.id == entry.id }
                    val newEntries = if (eIndex >= 0) {
                        lb.entries.toMutableList().apply { set(eIndex, entry) }
                    } else {
                        lb.entries + entry
                    }
                    lb.copy(entries = newEntries)
                } else lb
            }
            current.copy(lorebooks = updated)
        }
        persistAsync()
    }

    fun deleteLorebookEntry(lorebookId: String, entryId: String) {
        mutableState.update { current ->
            val updated = current.lorebooks.map { lb ->
                if (lb.id == lorebookId) {
                    lb.copy(entries = lb.entries.filterNot { it.id == entryId })
                } else lb
            }
            current.copy(lorebooks = updated)
        }
        persistAsync()
    }

    // MCP Management
    fun saveMcpServer(server: IosMcpServerConfig) {
        mutableState.update { current ->
            val index = current.mcpServers.indexOfFirst { it.id == server.id }
            val updated = if (index >= 0) {
                current.mcpServers.toMutableList().apply { set(index, server) }
            } else {
                current.mcpServers + server
            }
            current.copy(mcpServers = updated)
        }
        persistAsync()
    }

    fun deleteMcpServer(id: String) {
        mutableState.update { current ->
            current.copy(mcpServers = current.mcpServers.filterNot { it.id == id })
        }
        persistAsync()
    }

    fun toggleMcpServer(id: String, enabled: Boolean) {
        mutableState.update { current ->
            current.copy(mcpServers = current.mcpServers.map {
                if (it.id == id) it.clone(commonOptions = it.commonOptions.copy(enable = enabled)) else it
            })
        }
        persistAsync()
    }

    suspend fun refreshMcpTools(server: IosMcpServerConfig): Result<List<IosMcpTool>> = runCatching {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", Uuid.random().toString())
            put("method", "tools/list")
            put("params", buildJsonObject {})
        }
        val headers = (server.commonOptions.headers + listOf("Content-Type" to "application/json")).toMap()
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = server.url,
                headers = headers,
                body = payload.toString().encodeToByteArray(),
            )
        )
        val jsonRoot = json.parseToJsonElement(response.body.decodeToString()).jsonObject
        val resultObj = jsonRoot["result"]?.jsonObject
        val toolsArray = resultObj?.get("tools")?.jsonArray
        val discovered: List<IosMcpTool> = toolsArray?.mapNotNull { elem ->
            val obj = elem.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val desc = obj["description"]?.jsonPrimitive?.contentOrNull
            IosMcpTool(enable = true, name = name, description = desc, inputSchema = obj["inputSchema"])
        }.orEmpty()
        val updated = server.clone(
            commonOptions = server.commonOptions.copy(tools = discovered)
        )
        saveMcpServer(updated)
        discovered
    }

    fun toggleMcpTool(serverId: String, toolName: String, enabled: Boolean) {
        mutableState.update { current ->
            val updated = current.mcpServers.map { server ->
                if (server.id == serverId) {
                    val newTools = server.commonOptions.tools.map { t ->
                        if (t.name == toolName) t.copy(enable = enabled) else t
                    }
                    server.clone(commonOptions = server.commonOptions.copy(tools = newTools))
                } else server
            }
            current.copy(mcpServers = updated)
        }
        persistAsync()
    }

    // WebDAV and Backups
    fun updateWebDavConfig(config: IosWebDavConfig) {
        mutableState.update { it.copy(webDavConfig = config) }
        persistAsync()
    }

    suspend fun testWebDav(config: IosWebDavConfig = mutableState.value.webDavConfig): Result<Unit> =
        IosBackupManager.testWebDav(httpClient, config)

    suspend fun listWebDavBackups(config: IosWebDavConfig = mutableState.value.webDavConfig): Result<List<IosWebDavBackupItem>> =
        IosBackupManager.listWebDavBackups(httpClient, config)

    suspend fun backupToWebDav(config: IosWebDavConfig = mutableState.value.webDavConfig): Result<Unit> =
        IosBackupManager.backupToWebDav(httpClient, config, mutableState.value)

    suspend fun restoreFromWebDav(config: IosWebDavConfig, href: String): Result<IosRestoreResult> = runCatching {
        val (newState, result) = IosBackupManager.restoreFromWebDav(httpClient, config, href, mutableState.value).getOrThrow()
        mutableState.value = newState
        persist()
        result
    }

    fun exportBackupArchive(): ByteArray =
        IosBackupManager.createBackupArchive(mutableState.value)

    suspend fun restoreBackupArchive(bytes: ByteArray): Result<IosRestoreResult> = runCatching {
        val (newState, result) = IosBackupManager.restore(bytes, mutableState.value)
        mutableState.value = newState
        persist()
        result
    }

    suspend fun restoreFromPickedFile(picked: PlatformPickedFile): Result<IosRestoreResult> {
        val bytes = fileStore.readBytes(picked.storagePath)
            ?: return Result.failure(IllegalStateException("Could not read backup file"))
        return restoreBackupArchive(bytes)
    }

    suspend fun exportBackupToFile(): Result<PlatformPickedFile> = runCatching {
        val bytes = exportBackupArchive()
        val name = "LastChat_backup_${Clock.System.now().toEpochMilliseconds()}.zip"
        val path = "backups/$name"
        fileStore.writeBytes(path, bytes)
        val localUrl = fileStore.localUrl(path) ?: ""
        PlatformPickedFile(
            storagePath = path,
            localUrl = localUrl,
            displayName = name,
            mimeType = "application/zip",
            kind = PlatformPickedFileKind.Document,
        )
    }

    private fun resolvePromptInjections(
        assistant: IosAssistantPreferences,
        skills: List<IosSkill>,
        lorebooks: List<IosLorebook>,
        recentMessages: List<UIMessage>,
    ): Map<IosInjectionPosition, List<String>> {
        val map = mutableMapOf<IosInjectionPosition, MutableList<String>>()

        // Skills
        skills.filter { it.enabled && (it.alwaysEnabled || it.availableForAllAssistants || it.availableAssistantIds.contains(assistant.id)) }
            .forEach { skill ->
                if (skill.instructions.isNotBlank()) {
                    map.getOrPut(skill.injectionPosition) { mutableListOf() }.add(skill.instructions)
                }
            }

        // Lorebooks
        lorebooks.filter { it.enabled }.forEach { lb ->
            lb.entries.filter { it.enabled }.forEach { entry ->
                if (entry.prompt.isBlank()) return@forEach
                val triggered = when (entry.activationType) {
                    IosLorebookActivationType.ALWAYS -> true
                    IosLorebookActivationType.KEYWORDS -> {
                        val messagesToScan = recentMessages.takeLast(entry.scanDepth).joinToString(" ") { it.toContentText() }
                        entry.keywords.any { kw ->
                            if (kw.isBlank()) false
                            else if (entry.useRegex) {
                                runCatching {
                                    Regex(kw, if (entry.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                                        .containsMatchIn(messagesToScan)
                                }.getOrDefault(false)
                            } else {
                                messagesToScan.contains(kw, ignoreCase = !entry.caseSensitive)
                            }
                        }
                    }
                    IosLorebookActivationType.RAG -> false
                }
                if (triggered) {
                    map.getOrPut(entry.injectionPosition) { mutableListOf() }.add(entry.prompt)
                }
            }
        }
        return map
    }

    private suspend fun buildMcpTools(servers: List<IosMcpServerConfig>): List<Tool> {
        return servers.filter { it.commonOptions.enable }.flatMap { server ->
            server.commonOptions.tools.filter { it.enable }.map { mcpTool ->
                Tool(
                    name = mcpTool.name,
                    description = mcpTool.description ?: "MCP Tool: ${mcpTool.name}",
                    parameters = {
                        mcpTool.inputSchema?.let { schemaElement ->
                            runCatching {
                                json.decodeFromJsonElement(InputSchema.serializer(), schemaElement)
                            }.getOrNull()
                        } ?: InputSchema.Obj(properties = buildJsonObject {}, required = emptyList())
                    },
                    execute = { arguments ->
                        executeMcpToolCall(server, mcpTool.name, arguments)
                    }
                )
            }
        }
    }

    private suspend fun executeMcpToolCall(
        server: IosMcpServerConfig,
        toolName: String,
        arguments: JsonElement,
    ): JsonElement {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", Uuid.random().toString())
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
            })
        }
        val headers = (server.commonOptions.headers + listOf("Content-Type" to "application/json")).toMap()
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = server.url,
                headers = headers,
                body = payload.toString().encodeToByteArray(),
            )
        )
        val respText = response.body.decodeToString()
        return runCatching {
            val root = json.parseToJsonElement(respText).jsonObject
            root["result"] ?: root
        }.getOrElse {
            buildJsonObject {
                put("status", response.statusCode)
                put("output", respText)
            }
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
            search = snapshot.search,
            memories = snapshot.memories,
            skills = snapshot.skills,
            lorebooks = snapshot.lorebooks,
            mcpServers = snapshot.mcpServers,
            webDavConfig = snapshot.webDavConfig,
            tts = snapshot.tts,
            imageGeneration = snapshot.imageGeneration,
            generatedImages = snapshot.generatedImages,
            scheduledMessages = snapshot.scheduledMessages,
            notificationHistory = snapshot.notificationHistory,
            pendingQuestionnaire = snapshot.pendingQuestionnaire,
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
        const val MAX_TOOL_STEPS = 256
        const val ADAPTIVE_MEMORY_MESSAGE_THRESHOLD = 8
        const val ADAPTIVE_MEMORY_INACTIVITY_MS = 10 * 60 * 1_000L
        const val MAX_PENDING_MEMORY_MESSAGES = 20
        const val MAX_MEMORY_OPERATIONS_PER_PASS = 12
        const val MAX_MEMORY_STATEMENT_LENGTH = 600
        fun apiKeyName(type: IosProviderType): String = "provider_apikey_ios_${type.name.lowercase()}"
        fun searchApiKeyName(type: IosSearchProviderType): String =
            "search_apikey_ios_${type.name.lowercase()}"
        fun ttsApiKeyName(type: IosTtsProviderType): String =
            "tts_provider_apikey_ios_${type.name.lowercase()}"
        fun defaultProviderPreferences(type: IosProviderType): IosProviderPreferences = when (type) {
            IosProviderType.OPENAI -> IosProviderPreferences(type, "https://api.openai.com/v1", "gpt-4.1-mini")
            IosProviderType.GOOGLE -> IosProviderPreferences(type, "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash")
            IosProviderType.CLAUDE -> IosProviderPreferences(type, "https://api.anthropic.com/v1", "claude-sonnet-4-5")
        }
    }
}

private fun String.normalizedMemoryKey(fallback: String): String = lowercase()
    .replace(Regex("[^a-z0-9_]+"), "_")
    .trim('_')
    .take(48)
    .ifBlank { fallback }

private fun String.normalizedMemoryText(): String = lowercase().replace(Regex("\\s+"), " ").trim()

internal fun earliestAdaptiveMemoryDeadline(
    nowEpochMs: Long,
    candidates: List<Pair<Long, Int>>,
    messageThreshold: Int = 8,
    inactivityMs: Long = 10 * 60 * 1_000L,
): Long? = candidates.mapNotNull { (updatedAtEpochMs, pendingCount) ->
    when {
        pendingCount <= 0 -> null
        pendingCount >= messageThreshold -> nowEpochMs
        else -> (updatedAtEpochMs + inactivityMs).coerceAtLeast(nowEpochMs)
    }
}.minOrNull()

internal fun earliestScheduledMessageDeadline(messages: List<IosScheduledMessage>): Long? =
    messages.minOfOrNull(IosScheduledMessage::nextAttemptEpochMs)

internal fun scheduledMessageRetryDelayMs(attemptCount: Int): Long =
    (30_000L * (1L shl attemptCount.coerceIn(0, 6))).coerceAtMost(30 * 60 * 1_000L)

internal fun parseIosAskUserQuestions(arguments: JsonElement): List<IosAskUserQuestion> {
    val root = arguments as? JsonObject ?: return emptyList()
    val questions = root["questions"] as? JsonArray ?: return emptyList()
    return questions.mapNotNull { element ->
        val question = element as? JsonObject ?: return@mapNotNull null
        val id = (question["id"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val prompt = (question["question"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (id.isBlank() || prompt.isBlank()) return@mapNotNull null
        val options = (question["options"] as? JsonArray).orEmpty().mapNotNull { optionElement ->
            val option = optionElement as? JsonObject ?: return@mapNotNull null
            val label = (option["label"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            IosAskUserOption(
                label = label,
                description = (option["description"] as? JsonPrimitive)?.content?.trim()
                    ?.takeIf(String::isNotBlank),
            )
        }.distinctBy(IosAskUserOption::label).take(3)
        IosAskUserQuestion(id = id, question = prompt, options = options)
    }.distinctBy(IosAskUserQuestion::id).take(5)
}

internal fun buildAskUserAnswerPayload(
    pending: IosPendingQuestionnaire,
    selectedOptions: Map<String, String>,
    customAnswers: Map<String, String>,
    dismissed: Boolean,
): JsonObject = buildJsonObject {
    put("answers", JsonArray(pending.questions.map { question ->
        val custom = customAnswers[question.id]?.trim().orEmpty()
        val selected = selectedOptions[question.id]?.trim().orEmpty()
        buildJsonObject {
            put("id", question.id)
            when {
                custom.isNotBlank() -> {
                    put("status", "answered")
                    put("source", "custom")
                    put("value", custom)
                }
                selected.isNotBlank() -> {
                    put("status", "answered")
                    put("source", "option")
                    put("value", selected)
                }
                else -> put("status", "skipped")
            }
        }
    }))
    put("dismissed", dismissed)
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
    val search: IosSearchPreferences = IosSearchPreferences(),
    val memories: List<IosMemoryRecord> = emptyList(),
    val skills: List<IosSkill> = emptyList(),
    val lorebooks: List<IosLorebook> = emptyList(),
    val mcpServers: List<IosMcpServerConfig> = emptyList(),
    val webDavConfig: IosWebDavConfig = IosWebDavConfig(),
    val tts: IosTtsPreferences = IosTtsPreferences(),
    val imageGeneration: IosImageGenerationPreferences = IosImageGenerationPreferences(),
    val generatedImages: List<IosGeneratedImage> = emptyList(),
    val scheduledMessages: List<IosScheduledMessage> = emptyList(),
    val notificationHistory: List<IosNotificationRecord> = emptyList(),
    val pendingQuestionnaire: IosPendingQuestionnaire? = null,
    val assistant: IosAssistantPreferences? = null,
    val pendingAttachments: List<IosStoredPendingAttachment> = emptyList(),
)

internal fun IosTtsProviderType.displayName(): String = when (this) {
    IosTtsProviderType.OPENAI -> "OpenAI TTS"
    IosTtsProviderType.GEMINI -> "Gemini TTS"
    IosTtsProviderType.MINIMAX -> "MiniMax TTS"
    IosTtsProviderType.ELEVENLABS -> "ElevenLabs"
    IosTtsProviderType.QWEN -> "Qwen TTS"
    IosTtsProviderType.FISH_AUDIO -> "Fish Audio"
    IosTtsProviderType.CARTESIA -> "Cartesia"
    IosTtsProviderType.PLAY_HT -> "PlayHT"
}

internal fun IosTtsProviderType.defaultPreferences(): IosTtsPreferences = when (this) {
    IosTtsProviderType.OPENAI -> IosTtsPreferences(type = this)
    IosTtsProviderType.GEMINI -> IosTtsPreferences(
        type = this,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        model = "gemini-2.5-flash-preview-tts",
        voice = "Kore",
    )
    IosTtsProviderType.MINIMAX -> IosTtsPreferences(
        type = this,
        baseUrl = "https://api.minimaxi.com/v1",
        model = "speech-2.5-hd-preview",
        voice = "female-shaonv",
        emotion = "calm",
    )
    IosTtsProviderType.ELEVENLABS -> IosTtsPreferences(
        type = this,
        baseUrl = "https://api.elevenlabs.io/v1",
        model = "eleven_multilingual_v2",
        voice = "21m00Tcm4TlvDq8ikWAM",
    )
    IosTtsProviderType.QWEN -> IosTtsPreferences(
        type = this,
        baseUrl = "https://dashscope.aliyuncs.com/api/v1",
        model = "qwen3-tts-flash",
        voice = "Cherry",
    )
    IosTtsProviderType.FISH_AUDIO -> IosTtsPreferences(
        type = this,
        baseUrl = "https://api.fish.audio",
        model = "s2-pro",
        voice = "reference-id",
        language = "mp3",
    )
    IosTtsProviderType.CARTESIA -> IosTtsPreferences(
        type = this,
        baseUrl = "https://api.cartesia.ai",
        model = "sonic-3.5",
        voice = "voice-id",
        language = "en",
    )
    IosTtsProviderType.PLAY_HT -> IosTtsPreferences(
        type = this,
        baseUrl = "https://api.play.ht/api/v2",
        model = "PlayHT2.0",
        voice = "voice-manifest-uri",
    )
}

internal fun IosTtsPreferences.toProviderSetting(apiKey: String): TTSProviderSetting = when (type) {
    IosTtsProviderType.OPENAI -> TTSProviderSetting.OpenAI(
        apiKey = apiKey, baseUrl = baseUrl, model = model, voice = voice,
    )
    IosTtsProviderType.GEMINI -> TTSProviderSetting.Gemini(
        apiKey = apiKey, baseUrl = baseUrl, model = model, voiceName = voice,
    )
    IosTtsProviderType.MINIMAX -> TTSProviderSetting.MiniMax(
        apiKey = apiKey, baseUrl = baseUrl, model = model, voiceId = voice,
        emotion = emotion, speed = speed,
    )
    IosTtsProviderType.ELEVENLABS -> TTSProviderSetting.ElevenLabs(
        apiKey = apiKey, modelId = model, voiceId = voice,
    )
    IosTtsProviderType.QWEN -> TTSProviderSetting.Qwen(
        apiKey = apiKey, baseUrl = baseUrl, model = model, voice = voice,
        languageType = language,
    )
    IosTtsProviderType.FISH_AUDIO -> TTSProviderSetting.FishAudio(
        apiKey = apiKey, baseUrl = baseUrl, model = model, referenceId = voice,
        format = language, speed = speed,
    )
    IosTtsProviderType.CARTESIA -> TTSProviderSetting.Cartesia(
        apiKey = apiKey, baseUrl = baseUrl, modelId = model, voiceId = voice,
        language = language, speed = speed, emotion = emotion,
    )
    IosTtsProviderType.PLAY_HT -> TTSProviderSetting.PlayHT(
        apiKey = apiKey, userId = secondary, baseUrl = baseUrl, voice = voice,
        voiceEngine = model, speed = speed,
    )
}

internal fun IosSearchProviderType.displayName(): String = when (this) {
    IosSearchProviderType.BING -> "Bing"
    IosSearchProviderType.TAVILY -> "Tavily"
    IosSearchProviderType.EXA -> "Exa"
    IosSearchProviderType.BRAVE -> "Brave"
    IosSearchProviderType.PERPLEXITY -> "Perplexity"
    IosSearchProviderType.FIRECRAWL -> "Firecrawl"
    IosSearchProviderType.JINA -> "Jina"
    IosSearchProviderType.LINKUP -> "LinkUp"
    IosSearchProviderType.ZHIPU -> "Zhipu"
    IosSearchProviderType.METASO -> "Metaso"
    IosSearchProviderType.BOCHA -> "Bocha"
    IosSearchProviderType.OLLAMA -> "Ollama"
    IosSearchProviderType.GROK -> "Grok"
    IosSearchProviderType.NANOGPT -> "NanoGPT"
}

internal fun imageMimeTypeFromDataUrl(value: String): String {
    if (!value.startsWith("data:", ignoreCase = true)) return "image/png"
    return value.substringAfter("data:")
        .substringBefore(';')
        .takeIf { it.startsWith("image/") }
        ?: "image/png"
}

internal fun imageExtension(mimeType: String): String = when (mimeType.lowercase().substringBefore(';')) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "png"
}

internal fun IosSearchProviderType.requiresApiKey(): Boolean = this != IosSearchProviderType.BING

internal fun IosSearchProviderType.toOptions(apiKey: String): SearchServiceOptions = when (this) {
    IosSearchProviderType.BING -> SearchServiceOptions.BingLocalOptions()
    IosSearchProviderType.TAVILY -> SearchServiceOptions.TavilyOptions(apiKey = apiKey)
    IosSearchProviderType.EXA -> SearchServiceOptions.ExaOptions(apiKey = apiKey)
    IosSearchProviderType.BRAVE -> SearchServiceOptions.BraveOptions(apiKey = apiKey)
    IosSearchProviderType.PERPLEXITY -> SearchServiceOptions.PerplexityOptions(apiKey = apiKey)
    IosSearchProviderType.FIRECRAWL -> SearchServiceOptions.FirecrawlOptions(apiKey = apiKey)
    IosSearchProviderType.JINA -> SearchServiceOptions.JinaOptions(apiKey = apiKey)
    IosSearchProviderType.LINKUP -> SearchServiceOptions.LinkUpOptions(apiKey = apiKey)
    IosSearchProviderType.ZHIPU -> SearchServiceOptions.ZhipuOptions(apiKey = apiKey)
    IosSearchProviderType.METASO -> SearchServiceOptions.MetasoOptions(apiKey = apiKey)
    IosSearchProviderType.BOCHA -> SearchServiceOptions.BochaOptions(apiKey = apiKey)
    IosSearchProviderType.OLLAMA -> SearchServiceOptions.OllamaOptions(apiKey = apiKey)
    IosSearchProviderType.GROK -> SearchServiceOptions.GrokOptions(apiKey = apiKey)
    IosSearchProviderType.NANOGPT -> SearchServiceOptions.NanoGPTOptions(apiKey = apiKey)
}

@Serializable
private data class IosStoredPendingAttachment(
    val storagePath: String,
    val displayName: String,
    val mimeType: String,
    val kind: PlatformPickedFileKind,
)
