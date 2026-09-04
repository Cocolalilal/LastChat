package me.rerere.lastchat.ios.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.lastchat.ios.IosAppearancePreferences
import me.rerere.lastchat.ios.IosAppState
import me.rerere.lastchat.ios.IosAssistantPreferences
import me.rerere.lastchat.ios.IosColorMode
import me.rerere.lastchat.ios.IosConversation
import me.rerere.lastchat.ios.IosLocalToolOption
import me.rerere.lastchat.ios.IosMemoryMode
import me.rerere.lastchat.ios.IosMemoryRecord
import me.rerere.lastchat.ios.IosProviderPreferences
import me.rerere.lastchat.ios.IosProviderType
import me.rerere.lastchat.ios.IosSearchPreferences
import me.rerere.lastchat.ios.IosSearchProviderType
import me.rerere.lastchat.ios.IosTtsPreferences
import me.rerere.lastchat.ios.IosTtsProviderType
import me.rerere.lastchat.ios.IosZipArchive
import me.rerere.lastchat.ios.models.IosBackupItem
import me.rerere.lastchat.ios.models.IosBackupManifest
import me.rerere.lastchat.ios.models.IosCrossPlatformBackup
import me.rerere.lastchat.ios.models.IosInjectionPosition
import me.rerere.lastchat.ios.models.IosLorebook
import me.rerere.lastchat.ios.models.IosLorebookActivationType
import me.rerere.lastchat.ios.models.IosLorebookEntry
import me.rerere.lastchat.ios.models.IosMcpCommonOptions
import me.rerere.lastchat.ios.models.IosMcpServerConfig
import me.rerere.lastchat.ios.models.IosMcpTool
import me.rerere.lastchat.ios.models.IosRestoreResult
import me.rerere.lastchat.ios.models.IosSkill
import me.rerere.lastchat.ios.models.IosWebDavBackupItem
import me.rerere.lastchat.ios.models.IosWebDavConfig
import kotlin.time.Clock
import kotlin.uuid.Uuid

object IosBackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Restores app state from backup bytes (either an Android .zip backup or a .json export).
     */
    fun restore(
        backupBytes: ByteArray,
        currentState: IosAppState,
    ): Pair<IosAppState, IosRestoreResult> {
        val isZip = backupBytes.size >= 4 &&
            backupBytes[0] == 0x50.toByte() && // 'P'
            backupBytes[1] == 0x4B.toByte() && // 'K'
            backupBytes[2] == 0x03.toByte() &&
            backupBytes[3] == 0x04.toByte()

        val settingsJsonText: String
        var conversationsToRestore: List<IosConversation> = emptyList()
        var memoriesToRestore: List<IosMemoryRecord> = emptyList()

        if (isZip) {
            val entries = IosZipArchive.extractEntries(backupBytes)
            val settingsBytes = entries["settings.json"]
                ?: error("The backup archive does not contain settings.json.")
            settingsJsonText = settingsBytes.decodeToString()

            // If a conversations.json was packed in the zip
            entries["conversations.json"]?.let { convBytes ->
                runCatching {
                    conversationsToRestore = json.decodeFromString<List<IosConversation>>(convBytes.decodeToString())
                }
            }

            // If memories.json was packed
            entries["memories.json"]?.let { memBytes ->
                runCatching {
                    memoriesToRestore = json.decodeFromString<List<IosMemoryRecord>>(memBytes.decodeToString())
                }
            }
        } else {
            val text = backupBytes.decodeToString()
            // Try parsing as complete IosCrossPlatformBackup first
            val crossPlatform = runCatching { json.decodeFromString<IosCrossPlatformBackup>(text) }.getOrNull()
            if (crossPlatform != null) {
                val updatedState = currentState.copy(
                    appearance = crossPlatform.appearance,
                    provider = crossPlatform.provider,
                    providerConfigurations = crossPlatform.providerConfigurations.ifEmpty { listOf(crossPlatform.provider) },
                    assistants = crossPlatform.assistants.ifEmpty { currentState.assistants },
                    selectedAssistantId = crossPlatform.assistants.firstOrNull()?.id ?: currentState.selectedAssistantId,
                    skills = crossPlatform.skills,
                    lorebooks = crossPlatform.lorebooks,
                    mcpServers = crossPlatform.mcpServers,
                    search = crossPlatform.search,
                    tts = crossPlatform.tts,
                    webDavConfig = crossPlatform.webDavConfig,
                    conversations = crossPlatform.conversations.ifEmpty { currentState.conversations },
                    selectedConversationId = crossPlatform.conversations.firstOrNull()?.id ?: currentState.selectedConversationId,
                    memories = crossPlatform.memories,
                )
                val result = IosRestoreResult(
                    assistantsCount = crossPlatform.assistants.size,
                    providersCount = crossPlatform.providerConfigurations.size,
                    skillsCount = crossPlatform.skills.size,
                    lorebooksCount = crossPlatform.lorebooks.size,
                    mcpServersCount = crossPlatform.mcpServers.size,
                    conversationsCount = crossPlatform.conversations.size,
                    memoriesCount = crossPlatform.memories.size,
                    notes = listOf("Restored cross-platform backup package successfully."),
                )
                return updatedState to result
            }
            settingsJsonText = text
        }

        // Parse standard Android settings.json
        val root = json.parseToJsonElement(settingsJsonText).jsonObject

        // 1. Assistants
        val restoredAssistants = mutableListOf<IosAssistantPreferences>()
        val assistantsArray = root["assistants"] as? JsonArray
        assistantsArray?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val id = (obj["id"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { Uuid.random().toString() }
            val name = (obj["name"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { "Assistant" }
            val prompt = (obj["systemPrompt"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val memModeStr = (obj["memoryMode"] as? JsonPrimitive)?.content?.uppercase().orEmpty()
            val memMode = when (memModeStr) {
                "BASIC" -> IosMemoryMode.BASIC
                "SEARCHABLE" -> IosMemoryMode.SEARCHABLE
                "ADAPTIVE" -> IosMemoryMode.ADAPTIVE
                else -> IosMemoryMode.OFF
            }
            val tools = mutableSetOf<IosLocalToolOption>()
            (obj["localTools"] as? JsonArray)?.forEach { t ->
                val str = (t as? JsonPrimitive)?.content?.uppercase().orEmpty()
                when {
                    str.contains("JAVASCRIPT") -> tools.add(IosLocalToolOption.JAVASCRIPT)
                    str.contains("NOTIFICATION") -> tools.add(IosLocalToolOption.NOTIFICATIONS)
                    str.contains("TTS") -> tools.add(IosLocalToolOption.TTS)
                    str.contains("ASK") -> tools.add(IosLocalToolOption.ASK_USER)
                    str.contains("IMAGE") -> tools.add(IosLocalToolOption.IMAGE_GENERATION)
                }
            }
            restoredAssistants.add(
                IosAssistantPreferences(
                    id = id,
                    name = name,
                    systemPrompt = prompt,
                    memoryMode = memMode,
                    localTools = tools,
                )
            )
        }

        // 2. Providers & Models
        val restoredProviders = mutableListOf<IosProviderPreferences>()
        val providersArray = root["providers"] as? JsonArray
        providersArray?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val typeStr = (obj["type"] as? JsonPrimitive)?.content?.lowercase().orEmpty()
            val type = when {
                typeStr.contains("google") -> IosProviderType.GOOGLE
                typeStr.contains("claude") || typeStr.contains("anthropic") -> IosProviderType.CLAUDE
                else -> IosProviderType.OPENAI
            }
            val baseUrl = (obj["baseUrl"] as? JsonPrimitive)?.content?.trim().orEmpty()
                .ifBlank {
                    when (type) {
                        IosProviderType.OPENAI -> "https://api.openai.com/v1"
                        IosProviderType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
                        IosProviderType.CLAUDE -> "https://api.anthropic.com/v1"
                    }
                }
            val modelsArray = obj["models"] as? JsonArray
            val firstModel = (modelsArray?.firstOrNull() as? JsonObject)?.get("modelId")?.jsonPrimitive?.contentOrNull
                ?: when (type) {
                    IosProviderType.OPENAI -> "gpt-4.1-mini"
                    IosProviderType.GOOGLE -> "gemini-2.5-flash"
                    IosProviderType.CLAUDE -> "claude-sonnet-4-5"
                }
            restoredProviders.add(
                IosProviderPreferences(type = type, baseUrl = baseUrl, modelId = firstModel)
            )
        }

        // 3. Skills
        val restoredSkills = mutableListOf<IosSkill>()
        val skillsArray = root["skills"] as? JsonArray
        skillsArray?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val id = (obj["id"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { Uuid.random().toString() }
            val name = (obj["name"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { "Skill" }
            val desc = (obj["description"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val instructions = (obj["instructions"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true
            val always = (obj["always_enabled"] as? JsonPrimitive)?.booleanOrNull ?: true
            val posStr = (obj["injectionPosition"] as? JsonPrimitive)?.content?.uppercase().orEmpty()
            val pos = when {
                posStr.contains("BEFORE_SYSTEM") -> IosInjectionPosition.BEFORE_SYSTEM
                posStr.contains("AFTER_MEMORY") -> IosInjectionPosition.AFTER_MEMORY
                posStr.contains("BEFORE_MESSAGES") -> IosInjectionPosition.BEFORE_MESSAGES
                posStr.contains("AT_DEPTH") -> IosInjectionPosition.AT_DEPTH
                else -> IosInjectionPosition.AFTER_SYSTEM
            }
            restoredSkills.add(
                IosSkill(
                    id = id,
                    name = name,
                    description = desc,
                    instructions = instructions,
                    enabled = enabled,
                    alwaysEnabled = always,
                    injectionPosition = pos,
                )
            )
        }

        // 4. Lorebooks
        val restoredLorebooks = mutableListOf<IosLorebook>()
        val lorebooksArray = root["lorebooks"] as? JsonArray
        lorebooksArray?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val id = (obj["id"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { Uuid.random().toString() }
            val name = (obj["name"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { "Lorebook" }
            val desc = (obj["description"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true
            val entriesArray = obj["entries"] as? JsonArray
            val entries = mutableListOf<IosLorebookEntry>()
            entriesArray?.forEach { e ->
                val eObj = e as? JsonObject ?: return@forEach
                val eId = (eObj["id"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { Uuid.random().toString() }
                val eName = (eObj["name"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { "Entry" }
                val prompt = (eObj["prompt"] as? JsonPrimitive)?.content?.trim().orEmpty()
                val eEnabled = (eObj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true
                val actStr = (eObj["activationType"] as? JsonPrimitive)?.content?.uppercase().orEmpty()
                val actType = if (actStr.contains("ALWAYS")) IosLorebookActivationType.ALWAYS else IosLorebookActivationType.KEYWORDS
                val keywords = (eObj["keywords"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }.orEmpty()
                val caseSensitive = (eObj["caseSensitive"] as? JsonPrimitive)?.booleanOrNull ?: false
                val useRegex = (eObj["useRegex"] as? JsonPrimitive)?.booleanOrNull ?: false
                val scanDepth = (eObj["scanDepth"] as? JsonPrimitive)?.intOrNull ?: 10
                entries.add(
                    IosLorebookEntry(
                        id = eId,
                        name = eName,
                        prompt = prompt,
                        enabled = eEnabled,
                        activationType = actType,
                        keywords = keywords,
                        caseSensitive = caseSensitive,
                        useRegex = useRegex,
                        scanDepth = scanDepth,
                    )
                )
            }
            restoredLorebooks.add(
                IosLorebook(
                    id = id,
                    name = name,
                    description = desc,
                    enabled = enabled,
                    entries = entries,
                )
            )
        }

        // 5. MCP Servers
        val restoredMcpServers = mutableListOf<IosMcpServerConfig>()
        val mcpArray = root["mcpServers"] as? JsonArray
        mcpArray?.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val id = (obj["id"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { Uuid.random().toString() }
            val typeStr = (obj["type"] as? JsonPrimitive)?.content?.lowercase().orEmpty()
            val url = (obj["url"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val common = obj["commonOptions"] as? JsonObject
            val name = (common?.get("name") as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { "MCP Server" }
            val enabled = (common?.get("enable") as? JsonPrimitive)?.booleanOrNull ?: true
            val tools = (common?.get("tools") as? JsonArray)?.mapNotNull { t ->
                val tObj = t as? JsonObject ?: return@mapNotNull null
                val tName = (tObj["name"] as? JsonPrimitive)?.content?.trim().orEmpty()
                val tDesc = (tObj["description"] as? JsonPrimitive)?.content?.trim()
                val tEnabled = (tObj["enable"] as? JsonPrimitive)?.booleanOrNull ?: true
                IosMcpTool(enable = tEnabled, name = tName, description = tDesc, inputSchema = tObj["inputSchema"])
            }.orEmpty()
            val commonOpts = IosMcpCommonOptions(enable = enabled, name = name, tools = tools)
            if (typeStr.contains("streamable")) {
                restoredMcpServers.add(IosMcpServerConfig.StreamableHTTPServer(id = id, commonOptions = commonOpts, url = url))
            } else {
                restoredMcpServers.add(IosMcpServerConfig.SseTransportServer(id = id, commonOptions = commonOpts, url = url))
            }
        }

        // 6. Appearance & Display
        val themeId = (root["themeId"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val displayObj = root["displaySetting"] as? JsonObject
        val darkMode = (displayObj?.get("darkMode") as? JsonPrimitive)?.booleanOrNull
        val colorMode = when (darkMode) {
            true -> IosColorMode.DARK
            false -> IosColorMode.LIGHT
            null -> IosColorMode.SYSTEM
        }
        val restoredAppearance = currentState.appearance.copy(
            themeId = themeId.ifBlank { currentState.appearance.themeId },
            colorMode = colorMode,
        )

        val updatedState = currentState.copy(
            appearance = restoredAppearance,
            assistants = restoredAssistants.ifEmpty { currentState.assistants },
            selectedAssistantId = restoredAssistants.firstOrNull()?.id ?: currentState.selectedAssistantId,
            providerConfigurations = restoredProviders.ifEmpty { currentState.providerConfigurations },
            provider = restoredProviders.firstOrNull() ?: currentState.provider,
            skills = restoredSkills.ifEmpty { currentState.skills },
            lorebooks = restoredLorebooks.ifEmpty { currentState.lorebooks },
            mcpServers = restoredMcpServers.ifEmpty { currentState.mcpServers },
            conversations = conversationsToRestore.ifEmpty { currentState.conversations },
            memories = memoriesToRestore.ifEmpty { currentState.memories },
        )

        val result = IosRestoreResult(
            assistantsCount = restoredAssistants.size,
            providersCount = restoredProviders.size,
            skillsCount = restoredSkills.size,
            lorebooksCount = restoredLorebooks.size,
            mcpServersCount = restoredMcpServers.size,
            conversationsCount = conversationsToRestore.size,
            memoriesCount = memoriesToRestore.size,
            notes = listOf("Android backup settings successfully restored into iOS state."),
        )

        return updatedState to result
    }

    /**
     * Creates a standard LastChat backup ZIP archive matching Android's schema.
     */
    fun createBackupArchive(state: IosAppState): ByteArray {
        val settingsJsonObject = buildJsonObject {
            put("setupCompleted", true)
            put("dynamicColor", true)
            put("themeId", state.appearance.themeId)

            put("displaySetting", buildJsonObject {
                put("darkMode", state.appearance.colorMode == IosColorMode.DARK)
                put("fontSizeRatio", state.appearance.fontSizeRatio)
                put("showAssistantBubbles", state.appearance.showAssistantBubbles)
            })

            putJsonArray("assistants") {
                state.assistants.forEach { assistant ->
                    addJsonObject {
                        put("id", assistant.id)
                        put("name", assistant.name)
                        put("systemPrompt", assistant.systemPrompt)
                        put("memoryMode", assistant.memoryMode.name)
                        put("ragLimit", assistant.ragLimit)
                        put("ragSimilarityThreshold", assistant.ragSimilarityThreshold)
                        putJsonArray("localTools") {
                            assistant.localTools.forEach { tool ->
                                add(JsonPrimitive(tool.name))
                            }
                        }
                    }
                }
            }

            putJsonArray("providers") {
                state.providerConfigurations.forEach { prov ->
                    addJsonObject {
                        put("type", prov.type.name.lowercase())
                        put("baseUrl", prov.baseUrl)
                        putJsonArray("models") {
                            addJsonObject {
                                put("modelId", prov.modelId)
                                put("displayName", prov.modelId)
                            }
                        }
                    }
                }
            }

            putJsonArray("skills") {
                state.skills.forEach { skill ->
                    addJsonObject {
                        put("id", skill.id)
                        put("name", skill.name)
                        put("description", skill.description)
                        put("instructions", skill.instructions)
                        put("enabled", skill.enabled)
                        put("always_enabled", skill.alwaysEnabled)
                        put("injectionPosition", skill.injectionPosition.name)
                    }
                }
            }

            putJsonArray("lorebooks") {
                state.lorebooks.forEach { lb ->
                    addJsonObject {
                        put("id", lb.id)
                        put("name", lb.name)
                        put("description", lb.description)
                        put("enabled", lb.enabled)
                        putJsonArray("entries") {
                            lb.entries.forEach { entry ->
                                addJsonObject {
                                    put("id", entry.id)
                                    put("name", entry.name)
                                    put("prompt", entry.prompt)
                                    put("enabled", entry.enabled)
                                    put("activationType", entry.activationType.name)
                                    putJsonArray("keywords") {
                                        entry.keywords.forEach { add(JsonPrimitive(it)) }
                                    }
                                    put("caseSensitive", entry.caseSensitive)
                                    put("useRegex", entry.useRegex)
                                    put("scanDepth", entry.scanDepth)
                                }
                            }
                        }
                    }
                }
            }

            putJsonArray("mcpServers") {
                state.mcpServers.forEach { srv ->
                    addJsonObject {
                        put("id", srv.id)
                        put("url", srv.url)
                        put("type", if (srv is IosMcpServerConfig.StreamableHTTPServer) "streamable_http" else "sse")
                        put("commonOptions", buildJsonObject {
                            put("name", srv.commonOptions.name)
                            put("enable", srv.commonOptions.enable)
                            putJsonArray("tools") {
                                srv.commonOptions.tools.forEach { t ->
                                    addJsonObject {
                                        put("name", t.name)
                                        put("enable", t.enable)
                                        t.description?.let { put("description", it) }
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }

        val settingsJsonBytes = settingsJsonObject.toString().encodeToByteArray()
        val manifestJsonBytes = json.encodeToString(IosBackupManifest()).encodeToByteArray()
        val conversationsJsonBytes = json.encodeToString(state.conversations).encodeToByteArray()
        val memoriesJsonBytes = json.encodeToString(state.memories).encodeToByteArray()

        val entries = mapOf(
            "settings.json" to settingsJsonBytes,
            "backup_manifest.json" to manifestJsonBytes,
            "conversations.json" to conversationsJsonBytes,
            "memories.json" to memoriesJsonBytes,
        )

        return IosZipArchive.createStoredZip(entries)
    }

    /**
     * WebDAV synchronization support over PlatformHttpClient.
     */
    suspend fun testWebDav(client: PlatformHttpClient, config: IosWebDavConfig): Result<Unit> = runCatching {
        val targetUrl = config.url.trimEnd('/') + "/" + config.path.trim('/')
        val authHeader = buildBasicAuth(config.user, config.pass)
        val headers = listOfNotNull(
            "Depth" to "0",
            authHeader?.let { "Authorization" to it },
        ).toMap()
        val response = client.execute(
            PlatformHttpRequest(
                method = "PROPFIND",
                url = targetUrl,
                headers = headers,
            )
        )
        if (response.statusCode !in 200..299 && response.statusCode != 207) {
            error("WebDAV connection failed with HTTP ${response.statusCode}")
        }
    }

    suspend fun listWebDavBackups(
        client: PlatformHttpClient,
        config: IosWebDavConfig,
    ): Result<List<IosWebDavBackupItem>> = runCatching {
        val targetUrl = config.url.trimEnd('/') + "/" + config.path.trim('/')
        val authHeader = buildBasicAuth(config.user, config.pass)
        val headers = listOfNotNull(
            "Depth" to "1",
            authHeader?.let { "Authorization" to it },
        ).toMap()
        val response = client.execute(
            PlatformHttpRequest(
                method = "PROPFIND",
                url = targetUrl,
                headers = headers,
            )
        )
        if (response.statusCode !in 200..299 && response.statusCode != 207) {
            error("WebDAV list failed with HTTP ${response.statusCode}")
        }
        val xml = response.body.decodeToString()
        parseWebDavResponse(xml, targetUrl)
    }

    suspend fun backupToWebDav(
        client: PlatformHttpClient,
        config: IosWebDavConfig,
        state: IosAppState,
    ): Result<Unit> = runCatching {
        val zipBytes = createBackupArchive(state)
        val filename = "LastChat_backup_${Clock.System.now().toEpochMilliseconds()}.zip"
        val targetUrl = config.url.trimEnd('/') + "/" + config.path.trim('/') + "/" + filename
        val authHeader = buildBasicAuth(config.user, config.pass)
        val headers = listOfNotNull(
            "Content-Type" to "application/zip",
            authHeader?.let { "Authorization" to it },
        ).toMap()
        val response = client.execute(
            PlatformHttpRequest(
                method = "PUT",
                url = targetUrl,
                headers = headers,
                body = zipBytes,
            )
        )
        if (response.statusCode !in 200..299 && response.statusCode != 201 && response.statusCode != 204) {
            error("WebDAV upload failed with HTTP ${response.statusCode}")
        }
    }

    suspend fun restoreFromWebDav(
        client: PlatformHttpClient,
        config: IosWebDavConfig,
        itemHref: String,
        currentState: IosAppState,
    ): Result<Pair<IosAppState, IosRestoreResult>> = runCatching {
        val downloadUrl = if (itemHref.startsWith("http://") || itemHref.startsWith("https://")) {
            itemHref
        } else {
            config.url.trimEnd('/') + "/" + itemHref.trimStart('/')
        }
        val authHeader = buildBasicAuth(config.user, config.pass)
        val headers = listOfNotNull(
            authHeader?.let { "Authorization" to it },
        ).toMap()
        val response = client.execute(
            PlatformHttpRequest(
                method = "GET",
                url = downloadUrl,
                headers = headers,
            )
        )
        if (response.statusCode !in 200..299) {
            error("WebDAV download failed with HTTP ${response.statusCode}")
        }
        restore(response.body, currentState)
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun buildBasicAuth(user: String, pass: String): String? {
        if (user.isBlank()) return null
        val token = "$user:$pass"
        return "Basic " + kotlin.io.encoding.Base64.Default.encode(token.encodeToByteArray())
    }

    private fun parseWebDavResponse(xml: String, baseUrl: String): List<IosWebDavBackupItem> {
        val items = mutableListOf<IosWebDavBackupItem>()
        val hrefRegex = Regex("<(?:d:)?href>([^<]+)</(?:d:)?href>", RegexOption.IGNORE_CASE)
        val matches = hrefRegex.findAll(xml).toList()
        matches.forEach { match ->
            val href = match.groupValues[1].trim()
            val fileName = href.substringAfterLast('/').trim()
            if (fileName.endsWith(".zip", ignoreCase = true)) {
                items.add(
                    IosWebDavBackupItem(
                        href = href,
                        displayName = fileName,
                        size = 0L,
                        lastModifiedEpochMs = Clock.System.now().toEpochMilliseconds(),
                    )
                )
            }
        }
        return items
    }
}
