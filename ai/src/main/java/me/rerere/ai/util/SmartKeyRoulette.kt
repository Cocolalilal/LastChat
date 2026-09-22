package me.rerere.ai.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.rerere.ai.provider.KeyPoolConfig
import me.rerere.ai.provider.KeyPoolStrategy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.uuid.Uuid

/**
 * Advanced smart routing engine for API key pools.
 *
 * Capabilities:
 * - Strategy routing:
 *   - STICKY_UNTIL_FAILURE (default): Reuses current healthy key; fails over to next on error.
 *   - PRIORITY_FAILOVER: Sequential by priority (1 -> 2 -> ...), returns to 1 once healthy.
 *   - ROUND_ROBIN: Cycles continuously across healthy keys to distribute load and rate limits.
 * - Health tracking (successes, errors, rate limits, latency)
 * - Automatic exponential backoff for 429 rate limits (respecting Retry-After if provided)
 * - Temporary failover on 5xx server errors
 * - Auth failure (401/403) cooldown + event emission for in-app warning popup
 * - Quota exhaustion (402) cooldown + event emission
 * - Auto-recovery once a key returns a successful response
 */
class SmartKeyRoulette : KeyRoulette {
    private val healthMap = ConcurrentHashMap<Uuid, KeyHealthState>()
    private val activeKeyByProvider = ConcurrentHashMap<Uuid, Uuid>()
    private val roundRobinIndexByProvider = ConcurrentHashMap<Uuid, AtomicInteger>()

    private val _authErrorEvents = MutableSharedFlow<KeyAuthErrorEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val authErrorEvents: SharedFlow<KeyAuthErrorEvent> = _authErrorEvents.asSharedFlow()

    override fun next(keys: List<PooledKey>): PooledKey {
        return next(keys, Uuid.NIL, KeyPoolConfig())
    }

    override fun next(
        keys: List<PooledKey>,
        providerId: Uuid,
        config: KeyPoolConfig,
    ): PooledKey {
        if (keys.isEmpty()) {
            return PooledKey(Uuid.NIL, "default", "", 0, providerId = providerId)
        }
        if (keys.size == 1) {
            return keys.first()
        }

        val healthyKeys = keys.filter { key ->
            getKeyHealth(key.id).isHealthy
        }

        return when (config.strategy) {
            KeyPoolStrategy.STICKY_UNTIL_FAILURE -> {
                val currentActiveKeyId = activeKeyByProvider[providerId]
                val currentActiveKey = healthyKeys.firstOrNull { it.id == currentActiveKeyId }
                if (currentActiveKey != null) {
                    currentActiveKey
                } else {
                    val nextKey = if (healthyKeys.isNotEmpty()) {
                        healthyKeys.minByOrNull { it.priority } ?: healthyKeys.first()
                    } else {
                        // All keys are currently in cooldown or error: pick key with earliest cooldown expiry
                        keys.minByOrNull { getKeyHealth(it.id).cooldownUntil } ?: keys.first()
                    }
                    activeKeyByProvider[providerId] = nextKey.id
                    nextKey
                }
            }

            KeyPoolStrategy.PRIORITY_FAILOVER -> {
                if (healthyKeys.isNotEmpty()) {
                    healthyKeys.minByOrNull { it.priority } ?: healthyKeys.first()
                } else {
                    keys.minByOrNull { getKeyHealth(it.id).cooldownUntil } ?: keys.first()
                }
            }

            KeyPoolStrategy.ROUND_ROBIN -> {
                val candidateList = if (healthyKeys.isNotEmpty()) healthyKeys else keys
                val counter = roundRobinIndexByProvider.computeIfAbsent(providerId) { AtomicInteger(0) }
                val index = Math.floorMod(counter.getAndIncrement(), candidateList.size)
                candidateList[index]
            }
        }
    }

