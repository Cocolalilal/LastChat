package me.rerere.rikkahub.data.memory

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.rag.EmbeddingResult
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

/** Cross-lifecycle tests: the model is delayed while a screen/job disappears or a request is cancelled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MemoryDurableWorkTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: CharacterMemoryRepository
    private lateinit var appScope: AppScope
    private lateinit var application: Application
    private var assistant = Assistant(enableMemory = true, memoryExperience = "ADVANCED")
    private val owner get() = assistant.id.toString()
    private val request = "My cat is Milo"

    @Before fun setup() = runBlocking {
        application = ApplicationProvider.getApplicationContext()
        appScope = AppScope()
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = newRepository()
        repository.initialise(owner)
    }

    @After fun close() {
        appScope.cancel()
        db.close()
    }

    private fun newRepository() = CharacterMemoryRepository(db, object : MemoryEmbeddingClient {
        override fun getEmbeddingModelId(assistantId: String?) = "test"
        override suspend fun embedWithModelId(text: String, assistantId: String?) =
            EmbeddingResult(listOf(List(768) { 0f }), "test")
    }) { id -> assistant.takeIf { it.id.toString() == id } }

    private fun controller(repo: CharacterMemoryRepository = repository, schedule: (String) -> Unit = {}) =
        MemoryTaskController(application, repo, appScope, schedule)

    private fun proposal(source: String = "request", quote: String = request) = JsonInstant.encodeToString(
        MemoryProposal(memories = listOf(MemoryCandidate(text = "Has a cat named Milo", topic = "Pets", source = source, quote = quote)))
    )

    private fun processor(generate: suspend (String) -> String) = MemoryProcessor(repository, object : MemoryTextClient {
        override suspend fun generate(owner: String, prompt: String, maxTokens: Int) = generate(prompt)
    })

    private suspend fun taskReady(repo: CharacterMemoryRepository, id: String) = withTimeout(10_000) {
        while (repo.task(owner, id)?.state != "ready") delay(10)
        requireNotNull(repo.task(owner, id))
    }

    private suspend fun addChat(texts: List<String>) {
        val messages = texts.map { MessageNode.of(UIMessage.user(it)) }
        db.conversationDao().insert(ConversationEntity(Uuid.random().toString(), owner, "Source chat",
            JsonInstant.encodeToString(messages), 1, 2, -1, "[]", false))
    }

    @Test fun upgradingBrokenRuntimeQueuesOrganizationWithoutLosingNotesOrUserPause()=runBlocking {
        repository.saveNote(owner,MemoryNote("legacy-9","Saved memory",paragraphs=listOf(MemoryParagraph("p",request,authored=true))),null)
        repository.put(owner,"meta","meta",MemoryMetadata(migrated=true,runtimeVersion=0,paused=true,error="Temporary memories need an expiry.",inFlight="old-request"))
        repository.initialise(owner)
        var meta=repository.metadata(owner)
        assertFalse(meta.paused)
        assertTrue(meta.pendingRefresh)
        assertNull(meta.inFlight)
        assertNull(meta.error)
        assertEquals(request,repository.view(owner).notes.single().body)
        repository.put(owner,"meta","meta",MemoryMetadata(migrated=true,runtimeVersion=0,paused=true))
        repository.initialise(owner)
        meta=repository.metadata(owner)
        assertTrue(meta.paused) // A genuine user pause survives the upgrade.
        assertTrue(meta.pendingRefresh)
    }

    @Test fun leavingTheScreenDoesNotOwnTheSubmittedRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val workerFinished = CompletableDeferred<Unit>()
        val runner = MemoryTaskRunner(repository, processor {
            entered.complete(Unit)
            release.await()
            proposal()
        })
        val controller = controller { appScope.launch { runner.runOne(owner, UUID.randomUUID().toString()) {}; workerFinished.complete(Unit) } }
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val submitted = CompletableDeferred<String>()
        screen.launch { submitted.complete(controller.submit(owner, request)) }
        val taskId = withTimeout(10_000) { submitted.await() }
        withTimeout(10_000) { entered.await() }
        screen.cancel() // Equivalent ownership boundary to the page leaving composition.
        release.complete(Unit)
        withTimeout(10_000) { workerFinished.await() }
        val task = taskReady(repository, taskId)
        assertNotNull(task.draftId)
        assertEquals(taskId, repository.view(owner).drafts.single().taskId)
        assertTrue(repository.view(owner).facts.isEmpty()) // Review is still required.
    }

    @Test fun stoppedRunnerQueuesWorkAndANewRunnerCanFinishIt() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        controller().enqueue(owner, "task", request)
        val first = MemoryTaskRunner(repository, processor { entered.complete(Unit); awaitCancellation() })
        val stopped = launch(Dispatchers.IO) { first.runOne(owner, UUID.randomUUID().toString()) {} }
        withTimeout(10_000) { entered.await() }
        stopped.cancelAndJoin()
        assertEquals("queued", repository.task(owner, "task")?.state)
        assertTrue(repository.view(owner).drafts.isEmpty())

        val reopenedRepository = newRepository()
        val next = MemoryTaskRunner(reopenedRepository, MemoryProcessor(reopenedRepository, object : MemoryTextClient {
            override suspend fun generate(owner: String, prompt: String, maxTokens: Int) = proposal()
        }))
        next.runOne(owner, UUID.randomUUID().toString()) {}
        val ready = taskReady(reopenedRepository, "task")
        assertEquals(2, ready.attempts)
        assertEquals(1, reopenedRepository.view(owner).drafts.size)
    }

    @Test fun explicitCancelFencesAProviderThatReturnsLate() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = controller()
        controller.enqueue(owner, "task", request)
        val runner = MemoryTaskRunner(repository, processor {
            // Simulate a provider which does not abort its already sent request when cancellation happens.
            withContext(NonCancellable) { entered.complete(Unit); release.await() }
            proposal()
        })
        val running = launch(Dispatchers.IO) { runner.runOne(owner, UUID.randomUUID().toString()) {} }
        withTimeout(10_000) { entered.await() }
        controller.cancel(owner, "task")
        release.complete(Unit)
        withTimeout(10_000) { running.join() }
        assertEquals("cancelled", repository.task(owner, "task")?.state)
        assertTrue(repository.view(owner).drafts.isEmpty())
        assertTrue(repository.view(owner).facts.isEmpty())
    }

    @Test fun aPersistedRunningTaskSurvivesProcessReconstruction() = runBlocking {
        controller().enqueue(owner, "task", request)
        val original = requireNotNull(repository.task(owner, "task"))
        repository.put(owner, "task", "task", original.copy(state = "running", workerId = "dead-process", attempts = 1))
        val reopened = newRepository()
        val schedules = mutableListOf<String>()
        controller(reopened) { schedules += it }.recover()
        assertEquals(listOf(owner), schedules)
        val runner = MemoryTaskRunner(reopened, MemoryProcessor(reopened, object : MemoryTextClient {
            override suspend fun generate(owner: String, prompt: String, maxTokens: Int) = proposal()
        }))
        runner.runOne(owner, UUID.randomUUID().toString()) {}
        assertEquals("ready", reopened.task(owner, "task")?.state)
        assertEquals(1, reopened.view(owner).drafts.size)
    }

    @Test fun changingContextCannotWriteTheOldRequestIntoTheNewContext() = runBlocking {
        controller().enqueue(owner, "task", request)
        assistant = assistant.copy(memoryContext = "fantasy")
        val calls = AtomicInteger()
        MemoryTaskRunner(repository, processor { calls.incrementAndGet(); proposal() })
            .runOne(owner, UUID.randomUUID().toString()) {}
        assertEquals(0, calls.get())
        assertEquals("failed", repository.task(owner, "task")?.state)
        assertTrue(repository.view(owner).drafts.isEmpty())
        assertTrue(repository.view(owner).facts.isEmpty())
    }

    @Test fun retryCannotSilentlyMoveAnExistingRequestIntoAnotherContext() = runBlocking {
        val controller = controller()
        controller.enqueue(owner, "task", request)
        val original = requireNotNull(repository.task(owner, "task"))
        repository.put(owner, "task", original.id, original.copy(state = "failed", error = "Interrupted"))
        assistant = assistant.copy(memoryContext = "fantasy")
        assertTrue(runCatching { controller.retry(owner, "task") }.isFailure)
        assertEquals("failed", repository.task(owner, "task")?.state)
        assertEquals("real", repository.task(owner, "task")?.context)
        assistant = assistant.copy(memoryContext = "real")
        controller.retry(owner, "task")
        assertEquals("queued", repository.task(owner, "task")?.state)
    }

    @Test fun repeatedInterruptionsDoNotCreateAnUnboundedPaidRetryLoop() = runBlocking {
        controller().enqueue(owner, "task", request)
        val task = requireNotNull(repository.task(owner, "task"))
        repository.put(owner, "task", task.id, task.copy(state = "running", attempts = 3))
        val calls = AtomicInteger()
        MemoryTaskRunner(repository, processor { calls.incrementAndGet(); proposal() })
            .runOne(owner, UUID.randomUUID().toString()) {}
        assertEquals(0, calls.get())
        assertEquals("failed", repository.task(owner, "task")?.state)
        assertTrue(repository.task(owner, "task")?.error.orEmpty().contains("interrupted"))
    }

    @Test fun refiningAndApplyingDoesNotLeaveAReadyTaskWithNoDraft()=runBlocking {
        val controller=controller()
        val runner=MemoryTaskRunner(repository,processor{proposal()})
        controller.enqueue(owner,"first",request)
        runner.runOne(owner,"worker-1"){}
        val first=requireNotNull(repository.task(owner,"first"))
        controller.enqueue(owner,"refined",request,previousDraftId=first.draftId)
        runner.runOne(owner,"worker-2"){}
        assertNull(repository.task(owner,"first"))
        assertEquals(1,repository.view(owner).drafts.size)
        val draft=repository.view(owner).drafts.single()
        repository.apply(owner,draft)
        assertTrue(repository.tasks(owner).isEmpty())
        assertTrue(repository.view(owner).drafts.isEmpty())
        assertEquals("Has a cat named Milo",repository.view(owner).facts.single().text)
    }

    @Test fun foregroundIsEstablishedBeforeSendingARequestAndActivityIsDurable() = runBlocking {
        controller().enqueue(owner, "task", request)
        var foreground = false
        MemoryTaskRunner(repository, processor { assertTrue(foreground); proposal() })
            .runOne(owner, UUID.randomUUID().toString()) { foreground = true }
        val ready = requireNotNull(repository.task(owner, "task"))
        assertEquals("ready", ready.state)
        assertTrue(ready.activities.any { it.label == "Read memory" })
        assertTrue(ready.activities.any { it.label == "Prepare changes" && !it.running })
    }

    @Test fun malformedHistoryDoesNotPoisonLaterSourcesOrLeaveAnInterruptedMarker() = runBlocking {
        assistant = assistant.copy(memoryExperience = "BASIC", memoryDreaming = true)
        addChat(listOf("This is the malformed source.", "$request."))
        val calls = AtomicInteger()
        val processor = processor {
            when (calls.incrementAndGet()) { 1, 2 -> "not JSON"; else -> proposal("source") }
        }
        assertFalse(MemoryLearningPass(repository, processor, owner, "worker") {}.run())
        val view = repository.view(owner)
        assertEquals(listOf("Has a cat named Milo"), view.notes.map { it.body })
        assertEquals(3, calls.get()) // One bounded repair for the bad source, then the healthy source.
        assertFalse(view.metadata.paused)
        assertNull(view.metadata.inFlight)
        assertNull(view.metadata.inFlightWorker)
        assertTrue(view.metadata.error.orEmpty().contains("1 parts"))
        assertFalse(view.metadata.error.orEmpty().contains("billed"))
        MemoryLearningPass(repository, processor, owner, "next-worker") {}.run()
        assertEquals(3, calls.get()) // Receipts also checkpoint rejected sources until explicit Retry.
    }

    @Test fun uncertainTemporaryDetailDoesNotAbortDurableSiblingInAnAutomaticPass() = runBlocking {
        assistant = assistant.copy(memoryExperience = "BASIC", memoryDreaming = true)
        addChat(listOf("I might shower later. $request."))
        val output = JsonInstant.encodeToString(MemoryProposal(memories = listOf(
            MemoryCandidate(text = "Plans to shower later", kind = "temporary", source = "source", quote = "I might shower later"),
            MemoryCandidate(text = "Has a cat named Milo", topic = "Pets", source = "source", quote = request)
        )))
        MemoryLearningPass(repository, processor { output }, owner, "worker") {}.run()
        val view = repository.view(owner)
        assertEquals(listOf("Has a cat named Milo"), view.notes.map { it.body })
        assertFalse(view.metadata.paused)
        assertNull(view.metadata.inFlight)
        assertTrue(repository.dao.all(owner).any { it.kind == "rejected" })
    }

    @Test fun basicExplicitUpdateOrganizesLegacyNotesWithoutDestroyingTheEvidence() = runBlocking {
        assistant = assistant.copy(memoryExperience = "BASIC", memoryDreaming = false)
        repository.saveNote(owner, MemoryNote("legacy-7", "Saved memory", paragraphs = listOf(
            MemoryParagraph("source-paragraph", "$request.", listOf(MemoryEvidence(origin = "imported note")))
        )), null)
        repository.put(owner, "meta", "meta", repository.metadata(owner).copy(pendingRefresh = true))
        val calls = AtomicInteger()
        MemoryLearningPass(repository, processor { calls.incrementAndGet(); proposal("source") }, owner, "worker") {}.run()
        val notes = repository.view(owner).notes
        assertEquals("$request.", notes.single { it.id == "legacy-7" }.body)
        val organized = notes.single { it.id != "legacy-7" }
        assertEquals("Pets", organized.title)
        assertEquals("Has a cat named Milo", organized.body)
        assertEquals("legacy-7", organized.paragraphs.single().evidence.single().messageId)
        assertFalse(repository.metadata(owner).pendingRefresh)
        MemoryLearningPass(repository, processor { calls.incrementAndGet(); proposal("source") }, owner, "worker-next") {}.run()
        assertEquals(1, calls.get())
    }

    @Test fun pausingDuringAnAutomaticModelCallRejectsItsLateChanges() = runBlocking {
        assistant = assistant.copy(memoryExperience = "BASIC", memoryDreaming = true)
        addChat(listOf("$request."))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val processor = processor { entered.complete(Unit); release.await(); proposal("source") }
        val running = launch(Dispatchers.IO) { MemoryLearningPass(repository, processor, owner, "worker") {}.run() }
        withTimeout(10_000) { entered.await() }
        repository.configure(owner) { it.copy(paused = true, inFlight = null, inFlightWorker = null) }
        release.complete(Unit)
        withTimeout(10_000) { running.join() }
        assertTrue(repository.metadata(owner).paused)
        assertTrue(repository.view(owner).notes.isEmpty())
        assertTrue(repository.dao.all(owner).none { it.kind == "receipt" })
    }
}
