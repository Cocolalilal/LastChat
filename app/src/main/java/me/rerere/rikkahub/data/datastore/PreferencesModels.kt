package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.asr.ASRProviderSetting
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.ChatStorageSettings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Mode
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.TextSelectionAction
import me.rerere.rikkahub.data.model.TextSelectionConfig
import me.rerere.rikkahub.data.sync.BackupCleanupResult
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.ui.theme.normalizePresetThemeId
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TTSVoice
import me.rerere.tts.provider.findTtsVoice
import me.rerere.tts.provider.withDefaultVoices
import me.rerere.tts.provider.withVoiceApplied
import kotlin.uuid.Uuid

val DISABLED_MODEL_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000000")
val DEFAULT_STT_PROVIDERS: List<ASRProviderSetting> = emptyList()

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val setupCompleted: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val developerMode: Boolean = false,
    val enableRagLogging: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val enableWebSearch: Boolean = false,
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val titleModelId: Uuid = Uuid.random(),
    val titleThinkingBudget: Int = 0,
    val summarizerModelId: Uuid? = null,
    val summarizerThinkingBudget: Int = 0,
    val subagentModelId: Uuid? = null,
    val subagentThinkingBudget: Int = 0,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val suggestionModelId: Uuid = Uuid.random(),
    val suggestionThinkingBudget: Int = 0,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val learningModePrompt: String = DEFAULT_LEARNING_MODE_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrThinkingBudget: Int = 0,
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val embeddingModelId: Uuid = Uuid.random(),
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val providerTags: List<Tag> = emptyList(),
    val recentlyUsedAssistants: List<Uuid> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val selectedTTSVoiceId: Uuid = DEFAULT_SYSTEM_TTS_VOICE_ID,
    val ttsAutoplayMode: TtsAutoplayMode = TtsAutoplayMode.OFF,
    val sttProviders: List<ASRProviderSetting> = DEFAULT_STT_PROVIDERS,
    val selectedSttProviderId: Uuid? = null,
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerBackgroundSetupShown: Boolean = false,
    val consolidationWorkerIntervalMinutes: Int = 15,
    val consolidationRequiresDeviceIdle: Boolean = false,
    val modes: List<Mode> = emptyList(),
    val lorebooks: List<Lorebook> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val chatStorage: ChatStorageSettings = ChatStorageSettings(),
    val dismissedBanners: Set<String> = emptySet(),
    val textSelectionConfig: TextSelectionConfig = TextSelectionConfig(),
) {
    companion object {
        fun dummy() = Settings(init = true)
    }
}

data class ConversationContext(
    val assistantId: Uuid,
    val assistant: Assistant,
    val chatModel: Model?,
    val searchMode: AssistantSearchMode,
    val displaySetting: DisplaySetting,
)

@Serializable
data class RpStyleRule(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val pattern: String = "*",
    val colorHex: String = "#808080",
    val enabled: Boolean = true
)

@Serializable
data class TtsTextFilterRule(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val pattern: String = "*",
    val mode: TtsFilterMode = TtsFilterMode.SKIP,
    val enabled: Boolean = true
)

@Serializable
enum class TtsFilterMode {
    SKIP,
    ONLY_READ
}

@Serializable
enum class TtsAutoplayMode {
    OFF,
    AFTER_GENERATION,
    WHILE_GENERATING,
}

fun TtsAutoplayMode.asEnabledMode(): TtsAutoplayMode {
    return when (this) {
        TtsAutoplayMode.OFF -> TtsAutoplayMode.OFF
        TtsAutoplayMode.AFTER_GENERATION,
        TtsAutoplayMode.WHILE_GENERATING -> TtsAutoplayMode.WHILE_GENERATING
    }
}

@Serializable
enum class FontSource {
    System,
    SystemCode,
    Custom
}

@Serializable
data class FontAxis(
    val tag: String,
    val name: String,
    val minValue: Float,
    val maxValue: Float,
    val defaultValue: Float,
    val currentValue: Float = defaultValue
)

