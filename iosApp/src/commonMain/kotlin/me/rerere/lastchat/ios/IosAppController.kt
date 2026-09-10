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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
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
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.currentVersionMessages
import me.rerere.ai.ui.mergeCurrentVersionMessages
import me.rerere.ai.ui.toMessageNode
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformPickedFile
import me.rerere.common.platform.PlatformPickedFileKind
import me.rerere.common.platform.SecureSettingsStore
import me.rerere.lastchat.ios.backup.IOS_BACKUP_MANIFEST_ENTRY
import me.rerere.lastchat.ios.backup.IOS_BACKUP_SETTINGS_ENTRY
import me.rerere.lastchat.ios.backup.IosBackupDataImporter
import me.rerere.lastchat.ios.backup.IosBackupImporter
import me.rerere.lastchat.ios.backup.IosBackupImportPlan
import me.rerere.lastchat.ios.backup.IosBackupImportReport
import me.rerere.lastchat.ios.backup.IosBackupManifest
import me.rerere.lastchat.ios.backup.IosBackupZip
import me.rerere.lastchat.ios.backup.IosSqliteFile
import me.rerere.lastchat.ios.backup.IosZipEntry
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
    val embeddingProviderId: String? = null,
    val embeddingModelId: String = "text-embedding-3-small",
    val ragSimilarityThreshold: Float = 0.45f,
    val ragLimit: Int = 10,
    val localTools: Set<IosLocalToolOption> = emptySet(),
    /** Legacy per-type embedding provider; migrated into [embeddingProviderId] on load. */
    val embeddingProviderType: IosProviderType? = null,
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
    val providerId: String? = null,
    val modelId: String = "gpt-image-1",
    val method: ImageGenerationMethod = ImageGenerationMethod.DIFFUSION,
)

@Serializable
enum class IosImageProviderType { OPENAI, GOOGLE, COMFY_UI }

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
    /** Legacy flat storage; kept so pre-branching state files decode, then migrated into messageNodes. */
    val messages: List<UIMessage> = emptyList(),
    val messageNodes: List<MessageNode> = emptyList(),
    val isPinned: Boolean = false,
    val updatedAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    val memoryLastMessageId: String? = null,
) {
    /** The visible message path, resolved with the same version-selection semantics as Android. */
    val currentMessages: List<UIMessage>
        get() = if (messageNodes.isEmpty()) messages else messageNodes.currentVersionMessages()

    fun migratedToNodes(): IosConversation = copy(
        messages = emptyList(),
        messageNodes = messageNodes.ifEmpty { messages.map { it.toMessageNode() } },
    )

    fun merged(messages: List<UIMessage>): IosConversation = copy(
        messages = emptyList(),
        messageNodes = messageNodes.mergeCurrentVersionMessages(messages),
    )

    fun withoutTrailingBlankAssistant(): IosConversation {
        val nodes = messageNodes.toMutableList()
        val lastNode = nodes.lastOrNull() ?: return this
        val current = lastNode.currentMessage
        // A blank assistant snapshot is the streaming placeholder; keep tool-call turns intact
        if (lastNode.role != MessageRole.ASSISTANT || current.toText().isNotBlank() || current.getToolCalls().isNotEmpty()) {
            return this
        }
        return when {
            lastNode.messages.size == 1 -> copy(messages = emptyList(), messageNodes = nodes.dropLast(1))
            else -> {
                val fallback = lastNode.messages.indexOfLast {
                    it.toText().isNotBlank() && it.getToolCalls().isEmpty()
                }
                if (fallback == -1) {
                    copy(messages = emptyList(), messageNodes = nodes.dropLast(1))
                } else {
                    copy(messages = emptyList(), messageNodes = nodes.dropLast(1) + lastNode.copy(selectIndex = fallback))
                }
            }
        }
    }
}

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
    val providers: List<ProviderSetting> = emptyList(),
    val selectedChatModelId: String? = null,
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

    /** The selected chat model paired with its provider, resolved like Android's chatModelId. */
    val selectedChatModel: Pair<ProviderSetting, Model>?
        get() = findProviderModel(providers, selectedChatModelId)
}

/** Resolves a model reference (`Model.id` string) against the provider list. */
internal fun findProviderModel(
    providers: List<ProviderSetting>,
    modelRef: String?,
): Pair<ProviderSetting, Model>? {
    if (modelRef == null) return null
    providers.forEach { provider ->
        val model = provider.models.firstOrNull { it.id.toString() == modelRef } ?: return@forEach
        return provider to model
    }
    return null
}

/** The iOS-supported chat provider kind of a shared ProviderSetting, or null when the
 *  provider type cannot be used on iOS (LiteRT local, etc.). */
internal fun chatProviderType(provider: ProviderSetting): IosProviderType? = when (provider) {
    is ProviderSetting.OpenAI -> IosProviderType.OPENAI
    is ProviderSetting.Google -> IosProviderType.GOOGLE
    is ProviderSetting.Claude -> IosProviderType.CLAUDE
    else -> null
}

