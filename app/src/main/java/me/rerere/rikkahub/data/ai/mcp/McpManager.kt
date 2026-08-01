package me.rerere.rikkahub.data.ai.mcp

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import me.rerere.ai.core.InputSchema
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val TAG = "McpManager"

private class McpSession(initialConfig: McpServerConfig) {
    @Volatile
    var config: McpServerConfig = initialConfig

    @Volatile
    var client: Client? = null

    @Volatile
    var connectedKey: McpConnectionKey? = null

    val lifecycleMutex = Mutex()
}

internal data class McpConnectionKey(
    val transport: String,
    val url: String,
    val clientName: String,
    val headers: List<Pair<String, String>>,
    val hasOAuthCredentials: Boolean,
)

class McpManager(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val transportFactory: McpTransportFactory,
    private val oauthManager: McpOAuthManager,
) {
    private val sessions = ConcurrentHashMap<Uuid, McpSession>()
    val syncingStatus = MutableStateFlow<Map<Uuid, McpStatus>>(emptyMap())

    init {
        appScope.launch {
            settingsStore.settingsFlow
                .map { settings -> settings.mcpServers }
                .distinctUntilChanged()
                .collect(::reconcile)
        }
        appScope.launch {
            oauthManager.credentialChanges.collect { serverId ->
                val config = settingsStore.settingsFlow.value.mcpServers.find { it.id == serverId }
                    ?: return@collect
                if (config.commonOptions.enable && config.commonOptions.name.isNotBlank()) {
                    addClient(config)
                }
            }
        }
    }

    fun getClient(config: McpServerConfig): Client? = sessions[config.id]?.client

    fun getAllAvailableTools(): List<Pair<Uuid, McpTool>> {
        return getAvailableTools(settingsStore.settingsFlow.value.getCurrentAssistant())
    }

    fun getAvailableTools(assistant: Assistant): List<Pair<Uuid, McpTool>> {
        val settings = settingsStore.settingsFlow.value
        return settings.mcpServers
            .filter { it.commonOptions.enable && it.id in assistant.mcpServers }
            .flatMap { server ->
                server.commonOptions.tools
                    .filter { tool -> tool.enable }
                    .map { tool -> server.id to tool }
            }
    }

    suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): JsonElement {
        val configured = settingsStore.settingsFlow.value.mcpServers.find { it.id == serverId }
            ?: return JsonPrimitive("Failed to execute tool, because no such MCP server exists")
        oauthManager.refreshIfNeeded(serverId)
        if (sessions[serverId]?.client == null) addClient(configured)
        val client = sessions[serverId]?.client
            ?: return JsonPrimitive("Failed to execute tool, because the MCP server is not connected")

        Log.i(TAG, "callTool: $toolName / $args (server: ${configured.commonOptions.name})")
        val result = client.callTool(
            request = CallToolRequest(
                params = CallToolRequestParams(name = toolName, arguments = args),
            ),
            options = RequestOptions(timeout = 60.seconds),
        )
        return McpJson.encodeToJsonElement(result.content)
    }

    suspend fun addClient(configInput: McpServerConfig) {
        val desired = settingsStore.settingsFlow.value.mcpServers.find { it.id == configInput.id }
        if (desired == null || !desired.commonOptions.enable || desired.commonOptions.name.isBlank()) {
            removeClient(configInput)
            return
        }
        if (desired.commonOptions.authMode == McpAuthMode.OAUTH &&
            !oauthManager.hasCredentials(desired.id)
        ) {
            removeClient(desired)
            setStatus(desired.id, McpStatus.Idle)
            return
        }

        val session = sessions.computeIfAbsent(desired.id) { McpSession(desired) }
        session.config = desired
        connectSession(session, desired)
    }

    suspend fun syncAll() {
        sessions.values.toList().forEach { session ->
            if (session.client == null) {
                addClient(session.config)
            } else {
                syncSession(session)
            }
        }
    }

    suspend fun removeClient(config: McpServerConfig) {
        val session = sessions.remove(config.id)
        if (session != null) closeSession(session)
        syncingStatus.update { current -> current - config.id }
        if (settingsStore.settingsFlow.value.mcpServers.none { it.id == config.id }) {
            oauthManager.disconnect(config.id)
        }
    }

    fun getStatus(config: McpServerConfig): Flow<McpStatus> =
        syncingStatus.map { it[config.id] ?: McpStatus.Idle }.distinctUntilChanged()

    private fun reconcile(configs: List<McpServerConfig>) {
        val active = configs
            .filter {
                it.commonOptions.enable &&
                    it.commonOptions.name.isNotBlank() &&
                    (it.commonOptions.authMode != McpAuthMode.OAUTH || oauthManager.hasCredentials(it.id))
            }
            .associateBy { it.id }

        (sessions.keys - active.keys).forEach { id ->
            val detached = sessions.remove(id) ?: return@forEach
            syncingStatus.update { current -> current - id }
            if (configs.none { it.id == id }) oauthManager.disconnect(id)
            appScope.launch { closeSession(detached) }
        }

        active.values.forEach { newConfig ->
            val existing = sessions[newConfig.id]
            if (existing == null) {
                val session = McpSession(newConfig)
                if (sessions.putIfAbsent(newConfig.id, session) == null) {
                    appScope.launch { addClient(newConfig) }
                }
                return@forEach
            }

            val mustReconnect = existing.config.connectionKey(oauthManager.hasCredentials(existing.config.id)) !=
                newConfig.connectionKey(oauthManager.hasCredentials(newConfig.id))
            existing.config = newConfig
            if (mustReconnect) appScope.launch { addClient(newConfig) }
        }
    }

    private suspend fun connectSession(session: McpSession, requested: McpServerConfig) =
        withContext(Dispatchers.IO) {
            session.lifecycleMutex.withLock {
                if (sessions[requested.id] !== session) return@withLock

                val latest = settingsStore.settingsFlow.value.mcpServers.find { it.id == requested.id }
                    ?: return@withLock
                if (!latest.commonOptions.enable || latest.commonOptions.name.isBlank()) return@withLock
                session.config = latest

                oauthManager.refreshIfNeeded(latest.id)
                val connectionKey = latest.connectionKey(oauthManager.hasCredentials(latest.id))
                if (session.client != null && session.connectedKey == connectionKey) return@withLock

                setStatus(latest.id, McpStatus.Connecting)
                val oldClient = session.client
                session.client = null
                session.connectedKey = null
                oldClient?.let { closeClient(it, latest.commonOptions.name) }

                var retriedAfterUnauthorized = false
                while (true) {
                    val sdkClient = Client(
                        clientInfo = Implementation(name = latest.commonOptions.name, version = "1.0")
                    )
                    try {
                        sdkClient.connect(transportFactory.create(latest))
                        val synced = syncTools(sdkClient, latest)
                        if (sessions[latest.id] !== session) {
                            closeClient(sdkClient, latest.commonOptions.name)
                            return@withLock
                        }

                        session.config = synced
                        session.connectedKey = synced.connectionKey(oauthManager.hasCredentials(synced.id))
                        session.client = sdkClient
                        setStatus(latest.id, McpStatus.Connected)
                        Log.i(TAG, "Connected MCP server ${latest.id} (${latest.commonOptions.name})")
                        break
                    } catch (error: CancellationException) {
                        closeClient(sdkClient, latest.commonOptions.name)
                        throw error
                    } catch (error: Exception) {
                        closeClient(sdkClient, latest.commonOptions.name)
                        val shouldRefresh = !retriedAfterUnauthorized &&
                            latest.commonOptions.authMode == McpAuthMode.OAUTH &&
                            error.isInvalidOAuthToken()
                        if (shouldRefresh) {
                            retriedAfterUnauthorized = true
                            val refreshed = runCatching {
                                oauthManager.refreshAfterUnauthorized(latest.id)
                            }.getOrElse { refreshError ->
                                Log.w(TAG, "Unable to refresh rejected OAuth token for ${latest.id}", refreshError)
                                false
                            }
                            if (refreshed) continue
                        }
                        if (retriedAfterUnauthorized &&
                            latest.commonOptions.authMode == McpAuthMode.OAUTH &&
                            error.isInvalidOAuthToken()
                        ) {
                            oauthManager.invalidateCredentials(latest.id)
                        }

                        val detail = error.message ?: error::class.qualifiedName ?: "MCP connection failed"
                        Log.e(TAG, "Failed to connect MCP server ${latest.id}", error)
                        setStatus(latest.id, McpStatus.Error(detail))
                        break
                    }
                }
            }
        }

    private suspend fun syncSession(session: McpSession) = withContext(Dispatchers.IO) {
        session.lifecycleMutex.withLock {
            val client = session.client ?: return@withLock
            val config = session.config
            setStatus(config.id, McpStatus.Connecting)
            try {
                val synced = syncTools(client, config)
                session.config = synced
                session.connectedKey = synced.connectionKey(oauthManager.hasCredentials(synced.id))
                setStatus(config.id, McpStatus.Connected)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to sync MCP server ${config.id}", error)
                setStatus(config.id, McpStatus.Error(error.message ?: "MCP sync failed"))
            }
        }
    }

    private suspend fun syncTools(client: Client, connectionConfig: McpServerConfig): McpServerConfig {
        val serverTools = client.listTools().tools
        Log.i(TAG, "Synced ${serverTools.size} tools from ${connectionConfig.commonOptions.name}")
        var updatedConfig = connectionConfig
        settingsStore.update { old ->
            old.copy(
                mcpServers = old.mcpServers.map { stored ->
                    if (stored.id != connectionConfig.id) return@map stored
                    val tools = mergeTools(stored.commonOptions.tools, serverTools)
                    stored.clone(commonOptions = stored.commonOptions.copy(tools = tools))
                        .also { updatedConfig = it }
                }
            )
        }
        return updatedConfig
    }

    private suspend fun closeSession(session: McpSession) = withContext(Dispatchers.IO) {
        session.lifecycleMutex.withLock {
            val client = session.client
            session.client = null
            session.connectedKey = null
            client?.let { closeClient(it, session.config.commonOptions.name) }
        }
    }

    private suspend fun closeClient(client: Client, name: String) {
        runCatching { client.close() }
            .onFailure { Log.w(TAG, "Failed to close MCP client $name", it) }
    }

    private fun setStatus(id: Uuid, status: McpStatus) {
        syncingStatus.update { current -> current + (id to status) }
    }

}

