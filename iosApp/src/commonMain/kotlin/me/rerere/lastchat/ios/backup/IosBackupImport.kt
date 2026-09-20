package me.rerere.lastchat.ios.backup

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.lastchat.ios.IosAppearancePreferences
import me.rerere.lastchat.ios.IosAssistantPreferences
import me.rerere.lastchat.ios.IosLocalToolOption
import me.rerere.lastchat.ios.IosMemoryMode
import me.rerere.lastchat.ios.IosProviderType
import me.rerere.lastchat.ios.IosSearchPreferences
import me.rerere.lastchat.ios.IosSearchProviderType
import me.rerere.lastchat.ios.IosTtsPreferences
import me.rerere.lastchat.ios.IosTtsProviderType
import me.rerere.lastchat.ios.displayName
import me.rerere.lastchat.ios.IosSttPreferences
import me.rerere.lastchat.ios.IosWebDavPreferences
import me.rerere.lastchat.ios.IosWebPreferences
import me.rerere.search.SearchServiceOptions
import me.rerere.rikkahub.data.mcp.PortableMcpServer
import me.rerere.rikkahub.data.mcp.PortableMcpTool
import me.rerere.rikkahub.data.mcp.PortableMcpTransport
import me.rerere.rikkahub.data.prompt.LorebookActivationKind
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition
import me.rerere.tts.provider.TTSProviderSetting

@Serializable
internal data class IosBackupManifest(
    val formatVersion: Int = 1,
    val includesDatabase: Boolean = false,
    val includesFiles: Boolean = false,
    val managedFileDirs: List<String> = emptyList(),
    val sharedPrefsStores: List<String> = emptyList(),
)

/** A provider sourced from an Android backup; secrets go to per-provider Keychain entries. */
internal data class IosBackupImportPlan(
    val appearance: IosAppearancePreferences? = null,
    val assistants: List<IosAssistantPreferences> = emptyList(),
    val selectedAssistantId: String? = null,
    val providers: List<ProviderSetting> = emptyList(),
    val providerApiKeys: Map<String, String> = emptyMap(),
    val selectedChatModelId: String? = null,
    val search: IosSearchPreferences? = null,
    val searchApiKeys: Map<IosSearchProviderType, String> = emptyMap(),
    val tts: IosTtsPreferences? = null,
    val ttsApiKeys: Map<IosTtsProviderType, String> = emptyMap(),
    val skills: List<PortableSkill> = emptyList(),
    val lorebooks: List<PortableLorebook> = emptyList(),
    val mcpServers: List<PortableMcpServer> = emptyList(),
    val web: IosWebPreferences? = null,
    val webPassword: String? = null,
    val webDav: IosWebDavPreferences? = null,
    val webDavPassword: String? = null,
    val applied: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val skipped: List<String> = emptyList(),
) {
    fun toReport(manifest: IosBackupManifest?): IosBackupImportReport = IosBackupImportReport(
        formatVersion = manifest?.formatVersion ?: 1,
        applied = applied,
        warnings = warnings,
        skipped = skipped,
    )
}

internal data class IosBackupImportReport(
    val formatVersion: Int,
    val applied: List<String>,
    val warnings: List<String>,
    val skipped: List<String>,
)

/**
 * Maps an Android backup `settings.json` (format version 2, see
 * app/src/main/java/me/rerere/rikkahub/data/sync/BackupArchiveFormat.kt) onto the
 * iOS state model. Pure functions only; the controller applies the plan.
 */
internal object IosBackupImporter {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parseManifest(encoded: String?): IosBackupManifest? = encoded?.let {
        runCatching { json.decodeFromString<IosBackupManifest>(it) }.getOrNull()
    }

