package me.rerere.rikkahub.data.datastore

import android.app.Application
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.local.InstalledSherpaModel
import me.rerere.asr.local.SherpaModelFamily
import me.rerere.asr.local.SherpaModelStore
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateCache
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalSpeechModelSettingsSyncTest {
    private val appScope = AppScope()
    private lateinit var settingsStore: SettingsStore
    private lateinit var speechStore: SherpaModelStore

    @Before fun setUp() = runBlocking {
        startKoin { modules(module {
            single<MessageTemplateCache> { object : MessageTemplateCache { override fun invalidateAll() {} } }
        }) }
        val context = RuntimeEnvironment.getApplication()
        settingsStore = SettingsStore(context, appScope, QuickSettingsCache(context), SecretKeyManager(SecureStore(context)))
        speechStore = SherpaModelStore(context)
        speechStore.clear()
        withTimeout(20_000) { settingsStore.settingsFlow.first { !it.init } }
        settingsStore.update { it.copy(providers = emptyList(), sttModelId = null) }
    }

    @After fun tearDown() { appScope.cancel(); stopKoin() }

    private fun installed(id: String) = InstalledSherpaModel(
        id = id, displayName = "Speech $id", family = SherpaModelFamily.WHISPER,
        directoryPath = "/unused/$id", sizeInBytes = 100, revision = "1", files = emptyMap(),
    )

    private fun Settings.local() = providers.filterIsInstance<ProviderSetting.LiteRtLocal>().single()

    @Test fun installPublishesAudioModelWithStableIdAndPreservesSelectionOnRenameAndReorder() {
        val first = Settings(providers = emptyList()).withInstalledSherpaModels(listOf(installed("a"), installed("b")))
        val selected = first.local().models.first()
        val renamed = installed("a").copy(displayName = "Renamed", customIconUri = "content://icon")
        val updated = first.copy(sttModelId = selected.id)
            .withInstalledSherpaModels(listOf(installed("b"), renamed))
        assertEquals(listOf("b", "a"), updated.local().models.map { it.modelId })
        assertEquals(selected.id, updated.local().models.last().id)
        assertEquals(selected.id, updated.sttModelId)
        assertEquals("Renamed", updated.local().models.last().displayName)
        assertEquals("content://icon", updated.local().models.last().customIconUri)
        assertEquals(listOf(Modality.AUDIO), updated.local().models.last().inputModalities)
        assertEquals(listOf(Modality.TEXT), updated.local().models.last().outputModalities)
        assertEquals(ModelType.STT, updated.local().models.last().type)
        assertEquals(updated, updated.withInstalledSherpaModels(listOf(installed("b"), renamed)))
    }

    @Test fun syncPreservesRemoteSelectionAndLocalProviderEditsAndOtherModelTypes() {
        val llm = Model(modelId = "chat", type = ModelType.CHAT)
        val embedding = Model(modelId = "embed", type = ModelType.EMBEDDING)
        val remoteModel = Model(modelId = "speech-api", type = ModelType.STT)
        val remote = ProviderSetting.OpenAI(name = "My server", models = listOf(remoteModel))
        val local = ProviderSetting.LiteRtLocal(name = "My phone", enabled = false, models = listOf(llm, embedding))
        val initial = Settings(providers = listOf(remote, local), sttModelId = remoteModel.id)
        val updated = initial.withInstalledSherpaModels(listOf(installed("a")))
        assertEquals(remote, updated.providers.first())
        assertEquals(local.copy(models = updated.local().models), updated.local())
        assertEquals(listOf(llm, embedding), updated.local().models.filter { it.type != ModelType.STT })
        assertEquals(remoteModel.id, updated.sttModelId)
        assertEquals(initial, updated.withInstalledSherpaModels(emptyList()))
    }

    @Test fun removalClearsOnlySelectedRemovedSpeechModel() {
        val initial = Settings(providers = emptyList()).withInstalledSherpaModels(listOf(installed("a"), installed("b")))
        val a = initial.local().models.first()
        val b = initial.local().models.last()
        assertNull(initial.copy(sttModelId = a.id).withInstalledSherpaModels(listOf(installed("b"))).sttModelId)
        assertEquals(b.id, initial.copy(sttModelId = b.id).withInstalledSherpaModels(listOf(installed("b"))).sttModelId)
        assertTrue(Settings(providers = emptyList()).withInstalledSherpaModels(emptyList()).providers.isEmpty())
    }

    @Test fun explicitSelectionUsesLatestModelsWithoutDuplicatingOrReplacingTheirIds() {
        val first = Settings(providers = emptyList()).withInstalledSherpaModels(listOf(installed("a")))
        val llm = Model(modelId = "new-chat", displayName = "Edited", type = ModelType.CHAT)
        val latest = first.copy(providers = listOf(first.local().copy(name = "Edited provider", models = first.local().models + llm)))
        val selected = latest.withInstalledSherpaModels(listOf(installed("a")), selectInstalledId = "a")
        assertEquals(first.local().models.single().id, selected.sttModelId)
        assertEquals(latest.local(), selected.local())
        assertEquals(selected, selected.withInstalledSherpaModels(listOf(installed("a")), selectInstalledId = "already-removed"))
    }

    @Test fun backgroundInstallReachesPersistedSelectorWithoutAnySettingsViewModel() = runBlocking {
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val registry = appScope.launch {
            collectInstalledSherpaModelsToSettings(speechStore.models, settingsStore) { errors.add(it) }
        }
        try {
            // No Local models page or ViewModel is created; this is the same app-scoped collector.
            speechStore.upsert(installed("a"))
            val installedSettings = withTimeout(20_000) {
                settingsStore.settingsFlow.first { settings ->
                    settings.providers.filterIsInstance<ProviderSetting.LiteRtLocal>()
                        .any { provider -> provider.models.any { it.modelId == "a" && it.type == ModelType.STT } }
                }
            }
            val selectedId = installedSettings.local().models.single().id
            val remote = ProviderSetting.OpenAI(name = "Concurrent remote edit")
            settingsStore.update { latest -> latest.copy(providers = latest.providers + remote, sttModelId = selectedId) }
            speechStore.rename("a", "Renamed after install")
            val renamed = withTimeout(20_000) {
                settingsStore.settingsFlow.first { it.local().models.single().displayName == "Renamed after install" }
            }
            assertEquals(selectedId, renamed.sttModelId)
            assertEquals(selectedId, renamed.local().models.single().id)
            assertEquals(remote, renamed.providers.last())
            settingsStore.awaitQueuedWrites()
            val persisted = withTimeout(20_000) { settingsStore.settingsFlowRaw.first() }
            assertEquals(renamed.providers, persisted.providers)
            assertEquals(selectedId, persisted.sttModelId)
            speechStore.remove("a")
            val removed = withTimeout(20_000) { settingsStore.settingsFlow.first { it.local().models.isEmpty() } }
            assertNull(removed.sttModelId)
            assertEquals(remote, removed.providers.last())
            assertTrue(errors.toString(), errors.isEmpty())
        } finally {
            registry.cancel()
        }
    }
}
