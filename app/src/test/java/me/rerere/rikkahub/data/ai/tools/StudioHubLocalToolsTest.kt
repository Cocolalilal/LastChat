package me.rerere.rikkahub.data.ai.tools

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.StudioHubItem
import me.rerere.rikkahub.data.repository.StudioHubRepository
import me.rerere.rikkahub.service.StudioReminderScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StudioHubLocalToolsTest {

    private lateinit var appContext: Application
    private lateinit var studioRepo: StudioHubRepository

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        File(appContext.filesDir, "studio").deleteRecursively()
        File(appContext.filesDir, "studio_attachments").deleteRecursively()

        val emptyDao = object : WorkspaceDAO {
            override fun listFlow(): Flow<List<WorkspaceEntity>> = MutableStateFlow(emptyList())
            override suspend fun getById(id: String): WorkspaceEntity? = null
            override suspend fun upsert(workspace: WorkspaceEntity) = Unit
            override suspend fun getAll(): List<WorkspaceEntity> = emptyList()
            override suspend fun updateShellStatus(id: String, shellStatus: String, updatedAt: Long): Int = 0
            override suspend fun deleteById(id: String): Int = 0
        }
        val noopScheduler = object : StudioReminderScheduler {
            override fun schedule(item: StudioHubItem) = Unit
            override fun cancel(itemId: String) = Unit
        }
        studioRepo = StudioHubRepository(
            context = appContext,
            workspaceDao = emptyDao,
            reminderScheduler = noopScheduler,
            autoLoadOnInit = false,
        )
    }

    @After
    fun tearDown() {
        File(appContext.filesDir, "studio").deleteRecursively()
        File(appContext.filesDir, "studio_attachments").deleteRecursively()
    }

    @Test
    fun studioToolsSupportFullCrudLifecycle() = runBlocking {
        val tools = buildStudioHubTools(studioRepo).associateBy { it.name }

        assertEquals(
            setOf(
                "studio_list_items",
                "studio_read_item",
                "studio_create_item",
                "studio_update_item",
                "studio_delete_item",
            ),
            tools.keys,
        )

        val createRes = tools.getValue("studio_create_item").execute(
            buildJsonObject {
                put("type", "TASK_LIST")
                put("title", "Grocery Run")
                putJsonArray("checklist") {
                    add(JsonPrimitive("Coffee"))
                    add(JsonPrimitive("Milk"))
                }
                put("pinned", true)
            }
        ).jsonObject

        assertEquals("created", createRes["status"]?.jsonPrimitive?.content)
        val itemId = createRes["id"]?.jsonPrimitive?.content!!

        val listRes = tools.getValue("studio_list_items").execute(
            buildJsonObject { put("query", "Grocery") }
        ).jsonObject
        val itemsArray = listRes["items"] as JsonArray
        assertEquals(1, itemsArray.size)
        assertEquals(itemId, itemsArray.first().jsonObject["id"]?.jsonPrimitive?.content)

        val updateRes = tools.getValue("studio_update_item").execute(
            buildJsonObject {
                put("id", itemId)
                putJsonArray("toggleChecklistItemTexts") { add(JsonPrimitive("Coffee")) }
                putJsonArray("addChecklistItems") { add(JsonPrimitive("Bread")) }
            }
        ).jsonObject
        assertEquals("updated", updateRes["status"]?.jsonPrimitive?.content)

        val readRes = tools.getValue("studio_read_item").execute(
            buildJsonObject { put("id", itemId) }
        ).jsonObject
        val checklist = readRes["checklist"]!!.jsonArray
        assertEquals(3, checklist.size)
        assertEquals("true", checklist[0].jsonObject["checked"]?.jsonPrimitive?.content)

        val deleteRes = tools.getValue("studio_delete_item").execute(
            buildJsonObject { put("id", itemId) }
        ).jsonObject
        assertEquals("deleted", deleteRes["status"]?.jsonPrimitive?.content)

        val afterDelete = tools.getValue("studio_read_item").execute(
            buildJsonObject { put("id", itemId) }
        ).jsonObject
        assertTrue(afterDelete.containsKey("error"))
    }
}
