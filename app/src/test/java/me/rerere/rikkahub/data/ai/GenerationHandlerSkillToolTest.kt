package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.SKILL_SELECTION_OVERRIDE_ID
import me.rerere.rikkahub.data.model.Skill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class GenerationHandlerSkillToolTest {
    private val assistantId = Uuid.parse("00000000-0000-0000-0000-000000000101")
    private val otherAssistantId = Uuid.parse("00000000-0000-0000-0000-000000000102")

    private fun skill(
        id: String,
        name: String,
        description: String,
        autonomousForAssistant: Boolean = true,
        autonomousForAllAssistants: Boolean = false,
    ): Skill {
        return Skill(
            id = Uuid.parse(id),
            name = name,
            description = description,
            instructions = "Use $name",
            autonomousForAllAssistants = autonomousForAllAssistants,
            autonomousAssistantIds = if (autonomousForAllAssistants) {
                emptySet()
            } else if (autonomousForAssistant) {
                setOf(assistantId)
            } else {
                setOf(otherAssistantId)
            },
        )
    }

    @Test
    fun resolveActiveSkillIds_manualOverrideCanDisableAlwaysEnabledSkills() {
        val alwaysSkill = skill(
            id = "00000000-0000-0000-0000-000000000251",
            name = "always",
            description = "Default on.",
        )
        val optionalSkill = skill(
            id = "00000000-0000-0000-0000-000000000252",
            name = "optional",
            description = "Optional.",
        )
        val allSkillIds = setOf(alwaysSkill.id, optionalSkill.id)

        assertEquals(
            setOf(alwaysSkill.id),
            resolveActiveSkillIds(
                assistantDefaultSkillIds = emptySet(),
                conversationSkillIds = emptySet(),
                turnScopedSkillIds = emptySet(),
                allSkillIds = allSkillIds,
                alwaysEnabledSkillIds = setOf(alwaysSkill.id),
            )
        )

        assertEquals(
            emptySet<Uuid>(),
            resolveActiveSkillIds(
                assistantDefaultSkillIds = emptySet(),
                conversationSkillIds = setOf(SKILL_SELECTION_OVERRIDE_ID),
                turnScopedSkillIds = emptySet(),
                allSkillIds = allSkillIds,
                alwaysEnabledSkillIds = setOf(alwaysSkill.id),
            )
        )
    }

    @Test
    fun buildSkillToolState_filtersUnavailableSkillsForAssistant() {
        val availableSkill = skill(
            id = "00000000-0000-0000-0000-000000000261",
            name = "available",
            description = "Available.",
            autonomousForAllAssistants = true,
        )
        val unavailableSkill = skill(
            id = "00000000-0000-0000-0000-000000000262",
            name = "unavailable",
            description = "Unavailable.",
            autonomousForAllAssistants = true,
        ).copy(
            availableForAllAssistants = false,
            availableAssistantIds = setOf(otherAssistantId),
        )

        val state = buildSkillToolState(
            skills = listOf(availableSkill, unavailableSkill),
            assistantId = assistantId,
            assistantDefaultSkillIds = setOf(unavailableSkill.id),
            conversationSkillIds = emptySet(),
            turnScopedSkillIds = emptySet(),
        )

        assertEquals(listOf(availableSkill.id), state.availableSkills.map { it.id })
        assertFalse(state.activeSkillIds.contains(unavailableSkill.id))
        assertFalse(state.blockedSkills.any { it.id == unavailableSkill.id })
    }

    @Test
    fun buildSkillToolState_onlyExposesEligibleInactiveSkills() {
        val activeSkill = skill(
            id = "00000000-0000-0000-0000-000000000201",
            name = "brainstorm",
            description = "Generate ideas.",
            autonomousForAllAssistants = true,
        )
        val availableSkill = skill(
            id = "00000000-0000-0000-0000-000000000202",
            name = "code-review",
            description = "Review code.",
        )
        val unavailableSkill = skill(
            id = "00000000-0000-0000-0000-000000000203",
            name = "admin",
            description = "Admin only.",
        ).copy(
            availableForAllAssistants = false,
            availableAssistantIds = setOf(otherAssistantId),
        )

        val state = buildSkillToolState(
            skills = listOf(activeSkill, availableSkill, unavailableSkill),
            assistantId = assistantId,
            assistantDefaultSkillIds = setOf(activeSkill.id),
            conversationSkillIds = emptySet(),
            turnScopedSkillIds = emptySet(),
        )

        assertEquals(listOf(activeSkill.id), state.activeSkills.map { it.id })
        assertEquals(listOf(availableSkill.id), state.availableSkills.map { it.id })
        assertTrue(state.blockedSkills.isEmpty())
        assertEquals(setOf(activeSkill.id), state.activeSkillIds)
    }

    @Test
    fun activateSkillsForTurn_reportsActiveBlockedAndUnmatchedTargets() {
        val activeSkill = skill(
            id = "00000000-0000-0000-0000-000000000211",
            name = "brainstorm",
            description = "Generate ideas.",
            autonomousForAllAssistants = true,
        )
        val availableSkill = skill(
            id = "00000000-0000-0000-0000-000000000212",
            name = "code-review",
            description = "Review code.",
        )
        val blockedSkill = skill(
            id = "00000000-0000-0000-0000-000000000213",
            name = "admin",
            description = "Admin only.",
            autonomousForAssistant = false,
        )

        val outcome = activateSkillsForTurn(
            targets = listOf(availableSkill.name.uppercase(), activeSkill.id.toString(), blockedSkill.name, "code"),
            availableSkills = listOf(availableSkill),
            activeSkills = listOf(activeSkill),
            blockedSkills = listOf(blockedSkill),
            currentTurnScopedSkillIds = emptySet(),
        )

        assertEquals(listOf(availableSkill.id), outcome.activatedSkills.map { it.id })
        assertEquals(listOf(activeSkill.id), outcome.alreadyActiveSkills.map { it.id })
        assertEquals(listOf(blockedSkill.id), outcome.blockedSkills.map { it.id })
        assertEquals(listOf("code"), outcome.unmatchedTargets)
        assertEquals(setOf(availableSkill.id), outcome.updatedTurnScopedSkillIds)
        assertEquals(setOf(activeSkill.id, availableSkill.id), outcome.activeSkillIds)
    }

    @Test
    fun buildUsedModes_marksAssistantConversationAndTurnReasons() {
        val assistantSkill = skill(
            id = "00000000-0000-0000-0000-000000000221",
            name = "assistant-default",
            description = "Default skill.",
            autonomousForAllAssistants = true,
        )
        val conversationSkill = skill(
            id = "00000000-0000-0000-0000-000000000222",
            name = "conversation-manual",
            description = "Manual skill.",
            autonomousForAllAssistants = true,
        )
        val turnSkill = skill(
            id = "00000000-0000-0000-0000-000000000223",
            name = "turn-only",
            description = "Turn skill.",
            autonomousForAllAssistants = true,
        )

        val assistantModes = buildUsedModes(
            availableSkills = listOf(assistantSkill, conversationSkill, turnSkill),
            assistantDefaultSkillIds = setOf(assistantSkill.id),
            conversationSkillIds = emptySet(),
            turnScopedSkillIds = setOf(turnSkill.id),
        ).associateBy { it.modeId }

        assertEquals("Enabled for assistant", assistantModes.getValue(assistantSkill.id.toString()).activationReason)
        assertEquals("Activated for this turn", assistantModes.getValue(turnSkill.id.toString()).activationReason)

        val conversationModes = buildUsedModes(
            availableSkills = listOf(assistantSkill, conversationSkill, turnSkill),
            assistantDefaultSkillIds = setOf(assistantSkill.id),
            conversationSkillIds = setOf(conversationSkill.id),
            turnScopedSkillIds = setOf(turnSkill.id),
        ).associateBy { it.modeId }

        assertFalse(conversationModes.containsKey(assistantSkill.id.toString()))
        assertEquals("Enabled for chat", conversationModes.getValue(conversationSkill.id.toString()).activationReason)
        assertEquals("Activated for this turn", conversationModes.getValue(turnSkill.id.toString()).activationReason)
    }

    @Test
    fun createSkillManagementTool_omitsToolWhenNothingElseCanBeActivated() {
        val activeSkill = skill(
            id = "00000000-0000-0000-0000-000000000231",
            name = "brainstorm",
            description = "Generate ideas.",
            autonomousForAllAssistants = true,
        )
        val state = buildSkillToolState(
            skills = listOf(activeSkill),
            assistantId = assistantId,
            assistantDefaultSkillIds = setOf(activeSkill.id),
            conversationSkillIds = emptySet(),
            turnScopedSkillIds = emptySet(),
        )

        val tool = createSkillManagementTool(
            state = state,
            currentTurnScopedSkillIds = emptySet(),
            onUpdateTurnScopedSkillIds = {},
        )

        assertNull(tool)
    }

    @Test
    fun createSkillManagementTool_promptAndExecutionStayTurnScoped() = runBlocking {
        val activeSkill = skill(
            id = "00000000-0000-0000-0000-000000000241",
            name = "brainstorm",
            description = "Generate ideas.",
            autonomousForAllAssistants = true,
        )
        val availableSkill = skill(
            id = "00000000-0000-0000-0000-000000000242",
            name = "code-review",
            description = "Review code carefully.",
            autonomousForAllAssistants = true,
        )
        var updatedTurnSkillIds = emptySet<Uuid>()
        val state = buildSkillToolState(
            skills = listOf(activeSkill, availableSkill),
            assistantId = assistantId,
            assistantDefaultSkillIds = setOf(activeSkill.id),
            conversationSkillIds = emptySet(),
            turnScopedSkillIds = updatedTurnSkillIds,
        )

        val tool = createSkillManagementTool(
            state = state,
            currentTurnScopedSkillIds = updatedTurnSkillIds,
            onUpdateTurnScopedSkillIds = { updatedTurnSkillIds = it },
        )

        assertNotNull(tool)
        val prompt = tool!!.systemPrompt(Model(), emptyList())
        assertTrue(prompt.contains("Currently active skills: brainstorm"))
        assertTrue(prompt.contains("- code-review: Review code carefully."))
        assertFalse(prompt.contains("Generate ideas."))

        val result = tool.execute(
            kotlinx.serialization.json.buildJsonObject {
                put("skill", kotlinx.serialization.json.JsonPrimitive(availableSkill.name))
            }
        ).toString()

        assertTrue(result.contains(availableSkill.id.toString()))
        assertEquals(setOf(availableSkill.id), updatedTurnSkillIds)

        val nextState = buildSkillToolState(
            skills = listOf(activeSkill, availableSkill),
            assistantId = assistantId,
            assistantDefaultSkillIds = setOf(activeSkill.id),
            conversationSkillIds = emptySet(),
            turnScopedSkillIds = updatedTurnSkillIds,
        )
        assertTrue(nextState.activeSkillIds.contains(availableSkill.id))
        assertTrue(nextState.availableSkills.isEmpty())
    }
}
