package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.ai.buildSkillToolState
import me.rerere.rikkahub.data.model.*
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class AddonAssignmentTest {
    @Test fun defaultsAndExceptionsSurviveSettingsRoundTripAndApplyToFutureCharacters() {
        val included = Assistant(name = "Included")
        val excluded = Assistant(name = "Excluded")
        val future = Assistant(name = "Future")
        val skill = Skill(name = "review", instructions = "Review carefully", enabled = false,
            userInvocable = false, disableModelInvocation = true,
            availableExcludedAssistantIds = setOf(excluded.id))
        val context = AdditionalContext(prompt = "Always include", enabled = false, allAssistants = true,
            excludedAssistantIds = setOf(excluded.id))
        val book = Lorebook(allAssistants = true, excludedAssistantIds = setOf(excluded.id))
        val original = Settings(assistants = listOf(included, excluded), skills = listOf(skill),
            additionalContexts = listOf(context), lorebooks = listOf(book))
        val saved = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(original))
        for (character in listOf(included, future)) {
            assertEquals(listOf(skill), buildSkillToolState(saved.skills, character.id, emptySet()).availableSkills)
            assertEquals(listOf(context), resolveAdditionalContexts(saved.additionalContexts, character.id))
            assertEquals(listOf(book), resolveLorebooks(saved.lorebooks, character))
        }
        assertTrue(buildSkillToolState(saved.skills, excluded.id, emptySet()).availableSkills.isEmpty())
        assertTrue(resolveAdditionalContexts(saved.additionalContexts, excluded.id).isEmpty())
        assertTrue(resolveLorebooks(saved.lorebooks, excluded).isEmpty())
        assertEquals(skill, resolveSkillCommand("/review", saved.skills, excluded.id))
        assertEquals(listOf(context), resolveAdditionalContexts(saved.additionalContexts, excluded.id, setOf(context.id)))
        assertEquals(listOf(book), resolveLorebooks(saved.lorebooks, excluded, setOf(book.id)))
        assertTrue(resolveAdditionalContexts(saved.additionalContexts, included.id, emptySet()).isEmpty())
        assertTrue(resolveLorebooks(saved.lorebooks, included, emptySet()).isEmpty())
    }

    @Test fun lorebookDefaultChangesResetBothKindsOfExceptionsWithoutChangingOtherBooks() {
        val book = Lorebook(name = "Setting")
        val other = Lorebook(name = "Other")
        val first = Assistant(enabledLorebookIds = setOf(book.id, other.id))
        val second = Assistant()
        var settings = Settings(assistants = listOf(first, second), lorebooks = listOf(book, other))
        settings = settings.withLorebookDefault(book.id, true)
        assertEquals(setOf(other.id), settings.assistants.first().enabledLorebookIds)
        assertTrue(settings.assistants.all { settings.lorebooks.first().isAssignedTo(it) })
        settings = settings.withLorebookAssignment(book.id, first.id, false)
        assertFalse(settings.lorebooks.first().isAssignedTo(settings.assistants.first()))
        assertTrue(settings.lorebooks.first().isAssignedTo(settings.assistants.last()))
        settings = settings.withLorebookDefault(book.id, false)
        assertTrue(settings.lorebooks.first().excludedAssistantIds.isEmpty())
        assertTrue(settings.assistants.none { settings.lorebooks.first().isAssignedTo(it) })
        settings = settings.withLorebookAssignment(book.id, second.id, true)
        assertTrue(settings.lorebooks.first().isAssignedTo(settings.assistants.last()))
        settings = settings.withLorebookDefault(book.id, true)
        assertTrue(settings.assistants.all { settings.lorebooks.first().isAssignedTo(it) })
        assertEquals(setOf(other.id), settings.assistants.first().enabledLorebookIds)
    }

    @Test fun defaultOffKeepsExistingAssignmentsAndUsesExplicitInclusionsForNewCharacters() {
        val first = Assistant()
        val second = Assistant()
        val skill = Skill(name = "write", instructions = "Write", availableForAllAssistants = false,
            availableAssistantIds = setOf(first.id))
        val context = AdditionalContext(prompt = "Style", assistantIds = setOf(first.id))
        assertTrue(skill.canActivateAutonomously(first.id))
        assertFalse(skill.canActivateAutonomously(second.id))
        assertTrue(context.appliesTo(first.id))
        assertFalse(context.appliesTo(second.id))
        assertEquals(skill, resolveSkillCommand("/write", listOf(skill), second.id))
        assertNull(resolveSkillCommand("/write", listOf(skill.copy(instructions = " ")), second.id))
    }
}
