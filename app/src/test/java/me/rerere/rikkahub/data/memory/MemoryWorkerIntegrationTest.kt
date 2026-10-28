package me.rerere.rikkahub.data.memory

import android.app.Application
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.rag.EmbeddingResult
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateCache
import me.rerere.rikkahub.data.datastore.*
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Runs the actual CoroutineWorker, including foreground promotion, not only the request helper. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class MemoryWorkerIntegrationTest {
    private lateinit var app:Application
    private lateinit var appScope:AppScope
    private lateinit var settings:SettingsStore
    private lateinit var db:AppDatabase
    private lateinit var repository:CharacterMemoryRepository
    private lateinit var processor:MemoryProcessor
    private val assistant=Assistant(enableMemory=true,memoryExperience="BASIC")
    private val owner get()=assistant.id.toString()
    private val foreground=mutableListOf<ForegroundInfo>()

    @Before fun setup()=runBlocking {
        app=ApplicationProvider.getApplicationContext()
        appScope=AppScope()
        startKoin{modules(module {
            single{settings}
            single{repository}
            single{processor}
            single{MemoryTaskRunner(repository,processor)}
            single<MessageTemplateCache>{object:MessageTemplateCache{override fun invalidateAll(){}}}
        })}
        settings=SettingsStore(app,appScope,QuickSettingsCache(app),SecretKeyManager(SecureStore(app)))
        settings.settingsFlow.first{!it.init}
        settings.update{it.copy(assistants=listOf(assistant),providers=emptyList())}
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        repository=CharacterMemoryRepository(db,object:MemoryEmbeddingClient {
            override fun getEmbeddingModelId(assistantId:String?)="test"
            override suspend fun embedWithModelId(text:String,assistantId:String?)=EmbeddingResult(listOf(List(768){0f}),"test")
        }){assistant.takeIf{it.id.toString()==owner}}
        repository.initialise(owner)
    }
    @After fun close(){appScope.cancel();db.close();stopKoin()}
    private fun worker()=TestListenableWorkerBuilder<MemoryLearningWorker>(app)
        .setInputData(workDataOf("owner" to owner))
        .setForegroundUpdater(object:ForegroundUpdater {
            override fun setForegroundAsync(context:Context,id:UUID,info:ForegroundInfo):ListenableFuture<Void> =
                CallbackToFutureAdapter.getFuture { completer ->foreground+=info;completer.set(null);"memory foreground" }
        }).build()

    @Test fun realWorkerPromotesForegroundBeforeNetworkAndPersistsReview()=runBlocking {
        processor=MemoryProcessor(repository,object:MemoryTextClient {
            override suspend fun generate(owner:String,prompt:String,maxTokens:Int):String {
                assertTrue(foreground.isNotEmpty())
                return JsonInstant.encodeToString(MemoryProposal(memories=listOf(MemoryCandidate(
                    text="Has a cat named Milo",topic="Pets",source="request",quote="My cat is Milo"))))
            }
        })
        MemoryTaskController(app,repository,appScope){}.enqueue(owner,"task","My cat is Milo")
        assertEquals(ListenableWorker.Result.success(),worker().doWork())
        assertEquals("ready",repository.task(owner,"task")?.state)
        assertTrue(repository.view(owner).notes.isEmpty())
        assertEquals(1,repository.view(owner).drafts.size)
        assertEquals("memory-work",foreground.first().notification.channelId)
        assertEquals("Cancel",foreground.first().notification.actions.single().title.toString())
        assertNull(repository.metadata(owner).inFlight)
    }

    @Test fun realWorkerQuarantinesBadOutputWithoutPausingOrRetryingHealthyQueue()=runBlocking {
        repository.saveNote(owner,MemoryNote("legacy-1","Saved memory",paragraphs=listOf(
            MemoryParagraph("p","My cat is Milo",authored=true))),null)
        repository.put(owner,"meta","meta",repository.metadata(owner).copy(pendingRefresh=true))
        var calls=0
        processor=MemoryProcessor(repository,object:MemoryTextClient {
            override suspend fun generate(owner:String,prompt:String,maxTokens:Int):String {calls++;return "invalid JSON"}
        })
        assertEquals(ListenableWorker.Result.success(),worker().doWork())
        assertEquals(2,calls)
        val view=repository.view(owner)
        assertEquals("My cat is Milo",view.notes.single().body)
        assertFalse(view.metadata.paused)
        assertFalse(view.metadata.pendingRefresh)
        assertNull(view.metadata.inFlight)
        assertFalse(view.metadata.error.orEmpty().contains("billed"))
        assertEquals("Pause",foreground.first().notification.actions.single().title.toString())
    }
}
