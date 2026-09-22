package me.rerere.ai.util

import kotlinx.coroutines.flow.SharedFlow
import kotlin.uuid.Uuid

/**
 * Represents an active key in a provider's key pool with its priority and metadata.
 */
data class PooledKey(
    val id: Uuid,
    val name: String,
    val value: String,
    val priority: Int,
    val providerId: Uuid = Uuid.NIL,
    val providerName: String = "",
)

/**
 * Outcome reported after making a request with an API key.
 */
sealed class KeyOutcome {
    data object Success : KeyOutcome()
    data class RateLimited(val retryAfterMs: Long? = null) : KeyOutcome()
    data class Error(val statusCode: Int, val temporary: Boolean = true) : KeyOutcome()
    data class AuthFailure(
        val statusCode: Int,
        val keyName: String = "",
        val providerId: Uuid = Uuid.NIL,
        val providerName: String = "",
        val errorMessage: String? = null
    ) : KeyOutcome()
    data class QuotaExhausted(
        val keyName: String = "",
        val providerId: Uuid = Uuid.NIL,
        val providerName: String = "",
        val errorMessage: String? = null
    ) : KeyOutcome()
}

/**
 * Notification event emitted when an API key receives an authentication error (401/403) or quota exhaustion (402).
 */
data class KeyAuthErrorEvent(
    val keyId: Uuid,
    val keyName: String,
    val providerId: Uuid,
    val providerName: String,
    val statusCode: Int,
    val message: String? = null,
)

/**
 * Runtime health state of an API key in the pool.
 */
data class KeyHealthState(
    val consecutiveErrors: Int = 0,
    val lastErrorTime: Long = 0,
    val cooldownUntil: Long = 0,
    val totalRequests: Long = 0,
    val totalErrors: Long = 0,
    val lastRateLimitTime: Long = 0,
    val rateLimitCooldownMs: Long = 0,
    val hasAuthError: Boolean = false,
    val hasQuotaError: Boolean = false,
    val lastAuthErrorMessage: String? = null,
) {
    val isHealthy: Boolean
        get() = !hasAuthError && !hasQuotaError && System.currentTimeMillis() >= cooldownUntil
}

/**
 * Smart router interface for selecting and health-tracking provider API keys.
 */
interface KeyRoulette {
    /** Legacy method: pick from comma/space-separated string */
    fun next(keys: String): String

    /** Pool-aware method: pick from a pool of named keys based on priority & health */
    fun next(keys: List<PooledKey>): PooledKey {
        return keys.firstOrNull() ?: PooledKey(Uuid.NIL, "default", next(""), 0)
    }

    /** Pool-aware method with provider ID and configuration strategy */
    fun next(
        keys: List<PooledKey>,
        providerId: Uuid = Uuid.NIL,
        config: me.rerere.ai.provider.KeyPoolConfig = me.rerere.ai.provider.KeyPoolConfig(),
    ): PooledKey {
        return next(keys)
    }

    /** Report the outcome of using a key to update health, backoffs, and cooldowns */
    fun reportOutcome(keyId: Uuid, outcome: KeyOutcome) {}

    /** Query current health state for a key (used in UI to highlight errors) */
    fun getKeyHealth(keyId: Uuid): KeyHealthState = KeyHealthState()

    /** Reset health state for a key (e.g. after user edits or re-tests the key) */
    fun resetKeyHealth(keyId: Uuid) {}

    /** Stream of authentication error events to trigger in-app popups */
    val authErrorEvents: SharedFlow<KeyAuthErrorEvent>
        get() = kotlinx.coroutines.flow.MutableSharedFlow()

    companion object {
        private val instance: KeyRoulette by lazy { SmartKeyRoulette() }

        fun default(): KeyRoulette = instance
    }
}