    fun buildImportPlan(settings: JsonObject): IosBackupImportPlan {
        val applied = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        val providers = decodeProviders(settings, skipped)
        val searchServices = decodeSearchServices(settings, skipped)
        val ttsProviders = decodeTtsProviders(settings, skipped)

        val selectedChatModelId = parseUuid(settings.string("chatModelId"))
        val selectedModel = resolveModel(providers, selectedChatModelId)
        if (selectedModel != null) {
            applied += "Chat model: selected ${selectedModel.second}"
        } else {
            warnings += "Chat model: the Android selection could not be matched on iOS; kept the iOS default"
        }

        val providerImport = buildProviderImport(
            providers = providers,
            selectedChatModelId = selectedChatModelId,
            applied = applied,
            warnings = warnings,
            skipped = skipped,
        )

        return IosBackupImportPlan(
            appearance = mapAppearance(settings, warnings),
            assistants = mapAssistants(settings, providers, warnings, skipped),
            selectedAssistantId = settings.string("assistantId")?.takeIf(String::isNotBlank),
            providers = providerImport.providers,
            providerApiKeys = providerImport.apiKeys,
            selectedChatModelId = selectedChatModelId?.toString(),
            search = mapSearch(settings, searchServices, applied, warnings, skipped),
            searchApiKeys = collectSearchApiKeys(searchServices),
            tts = mapTts(settings, ttsProviders, applied, warnings, skipped),
            ttsApiKeys = collectTtsApiKeys(ttsProviders),
            skills = mapSkills(settings, applied),
            lorebooks = mapLorebooks(settings, applied),
            mcpServers = mapMcpServers(settings, applied, skipped),
            web = mapWeb(settings, applied),
            webPassword = settings.string("webServerAccessPassword")?.takeIf { it.isNotBlank() },
            webDav = mapWebDav(settings, applied),
            webDavPassword = (settings["webDavConfig"] as? JsonObject)?.string("password")?.takeIf { it.isNotBlank() },
            applied = applied,
            warnings = warnings,
            skipped = skipped,
        )
    }

    private fun decodeProviders(
        settings: JsonObject,
        skipped: MutableList<String>,
    ): List<ProviderSetting> {
        val element = settings["providers"] as? JsonArray ?: return emptyList()
        return element.mapNotNull { entry ->
            runCatching {
                json.decodeFromJsonElement(ProviderSetting.serializer(), entry)
            }.getOrElse {
                skipped += "Provider entry could not be parsed: ${it.message}"
                null
            }
        }
    }

    private fun decodeSearchServices(
        settings: JsonObject,
        skipped: MutableList<String>,
    ): List<SearchServiceOptions> {
        val element = settings["searchServices"] as? JsonArray ?: return emptyList()
        return element.mapNotNull { entry ->
            runCatching {
                json.decodeFromJsonElement(SearchServiceOptions.serializer(), entry)
            }.getOrElse {
                skipped += "Search service entry could not be parsed: ${it.message}"
                null
            }
        }
    }

    private fun decodeTtsProviders(
        settings: JsonObject,
        skipped: MutableList<String>,
    ): List<TTSProviderSetting> {
        val element = settings["ttsProviders"] as? JsonArray ?: return emptyList()
        return element.mapNotNull { entry ->
            runCatching {
                json.decodeFromJsonElement(TTSProviderSetting.serializer(), entry)
            }.getOrElse {
                skipped += "TTS provider entry could not be parsed: ${it.message}"
                null
            }
        }
    }

    private data class ProviderImport(
        val providers: List<ProviderSetting>,
        val apiKeys: Map<String, String>,
    )

