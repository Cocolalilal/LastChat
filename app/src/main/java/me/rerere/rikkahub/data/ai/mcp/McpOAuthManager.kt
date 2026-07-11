package me.rerere.rikkahub.data.ai.mcp

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
private const val REDIRECT_BASE = "lastchat://mcp/oauth"
private const val TOKEN_REFRESH_SKEW_SECONDS = 60L

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
)

class McpOAuthManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: PlatformHttpClient,
    private val settingsStore: SettingsStore,
    private val secretKeyManager: SecretKeyManager,
) {
    private val _statuses = MutableStateFlow<Map<Uuid, McpOAuthStatus>>(emptyMap())
    val statuses: StateFlow<Map<Uuid, McpOAuthStatus>> = _statuses.asStateFlow()

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
        if (uri?.scheme != "lastchat" || uri.host != "mcp") return
        val serverId = uri.lastPathSegment
            ?.takeUnless { it == "oauth" }
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            ?: return

        scope.launch(Dispatchers.IO) {
            runCatching { finishAuthorization(serverId, uri) }
                .onFailure { error ->
                    Log.e(TAG, "Unable to finish MCP authorization", error)
                    setStatus(serverId, McpOAuthStatus.Error(error.message ?: "OAuth sign-in failed"))
                }
        }
    }

    fun hasCredentials(serverId: Uuid): Boolean = loadTokens(serverId) != null

    fun authorizationHeaders(serverId: Uuid): Map<String, String> {
        val tokens = loadTokens(serverId) ?: return emptyMap()
        return mapOf("Authorization" to "${tokens.tokenType} ${tokens.accessToken}")
    }

    suspend fun refreshIfNeeded(serverId: Uuid) {
        val tokens = loadTokens(serverId) ?: return
        val expiresAt = tokens.expiresAtEpochSeconds ?: return
        if (Clock.System.now().epochSeconds + TOKEN_REFRESH_SKEW_SECONDS < expiresAt) return
        val refreshToken = tokens.refreshToken ?: return
        val pending = loadSession(serverId) ?: return

        val refreshed = requestTokens(
            endpoint = pending.tokenEndpoint,
            fields = buildMap {
                put("grant_type", "refresh_token")
                put("refresh_token", refreshToken)
                put("client_id", pending.clientId)
                pending.clientSecret?.let { put("client_secret", it) }
                put("resource", pending.resource)
            },
        )
        saveTokens(
            serverId,
            refreshed.copy(refreshToken = refreshed.refreshToken ?: refreshToken),
        )
    }

    fun disconnect(serverId: Uuid) {
        secretKeyManager.removeMcpOAuthSecrets(serverId)
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
        val redirectUri = "$REDIRECT_BASE/${config.id}"
        val registration = loadRegistration(config.id) ?: registerClient(
            endpoint = serverMetadata.registrationEndpoint
                ?: error("The authorization server does not support automatic client registration"),
            redirectUri = redirectUri,
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
                if (resourceMetadata.scopes.isNotEmpty()) {
                    appendQueryParameter("scope", resourceMetadata.scopes.joinToString(" "))
                }
            }
            .build()

        setStatus(config.id, McpOAuthStatus.WaitingForUser)
        withContext(Dispatchers.Main) { context.openUrl(authUrl.toString()) }
    }

    private suspend fun finishAuthorization(serverId: Uuid, uri: Uri) {
        uri.getQueryParameter("error")?.let { error ->
            val description = uri.getQueryParameter("error_description")
            error(description ?: error)
        }
        val session = loadSession(serverId) ?: error("The sign-in session has expired")
        require(uri.getQueryParameter("state") == session.state) {
            "OAuth state validation failed"
        }
        uri.getQueryParameter("iss")?.let { returnedIssuer ->
            require(returnedIssuer == session.issuer) { "OAuth issuer validation failed" }
        }
        val code = uri.getQueryParameter("code") ?: error("The authorization code is missing")
        setStatus(serverId, McpOAuthStatus.ExchangingCode)

        val tokens = requestTokens(
            endpoint = session.tokenEndpoint,
            fields = buildMap {
                put("grant_type", "authorization_code")
                put("code", code)
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
        setStatus(serverId, McpOAuthStatus.Connected)
    }

    private suspend fun discoverProtectedResource(url: String): ProtectedResourceMetadata {
        val uri = Uri.parse(url)
        require(uri.scheme == "https") { "OAuth MCP connections must use HTTPS" }
        val origin = "${uri.scheme}://${uri.authority}"
        val path = uri.path.orEmpty().trimEnd('/')
        val candidates = buildList {
            if (path.isNotBlank()) add("$origin/.well-known/oauth-protected-resource$path")
            add("$origin/.well-known/oauth-protected-resource")
        }
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
            add("$origin/.well-known/oauth-authorization-server")
            add("${issuerUrl.trimEnd('/')}/.well-known/openid-configuration")
        }
        val json = candidates.firstNotNullOfOrNull { candidate -> getJsonOrNull(candidate) }
            ?: error("The OAuth authorization server did not publish discovery metadata")
        return AuthorizationServerMetadata(
            issuer = json.string("issuer") ?: issuerUrl,
            authorizationEndpoint = json.string("authorization_endpoint")
                ?: error("OAuth metadata is missing the authorization endpoint"),
            tokenEndpoint = json.string("token_endpoint")
                ?: error("OAuth metadata is missing the token endpoint"),
            registrationEndpoint = json.string("registration_endpoint"),
        )
    }

    private suspend fun registerClient(
        endpoint: String,
        redirectUri: String,
    ): OAuthClientRegistration {
        val body = buildJsonObject {
            put("client_name", "LastChat")
            put("application_type", "native")
            put("token_endpoint_auth_method", "none")
            put("redirect_uris", JsonArray(listOf(JsonPrimitive(redirectUri))))
            put("grant_types", JsonArray(listOf(JsonPrimitive("authorization_code"), JsonPrimitive("refresh_token"))))
            put("response_types", JsonArray(listOf(JsonPrimitive("code"))))
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
        require(response.statusCode in 200..299) {
            "OAuth client registration failed (HTTP ${response.statusCode})"
        }
        val json = JsonInstant.parseToJsonElement(response.body.decodeToString()) as? JsonObject
            ?: error("OAuth client registration returned an invalid response")
        return OAuthClientRegistration(
            clientId = json.string("client_id") ?: error("OAuth registration did not return a client ID"),
            clientSecret = json.string("client_secret"),
        )
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
            tokenType = json.string("token_type") ?: "Bearer",
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

    private fun setStatus(serverId: Uuid, status: McpOAuthStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(serverId, status) }
    }

    private fun randomUrlSafe(size: Int): String {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

private fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull

private fun JsonObject.arrayStrings(key: String): List<String> =
    (get(key) as? JsonArray).orEmpty().mapNotNull { element ->
        (element as? JsonPrimitive)?.contentOrNull
    }
