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
    @Test fun `chat overrides inherit by default and explicitly disable all with an empty set`() {
        val character = Uuid.random()
        val first = AdditionalContext(name = "First", prompt = "first", assistantIds = setOf(character))
        val second = AdditionalContext(name = "Second", prompt = "second")
        val disabled = AdditionalContext(prompt = "disabled", enabled = false)
        val blank = AdditionalContext(prompt = " ", allAssistants = true)
        val contexts = listOf(first, second, disabled, blank)
        fun active(ids: Set<Uuid>?) = me.rerere.rikkahub.data.model.resolveAdditionalContexts(contexts, character, ids)
        assertEquals(listOf(first), active(null))
        assertEquals(emptyList<AdditionalContext>(), active(emptySet()))
        assertEquals(listOf(second), active(setOf(second.id, disabled.id, blank.id, Uuid.random())))
        assertEquals(listOf(first, second), active(linkedSetOf(second.id, first.id)))
        assertEquals(listOf(second, first), me.rerere.rikkahub.data.model.resolveAdditionalContexts(
            listOf(second, first), character, setOf(first.id, second.id)))
    }

    @Test fun `chat selection round trips without changing legacy inheritance or character assignments`() {
        val original = me.rerere.rikkahub.data.model.Conversation.ofId(Uuid.random())
        val legacy = JsonInstant.decodeFromString<me.rerere.rikkahub.data.model.Conversation>(JsonInstant.encodeToString(original))
        assertEquals(null, legacy.enabledAdditionalContextIds)
        listOf(emptySet(), setOf(Uuid.random())).forEach { ids ->
            val selected = original.copy(enabledAdditionalContextIds = ids)
            assertEquals(ids, JsonInstant.decodeFromString<me.rerere.rikkahub.data.model.Conversation>(
                JsonInstant.encodeToString(selected)).enabledAdditionalContextIds)
        }
    }

    @Test fun `changing context during generation preserves live messages and the new selection`() {
        val draft = me.rerere.rikkahub.data.model.Conversation.ofId(Uuid.random())
        val live = draft.updateCurrentMessages(listOf(me.rerere.ai.ui.UIMessage.assistant("Live reply")))
            .copy(updateAt = draft.updateAt.plusSeconds(1))
        val ids = setOf(Uuid.random())
        val merged = me.rerere.rikkahub.service.mergeLiveMessagesIfIncomingIsStale(live,
            draft.copy(enabledAdditionalContextIds = ids))
        assertEquals(ids, merged.enabledAdditionalContextIds)
        assertEquals(live.messageNodes, merged.messageNodes)
    }

    @Test fun `switching characters invalidates context usage when inherited assignments differ`() {
        val first = me.rerere.rikkahub.data.model.Assistant()
        val second = first.copy(id = Uuid.random())
        val snippet = AdditionalContext(prompt = "Only the first character", assistantIds = setOf(first.id))
        val settings = Settings(assistants = listOf(first, second), additionalContexts = listOf(snippet))
        val chat = me.rerere.rikkahub.data.model.Conversation.ofId(Uuid.random(), first.id)
        val model = me.rerere.ai.provider.Model()
        org.junit.Assert.assertNotEquals(
            me.rerere.rikkahub.data.ai.contextUsageSourceKey(chat, first, model, settings),
            me.rerere.rikkahub.data.ai.contextUsageSourceKey(chat.copy(assistantId = second.id), second, model, settings))
    }

}