    /** Imports every iOS-capable provider with its models preserved, keys going to
     *  per-provider Keychain entries. Unsupported provider types are reported. */
    private fun buildProviderImport(
        providers: List<ProviderSetting>,
        selectedChatModelId: Uuid?,
        applied: MutableList<String>,
        warnings: MutableList<String>,
        skipped: MutableList<String>,
    ): ProviderImport {
        val usable = mutableListOf<ProviderSetting>()
        val apiKeys = mutableMapOf<String, String>()
        var importedKeys = 0
        providers.forEach { provider ->
            val type = chatProviderType(provider)
            when {
                type == null -> skipped += "Provider \"${provider.name}\" is not supported on iOS"
                !provider.enabled -> skipped += "Provider \"${provider.name}\" is disabled on Android and was skipped"
                provider is ProviderSetting.Google && provider.vertexAI ->
                    skipped += "Google provider \"${provider.name}\" uses Vertex AI (not supported on iOS yet)"
                else -> {
                    usable += provider
                    val apiKey = providerApiKey(provider)
                    if (apiKey.isNotBlank()) {
                        apiKeys[provider.id.toString()] = apiKey
                        importedKeys++
                    } else {
                        warnings += "Provider \"${provider.name}\": the backup did not contain an API key"
                    }
                }
            }
        }
        if (usable.isNotEmpty()) {
            applied += "Providers: imported ${usable.size} providers ($importedKeys with API keys)"
        }
        return ProviderImport(usable, apiKeys)
    }

    private fun mapAssistants(
        settings: JsonObject,
        providers: List<ProviderSetting>,
        warnings: MutableList<String>,
        skipped: MutableList<String>,
    ): List<IosAssistantPreferences> {
        val elements = settings["assistants"] as? JsonArray ?: return emptyList()
        return elements.mapIndexedNotNull { index, entry ->
            val element = entry as? JsonObject ?: return@mapIndexedNotNull null
            mapAssistant(element, providers, index, warnings, skipped)
        }
    }

    private fun mapAssistant(
        element: JsonObject,
        providers: List<ProviderSetting>,
        index: Int,
        warnings: MutableList<String>,
        skipped: MutableList<String>,
    ): IosAssistantPreferences? {
        val name = element.string("name")?.takeIf(String::isNotBlank) ?: "Assistant ${index + 1}"
        val enableMemory = element.boolean("enableMemory") ?: false
        val memoryMode = when {
            !enableMemory -> IosMemoryMode.OFF
            element.boolean("enableMemoryConsolidation") == true -> IosMemoryMode.ADAPTIVE
            element.boolean("enableMemorySearchTool") == true -> IosMemoryMode.SEARCHABLE
            else -> IosMemoryMode.BASIC
        }
        val embeddingModelId = element.string("embeddingModelId")
        val embeddingUuid = embeddingModelId?.let(::parseUuid)
        val embeddingProvider = embeddingUuid
            ?.takeIf { memoryMode != IosMemoryMode.OFF }
            ?.let { uuid -> providers.firstOrNull { provider -> provider.models.any { it.id == uuid } } }
        val embedding = embeddingModelId
            ?.takeIf { memoryMode != IosMemoryMode.OFF }
            ?.let { rawId -> resolveModel(providers, parseUuid(rawId)) }
        if (memoryMode != IosMemoryMode.OFF && embeddingModelId != null && embedding == null) {
            warnings += "Assistant \"$name\": embedding model could not be matched to an iOS provider; kept the default"
        }
        val localTools = mutableSetOf<IosLocalToolOption>()
        val tools = element["localTools"] as? JsonArray
        tools?.forEach { toolElement ->
            val type = (toolElement as? JsonObject)?.string("type") ?: return@forEach
            when (type) {
                "javascript_engine" -> localTools += IosLocalToolOption.JAVASCRIPT
                "device_control" -> localTools += IosLocalToolOption.NOTIFICATIONS
                "tts" -> localTools += IosLocalToolOption.TTS
                "character_questions" -> localTools += IosLocalToolOption.ASK_USER
                "image_generation" -> localTools += IosLocalToolOption.IMAGE_GENERATION
                "python_engine" -> skipped += "Assistant \"$name\": local tool Python sandbox (not supported on iOS)"
            }
        }
        return IosAssistantPreferences(
            id = element.string("id")?.takeIf(String::isNotBlank) ?: Uuid.random().toString(),
            name = name,
            systemPrompt = element.string("systemPrompt").orEmpty(),
            memoryMode = memoryMode,
            embeddingProviderId = embeddingProvider?.id?.toString(),
            embeddingProviderType = embedding?.first ?: IosProviderType.OPENAI,
            embeddingModelId = embedding?.second ?: "text-embedding-3-small",
            ragSimilarityThreshold = element.float("ragSimilarityThreshold") ?: 0.45f,
            ragLimit = element.int("ragLimit") ?: 10,
            localTools = localTools,
            enabledSkillIds = uuidSet(element, "enabledSkillIds"),
            enabledLorebookIds = uuidSet(element, "enabledLorebookIds"),
        )
    }

