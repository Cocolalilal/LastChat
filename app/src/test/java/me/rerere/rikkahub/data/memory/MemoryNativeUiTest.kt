package me.rerere.rikkahub.data.memory

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import me.rerere.common.platform.*
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.rag.EmbeddingResult
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateCache
import me.rerere.rikkahub.data.datastore.*
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.motion.MotionPolicy
import me.rerere.rikkahub.ui.pages.assistant.detail.CharacterMemoryPage
import me.rerere.rikkahub.ui.theme.Typography
import me.rerere.rikkahub.ui.theme.seafoamMintColorScheme
import me.rerere.rikkahub.ui.theme.withLastChatAmoledSurface
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryNativeUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var db:AppDatabase
    private lateinit var scope:AppScope
    private lateinit var settings:SettingsStore
    private lateinit var repository:CharacterMemoryRepository
    private lateinit var processor:MemoryProcessor
    private lateinit var decision:JevDecisionClient
    private lateinit var models:me.rerere.locallm.LocalModelStore
    private var assistant=Assistant(name="Generical",enableMemory=true,memoryExperience="ADVANCED")
    private val owner get()=assistant.id.toString()
    @Before fun setup()=runBlocking{
        val context:Application=RuntimeEnvironment.getApplication()
        startKoin{modules(module{single{settings};single<MessageTemplateCache>{object:MessageTemplateCache{override fun invalidateAll(){}}}})}
        scope=AppScope()
        val secrets=SecretKeyManager(SecureStore(context))
        settings=SettingsStore(context,scope,QuickSettingsCache(context),secrets)
        settings.settingsFlow.first{!it.init}
        settings.update{it.copy(assistants=listOf(assistant),providers=emptyList(),displaySetting=it.displaySetting.copy(enableUIHaptics=false))}
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        repository=CharacterMemoryRepository(db,object:MemoryEmbeddingClient{
            override fun getEmbeddingModelId(assistantId:String?)="test"
            override suspend fun embedWithModelId(text:String,assistantId:String?)=error("No native model in fixture")
        }){id->settings.settingsFlow.value.assistants.firstOrNull{it.id.toString()==id}}
        repository.initialise(owner)
        repository.put(owner,"node","clara",MemoryNode("clara","Clara","Person"))
        repository.put(owner,"fact","relationship",MemoryFact("relationship","Clara is your girlfriend.",objectId="clara",relation="partner",authored=true))
        repository.put(owner,"meta","meta",MemoryMetadata(migrated=true,activeAdvanced=true,summary=listOf(MemorySummarySection("Overview","Clara is your girlfriend.",listOf("relationship")))))
        processor=MemoryProcessor(repository,object:MemoryTextClient{
            override suspend fun generate(owner:String,prompt:String,maxTokens:Int)=JsonInstant.encodeToString(MemoryProposal(
                memories=listOf(MemoryCandidate(id="relationship",text="Clara is your ex-partner.",source="request",quote="Clara is my ex now",objectId="clara",relation="former partner"))))
        })
        decision=JevDecisionClient(object:PlatformHttpClient{
            override suspend fun execute(request:PlatformHttpRequest):PlatformHttpResponse=error("No network in fixture")
            override fun streamEvents(request:PlatformHttpRequest)=kotlinx.coroutines.flow.emptyFlow<PlatformServerEvent>()
        },secrets)
        models=me.rerere.locallm.LocalModelStore(context)
    }
    @After fun close(){scope.cancel();db.close();stopKoin()}
    private fun show(dark:Boolean=true,fontScale:Float=1f){
        compose.setContent{
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,fontScale),LocalNavController provides rememberNavController(),LocalSettings provides settings.settingsFlow.value,LocalMotionPolicy provides MotionPolicy(true)){
                MaterialTheme(colorScheme=seafoamMintColorScheme(dark).withLastChatAmoledSurface(dark),typography=Typography){
                    Surface(Modifier.fillMaxSize()){
                        CharacterMemoryPage(assistant,{},repository,processor,settings,models,decision)
                    }
                }
            }
        }
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("Memory settings").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun image(name:String){
        val dir=File("/workspace/attachments/memory-native-2026-10-27").apply{mkdirs()}
        val dialog=ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}
        lateinit var bitmap:Bitmap
        compose.runOnIdle {
            val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            val base=activity.window.decorView
            bitmap=Bitmap.createBitmap(base.width.coerceAtLeast(1),base.height.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            base.draw(canvas)
            if(dialog!=null){
                canvas.drawARGB(((dialog.window?.attributes?.dimAmount?:.32f)*255).toInt(),0,0,0)
                dialog.window?.decorView?.draw(canvas)
            }
        }
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun richerOverview()=runBlocking {
        val people=listOf("Leo Vance","Barnaby","Maya","Theo")
        val places=listOf("Seattle","Astoria","University of Oregon")
        val things=listOf("iPad Pro","Procreate","AutoCAD","Downtown Library Rooftop Garden","Urban gardens")
        for((i,name) in (people+places+things).withIndex()) repository.put(owner,"node","entity-$i",MemoryNode("entity-$i",name,if(i<people.size)"Person" else if(i<people.size+places.size)"Place" else "Thing"))
        val texts=listOf("Leo Vance is a freelance landscape architect living in Seattle.","He grew up in Astoria and graduated from the University of Oregon.","He shares his apartment with a rescued Golden Retriever named Barnaby.","He is leading the Downtown Library Rooftop Garden redesign and specializes in Urban gardens.","He sketches in Procreate on an iPad Pro and finalizes plans in AutoCAD.")
        for((i,text) in texts.withIndex()) repository.put(owner,"fact","profile-$i",MemoryFact("profile-$i",text,if(i<3)"Overview" else "Work & Routine",objectId="entity-${i}",authored=true))
        for(i in 0..17)repository.put(owner,"fact","detail-$i",MemoryFact("detail-$i","Demonstration supported detail $i",subject="entity-${i%12}",objectId="entity-${(i*3+2)%12}",authored=true))
        repository.put(owner,"meta","meta",MemoryMetadata(migrated=true,activeAdvanced=true,updatedAt=System.currentTimeMillis()-10800000,summary=listOf(
            MemorySummarySection("Overview",texts.take(3).joinToString(" "),(0..2).map{"profile-$it"}),
            MemorySummarySection("Work & Routine",texts.drop(3).joinToString(" "),(3..4).map{"profile-$it"}))))
    }
    @Test fun advancedNativeOverview(){richerOverview();show();image("advanced-dark")}
    @Test fun lightNativeOverview(){richerOverview();show(false);image("advanced-light")}
    @Test fun basicNativeGroupedNotes()=runBlocking {
        assistant=assistant.copy(memoryExperience="BASIC")
        settings.update{it.copy(assistants=listOf(assistant))}
        listOf("You" to "About you","Topics" to "Interests and Preferences","Topics" to "Pets","People" to "Clara","People" to "Corin").forEachIndexed{index,(category,title)->
            repository.saveNote(owner,MemoryNote("note-$index",title,category,paragraphs=listOf(MemoryParagraph("paragraph-$index","Example saved note",authored=true))),null)
        }
        show()
        compose.waitUntil(10000){compose.onAllNodesWithText("Interests and Preferences").fetchSemanticsNodes().isNotEmpty()}
        image("basic-dark")
    }
    @Test fun entitySheetHasSourcesAndGroupedActions(){
        show()
        compose.onNodeWithText("More about Clara").performClick()
        compose.onNodeWithText("Person").assertExists()
        compose.onNodeWithText("Forget").assertExists()
        image("entity-detail")
    }
    @Test @Config(qualifiers="w320dp-h720dp-mdpi") fun narrowLargeTextPopupKeepsApplyReachable()=runBlocking {
        show(fontScale=1.5f)
        compose.onNode(hasSetTextAction()).performTextInput("Clara is my ex now")
        compose.onNodeWithContentDescription("Send memory request").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("Apply changes").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Apply changes").assertIsDisplayed()
        compose.onNodeWithText("Discard").assertIsDisplayed()
        image("edit-large-text")
    }
    @Test fun editIsStagedThenAppliedThroughPopup()=runBlocking{
        show()
        compose.onNode(hasSetTextAction()).performTextInput("Clara is my ex now")
        compose.onNodeWithContentDescription("Send memory request").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("Apply changes").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("Clara is your girlfriend.",repository.view(owner).facts.single().text)
        image("edit-preview")
        compose.onNodeWithText("Apply changes").performClick()
        compose.waitUntil(10000){runBlocking{repository.view(owner).facts.single().text=="Clara is your ex-partner."}}
    }
    @Test fun discardPreservesCanonicalMemory()=runBlocking{
        show()
        compose.onNode(hasSetTextAction()).performTextInput("Clara is my ex now")
        compose.onNodeWithContentDescription("Send memory request").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("Discard").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(10000){runBlocking{repository.view(owner).drafts.isEmpty()}}
        assertEquals("Clara is your girlfriend.",repository.view(owner).facts.single().text)
    }
}
