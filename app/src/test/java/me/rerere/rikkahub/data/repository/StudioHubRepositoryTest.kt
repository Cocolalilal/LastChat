package me.rerere.rikkahub.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.service.StudioReminderScheduler
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StudioHubRepositoryTest {

    private lateinit var appContext: Application
    private lateinit var fakeWorkspaceDao: FakeWorkspaceDao
    private lateinit var fakeScheduler: FakeReminderScheduler
    private lateinit var repo: StudioHubRepository
    private lateinit var externalTempFile: File

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        File(appContext.filesDir, "studio").deleteRecursively()
        File(appContext.filesDir, "studio_attachments").deleteRecursively()
        File(appContext.filesDir, "workspaces").deleteRecursively()

        fakeWorkspaceDao = FakeWorkspaceDao()
        fakeScheduler = FakeReminderScheduler()
        repo = StudioHubRepository(
            context = appContext,
            workspaceDao = fakeWorkspaceDao,
            reminderScheduler = fakeScheduler,
            autoLoadOnInit = false,
        )
        externalTempFile = Files.createTempFile("studio-test-img-", ".png").toFile().apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
    }

    @After
    fun tearDown() {
        externalTempFile.delete()
        File(appContext.filesDir, "studio").deleteRecursively()
        File(appContext.filesDir, "studio_attachments").deleteRecursively()
        File(appContext.filesDir, "workspaces").deleteRecursively()
    }

    @Test
    fun crudAndBriefSummaryWorkWithoutWorkspace() = runBlocking {
        val created = repo.createItem(
            type = StudioItemType.TASK_LIST,
            title = "Release Prep",
            body = "Verify CI and push final build.",
            checklist = listOf(
                StudioChecklistEntry(id = "e1", text = "Run unit tests", isChecked = true),
                StudioChecklistEntry(id = "e2", text = "Push commit", isChecked = false),
            ),
            attachmentSources = listOf(externalTempFile.absolutePath),
            tags = listOf("release", "ci"),
            isPinned = true,
            reminderAtMillis = System.currentTimeMillis() + 60_000L,
        )

        assertTrue(created.briefSummary.length <= 140)
        assertTrue(created.briefSummary.contains("1/2 done"))
        assertEquals(1, created.attachmentPaths.size)
        assertTrue(File(created.attachmentPaths.single()).exists())
        assertTrue(created.attachmentPaths.single().contains("studio_attachments"))
        assertEquals(listOf(created.id), fakeScheduler.scheduledIds)

        val toggled = repo.toggleChecklistEntry(created.id, "e2")
        assertNotNull(toggled)
        assertTrue(toggled!!.briefSummary.contains("2/2 done"))

        val archived = repo.toggleArchive(created.id)
        assertTrue(archived!!.isArchived)
        assertTrue(created.id in fakeScheduler.cancelledIds)

        val fetched = repo.getItem(created.id)
        assertNotNull(fetched)
        assertEquals("Release Prep", fetched!!.title)

        val deleted = repo.deleteItem(created.id)
        assertTrue(deleted)
        assertNull(repo.getItem(created.id))
        assertFalse(File(created.attachmentPaths.single()).exists())
    }

    @Test
    fun syncsItemsAndMasterIndexToWorkspace() = runBlocking {
        fakeWorkspaceDao.workspaces.value = listOf(
            WorkspaceEntity(
                id = "ws-1",
                name = "Main",
                root = "ws-1",
                createdAt = 1L,
                updatedAt = 1L,
            )
        )

        val note = repo.createItem(
            type = StudioItemType.NOTE,
            title = "Architecture Note",
            body = "Keep Keep-style cards scannable and crisp.",
            tags = listOf("ui", "studio"),
            createdBy = "assistant",
        )

        val studioDir = File(appContext.filesDir, "workspaces/ws-1/files/studio")
        val mdFile = File(studioDir, "${note.id}.md")
        val jsonFile = File(studioDir, "${note.id}.json")
        val indexFile = File(studioDir, "index.json")

        assertTrue(mdFile.exists())
        assertTrue(jsonFile.exists())
        assertTrue(indexFile.exists())
        assertTrue(mdFile.readText().contains("Architecture Note"))

        val index = JsonInstant.decodeFromString<StudioMasterIndex>(indexFile.readText())
        assertEquals(1, index.items.size)
        assertEquals(note.id, index.items.single().id)
        assertEquals("/workspace/studio/${note.id}.md", index.items.single().path)

        repo.deleteItem(note.id)
        assertFalse(mdFile.exists())
        assertFalse(jsonFile.exists())
        val updatedIndex = JsonInstant.decodeFromString<StudioMasterIndex>(indexFile.readText())
        assertTrue(updatedIndex.items.isEmpty())
    }

    private class FakeWorkspaceDao : WorkspaceDAO {
        val workspaces = MutableStateFlow<List<WorkspaceEntity>>(emptyList())
        override fun listFlow(): Flow<List<WorkspaceEntity>> = workspaces
        override suspend fun getById(id: String): WorkspaceEntity? = workspaces.value.firstOrNull { it.id == id }
        override suspend fun upsert(workspace: WorkspaceEntity) {
            workspaces.value = workspaces.value.filterNot { it.id == workspace.id } + workspace
        }
        override suspend fun getAll(): List<WorkspaceEntity> = workspaces.value
        override suspend fun updateShellStatus(id: String, shellStatus: String, updatedAt: Long): Int = 1
        override suspend fun deleteById(id: String): Int {
            workspaces.value = workspaces.value.filterNot { it.id == id }
            return 1
        }
    }

    private class FakeReminderScheduler : StudioReminderScheduler {
        val scheduledIds = mutableListOf<String>()
        val cancelledIds = mutableListOf<String>()

        override fun schedule(item: StudioHubItem) {
            if (item.reminderAtMillis == null || item.isCompleted || item.isArchived || item.reminderTriggered) {
                cancel(item.id)
                return
            }
            scheduledIds += item.id
        }

        override fun cancel(itemId: String) {
            cancelledIds += itemId
        }
    }
}
