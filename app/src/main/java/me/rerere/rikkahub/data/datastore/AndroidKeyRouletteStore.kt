package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import me.rerere.ai.util.KeyRouletteSnapshot
import me.rerere.ai.util.KeyRouletteStore

/**
 * Persists [SmartKeyRoulette] health + sticky active-key maps using
 * EncryptedSharedPreferences (same Keystore-backed scheme as [SecureStore]).
 *
 * Values are not API secrets — only key UUIDs, cooldown timestamps, and flags —
 * but encrypted storage keeps the routing state consistent with other sensitive prefs.
 */
class AndroidKeyRouletteStore(
    context: Context,
) : KeyRouletteStore {
    companion object {
        private const val TAG = "AndroidKeyRouletteStore"
        private const val PREFS_NAME = "key_roulette_state"
        private const val SNAPSHOT_KEY = "snapshot_v1"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val masterKey: MasterKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private var fallbackPrefs: SharedPreferences? = null

    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable; falling back to plain prefs", e)
            context.applicationContext
                .getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
                .also { fallbackPrefs = it }
        }
    }

    override fun load(): KeyRouletteSnapshot {
        val raw = prefs.getString(SNAPSHOT_KEY, null) ?: return KeyRouletteSnapshot()
        return try {
            json.decodeFromString(KeyRouletteSnapshot.serializer(), raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode key roulette snapshot", e)
            KeyRouletteSnapshot()
        }
    }

    override fun save(snapshot: KeyRouletteSnapshot) {
        try {
            val raw = json.encodeToString(KeyRouletteSnapshot.serializer(), snapshot)
            val ok = prefs.edit().putString(SNAPSHOT_KEY, raw).commit()
            if (!ok) {
                Log.e(TAG, "Failed to persist key roulette snapshot")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode/save key roulette snapshot", e)
        }
    }
}
