package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsSanitizationTest {

    @Test
    fun sanitize_removesInvalidReferencesAndClampsSearchSelection() {
        val validTagId = Uuid.random()
        val orphanedTagId = Uuid.random()
        val orphanedModelId = Uuid.random()

        val assistant = DEFAULT_ASSISTANTS.first().copy(
            tags = listOf(validTagId, orphanedTagId),
            searchMode = AssistantSearchMode.Provider(index = 5)
        )

        val settings = Settings(
            assistants = listOf(assistant),
            assistantId = assistant.id,
            assistantTags = listOf(Tag(id = validTagId, name = "Valid")),
            favoriteModels = listOf(orphanedModelId),
            searchServices = emptyList(),
            searchServiceSelected = 9,
        )

        val (sanitized, cleanup) = settings.sanitize()

        assertEquals(listOf(validTagId), sanitized.assistants.single().tags)
        assertTrue(sanitized.favoriteModels.isEmpty())
        assertEquals(0, sanitized.searchServiceSelected)
        assertEquals(AssistantSearchMode.Off, sanitized.assistants.single().searchMode)
        assertEquals(1, cleanup.invalidSearchModeCount)
        assertEquals(1, cleanup.orphanedTagReferences)
        assertEquals(1, cleanup.orphanedModelReferences)
    }
}