@Serializable
data class FontFeature(
    val tag: String,
    val name: String,
    val enabled: Boolean = true
)

@Serializable
data class FontConfig(
    val fontSource: FontSource = FontSource.System,
    val customFontPath: String? = null,
    val customFontName: String? = null,
    val weight: Float = 400f,
    val width: Float = 100f,
    val roundness: Float = 100f,
    val grade: Float = 0f,
    val slant: Float = 0f,
    val fontSize: Float = 1.0f,
    val lineHeight: Float = 1.0f,
    val letterSpacing: Float = 0f,
    val customAxes: List<FontAxis> = emptyList(),
    val features: List<FontFeature> = emptyList()
) {
    companion object {
        val DEFAULT_EXPRESSIVE = FontConfig(
            fontSource = FontSource.System,
            roundness = 100f
        )
        val DEFAULT_NORMAL = FontConfig(
            fontSource = FontSource.System,
            roundness = 0f
        )
        val DEFAULT_CODE = FontConfig(
            fontSource = FontSource.SystemCode,
            roundness = 0f,
            weight = 400f
        )
    }
}

@Serializable
data class FontSettings(
    val useSameFontForHeadersAndContent: Boolean = true,
    val usePhoneSystemFont: Boolean = false,
    val headerFont: FontConfig = FontConfig.DEFAULT_EXPRESSIVE,
    val contentFont: FontConfig = FontConfig.DEFAULT_EXPRESSIVE,
    val codeFont: FontConfig = FontConfig.DEFAULT_CODE
)

internal fun FontSettings.normalize(): FontSettings {
    return copy(
        useSameFontForHeadersAndContent = true,
        contentFont = headerFont
    )
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val showUserAvatar: Boolean = true,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showAssistantBubbles: Boolean = true,
    val showTokenUsage: Boolean = false,
    val autoCloseThinking: Boolean = true,
    val reasoningPreviewEnabled: Boolean = false,
    val showUpdates: Boolean = false,
    val checkForUpdates: Boolean = true,
    val showMessageJumper: Boolean = false,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val fontSettings: FontSettings = FontSettings(),
    val enableMessageGenerationHapticEffect: Boolean = false,
    val enableUIHaptics: Boolean = true,
    val enableBlurEffect: Boolean = false,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = true,
    val rpStyleRules: List<RpStyleRule> = emptyList(),
        val ttsTextFilterRules: List<TtsTextFilterRule> = emptyList(),
    val providerViewMode: ProviderViewMode = ProviderViewMode.LIST,
    val showContextStacks: Boolean = false,
    val newChatHeaderStyle: NewChatHeaderStyle = NewChatHeaderStyle.GREETING,
    val newChatContentStyle: NewChatContentStyle = NewChatContentStyle.ACTIONS,
    val newChatShowAvatar: Boolean = true,
    val chatToolbarAtBottom: Boolean = false,
    val sttReplaceModelIcon: Boolean = false,
)

internal fun DisplaySetting.normalizeFontSettings(): DisplaySetting {
    return copy(fontSettings = fontSettings.normalize())
}

@Serializable
enum class NewChatHeaderStyle {
    NONE,
    GREETING,
    BIG_ICON
}

@Serializable
enum class NewChatContentStyle {
    NONE,
    TEMPLATES,
    ACTIONS
}

internal fun Settings.normalizeWebServerSettings(): Settings {
    return copy(
        webServerPort = webServerPort.coerceIn(1024, 65535),
        webServerJwtEnabled = webServerJwtEnabled && webServerAccessPassword.isNotBlank(),
    )
}

internal fun Settings.normalizeFontSettings(): Settings {
    return copy(displaySetting = displaySetting.normalizeFontSettings())
}

internal fun Settings.normalizeThemeId(): Settings {
    val normalizedThemeId = normalizePresetThemeId(themeId)
    return if (normalizedThemeId == themeId) {
        this
    } else {
        copy(themeId = normalizedThemeId)
    }
}

@Serializable
enum class ProviderViewMode {
    LIST,
    GRID
}

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "lastchat_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