    private fun mapAppearance(
        settings: JsonObject,
        warnings: MutableList<String>,
    ): IosAppearancePreferences? {
        val themeId = settings.string("themeId")?.takeIf(String::isNotBlank)
        val fontSizeRatio = (settings["displaySetting"] as? JsonObject)?.float("fontSizeRatio")
        if (themeId == null && fontSizeRatio == null) return null
        warnings += "Appearance: dynamic color is not available on iOS; the Android theme id and font size are applied where supported"
        return IosAppearancePreferences(
            themeId = themeId ?: IosAppearancePreferences().themeId,
            fontSizeRatio = fontSizeRatio ?: IosAppearancePreferences().fontSizeRatio,
        )
    }

    private fun mapSearch(
        settings: JsonObject,
        searchServices: List<SearchServiceOptions>,
        applied: MutableList<String>,
        warnings: MutableList<String>,
        skipped: MutableList<String>,
    ): IosSearchPreferences? {
        if (searchServices.isEmpty()) return null
        val selectedIndex = (settings.int("searchServiceSelected") ?: 0)
            .coerceIn(0, searchServices.size - 1)
        val selected = searchServices[selectedIndex]
        val providerType = searchProviderType(selected)
        if (providerType == null) {
            skipped += "Selected search service is not available on iOS; kept the current iOS search provider"
        } else {
            applied += "Search: selected ${providerType.displayName()}"
        }
        val enabled = settings.boolean("enableWebSearch")
        if (enabled == null) {
            warnings += "Search: enabled flag missing in the backup; kept the current iOS value"
        }
        val resultSize = (settings["searchCommonOptions"] as? JsonObject)?.int("resultSize")
        val searxng = selected as? SearchServiceOptions.SearXNGOptions
        return IosSearchPreferences(
            enabled = enabled ?: false,
            provider = providerType ?: IosSearchProviderType.BING,
            resultSize = resultSize ?: 5,
            searxngUrl = searxng?.url.orEmpty(),
            searxngEngines = searxng?.engines.orEmpty(),
            searxngLanguage = searxng?.language.orEmpty(),
            searxngUsername = searxng?.username.orEmpty(),
        )
    }

    private fun collectSearchApiKeys(
        searchServices: List<SearchServiceOptions>,
    ): Map<IosSearchProviderType, String> = buildMap {
        searchServices.forEach { options ->
            val type = searchProviderType(options) ?: return@forEach
            val key = searchApiKey(options)
            if (!key.isNullOrBlank()) put(type, key)
        }
    }

    private fun mapTts(
        settings: JsonObject,
        ttsProviders: List<TTSProviderSetting>,
        applied: MutableList<String>,
        warnings: MutableList<String>,
        skipped: MutableList<String>,
    ): IosTtsPreferences? {
        if (ttsProviders.isEmpty()) return null
        val selectedId = parseUuid(settings.string("selectedTTSProviderId"))
        val selected = ttsProviders.firstOrNull { it.id == selectedId } ?: ttsProviders.first()
        if (selected is TTSProviderSetting.SystemTTS) {
            skipped += "TTS: Android system TTS is not available on iOS; kept the current iOS provider"
            return null
        }
        val preferences = mapTtsProvider(selected)
        if (preferences == null) {
            skipped += "TTS: provider \"${selected.name}\" is not supported on iOS"
            return null
        }
        val apiKey = ttsApiKey(selected).orEmpty()
        applied += "TTS: selected ${preferences.type.displayName()}${if (apiKey.isNotBlank()) " with API key" else ""}"
        if (apiKey.isBlank()) {
            warnings += "TTS: the backup did not contain an API key for \"${selected.name}\""
        }
        return preferences.copy(enabled = apiKey.isNotBlank())
    }

