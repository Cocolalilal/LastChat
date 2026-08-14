package me.rerere.rikkahub.data.codex

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.common.platform.android.await
import me.rerere.rikkahub.R
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class CodexOAuthManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val repository: CodexAccountRepository,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var callbackPort: Int? = null
    private val sessions = ConcurrentHashMap<String, OAuthSession>()
    private val _status = MutableStateFlow<CodexOAuthStatus>(CodexOAuthStatus.Idle)
    val status: StateFlow<CodexOAuthStatus> = _status.asStateFlow()

    fun startLogin() {
        val state = randomUrlSafe(32)
        try {
            val verifier = randomUrlSafe(64)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())
            )
            val port = ensureCallbackServer()
            val redirect = "http://localhost:$port/auth/callback"
            sessions[state] = OAuthSession(
                verifier = verifier,
                redirectUri = redirect,
            )
            _status.value = CodexOAuthStatus.Waiting

            val authUrl = Uri.parse(AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", redirect)
                .appendQueryParameter("scope", DEFAULT_SCOPES)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("id_token_add_organizations", "true")
                .appendQueryParameter("codex_cli_simplified_flow", "true")
                .appendQueryParameter("originator", "codex_cli_rs")
                .build()
            context.startActivity(
                Intent(Intent.ACTION_VIEW, authUrl).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (error: Throwable) {
            sessions.remove(state)
            _status.value = CodexOAuthStatus.Error(
                if (error.message == CALLBACK_PORTS_UNAVAILABLE) {
                    context.getString(R.string.codex_oauth_ports_unavailable)
                } else {
                    error.message ?: "Unable to open the OpenAI sign-in page"
                }
            )
        }
    }

    fun consumeResult() {
        _status.value = CodexOAuthStatus.Idle
    }

    @Synchronized
    private fun ensureCallbackServer(): Int {
        callbackPort?.let { return it }
        var lastError: Throwable? = null
        for (port in CALLBACK_PORTS) {
            try {
                server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
                    routing {
                        get("/auth/callback") {
                            val callbackState = call.request.queryParameters["state"]
                            val code = call.request.queryParameters["code"]
                            val error = call.request.queryParameters["error"]
                            val session = callbackState?.let(sessions::remove)
                            when {
                                session == null -> {
                                    _status.value = CodexOAuthStatus.Error("OAuth state mismatch")
                                    call.respondText(callbackPage(false, "OAuth state mismatch"), ContentType.Text.Html)
                                }

                                !error.isNullOrBlank() -> {
                                    _status.value = CodexOAuthStatus.Error(error)
                                    call.respondText(callbackPage(false, error), ContentType.Text.Html)
                                }

                                code.isNullOrBlank() -> {
                                    _status.value = CodexOAuthStatus.Error("Missing authorization code")
                                    call.respondText(callbackPage(false, "Missing authorization code"), ContentType.Text.Html)
                                }

                                else -> {
                                    try {
                                        val account = exchangeCode(code, session)
                                        _status.value = CodexOAuthStatus.Success(account.id)
                                        runCatching { repository.refreshAccount(account.id) }
                                        call.respondText(callbackPage(true), ContentType.Text.Html)
                                    } catch (error: Throwable) {
                                        Log.e(
                                            TAG,
                                            "OAuth token exchange failed: " +
                                                "${error::class.java.name}: ${error.message}",
                                            error,
                                        )
                                        val message = when (error) {
                                            is java.net.UnknownHostException ->
                                                context.getString(R.string.codex_oauth_dns_error)
                                            else -> error.message ?: "OAuth token exchange failed"
                                        }
                                        _status.value = CodexOAuthStatus.Error(message)
                                        call.respondText(callbackPage(false, message), ContentType.Text.Html)
                                    }
                                }
                            }
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

    private suspend fun exchangeCode(code: String, session: OAuthSession): CodexAccount {
        val response = client.newCall(
            Request.Builder()
                .url(TOKEN_URL)
                .post(
                    FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("client_id", CLIENT_ID)
                        .add("code", code)
                        .add("redirect_uri", session.redirectUri)
                        .add("code_verifier", session.verifier)
                        .build()
                )
                .build()
        ).await()
        val body = response.body.string()
        if (!response.isSuccessful) {
            error("Token exchange failed: ${response.code}")
        }
        return repository.saveLogin(body)
    }

    private fun callbackPage(success: Boolean, errorMessage: String? = null): String {
        val status = if (success) "success" else "error"
        val packageName = context.packageName
        val deepLink = "lastchat://codex/oauth?status=${URLEncoder.encode(status, Charsets.UTF_8.name())}"
        val intentUri = "intent://codex/oauth?status=${URLEncoder.encode(status, Charsets.UTF_8.name())}#Intent;scheme=lastchat;package=$packageName;end"
        val title = if (success) "Sign-in complete" else "Sign-in failed"
        val message = if (success) {
            "Successfully signed in to Codex. Returning to LastChat…"
        } else {
            errorMessage ?: "Return to LastChat to try again."
        }
        val statusColor = if (success) "#10b981" else "#ef4444"
        val statusIcon = if (success) "✓" else "✕"

        return """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>LastChat Codex sign-in</title>
                <style>
                  body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    background-color: #0f172a;
                    color: #f8fafc;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    margin: 0;
                    padding: 20px;
                    box-sizing: border-box;
                  }
                  .card {
                    background-color: #1e293b;
                    border-radius: 16px;
                    padding: 32px 24px;
                    max-width: 400px;
                    width: 100%;
                    text-align: center;
                    box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3);
                  }
                  .icon {
                    width: 56px;
                    height: 56px;
                    border-radius: 50%;
                    background-color: ${statusColor}22;
                    color: $statusColor;
                    font-size: 28px;
                    line-height: 56px;
                    margin: 0 auto 16px auto;
                    font-weight: bold;
                  }
                  h2 { margin: 0 0 8px 0; font-size: 20px; font-weight: 600; }
                  p { margin: 0 0 20px 0; color: #94a3b8; font-size: 14px; line-height: 1.5; }
                  a.btn {
                    display: block;
                    width: 100%;
                    padding: 12px 0;
                    background-color: #3b82f6;
                    color: #ffffff;
                    text-decoration: none;
                    font-weight: 600;
                    border-radius: 10px;
                    box-sizing: border-box;
                  }
                </style>
              </head>
              <body>
                <div class="card">
                  <div class="icon">$statusIcon</div>
                  <h2>$title</h2>
                  <p>$message</p>
                  <a href="$intentUri" class="btn">Return to LastChat</a>
                </div>
                <script>
                  setTimeout(function() {
                    try {
                      window.location.href = "$intentUri";
                    } catch (e) {
                      window.location.href = "$deepLink";
                    }
                  }, 300);
                </script>
              </body>
            </html>
        """.trimIndent()
    }

    private fun randomUrlSafe(size: Int): String {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val TAG = "CodexOAuthManager"
        private const val CALLBACK_PORTS_UNAVAILABLE = "OAuth callback ports are unavailable"
        const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val TOKEN_URL = "https://auth.openai.com/oauth/token"
        const val AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize"
        const val DEFAULT_SCOPES = "openid profile email offline_access"
        const val REFRESH_SCOPES = "openid profile email"
        private val CALLBACK_PORTS = listOf(1455, 1457)
    }
}

private data class OAuthSession(
    val verifier: String,
    val redirectUri: String,
)

sealed interface CodexOAuthStatus {
    data object Idle : CodexOAuthStatus
    data object Waiting : CodexOAuthStatus
    data class Success(val accountId: String) : CodexOAuthStatus
    data class Error(val message: String) : CodexOAuthStatus
}
