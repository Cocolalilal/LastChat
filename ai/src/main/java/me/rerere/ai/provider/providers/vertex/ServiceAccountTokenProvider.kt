package me.rerere.ai.provider.providers.vertex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.http.urlEncode
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformJwtSigner
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * 使用服务账号（email + private key PEM）换取 Google OAuth2 Access Token。
 * 构造时传入 OkHttpClient；调用时传 email、私钥 PEM 与 scopes。
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalEncodingApi::class)
class ServiceAccountTokenProvider(
    private val http: PlatformHttpClient,
    private val jwtSigner: PlatformJwtSigner,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Token cache to avoid frequent token requests
    private val tokenCache = AtomicReference<Map<String, CachedToken>>(emptyMap())

    @Serializable
    private data class CachedToken(
        val token: String,
        val expiresAt: Long // Unix timestamp in seconds
    )

    /**
     * Generate cache key based on service account email and scopes
     */
    private fun generateCacheKey(serviceAccountEmail: String, scopes: List<String>): String {
        return "$serviceAccountEmail:${scopes.sorted().joinToString(",")}"
    }

    /**
     * Check if cached token is still valid (not expired with 5 minutes buffer)
     */
    private fun isCachedTokenValid(cachedToken: CachedToken): Boolean {
        val now = Clock.System.now().epochSeconds
        val bufferSeconds = 300 // 5 minutes buffer before actual expiration
        return cachedToken.expiresAt > (now + bufferSeconds)
    }

    /**
     * @param serviceAccountEmail  形如 xxx@project-id.iam.gserviceaccount.com
     * @param privateKeyPem        服务账号 JSON 中的 private_key 字段（PKCS#8 PEM, 含 -----BEGIN PRIVATE KEY-----）
     * @param scopes               OAuth scopes，默认 cloud-platform；多个 scope 用 List 传入
     * @return                     access token 字符串
     */
    suspend fun fetchAccessToken(
        serviceAccountEmail: String,
        privateKeyPem: String,
        scopes: List<String> = listOf("https://www.googleapis.com/auth/cloud-platform")
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(serviceAccountEmail, scopes)

        // Check cache first
        tokenCache.load()[cacheKey]?.let { cachedToken ->
            if (isCachedTokenValid(cachedToken)) {
                return@withContext cachedToken.token
            }
        }
        val now = Clock.System.now().epochSeconds
        val exp = now + 3600 // 最长 1h

        val headerJson = """{"alg":"RS256","typ":"JWT"}"""
        val claimJson = """{
          "iss":"$serviceAccountEmail",
          "scope":"${scopes.joinToString(" ")}",
          "aud":"https://oauth2.googleapis.com/token",
          "iat":$now,
          "exp":$exp
        }""".trimIndent()

        val headerB64 = base64UrlNoPad(headerJson.toByteArray(Charsets.UTF_8))
        val claimB64 = base64UrlNoPad(claimJson.toByteArray(Charsets.UTF_8))
        val signingInput = "$headerB64.$claimB64"

        val signature = jwtSigner.signRs256(signingInput.toByteArray(Charsets.UTF_8), privateKeyPem)
        val assertion = "$signingInput.${base64UrlNoPad(signature)}"

        val form = formUrlEncode(
            "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion" to assertion
        )

        val resp = http.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "https://oauth2.googleapis.com/token",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = form.encodeToByteArray(),
                mediaType = "application/x-www-form-urlencoded"
            )
        )
        val body = resp.body.decodeToString()
        if (resp.statusCode !in 200..299) {
            throw IllegalStateException("Token endpoint ${resp.statusCode}: $body")
        }
        val tokenResp = json.decodeFromString(TokenResponse.serializer(), body)
        val accessToken = tokenResp.accessToken ?: error("No access_token in response")

        // Cache the token with expiration time
        val expiresIn = tokenResp.expiresIn ?: 3600 // Default 1 hour if not provided
        val expiresAt = now + expiresIn
        cacheToken(cacheKey, CachedToken(accessToken, expiresAt))

        accessToken
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token")
        val accessToken: String? = null,
        @SerialName("token_type")
        val tokenType: String? = null,
        @SerialName("expires_in")
        val expiresIn: Long? = null
    )

    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.UrlSafe.encode(bytes).trimEnd('=')

    private fun formUrlEncode(vararg params: Pair<String, String>): String {
        return params.joinToString("&") { (name, value) ->
            "${name.urlEncode(spaceAsPlus = true)}=${value.urlEncode(spaceAsPlus = true)}"
        }
    }

    private fun cacheToken(cacheKey: String, token: CachedToken) {
        while (true) {
            val current = tokenCache.load()
            val next = current + (cacheKey to token)
            if (tokenCache.compareAndSet(current, next)) return
        }
    }
}