    override fun reportOutcome(keyId: Uuid, outcome: KeyOutcome) {
        val now = System.currentTimeMillis()
        healthMap.compute(keyId) { _, current ->
            val prev = current ?: KeyHealthState()
            when (outcome) {
                is KeyOutcome.Success -> {
                    prev.copy(
                        consecutiveErrors = 0,
                        cooldownUntil = 0L,
                        hasAuthError = false,
                        hasQuotaError = false,
                        lastAuthErrorMessage = null,
                        totalRequests = prev.totalRequests + 1,
                    )
                }

                is KeyOutcome.RateLimited -> {
                    val consecutive = prev.consecutiveErrors + 1
                    val backoffMs = outcome.retryAfterMs?.takeIf { it > 0 }
                        ?: calculateExponentialBackoff(consecutive)
                    prev.copy(
                        consecutiveErrors = consecutive,
                        lastErrorTime = now,
                        lastRateLimitTime = now,
                        rateLimitCooldownMs = backoffMs,
                        cooldownUntil = now + backoffMs,
                        totalRequests = prev.totalRequests + 1,
                        totalErrors = prev.totalErrors + 1,
                    )
                }

                is KeyOutcome.Error -> {
                    val consecutive = prev.consecutiveErrors + 1
                    when (outcome.statusCode) {
                        402 -> {
                            val cooldownMs = QUOTA_ERROR_COOLDOWN_MS
                            _authErrorEvents.tryEmit(
                                KeyAuthErrorEvent(
                                    keyId = keyId,
                                    keyName = "",
                                    providerId = Uuid.NIL,
                                    providerName = "",
                                    statusCode = 402,
                                    message = "Quota exhausted",
                                )
                            )
                            prev.copy(
                                consecutiveErrors = consecutive,
                                lastErrorTime = now,
                                hasQuotaError = true,
                                cooldownUntil = now + cooldownMs,
                                totalRequests = prev.totalRequests + 1,
                                totalErrors = prev.totalErrors + 1,
                            )
                        }

                        401, 403 -> {
                            val cooldownMs = AUTH_ERROR_COOLDOWN_MS
                            _authErrorEvents.tryEmit(
                                KeyAuthErrorEvent(
                                    keyId = keyId,
                                    keyName = "",
                                    providerId = Uuid.NIL,
                                    providerName = "",
                                    statusCode = outcome.statusCode,
                                    message = "Authentication error",
                                )
                            )
                            prev.copy(
                                consecutiveErrors = consecutive,
                                lastErrorTime = now,
                                hasAuthError = true,
                                cooldownUntil = now + cooldownMs,
                                totalRequests = prev.totalRequests + 1,
                                totalErrors = prev.totalErrors + 1,
                            )
                        }

                        else -> {
                            val cooldownMs = if (outcome.temporary) {
                                min(MAX_SERVER_ERROR_COOLDOWN_MS, BASE_SERVER_ERROR_COOLDOWN_MS * consecutive)
                            } else {
                                0L
                            }
                            prev.copy(
                                consecutiveErrors = consecutive,
                                lastErrorTime = now,
                                cooldownUntil = if (cooldownMs > 0) now + cooldownMs else prev.cooldownUntil,
                                totalRequests = prev.totalRequests + 1,
                                totalErrors = prev.totalErrors + 1,
                            )
                        }
                    }
                }

                is KeyOutcome.AuthFailure -> {
                    val consecutive = prev.consecutiveErrors + 1
                    val cooldownMs = AUTH_ERROR_COOLDOWN_MS
                    _authErrorEvents.tryEmit(
                        KeyAuthErrorEvent(
                            keyId = keyId,
                            keyName = outcome.keyName,
                            providerId = outcome.providerId,
                            providerName = outcome.providerName,
                            statusCode = outcome.statusCode,
                            message = outcome.errorMessage,
                        )
                    )
                    prev.copy(
                        consecutiveErrors = consecutive,
                        lastErrorTime = now,
                        hasAuthError = true,
                        lastAuthErrorMessage = outcome.errorMessage,
                        cooldownUntil = now + cooldownMs,
                        totalRequests = prev.totalRequests + 1,
                        totalErrors = prev.totalErrors + 1,
                    )
                }

                is KeyOutcome.QuotaExhausted -> {
                    val consecutive = prev.consecutiveErrors + 1
                    val cooldownMs = QUOTA_ERROR_COOLDOWN_MS
                    _authErrorEvents.tryEmit(
                        KeyAuthErrorEvent(
                            keyId = keyId,
                            keyName = outcome.keyName,
                            providerId = outcome.providerId,
                            providerName = outcome.providerName,
                            statusCode = 402,
                            message = outcome.errorMessage ?: "Quota exhausted",
                        )
                    )
                    prev.copy(
                        consecutiveErrors = consecutive,
                        lastErrorTime = now,
                        hasQuotaError = true,
                        lastAuthErrorMessage = outcome.errorMessage,
                        cooldownUntil = now + cooldownMs,
                        totalRequests = prev.totalRequests + 1,
                        totalErrors = prev.totalErrors + 1,
                    )
                }
            }
        }
    }

    override fun getKeyHealth(keyId: Uuid): KeyHealthState {
        return healthMap[keyId] ?: KeyHealthState()
    }

    override fun resetKeyHealth(keyId: Uuid) {
        healthMap.remove(keyId)
    }

    override fun next(keys: String): String {
        val keyList = splitKey(keys)
        if (keyList.isEmpty()) return keys
        if (keyList.size == 1) return keyList.first()

        val pooledKeys = keyList.mapIndexed { index, rawKey ->
            val syntheticId = Uuid.fromLongs(rawKey.hashCode().toLong(), index.toLong())
            PooledKey(
                id = syntheticId,
                name = "Key ${index + 1}",
                value = rawKey,
                priority = index
            )
        }
        return next(pooledKeys).value
    }

    private fun calculateExponentialBackoff(consecutiveErrors: Int): Long {
        val shift = min(consecutiveErrors - 1, 6)
        val factor = 1L shl shift
        return min(MAX_RATE_LIMIT_COOLDOWN_MS, BASE_RATE_LIMIT_COOLDOWN_MS * factor)
    }

    companion object {
        private const val BASE_RATE_LIMIT_COOLDOWN_MS = 5_000L
        private const val MAX_RATE_LIMIT_COOLDOWN_MS = 300_000L // 5 minutes
        private const val BASE_SERVER_ERROR_COOLDOWN_MS = 10_000L
        private const val MAX_SERVER_ERROR_COOLDOWN_MS = 120_000L // 2 minutes
        private const val AUTH_ERROR_COOLDOWN_MS = 900_000L // 15 minutes
        private const val QUOTA_ERROR_COOLDOWN_MS = 1_800_000L // 30 minutes
        private val SPLIT_KEY_REGEX = "[\\s,]+".toRegex()

        private fun splitKey(key: String): List<String> {
            return key
                .split(SPLIT_KEY_REGEX)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }
    }
}
