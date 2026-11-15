package me.rerere.rikkahub.data.datastore

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsStoreDurabilityTest {
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single<MessageTemplateCache> { object : MessageTemplateCache { override fun invalidateAll() {} } }
            })
        }
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        store = SettingsStore(
            context = context,
            scope = AppScope(),
            quickCache = QuickSettingsCache(context),
            secretKeyManager = SecretKeyManager(SecureStore(context)),
        )
        runBlocking { withTimeout(20_000) { store.settingsFlow.first { !it.init } } }
    }

    @After
    fun tearDown() = stopKoin()

    private fun persisted(): Settings = runBlocking { withTimeout(20_000) { store.settingsFlowRaw.first() } }

    @Test
    fun additionalContextAssignmentsSurvivePersistence() = runBlocking {
        val character = store.settingsFlow.value.assistants.first().id
        val snippet = me.rerere.rikkahub.data.model.AdditionalContext(
            name = "Writing style", prompt = "Keep replies concise", assistantIds = setOf(character),
        )
        store.update { it.copy(additionalContexts = listOf(snippet)) }
        store.awaitQueuedWrites()
        assertEquals(listOf(snippet), persisted().additionalContexts)
        assertEquals(listOf(snippet), store.settingsFlow.value.additionalContexts)
    }

    @Test
    fun hundredInterleavedConcurrentEditsAllPersist() = runBlocking {
        val before = store.settingsFlow.value
        val toggleStart = before.enableWebSearch
        val jobs = (0 until 100).map { i ->
            async(Dispatchers.Default) {
                when (i % 2) {
                    0 -> store.enqueueUpdate { it.copy(enableWebSearch = !it.enableWebSearch) }
                    else -> store.enqueueUpdate { it.copy(titlePrompt = it.titlePrompt + "|$i") }
                }.await()
            }
        }
        jobs.awaitAll()
        store.awaitQueuedWrites()
        val disk = persisted()
        // 50 toggles: back to the start value. 50 appends: every one is there.
        assertEquals(toggleStart, disk.enableWebSearch)
        val parts = disk.titlePrompt.removePrefix(before.titlePrompt).split('|').filter { it.isNotEmpty() }
        assertEquals((1 until 100 step 2).map { it.toString() }.toSet(), parts.toSet())
        assertEquals(50, parts.size)
        assertEquals(disk.titlePrompt, store.settingsFlow.value.titlePrompt)
    }

    @Test
    fun editThenImmediateDisposePersists() {
        // The screen's scope (viewModelScope) is cancelled right after the edit, as when
        // navigating away; the write lives in the store's queue, not that scope.
        val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        runBlocking(screenScope.coroutineContext) {
            store.enqueueUpdate { it.copy(suggestionPrompt = "disposed-edit") }
        }
        screenScope.cancel()
        runBlocking { withTimeout(10_000) { store.awaitQueuedWrites() } }
        assertEquals("disposed-edit", persisted().suggestionPrompt)
    }

    @Test
    fun editThenOnStopFlushPersists() {
        store.enqueueUpdate { it.copy(ocrPrompt = "stop-edit") }
        store.flushBlocking() // what ProcessLifecycleOwner ON_STOP runs
        assertEquals("stop-edit", persisted().ocrPrompt)
    }

    @Test
    fun reloadRaceNeverRevertsAValue() = runBlocking {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val watcher = launch(Dispatchers.Default) {
            store.settingsFlow.collect { seen += it.learningModePrompt }
        }
        for (i in 1..60) {
            store.enqueueUpdate { it.copy(learningModePrompt = "v$i") }
        }
        store.awaitQueuedWrites()
        kotlinx.coroutines.delay(1_500) // let every DataStore re-read come through
        watcher.cancel()
        val versions = seen.mapNotNull { it.removePrefix("v").toIntOrNull() }
        assertTrue("never goes backwards: $versions", versions.zipWithNext().all { (a, b) -> b >= a })
        assertEquals("v60", store.settingsFlow.value.learningModePrompt)
        assertEquals("v60", persisted().learningModePrompt)
    }
}
