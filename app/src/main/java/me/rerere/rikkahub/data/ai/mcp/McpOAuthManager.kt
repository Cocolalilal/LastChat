package me.rerere.rikkahub.data.ai.mcp

import android.content.Context
import android.net.Uri
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.common.http.urlEncode
import me.rerere.common.http.urlHostOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.openUrl
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "McpOAuthManager"
private const val TOKEN_REFRESH_SKEW_SECONDS = 60L
private const val CALLBACK_PORTS_UNAVAILABLE = "OAuth callback ports are unavailable"
private const val NOTION_OAUTH_REDIRECT_URI = "lastchat://mcp-oauth-callback"
private val CALLBACK_PORTS = listOf(1460, 1461, 1462)

sealed class McpOAuthStatus {
    data object Idle : McpOAuthStatus()
    data object Discovering : McpOAuthStatus()
    data object WaitingForUser : McpOAuthStatus()
    data object ExchangingCode : McpOAuthStatus()
    data object Connected : McpOAuthStatus()
    data class Error(val message: String) : McpOAuthStatus()
}

@Serializable
private data class OAuthClientRegistration(
    val clientId: String,
    val clientSecret: String? = null,
    val redirectUri: String? = null,
    val registrationEndpoint: String? = null,
    val scope: String? = null,
    val tokenEndpointAuthMethod: String = "none",
)

@Serializable
private data class OAuthPendingSession(
    val state: String,
    val verifier: String,
    val redirectUri: String,
    val tokenEndpoint: String,
    val clientId: String,
    val clientSecret: String? = null,
    val resource: String,
    val issuer: String,
)

@Serializable
private data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresAtEpochSeconds: Long? = null,
    val scope: String? = null,
)

private data class ProtectedResourceMetadata(
    val resource: String,
    val authorizationServer: String,
    val scopes: List<String>,
)

private data class AuthorizationServerMetadata(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val registrationEndpoint: String?,
    val scopes: List<String>,
    val tokenEndpointAuthMethods: List<String>,
)

class McpOAuthManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: PlatformHttpClient,
    private val settingsStore: SettingsStore,
    private val secretKeyManager: SecretKeyManager,
) {
    private var callbackServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var callbackPort: Int? = null
    private val refreshLocks = mutableMapOf<Uuid, Mutex>()
    private val _statuses = MutableStateFlow<Map<Uuid, McpOAuthStatus>>(emptyMap())
    val statuses: StateFlow<Map<Uuid, McpOAuthStatus>> = _statuses.asStateFlow()
    private val _credentialChanges = MutableSharedFlow<Uuid>(extraBufferCapacity = 16)
    val credentialChanges: SharedFlow<Uuid> = _credentialChanges.asSharedFlow()

    fun startAuthorization(config: McpServerConfig) {
        scope.launch(Dispatchers.IO) {
            runCatching { beginAuthorization(config) }
                .onFailure { error ->
                    Log.e(TAG, "Unable to authorize ${config.commonOptions.name}", error)
                    setStatus(config.id, McpOAuthStatus.Error(error.message ?: "OAuth setup failed"))
                }
        }
    }

    fun handleRedirect(uri: Uri?) {
        if (uri?.scheme != "lastchat") return
        if (uri.host == "mcp" && uri.getQueryParameter("status") == "complete") return
        val returnedState = uri.getQueryParameter("state")
        val serverId = when (uri.host) {
            "mcp" -> uri.lastPathSegment
                ?.takeUnless { it == "oauth" }
                ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            "mcp-oauth-callback" -> settingsStore.settingsFlow.value.mcpServers
                .firstOrNull { config -> loadSession(config.id)?.state == returnedState }
                ?.id
            else -> null
        } ?: return

        scope.launch(Dispatchers.IO) {
            runCatching {
                finishAuthorization(
                    serverId = serverId,
                    code = uri.getQueryParameter("code"),
                    state = returnedState,
                    returnedIssuer = uri.getQueryParameter("iss"),
                    oauthError = uri.getQueryParameter("error"),
                    errorDescription = uri.getQueryParameter("error_description"),
                )
            }
                .onFailure { error ->
                    Log.e(TAG, "Unable to finish MCP authorization", error)
                    setStatus(serverId, McpOAuthStatus.Error(error.message ?: "OAuth sign-in failed"))
                }
        }
    }

    fun hasCredentials(serverId: Uuid): Boolean = loadTokens(serverId) != null

    fun authorizationHeaders(serverId: Uuid): Map<String, String> {
        val tokens = loadTokens(serverId) ?: return emptyMap()
        val authorizationScheme = tokens.tokenType.trim()
            .let { if (it.equals("bearer", ignoreCase = true)) "Bearer" else it }
            .ifBlank { "Bearer" }
        return mapOf("Authorization" to "$authorizationScheme ${tokens.accessToken.trim()}")
    }

    fun invalidateCredentials(serverId: Uuid) {
        clearTokens(serverId)
    }

    suspend fun refreshIfNeeded(serverId: Uuid) {
        refreshLock(serverId).withLock {
            // Re-read after acquiring the lock. Refresh tokens can rotate, so parallel tool
            // calls must not exchange the same token more than once.
            val tokens = loadTokens(serverId) ?: return@withLock
            val expiresAt = tokens.expiresAtEpochSeconds ?: return@withLock
            if (Clock.System.now().epochSeconds + TOKEN_REFRESH_SKEW_SECONDS < expiresAt) return@withLock
            refreshTokensLocked(serverId, tokens)
        }
    }

    /**
     * Refresh after the resource server rejects an access token, even when its local expiry
     * has not elapsed. OAuth resource servers can revoke access tokens early.
     */
    suspend fun refreshAfterUnauthorized(serverId: Uuid): Boolean =
        refreshLock(serverId).withLock {
            val tokens = loadTokens(serverId) ?: return@withLock false
            if (tokens.refreshToken.isNullOrBlank()) {
                clearTokens(serverId)
                return@withLock false
            }
            refreshTokensLocked(serverId, tokens)
            true
        }

    private suspend fun refreshTokensLocked(serverId: Uuid, tokens: OAuthTokens) {
        val refreshToken = tokens.refreshToken ?: run {
            clearTokens(serverId)
            error("MCP sign-in expired; please sign in again")
        }
        val pending = loadSession(serverId) ?: error("MCP sign-in metadata is missing; please sign in again")

        val refreshed = runCatching {
            requestTokens(
                endpoint = pending.tokenEndpoint,
                fields = buildMap {
                    put("grant_type", "refresh_token")
                    put("refresh_token", refreshToken)
                    put("client_id", pending.clientId)
                    pending.clientSecret?.let { put("client_secret", it) }
                    put("resource", pending.resource)
                    tokens.scope?.let { put("scope", it) }
                },
            )
        }.getOrElse { error ->
            if (error.message?.contains(Regex("HTTP (400|401)")) == true) {
                clearTokens(serverId)
            }
            throw error
        }
        saveTokens(
            serverId,
            refreshed.copy(refreshToken = refreshed.refreshToken ?: refreshToken),
        )
    }

    fun disconnect(serverId: Uuid) {
        secretKeyManager.removeMcpOAuthSecrets(serverId)
        synchronized(refreshLocks) { refreshLocks.remove(serverId) }
        setStatus(serverId, McpOAuthStatus.Idle)
    }

    private suspend fun beginAuthorization(config: McpServerConfig) {
        require(config.commonOptions.authMode == McpAuthMode.OAUTH) {
            "This connection does not support automatic OAuth setup"
        }
        setStatus(config.id, McpOAuthStatus.Discovering)

        val serverUrl = when (config) {
            is McpServerConfig.SseTransportServer -> config.url
            is McpServerConfig.StreamableHTTPServer -> config.url
        }
        val resourceMetadata = discoverProtectedResource(serverUrl)
        val serverMetadata = discoverAuthorizationServer(resourceMetadata.authorizationServer)
        val redirectUri = if (isNotionMcpResource(serverUrl)) {
            NOTION_OAUTH_REDIRECT_URI
        } else {
            "http://127.0.0.1:${ensureCallbackServer()}/mcp/oauth/${config.id}"
        }
        val registrationEndpoint = serverMetadata.registrationEndpoint
            ?: error("The authorization server does not support automatic client registration")
        val scopes = resourceMetadata.scopes.ifEmpty { serverMetadata.scopes }
        val registrationScope = scopes.joinToString(" ")
        val tokenEndpointAuthMethod = when {
            "none" in serverMetadata.tokenEndpointAuthMethods -> "none"
            "client_secret_post" in serverMetadata.tokenEndpointAuthMethods -> "client_secret_post"
            else -> error("The authorization server does not support a compatible token authentication method")
        }
        val registration = loadRegistration(config.id)
            ?.takeIf {
                it.redirectUri == redirectUri &&
                    it.registrationEndpoint == registrationEndpoint &&
                    it.scope == registrationScope &&
                    it.tokenEndpointAuthMethod == tokenEndpointAuthMethod
            }
            ?: registerClient(
                endpoint = registrationEndpoint,
                redirectUri = redirectUri,
                scope = scopes,
                tokenEndpointAuthMethod = tokenEndpointAuthMethod,
            ).also { saveRegistration(config.id, it) }

        val verifier = randomUrlSafe(64)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())
        )
        val state = randomUrlSafe(32)
        val session = OAuthPendingSession(
            state = state,
            verifier = verifier,
            redirectUri = redirectUri,
            tokenEndpoint = serverMetadata.tokenEndpoint,
            clientId = registration.clientId,
            clientSecret = registration.clientSecret,
            resource = resourceMetadata.resource,
            issuer = serverMetadata.issuer,
        )
        saveSession(config.id, session)

        val authUrl = Uri.parse(serverMetadata.authorizationEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", registration.clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("resource", resourceMetadata.resource)
            .apply {
                if (scopes.isNotEmpty()) {
                    appendQueryParameter("scope", scopes.joinToString(" "))
                }
            }
            .build()

        setStatus(config.id, McpOAuthStatus.WaitingForUser)
        withContext(Dispatchers.Main) { context.openUrl(authUrl.toString()) }
    }

    private suspend fun finishAuthorization(
        serverId: Uuid,
        code: String?,
        state: String?,
        returnedIssuer: String?,
        oauthError: String?,
        errorDescription: String?,
    ) {
        oauthError?.let { error ->
            error(errorDescription ?: error)
        }
        val session = loadSession(serverId) ?: error("The sign-in session has expired")
        require(state == session.state) {
            "OAuth state validation failed"
        }
        returnedIssuer?.let {
            require(returnedIssuer == session.issuer) { "OAuth issuer validation failed" }
        }
        val authorizationCode = code ?: error("The authorization code is missing")
        setStatus(serverId, McpOAuthStatus.ExchangingCode)

        val tokens = requestTokens(
            endpoint = session.tokenEndpoint,
            fields = buildMap {
                put("grant_type", "authorization_code")
                put("code", authorizationCode)
                put("redirect_uri", session.redirectUri)
                put("client_id", session.clientId)
                put("code_verifier", session.verifier)
                session.clientSecret?.let { put("client_secret", it) }
                put("resource", session.resource)
            },
        )
        saveTokens(serverId, tokens)
        settingsStore.update { settings ->
            settings.copy(
                mcpServers = settings.mcpServers.map { config ->
                    if (config.id == serverId) {
                        config.clone(commonOptions = config.commonOptions.copy(enable = true))
                    } else {
                        config
                    }
                }
            )
        }
        _credentialChanges.tryEmit(serverId)
        setStatus(serverId, McpOAuthStatus.Connected)
    }

    @Synchronized
    private fun ensureCallbackServer(): Int {
        callbackPort?.let { return it }
        var lastError: Throwable? = null
        for (port in CALLBACK_PORTS) {
            try {
                callbackServer = embeddedServer(CIO, host = "127.0.0.1", port = port) {
                    routing {
                        get("/mcp/oauth/{serverId}") {
                            val serverId = call.parameters["serverId"]
                                ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                            if (serverId == null) {
                                call.respondText(callbackPage(null, false), ContentType.Text.Html)
                                return@get
                            }
                            val result = runCatching {
                                finishAuthorization(
                                    serverId = serverId,
                                    code = call.request.queryParameters["code"],
                                    state = call.request.queryParameters["state"],
                                    returnedIssuer = call.request.queryParameters["iss"],
                                    oauthError = call.request.queryParameters["error"],
                                    errorDescription = call.request.queryParameters["error_description"],
                                )
                            }
                            result.exceptionOrNull()?.let { error ->
                                Log.e(TAG, "Unable to finish MCP authorization", error)
                                setStatus(serverId, McpOAuthStatus.Error(error.message ?: "OAuth sign-in failed"))
                            }
                            call.respondText(
                                callbackPage(serverId, result.isSuccess),
                                ContentType.Text.Html,
                            )
                        }
                    }
                }.start(wait = false)
                callbackPort = port
                return port
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException(CALLBACK_PORTS_UNAVAILABLE, lastError)
    }

    private fun callbackPage(serverId: Uuid?, success: Boolean): String {
        val deepLink = serverId?.let { "lastchat://mcp/oauth/$it?status=complete" }
        val heading = if (success) "Connection complete" else "Connection failed"
        val message = if (success) {
            "Returning to LastChat&hellip;"
        } else {
            "Return to LastChat and try signing in again."
        }
        val returnLink = deepLink?.let { "<p><a href=\"$it\">Return to LastChat</a></p>" }.orEmpty()
        val returnScript = deepLink?.let { "<script>window.location.replace(\"$it\");</script>" }.orEmpty()
        return """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>LastChat connection</title>
              </head>
              <body>
                <p>$heading</p>
                <p>$message</p>
                $returnLink
                $returnScript
              </body>
            </html>
        """.trimIndent()
    }

    private suspend fun discoverProtectedResource(url: String): ProtectedResourceMetadata {
        val uri = Uri.parse(url)
        require(uri.scheme == "https") { "OAuth MCP connections must use HTTPS" }
        val origin = "${uri.scheme}://${uri.authority}"
        val path = uri.path.orEmpty().trimEnd('/')
        val candidates = buildList {
            probeResourceMetadataUrl(url)?.let { add(it) }
            if (path.isNotBlank()) add("$origin/.well-known/oauth-protected-resource$path")
            add("$origin/.well-known/oauth-protected-resource")
        }.distinct()
        val json = candidates.firstNotNullOfOrNull { candidate -> getJsonOrNull(candidate) }
            ?: error("This server did not publish MCP OAuth discovery metadata")
        val authorizationServers = json.arrayStrings("authorization_servers")
        return ProtectedResourceMetadata(
            resource = json.string("resource") ?: url,
            authorizationServer = authorizationServers.firstOrNull()
                ?: error("OAuth discovery did not include an authorization server"),
            scopes = json.arrayStrings("scopes_supported"),
        )
    }

    private suspend fun discoverAuthorizationServer(issuerUrl: String): AuthorizationServerMetadata {
        val uri = Uri.parse(issuerUrl)
        require(uri.scheme == "https") { "The OAuth issuer must use HTTPS" }
        val origin = "${uri.scheme}://${uri.authority}"
        val path = uri.path.orEmpty().trim('/')
        val candidates = buildList {
            if (path.isNotBlank()) add("$origin/.well-known/oauth-authorization-server/$path")
            if (path.isNotBlank()) add("$origin/.well-known/openid-configuration/$path")
            if (path.isNotBlank()) add("$origin/$path/.well-known/openid-configuration")
            add("$origin/.well-known/oauth-authorization-server")
            add("$origin/.well-known/openid-configuration")
        }.distinct()
        val json = candidates.firstNotNullOfOrNull { candidate -> getJsonOrNull(candidate) }
            ?: error("The OAuth authorization server did not publish discovery metadata")
        return AuthorizationServerMetadata(
            issuer = json.string("issuer") ?: issuerUrl,
            authorizationEndpoint = json.string("authorization_endpoint")
                ?: error("OAuth metadata is missing the authorization endpoint"),
            tokenEndpoint = json.string("token_endpoint")
                ?: error("OAuth metadata is missing the token endpoint"),
            registrationEndpoint = json.string("registration_endpoint"),
            scopes = json.arrayStrings("scopes_supported"),
            tokenEndpointAuthMethods = json.arrayStrings("token_endpoint_auth_methods_supported")
                .ifEmpty { listOf("none") },
        )
    }

    private suspend fun registerClient(
        endpoint: String,
        redirectUri: String,
        scope: List<String>,
        tokenEndpointAuthMethod: String,
    ): OAuthClientRegistration {
        val clientNamesToTry = resolveOAuthClientNames(endpoint)
        var lastException: Throwable? = null

        for (clientName in clientNamesToTry) {
            val body = buildJsonObject {
                put("client_name", clientName)
                put("token_endpoint_auth_method", tokenEndpointAuthMethod)
                put("redirect_uris", JsonArray(listOf(JsonPrimitive(redirectUri))))
                put("grant_types", JsonArray(listOf(JsonPrimitive("authorization_code"), JsonPrimitive("refresh_token"))))
                put("response_types", JsonArray(listOf(JsonPrimitive("code"))))
                if (scope.isNotEmpty()) put("scope", scope.joinToString(" "))
            }
            val response = client.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = endpoint,
                    body = body.toString().encodeToByteArray(),
                    mediaType = "application/json",
                    headers = mapOf("Accept" to "application/json"),
                )
            )
            if (response.statusCode in 200..299) {
                val json = JsonInstant.parseToJsonElement(response.body.decodeToString()) as? JsonObject
                    ?: error("OAuth client registration returned an invalid response")
                return OAuthClientRegistration(
                    clientId = json.string("client_id") ?: error("OAuth registration did not return a client ID"),
                    clientSecret = json.string("client_secret"),
                    redirectUri = redirectUri,
                    registrationEndpoint = endpoint,
                    scope = scope.joinToString(" "),
                    tokenEndpointAuthMethod = tokenEndpointAuthMethod,
                )
            } else {
                val detail = response.body.decodeToString().take(300)
                lastException = IllegalStateException("OAuth client registration failed (HTTP ${response.statusCode}): $detail")
                if (response.statusCode !in listOf(400, 403)) {
                    break
                }
            }
        }
        throw lastException ?: IllegalStateException("OAuth client registration failed")
    }

    private suspend fun requestTokens(endpoint: String, fields: Map<String, String>): OAuthTokens {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode(spaceAsPlus = true)}=${value.urlEncode(spaceAsPlus = true)}"
        }
        val response = client.execute(
            PlatformHttpRequest(
                method = "POST",
                url = endpoint,
                headers = mapOf("Accept" to "application/json"),
                body = body.encodeToByteArray(),
                mediaType = "application/x-www-form-urlencoded",
            )
        )
        require(response.statusCode in 200..299) {
            val detail = response.body.decodeToString().take(300)
            "OAuth token exchange failed (HTTP ${response.statusCode}): $detail"
        }
        val json = JsonInstant.parseToJsonElement(response.body.decodeToString()) as? JsonObject
            ?: error("The OAuth token endpoint returned an invalid response")
        val expiresIn = json["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        return OAuthTokens(
            accessToken = json.string("access_token") ?: error("The OAuth response did not include an access token"),
            refreshToken = json.string("refresh_token"),
            tokenType = json.string("token_type")
                ?.let { if (it.equals("bearer", ignoreCase = true)) "Bearer" else it }
                ?: "Bearer",
            expiresAtEpochSeconds = expiresIn?.let { Clock.System.now().epochSeconds + it },
            scope = json.string("scope"),
        )
    }

    private suspend fun getJsonOrNull(url: String): JsonObject? {
        val response = client.execute(
            PlatformHttpRequest(
                method = "GET",
                url = url,
                headers = mapOf("Accept" to "application/json"),
            )
        )
        if (response.statusCode !in 200..299) return null
        return runCatching {
            JsonInstant.parseToJsonElement(response.body.decodeToString()) as? JsonObject
        }.getOrNull()
    }

    private suspend fun probeResourceMetadataUrl(serverUrl: String): String? {
        val response = runCatching {
            client.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = serverUrl,
                    headers = mapOf("Accept" to "application/json, text/event-stream"),
                )
            )
        }.getOrNull() ?: return null
        if (response.statusCode != 401) return null
        return parseMcpResourceMetadataHeader(response.headers)
    }

    private fun loadRegistration(serverId: Uuid): OAuthClientRegistration? =
        secretKeyManager.getMcpOAuthSecret(serverId, "client")
            ?.let { runCatching { JsonInstant.decodeFromString<OAuthClientRegistration>(it) }.getOrNull() }

    private fun saveRegistration(serverId: Uuid, registration: OAuthClientRegistration) {
        secretKeyManager.setMcpOAuthSecret(serverId, "client", JsonInstant.encodeToString(registration))
    }

    private fun loadSession(serverId: Uuid): OAuthPendingSession? =
        secretKeyManager.getMcpOAuthSecret(serverId, "session")
            ?.let { runCatching { JsonInstant.decodeFromString<OAuthPendingSession>(it) }.getOrNull() }

    private fun saveSession(serverId: Uuid, session: OAuthPendingSession) {
        secretKeyManager.setMcpOAuthSecret(serverId, "session", JsonInstant.encodeToString(session))
    }

    private fun loadTokens(serverId: Uuid): OAuthTokens? =
        secretKeyManager.getMcpOAuthSecret(serverId, "tokens")
            ?.let { runCatching { JsonInstant.decodeFromString<OAuthTokens>(it) }.getOrNull() }

    private fun saveTokens(serverId: Uuid, tokens: OAuthTokens) {
        secretKeyManager.setMcpOAuthSecret(serverId, "tokens", JsonInstant.encodeToString(tokens))
    }

    private fun clearTokens(serverId: Uuid) {
        secretKeyManager.setMcpOAuthSecret(serverId, "tokens", null)
        setStatus(serverId, McpOAuthStatus.Error("MCP sign-in expired; please sign in again"))
    }

    private fun setStatus(serverId: Uuid, status: McpOAuthStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(serverId, status) }
    }

    private fun randomUrlSafe(size: Int): String {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun refreshLock(serverId: Uuid): Mutex = synchronized(refreshLocks) {
        refreshLocks.getOrPut(serverId) { Mutex() }
    }
}

