package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

/**
 * Manages secrets for providers and other sensitive data.
 * Handles migration from plaintext DataStore to encrypted SecureStore.
 * 
 * Key naming conventions:
 * - Provider API key: "provider_apikey_{providerId}"
 * - Provider private key (Vertex AI): "provider_privatekey_{providerId}"
 * - WebDAV password: "webdav_password"
 */
class SecretKeyManager(
    private val secureStore: SecureStore
) {
    companion object {
        private const val PROVIDER_APIKEY_PREFIX = "provider_apikey_"
        private const val PROVIDER_PRIVATEKEY_PREFIX = "provider_privatekey_"
        private const val TTS_PROVIDER_APIKEY_PREFIX = "tts_provider_apikey_"
        private const val WEBDAV_PASSWORD_KEY = "webdav_password"
    }

    // ========== Provider API Key Management ==========

    /**
     * Get the API key for a provider. First checks SecureStore, then falls back
     * to the plaintext value (for migration).
     */
    fun getApiKey(providerId: Uuid, plaintextFallback: String): String {
        val key = "$PROVIDER_APIKEY_PREFIX$providerId"
        return secureStore.getSecret(key) ?: plaintextFallback
    }

    /**
     * Store an API key securely for a provider.
     */
    fun setApiKey(providerId: Uuid, apiKey: String) {
        val key = "$PROVIDER_APIKEY_PREFIX$providerId"
        if (apiKey.isNotBlank()) {
            secureStore.putSecret(key, apiKey)
        } else {
            secureStore.removeSecret(key)
        }
    }

    /**
     * Get the private key for a Google Vertex AI provider.
     */
    fun getPrivateKey(providerId: Uuid, plaintextFallback: String): String {
        val key = "$PROVIDER_PRIVATEKEY_PREFIX$providerId"
        return secureStore.getSecret(key) ?: plaintextFallback
    }

    /**
     * Store a private key securely for a Google Vertex AI provider.
     */
    fun setPrivateKey(providerId: Uuid, privateKey: String) {
        val key = "$PROVIDER_PRIVATEKEY_PREFIX$providerId"
        if (privateKey.isNotBlank()) {
            secureStore.putSecret(key, privateKey)
        } else {
            secureStore.removeSecret(key)
        }
    }

    /**
     * Remove all secrets for a provider (when provider is deleted).
     */
    fun removeProviderSecrets(providerId: Uuid) {
        secureStore.removeSecret("$PROVIDER_APIKEY_PREFIX$providerId")
        secureStore.removeSecret("$PROVIDER_PRIVATEKEY_PREFIX$providerId")
    }

    // ========== TTS API Key Management ==========

    fun getTtsApiKey(providerId: Uuid, plaintextFallback: String): String {
        val key = "$TTS_PROVIDER_APIKEY_PREFIX$providerId"
        return secureStore.getSecret(key) ?: plaintextFallback
    }

    fun setTtsApiKey(providerId: Uuid, apiKey: String) {
        val key = "$TTS_PROVIDER_APIKEY_PREFIX$providerId"
        if (apiKey.isNotBlank()) {
            secureStore.putSecret(key, apiKey)
        } else {
            secureStore.removeSecret(key)
        }
    }

    fun removeTtsProviderSecrets(providerId: Uuid) {
        secureStore.removeSecret("$TTS_PROVIDER_APIKEY_PREFIX$providerId")
    }

    // ========== WebDAV Password Management ==========

    fun getWebDavPassword(plaintextFallback: String): String {
        return secureStore.getSecret(WEBDAV_PASSWORD_KEY) ?: plaintextFallback
    }

    fun setWebDavPassword(password: String) {
        if (password.isNotBlank()) {
            secureStore.putSecret(WEBDAV_PASSWORD_KEY, password)
        } else {
            secureStore.removeSecret(WEBDAV_PASSWORD_KEY)
        }
    }

    // ========== Migration Logic ==========

    /**
     * Migrate secrets from plaintext Settings to SecureStore.
     * Returns updated Settings with credentials cleared (moved to SecureStore).
     * 
     * This should be called once when settings are loaded to ensure migration.
     */
    fun migrateSecretsFromSettings(settings: Settings): Settings {
        var migrated = false
        
        // Migrate provider API keys
        val migratedProviders = settings.providers.map { provider ->
            migrateProviderSecrets(provider).also { 
                if (it != provider) migrated = true
            }
        }

        // Migrate WebDAV password
        val migratedWebDav = if (settings.webDavConfig.password.isNotBlank()) {
            setWebDavPassword(settings.webDavConfig.password)
            migrated = true
            settings.webDavConfig.copy(password = "") // Clear plaintext
        } else {
            settings.webDavConfig
        }

        // Migrate TTS provider API keys
        val migratedTtsProviders = settings.ttsProviders.map { provider ->
            migrateTtsProviderSecrets(provider).also {
                if (it != provider) migrated = true
            }
        }

        return if (migrated) {
            settings.copy(
                providers = migratedProviders,
                webDavConfig = migratedWebDav,
                ttsProviders = migratedTtsProviders
            )
        } else {
            settings
        }
    }

    /**
     * Handle explicit secret deletions.
     * When a user clears a secret field (changes from non-empty to empty),
     * we need to remove it from SecureStore so it doesn't get re-populated.
     * 
     * This should be called BEFORE migrateSecretsFromSettings() in the update flow.
     */
    fun handleExplicitSecretDeletions(oldSettings: Settings, newSettings: Settings) {
        // Handle provider secrets (API keys and private keys)
        for (newProvider in newSettings.providers) {
            val oldProvider = oldSettings.providers.find { it.id == newProvider.id } ?: continue
            
            when {
                oldProvider is ProviderSetting.OpenAI && newProvider is ProviderSetting.OpenAI -> {
                    // Check if API key was explicitly cleared
                    if (oldProvider.apiKey.isNotBlank() && newProvider.apiKey.isBlank()) {
                        setApiKey(newProvider.id, "")
                    }
                }
                oldProvider is ProviderSetting.Google && newProvider is ProviderSetting.Google -> {
                    if (oldProvider.apiKey.isNotBlank() && newProvider.apiKey.isBlank()) {
                        setApiKey(newProvider.id, "")
                    }
                    if (oldProvider.privateKey.isNotBlank() && newProvider.privateKey.isBlank()) {
                        setPrivateKey(newProvider.id, "")
                    }
                }
                oldProvider is ProviderSetting.Claude && newProvider is ProviderSetting.Claude -> {
                    if (oldProvider.apiKey.isNotBlank() && newProvider.apiKey.isBlank()) {
                        setApiKey(newProvider.id, "")
                    }
                }
            }
        }

        // Handle TTS provider secrets
        for (newTtsProvider in newSettings.ttsProviders) {
            val oldTtsProvider = oldSettings.ttsProviders.find { it.id == newTtsProvider.id } ?: continue
            
            val oldKey = when (oldTtsProvider) {
                is TTSProviderSetting.OpenAI -> oldTtsProvider.apiKey
                is TTSProviderSetting.Gemini -> oldTtsProvider.apiKey
                is TTSProviderSetting.MiniMax -> oldTtsProvider.apiKey
                is TTSProviderSetting.ElevenLabs -> oldTtsProvider.apiKey
                is TTSProviderSetting.SystemTTS -> ""
            }
            val newKey = when (newTtsProvider) {
                is TTSProviderSetting.OpenAI -> newTtsProvider.apiKey
                is TTSProviderSetting.Gemini -> newTtsProvider.apiKey
                is TTSProviderSetting.MiniMax -> newTtsProvider.apiKey
                is TTSProviderSetting.ElevenLabs -> newTtsProvider.apiKey
                is TTSProviderSetting.SystemTTS -> ""
            }
            
            if (oldKey.isNotBlank() && newKey.isBlank()) {
                setTtsApiKey(newTtsProvider.id, "")
            }
        }

        // Handle WebDAV password
        if (oldSettings.webDavConfig.password.isNotBlank() && 
            newSettings.webDavConfig.password.isBlank()) {
            setWebDavPassword("")
        }
    }

    /**
     * Migrate a single provider's secrets to SecureStore.
     * Returns provider with credentials cleared if migration occurred.
     */
    private fun migrateProviderSecrets(provider: ProviderSetting): ProviderSetting {
        return when (provider) {
            is ProviderSetting.OpenAI -> {
                if (provider.apiKey.isNotBlank()) {
                    setApiKey(provider.id, provider.apiKey)
                    provider.copy(apiKey = "") // Clear plaintext
                } else provider
            }
            is ProviderSetting.Google -> {
                var updated = provider
                if (provider.apiKey.isNotBlank()) {
                    setApiKey(provider.id, provider.apiKey)
                    updated = updated.copy(apiKey = "")
                }
                if (provider.privateKey.isNotBlank()) {
                    setPrivateKey(provider.id, provider.privateKey)
                    updated = updated.copy(privateKey = "")
                }
                updated
            }
            is ProviderSetting.Claude -> {
                if (provider.apiKey.isNotBlank()) {
                    setApiKey(provider.id, provider.apiKey)
                    provider.copy(apiKey = "") // Clear plaintext
                } else provider
            }
        }
    }

    private fun migrateTtsProviderSecrets(provider: TTSProviderSetting): TTSProviderSetting {
        return when (provider) {
            is TTSProviderSetting.OpenAI -> {
                if (provider.apiKey.isNotBlank()) {
                    setTtsApiKey(provider.id, provider.apiKey)
                    provider.copy(apiKey = "")
                } else provider
            }

            is TTSProviderSetting.Gemini -> {
                if (provider.apiKey.isNotBlank()) {
                    setTtsApiKey(provider.id, provider.apiKey)
                    provider.copy(apiKey = "")
                } else provider
            }

            is TTSProviderSetting.MiniMax -> {
                if (provider.apiKey.isNotBlank()) {
                    setTtsApiKey(provider.id, provider.apiKey)
                    provider.copy(apiKey = "")
                } else provider
            }

            is TTSProviderSetting.ElevenLabs -> {
                if (provider.apiKey.isNotBlank()) {
                    setTtsApiKey(provider.id, provider.apiKey)
                    provider.copy(apiKey = "")
                } else provider
            }

            is TTSProviderSetting.SystemTTS -> provider
        }
    }

    // ========== Backup/Export Support ==========

    /**
     * Populate settings with decrypted secrets for export.
     * This creates a copy with all secrets in plaintext for backup portability.
     */
    fun populateSecretsForExport(settings: Settings): Settings {
        val providersWithSecrets = settings.providers.map { provider ->
            populateProviderSecrets(provider)
        }

        val webDavWithPassword = settings.webDavConfig.copy(
            password = getWebDavPassword(settings.webDavConfig.password)
        )

        val ttsProvidersWithSecrets = settings.ttsProviders.map { provider ->
            populateTtsProviderSecrets(provider)
        }

        return settings.copy(
            providers = providersWithSecrets,
            webDavConfig = webDavWithPassword,
            ttsProviders = ttsProvidersWithSecrets
        )
    }

    /**
     * Populate a single provider with its secrets for export.
     */
    private fun populateProviderSecrets(provider: ProviderSetting): ProviderSetting {
        return when (provider) {
            is ProviderSetting.OpenAI -> {
                provider.copy(apiKey = getApiKey(provider.id, provider.apiKey))
            }
            is ProviderSetting.Google -> {
                provider.copy(
                    apiKey = getApiKey(provider.id, provider.apiKey),
                    privateKey = getPrivateKey(provider.id, provider.privateKey)
                )
            }
            is ProviderSetting.Claude -> {
                provider.copy(apiKey = getApiKey(provider.id, provider.apiKey))
            }
        }
    }

    private fun populateTtsProviderSecrets(provider: TTSProviderSetting): TTSProviderSetting {
        return when (provider) {
            is TTSProviderSetting.OpenAI -> {
                provider.copy(apiKey = getTtsApiKey(provider.id, provider.apiKey))
            }

            is TTSProviderSetting.Gemini -> {
                provider.copy(apiKey = getTtsApiKey(provider.id, provider.apiKey))
            }

            is TTSProviderSetting.MiniMax -> {
                provider.copy(apiKey = getTtsApiKey(provider.id, provider.apiKey))
            }

            is TTSProviderSetting.ElevenLabs -> {
                provider.copy(apiKey = getTtsApiKey(provider.id, provider.apiKey))
            }

            is TTSProviderSetting.SystemTTS -> provider
        }
    }

    /**
     * Import secrets from backup settings and store them encrypted.
     * This should be called after restoring settings from a backup file.
     */
    fun importSecretsFromBackup(settings: Settings): Settings {
        // Same as migration - store secrets and clear plaintext
        return migrateSecretsFromSettings(settings)
    }
}
