package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.IOException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Mode
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.TextSelectionConfig
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"

private val Context.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            PreferenceStoreV1Migration()
        )
    }
)

class SettingsStore(
    context: Context,
    scope: AppScope,
    private val quickCache: QuickSettingsCache,
    private val secretKeyManager: SecretKeyManager,
) : KoinComponent {
    companion object {
        // 版本号
        val VERSION = intPreferencesKey("data_version")

        // UI设置
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_ID = stringPreferencesKey("theme_id")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val ENABLE_RAG_LOGGING = booleanPreferencesKey("enable_rag_logging")

        // 模型选择
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val TITLE_MODEL = stringPreferencesKey("title_model")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val LEARNING_MODE_PROMPT = stringPreferencesKey("learning_mode_prompt")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_PROMPT = stringPreferencesKey("ocr_prompt")
        val EMBEDDING_MODEL = stringPreferencesKey("embedding_model")

        // 提供商
        val PROVIDERS = stringPreferencesKey("providers")

        // 助手
        val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
        val ASSISTANTS = stringPreferencesKey("assistants")
        val ASSISTANT_TAGS = stringPreferencesKey("assistant_tags")
        val PROVIDER_TAGS = stringPreferencesKey("provider_tags")
        val RECENTLY_USED_ASSISTANTS = stringPreferencesKey("recently_used_assistants")

        // 搜索
        val SEARCH_SERVICES = stringPreferencesKey("search_services")
        val SEARCH_COMMON = stringPreferencesKey("search_common")
        val SEARCH_SELECTED = intPreferencesKey("search_selected")

        // MCP
        val MCP_SERVERS = stringPreferencesKey("mcp_servers")

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")

        // Background Worker
        val CONSOLIDATION_WORKER_INTERVAL = intPreferencesKey("consolidation_worker_interval")
        val CONSOLIDATION_REQUIRES_DEVICE_IDLE = booleanPreferencesKey("consolidation_requires_device_idle")

        // Prompt Injections
        val MODES = stringPreferencesKey("modes")
        val LOREBOOKS = stringPreferencesKey("lorebooks")

        // Android Integration
        val TEXT_SELECTION_CONFIG = stringPreferencesKey("text_selection_config")
    }

    private val dataStore = context.settingsStore

    val settingsFlowRaw = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            Settings(
                enableWebSearch = preferences[ENABLE_WEB_SEARCH] == true,
                favoriteModels = preferences[FAVORITE_MODELS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                    ?: GEMINI_2_5_FLASH_ID,
                titleModelId = preferences[TITLE_MODEL]?.let { Uuid.parse(it) }
                    ?: GEMINI_2_5_FLASH_ID,
                translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
                    ?: GEMINI_2_5_FLASH_ID,
                suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) }
                    ?: GEMINI_2_5_FLASH_ID,
                imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
                learningModePrompt = preferences[LEARNING_MODE_PROMPT] ?: DEFAULT_LEARNING_MODE_PROMPT,
                ocrModelId = preferences[OCR_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                ocrPrompt = preferences[OCR_PROMPT] ?: DEFAULT_OCR_PROMPT,
                embeddingModelId = preferences[EMBEDDING_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
                    ?: DEFAULT_ASSISTANT_ID,
                assistantTags = preferences[ASSISTANT_TAGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                providerTags = preferences[PROVIDER_TAGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                providers = JsonInstant.decodeFromString(preferences[PROVIDERS] ?: "[]"),
                assistants = JsonInstant.decodeFromString(preferences[ASSISTANTS] ?: "[]"),
                recentlyUsedAssistants = preferences[RECENTLY_USED_ASSISTANTS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                dynamicColor = preferences[DYNAMIC_COLOR] != false,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                developerMode = preferences[DEVELOPER_MODE] == true,
                enableRagLogging = preferences[ENABLE_RAG_LOGGING] == true,
                displaySetting = JsonInstant.decodeFromString(preferences[DISPLAY_SETTING] ?: "{}"),
                searchServices = preferences[SEARCH_SERVICES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: listOf(SearchServiceOptions.DEFAULT),
                searchCommonOptions = preferences[SEARCH_COMMON]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: SearchCommonOptions(),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = preferences[MCP_SERVERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webDavConfig = preferences[WEBDAV_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: WebDavConfig(),
                ttsProviders = preferences[TTS_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_TTS_ID,
                consolidationWorkerIntervalMinutes = preferences[CONSOLIDATION_WORKER_INTERVAL] ?: 15,
                consolidationRequiresDeviceIdle = preferences[CONSOLIDATION_REQUIRES_DEVICE_IDLE] ?: false,
                modes = preferences[MODES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                lorebooks = preferences[LOREBOOKS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                textSelectionConfig = preferences[TEXT_SELECTION_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: TextSelectionConfig(),
            )
        }
        .map {
            var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
            // DEFAULT_PROVIDERS.forEach { defaultProvider ->
            //     if (providers.none { it.id == defaultProvider.id }) {
            //         providers.add(defaultProvider.copyProvider())
            //     }
            // }
            providers = providers.map { provider ->
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                if (defaultProvider != null) {
                    provider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                        description = defaultProvider.description,
                        shortDescription = defaultProvider.shortDescription,
                    )
                } else provider
            }.toMutableList()
            val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
            val ttsProviders = it.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
            DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
                if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
                    ttsProviders.add(defaultTTSProvider.copyProvider())
                }
            }
            it.copy(
                providers = providers,
                assistants = assistants,
                ttsProviders = ttsProviders
            )
        }
        .map { settings ->
            // 去重并清理无效引用
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            settings.copy(
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Google -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Claude -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )
                    }
                },
                assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
                    assistant.copy(
                        // 过滤掉不存在的 MCP 服务器 ID
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet()
                    )
                },
                ttsProviders = settings.ttsProviders.distinctBy { it.id },
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                }
            )
        }
        .onEach {
            get<PebbleEngine>().templateCache.invalidateAll()
        }
        .flowOn(Dispatchers.Default)

    // Track if we've done the one-time migration
    private var hasMigratedSecrets = false

    val settingsFlow = settingsFlowRaw
        .distinctUntilChanged()
        .map { settings ->
            // Perform one-time migration on first settings load
            if (!hasMigratedSecrets && !settings.init) {
                hasMigratedSecrets = true
                val migratedSettings = secretKeyManager.migrateSecretsFromSettings(settings)
                if (migratedSettings != settings) {
                    // Secrets were migrated - persist the cleared plaintext back to DataStore
                    // We do this synchronously to ensure consistency
                    scope.launch {
                        persistMigratedSettings(migratedSettings)
                    }
                }
                migratedSettings
            } else {
                settings
            }
        }
        // Hydrate secrets (populate API keys from SecureStore) so they are available in memory/UI
        .map { settings ->
            secretKeyManager.populateSecretsForExport(settings)
        }
        .onEach { settings -> quickCache.updateCache(settings) }
        .toMutableStateFlow(scope, quickCache.createCachedSettings())

    /**
     * Persist settings after migration (to clear plaintext secrets from DataStore).
     */
    private suspend fun persistMigratedSettings(settings: Settings) {
        dataStore.edit { preferences ->
            preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
        }
    }


    suspend fun update(settings: Settings) {
        if(settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return
        }
        
        // Auto-update recently used assistants when assistant changes
        val settingsToSave = if (settings.assistantId != settingsFlow.value.assistantId && 
            !settingsFlow.value.init &&
            settings.assistants.any { it.id == settings.assistantId }) {
            val updatedList = buildList {
                add(settings.assistantId)
                settings.recentlyUsedAssistants
                    .filter { it != settings.assistantId }
                    .take(2)
                    .forEach { add(it) }
            }
            settings.copy(recentlyUsedAssistants = updatedList)
        } else {
            settings
        }
        
        // Handle explicit secret deletions (user cleared a field that had a value)
        // This must be called BEFORE migration to remove deleted secrets from SecureStore
        secretKeyManager.handleExplicitSecretDeletions(settingsFlow.value, settingsToSave)
        
        // Migrate secrets from plaintext to SecureStore if needed
        val migratedSettings = secretKeyManager.migrateSecretsFromSettings(settingsToSave)
        
        settingsFlow.value = secretKeyManager.populateSecretsForExport(migratedSettings)
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = settingsToSave.dynamicColor
            preferences[THEME_ID] = settingsToSave.themeId
            preferences[DEVELOPER_MODE] = settingsToSave.developerMode
            preferences[ENABLE_RAG_LOGGING] = settingsToSave.enableRagLogging
            preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settingsToSave.displaySetting)

            preferences[ENABLE_WEB_SEARCH] = settingsToSave.enableWebSearch
            preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settingsToSave.favoriteModels)
            preferences[SELECT_MODEL] = settingsToSave.chatModelId.toString()
            preferences[TITLE_MODEL] = settingsToSave.titleModelId.toString()
            preferences[TRANSLATE_MODEL] = settingsToSave.translateModeId.toString()
            preferences[SUGGESTION_MODEL] = settingsToSave.suggestionModelId.toString()
            preferences[IMAGE_GENERATION_MODEL] = settingsToSave.imageGenerationModelId.toString()
            preferences[TITLE_PROMPT] = settingsToSave.titlePrompt
            preferences[TRANSLATION_PROMPT] = settingsToSave.translatePrompt
            preferences[SUGGESTION_PROMPT] = settingsToSave.suggestionPrompt
            preferences[LEARNING_MODE_PROMPT] = settingsToSave.learningModePrompt
            preferences[OCR_MODEL] = settingsToSave.ocrModelId.toString()
            preferences[OCR_PROMPT] = settingsToSave.ocrPrompt
            preferences[EMBEDDING_MODEL] = settingsToSave.embeddingModelId.toString()

            preferences[PROVIDERS] = JsonInstant.encodeToString(migratedSettings.providers)

            preferences[ASSISTANTS] = JsonInstant.encodeToString(settingsToSave.assistants)
            preferences[SELECT_ASSISTANT] = settingsToSave.assistantId.toString()
            preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settingsToSave.assistantTags)
            preferences[PROVIDER_TAGS] = JsonInstant.encodeToString(settingsToSave.providerTags)
            preferences[RECENTLY_USED_ASSISTANTS] = JsonInstant.encodeToString(settingsToSave.recentlyUsedAssistants)

            preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settingsToSave.searchServices)
            preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settingsToSave.searchCommonOptions)
            preferences[SEARCH_SELECTED] = settingsToSave.searchServiceSelected.coerceIn(0, settingsToSave.searchServices.size - 1)

            preferences[MCP_SERVERS] = JsonInstant.encodeToString(settingsToSave.mcpServers)
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(migratedSettings.webDavConfig)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(migratedSettings.ttsProviders)
            settingsToSave.selectedTTSProviderId?.let {
                preferences[SELECTED_TTS_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_TTS_PROVIDER)

            preferences[CONSOLIDATION_WORKER_INTERVAL] = settingsToSave.consolidationWorkerIntervalMinutes
            preferences[CONSOLIDATION_REQUIRES_DEVICE_IDLE] = settingsToSave.consolidationRequiresDeviceIdle

            preferences[MODES] = JsonInstant.encodeToString(settingsToSave.modes)
            preferences[LOREBOOKS] = JsonInstant.encodeToString(settingsToSave.lorebooks)
            preferences[TEXT_SELECTION_CONFIG] = JsonInstant.encodeToString(settingsToSave.textSelectionConfig)
        }
    }

    suspend fun update(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    suspend fun updateAssistant(assistantId: Uuid) {
        // Update in-memory state immediately to avoid race conditions
        val current = settingsFlow.value
        if (!current.init && current.assistants.any { it.id == assistantId }) {
            val updatedRecentlyUsed = buildList {
                add(assistantId)
                current.recentlyUsedAssistants
                    .filter { it != assistantId }
                    .take(2)
                    .forEach { add(it) }
            }
            settingsFlow.value = current.copy(
                assistantId = assistantId,
                recentlyUsedAssistants = updatedRecentlyUsed
            )
        }
        // Persist to DataStore
        dataStore.edit { preferences ->
            preferences[SELECT_ASSISTANT] = assistantId.toString()
        }
    }

    /**
     * Mark an assistant as recently used for app shortcuts.
     * Moves it to the front of the list and keeps only the 3 most recent.
     */
    suspend fun markAssistantUsed(assistantId: Uuid) {
        val current = settingsFlow.value
        // Only add if the assistant exists
        if (current.assistants.none { it.id == assistantId }) return
        
        val updatedList = buildList {
            add(assistantId)
            current.recentlyUsedAssistants
                .filter { it != assistantId }
                .take(2)
                .forEach { add(it) }
        }
        
        if (updatedList != current.recentlyUsedAssistants) {
            update(current.copy(recentlyUsedAssistants = updatedList))
        }
    }
}

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val developerMode: Boolean = false,
    val enableRagLogging: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val enableWebSearch: Boolean = false,
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val titleModelId: Uuid = Uuid.random(),
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val suggestionModelId: Uuid = Uuid.random(),
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val learningModePrompt: String = DEFAULT_LEARNING_MODE_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val embeddingModelId: Uuid = Uuid.random(),
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val providerTags: List<Tag> = emptyList(),
    val recentlyUsedAssistants: List<Uuid> = emptyList(), // For app shortcuts, max 3 items
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val consolidationWorkerIntervalMinutes: Int = 15,
    val consolidationRequiresDeviceIdle: Boolean = false,
    // Prompt Injections
    val modes: List<Mode> = emptyList(),
    val lorebooks: List<Lorebook> = emptyList(),
    // Android Integration
    val textSelectionConfig: TextSelectionConfig = TextSelectionConfig(),
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