internal fun Settings.migrateAssistantSummarizerToGlobal(): Settings {
    val selectedAssistantSummarizer = assistants.find { it.id == assistantId }?.summarizerModelId
    val legacySummarizerModelId = summarizerModelId
        ?: selectedAssistantSummarizer
        ?: assistants.firstNotNullOfOrNull { it.summarizerModelId }
    val clearedAssistants = assistants.map { assistant ->
        if (assistant.summarizerModelId != null) {
            assistant.copy(summarizerModelId = null)
        } else {
            assistant
        }
    }

    return if (legacySummarizerModelId != summarizerModelId || clearedAssistants != assistants) {
        copy(
            summarizerModelId = legacySummarizerModelId,
            assistants = clearedAssistants
        )
    } else {
        this
    }
}

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid): Model? {
    return providers.findModelById(uuid)
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.getCurrentChatModel(): Model? {
    return getChatModelForAssistant(getCurrentAssistant())
}

fun Settings.getChatModelForAssistant(assistant: Assistant): Model? {
    return findModelById(assistant.chatModelId ?: chatModelId)
}

fun Settings.resolveTextSelectionAssistant(): Assistant {
    return textSelectionConfig.assistantId?.let { getAssistantById(it) }
        ?: getCurrentAssistant()
}

fun Settings.findTextSelectionAction(actionId: String): TextSelectionAction? {
    return textSelectionConfig.actions.find { it.id == actionId }
}

fun Settings.getTextSelectionActionModel(
    actionId: String,
    assistant: Assistant = resolveTextSelectionAssistant(),
): Model? {
    findTextSelectionAction(actionId)?.modelId?.let { configuredModelId ->
        findModelById(configuredModelId)?.let { return it }
    }

    return getChatModelForAssistant(assistant)
}

fun Settings.resolveConversationContext(assistantId: Uuid): ConversationContext {
    val assistant = getAssistantById(assistantId) ?: getCurrentAssistant()
    return ConversationContext(
        assistantId = assistant.id,
        assistant = assistant,
        chatModel = findModelById(assistant.chatModelId ?: chatModelId),
        searchMode = assistant.searchMode,
        displaySetting = getEffectiveDisplaySetting(assistant),
    )
}

fun Settings.resolveConversationContext(conversation: Conversation): ConversationContext {
    return resolveConversationContext(conversation.assistantId)
}

fun Settings.getCurrentAssistant(): Assistant {
    return assistants.find { it.id == assistantId } ?: assistants.firstOrNull() ?: DEFAULT_ASSISTANTS.first()
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return assistants.find { it.id == id }
}

fun Settings.getEffectiveDisplaySetting(assistant: Assistant? = null): DisplaySetting {
    val ui = (assistant ?: getCurrentAssistant()).uiSettings
    return displaySetting.copy(
        showUserAvatar = ui.showUserAvatar ?: displaySetting.showUserAvatar,
        showModelIcon = ui.showAssistantAvatar ?: displaySetting.showModelIcon,
        showAssistantBubbles = ui.showAssistantBubbles ?: displaySetting.showAssistantBubbles,
        showTokenUsage = ui.showTokenUsage ?: displaySetting.showTokenUsage,
        autoCloseThinking = ui.autoCloseThinking ?: displaySetting.autoCloseThinking,
        showMessageJumper = ui.showMessageJumper ?: displaySetting.showMessageJumper,
        messageJumperOnLeft = ui.messageJumperOnLeft ?: displaySetting.messageJumperOnLeft,
        fontSizeRatio = ui.fontSizeRatio ?: displaySetting.fontSizeRatio,
        codeBlockAutoWrap = ui.codeBlockAutoWrap ?: displaySetting.codeBlockAutoWrap,
        codeBlockAutoCollapse = ui.codeBlockAutoCollapse ?: displaySetting.codeBlockAutoCollapse,
        showContextStacks = ui.showContextStacks ?: displaySetting.showContextStacks,
        newChatHeaderStyle = ui.newChatHeaderStyle ?: displaySetting.newChatHeaderStyle,
        newChatContentStyle = ui.newChatContentStyle ?: displaySetting.newChatContentStyle,
        newChatShowAvatar = ui.newChatShowAvatar ?: displaySetting.newChatShowAvatar,
    )
}

fun Settings.getSelectedTTSProvider(): TTSProviderSetting? {
    return selectedTTSProviderId.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Settings.getSelectedTTSVoice(): Pair<TTSProviderSetting, TTSVoice>? {
    val selectedProvider = getSelectedTTSProvider()
    return ttsProviders.findTtsVoice(selectedTTSVoiceId)
        ?: selectedProvider?.voices?.firstOrNull()?.let { voice -> selectedProvider to voice }
        ?: ttsProviders.firstOrNull()?.voices?.firstOrNull()?.let { voice -> ttsProviders.first() to voice }
}

fun Settings.getEffectiveTTSVoice(assistant: Assistant? = null): Pair<TTSProviderSetting, TTSVoice>? {
    val assistantVoice = assistant?.ttsVoiceId?.let { ttsProviders.findTtsVoice(it) }
    return assistantVoice ?: getSelectedTTSVoice()
}

fun Settings.getEffectiveTTSProvider(assistant: Assistant? = null): TTSProviderSetting? {
    val (provider, voice) = getEffectiveTTSVoice(assistant) ?: return getSelectedTTSProvider()
    return provider.withVoiceApplied(voice)
}

fun Settings.getEffectiveTtsAutoplayMode(assistant: Assistant? = null): TtsAutoplayMode {
    return (assistant?.ttsAutoplayMode ?: ttsAutoplayMode).asEnabledMode()
}

fun Settings.getSelectedSTTProvider(): ASRProviderSetting? {
    return selectedSttProviderId?.let { id ->
        sttProviders.find { it.id == id }
    }
}

fun Settings.normalizeSttSettings(): Settings {
    // Filter out legacy SystemSTT entries (local STT removed); keep only remote providers
    val withoutLegacy = sttProviders.filterNot { it is ASRProviderSetting.SystemSTT }
    val normalizedProviders = withoutLegacy.distinctBy { it.id }
    val normalizedSelectedId = selectedSttProviderId?.takeIf { id ->
        normalizedProviders.any { provider -> provider.id == id }
    }
    return copy(
        sttProviders = normalizedProviders,
        selectedSttProviderId = normalizedSelectedId,
    )
}

fun Settings.normalizeTtsSettings(): Settings {
    val normalizedProviders = ttsProviders.distinctBy { it.id }.map { provider ->
        if (provider is TTSProviderSetting.SystemTTS) {
            val defaultVoiceId = if (provider.id == DEFAULT_SYSTEM_TTS_ID) DEFAULT_SYSTEM_TTS_VOICE_ID else null
            provider.copyProvider(
                voices = provider.withDefaultVoices(defaultVoiceId).voices.distinctBy { voice ->
                    if (voice.providerVoiceId.isBlank()) {
                        voice.id.toString()
                    } else {
                        "${voice.enginePackageName.orEmpty()}:${voice.providerVoiceId}"
                    }
                }
            )
        } else {
            provider.withDefaultVoices().let { normalized ->
                normalized.copyProvider(voices = normalized.voices.distinctBy { voice -> voice.id })
            }
        }
    }
    val allVoiceIds = normalizedProviders.flatMap { it.voices }.map { it.id }.toSet()
    val allProviderIds = normalizedProviders.map { it.id }.toSet()
    val fallbackVoiceId = normalizedProviders.find { it.id == selectedTTSProviderId }
        ?.voices
        ?.firstOrNull()
        ?.id
        ?: normalizedProviders.firstOrNull()?.voices?.firstOrNull()?.id
        ?: DEFAULT_SYSTEM_TTS_VOICE_ID
    val normalizedVoiceId = selectedTTSVoiceId.takeIf { it in allVoiceIds } ?: fallbackVoiceId
    val normalizedProviderId = selectedTTSProviderId.takeIf { it in allProviderIds }
        ?: normalizedProviders.firstOrNull { provider -> provider.voices.any { voice -> voice.id == normalizedVoiceId } }?.id
        ?: normalizedProviders.firstOrNull()?.id
        ?: DEFAULT_SYSTEM_TTS_ID
    return copy(
        ttsProviders = normalizedProviders,
        selectedTTSProviderId = normalizedProviderId,
        selectedTTSVoiceId = normalizedVoiceId,
        ttsAutoplayMode = ttsAutoplayMode.asEnabledMode(),
        assistants = assistants.map { assistant ->
            assistant.copy(
                ttsVoiceId = assistant.ttsVoiceId?.takeIf { it in allVoiceIds },
                ttsAutoplayMode = assistant.ttsAutoplayMode?.asEnabledMode(),
            )
        }
    )
}

internal fun Settings.ensureBuiltInProviders(): Settings {
    val defaultById = DEFAULT_PROVIDERS.associateBy { it.id }
    val normalizedProviders = providers.map { provider ->
        defaultById[provider.id]?.let { defaultProvider ->
            provider.copyProvider(
                id = defaultProvider.id,
                builtIn = defaultProvider.builtIn,
            )
        } ?: provider
    }

    val missingDefaults = DEFAULT_PROVIDERS
        .filterNot { default -> normalizedProviders.any { it.id == default.id } }

    val updatedProviders = buildList {
        addAll(normalizedProviders)
        addAll(missingDefaults)
    }
    return if (updatedProviders != providers) {
        copy(providers = updatedProviders)
    } else {
        this
    }
}

internal fun Settings.clearMissingModelReferences(): Settings {
    val allModels = providers.flatMap { it.models }
    val allModelIds = allModels.map { it.id }.toSet()
    val chatFallback = allModels.firstOrNull { it.type == ModelType.CHAT }?.id ?: Uuid.random()
    val imageFallback = allModels.firstOrNull { it.type == ModelType.IMAGE }?.id ?: Uuid.random()
    val embeddingFallback = allModels.firstOrNull { it.type == ModelType.EMBEDDING }?.id ?: Uuid.random()
    val multimodalFallback = allModels.firstOrNull {
        it.type == ModelType.CHAT && it.inputModalities.contains(me.rerere.ai.provider.Modality.IMAGE)
    }?.id ?: chatFallback

    fun Uuid.ensureValid(fallback: Uuid): Uuid {
        return if (this in allModelIds) this else fallback
    }

    fun Uuid.ensureValidOrDisabled(fallback: Uuid): Uuid {
        return if (this == DISABLED_MODEL_ID) this else ensureValid(fallback)
    }

    fun Uuid?.ensureValidOrNull(): Uuid? {
        return this?.takeIf { it in allModelIds }
    }

    val updatedAssistants = assistants.map { assistant ->
        assistant.copy(
            chatModelId = assistant.chatModelId.ensureValidOrNull(),
            backgroundModelId = assistant.backgroundModelId.ensureValidOrNull(),
            embeddingModelId = assistant.embeddingModelId.ensureValidOrNull(),
            summarizerModelId = assistant.summarizerModelId.ensureValidOrNull(),
        )
    }

    return copy(
        chatModelId = chatModelId.ensureValid(chatFallback),
        titleModelId = titleModelId.ensureValid(chatFallback),
        summarizerModelId = summarizerModelId.ensureValidOrNull(),
        subagentModelId = subagentModelId.ensureValidOrNull(),
        imageGenerationModelId = imageGenerationModelId.ensureValid(imageFallback),
        translateModeId = translateModeId.ensureValid(chatFallback),
        suggestionModelId = suggestionModelId.ensureValidOrDisabled(chatFallback),
        ocrModelId = ocrModelId.ensureValid(multimodalFallback),
        embeddingModelId = embeddingModelId.ensureValidOrDisabled(embeddingFallback),
        favoriteModels = favoriteModels.filter { it in allModelIds },
        assistants = updatedAssistants,
        textSelectionConfig = textSelectionConfig.copy(
            assistantId = textSelectionConfig.assistantId?.takeIf { id -> updatedAssistants.any { it.id == id } },
            actions = textSelectionConfig.actions.map { action ->
                action.copy(modelId = action.modelId.ensureValidOrNull())
            }
        ),
    )
}

fun Model.findProvider(
    providers: List<ProviderSetting>,
    checkOverwrite: Boolean = true
): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(proxy = provider.proxy, models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == id) {
                return setting
            }
        }
    }
    return null
}