internal fun isNotionMcpResource(resource: String): Boolean =
    resource.urlHostOrNull().equals("mcp.notion.com", ignoreCase = true)

internal fun resolveOAuthClientNames(endpoint: String): List<String> = buildList {
    if (endpoint.contains("figma", ignoreCase = true)) {
        add("Claude")
        add("Cursor")
        add("VS Code")
        add("LastChat")
    } else {
        add("LastChat")
        add("Claude")
        add("Cursor")
    }
}.distinct()

internal fun parseMcpResourceMetadataHeader(headers: Map<String, List<String>>): String? {
    val challenge = headers.entries
        .filter { (name, _) -> name.equals("WWW-Authenticate", ignoreCase = true) }
        .flatMap { it.value }
        .joinToString(",")
    if (challenge.isBlank()) return null
    return Regex("""resource_metadata\s*=\s*(?:\"([^\"]+)\"|([^,\s]+))""", RegexOption.IGNORE_CASE)
        .find(challenge)
        ?.let { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }
        ?.takeIf { value -> value.startsWith("https://") }
}

private fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull

private fun JsonObject.arrayStrings(key: String): List<String> =
    (get(key) as? JsonArray).orEmpty().mapNotNull { element ->
        (element as? JsonPrimitive)?.contentOrNull
    }