/**
 * Custom text styling rule for roleplay formatting.
 * Pattern wrapping (e.g., "*", "%") will be matched and styled with the specified color.
 */
@Serializable
data class RpStyleRule(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val pattern: String = "*",      // The wrapping pattern, e.g., "*" for *text*, "%" for %text%
    val colorHex: String = "#808080", // Hex color code
    val enabled: Boolean = true
)

/**
 * TTS text filter rule for skipping or only reading text matching a pattern.
 * Pattern wrapping (e.g., "*", "%") will be matched and filtered accordingly.
 */
@Serializable
data class TtsTextFilterRule(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val pattern: String = "*",      // The wrapping pattern, e.g., "*" for *text*
    val mode: TtsFilterMode = TtsFilterMode.SKIP,
    val enabled: Boolean = true
)

@Serializable
enum class TtsFilterMode {
    SKIP,       // Skip text inside this pattern (don't read it)
    ONLY_READ   // Only read text inside this pattern (skip everything else)
}

/**
 * Source of font for text rendering
 */
@Serializable
enum class FontSource {
    System,       // Default Google Sans Flex (with roundness control)
    SystemCode,   // Google Sans Code (monospace for code blocks)
    Custom        // User-uploaded font file
}

/**
 * Variable font axis with its metadata
 */
