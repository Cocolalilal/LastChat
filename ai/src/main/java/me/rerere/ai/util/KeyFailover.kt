package me.rerere.ai.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.buildEffectiveApiKeyPool
import me.rerere.ai.provider.selectProviderKey
import me.rerere.ai.ui.MessageChunk
import me.rerere.common.platform.PlatformLog
import kotlin.uuid.Uuid

/**
 * Thrown when a provider HTTP request fails with a status that may warrant
 * trying another key from the pool.
 */
class KeyRequestException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isFailoverEligible: Boolean
        get() = isKeyFailoverStatus(statusCode)
}

fun isKeyFailoverStatus(statusCode: Int): Boolean =
    statusCode == 401 ||
        statusCode == 402 ||
        statusCode == 403 ||
        statusCode == 429 ||
        statusCode in 500..599

/**
 * Report a failed HTTP status against [key] and return whether the caller should
 * try another key for the same user request.
 */
fun KeyRoulette.reportHttpStatusFailure(
    key: PooledKey,
    providerSetting: ProviderSetting,
    statusCode: Int,
    errorBody: String?,
): Boolean {
    when (statusCode) {
        401, 403 -> reportOutcome(
            key.id,
            KeyOutcome.AuthFailure(
                statusCode = statusCode,
                keyName = key.name,
                providerId = providerSetting.id,
                providerName = providerSetting.name,
                errorMessage = errorBody,
            )
        )
        402 -> reportOutcome(
            key.id,
            KeyOutcome.QuotaExhausted(
                keyName = key.name,
                providerId = providerSetting.id,
                providerName = providerSetting.name,
                errorMessage = errorBody,
            )
        )
        429 -> reportOutcome(key.id, KeyOutcome.RateLimited())
        in 500..599 -> reportOutcome(
            key.id,
            KeyOutcome.Error(statusCode = statusCode, temporary = true)
        )
        else -> return false
    }
    return isKeyFailoverStatus(statusCode)
}

/**
 * Run [attempt] with automatic key failover on 401/403/402/429/5xx until success
 * or every distinct pool key has been tried for this request.
 */
suspend fun <T> withProviderKeyFailover(
    keyRoulette: KeyRoulette,
    providerSetting: ProviderSetting,
    logTag: String,
    attempt: suspend (PooledKey) -> T,
): T {
    val poolSize = providerSetting.buildEffectiveApiKeyPool().size.coerceAtLeast(1)
    val attempted = linkedSetOf<Uuid>()
    var lastError: Throwable? = null

    while (true) {
        val key = keyRoulette.selectProviderKey(providerSetting)
        if (!attempted.add(key.id)) {
            break
        }
        try {
            return attempt(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: KeyRequestException) {
            lastError = e
            if (!e.isFailoverEligible) throw e
            PlatformLog.w(
                logTag,
                "Key '${key.name}' failed with HTTP ${e.statusCode}; " +
                    "trying next healthy key (${attempted.size}/$poolSize tried)"
            )
        }
    }
    throw lastError
        ?: IllegalStateException("All API keys failed for provider ${providerSetting.name}")
}

/**
 * Stream with key failover. Only switches keys when the failure happens before the
 * first emitted chunk (partial streams must not restart mid-response).
 */
fun streamWithProviderKeyFailover(
    keyRoulette: KeyRoulette,
    providerSetting: ProviderSetting,
    logTag: String,
    openStream: (PooledKey) -> Flow<MessageChunk>,
): Flow<MessageChunk> = flow {
    val poolSize = providerSetting.buildEffectiveApiKeyPool().size.coerceAtLeast(1)
    val attempted = linkedSetOf<Uuid>()
    var lastError: Throwable? = null

    while (true) {
        val key = keyRoulette.selectProviderKey(providerSetting)
        if (!attempted.add(key.id)) {
            break
        }
        var emittedChunk = false
        try {
            openStream(key).collect { chunk ->
                emittedChunk = true
                emit(chunk)
            }
            return@flow
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            lastError = e
            if (emittedChunk) throw e
            val keyError = e as? KeyRequestException
            if (keyError?.isFailoverEligible != true) throw e
            PlatformLog.w(
                logTag,
                "Stream with key '${key.name}' failed before first chunk " +
                    "(HTTP ${keyError.statusCode}); " +
                    "trying next healthy key (${attempted.size}/$poolSize tried)"
            )
        }
    }
    throw lastError
        ?: IllegalStateException("All API keys failed for provider ${providerSetting.name}")
}
