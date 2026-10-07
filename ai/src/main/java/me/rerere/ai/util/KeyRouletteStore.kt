package me.rerere.ai.util

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Snapshot of [SmartKeyRoulette] runtime state that must survive process death.
 *
 * [KeyHealthState.cooldownUntil] is stored as an absolute epoch millis so cooldowns
 * remain correct after the app is reopened.
 */
@Serializable
data class KeyRouletteSnapshot(
    val healthByKeyId: Map<String, KeyHealthState> = emptyMap(),
    val activeKeyByProviderId: Map<String, String> = emptyMap(),
)

/**
 * Persistence backend for key-pool health and sticky active-key selection.
 * Platform code (Android SharedPreferences / EncryptedSharedPreferences) supplies
 * the implementation; the ai module stays free of Android types.
 */
interface KeyRouletteStore {
    fun load(): KeyRouletteSnapshot
    fun save(snapshot: KeyRouletteSnapshot)
}

/** In-memory store used by unit tests and as a no-op default before app wiring. */
class InMemoryKeyRouletteStore(
    initial: KeyRouletteSnapshot = KeyRouletteSnapshot(),
) : KeyRouletteStore {
    @Volatile
    private var snapshot: KeyRouletteSnapshot = initial

    override fun load(): KeyRouletteSnapshot = snapshot

    override fun save(snapshot: KeyRouletteSnapshot) {
        this.snapshot = snapshot
    }
}

internal fun KeyRouletteSnapshot.toHealthMap(): Map<Uuid, KeyHealthState> =
    healthByKeyId.mapNotNull { (id, state) ->
        runCatching { Uuid.parse(id) to state }.getOrNull()
    }.toMap()

internal fun KeyRouletteSnapshot.toActiveKeyMap(): Map<Uuid, Uuid> =
    activeKeyByProviderId.mapNotNull { (providerId, keyId) ->
        runCatching { Uuid.parse(providerId) to Uuid.parse(keyId) }.getOrNull()
    }.toMap()

internal fun buildKeyRouletteSnapshot(
    health: Map<Uuid, KeyHealthState>,
    activeKeyByProvider: Map<Uuid, Uuid>,
): KeyRouletteSnapshot = KeyRouletteSnapshot(
    healthByKeyId = health.mapKeys { it.key.toString() },
    activeKeyByProviderId = activeKeyByProvider.mapKeys { it.key.toString() }
        .mapValues { it.value.toString() },
)
