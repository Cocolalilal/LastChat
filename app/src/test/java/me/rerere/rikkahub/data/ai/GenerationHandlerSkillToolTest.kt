package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.resolveSkillCommand
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class GenerationHandlerSkillToolTest {
    private val assistantId = Uuid.random()
    private val otherAssistantId = Uuid.random()
    private fun skill(name: String) = Skill(name = name, description = "Use $name when needed", instructions = "SECRET BODY $name")

    @Test
    fun `access and legacy always-on flags do not activate skills`() {
        val legacy = skill("legacy").copy(alwaysEnabled = true)
        val state = buildSkillToolState(listOf(legacy), assistantId, emptySet())
        assertTrue(state.activeSkillIds.isEmpty())
        assertEquals(listOf(legacy), state.availableSkills)
    }

    @Test
    fun `disabled empty and unauthorized skills are not exposed even when requested`() {
        val allowed = skill("allowed")
        val blocked = skill("blocked").copy(availableForAllAssistants = false, availableAssistantIds = setOf(otherAssistantId))
        val disabled = skill("disabled").copy(enabled = false)
        val empty = skill("empty").copy(instructions = "")
        val state = buildSkillToolState(listOf(allowed, blocked, disabled, empty), assistantId, setOf(blocked.id, disabled.id))
        assertEquals(listOf(allowed), state.availableSkills)
        assertTrue(state.activeSkillIds.isEmpty())
    }

    @Test
    fun `manual-only skills remain invocable through exact slash command`() {
        val manual = skill("review").copy(disableModelInvocation = true)
        val state = buildSkillToolState(listOf(manual), assistantId, emptySet())
        assertTrue(state.availableSkills.isEmpty())
        assertEquals(manual, resolveSkillCommand("/review this code", listOf(manual), assistantId))
        assertNull(resolveSkillCommand("please /review this code", listOf(manual), assistantId))
        assertNull(resolveSkillCommand("/reviewer", listOf(manual), assistantId))
        assertNull(resolveSkillCommand("/review", listOf(manual.copy(userInvocable = false)), assistantId))
        assertNull(resolveSkillCommand("/review", listOf(manual.copy(availableForAllAssistants = false)), assistantId))
        assertNull(resolveSkillCommand("/review", listOf(manual.copy(enabled = false)), assistantId))
        assertEquals(manual, resolveSkillCommand(" /REVIEW\ncode", listOf(manual), assistantId))
    }

    @Test
    fun `automatic selection can be disabled without disabling manual invocation`() {
        val review = skill("review")
        val state = buildSkillToolState(listOf(review), assistantId, emptySet())
        assertNull(createSkillManagementTool(state, emptySet(), automaticInvocationEnabled = false) {})
        assertEquals(review, resolveSkillCommand("/review", listOf(review), assistantId))
    }

    @Test
    fun `tool reveals metadata only and activation lasts one generation`() = runBlocking {
        val review = skill("review")
        val writing = skill("writing")
        var activated = emptySet<Uuid>()
        val tool = requireNotNull(createSkillManagementTool(
            state = buildSkillToolState(listOf(review, writing), assistantId, activated),
            currentTurnScopedSkillIds = activated,
            onUpdateTurnScopedSkillIds = { activated = it },
        ))
        assertTrue(tool.description.contains(review.description))
        assertFalse(tool.description.contains(review.instructions))
        assertTrue(tool.systemPrompt(Model(), emptyList()).isEmpty())
        val schema = tool.parameters() as InputSchema.Obj
        assertEquals(setOf("skills"), schema.properties.keys)
        tool.execute(buildJsonObject { put("skill", JsonPrimitive("review")) })
        assertEquals(setOf(review.id), activated)
        val nextStep = buildSkillToolState(listOf(review, writing), assistantId, activated)
        assertEquals(listOf(review), nextStep.activeSkills)
        assertEquals(listOf(writing), nextStep.availableSkills)
        val nextTurn = buildSkillToolState(listOf(review, writing), assistantId, emptySet())
        assertTrue(nextTurn.activeSkills.isEmpty())
    }

    @Test
    fun `already active skills omit the selection tool and have turn annotations`() {
        val review = skill("review")
        val state = buildSkillToolState(listOf(review), assistantId, setOf(review.id))
        assertNull(createSkillManagementTool(state, setOf(review.id)) {})
        val used = buildUsedModes(listOf(review), setOf(review.id)).single()
        assertEquals(review.id.toString(), used.modeId)
        assertEquals("Activated for this turn", used.activationReason)
        assertTrue(buildUsedModes(listOf(review), emptySet()).isEmpty())
    }

    @Test
    fun `activation matches exact names or ids and reports blocked or unknown targets`() {
        val available = skill("review")
        val active = skill("write")
        val blocked = skill("admin")
        val outcome = activateSkillsForTurn(
            targets = listOf("review", active.id.toString(), "admin", "rev"),
            availableSkills = listOf(available), activeSkills = listOf(active), blockedSkills = listOf(blocked),
            currentTurnScopedSkillIds = setOf(active.id),
        )
        assertEquals(listOf(available), outcome.activatedSkills)
        assertEquals(listOf(active), outcome.alreadyActiveSkills)
        assertEquals(listOf(blocked), outcome.blockedSkills)
        assertEquals(listOf("rev"), outcome.unmatchedTargets)
        assertEquals(setOf(available.id, active.id), outcome.updatedTurnScopedSkillIds)
    }
}
