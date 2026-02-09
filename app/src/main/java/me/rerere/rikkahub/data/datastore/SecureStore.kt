package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import me.rerere.rikkahub.BuildConfig

/**
 * Secure storage for sensitive data like API keys and passwords.
 * Uses Android's EncryptedSharedPreferences backed by the Keystore.
 * 
 * Secrets are stored encrypted at rest and decrypted only when accessed.
 * Export/backup operations should call getSecretForExport() to get decrypted values.
 */
class SecureStore(context: Context) {
    companion object {
        private const val TAG = "SecureStore"
        private const val PREFS_NAME = "encrypted_secrets"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Store a secret securely.
     * @param key Unique identifier for this secret (e.g., "provider_apikey_<uuid>")
     * @param value The secret value to encrypt and store
     */
    fun putSecret(key: String, value: String) {
        val success = encryptedPrefs.edit().putString(key, value).commit()
        if (!success) {
            Log.e(TAG, "Failed to store secret for key: $key")
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Stored secret for key: $key")
        }
    }

    /**
     * Retrieve a decrypted secret.
     * @param key The identifier used when storing
     * @return The decrypted secret value, or null if not found
     */
    fun getSecret(key: String): String? {
        return encryptedPrefs.getString(key, null)
    }

    /**
     * Remove a stored secret.
     * @param key The identifier to remove
     */
    fun removeSecret(key: String) {
        val success = encryptedPrefs.edit().remove(key).commit()
        if (!success) {
            Log.e(TAG, "Failed to remove secret for key: $key")
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Removed secret for key: $key")
        }
    }

    /**
     * Check if a secret exists.
     * @param key The identifier to check
     */
    fun hasSecret(key: String): Boolean {
        return encryptedPrefs.contains(key)
    }

    /**
     * Get all secret keys (not values!) for enumeration.
     * Useful for migration and cleanup.
     */
    fun getAllKeys(): Set<String> {
        return encryptedPrefs.all.keys
    }

    /**
     * Get a secret for export/backup purposes.
     * This returns the decrypted value which should only be used for backup files.
     * The backup system will re-encrypt these values on import.
     */
    fun getSecretForExport(key: String): String? {
        return getSecret(key)
    }

    /**
     * Import a secret from a backup file.
     * This takes a plaintext value (from backup) and encrypts it.
     */
    fun importSecret(key: String, plaintextValue: String) {
        putSecret(key, plaintextValue)
    }

    /**
     * Clear all secrets.
     * Use with caution - typically only for debugging or factory reset.
     */
    fun clearAll() {
        val success = encryptedPrefs.edit().clear().commit()
        if (!success) {
            Log.e(TAG, "Failed to clear encrypted secrets")
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Cleared all secrets")
        }
    }
}
