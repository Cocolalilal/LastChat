package me.rerere.ai.util

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.KeyPoolConfig
import me.rerere.ai.provider.KeyPoolStrategy
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessageChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.uuid.Uuid

class SmartKeyRouletteTest {

    @Test
    fun `next with empty list returns default key`() {
        val roulette = SmartKeyRoulette()
        val key = roulette.next(emptyList())
        assertEquals("", key.value)
    }

    @Test
    fun `next with single key returns that key`() {
        val roulette = SmartKeyRoulette()
        val key = PooledKey(Uuid.random(), "Primary", "sk-test-1", 0)
        assertEquals("sk-test-1", roulette.next(listOf(key)).value)
    }

    @Test
    fun `next prioritizes primary key when healthy in PRIORITY_FAILOVER`() {
        val roulette = SmartKeyRoulette()
        val primary = PooledKey(Uuid.random(), "Primary", "sk-1", 0)
        val secondary = PooledKey(Uuid.random(), "Secondary", "sk-2", 1)
        val tertiary = PooledKey(Uuid.random(), "Tertiary", "sk-3", 2)

        val config = KeyPoolConfig(strategy = KeyPoolStrategy.PRIORITY_FAILOVER)
        val selected = roulette.next(listOf(primary, secondary, tertiary), Uuid.random(), config)
        assertEquals(primary.id, selected.id)
    }

    @Test
    fun `sticky strategy reuses active key until error then switches to next healthy`() {
        val roulette = SmartKeyRoulette()
        val providerId = Uuid.random()
        val key1 = PooledKey(Uuid.random(), "Key 1", "sk-1", 0)
        val key2 = PooledKey(Uuid.random(), "Key 2", "sk-2", 1)
        val config = KeyPoolConfig(strategy = KeyPoolStrategy.STICKY_UNTIL_FAILURE)

        // First call selects key1
        val first = roulette.next(listOf(key1, key2), providerId, config)
        assertEquals(key1.id, first.id)

        // Second call stays on key1
        val second = roulette.next(listOf(key1, key2), providerId, config)
        assertEquals(key1.id, second.id)

        // Key1 fails with error
        roulette.reportOutcome(key1.id, KeyOutcome.Error(statusCode = 500, temporary = true))

        // Sticky pointer for the failed key is cleared
        assertNull(roulette.getActiveKeyId(providerId))

        // Next call switches to key2
        val third = roulette.next(listOf(key1, key2), providerId, config)
        assertEquals(key2.id, third.id)

        // Subsequent call stays sticky on key2
        val fourth = roulette.next(listOf(key1, key2), providerId, config)
        assertEquals(key2.id, fourth.id)
    }

    @Test
    fun `round robin strategy cycles across healthy keys`() {
        val roulette = SmartKeyRoulette()
        val providerId = Uuid.random()
        val key1 = PooledKey(Uuid.random(), "Key 1", "sk-1", 0)
        val key2 = PooledKey(Uuid.random(), "Key 2", "sk-2", 1)
        val key3 = PooledKey(Uuid.random(), "Key 3", "sk-3", 2)
        val config = KeyPoolConfig(strategy = KeyPoolStrategy.ROUND_ROBIN)

        val keys = listOf(key1, key2, key3)
        val pick1 = roulette.next(keys, providerId, config)
        val pick2 = roulette.next(keys, providerId, config)
        val pick3 = roulette.next(keys, providerId, config)
        val pick4 = roulette.next(keys, providerId, config)

        assertEquals(key1.id, pick1.id)
        assertEquals(key2.id, pick2.id)
        assertEquals(key3.id, pick3.id)
        assertEquals(key1.id, pick4.id)
    }

    @Test
    fun `next fails over to secondary key when primary is rate limited`() {
        val roulette = SmartKeyRoulette()
        val primary = PooledKey(Uuid.random(), "Primary", "sk-1", 0)
        val secondary = PooledKey(Uuid.random(), "Secondary", "sk-2", 1)

        // Primary gets 429 rate limit
        roulette.reportOutcome(primary.id, KeyOutcome.RateLimited(retryAfterMs = 60_000L))

        val health = roulette.getKeyHealth(primary.id)
        assertFalse(health.isHealthy)
        assertTrue(health.cooldownUntil > System.currentTimeMillis())

        val selected = roulette.next(listOf(primary, secondary))
        assertEquals(secondary.id, selected.id)
    }

