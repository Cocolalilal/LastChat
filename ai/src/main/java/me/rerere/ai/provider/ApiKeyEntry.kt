package me.rerere.ai.provider

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Represents a single API key entry in a provider's key pool.
 *
 * Notice: The secret key string itself is NOT stored in serialized settings.
 * It is securely stored in Android's EncryptedSharedPreferences via SecretKeyManager
 * under `provider_apikey_pool_{providerId}_{id}`.
 */
@Serializable
data class ApiKeyEntry(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val enabled: Boolean = true,
    val exportable: Boolean = true,
    val key: String = "",
)

/**
 * Routing strategy for selecting keys within a provider's key pool.
 */
@Serializable
enum class KeyPoolStrategy {
    /**
     * Stays on the currently working key continuously. Only switches to another key
     * when the current one fails, avoiding returning to errored keys unless all options are exhausted.
     * (Default strategy)
     */
    STICKY_UNTIL_FAILURE,

    /**
     * Always attempts keys in priority order (first in list).
     * If higher-priority keys fail or rate-limit, fails over to lower keys.
     * Returns to primary key once cooldown expires.
     */
    PRIORITY_FAILOVER,

    /**
     * Cycles across available healthy keys on each request to distribute volume
     * and avoid hitting rate limits.
     */
    ROUND_ROBIN,
}

/**
 * Configuration for a provider's key pool routing behavior.
 */
@Serializable
data class KeyPoolConfig(
    val strategy: KeyPoolStrategy = KeyPoolStrategy.STICKY_UNTIL_FAILURE,
)


