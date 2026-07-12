package me.rerere.rikkahub.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryConversionDirection
import me.rerere.rikkahub.data.model.MemoryConversionEntryProposal
import me.rerere.rikkahub.data.model.MemoryConversionPreview
import me.rerere.rikkahub.data.model.MemoryDocumentKind
import me.rerere.rikkahub.data.model.MemorySystemType
import me.rerere.rikkahub.data.repository.HybridMemoryRepository
import me.rerere.rikkahub.data.ai.MemoryContextCoordinator
import kotlin.uuid.Uuid
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.db.entity.MemoryConversationDigestEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphNodeEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HybridMemoryConversionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: HybridMemoryRepository
    private val assistant = Assistant(enableMemory = true, memorySystem = MemorySystemType.DOCUMENT_BASED)

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = HybridMemoryRepository(database, database.hybridMemoryDao(), database.memoryDao(), null, database.embeddingCacheDao())
    }

    @After fun close() = database.close()

    @Test
    fun entryToDocument_applyIsTransactionalAndRejectsStalePreview() = runBlocking {
        repository.ensureDocuments(assistant)
        database.memoryDao().insertMemory(MemoryEntity(assistantId = assistant.id.toString(), content = "Luna is the user's cat", createdAt = 100, lastAccessedAt = 100))
        val input = repository.buildConversionInput(assistant, MemoryConversionDirection.ENTRY_TO_DOCUMENT)
        val preview = MemoryConversionPreview(
            id = "preview-1", assistantId = assistant.id.toString(), direction = MemoryConversionDirection.ENTRY_TO_DOCUMENT,
            sourceRevision = input.sourceRevision, previousWatermark = input.previousWatermark,
            expectedUserProfileRevision = input.userProfileRevision, expectedCharacterMemoryRevision = input.characterMemoryRevision,
            userProfileReplacement = "The user has a cat named Luna.", characterMemoryReplacement = "We learned about Luna.", summary = "Import Luna",
        )
        assertTrue(repository.applyConversionPreview(assistant, preview).applied)
        val documents = repository.getDocuments(assistant).associateBy { it.kind }
        assertEquals("The user has a cat named Luna.", documents.getValue(MemoryDocumentKind.USER_PROFILE.name).content)
        assertEquals(input.sourceRevision, database.hybridMemoryDao().getConversionState(assistant.id.toString(), MemoryConversionDirection.ENTRY_TO_DOCUMENT.name)?.sourceRevision)

        database.memoryDao().insertMemory(MemoryEntity(assistantId = assistant.id.toString(), content = "Newer fact", createdAt = 200, lastAccessedAt = 200))
        assertFalse(repository.applyConversionPreview(assistant, preview).applied)
    }

    @Test
    fun documentToEntry_deduplicatesAndAdvancesWatermarkWithApply() = runBlocking {
        repository.ensureDocuments(assistant)
        val input = repository.buildConversionInput(assistant, MemoryConversionDirection.DOCUMENT_TO_ENTRY)
        val preview = MemoryConversionPreview(
            id = "preview-2", assistantId = assistant.id.toString(), direction = MemoryConversionDirection.DOCUMENT_TO_ENTRY,
            sourceRevision = input.sourceRevision, previousWatermark = input.previousWatermark,
            entryProposals = listOf(
                MemoryConversionEntryProposal("The user likes tea."),
                MemoryConversionEntryProposal("  the USER likes tea.  "),
            ),
            summary = "Create entries",
        )
        val result = repository.applyConversionPreview(assistant, preview)
        assertTrue(result.applied)
        assertEquals(1, result.addedEntries)
        assertEquals(1, database.memoryDao().getMemoriesOfAssistant(assistant.id.toString()).size)
    }

    @Test
    fun documentUpdates_enforceLimitsRevisionsRestoreAndStableConversationSnapshots() = runBlocking {
        repository.ensureDocuments(assistant)
        val initial = repository.getDocuments(assistant).first { it.kind == MemoryDocumentKind.USER_PROFILE.name }
        val saved = repository.updateDocument(assistant, MemoryDocumentKind.USER_PROFILE, "First profile", initial.revision, "manual", "test")
        assertTrue(saved.applied)
        val stale = repository.updateDocument(assistant, MemoryDocumentKind.USER_PROFILE, "Stale write", initial.revision, "manual", "test")
        assertFalse(stale.applied)
        val oversized = repository.updateDocument(assistant, MemoryDocumentKind.USER_PROFILE, "x".repeat(assistant.userProfileCharLimit + 1), saved.revision, "manual", "test")
        assertFalse(oversized.applied)

        val coordinator = MemoryContextCoordinator(null, repository)
        val conversation = Uuid.random()
        val firstSnapshot = coordinator.contextFor(assistant, "", conversation, stableConversationSnapshot = true)
        val second = repository.updateDocument(assistant, MemoryDocumentKind.USER_PROFILE, "Second profile", saved.revision, "manual", "test")
        assertTrue(second.applied)
        val stableSnapshot = coordinator.contextFor(assistant, "", conversation, stableConversationSnapshot = true)
        assertTrue(firstSnapshot.promptText.contains("First profile"))
        assertEquals(firstSnapshot.promptText, stableSnapshot.promptText)
        val reopened = coordinator.contextFor(assistant, "", Uuid.random(), stableConversationSnapshot = true)
        assertTrue(reopened.promptText.contains("Second profile"))

        val restored = repository.restoreDocument(assistant, MemoryDocumentKind.USER_PROFILE, saved.revision)
        assertTrue(restored.applied)
        assertTrue(restored.revision > second.revision)
    }

    @Test
    fun rawIndex_tracksOnlySelectedBranchAndDeactivatesDeletedVersions() = runBlocking {
        val first = UIMessage.user("First branch: apples")
        val second = UIMessage.user("Second branch: oranges")
        val node = MessageNode(messages = listOf(first, second), selectIndex = 1)
        val conversation = Conversation.ofId(Uuid.random(), assistant.id, listOf(node))
        repository.indexConversation(conversation)
        assertFalse(database.hybridMemoryDao().findSearchRow("RAW_CHAT", first.id.toString())!!.active)
        assertTrue(database.hybridMemoryDao().findSearchRow("RAW_CHAT", second.id.toString())!!.active)

        repository.indexConversation(conversation.copy(messageNodes = listOf(node.copy(selectIndex = 0))))
        assertTrue(database.hybridMemoryDao().findSearchRow("RAW_CHAT", first.id.toString())!!.active)
        assertFalse(database.hybridMemoryDao().findSearchRow("RAW_CHAT", second.id.toString())!!.active)

        repository.indexConversation(conversation.copy(messageNodes = emptyList()))
        assertFalse(database.hybridMemoryDao().findSearchRow("RAW_CHAT", first.id.toString())!!.active)
    }

    @Test
    fun coordinatorReturnsNoMemoryWhenCallerMarksChatTemporary() = runBlocking {
        repository.ensureDocuments(assistant)
        val coordinator = MemoryContextCoordinator(null, repository)
        val context = coordinator.contextFor(assistant, "anything", allowMemory = false)
        assertTrue(context.memories.isEmpty())
        assertTrue(context.usedSources.isEmpty())
        assertEquals("", context.promptText)
    }

    @Test
    fun deletingChatRemovesRawTextButKeepsDistilledKnowledgeWithUnavailableSource() = runBlocking {
        val message = UIMessage.user("Private source detail")
        val conversation = Conversation.ofId(Uuid.random(), assistant.id, listOf(MessageNode.of(message)))
        repository.indexConversation(conversation)
        repository.upsertDigest(MemoryConversationDigestEntity(
            id = "digest-delete", assistantId = assistant.id.toString(), conversationId = conversation.id.toString(),
            summary = "Distilled detail", recordedAt = 10,
        ))
        repository.upsertGraphNode(
            MemoryGraphNodeEntity("node-delete", assistant.id.toString(), "Detail", "detail", "topic", createdAt = 10, updatedAt = 10),
            conversation.id.toString(), "Private source detail",
        )

        repository.onConversationDeleted(conversation.id.toString())
        assertEquals(null, database.hybridMemoryDao().findSearchRow("RAW_CHAT", message.id.toString()))
        assertFalse(database.hybridMemoryDao().getDigests(assistant.id.toString()).first { it.id == "digest-delete" }.sourceAvailable)
        assertFalse(database.hybridMemoryDao().getGraphProvenance("node-delete").first().sourceAvailable)
    }
}