    @Test
    fun `quota exhausted 402 triggers cooldown and hasQuotaError`() {
        val roulette = SmartKeyRoulette()
        val primary = PooledKey(Uuid.random(), "Primary", "sk-1", 0)
        val secondary = PooledKey(Uuid.random(), "Secondary", "sk-2", 1)

        roulette.reportOutcome(
            primary.id,
            KeyOutcome.QuotaExhausted(
                keyName = "Primary",
                errorMessage = "Insufficient credits"
            )
        )

        val health = roulette.getKeyHealth(primary.id)
        assertTrue(health.hasQuotaError)
        assertFalse(health.isHealthy)

        // Should failover to secondary key
        val selected = roulette.next(listOf(primary, secondary))
        assertEquals(secondary.id, selected.id)

        // Success resets quota error
        roulette.reportOutcome(primary.id, KeyOutcome.Success)
        assertFalse(roulette.getKeyHealth(primary.id).hasQuotaError)
        assertTrue(roulette.getKeyHealth(primary.id).isHealthy)
    }

    @Test
    fun `success clears rate limit and consecutive errors`() {
        val roulette = SmartKeyRoulette()
        val primary = PooledKey(Uuid.random(), "Primary", "sk-1", 0)

        roulette.reportOutcome(primary.id, KeyOutcome.RateLimited(retryAfterMs = 60_000L))
        assertFalse(roulette.getKeyHealth(primary.id).isHealthy)

        roulette.reportOutcome(primary.id, KeyOutcome.Success)
        val health = roulette.getKeyHealth(primary.id)
        assertTrue(health.isHealthy)
        assertEquals(0, health.consecutiveErrors)
        assertEquals(0L, health.cooldownUntil)
    }

    @Test
    fun `auth failure sets hasAuthError and triggers cooldown`() {
        val roulette = SmartKeyRoulette()
        val primary = PooledKey(Uuid.random(), "Primary", "sk-1", 0)
        val secondary = PooledKey(Uuid.random(), "Secondary", "sk-2", 1)

        roulette.reportOutcome(
            primary.id,
            KeyOutcome.AuthFailure(
                statusCode = 401,
                keyName = "Primary",
                errorMessage = "Invalid API key"
            )
        )

        val health = roulette.getKeyHealth(primary.id)
        assertTrue(health.hasAuthError)
        assertFalse(health.isHealthy)

        // Routing fails over to secondary
        val selected = roulette.next(listOf(primary, secondary))
        assertEquals(secondary.id, selected.id)

        // Once primary succeeds, auth error clears
        roulette.reportOutcome(primary.id, KeyOutcome.Success)
        val updatedHealth = roulette.getKeyHealth(primary.id)
        assertFalse(updatedHealth.hasAuthError)
        assertTrue(updatedHealth.isHealthy)
    }

    @Test
    fun `legacy comma-separated string routing works with failover`() {
        val roulette = SmartKeyRoulette()
        val key = roulette.next("key1, key2, key3")
        assertTrue(key in listOf("key1", "key2", "key3"))
    }

    @Test
    fun `persistence restores health and sticky active key after reopen`() {
        val store = InMemoryKeyRouletteStore()
        val providerId = Uuid.random()
        val key1 = PooledKey(Uuid.random(), "Key 1", "sk-1", 0, providerId = providerId)
        val key2 = PooledKey(Uuid.random(), "Key 2", "sk-2", 1, providerId = providerId)
        val config = KeyPoolConfig(strategy = KeyPoolStrategy.STICKY_UNTIL_FAILURE)

        val firstSession = SmartKeyRoulette(store = store)
        val selected = firstSession.next(listOf(key1, key2), providerId, config)
        assertEquals(key1.id, selected.id)

        // Fail key1 — health + sticky clear are persisted
        firstSession.reportOutcome(key1.id, KeyOutcome.AuthFailure(statusCode = 401, keyName = "Key 1"))
        assertFalse(firstSession.getKeyHealth(key1.id).isHealthy)
        assertNull(firstSession.getActiveKeyId(providerId))

        // Stick to key2 so reopen remembers the working sticky key
        val afterFailover = firstSession.next(listOf(key1, key2), providerId, config)
        assertEquals(key2.id, afterFailover.id)
        assertEquals(key2.id, firstSession.getActiveKeyId(providerId))

        // Simulate process death / app reopen
        val secondSession = SmartKeyRoulette(store = store)
        assertFalse(secondSession.getKeyHealth(key1.id).isHealthy)
        assertTrue(secondSession.getKeyHealth(key1.id).hasAuthError)
        assertEquals(key2.id, secondSession.getActiveKeyId(providerId))

        val restored = secondSession.next(listOf(key1, key2), providerId, config)
        assertEquals(key2.id, restored.id)
    }

    @Test
    fun `absolute cooldownUntil survives restore across clock continuity`() {
        val store = InMemoryKeyRouletteStore()
        val keyId = Uuid.random()
        val session1 = SmartKeyRoulette(store = store)
        session1.reportOutcome(keyId, KeyOutcome.RateLimited(retryAfterMs = 120_000L))
        val cooldownUntil = session1.getKeyHealth(keyId).cooldownUntil
        assertTrue(cooldownUntil > System.currentTimeMillis())

        val session2 = SmartKeyRoulette(store = store)
        assertEquals(cooldownUntil, session2.getKeyHealth(keyId).cooldownUntil)
        assertFalse(session2.getKeyHealth(keyId).isHealthy)
    }

