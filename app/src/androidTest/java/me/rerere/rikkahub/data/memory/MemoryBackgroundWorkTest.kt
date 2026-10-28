package me.rerere.rikkahub.data.memory

import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.rag.EmbeddingResult
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.assistant.detail.CharacterMemoryPage
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.*
import org.junit.Assert.*
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/** Real Android WorkManager/foreground service, with a controlled delayed provider. */
class MemoryBackgroundWorkTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val assistant=Assistant(name="Background memory QA",enableMemory=true,memoryExperience="BASIC")
    private val owner get()=assistant.id.toString()
    private lateinit var db:AppDatabase
    private lateinit var repository:CharacterMemoryRepository
    private lateinit var processor:MemoryProcessor
    private lateinit var controller:MemoryTaskController
    private lateinit var settings:SettingsStore
    private val entered=CompletableDeferred<Unit>()
    private val release=CompletableDeferred<Unit>()
    private var pageVisible by mutableStateOf(true)
    private val overrides=module {
        single<CharacterMemoryRepository>{repository}
        single<MemoryProcessor>{processor}
        single<MemoryTaskRunner>{MemoryTaskRunner(repository,processor)}
    }
    @Before fun setup()=runBlocking {
        val koin=GlobalContext.get()
        settings=koin.get()
        settings.settingsFlow.first{!it.init}
        settings.update{it.copy(assistants=it.assistants+assistant)}
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        repository=CharacterMemoryRepository(db,object:MemoryEmbeddingClient {
            override fun getEmbeddingModelId(assistantId:String?)="test"
            override suspend fun embedWithModelId(text:String,assistantId:String?)=EmbeddingResult(listOf(List(768){0f}),"test")
        }) { id->assistant.takeIf{it.id.toString()==id} }
        repository.initialise(owner)
        processor=MemoryProcessor(repository,object:MemoryTextClient {
            override suspend fun generate(owner:String,prompt:String,maxTokens:Int):String {
                entered.complete(Unit)
                release.await()
                return JsonInstant.encodeToString(MemoryProposal(memories=listOf(MemoryCandidate(
                    text="Has a cat named Milo",topic="Pets",source="request",quote="My cat is Milo"))))
            }
        })
        loadKoinModules(overrides)
        controller=MemoryTaskController(context,repository,koin.get<AppScope>())
        compose.activityRule.scenario.onActivity {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it.window,false)
            it.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            CompositionLocalProvider(LocalNavController provides rememberNavController(),LocalSettings provides settings.settingsFlow.value) {
                MaterialTheme { Surface(Modifier.fillMaxSize()) {
                    if(pageVisible)CharacterMemoryPage(assistant,{},repository,processor,settings,
                        koin.get(),koin.get(),controller) else Text("Another page")
                } }
            }
        }
    }
    @After fun cleanup()=runBlocking {
        release.complete(Unit)
        WorkManager.getInstance(context).cancelUniqueWork("character-memory:$owner").await()
        settings.update{it.copy(assistants=it.assistants.filterNot{item->item.id==assistant.id})}
        unloadKoinModules(overrides)
        db.close()
    }
    private fun capture(name:String) {
        val bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(bitmap).let { image ->
            java.io.File(context.getExternalFilesDir(null),"memory-qa-$name.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
    }
    @Test fun profileOpensForReadingAndFloatingControlsStayAboveTheKeyboard()=runBlocking {
        repository.saveNote(owner,MemoryNote("profile","About you","You",paragraphs=listOf(MemoryParagraph("p","Lives in Zurich",authored=true))),null)
        repeat(20){i->repository.saveNote(owner,MemoryNote("topic-$i","Topic $i",paragraphs=listOf(MemoryParagraph("p-$i","Saved detail $i",authored=true))),null)}
        compose.waitUntil(30_000){compose.onAllNodesWithText("Updated just now").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText(settings.settingsFlow.value.displaySetting.userNickname.ifBlank{"You"}).onLast().performClick()
        compose.onNodeWithText("Lives in Zurich").assertIsDisplayed()
        compose.onNodeWithText("Details").assertExists()
        compose.onNodeWithText("Save").assertDoesNotExist()
        capture("profile-reader")
        compose.onNodeWithContentDescription("Close profile").performClick()
        compose.onNode(hasSetTextAction()).performClick()
        compose.waitUntil(30_000){var visible=false;compose.activityRule.scenario.onActivity{visible=it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true};visible}
        var keyboardTop=0f
        compose.activityRule.scenario.onActivity {
            keyboardTop=(it.windowManager.currentWindowMetrics.bounds.bottom-it.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom).toFloat()
        }
        assertTrue(compose.onNodeWithTag("memory-footer").fetchSemanticsNode().boundsInWindow.bottom<=keyboardTop+2)
        compose.onNodeWithTag("memory-scroll").performScrollToNode(hasText("Add a note"))
        assertTrue(compose.onNodeWithText("Add a note").fetchSemanticsNode().boundsInWindow.bottom<=compose.onNodeWithTag("memory-footer").fetchSemanticsNode().boundsInWindow.top+32)
        capture("keyboard-scroll")
    }
    @Test fun requestFinishesAfterScreenRemovalAndActivityBackgrounding()=runBlocking {
        compose.onNode(hasSetTextAction()).performTextInput("My cat is Milo")
        compose.onNodeWithContentDescription("Send memory request").performClick()
        withTimeout(90_000){entered.await()}
        assertEquals("running",repository.tasks(owner).single().state)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any{it.notification.channelId=="memory-work"})
        // Close the temporary UI and remove the page from composition, then stop the activity.
        compose.onNodeWithContentDescription("Close memory request").performClick()
        compose.runOnIdle{pageVisible=false}
        compose.onNodeWithText("Another page").assertExists()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        release.complete(Unit)
        withTimeout(90_000){while(repository.tasks(owner).single().state!="ready")delay(100)}
        assertTrue(repository.view(owner).notes.isEmpty()) // Applying remains an explicit user action.
        assertEquals(1,repository.view(owner).drafts.size)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle{pageVisible=true}
        compose.waitUntil(30_000){compose.onAllNodesWithText("Changes ready to review").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Changes ready to review").performClick()
        compose.onNodeWithText("Apply changes").performClick()
        withTimeout(30_000){while(repository.view(owner).notes.isEmpty())delay(100)}
        assertEquals("Has a cat named Milo",repository.view(owner).notes.single().body)
    }
}