/** Swaps in the request-time API key and single model snapshot. */
internal fun ProviderSetting.withApiKey(apiKey: String, model: Model): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(apiKey = apiKey, models = listOf(model))
    is ProviderSetting.Google -> copy(apiKey = apiKey, models = listOf(model))
    is ProviderSetting.Claude -> copy(apiKey = apiKey, models = listOf(model))
    is ProviderSetting.ComfyUI -> this
    else -> this
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
            val legacyProviders = stored?.providerConfigurations.orEmpty().mapNotNull { config ->
                migrateLegacyProvider(config)
            }
            val providers = stored?.providers.orEmpty().ifEmpty { legacyProviders }
            val selectedChatModelId = stored?.selectedChatModelId
                ?: providers.firstNotNullOfOrNull { provider ->
                    provider.models.firstOrNull { it.type == ModelType.CHAT }?.id?.toString()
                }
            val legacyAssistant = stored?.assistant ?: IosAssistantPreferences()
            val assistants = stored?.assistants.orEmpty().ifEmpty { listOf(legacyAssistant) }
                .map { assistant ->
                    if (assistant.embeddingProviderId == null && assistant.embeddingProviderType != null) {
                        assistant.copy(
                            embeddingProviderId = providers.firstOrNull { provider ->
                                chatProviderType(provider) == assistant.embeddingProviderType
                            }?.id?.toString(),
                        )
                    } else assistant
                }
            val selectedAssistantId = stored?.selectedAssistantId
                ?.takeIf { selected -> assistants.any { it.id == selected } }
                ?: assistants.first().id
            val conversations = stored?.conversations.orEmpty()
                .ifEmpty { listOf(IosConversation(assistantId = selectedAssistantId)) }
                .map { conversation ->
                    val withAssistant = if (conversation.assistantId == null) {
                        conversation.copy(assistantId = selectedAssistantId)
                    } else conversation
                    withAssistant.migratedToNodes()
                }
            val selectedConversationId = stored?.selectedConversationId
                ?.takeIf { selected -> conversations.any { it.id == selected } }
                ?: conversations.first().id
            val restoredAssistantId = conversations
                .firstOrNull { it.id == selectedConversationId }
                ?.assistantId
                ?.takeIf { id -> assistants.any { it.id == id } }
                ?: selectedAssistantId
            val selectedModel = findProviderModel(providers, selectedChatModelId)
            val selectedProviderId = selectedModel?.first?.id?.toString()
            val ttsPreferences = stored?.tts ?: IosTtsPreferences()
            val ttsApiKey = secureStore.readString(ttsApiKeyName(ttsPreferences.type)).orEmpty()
            mutableState.value = IosAppState(
                loading = false,
                conversations = conversations,
                selectedConversationId = selectedConversationId,
                providers = providers,
                selectedChatModelId = selectedChatModelId,
                appearance = stored?.appearance ?: IosAppearancePreferences(),
                assistants = assistants,
                selectedAssistantId = restoredAssistantId,
                search = stored?.search ?: IosSearchPreferences(),
                memories = stored?.memories.orEmpty(),
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
                hasApiKey = selectedProviderId?.let { providerId ->
                    secureStore.readString(apiKeyName(providerId)).isNullOrBlank().not()
                } ?: false,
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

    /** Converts a legacy per-type provider configuration into a shared ProviderSetting,
     *  moving its Keychain entry to the new provider-scoped key. */
    private suspend fun migrateLegacyProvider(config: IosProviderPreferences): ProviderSetting? {
        val providerId = Uuid.random().toString()
        val legacyKey = secureStore.readString(legacyApiKeyName(config.type)).orEmpty()
        if (legacyKey.isNotBlank()) secureStore.writeString(apiKeyName(providerId), legacyKey)
        val model = Model(
            modelId = config.modelId,
            displayName = config.modelId,
            type = ModelType.CHAT,
        )
        return when (config.type) {
            IosProviderType.OPENAI -> ProviderSetting.OpenAI(
                name = "OpenAI compatible",
                baseUrl = config.baseUrl,
                models = listOf(model),
            )
            IosProviderType.GOOGLE -> ProviderSetting.Google(
                name = "Google",
                baseUrl = config.baseUrl,
                models = listOf(model),
            )
            IosProviderType.CLAUDE -> ProviderSetting.Claude(
                name = "Claude",
                baseUrl = config.baseUrl,
                models = listOf(model),
            )
        }
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

    fun saveProvider(providerId: String, name: String, baseUrl: String, apiKey: String) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (name.isBlank() || normalizedBaseUrl.isBlank()) {
            mutableState.update { it.copy(error = "Provider name and base URL are required.") }
            return
        }
        scope.launch {
            if (apiKey.isNotBlank()) secureStore.writeString(apiKeyName(providerId), apiKey.trim())
            val hasSelectedKey = mutableState.value.selectedChatModel
                ?.takeIf { (provider, _) -> provider.id.toString() == providerId }
                ?.let { secureStore.readString(apiKeyName(providerId)).isNullOrBlank().not() }
            mutableState.update { current ->
                current.copy(
                    providers = current.providers.map { provider ->
                        if (provider.id.toString() != providerId) provider else when (provider) {
                            is ProviderSetting.OpenAI -> provider.copy(name = name, baseUrl = normalizedBaseUrl)
                            is ProviderSetting.Google -> provider.copy(name = name, baseUrl = normalizedBaseUrl)
                            is ProviderSetting.Claude -> provider.copy(name = name, baseUrl = normalizedBaseUrl)
                            else -> provider
                        }
                    },
                    hasApiKey = hasSelectedKey ?: current.hasApiKey,
                    error = null,
                )
            }
            persist()
        }
    }

    fun addProvider(name: String, type: IosProviderType) {
        val provider = when (type) {
            IosProviderType.OPENAI -> ProviderSetting.OpenAI(name = name)
            IosProviderType.GOOGLE -> ProviderSetting.Google(name = name)
            IosProviderType.CLAUDE -> ProviderSetting.Claude(name = name)
        }
        mutableState.update { current ->
            current.copy(providers = current.providers + provider)
        }
        persistAsync()
    }

    fun deleteProvider(providerId: String) {
        mutableState.update { current ->
            val remaining = current.providers.filterNot { it.id.toString() == providerId }
            current.copy(
                providers = remaining,
                selectedChatModelId = current.selectedChatModelId
                    ?.takeIf { ref -> findProviderModel(remaining, ref) != null },
            )
        }
        scope.launch { secureStore.remove(apiKeyName(providerId)) }
        persistAsync()
    }

    fun addModelToProvider(providerId: String, modelId: String) {
        val normalized = modelId.trim()
        if (normalized.isBlank()) return
        mutableState.update { current ->
            current.copy(providers = current.providers.map { provider ->
                if (provider.id.toString() != providerId) provider else provider.addModel(
                    Model(modelId = normalized, displayName = normalized, type = ModelType.CHAT),
                )
            })
        }
        persistAsync()
    }

    fun removeModelFromProvider(providerId: String, modelRef: String) {
        mutableState.update { current ->
            current.copy(providers = current.providers.map { provider ->
                if (provider.id.toString() != providerId) provider else provider.models
                    .firstOrNull { it.id.toString() == modelRef }
                    ?.let(provider::delModel)
                    ?: provider
            })
        }
        persistAsync()
    }

    fun selectDefaultModel(providerId: String, modelDisplayId: String) {
        val provider = mutableState.value.providers
            .firstOrNull { it.id.toString() == providerId } ?: return
        val model = provider.models
            .firstOrNull { it.modelId == modelDisplayId && it.type == ModelType.CHAT } ?: return
        mutableState.update { it.copy(selectedChatModelId = model.id.toString()) }
        persistAsync()
    }

    fun clearProviderApiKey(providerId: String) {
        scope.launch {
            secureStore.remove(apiKeyName(providerId))
            val selectedProviderId = mutableState.value.selectedChatModel?.first?.id?.toString()
            if (selectedProviderId == providerId) {
                mutableState.update { it.copy(hasApiKey = false) }
            }
        }
    }

    fun clearApiKey() {
        val providerId = mutableState.value.selectedChatModel?.first?.id?.toString() ?: return
        clearProviderApiKey(providerId)
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
        if (preferences.enabled && preferences.providerId.isNullOrBlank()) {
            mutableState.update { it.copy(error = "Select the image generation provider first.") }
            return
        }
        mutableState.update {
            it.copy(
                imageGeneration = preferences.copy(modelId = modelId),
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
        embeddingProviderId: String?,
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
                            embeddingProviderId = embeddingProviderId,
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

    private data class GenerationTarget(
        val providerSetting: ProviderSetting,
        val model: Model,
    )

    private suspend fun readApiKey(providerId: String): String =
        secureStore.readString(apiKeyName(providerId)).orEmpty()

    private suspend fun resolveChatGeneration(): GenerationTarget? {
        val selected = mutableState.value.selectedChatModel
        if (selected == null) {
            mutableState.update { it.copy(error = "Select a chat model in Settings first.") }
            return null
        }
        val apiKey = readApiKey(selected.first.id.toString())
        if (apiKey.isBlank()) {
            mutableState.update {
                it.copy(error = "Configure the ${selected.first.name} API key in Settings first.")
            }
            return null
        }
        return GenerationTarget(selected.first.withApiKey(apiKey, selected.second), selected.second)
    }

    fun send(text: String) {
        val prompt = text.trim()
        val snapshot = mutableState.value
        val conversation = snapshot.selectedConversation ?: return
        if ((prompt.isEmpty() && snapshot.pendingAttachments.isEmpty()) || snapshot.generating) return
        generationJob = scope.launch {
            val target = resolveChatGeneration()
            if (target == null) {
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
                    title = if (current.currentMessages.isEmpty()) {
                        prompt.ifBlank { snapshot.pendingAttachments.first().displayName }.take(48)
                    } else current.title,
                ).merged(current.currentMessages + userMessage + assistantMessage)
            }
            mutableState.update {
                it.copy(generating = true, pendingAttachments = emptyList(), error = null)
            }
            persist()

            val model = target.model
            val selectedMemories = runCatching {
                selectMemories(snapshot.assistant, prompt)
            }.getOrElse { emptyList() }
            val memoryPrompt = buildMemoryPrompt(
                memories = selectedMemories,
                includeToolGuide = snapshot.assistant.memoryMode != IosMemoryMode.OFF,
            )
            val stableSystemPrompt = buildList {
                snapshot.assistant.systemPrompt.takeIf(String::isNotBlank)?.let(::add)
                if (snapshot.assistant.memoryMode == IosMemoryMode.BASIC && memoryPrompt.isNotBlank()) {
                    add(memoryPrompt)
                }
            }.joinToString("\n\n")
            val systemMessages = stableSystemPrompt.takeIf(String::isNotBlank)
                ?.let { listOf(UIMessage.system(it)) }.orEmpty()
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
            val requestMessages = systemMessages + conversation.currentMessages + requestUserMessage
            var generationSucceeded = false
            try {
                val tools = listOfNotNull(buildSearchTool(snapshot.search)) +
                    buildMemoryTools(snapshot.assistant) +
                    buildLocalTools(snapshot.assistant, conversation.id)
                val toolGuide = tools.joinToString("\n") { it.systemPrompt(model, requestMessages) }.trim()
                val providerRequestMessages = systemMessages +
                    toolGuide.takeIf(String::isNotBlank)?.let { listOf(UIMessage.system(it)) }.orEmpty() +
                    requestMessages.drop(systemMessages.size)
                runProviderToolLoop(
                    target.providerSetting,
                    model,
                    conversation.id,
                    tools,
                    providerRequestMessages,
                )
                generationSucceeded = true
            } catch (cancellation: CancellationException) {
                updateConversation(conversation.id) { current ->
                    current.withoutTrailingBlankAssistant()
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

    private fun updateConversation(id: String, transform: (IosConversation) -> IosConversation) {
        mutableState.update { current ->
            current.copy(conversations = current.conversations.map { conversation ->
                if (conversation.id == id) {
                    transform(conversation).copy(updatedAtEpochMs = Clock.System.now().toEpochMilliseconds())
                } else conversation
            })
        }
    }

    fun updateNodeSelection(conversationId: String, nodeId: String, selectIndex: Int) {
        updateConversation(conversationId) { current ->
            current.copy(
                messages = emptyList(),
                messageNodes = current.messageNodes.map { node ->
                    if (node.id.toString() == nodeId) node.copy(selectIndex = selectIndex) else node
                },
            )
        }
    }

    /** Branches the node containing [messageId] with a new version carrying the edited parts. */
    fun editMessage(conversationId: String, messageId: String, parts: List<UIMessagePart>) {
        if (parts.isEmpty()) return
        updateConversation(conversationId) { current ->
            current.withEditedMessage(messageId, parts)
        }
        persistAsync()
    }

    fun deleteMessage(conversationId: String, messageId: String) {
        updateConversation(conversationId) { current ->
            current.withDeletedMessage(messageId)
        }
        persistAsync()
    }

    fun forkConversation(conversationId: String, messageId: String) {
        val conversation = mutableState.value.conversations
            .firstOrNull { it.id == conversationId } ?: return
        val fork = buildIosForkConversation(conversation, messageId) ?: return
        mutableState.update { current ->
            current.copy(
                conversations = current.conversations + fork,
                selectedConversationId = fork.id,
            )
        }
        persistAsync()
    }

    /**
     * Regenerates the trailing assistant turn: appends a tagged placeholder version to the
     * first assistant node of the turn and re-runs the tool loop. The shared version-selection
     * resolution then shows only the new version while old snapshots stay selectable.
     */
    fun regenerateResponse(conversationId: String) {
        val snapshot = mutableState.value
        if (snapshot.generating) return
        val conversation = snapshot.conversations.firstOrNull { it.id == conversationId } ?: return
        val nodes = conversation.messageNodes
        val lastUserIndex = nodes.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIndex == -1 || lastUserIndex == nodes.lastIndex) return
        val turnStart = lastUserIndex + 1
        val firstAssistantOfTurn = nodes.drop(turnStart)
            .indexOfFirst { it.role == MessageRole.ASSISTANT }
            .takeIf { it >= 0 }
            ?.plus(turnStart)
            ?: return
        generationJob = scope.launch {
            val target = resolveChatGeneration()
            if (target == null) {
                generationJob = null
                return@launch
            }
            val tag = Uuid.random().toString()
            updateConversation(conversationId) { current ->
                val currentNodes = current.messageNodes.toMutableList()
                val node = currentNodes.getOrNull(firstAssistantOfTurn)
                    ?: return@updateConversation current
                val placeholder = UIMessage.assistant("").copy(versionTag = tag)
                currentNodes[firstAssistantOfTurn] = node.copy(
                    messages = node.messages + placeholder,
                    selectIndex = node.messages.size,
                )
                current.copy(messages = emptyList(), messageNodes = currentNodes)
            }
            mutableState.update { it.copy(generating = true, error = null) }
            persist()
            var generationSucceeded = false
            try {
                val history = mutableState.value.conversations
                    .firstOrNull { it.id == conversationId }
                    ?.messageNodes.orEmpty()
                    .take(lastUserIndex + 1)
                    .currentVersionMessages()
                val assistant = snapshot.assistant
                val prompt = history.lastOrNull { it.role == MessageRole.USER }
                    ?.toText().orEmpty()
                val selectedMemories = runCatching {
                    selectMemories(assistant, prompt)
                }.getOrElse { emptyList() }
                val memoryPrompt = buildMemoryPrompt(
                    memories = selectedMemories,
                    includeToolGuide = assistant.memoryMode != IosMemoryMode.OFF,
                )
                val systemMessages = buildList {
                    assistant.systemPrompt.takeIf(String::isNotBlank)?.let(::add)
                    if (assistant.memoryMode == IosMemoryMode.BASIC && memoryPrompt.isNotBlank()) {
                        add(memoryPrompt)
                    }
                }.joinToString("\n\n")
                    .takeIf(String::isNotBlank)
                    ?.let { listOf(UIMessage.system(it)) }.orEmpty()
                val requestMessages = if (
                    assistant.memoryMode == IosMemoryMode.SEARCHABLE ||
                    assistant.memoryMode == IosMemoryMode.ADAPTIVE
                ) {
                    history.mapIndexed { index, message ->
                        if (index == history.lastIndex && message.role == MessageRole.USER) {
                            message.copy(
                                parts = if (memoryPrompt.isBlank()) message.parts else {
                                    listOf(UIMessagePart.Text("<system>\n$memoryPrompt\n</system>\n\n")) + message.parts
                                },
                            )
                        } else message
                    }
                } else history
                val model = target.model
                val tools = listOfNotNull(buildSearchTool(snapshot.search)) +
                    buildMemoryTools(assistant) +
                    buildLocalTools(assistant, conversationId)
                val toolGuide = tools.joinToString("\n") { it.systemPrompt(model, requestMessages) }.trim()
                val providerRequestMessages = systemMessages +
                    toolGuide.takeIf(String::isNotBlank)?.let { listOf(UIMessage.system(it)) }.orEmpty() +
                    requestMessages
                runProviderToolLoop(
                    target.providerSetting,
                    model,
                    conversationId,
                    tools,
                    providerRequestMessages,
                )
                generationSucceeded = true
            } catch (cancellation: CancellationException) {
                updateConversation(conversationId) { current ->
                    current.withoutTrailingBlankAssistant()
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
                    scheduleAdaptiveMemory(conversationId)
                }
                generationJob = null
            }
        }
    }

    private suspend fun runProviderToolLoop(
        providerSetting: ProviderSetting,
        model: Model,
        conversationId: String,
        tools: List<Tool>,
        initialProviderMessages: List<UIMessage>,
    ) {
        var providerMessages = initialProviderMessages
        var toolStep = 0
        var lastCheckpointAt = Clock.System.now().toEpochMilliseconds()
        while (true) {
            providerFlow(providerSetting, model, providerMessages, tools).collect { chunk ->
                updateConversation(conversationId) { current ->
                    val currentMessages = current.currentMessages
                    val last = currentMessages.lastOrNull()
                    if (last?.role != MessageRole.ASSISTANT) current else current.merged(
                        currentMessages.dropLast(1) + (last + chunk),
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
                ?.currentMessages
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
                current.merged(current.currentMessages + toolMessage + UIMessage.assistant(""))
            }
            providerMessages = providerMessages + assistantResponse + toolMessage
            persist()
        }
    }

    private suspend fun providerFlow(
        providerSetting: ProviderSetting,
        model: Model,
        messages: List<UIMessage>,
        tools: List<Tool>,
    ): kotlinx.coroutines.flow.Flow<MessageChunk> =
        providerManager.getProviderByType(providerSetting).streamText(
            providerSetting, messages, TextGenerationParams(model = model, tools = tools),
        )

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

        val history = conversation.currentMessages
            .asSequence()
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .filter { it.toContentText().isNotBlank() }
            .toList()
            .takeLast(10)
            .joinToString("\n") { message -> "${message.role.name}: ${message.toContentText()}" }
        val lastUserText = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }
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
        val target = resolveChatGeneration()
        requireNotNull(target) { "Configure a chat model and its API key first." }
        val response = generateBackgroundText(
            providerSetting = target.providerSetting,
            model = target.model,
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
        val toolCall = conversation.currentMessages.asSequence()
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
            current.merged(current.currentMessages + toolMessage + UIMessage.assistant(""))
        }
        mutableState.update { it.copy(generating = true, pendingQuestionnaire = null, error = null) }
        persist()
        generationJob = scope.launch {
            try {
                val target = resolveChatGeneration()
                    ?: error("Configure a chat model and its API key first.")
                val tools = listOfNotNull(buildSearchTool(mutableState.value.search)) +
                    buildMemoryTools(assistant) + buildLocalTools(assistant, conversation.id)
                val systemMessages = assistant.systemPrompt.takeIf(String::isNotBlank)
                    ?.let { listOf(UIMessage.system(it)) }.orEmpty()
                val storedMessages = mutableState.value.conversations
                    .first { it.id == conversation.id }.currentMessages.dropLast(1)
                val model = target.model
                val toolGuide = tools.joinToString("\n") {
                    it.systemPrompt(model, systemMessages + storedMessages)
                }.trim()
                runProviderToolLoop(
                    providerSetting = target.providerSetting,
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
        val messages = conversation.currentMessages.filter { message ->
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
        val target = resolveChatGeneration() ?: return
        val model = target.model
        val prompt = buildTemporalMemoryExtractionPrompt(sourceMessages, existing)
        val response = generateBackgroundText(target.providerSetting, model, prompt)
        val text = response.choices.firstOrNull()?.message?.toContentText().orEmpty()
        val extraction = parseMemoryExtraction(text) ?: return
        applyAdaptiveExtraction(assistant, conversationId, sourceMessages, extraction)
        updateConversation(conversationId) { current ->
            current.copy(memoryLastMessageId = sourceMessages.last().id)
        }
        persist()
    }

    private suspend fun generateBackgroundText(
        providerSetting: ProviderSetting,
        model: Model,
        prompt: String,
        temperature: Float = 0.1f,
        thinkingBudget: Int? = null,
    ): MessageChunk = providerManager.getProviderByType(providerSetting).generateText(
        providerSetting, listOf(UIMessage.user(prompt)), TextGenerationParams(
            model = model,
            temperature = temperature,
            thinkingBudget = thinkingBudget,
        ),
    )

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
        val provider = mutableState.value.providers
            .firstOrNull { it.id.toString() == assistant.embeddingProviderId }
            ?: error("Configure the embedding provider in memory settings first")
        val apiKey = readApiKey(provider.id.toString())
        require(apiKey.isNotBlank()) {
            "Configure the ${provider.name} API key before using searchable memory"
        }
        val model = Model(
            modelId = assistant.embeddingModelId,
            displayName = assistant.embeddingModelId,
            type = ModelType.EMBEDDING,
        )
        val chunks = PortableMemoryChunker.chunkText(content)
        val setting = provider.withApiKey(apiKey, model)
        val embeddings = when (setting) {
            is ProviderSetting.Claude -> error("Claude does not provide an embedding endpoint")
            else -> providerManager.getProviderByType(setting)
                .createEmbedding(setting, chunks, model)
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
        val providerSetting = snapshot.providers
            .firstOrNull { it.id.toString() == imagePreferences.providerId }
            ?: error("Select the image generation provider in Settings first")
        val apiKey = if (providerSetting is ProviderSetting.ComfyUI) "" else {
            readApiKey(providerSetting.id.toString()).also { key ->
                require(key.isNotBlank()) { "Configure the ${providerSetting.name} API key first" }
            }
        }
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
        val model = if (providerSetting is ProviderSetting.ComfyUI) {
            configuredModel.withComfyDefaults()
        } else {
            configuredModel
        }
        val effectiveSetting = providerSetting.withApiKey(apiKey, model)
        val provider = providerManager.getProviderByType(effectiveSetting)
        val items = when (model.imageGenerationMethod ?: ImageGenerationMethod.DIFFUSION) {
            ImageGenerationMethod.DIFFUSION -> provider.generateImage(
                providerSetting = effectiveSetting,
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
                    providerSetting = effectiveSetting,
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

    /**
     * Restores the configuration tier of a LastChat Android backup zip: providers,
     * assistants, appearance, search and TTS settings. Conversations and attachment
     * files are reported as not yet imported.
     */
    internal fun restoreAndroidBackup(storagePath: String, onResult: (Result<IosBackupImportReport>) -> Unit) {
        scope.launch {
            onResult(runCatching {
                val archive = fileStore.readBytes(storagePath)
                    ?: error("Could not read the selected backup file")
                restoreAndroidBackupInternal(archive)
            })
        }
    }

    private suspend fun restoreAndroidBackupInternal(archive: ByteArray): IosBackupImportReport {
        val entries = IosBackupZip.read(
            archive = archive,
            wanted = setOf(
                IOS_BACKUP_SETTINGS_ENTRY,
                IOS_BACKUP_MANIFEST_ENTRY,
            ) + IOS_BACKUP_DATABASE_ENTRIES,
            maxEntryBytes = DATA_RESTORE_MAX_ENTRY_BYTES,
            namePredicate = { name ->
                val dir = name.substringBefore('/')
                dir in IosBackupDataImporter.FILE_DIRS && name.substringAfter('/', "").isNotEmpty()
            },
        ) ?: error("The selected file is not a valid backup archive")
        val settingsEntry = entries.firstOrNull { it.name == IOS_BACKUP_SETTINGS_ENTRY && it.data != null }
            ?: error("The archive does not contain settings.json and is not a LastChat backup")
        val manifest = entries.firstOrNull { it.name == IOS_BACKUP_MANIFEST_ENTRY }
            ?.data
            ?.decodeToString()
            .let { encoded -> IosBackupImporter.parseManifest(encoded) }
        val settingsData = settingsEntry.data
            ?: error("settings.json could not be read from the backup")
        val settings = json.parseToJsonElement(settingsData.decodeToString()) as? JsonObject
            ?: error("settings.json in the backup is not a JSON object")
        val plan = IosBackupImporter.buildImportPlan(settings)
        applyIosBackupImportPlan(plan)
        val report = plan.toReport(manifest)
        val (dataApplied, dataSkipped) = restoreAndroidBackupData(entries, manifest)
        return report.copy(
            applied = report.applied + dataApplied,
            skipped = report.skipped + dataSkipped,
        )
    }

    /** Imports the database tier of the backup: conversations, memories, and managed
     *  attachment/media files. Returns applied lines and honest skip notes. */
    private suspend fun restoreAndroidBackupData(
        entries: List<IosZipEntry>,
        manifest: IosBackupManifest?,
    ): Pair<List<String>, List<String>> {
        if (manifest?.includesDatabase != true) return emptyList<String>() to emptyList()
        val databaseEntry = entries.firstOrNull {
            it.name in IOS_BACKUP_DATABASE_ENTRIES && it.data != null && it.data.isNotEmpty()
        }
        val databaseData = databaseEntry?.data
        if (databaseData == null || databaseData.isEmpty()) {
            return emptyList<String>() to listOf(
                "Backup manifest lists a database but it was not found in the archive",
            )
        }

        val warnings = mutableListOf<String>()
        val conversations = mutableListOf<IosConversation>()
        val memories = mutableListOf<IosMemoryRecord>()
        runCatching {
            val database = IosSqliteFile(databaseData)
            database.readTable(IosBackupDataImporter.CONVERSATIONS_TABLE) { row ->
                IosBackupDataImporter.mapConversationRow(row, warnings)?.let(conversations::add)
            }
            database.readTable(IosBackupDataImporter.MEMORIES_TABLE) { row ->
                IosBackupDataImporter.mapMemoryRow(row)?.let(memories::add)
            }
        }.getOrElse {
            return emptyList<String>() to listOf(
                "The backup database could not be read as a SQLite database",
            )
        }

        var extractedFiles = 0
        entries.forEach { entry ->
            val data = entry.data ?: return@forEach
            val dir = entry.name.substringBefore('/')
            if (dir in IosBackupDataImporter.FILE_DIRS && entry.name.substringAfter('/', "").isNotEmpty()) {
                fileStore.writeBytes(entry.name, data)
                extractedFiles++
            }
        }

        val remapped = conversations.map { conversation ->
            IosBackupDataImporter.withRemappedAttachmentUrls(conversation) { url ->
                IosBackupDataImporter.androidAttachmentRemap(url) { storagePath ->
                    fileStore.localUrl(storagePath)
                }
            }
        }

        mutableState.update { current ->
            val importedConversations = remapped.ifEmpty {
                listOf(IosConversation(assistantId = current.selectedAssistantId))
            }
            current.copy(
                conversations = importedConversations,
                selectedConversationId = importedConversations
                    .maxByOrNull { it.updatedAtEpochMs }?.id,
                memories = memories,
            )
        }
        persist()

        val applied = mutableListOf<String>()
        if (conversations.isNotEmpty()) {
            applied += "Database: imported ${conversations.size} conversations (including message versions)"
        }
        if (memories.isNotEmpty()) {
            applied += "Database: imported ${memories.size} memories"
        }
        if (extractedFiles > 0) {
            applied += "Files: extracted $extractedFiles attachment/media files"
        }
        val skipped = buildList {
            addAll(warnings)
            if (conversations.isEmpty()) {
                add("The backup database contained no readable conversations")
            }
            add("Memory embeddings are re-computed on iOS (Android stores them in a different format)")
            add("Usage statistics, daily activity, and memory-system internals (episodes, claims) are not imported")
        }
        return applied to skipped
    }

    private suspend fun applyIosBackupImportPlan(plan: IosBackupImportPlan) {
        plan.providerApiKeys.forEach { (providerId, key) ->
            secureStore.writeString(apiKeyName(providerId), key)
        }
        plan.ttsApiKeys.forEach { (type, key) ->
            if (key.isNotBlank()) secureStore.writeString(ttsApiKeyName(type), key)
        }
        plan.searchApiKeys.forEach { (type, key) ->
            if (key.isNotBlank()) secureStore.writeString(searchApiKeyName(type), key)
        }
        val providerList = plan.providers.ifEmpty { mutableState.value.providers }
        val assistantList = plan.assistants.ifEmpty { mutableState.value.assistants }
        mutableState.update { current ->
            current.copy(
                appearance = plan.appearance ?: current.appearance,
                assistants = assistantList,
                selectedAssistantId = plan.selectedAssistantId
                    ?.takeIf { id -> assistantList.any { it.id == id } }
                    ?: current.selectedAssistantId?.takeIf { id -> assistantList.any { it.id == id } }
                    ?: assistantList.firstOrNull()?.id,
                providers = providerList,
                selectedChatModelId = plan.selectedChatModelId
                    ?.takeIf { ref -> findProviderModel(providerList, ref) != null }
                    ?: current.selectedChatModelId?.takeIf { ref -> findProviderModel(providerList, ref) != null },
                search = plan.search ?: current.search,
                tts = plan.tts ?: current.tts,
            )
        }
        persist()
        val snapshot = mutableState.value
        val selectedModel = snapshot.selectedChatModel
        val apiKey = selectedModel
            ?.let { (provider, _) -> secureStore.readString(apiKeyName(provider.id.toString())).orEmpty() }
            .orEmpty()
        val searchKeyPresent = hasSearchApiKey(snapshot.search)
        val ttsKey = secureStore.readString(ttsApiKeyName(snapshot.tts.type)).orEmpty()
        mutableState.update {
            it.copy(
                hasApiKey = selectedModel != null && apiKey.isNotBlank(),
                hasSearchApiKey = searchKeyPresent,
                hasTtsApiKey = ttsKey.isNotBlank(),
            )
        }
        ttsController.setProvider(
            snapshot.tts.takeIf { it.enabled && ttsKey.isNotBlank() }?.toProviderSetting(ttsKey)
        )
        mutableState.value.conversations
            .filter { conversation ->
                assistantList.firstOrNull { it.id == conversation.assistantId }?.memoryMode == IosMemoryMode.ADAPTIVE
            }
            .forEach { scheduleAdaptiveMemory(it.id) }
        refreshAdaptiveBackgroundSchedule()
    }

    private suspend fun hasSearchApiKey(preferences: IosSearchPreferences): Boolean {
        if (!preferences.provider.requiresApiKey()) return true
        return secureStore.readString(searchApiKeyName(preferences.provider)).isNullOrBlank().not()
    }

    private fun persistAsync() {
        scope.launch { persist() }
    }

    private suspend fun persist() = persistMutex.withLock {
        val snapshot = mutableState.value
        val stored = IosStoredState(
            conversations = snapshot.conversations,
            selectedConversationId = snapshot.selectedConversationId,
            providers = snapshot.providers,
            selectedChatModelId = snapshot.selectedChatModelId,
            appearance = snapshot.appearance,
            assistants = snapshot.assistants,
            selectedAssistantId = snapshot.selectedAssistantId,
            search = snapshot.search,
            memories = snapshot.memories,
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
        const val DATA_RESTORE_MAX_ENTRY_BYTES = 256 * 1024 * 1024
        val IOS_BACKUP_DATABASE_ENTRIES = listOf("rikka_hub.db", "rikka_hub")
        const val ADAPTIVE_MEMORY_MESSAGE_THRESHOLD = 8
        const val ADAPTIVE_MEMORY_INACTIVITY_MS = 10 * 60 * 1_000L
        const val MAX_PENDING_MEMORY_MESSAGES = 20
        const val MAX_MEMORY_OPERATIONS_PER_PASS = 12
        const val MAX_MEMORY_STATEMENT_LENGTH = 600
        fun legacyApiKeyName(type: IosProviderType): String = "provider_apikey_ios_${type.name.lowercase()}"
        fun apiKeyName(providerId: String): String = "provider_apikey_ios_$providerId"
        fun searchApiKeyName(type: IosSearchProviderType): String =
            "search_apikey_ios_${type.name.lowercase()}"
        fun ttsApiKeyName(type: IosTtsProviderType): String =
            "tts_provider_apikey_ios_${type.name.lowercase()}"
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

/** Branches the node containing [messageId] with a new version carrying the edited parts,
 *  mirroring Android ChatService.editMessage. */
internal fun IosConversation.withEditedMessage(
    messageId: String,
    parts: List<UIMessagePart>,
): IosConversation = copy(
    messages = emptyList(),
    messageNodes = messageNodes.map { node ->
        val original = node.messages.find { it.id.toString() == messageId } ?: return@map node
        node.copy(
            messages = node.messages + UIMessage(
                role = original.role,
                parts = parts,
                versionTag = original.versionTag,
            ),
            selectIndex = node.messages.size,
        )
    },
)

/** Mirrors Android ChatService.deleteMessage: user messages truncate, assistant/tool
 *  messages remove themselves plus the adjacent tool-call/result chain. */
internal fun IosConversation.withDeletedMessage(messageId: String): IosConversation {
    val message = messageNodes
        .flatMap { it.messages }
        .firstOrNull { it.id.toString() == messageId }
        ?: return this
    val nodes = messageNodes

    if (message.role == MessageRole.USER) {
        val nodeIndex = nodes.indexOfFirst { it.messages.any { it.id.toString() == messageId } }
        if (nodeIndex == -1) return this
        val node = nodes[nodeIndex]
        return if (node.messages.size > 1) {
            val remaining = node.messages.filter { it.id.toString() != messageId }
            val updatedNode = node.copy(
                messages = remaining,
                selectIndex = if (node.selectIndex >= remaining.size) remaining.lastIndex else node.selectIndex,
            )
            copy(
                messages = emptyList(),
                messageNodes = nodes.subList(0, nodeIndex) + listOf(updatedNode),
            )
        } else {
            copy(messages = emptyList(), messageNodes = nodes.subList(0, nodeIndex))
        }
    }

    val currentMessages = currentMessages
    val viewIndex = currentMessages.indexOfFirst { it.id.toString() == messageId }
    val related = if (viewIndex == -1) {
        emptyList()
    } else {
        buildList {
            for (i in viewIndex - 1 downTo 0) {
                val candidate = currentMessages[i]
                if (candidate.getToolCalls().isNotEmpty() || candidate.getToolResults().isNotEmpty()) {
                    add(candidate)
                } else break
            }
            for (i in viewIndex + 1 until currentMessages.size) {
                val candidate = currentMessages[i]
                if (candidate.getToolCalls().isNotEmpty() || candidate.getToolResults().isNotEmpty()) {
                    add(candidate)
                } else break
            }
        }
    }
    var result = withDeletedNodeMessage(message)
    related.forEach { relatedMessage ->
        val target = result.messageNodes
            .flatMap { it.messages }
            .firstOrNull { it.id == relatedMessage.id }
            ?: return@forEach
        result = result.withDeletedNodeMessage(target)
    }
    return result
}

/** Mirrors Android ChatService.deleteMessageInternal. */
private fun IosConversation.withDeletedNodeMessage(message: UIMessage): IosConversation {
    val nodeIndex = messageNodes
        .indexOfFirst { it.messages.any { it.id == message.id } }
    if (nodeIndex == -1) return this
    val nodes = messageNodes
    val node = nodes[nodeIndex]
    val deleteVersionTag = message.versionTag
    val turnStartIndex = nodes.subList(0, nodeIndex + 1)
        .indexOfLast { it.role == MessageRole.USER } + 1
    val turnEndIndex = nodes.subList(nodeIndex, nodes.size)
        .indexOfFirst { it.role == MessageRole.USER }
        .let { if (it == -1) nodes.size else nodeIndex + it }

    val updatedNodes = if (node.messages.size == 1 && deleteVersionTag == null) {
        nodes.filterIndexed { index, _ -> index != nodeIndex }
    } else {
        nodes.mapIndexedNotNull { index, messageNode ->
            val canDeleteByVersionTag = deleteVersionTag != null &&
                index in turnStartIndex until turnEndIndex &&
                messageNode.role != MessageRole.USER
            val remaining = messageNode.messages.filter { currentMessage ->
                if (canDeleteByVersionTag && currentMessage.versionTag == deleteVersionTag) {
                    false
                } else {
                    currentMessage.id != message.id
                }
            }
            if (remaining.isEmpty()) {
                null
            } else {
                messageNode.copy(
                    messages = remaining,
                    selectIndex = if (messageNode.selectIndex >= remaining.size) {
                        remaining.lastIndex
                    } else {
                        messageNode.selectIndex
                    },
                )
            }
        }
    }
    return copy(messages = emptyList(), messageNodes = updatedNodes)
}

/** Mirrors Android buildForkConversationSnapshot: copies nodes up to and including the
 *  target message's node into a new conversation. Attachment URLs are shared, which is
 *  safe because iOS never garbage-collects attachment files. */
internal fun buildIosForkConversation(
    conversation: IosConversation,
    messageId: String,
): IosConversation? {
    val targetIndex = conversation.messageNodes
        .indexOfFirst { it.messages.any { it.id.toString() == messageId } }
    if (targetIndex == -1) return null
    return IosConversation(
        assistantId = conversation.assistantId,
        title = conversation.title,
        messageNodes = conversation.messageNodes.subList(0, targetIndex + 1).map { node ->
            node.copy(id = Uuid.random())
        },
    )
}

@Serializable
private data class IosStoredState(
    val conversations: List<IosConversation> = emptyList(),
    val selectedConversationId: String? = null,
    val providers: List<ProviderSetting> = emptyList(),
    val selectedChatModelId: String? = null,
    // Legacy per-type provider state; migrated into [providers] on load
    val provider: IosProviderPreferences? = null,
    val providerConfigurations: List<IosProviderPreferences> = emptyList(),
    val appearance: IosAppearancePreferences = IosAppearancePreferences(),
    val assistants: List<IosAssistantPreferences> = emptyList(),
    val selectedAssistantId: String? = null,
    val search: IosSearchPreferences = IosSearchPreferences(),
    val memories: List<IosMemoryRecord> = emptyList(),
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