    @Test
    fun `withProviderKeyFailover retries next key on 401 then succeeds`() = runBlocking {
        val store = InMemoryKeyRouletteStore()
        val roulette = SmartKeyRoulette(store = store)
        val providerId = Uuid.random()
        val key1Id = Uuid.random()
        val key2Id = Uuid.random()
        val provider = ProviderSetting.OpenAI(
            id = providerId,
            name = "Test",
            apiKey = "",
            apiKeyPool = listOf(
                me.rerere.ai.provider.ApiKeyEntry(id = key1Id, name = "A", key = "sk-a"),
                me.rerere.ai.provider.ApiKeyEntry(id = key2Id, name = "B", key = "sk-b"),
            ),
            keyPoolConfig = KeyPoolConfig(strategy = KeyPoolStrategy.STICKY_UNTIL_FAILURE),
        )

        val tried = mutableListOf<String>()
        val result = withProviderKeyFailover(roulette, provider, "Test") { key ->
            tried.add(key.value)
            if (key.value == "sk-a") {
                roulette.reportHttpStatusFailure(key, provider, 401, "bad key")
                throw KeyRequestException(401, "unauthorized")
            }
            "ok:${key.value}"
        }

        assertEquals("ok:sk-b", result)
        assertEquals(listOf("sk-a", "sk-b"), tried)
        assertFalse(roulette.getKeyHealth(key1Id).isHealthy)
        assertEquals(key2Id, roulette.getActiveKeyId(providerId))
    }

    @Test
    fun `withProviderKeyFailover stops when pool exhausted`() = runBlocking {
        val roulette = SmartKeyRoulette()
        val provider = ProviderSetting.OpenAI(
            apiKey = "",
            apiKeyPool = listOf(
                me.rerere.ai.provider.ApiKeyEntry(name = "A", key = "sk-a"),
                me.rerere.ai.provider.ApiKeyEntry(name = "B", key = "sk-b"),
            ),
        )

        try {
            withProviderKeyFailover(roulette, provider, "Test") { key ->
                roulette.reportHttpStatusFailure(key, provider, 429, "rate")
                throw KeyRequestException(429, "rate limited")
            }
            fail("expected KeyRequestException")
        } catch (e: KeyRequestException) {
            assertEquals(429, e.statusCode)
        }
    }

    @Test
    fun `streamWithProviderKeyFailover switches key only before first chunk`() = runBlocking {
        val roulette = SmartKeyRoulette()
        val provider = ProviderSetting.OpenAI(
            apiKey = "",
            apiKeyPool = listOf(
                me.rerere.ai.provider.ApiKeyEntry(name = "A", key = "sk-a"),
                me.rerere.ai.provider.ApiKeyEntry(name = "B", key = "sk-b"),
            ),
            keyPoolConfig = KeyPoolConfig(strategy = KeyPoolStrategy.STICKY_UNTIL_FAILURE),
        )

        val chunks = streamWithProviderKeyFailover(roulette, provider, "Test") { key ->
            flow {
                if (key.value == "sk-a") {
                    roulette.reportHttpStatusFailure(key, provider, 500, "boom")
                    throw KeyRequestException(500, "server error")
                }
                emit(
                    MessageChunk(
                        id = "1",
                        model = "m",
                        choices = listOf(
                            UIMessageChoice(index = 0, delta = null, message = null, finishReason = "stop")
                        ),
                    )
                )
            }
        }.toList()

        assertEquals(1, chunks.size)
        // After failing A before first chunk, sticky should stick on B
        assertEquals(provider.apiKeyPool[1].id, roulette.getActiveKeyId(provider.id))
    }

    @Test
    fun `streamWithProviderKeyFailover does not switch after emitting`() = runBlocking {
        val roulette = SmartKeyRoulette()
        val provider = ProviderSetting.OpenAI(
            apiKey = "",
            apiKeyPool = listOf(
                me.rerere.ai.provider.ApiKeyEntry(name = "A", key = "sk-a"),
                me.rerere.ai.provider.ApiKeyEntry(name = "B", key = "sk-b"),
            ),
        )

        try {
            streamWithProviderKeyFailover(roulette, provider, "Test") { key ->
                flow {
                    emit(
                        MessageChunk(
                            id = "1",
                            model = "m",
                            choices = emptyList(),
                        )
                    )
                    roulette.reportHttpStatusFailure(key, provider, 500, "mid-stream")
                    throw KeyRequestException(500, "mid-stream failure")
                }
            }.toList()
            fail("expected mid-stream failure to propagate")
        } catch (e: KeyRequestException) {
            assertEquals(500, e.statusCode)
        }
    }
}
