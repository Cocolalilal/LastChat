package me.rerere.rikkahub.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.normalizeMemorySettings
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class MemoryRecallIntegrationTest {
    @Test
    fun advancedMemoryCannotRetainAHiddenDisabledRuntimeShape() {
        val assistant = Settings(
            assistants = listOf(
                Assistant(
                    enableMemory = false,
                    useRagMemoryRetrieval = false,
                    enableMemoryConsolidation = true,
                    ragIncludeCore = false,
                    ragIncludeEpisodes = false,
                    ragLimit = 0,
                )
            )
        ).normalizeMemorySettings().assistants.single()

        assertTrue(assistant.enableMemory)
        assertTrue(assistant.useRagMemoryRetrieval)
        assertTrue(assistant.ragIncludeCore)
        assertTrue(assistant.ragIncludeEpisodes)
        assertEquals(1, assistant.ragLimit)
    }

    @Test
    fun durableCoreMemorySurvivesUnavailableVectorRecall() = runBlocking {
        val koin = GlobalContext.get()
        val repository = koin.get<MemoryRepository>()
        val settings = koin.get<SettingsStore>().settingsFlow.value
        val assistant = settings.assistants.first()
        val marker = "durable-memory-fallback-${System.nanoTime()}"
        val memory = repository.addMemory(
            assistantId = assistant.id.toString(),
            content = marker,
        )

        try {
            // Deliberately avoid lexical overlap. This exercises the final durable fallback when
            // query embeddings are disabled, unavailable, stale, or rejected by the live cutoff.
            val recalled = repository.retrieveRelevantMemories(
                assistantId = assistant.id.toString(),
                query = "completely unrelated retrieval probe",
                limit = 3,
                similarityThreshold = 1f,
                includeCore = true,
                includeEpisodes = true,
            )

            assertTrue(recalled.any { it.id == memory.id && it.content == marker })
        } finally {
            repository.deleteMemory(memory.id)
        }
    }

    @Test
    fun rollbackCatchUpFindsConsolidatedConversationWithoutStableEpisode() = runBlocking {
        val database = GlobalContext.get().get<AppDatabase>()
        val settings = GlobalContext.get().get<SettingsStore>().settingsFlow.value
        val assistantId = settings.assistants.first().id.toString()
        val conversation = ConversationEntity(
            id = "memory-catch-up-${System.nanoTime()}",
            assistantId = assistantId,
            title = "Memory catch-up probe",
            nodes = "[]",
            createAt = System.currentTimeMillis(),
            updateAt = System.currentTimeMillis(),
            truncateIndex = -1,
            chatSuggestions = "[]",
            isPinned = false,
            isConsolidated = true,
        )
        val dao = database.conversationDao()
        dao.insert(conversation)

        try {
            assertTrue(
                dao.getConsolidatedConversationsMissingEpisode(assistantId, 100)
                    .any { it.id == conversation.id }
            )
            val episodeId = database.chatEpisodeDao().insertEpisode(
                ChatEpisodeEntity(
                    assistantId = assistantId,
                    content = "Catch-up episode",
                    startTime = conversation.createAt,
                    endTime = conversation.updateAt,
                    lastAccessedAt = conversation.updateAt,
                    conversationId = conversation.id,
                )
            ).toInt()
            try {
                assertFalse(
                    dao.getConsolidatedConversationsMissingEpisode(assistantId, 100)
                        .any { it.id == conversation.id }
                )
            } finally {
                database.chatEpisodeDao().deleteEpisode(episodeId)
            }
        } finally {
            dao.delete(conversation)
        }
    }
}