@Serializable
data class FontAxis(
    val tag: String,         // e.g., "wght", "wdth", "ROND"
    val name: String,        // Human-readable name
    val minValue: Float,
    val maxValue: Float,
    val defaultValue: Float,
    val currentValue: Float = defaultValue
)

/**
 * OpenType feature toggle
 */
@Serializable
data class FontFeature(
    val tag: String,         // e.g., "liga", "kern", "smcp"
    val name: String,        // Human-readable name
    val enabled: Boolean = true
)

/**
 * Font configuration for a specific text element (headers, content, or code)
 */
@Serializable
data class FontConfig(
    val fontSource: FontSource = FontSource.System,
    val customFontPath: String? = null,  // Internal path to custom font file
    val customFontName: String? = null,  // Display name of custom font
    // Common variable font axes (applied when supported)
    val weight: Float = 400f,       // 100-900
    val width: Float = 100f,        // 75-125
    val roundness: Float = 100f,    // 0-100 (Google Sans Flex specific, default expressive)
    val grade: Float = 0f,          // -50 to 150
    val slant: Float = 0f,          // -10 to 0
    // Typography adjustments
    val fontSize: Float = 1.0f,     // Multiplier (0.5-2.0)
    val lineHeight: Float = 1.0f,   // Multiplier (0.8-2.0)
    val letterSpacing: Float = 0f,  // -0.05 to 0.1 em
    // Custom axes detected from font (for custom fonts)
    val customAxes: List<FontAxis> = emptyList(),
    // OpenType features (detected from font)
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

/**
 * Complete font customization settings for the app
 */
@Serializable
data class FontSettings(
    val useSameFontForHeadersAndContent: Boolean = false,
    val headerFont: FontConfig = FontConfig.DEFAULT_EXPRESSIVE,
    val contentFont: FontConfig = FontConfig.DEFAULT_EXPRESSIVE,
    val codeFont: FontConfig = FontConfig.DEFAULT_CODE
)

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val chatInputStyle: ChatInputStyle = ChatInputStyle.MINIMAL, // Input bar style (floating toolbar or minimal)
    val showUserAvatar: Boolean = true,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showAssistantBubbles: Boolean = true,
    val showTokenUsage: Boolean = false,
    val autoCloseThinking: Boolean = true,
    val showUpdates: Boolean = false,
    val checkForUpdates: Boolean = true, // Check GitHub for app updates
    val showMessageJumper: Boolean = false,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    @Deprecated("Use fontSettings instead")
    val useExpressiveFont: Boolean = true, // Kept for migration, use fontSettings
    val fontSettings: FontSettings = FontSettings(), // Comprehensive font customization
    val enableMessageGenerationHapticEffect: Boolean = false,
    val enableUIHaptics: Boolean = true,
    val skipCropImage: Boolean = false,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = true,
    val rpStyleRules: List<RpStyleRule> = emptyList(), // Custom RP text styling rules
    val ttsTextFilterRules: List<TtsTextFilterRule> = emptyList(), // TTS text filter rules
    val providerViewMode: ProviderViewMode = ProviderViewMode.LIST, // Provider page view mode
    val showContextStacks: Boolean = false, // Show context sources (modes, memories, lorebooks) in message toolbar
    // New chat customization
    val newChatHeaderStyle: NewChatHeaderStyle = NewChatHeaderStyle.GREETING, // Header for empty new chats
    val newChatContentStyle: NewChatContentStyle = NewChatContentStyle.ACTIONS, // Content for empty new chats
    val newChatShowAvatar: Boolean = true, // Show avatar in header (true) or top-right corner (false)
)

