package me.rerere.rikkahub.ui.pages.setting

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateCache
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.utils.SkillExportImport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SkillEditPersistenceTest {
    private val appScope = AppScope()
    private val scheduler = TestCoroutineScheduler()
    private lateinit var context: Context
    private lateinit var store: SettingsStore
    private var submitted = 0
    private var saved = 0
    private val errors = mutableListOf<Throwable>()

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        startKoin { modules(module {
            single<MessageTemplateCache> { object : MessageTemplateCache { override fun invalidateAll() {} } }
        }) }
        context = RuntimeEnvironment.getApplication()
        store = SettingsStore(context, appScope, QuickSettingsCache(context), SecretKeyManager(SecureStore(context)),
            StandardTestDispatcher(scheduler))
        runBlocking { withTimeout(20_000) {
            while (store.settingsFlow.value.init) { scheduler.runCurrent(); delay(10) }
        } }
    }

    @After fun tearDown() {
        try {
            runBlocking { withTimeout(20_000) {
                while (saved + errors.size < submitted) { scheduler.runCurrent(); delay(10) }
                val drained = async(Dispatchers.Default) { store.awaitQueuedWrites() }
                while (!drained.isCompleted) { scheduler.runCurrent(); delay(10) }
                drained.await()
            } }
        } finally {
            appScope.cancel()
            Dispatchers.resetMain()
            stopKoin()
        }
    }

    private fun <T> acknowledge(result: Deferred<T>): T = runBlocking {
        withTimeout(20_000) {
            while (!result.isCompleted) { scheduler.runCurrent(); delay(10) }
            result.await()
        }
    }

    private fun seed(): Skill {
        val skill = SkillExportImport.syncManagedSkill(context,
            Skill(name = "original", description = "Original description", instructions = "Original instructions", enabled = false))
        acknowledge(store.enqueueUpdate { it.copy(skills = listOf(skill)) })
        return skill
    }

    private fun save(
        writer: SkillEditWriter,
        skill: Skill,
        create: Boolean = false,
        synchronizePackage: Boolean = true,
    ) {
        submitted++
        writer.save(skill, create = create, synchronizePackage = synchronizePackage,
            onSaved = { saved++ }, onError = { errors += it })
    }

    private fun awaitSaves() {
        runBlocking { withTimeout(20_000) {
            while (saved + errors.size < submitted) { scheduler.runCurrent(); delay(10) }
        } }
        assertTrue("Saving failed: $errors", errors.isEmpty())
        assertEquals(submitted, saved)
    }

    private fun packageFile(skill: Skill) = File(context.filesDir, "skills/${skill.safePackageRoot()}/SKILL.md")

    private fun packageSkill(skill: Skill): Skill {
        val imported = SkillExportImport.importFromString(packageFile(skill).readText())
        assertTrue("Saved SKILL.md must remain importable", imported is SkillExportImport.ImportResult.Success)
        return (imported as SkillExportImport.ImportResult.Success).skill
    }

    @Test fun renameIsVisibleImmediatelyWhileDiskAndPackageWritesArePaused() {
        val original = seed()
        val originalPackage = packageFile(original).readText()
        val writer = SkillEditWriter(context, store, appScope)
        save(writer, original.copy(name = "renamed"))

        assertEquals("renamed", store.settingsFlow.value.skills.single().name)
        assertEquals("The paused settings ACK must precede package IO", originalPackage, packageFile(original).readText())
        assertEquals(0, saved)

        awaitSaves()
        assertEquals("renamed", packageSkill(original).name)
        assertFalse(store.settingsFlow.value.skills.single().enabled)
    }

    @Test fun reopenedEditorReadsRenameAndAnotherWriterKeepsTheLatestCompletePackage() {
        val original = seed()
        val originalPackage = packageFile(original).readText()
        val releasePackageEdit = CompletableDeferred<Unit>()
        // An explicit raw-file editor uses this same lane. Autosave metadata stays
        // immediately visible while its package writes wait behind that editor.
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withSkillPackageEdit { releasePackageEdit.await() }
        }
        try {
            save(SkillEditWriter(context, store, appScope), original.copy(name = "renamed"))
            val reopenedDraft = store.settingsFlow.value.skills.single().copy(
                description = "Reopened description", instructions = "Final instructions", disableModelInvocation = true,
                userInvocable = false, argumentHint = "[document: path]",
            )
            save(SkillEditWriter(context, store, appScope), reopenedDraft)

            val immediate = store.settingsFlow.value.skills.single()
            assertEquals("renamed", immediate.name)
            assertEquals("Reopened description", immediate.description)
            assertEquals("Final instructions", immediate.instructions)
            acknowledge(store.enqueueUpdate { it })
            assertEquals("All writer instances must respect the raw-editor lane", originalPackage,
                packageFile(original).readText())
        } finally {
            releasePackageEdit.complete(Unit)
        }
        awaitSaves()

        val written = packageSkill(original)
        assertEquals("renamed", written.name)
        assertEquals("Reopened description", written.description)
        assertEquals("Final instructions", written.instructions)
        assertTrue(written.disableModelInvocation)
        assertFalse(written.userInvocable)
        assertEquals("[document: path]", written.argumentHint)
        assertFalse(store.settingsFlow.value.skills.single().enabled)
    }

    @Test fun queuedAutosaveAndStaleEditorCannotResurrectADeletedSkill() {
        val original = seed()
        val originalPackage = packageFile(original).readText()
        save(SkillEditWriter(context, store, appScope), original.copy(name = "pending-rename"))
        val deletion = store.enqueueUpdate { it.copy(skills = emptyList()) }
        save(SkillEditWriter(context, store, appScope), original.copy(description = "Stale edit after deletion"))

        assertTrue(store.settingsFlow.value.skills.isEmpty())
        awaitSaves()
        acknowledge(deletion)
        assertTrue(store.settingsFlow.value.skills.isEmpty())
        assertEquals(originalPackage, packageFile(original).readText())
        val persisted = runBlocking { withTimeout(20_000) { store.settingsFlowRaw.first() } }
        assertTrue(persisted.skills.isEmpty())
    }

    @Test fun rawPackageMetadataSavePreservesHandAuthoredFileBytes() {
        val original = seed()
        val handAuthored = """
            ---
            name: original
            custom-extension: keep-this-exactly
            ---
            Hand-authored instructions, spacing and frontmatter.
        """.trimIndent()
        packageFile(original).writeText(handAuthored)
        save(SkillEditWriter(context, store, appScope), original.copy(instructions = "Updated metadata body"),
            synchronizePackage = false)
        assertEquals("Updated metadata body", store.settingsFlow.value.skills.single().instructions)
        awaitSaves()
        assertEquals(handAuthored, packageFile(original).readText())
    }

    @Test fun creationKeepsOneIdAndItsSaveSurvivesCancellationOfTheOwningScreen() {
        acknowledge(store.enqueueUpdate { it.copy(skills = emptyList()) })
        val draft = Skill(name = "created", instructions = "Created instructions")
        val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        screenScope.launch { save(SkillEditWriter(context, store, appScope), draft, create = true) }
        screenScope.cancel()

        val reopenedDraft = store.settingsFlow.value.skills.single()
        assertEquals(draft.id, reopenedDraft.id)
        assertEquals(draft.safePackageRoot(), reopenedDraft.packageRoot)
        assertFalse(packageFile(draft).exists())
        save(SkillEditWriter(context, store, appScope), reopenedDraft.copy(description = "After reopen"))
        awaitSaves()

        assertEquals(listOf(draft.id), store.settingsFlow.value.skills.map { it.id })
        assertEquals("After reopen", packageSkill(draft).description)
        assertEquals("Created instructions", packageSkill(draft).instructions)
    }
}