    private fun mapTtsProvider(provider: TTSProviderSetting): IosTtsPreferences? = when (provider) {
        is TTSProviderSetting.OpenAI -> IosTtsPreferences(
            type = IosTtsProviderType.OPENAI,
            baseUrl = provider.baseUrl,
            model = provider.model,
            voice = provider.voice,
        )
        is TTSProviderSetting.Gemini -> IosTtsPreferences(
            type = IosTtsProviderType.GEMINI,
            baseUrl = provider.baseUrl,
            model = provider.model,
            voice = provider.voiceName,
        )
        is TTSProviderSetting.MiniMax -> IosTtsPreferences(
            type = IosTtsProviderType.MINIMAX,
            baseUrl = provider.baseUrl,
            model = provider.model,
            voice = provider.voiceId,
            emotion = provider.emotion,
            speed = provider.speed,
        )
        is TTSProviderSetting.ElevenLabs -> IosTtsPreferences(
            type = IosTtsProviderType.ELEVENLABS,
            baseUrl = "https://api.elevenlabs.io/v1",
            model = provider.modelId,
            voice = provider.voiceId,
        )
        is TTSProviderSetting.Qwen -> IosTtsPreferences(
            type = IosTtsProviderType.QWEN,
            baseUrl = provider.baseUrl,
            model = provider.model,
            voice = provider.voice,
            language = provider.languageType,
        )
        is TTSProviderSetting.FishAudio -> IosTtsPreferences(
            type = IosTtsProviderType.FISH_AUDIO,
            baseUrl = provider.baseUrl,
            model = provider.model,
            voice = provider.referenceId,
            language = provider.format,
            speed = provider.speed,
        )
        is TTSProviderSetting.Cartesia -> IosTtsPreferences(
            type = IosTtsProviderType.CARTESIA,
            baseUrl = provider.baseUrl,
            model = provider.modelId,
            voice = provider.voiceId,
            language = provider.language,
            emotion = provider.emotion,
            speed = provider.speed,
        )
        is TTSProviderSetting.PlayHT -> IosTtsPreferences(
            type = IosTtsProviderType.PLAY_HT,
            baseUrl = provider.baseUrl,
            model = provider.voiceEngine,
            voice = provider.voice,
            secondary = provider.userId,
            speed = provider.speed,
        )
        is TTSProviderSetting.SystemTTS -> null
    }

    private fun collectTtsApiKeys(
        ttsProviders: List<TTSProviderSetting>,
    ): Map<IosTtsProviderType, String> = buildMap {
        ttsProviders.forEach { provider ->
            val key = ttsApiKey(provider)
            if (key.isNullOrBlank()) return@forEach
            when (provider) {
                is TTSProviderSetting.OpenAI -> put(IosTtsProviderType.OPENAI, key)
                is TTSProviderSetting.Gemini -> put(IosTtsProviderType.GEMINI, key)
                is TTSProviderSetting.MiniMax -> put(IosTtsProviderType.MINIMAX, key)
                is TTSProviderSetting.ElevenLabs -> put(IosTtsProviderType.ELEVENLABS, key)
                is TTSProviderSetting.Qwen -> put(IosTtsProviderType.QWEN, key)
                is TTSProviderSetting.FishAudio -> put(IosTtsProviderType.FISH_AUDIO, key)
                is TTSProviderSetting.Cartesia -> put(IosTtsProviderType.CARTESIA, key)
                is TTSProviderSetting.PlayHT -> put(IosTtsProviderType.PLAY_HT, key)
                is TTSProviderSetting.SystemTTS -> Unit
            }
        }
    }