internal val GEMINI_2_5_FLASH_ID = Uuid.parse("cd2cba9a-3f92-4148-b4c6-4d7a86f7b9c2")
internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "Generical",
        avatar = Avatar.Resource(R.drawable.default_generical_pfp),
        temperature = 0.6f,
        uiSettings = me.rerere.rikkahub.data.model.AssistantUISettings(newChatShowAvatar = false),
        enableTimeAwareness = true,
        systemPrompt = """
            You are the best generic assistant, called {{char}}. {{char}} is a really nice guy. He doesn't use emojis though. Use the search tool when looking for factual info. You can have opinions if the user asks you for one. 

            **Context:
            - You are currently chatting to {{user}}
            - You are running on {{model_name}}
            - Date: {{cur_date}}

            **Additional info:
            - The UI supports LaTeX rendering
            - The user is chatting to you trough an app called LastChat
            - You are an AI/LLM and shouldn't hide this fact
        """.trimIndent()
    )
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
val DEFAULT_SYSTEM_TTS_VOICE_ID = Uuid.parse("65e59f2d-31a4-4db4-99dd-ef2f8a9a8a38")
internal val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
        voices = listOf(
            TTSVoice(
                id = DEFAULT_SYSTEM_TTS_VOICE_ID,
                name = "System TTS",
            )
        ),
    ),
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

