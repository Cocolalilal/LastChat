package me.rerere.ai.provider

import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.PooledKey
import me.rerere.ai.util.SmartKeyRoulette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ApiKeyResolutionTest {
    private val roulette: KeyRoulette = SmartKeyRoulette()

    @Test
    fun selectProviderKey_prefersResolvedPoolWithValues() {
        val entryId = Uuid.random()
        val provider = ProviderSetting.OpenAI(
            apiKey = "",
            apiKeyPool = listOf(ApiKeyEntry(id = entryId, name = "Primary", key = "")),
        ).also {
            it.resolvedApiKeyPool = listOf(
                PooledKey(entryId, "Primary", "sk-resolved", 0, it.id, it.name)
            )
        }

        val selected = roulette.selectProviderKey(provider)
        assertEquals("sk-resolved", selected.value)
        assertEquals("Primary", selected.name)
    }

    @Test
    fun selectProviderKey_fallsBackToInlinePoolWhenResolvedLost() {
        // Simulates ProviderSetting.copy() dropping the transient resolvedApiKeyPool
        // while in-memory entry.key still holds the secret (populateSecrets path).
        val entryId = Uuid.random()
        val original = ProviderSetting.OpenAI(
            apiKey = "",
            apiKeyPool = listOf(ApiKeyEntry(id = entryId, name = "Primary", key = "sk-inline")),
        ).also {
            it.resolvedApiKeyPool = listOf(
                PooledKey(entryId, "Primary", "sk-inline", 0, it.id, it.name)
            )
        }
        val copied = original.copy(baseUrl = original.baseUrl) // drops resolvedApiKeyPool
        assertTrue(copied.resolvedApiKeyPool.isEmpty())

        val selected = roulette.selectProviderKey(copied)
        assertEquals("sk-inline", selected.value)
        assertEquals("Primary", selected.name)
    }

    @Test
    fun selectProviderKey_ignoresBlankResolvedEntries() {
        val entryId = Uuid.random()
        val provider = ProviderSetting.OpenAI(
            apiKey = "sk-legacy",
            apiKeyPool = listOf(ApiKeyEntry(id = entryId, name = "Primary", key = "")),
        ).also {
            // Non-empty resolved list but blank values (SecureStore miss) must not
            // block the legacy apiKey fallback.
            it.resolvedApiKeyPool = listOf(
                PooledKey(entryId, "Primary", "", 0, it.id, it.name)
            )
        }

        val selected = roulette.selectProviderKey(provider)
        assertEquals("sk-legacy", selected.value)
        assertEquals("default", selected.name)
    }

    @Test
    fun selectProviderKey_emptyEverythingUsesDefaultEmpty() {
        val provider = ProviderSetting.OpenAI(apiKey = "", apiKeyPool = emptyList())
        val selected = roulette.selectProviderKey(provider)
        assertEquals("", selected.value)
        assertEquals("default", selected.name)
    }

    @Test
    fun withInheritedCredentials_mergesParentSecretsIntoEmptyOverwrite() {
        val entryId = Uuid.random()
        val parent = ProviderSetting.OpenAI(
            name = "OpenRouter",
            apiKey = "",
            apiKeyPool = listOf(ApiKeyEntry(id = entryId, name = "Primary", key = "sk-parent")),
        ).also {
            it.resolvedApiKeyPool = listOf(
                PooledKey(entryId, "Primary", "sk-parent", 0, it.id, it.name)
            )
        }
        val overwrite = parent.copyProvider(id = Uuid.random(), models = emptyList()).let {
            // Simulate persisted overwrite after pool migration: empty credentials
            when (it) {
                is ProviderSetting.OpenAI -> it.copy(apiKey = "", apiKeyPool = emptyList())
                else -> it
            }
        }
        assertTrue(overwrite.buildEffectiveApiKeyPool().isEmpty())
        assertEquals("", overwrite.legacyApiKey())

        val merged = overwrite.withInheritedCredentials(parent)
        val selected = roulette.selectProviderKey(merged)
        assertEquals("sk-parent", selected.value)
    }

    @Test
    fun googleSelectKey_usesInlinePoolAfterCopy() {
        val entryId = Uuid.random()
        val original = ProviderSetting.Google(
            apiKey = "",
            apiKeyPool = listOf(ApiKeyEntry(id = entryId, name = "Primary", key = "AIza-test")),
        ).also {
            it.resolvedApiKeyPool = listOf(
                PooledKey(entryId, "Primary", "AIza-test", 0, it.id, it.name)
            )
        }
        val copied = original.copy(name = original.name)
        assertTrue(copied.resolvedApiKeyPool.isEmpty())
        assertEquals("AIza-test", roulette.selectProviderKey(copied).value)
    }
}