    private fun chatProviderType(provider: ProviderSetting): IosProviderType? = when (provider) {
        is ProviderSetting.OpenAI -> IosProviderType.OPENAI
        is ProviderSetting.Google -> IosProviderType.GOOGLE
        is ProviderSetting.Claude -> IosProviderType.CLAUDE
        else -> null
    }

    private fun providerApiKey(provider: ProviderSetting): String = when (provider) {
        is ProviderSetting.OpenAI -> provider.apiKey
        is ProviderSetting.Google -> provider.apiKey
        is ProviderSetting.Claude -> provider.apiKey
        else -> ""
    }

    private fun resolveModel(
        providers: List<ProviderSetting>,
        modelId: Uuid?,
    ): Pair<IosProviderType, String>? {
        if (modelId == null) return null
        providers.forEach { provider ->
            val model = provider.models.firstOrNull { it.id == modelId } ?: return@forEach
            val type = chatProviderType(provider) ?: return@forEach
            return type to model.modelId
        }
        return null
    }

    private fun searchProviderType(options: SearchServiceOptions): IosSearchProviderType? = when (options) {
        is SearchServiceOptions.KeylessOptions -> IosSearchProviderType.KEYLESS
        is SearchServiceOptions.BingLocalOptions -> IosSearchProviderType.BING
        is SearchServiceOptions.TavilyOptions -> IosSearchProviderType.TAVILY
        is SearchServiceOptions.ExaOptions -> IosSearchProviderType.EXA
        is SearchServiceOptions.BraveOptions -> IosSearchProviderType.BRAVE
        is SearchServiceOptions.PerplexityOptions -> IosSearchProviderType.PERPLEXITY
        is SearchServiceOptions.FirecrawlOptions -> IosSearchProviderType.FIRECRAWL
        is SearchServiceOptions.JinaOptions -> IosSearchProviderType.JINA
        is SearchServiceOptions.LinkUpOptions -> IosSearchProviderType.LINKUP
        is SearchServiceOptions.ZhipuOptions -> IosSearchProviderType.ZHIPU
        is SearchServiceOptions.MetasoOptions -> IosSearchProviderType.METASO
        is SearchServiceOptions.BochaOptions -> IosSearchProviderType.BOCHA
        is SearchServiceOptions.OllamaOptions -> IosSearchProviderType.OLLAMA
        is SearchServiceOptions.GrokOptions -> IosSearchProviderType.GROK
        is SearchServiceOptions.NanoGPTOptions -> IosSearchProviderType.NANOGPT
        is SearchServiceOptions.SearXNGOptions -> IosSearchProviderType.SEARXNG
    }

