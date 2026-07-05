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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.withComfyDefaults
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateCache
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.normalizeSuggestionPrompt
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.ChatStorageSettings
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Mode
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.TextSelectionConfig
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.withDefaultVoices
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
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val THEME_ID = stringPreferencesKey("theme_id")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val ENABLE_RAG_LOGGING = booleanPreferencesKey("enable_rag_logging")

        // 模型选择
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val TITLE_MODEL = stringPreferencesKey("title_model")
        val TITLE_THINKING_BUDGET = intPreferencesKey("title_thinking_budget")
        val SUMMARIZER_MODEL = stringPreferencesKey("summarizer_model")
        val SUMMARIZER_THINKING_BUDGET = intPreferencesKey("summarizer_thinking_budget")
        val SUBAGENT_MODEL = stringPreferencesKey("subagent_model")
        val SUBAGENT_THINKING_BUDGET = intPreferencesKey("subagent_thinking_budget")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
        val SUGGESTION_THINKING_BUDGET = intPreferencesKey("suggestion_thinking_budget")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val LEARNING_MODE_PROMPT = stringPreferencesKey("learning_mode_prompt")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_THINKING_BUDGET = intPreferencesKey("ocr_thinking_budget")
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
        val SELECTED_TTS_VOICE = stringPreferencesKey("selected_tts_voice")
        val TTS_AUTOPLAY_MODE = stringPreferencesKey("tts_autoplay_mode")

        // STT
        val STT_MODEL = stringPreferencesKey("stt_model")
        val STT_THINKING_BUDGET = intPreferencesKey("stt_thinking_budget")
        val STT_PROMPT = stringPreferencesKey("stt_prompt")

        // Web Server
        val WEB_SERVER_ENABLED = booleanPreferencesKey("web_server_enabled")
        val WEB_SERVER_PORT = intPreferencesKey("web_server_port")
        val WEB_SERVER_JWT_ENABLED = booleanPreferencesKey("web_server_jwt_enabled")
        val WEB_SERVER_ACCESS_PASSWORD = stringPreferencesKey("web_server_access_password")
        val WEB_SERVER_BACKGROUND_SETUP_SHOWN = booleanPreferencesKey("web_server_background_setup_shown")

        // Background Worker
        val CONSOLIDATION_WORKER_INTERVAL = intPreferencesKey("consolidation_worker_interval")
        val CONSOLIDATION_REQUIRES_DEVICE_IDLE = booleanPreferencesKey("consolidation_requires_device_idle")

        // Prompt Injections
        val MODES = stringPreferencesKey("modes")
        val LOREBOOKS = stringPreferencesKey("lorebooks")
        val SKILLS = stringPreferencesKey("skills")
        val CHAT_STORAGE = stringPreferencesKey("chat_storage")

        // Dismissed banners
        val DISMISSED_BANNERS = stringPreferencesKey("dismissed_banners")

        // Android Integration
        val TEXT_SELECTION_CONFIG = stringPreferencesKey("text_selection_config")

        // Graph memory system (v34)
        val MEMORY_SETTINGS = stringPreferencesKey("memory_settings")

        // Local Models
        val DEVICE_PERFORMANCE_PROFILE = stringPreferencesKey("device_performance_profile")
    }

    private val dataStore = context.settingsStore

    // One-time migration flags
    private var hasPersistedLegacyModeMigration = false
    private var hasPersistedLegacySummarizerMigration = false

    val settingsFlowRaw = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            runCatching {
                Settings(
                    setupCompleted = preferences[SETUP_COMPLETED] == true,
                    enableWebSearch = preferences[ENABLE_WEB_SEARCH] == true,
                    favoriteModels = preferences[FAVORITE_MODELS]?.let {
                        JsonInstant.decodeFromString(it)
                    } ?: emptyList(),
                    chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                        ?: GEMINI_2_5_FLASH_ID,
                    titleModelId = preferences[TITLE_MODEL]?.let { Uuid.parse(it) }
                        ?: GEMINI_2_5_FLASH_ID,
                    titleThinkingBudget = preferences[TITLE_THINKING_BUDGET] ?: 0,
                    summarizerModelId = preferences[SUMMARIZER_MODEL]?.let { Uuid.parse(it) },
                    summarizerThinkingBudget = preferences[SUMMARIZER_THINKING_BUDGET] ?: 0,
                    subagentModelId = preferences[SUBAGENT_MODEL]?.let { Uuid.parse(it) },
                    subagentThinkingBudget = preferences[SUBAGENT_THINKING_BUDGET] ?: 0,
                    translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
                        ?: GEMINI_2_5_FLASH_ID,
                    suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) }
                        ?: GEMINI_2_5_FLASH_ID,
                    suggestionThinkingBudget = preferences[SUGGESTION_THINKING_BUDGET] ?: 0,
                    imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                    titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                    translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                    suggestionPrompt = normalizeSuggestionPrompt(
                        preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT
                    ),
                    learningModePrompt = preferences[LEARNING_MODE_PROMPT] ?: DEFAULT_LEARNING_MODE_PROMPT,
                    ocrModelId = preferences[OCR_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                    ocrThinkingBudget = preferences[OCR_THINKING_BUDGET] ?: 0,
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
                    displaySetting = JsonInstant.decodeFromString<DisplaySetting>(
                        preferences[DISPLAY_SETTING] ?: "{}"
                    ).normalizeFontSettings(),
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
                    } ?: DEFAULT_TTS_PROVIDERS,
                    selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                        ?: DEFAULT_SYSTEM_TTS_ID,
                    selectedTTSVoiceId = preferences[SELECTED_TTS_VOICE]?.let { Uuid.parse(it) }
                        ?: DEFAULT_SYSTEM_TTS_VOICE_ID,
                    ttsAutoplayMode = preferences[TTS_AUTOPLAY_MODE]?.let {
                        JsonInstant.decodeFromString<TtsAutoplayMode>(it)
                    } ?: TtsAutoplayMode.OFF,
                    sttModelId = preferences[STT_MODEL]?.let { Uuid.parse(it) },
                    sttThinkingBudget = preferences[STT_THINKING_BUDGET] ?: 0,
                    sttPrompt = preferences[STT_PROMPT] ?: me.rerere.rikkahub.data.ai.prompts.DEFAULT_STT_PROMPT,
                    webServerEnabled = preferences[WEB_SERVER_ENABLED] == true,
                    webServerPort = preferences[WEB_SERVER_PORT] ?: 8080,
                    webServerJwtEnabled = preferences[WEB_SERVER_JWT_ENABLED] == true,
                    webServerAccessPassword = preferences[WEB_SERVER_ACCESS_PASSWORD] ?: "",
                    webServerBackgroundSetupShown = preferences[WEB_SERVER_BACKGROUND_SETUP_SHOWN] == true,
                    consolidationWorkerIntervalMinutes = preferences[CONSOLIDATION_WORKER_INTERVAL] ?: 15,
                    consolidationRequiresDeviceIdle = preferences[CONSOLIDATION_REQUIRES_DEVICE_IDLE] ?: false,
                    modes = preferences[MODES]?.let {
                        JsonInstant.decodeFromString(it)
                    } ?: emptyList(),
                    lorebooks = preferences[LOREBOOKS]?.let {
                        JsonInstant.decodeFromString(it)
                    } ?: emptyList(),
                    skills = preferences[SKILLS]?.let {
                        JsonInstant.decodeFromString(it)
                    } ?: emptyList(),
                    chatStorage = preferences[CHAT_STORAGE]?.let {
                        JsonInstant.decodeFromString<ChatStorageSettings>(it)
                    } ?: ChatStorageSettings(),
                    dismissedBanners = preferences[DISMISSED_BANNERS]?.let {
                        JsonInstant.decodeFromString(it)
                    } ?: emptySet(),
                    textSelectionConfig = preferences[TEXT_SELECTION_CONFIG]?.let {
                        JsonInstant.decodeFromString(it)
                    } ?: TextSelectionConfig(),
                    memory = preferences[MEMORY_SETTINGS]?.let {
                        JsonInstant.decodeFromString(it)
                    } ?: MemorySettings(),
                ).normalizeThemeId()
            }.getOrElse {
                Log.e(TAG, "Failed to parse settings", it)
                Settings().copy(init = false)
            }
        }
        .map {
            var providers = it.providers
                .ifEmpty { DEFAULT_PROVIDERS }
                .toMutableList()
            providers = providers.map { provider ->
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                if (defaultProvider != null) {
                    provider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                    )
                } else provider
            }.toMutableList()
            val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
            val ttsProviders = it.ttsProviders.map { provider ->
                val defaultVoiceId = if (provider.id == DEFAULT_SYSTEM_TTS_ID) DEFAULT_SYSTEM_TTS_VOICE_ID else null
                provider.withDefaultVoices(defaultVoiceId)
            }.toMutableList()
            val selectedTtsVoiceId = ttsProviders
                .flatMap { provider -> provider.voices }
                .firstOrNull { voice -> voice.id == it.selectedTTSVoiceId }
                ?.id
                ?: ttsProviders.find { provider -> provider.id == it.selectedTTSProviderId }
                    ?.voices
                    ?.firstOrNull()
                    ?.id
                ?: DEFAULT_SYSTEM_TTS_VOICE_ID
            it.copy(
                providers = providers,
                assistants = assistants,
                ttsProviders = ttsProviders,
                selectedTTSVoiceId = selectedTtsVoiceId,
            ).normalizeWebServerSettings().normalizeFontSettings().normalizeTtsSettings()
        }
        .map { settings ->
            // 去重并清理无效引用
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            settings.copy(
                providers = settings.providers
                    .distinctBy { it.id }
                    .map { provider ->
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

                        is ProviderSetting.ComfyUI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                                .map { model -> model.withComfyDefaults() }
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
                ttsProviders = settings.ttsProviders.distinctBy { it.id }.map { provider ->
                    val defaultVoiceId = if (provider.id == DEFAULT_SYSTEM_TTS_ID) DEFAULT_SYSTEM_TTS_VOICE_ID else null
                    provider.copyProvider(
                        voices = provider.withDefaultVoices(defaultVoiceId).voices.distinctBy { voice -> voice.id }
                    )
                },

                selectedTTSVoiceId = settings.ttsProviders
                    .flatMap { it.voices }
                    .firstOrNull { it.id == settings.selectedTTSVoiceId }
                    ?.id
                    ?: settings.ttsProviders.find { it.id == settings.selectedTTSProviderId }
                        ?.voices
                        ?.firstOrNull()
                        ?.id
                    ?: DEFAULT_SYSTEM_TTS_VOICE_ID,
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                }
            )
        }
        .map { settings ->
            val migrated = settings.migrateAssistantSummarizerToGlobal()
            if (!hasPersistedLegacySummarizerMigration) {
                hasPersistedLegacySummarizerMigration = true
                if (migrated != settings) {
                    scope.launch(Dispatchers.IO) {
                        persistLegacySummarizerMigration(migrated)
                    }
                }
            }
            migrated.normalizeFontSettings()
        }
        .map { settings ->
            val migrated = settings.migrateLegacyModesToSkills()
            if (!hasPersistedLegacyModeMigration) {
                hasPersistedLegacyModeMigration = true
                if (migrated != settings) {
                    scope.launch(Dispatchers.IO) {
                        persistLegacyModeMigration(migrated)
                    }
                }
            }
            migrated.normalizeFontSettings()
        }
        .onEach {
            get<MessageTemplateCache>().invalidateAll()
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
                    scope.launch(Dispatchers.IO) {
                        persistMigratedSettings(migratedSettings)
                    }
                }
                migratedSettings.normalizeFontSettings()
            } else {
                settings.normalizeFontSettings()
            }
        }
        // Hydrate secrets (populate API keys from SecureStore) so they are available in memory/UI
        .map { settings ->
            secretKeyManager.populateSecretsForExport(settings).normalizeFontSettings()
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

    /**
     * Persist one-time legacy mode -> skill migration.
     */
    private suspend fun persistLegacyModeMigration(settings: Settings) {
        dataStore.edit { preferences ->
            preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
            preferences[MODES] = JsonInstant.encodeToString(settings.modes)
            preferences[SKILLS] = JsonInstant.encodeToString(settings.skills)
        }
    }

    /**
     * Persist one-time assistant summarizer -> global summarizer migration.
     */
    private suspend fun persistLegacySummarizerMigration(settings: Settings) {
        dataStore.edit { preferences ->
            settings.summarizerModelId?.let {
                preferences[SUMMARIZER_MODEL] = it.toString()
            } ?: preferences.remove(SUMMARIZER_MODEL)
            preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
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
        
        val normalizedSettings = settingsToSave
            .normalizeWebServerSettings()
            .migrateLegacyModesToSkills()
            .normalizeFontSettings()
            .normalizeThemeId()
            .normalizeTtsSettings()
            .normalizeAssistantMemory()

        // Handle explicit secret deletions (user cleared a field that had a value)
        // This must be called BEFORE migration to remove deleted secrets from SecureStore
        secretKeyManager.handleExplicitSecretDeletions(settingsFlow.value, normalizedSettings)
        
        // Migrate secrets from plaintext to SecureStore if needed
        val migratedSettings = secretKeyManager.migrateSecretsFromSettings(normalizedSettings)
            .normalizeFontSettings()

        settingsFlow.value = secretKeyManager.populateSecretsForExport(migratedSettings)
            .normalizeFontSettings()
            .normalizeTtsSettings()
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = normalizedSettings.dynamicColor
            preferences[SETUP_COMPLETED] = normalizedSettings.setupCompleted
            preferences[THEME_ID] = normalizedSettings.themeId
            preferences[DEVELOPER_MODE] = normalizedSettings.developerMode
            preferences[ENABLE_RAG_LOGGING] = normalizedSettings.enableRagLogging
            preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(normalizedSettings.displaySetting)

            preferences[ENABLE_WEB_SEARCH] = normalizedSettings.enableWebSearch
            preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(normalizedSettings.favoriteModels)
            preferences[SELECT_MODEL] = normalizedSettings.chatModelId.toString()
            preferences[TITLE_MODEL] = normalizedSettings.titleModelId.toString()
            preferences[TITLE_THINKING_BUDGET] = normalizedSettings.titleThinkingBudget
            normalizedSettings.summarizerModelId?.let {
                preferences[SUMMARIZER_MODEL] = it.toString()
            } ?: preferences.remove(SUMMARIZER_MODEL)
            preferences[SUMMARIZER_THINKING_BUDGET] = normalizedSettings.summarizerThinkingBudget
            normalizedSettings.subagentModelId?.let {
                preferences[SUBAGENT_MODEL] = it.toString()
            } ?: preferences.remove(SUBAGENT_MODEL)
            preferences[SUBAGENT_THINKING_BUDGET] = normalizedSettings.subagentThinkingBudget
            preferences[TRANSLATE_MODEL] = normalizedSettings.translateModeId.toString()
            preferences[SUGGESTION_MODEL] = normalizedSettings.suggestionModelId.toString()
            preferences[SUGGESTION_THINKING_BUDGET] = normalizedSettings.suggestionThinkingBudget
            preferences[IMAGE_GENERATION_MODEL] = normalizedSettings.imageGenerationModelId.toString()
            preferences[TITLE_PROMPT] = normalizedSettings.titlePrompt
            preferences[TRANSLATION_PROMPT] = normalizedSettings.translatePrompt
            preferences[SUGGESTION_PROMPT] = normalizedSettings.suggestionPrompt
            preferences[LEARNING_MODE_PROMPT] = normalizedSettings.learningModePrompt
            preferences[OCR_MODEL] = normalizedSettings.ocrModelId.toString()
            preferences[OCR_THINKING_BUDGET] = normalizedSettings.ocrThinkingBudget
            preferences[OCR_PROMPT] = normalizedSettings.ocrPrompt
            preferences[EMBEDDING_MODEL] = normalizedSettings.embeddingModelId.toString()

            preferences[PROVIDERS] = JsonInstant.encodeToString(migratedSettings.providers)

            preferences[ASSISTANTS] = JsonInstant.encodeToString(normalizedSettings.assistants)
            preferences[SELECT_ASSISTANT] = normalizedSettings.assistantId.toString()
            preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(normalizedSettings.assistantTags)
            preferences[PROVIDER_TAGS] = JsonInstant.encodeToString(normalizedSettings.providerTags)
            preferences[RECENTLY_USED_ASSISTANTS] = JsonInstant.encodeToString(normalizedSettings.recentlyUsedAssistants)

            preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(normalizedSettings.searchServices)
            preferences[SEARCH_COMMON] = JsonInstant.encodeToString(normalizedSettings.searchCommonOptions)
            preferences[SEARCH_SELECTED] = normalizedSettings.searchServiceSelected.coerceIn(0, normalizedSettings.searchServices.size - 1)

            preferences[MCP_SERVERS] = JsonInstant.encodeToString(normalizedSettings.mcpServers)
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(migratedSettings.webDavConfig)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(migratedSettings.ttsProviders)
            normalizedSettings.selectedTTSProviderId?.let {
                preferences[SELECTED_TTS_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_TTS_PROVIDER)
            preferences[SELECTED_TTS_VOICE] = normalizedSettings.selectedTTSVoiceId.toString()
            preferences[TTS_AUTOPLAY_MODE] = JsonInstant.encodeToString(normalizedSettings.ttsAutoplayMode)
            normalizedSettings.sttModelId?.let {
                preferences[STT_MODEL] = it.toString()
            } ?: preferences.remove(STT_MODEL)
            preferences[STT_THINKING_BUDGET] = normalizedSettings.sttThinkingBudget
            preferences[STT_PROMPT] = normalizedSettings.sttPrompt
            preferences[WEB_SERVER_ENABLED] = normalizedSettings.webServerEnabled
            preferences[WEB_SERVER_PORT] = normalizedSettings.webServerPort
            preferences[WEB_SERVER_JWT_ENABLED] = normalizedSettings.webServerJwtEnabled
            preferences[WEB_SERVER_ACCESS_PASSWORD] = normalizedSettings.webServerAccessPassword
            preferences[WEB_SERVER_BACKGROUND_SETUP_SHOWN] = normalizedSettings.webServerBackgroundSetupShown

            preferences[CONSOLIDATION_WORKER_INTERVAL] = normalizedSettings.consolidationWorkerIntervalMinutes
            preferences[CONSOLIDATION_REQUIRES_DEVICE_IDLE] = normalizedSettings.consolidationRequiresDeviceIdle

            preferences[MODES] = JsonInstant.encodeToString(normalizedSettings.modes)
            preferences[LOREBOOKS] = JsonInstant.encodeToString(normalizedSettings.lorebooks)
            preferences[SKILLS] = JsonInstant.encodeToString(normalizedSettings.skills)
            preferences[CHAT_STORAGE] = JsonInstant.encodeToString(normalizedSettings.chatStorage)
            preferences[DISMISSED_BANNERS] = JsonInstant.encodeToString(normalizedSettings.dismissedBanners)
            preferences[TEXT_SELECTION_CONFIG] = JsonInstant.encodeToString(normalizedSettings.textSelectionConfig)
            preferences[MEMORY_SETTINGS] = JsonInstant.encodeToString(normalizedSettings.memory)
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