fun Settings.sanitize(): Pair<Settings, BackupCleanupResult> {
    var invalidSearchModeCount = 0
    var orphanedTagReferences = 0
    var orphanedModelReferences = 0

    val sanitizedAssistants = assistants.map { assistant ->
        when (val mode = assistant.searchMode) {
            is AssistantSearchMode.Provider -> {
                if (mode.index < 0 || mode.index >= searchServices.size) {
                    invalidSearchModeCount++
                    assistant.copy(searchMode = AssistantSearchMode.Off)
                } else {
                    assistant
                }
            }

            else -> assistant
        }
    }

    val validTagIds = assistantTags.map { it.id }.toSet()
    val cleanedAssistants = sanitizedAssistants.map { assistant ->
        val validTags = assistant.tags.filter { it in validTagIds }
        if (validTags.size != assistant.tags.size) {
            orphanedTagReferences += assistant.tags.size - validTags.size
            assistant.copy(tags = validTags)
        } else {
            assistant
        }
    }

    val allModelIds = providers.flatMap { it.models.map { model -> model.id } }.toSet()
    val cleanedFavorites = favoriteModels.filter { it in allModelIds }
    orphanedModelReferences += favoriteModels.size - cleanedFavorites.size

    val cleanedTextSelectionActions = textSelectionConfig.actions.map { action ->
        val modelId = action.modelId
        if (modelId != null && modelId !in allModelIds) {
            orphanedModelReferences++
            action.copy(modelId = null)
        } else {
            action
        }
    }

    val clampedSearchSelected = if (searchServices.isNotEmpty()) {
        searchServiceSelected.coerceIn(0, searchServices.size - 1)
    } else {
        0
    }

    val cleanedSettings = copy(
        assistants = cleanedAssistants,
        favoriteModels = cleanedFavorites,
        searchServiceSelected = clampedSearchSelected,
        textSelectionConfig = textSelectionConfig.copy(actions = cleanedTextSelectionActions),
    ).migrateLegacyModesToSkills()

    val result = BackupCleanupResult(
        invalidSearchModeCount = invalidSearchModeCount,
        orphanedTagReferences = orphanedTagReferences,
        orphanedModelReferences = orphanedModelReferences,
    )

    return cleanedSettings to result
}
