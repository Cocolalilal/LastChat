package me.rerere.ai.provider

import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.PooledKey
import kotlin.uuid.Uuid

/**
 * Build the effective pool of API keys for request auth.
 *
 * Order of preference:
 * 1. [ProviderSetting.resolvedApiKeyPool] entries with non-blank values
 *    (hydrated from SecureStore by SecretKeyManager)
 * 2. Inline [ProviderSetting.apiKeyPool] entry.key values (in-memory / export /
 *    provider-overwrite copies that lost the transient resolved pool)
 * 3. Empty — caller should fall back to the legacy apiKey string field
 *
 * This mirrors the balance-check fallback added in 0d0562f7 and prevents the
 * chat/stream path from sending an empty Bearer token labeled "default" when
 * resolvedApiKeyPool was lost by ProviderSetting.copy() / copyProvider().
 */
fun ProviderSetting.buildEffectiveApiKeyPool(): List<PooledKey> {
    val fromResolved = resolvedApiKeyPool.filter { it.value.isNotBlank() }
    if (fromResolved.isNotEmpty()) return fromResolved

    return apiKeyPool
        .filter { it.enabled && it.key.isNotBlank() }
        .mapIndexed { index, entry ->
            PooledKey(
                id = entry.id,
                name = entry.name.ifBlank { "Key ${index + 1}" },
                value = entry.key,
                priority = index,
                providerId = id,
                providerName = name,
            )
        }
}

fun ProviderSetting.legacyApiKey(): String = when (this) {
    is ProviderSetting.OpenAI -> apiKey
    is ProviderSetting.Google -> apiKey
    is ProviderSetting.Claude -> apiKey
    is ProviderSetting.ComfyUI -> ""
    is ProviderSetting.LiteRtLocal -> ""
}

fun ProviderSetting.withLegacyApiKey(newKey: String): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(apiKey = newKey)
    is ProviderSetting.Google -> copy(apiKey = newKey)
    is ProviderSetting.Claude -> copy(apiKey = newKey)
    is ProviderSetting.ComfyUI -> this
    is ProviderSetting.LiteRtLocal -> this
}

/**
 * Select a pooled key for an HTTP request. Never prefers a blank-valued resolved
 * pool entry over an inline apiKeyPool / legacy apiKey secret.
 */
fun KeyRoulette.selectProviderKey(providerSetting: ProviderSetting): PooledKey {
    val pool = providerSetting.buildEffectiveApiKeyPool()
    if (pool.isNotEmpty()) {
        return next(
            keys = pool,
            providerId = providerSetting.id,
            config = providerSetting.keyPoolConfig,
        )
    }
    return PooledKey(
        id = Uuid.NIL,
        name = "default",
        value = next(providerSetting.legacyApiKey()),
        priority = 0,
        providerId = providerSetting.id,
        providerName = providerSetting.name,
    )
}

/**
 * When a model uses providerOverwrite, merge missing credentials from the parent
 * provider. overwrite.copyProvider() drops the transient resolvedApiKeyPool and
 * often has an empty apiKey after pool migration of the parent.
 */
fun ProviderSetting.withInheritedCredentials(parent: ProviderSetting): ProviderSetting {
    val hasOwnSecret = buildEffectiveApiKeyPool().isNotEmpty() || legacyApiKey().isNotBlank()
    if (hasOwnSecret) return this

    var merged: ProviderSetting = this
    if (merged.apiKeyPool.isEmpty() && parent.apiKeyPool.isNotEmpty()) {
        merged = merged.withApiKeyPool(parent.apiKeyPool)
    }
    if (merged.legacyApiKey().isBlank() && parent.legacyApiKey().isNotBlank()) {
        merged = merged.withLegacyApiKey(parent.legacyApiKey())
    }
    if (merged.resolvedApiKeyPool.none { it.value.isNotBlank() } &&
        parent.resolvedApiKeyPool.any { it.value.isNotBlank() }
    ) {
        merged.resolvedApiKeyPool = parent.resolvedApiKeyPool
    }
    return merged
}