    private fun searchApiKey(options: SearchServiceOptions): String? = when (options) {
        is SearchServiceOptions.TavilyOptions -> options.apiKey
        is SearchServiceOptions.ExaOptions -> options.apiKey
        is SearchServiceOptions.BraveOptions -> options.apiKey
        is SearchServiceOptions.PerplexityOptions -> options.apiKey
        is SearchServiceOptions.FirecrawlOptions -> options.apiKey
        is SearchServiceOptions.JinaOptions -> options.apiKey
        is SearchServiceOptions.LinkUpOptions -> options.apiKey
        is SearchServiceOptions.ZhipuOptions -> options.apiKey
        is SearchServiceOptions.MetasoOptions -> options.apiKey
        is SearchServiceOptions.BochaOptions -> options.apiKey
        is SearchServiceOptions.OllamaOptions -> options.apiKey
        is SearchServiceOptions.GrokOptions -> options.apiKey
        is SearchServiceOptions.NanoGPTOptions -> options.apiKey
        is SearchServiceOptions.SearXNGOptions -> options.password.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun ttsApiKey(provider: TTSProviderSetting): String? = when (provider) {
        is TTSProviderSetting.OpenAI -> provider.apiKey
        is TTSProviderSetting.Gemini -> provider.apiKey
        is TTSProviderSetting.MiniMax -> provider.apiKey
        is TTSProviderSetting.ElevenLabs -> provider.apiKey
        is TTSProviderSetting.Qwen -> provider.apiKey
        is TTSProviderSetting.FishAudio -> provider.apiKey
        is TTSProviderSetting.Cartesia -> provider.apiKey
        is TTSProviderSetting.PlayHT -> provider.apiKey
        is TTSProviderSetting.SystemTTS -> null
    }

    private fun mapSkills(settings: JsonObject, applied: MutableList<String>): List<PortableSkill> {
        val elements = settings["skills"] as? JsonArray ?: return emptyList()
        val skills = elements.mapNotNull { entry ->
            val element = entry as? JsonObject ?: return@mapNotNull null
            val instructions = element.string("instructions").orEmpty()
            val name = element.string("name").orEmpty()
            if (instructions.isBlank() && name.isBlank()) return@mapNotNull null
            PortableSkill(
                id = element.string("id")?.takeIf { it.isNotBlank() } ?: Uuid.random().toString(),
                name = name,
                description = element.string("description").orEmpty(),
                instructions = instructions,
                enabled = element.boolean("enabled") ?: true,
                alwaysEnabled = element.boolean("always_enabled") ?: element.boolean("alwaysEnabled") ?: false,
                availableForAllAssistants = element.boolean("available_for_all_assistants")
                    ?: element.boolean("availableForAllAssistants")
                    ?: true,
                availableAssistantIds = uuidSet(element, "availableAssistantIds"),
                injectionPosition = injectionPosition(element.string("injectionPosition")),
                depth = element.int("depth") ?: 0,
                disableModelInvocation = element.boolean("disable_model_invocation")
                    ?: element.boolean("disableModelInvocation")
                    ?: false,
            )
        }
        if (skills.isNotEmpty()) applied += "Skills: imported ${skills.size}"
        return skills
    }

    private fun mapLorebooks(settings: JsonObject, applied: MutableList<String>): List<PortableLorebook> {
        val elements = settings["lorebooks"] as? JsonArray ?: return emptyList()
        val books = elements.mapNotNull { entry ->
            val element = entry as? JsonObject ?: return@mapNotNull null
            val entries = (element["entries"] as? JsonArray).orEmpty().mapNotNull { item ->
                val child = item as? JsonObject ?: return@mapNotNull null
                PortableLorebookEntry(
                    id = child.string("id")?.takeIf { it.isNotBlank() } ?: Uuid.random().toString(),
                    name = child.string("name").orEmpty(),
                    prompt = child.string("prompt").orEmpty(),
                    enabled = child.boolean("enabled") ?: true,
                    injectionPosition = injectionPosition(child.string("injectionPosition")),
                    depth = child.int("depth") ?: 0,
                    activationType = lorebookActivation(child.string("activationType")),
                    keywords = (child["keywords"] as? JsonArray)?.mapNotNull { keyword ->
                        (keyword as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    }.orEmpty(),
                    caseSensitive = child.boolean("caseSensitive") ?: false,
                    useRegex = child.boolean("useRegex") ?: false,
                    scanDepth = child.int("scanDepth") ?: 10,
                )
            }
            PortableLorebook(
                id = element.string("id")?.takeIf { it.isNotBlank() } ?: Uuid.random().toString(),
                name = element.string("name").orEmpty().ifBlank { "Lorebook" },
                description = element.string("description").orEmpty(),
                entries = entries,
                enabled = element.boolean("enabled") ?: true,
            )
        }
        if (books.isNotEmpty()) applied += "Lorebooks: imported ${books.size}"
        return books
    }

    private fun mapMcpServers(
        settings: JsonObject,
        applied: MutableList<String>,
        skipped: MutableList<String>,
    ): List<PortableMcpServer> {
        val elements = settings["mcpServers"] as? JsonArray ?: return emptyList()
        val servers = elements.mapNotNull { entry ->
            val element = entry as? JsonObject ?: return@mapNotNull null
            val common = element["commonOptions"] as? JsonObject
            val url = element.string("url").orEmpty()
            if (url.isBlank()) {
                skipped += "MCP server skipped: missing URL"
                return@mapNotNull null
            }
            val type = element.string("type")?.lowercase().orEmpty()
            val transport = when {
                type.contains("sse") -> PortableMcpTransport.SSE
                else -> PortableMcpTransport.STREAMABLE_HTTP
            }
            val tools = ((common?.get("tools") ?: element["tools"]) as? JsonArray).orEmpty().mapNotNull { toolEntry ->
                val tool = toolEntry as? JsonObject ?: return@mapNotNull null
                val name = tool.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PortableMcpTool(
                    name = name,
                    description = tool.string("description"),
                    enable = tool.boolean("enable") ?: true,
                )
            }
            val headers = ((common?.get("headers") ?: element["headers"]) as? JsonArray).orEmpty().mapNotNull { header ->
                val pair = header as? JsonArray ?: return@mapNotNull null
                val name = (pair.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
                val value = (pair.getOrNull(1) as? JsonPrimitive)?.content.orEmpty()
                name to value
            }
            PortableMcpServer(
                id = element.string("id")?.takeIf { it.isNotBlank() } ?: Uuid.random().toString(),
                name = common?.string("name") ?: element.string("name").orEmpty(),
                url = url,
                enable = common?.boolean("enable") ?: element.boolean("enable") ?: true,
                transport = transport,
                headers = headers,
                tools = tools,
            )
        }
        if (servers.isNotEmpty()) applied += "MCP: imported ${servers.size} servers"
        return servers
    }

    private fun mapWeb(settings: JsonObject, applied: MutableList<String>): IosWebPreferences? {
        val enabled = settings.boolean("webServerEnabled") ?: return null
        val port = settings.int("webServerPort") ?: 8080
        applied += "Web server: enabled=$enabled port=$port"
        return IosWebPreferences(enabled = enabled, port = port)
    }

    private fun mapWebDav(settings: JsonObject, applied: MutableList<String>): IosWebDavPreferences? {
        val config = settings["webDavConfig"] as? JsonObject ?: return null
        val url = config.string("url").orEmpty()
        if (url.isBlank()) return null
        applied += "WebDAV: imported remote URL"
        return IosWebDavPreferences(
            url = url,
            username = config.string("username").orEmpty(),
            path = config.string("path")?.ifBlank { "lastchat_backups" } ?: "lastchat_backups",
        )
    }

    private fun uuidSet(element: JsonObject, key: String): Set<String> {
        val array = element[key] as? JsonArray ?: return emptySet()
        return array.mapNotNull { item ->
            (item as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }.toSet()
    }

    private fun injectionPosition(raw: String?): PromptInjectionPosition = when (raw?.lowercase()) {
        "before_system" -> PromptInjectionPosition.BEFORE_SYSTEM
        "top_of_chat" -> PromptInjectionPosition.TOP_OF_CHAT
        "before_latest" -> PromptInjectionPosition.BEFORE_LATEST
        "at_depth" -> PromptInjectionPosition.AT_DEPTH
        else -> PromptInjectionPosition.AFTER_SYSTEM
    }

    private fun lorebookActivation(raw: String?): LorebookActivationKind = when (raw?.lowercase()) {
        "always" -> LorebookActivationKind.ALWAYS
        "rag" -> LorebookActivationKind.RAG
        else -> LorebookActivationKind.KEYWORDS
    }

    private fun parseUuid(value: String?): Uuid? {
        val text = value?.takeIf(String::isNotBlank) ?: return null
        return runCatching { Uuid.parse(text) }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.float(key: String): Float? =
        (this[key] as? JsonPrimitive)?.floatOrNull
}
