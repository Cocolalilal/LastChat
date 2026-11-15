package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class AdditionalContextTest {
    @Test
    fun `snippets require assignment and obey enabled flag`() {
        val character = Uuid.random()
        val other = Uuid.random()
        val snippet = AdditionalContext(name = "Style", prompt = "Be concise", assistantIds = setOf(character))
        assertTrue(snippet.appliesTo(character))
        assertFalse(snippet.appliesTo(other))
        assertTrue(snippet.copy(allAssistants = true).appliesTo(other))
        assertFalse(snippet.copy(enabled = false).appliesTo(character))
        assertFalse(snippet.copy(prompt = " ").appliesTo(character))
    }

    @Test
    fun `legacy always enabled skills migrate once with scope and placement intact`() {
        val character = Uuid.random()
        val skill = Skill(name = "style", instructions = "Use this style", alwaysEnabled = true,
            availableForAllAssistants = false, availableAssistantIds = setOf(character),
            injectionPosition = InjectionPosition.BEFORE_LATEST)
        val settings = Settings(skills = listOf(skill))
        val migrated = settings.migrateAlwaysEnabledSkillsToContext()
        val snippet = migrated.additionalContexts.single()
        assertEquals(skill.id, snippet.id)
        assertEquals(skill.instructions, snippet.prompt)
        assertEquals(skill.injectionPosition, snippet.injectionPosition)
        assertEquals(setOf(character), snippet.assistantIds)
        assertFalse(snippet.allAssistants)
        assertTrue(migrated.skills.isEmpty())
        assertEquals(migrated, migrated.migrateAlwaysEnabledSkillsToContext())
        val restored = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(migrated))
        assertEquals(migrated.additionalContexts, restored.additionalContexts)
        assertEquals(migrated.skills, restored.skills)
    }

    @Test
    fun `legacy default mode becomes additional context instead of dormant skill`() {
        val mode = me.rerere.rikkahub.data.model.Mode(name = "Style", prompt = "Use this style", defaultEnabled = true)
        val migrated = Settings(modes = listOf(mode)).migrateLegacyModesToSkills().migrateAlwaysEnabledSkillsToContext()
        assertTrue(migrated.modes.isEmpty())
        assertTrue(migrated.skills.isEmpty())
        assertEquals(mode.prompt, migrated.additionalContexts.single().prompt)
    }

    @Test
    fun `merging a skill edit keeps a concurrent context assignment`() {
        val base = Settings()
        val snippet = AdditionalContext(name = "Context", prompt = "Always include")
        val latest = base.copy(additionalContexts = listOf(snippet))
        val edited = base.copy(skills = listOf(Skill(name = "new-skill")))
        assertEquals(listOf(snippet), mergeSettingsEdit(base, edited, latest).additionalContexts)
    }
}