@OptIn(ExperimentalSerializationApi::class)
internal val McpJson: Json by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        explicitNulls = false
    }
}

private fun mergeTools(storedTools: List<McpTool>, serverTools: List<Tool>): List<McpTool> {
    val storedByName = storedTools.associateBy { it.name }
    return serverTools.map { serverTool ->
        storedByName[serverTool.name]?.copy(
            description = serverTool.description,
            inputSchema = serverTool.inputSchema.toSchema(),
        ) ?: McpTool(
            name = serverTool.name,
            description = serverTool.description,
            enable = true,
            inputSchema = serverTool.inputSchema.toSchema(),
        )
    }
}

private fun ToolSchema.toSchema(): InputSchema = InputSchema.Obj(
    properties = properties ?: JsonObject(emptyMap()),
    required = required,
)

internal fun McpServerConfig.connectionKey(hasOAuthCredentials: Boolean): McpConnectionKey {
    val hasManualAuthorization = commonOptions.headers.any {
        it.first.equals("Authorization", ignoreCase = true)
    }
    return McpConnectionKey(
        transport = when (this) {
            is McpServerConfig.SseTransportServer -> "sse"
            is McpServerConfig.StreamableHTTPServer -> "http"
        },
        url = when (this) {
            is McpServerConfig.SseTransportServer -> url
            is McpServerConfig.StreamableHTTPServer -> url
        },
        clientName = commonOptions.name,
        headers = commonOptions.headers,
        hasOAuthCredentials = hasOAuthCredentials && !hasManualAuthorization,
    )
}

internal fun Throwable.isInvalidOAuthToken(): Boolean =
    generateSequence(this) { it.cause }.any { error ->
        (error is StreamableHttpError && error.code == 401) ||
            error.message?.contains("invalid_token", ignoreCase = true) == true
    }