@Serializable
enum class NewChatHeaderStyle {
    NONE,       // Empty header
    GREETING,   // Small avatar left of greeting
    BIG_ICON    // Big avatar with name below (no greeting)
}

@Serializable
enum class NewChatContentStyle {
    NONE,       // Empty content
    TEMPLATES,  // Template cards (Write, Code, etc.)
    STATS,      // Stats widgets (streak, chats, avg msgs)
    ACTIONS     // ChatGPT-style pill buttons with navigation (Create image, Translate, Code, More)
}

@Serializable
enum class ProviderViewMode {
    LIST,
    GRID
}

@Serializable
enum class ChatInputStyle {
    FLOATING,   // "LastChat" - current floating toolbar
    MINIMAL     // "Minimal" - ChatGPT-style simple bar with bottom sheet picker
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

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid): Model? {
    return this.providers.findModelById(uuid)
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.getCurrentChatModel(): Model? {
    return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId } ?: this.assistants.first()
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

/**
 * Get effective display settings by merging assistant's UI overrides with global display settings.
 * Per-assistant settings take precedence when set (non-null).
 */
fun Settings.getEffectiveDisplaySetting(assistant: Assistant? = null): DisplaySetting {
    val ui = (assistant ?: getCurrentAssistant()).uiSettings
    return displaySetting.copy(
        chatInputStyle = ui.chatInputStyle ?: displaySetting.chatInputStyle,
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
    return selectedTTSProviderId?.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(proxy = provider.proxy, models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
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
        avatar = Avatar.Resource(me.rerere.rikkahub.R.drawable.default_generical_pfp),
        temperature = 0.6f,
        systemPrompt = """
            You are the best generic assistant, called {{char}}. {{char}} is a really nice guy. He doesn't use emojis though. Use the search tool when looking for factual info. You can have opinions if the user asks you for one. 

            **Context:
            - You are currently chatting to {{user}}
            - You are running on {{model_name}}
            - Date: {{cur_date}}
            - Time: {{cur_time}}

            **Additional info:
            - The UI supports LaTeX rendering
            - The user is chatting to you trough an app called LastChat
            - You are an AI/LLM and shouldn't hide this fact
        """.trimIndent()
    )
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
private val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

/**
 * Sanitize settings after backup restore.
 * Cleans up deprecated fields and invalid references.
 * @return Pair of sanitized settings and cleanup result with statistics
 */
fun Settings.sanitize(): Pair<Settings, me.rerere.rikkahub.data.sync.BackupCleanupResult> {
    var invalidSearchModeCount = 0
    var orphanedTagReferences = 0
    var orphanedModelReferences = 0

    // 1. Fix invalid searchMode.Provider indices
    val sanitizedAssistants = assistants.map { assistant ->
        when (val mode = assistant.searchMode) {
            is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider -> {
                if (mode.index < 0 || mode.index >= searchServices.size) {
                    invalidSearchModeCount++
                    assistant.copy(searchMode = me.rerere.rikkahub.data.model.AssistantSearchMode.Off)
                } else {
                    assistant
                }
            }
            else -> assistant
        }
    }

    // 2. Remove orphaned tag references from assistants
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

    // 3. Remove orphaned favorite model references
    val allModelIds = providers.flatMap { it.models.map { m -> m.id } }.toSet()
    val cleanedFavorites = favoriteModels.filter { it in allModelIds }
    orphanedModelReferences = favoriteModels.size - cleanedFavorites.size

    // 4. Clamp searchServiceSelected to valid range
    val clampedSearchSelected = if (searchServices.isNotEmpty()) {
        searchServiceSelected.coerceIn(0, searchServices.size - 1)
    } else {
        0
    }

    val cleanedSettings = copy(
        assistants = cleanedAssistants,
        favoriteModels = cleanedFavorites,
        searchServiceSelected = clampedSearchSelected,
    )

    val result = me.rerere.rikkahub.data.sync.BackupCleanupResult(
        invalidSearchModeCount = invalidSearchModeCount,
        orphanedTagReferences = orphanedTagReferences,
        orphanedModelReferences = orphanedModelReferences,
    )

    return cleanedSettings to result
}

