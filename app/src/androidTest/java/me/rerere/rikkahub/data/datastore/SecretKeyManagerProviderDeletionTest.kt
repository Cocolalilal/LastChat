package me.rerere.rikkahub.data.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.ai.provider.ProviderSetting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class SecretKeyManagerProviderDeletionTest {
    private lateinit var secureStore: SecureStore
    private lateinit var secretKeyManager: SecretKeyManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        secureStore = SecureStore(context)
        secureStore.clearAll()
        secretKeyManager = SecretKeyManager(secureStore)
    }

    @After
    fun tearDown() {
        secureStore.clearAll()
    }

    @Test
    fun deletingProviderRemovesApiKeyAndPrivateKey() {
        val providerId = Uuid.random()
        val provider = ProviderSetting.Google(id = providerId)
        secretKeyManager.setApiKey(providerId, "api-secret")
        secretKeyManager.setPrivateKey(providerId, "private-secret")

        secretKeyManager.handleExplicitSecretDeletions(
            oldSettings = Settings(providers = listOf(provider)),
            newSettings = Settings(providers = emptyList()),
        )

        assertEquals("", secretKeyManager.getApiKey(providerId, ""))
        assertEquals("", secretKeyManager.getPrivateKey(providerId, ""))
    }

    @Test
    fun readdingPresetWithSameIdDoesNotRestoreDeletedSecrets() {
        val providerId = Uuid.random()
        val providerPreset = ProviderSetting.Google(id = providerId)
        secretKeyManager.setApiKey(providerId, "api-secret")
        secretKeyManager.setPrivateKey(providerId, "private-secret")

        secretKeyManager.handleExplicitSecretDeletions(
            oldSettings = Settings(providers = listOf(providerPreset)),
            newSettings = Settings(providers = emptyList()),
        )
        val readdedProvider = secretKeyManager.populateSecretsForExport(
            Settings(providers = listOf(providerPreset)),
        ).providers.single() as ProviderSetting.Google

        assertEquals("", readdedProvider.apiKey)
        assertEquals("", readdedProvider.privateKey)
    }

    @Test
    fun keepingProviderDoesNotRemoveStoredSecrets() {
        val providerId = Uuid.random()
        val provider = ProviderSetting.Google(id = providerId)
        secretKeyManager.setApiKey(providerId, "api-secret")
        secretKeyManager.setPrivateKey(providerId, "private-secret")

        secretKeyManager.handleExplicitSecretDeletions(
            oldSettings = Settings(providers = listOf(provider)),
            newSettings = Settings(providers = listOf(provider.copy(name = "Renamed"))),
        )

        assertEquals("api-secret", secretKeyManager.getApiKey(providerId, ""))
        assertEquals("private-secret", secretKeyManager.getPrivateKey(providerId, ""))
    }
}
