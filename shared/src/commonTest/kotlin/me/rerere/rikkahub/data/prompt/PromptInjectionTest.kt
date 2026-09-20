package me.rerere.rikkahub.data.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptInjectionTest {
    @Test
    fun alwaysActiveLorebookEntriesActivateRegardlessOfHistory() {
        val entry = PortableLorebookEntry(
            id = "always",
            prompt = "The castle is made of glass.",
            activationType = LorebookActivationKind.ALWAYS,
        )
        assertEquals("Always Active", PromptInjectionEngine.lorebookActivationReason(entry, emptyList()))
    }

    @Test
    fun keywordActivationIsCaseInsensitiveByDefault() {
        val entry = PortableLorebookEntry(
            id = "keyword",
            keywords = listOf("Castle"),
            activationType = LorebookActivationKind.KEYWORDS,
        )
        assertEquals(
            "Keyword: Castle",
            PromptInjectionEngine.lorebookActivationReason(entry, listOf("we visited the castle")),
        )
        assertNull(PromptInjectionEngine.lorebookActivationReason(entry, listOf("the forest is quiet")))
    }

    @Test
    fun regexActivationHonorsCaseSensitivity() {
        val entry = PortableLorebookEntry(
            id = "regex",
            keywords = listOf("Cat\\b"),
            useRegex = true,
            caseSensitive = true,
            activationType = LorebookActivationKind.KEYWORDS,
        )
        assertEquals(
            "Keyword: Cat\\b",
            PromptInjectionEngine.lorebookActivationReason(entry, listOf("The Cat sat")),
        )
        assertNull(PromptInjectionEngine.lorebookActivationReason(entry, listOf("The cat sat")))
    }

    @Test
    fun ragActivationUsesSharedCosineThreshold() {
        val entry = PortableLorebookEntry(
            id = "rag",
            activationType = LorebookActivationKind.RAG,
            embedding = listOf(1f, 0f),
        )
        assertNull(PromptInjectionEngine.lorebookActivationReason(entry, emptyList(), queryEmbedding = listOf(0f, 1f)))
        val reason = PromptInjectionEngine.lorebookActivationReason(
            entry,
            emptyList(),
            queryEmbedding = listOf(1f, 0f),
        )
        assertTrue(reason.orEmpty().startsWith("RAG Match"))
    }

    @Test
    fun skillSelectionIntersectsAlwaysEnabledSkillsAndManualConversationOverride() {
        val all = setOf("a", "b", "always")
        val withoutOverride = PromptInjectionEngine.resolveActiveSkillIds(
            assistantDefaultSkillIds = setOf("a"),
            conversationSkillIds = emptySet(),
            turnScopedSkillIds = emptySet(),
            allSkillIds = all,
            alwaysEnabledSkillIds = setOf("always"),
        )
        assertEquals(setOf("a", "always"), withoutOverride)

        val withOverride = PromptInjectionEngine.resolveActiveSkillIds(
            assistantDefaultSkillIds = setOf("a"),
            conversationSkillIds = setOf(PromptInjectionEngine.SKILL_SELECTION_OVERRIDE_ID, "b"),
            turnScopedSkillIds = emptySet(),
            allSkillIds = all,
            alwaysEnabledSkillIds = setOf("always"),
        )
        assertEquals(setOf("b"), withOverride)
    }

    @Test
    fun systemPromptPutsBeforeAndAfterSystemFragmentsAroundTheBasePrompt() {
        val skills = listOf(
            PortableSkill(
                id = "before",
                name = "Before",
                instructions = "before-body",
                injectionPosition = PromptInjectionPosition.BEFORE_SYSTEM,
            ),
            PortableSkill(
                id = "after",
                name = "After",
                instructions = "after-body",
                injectionPosition = PromptInjectionPosition.AFTER_SYSTEM,
            ),
        )
        val lore = listOf(
            PortableLorebookEntry(
                id = "lore-before",
                prompt = "lore-before",
                injectionPosition = PromptInjectionPosition.BEFORE_SYSTEM,
                activationType = LorebookActivationKind.ALWAYS,
            ),
        )
        val assembled = PromptInjectionEngine.assembleSystemPrompt(
            baseSystemPrompt = "You are LastChat.",
            skills = skills,
            lorebookEntries = lore,
            toolGuide = "Use search_web when needed.",
        )
        assertTrue(assembled.startsWith("[Skill: Before]\nbefore-body"))
        assertTrue(assembled.contains("lore-before"))
        assertTrue(assembled.contains("You are LastChat."))
        assertTrue(assembled.contains("[Skill: After]\nafter-body"))
        assertTrue(assembled.endsWith("Use search_web when needed."))
    }

    @Test
    fun manageSkillsActivatesAvailableSkillsForTheCurrentTurn() {
        val available = PortableSkill(id = "research", name = "Research", instructions = "search first")
        val outcome = PromptInjectionEngine.activateSkillsForTurn(
            targets = listOf("Research", "missing"),
            availableSkills = listOf(available),
            activeSkills = emptyList(),
            currentTurnScopedSkillIds = emptySet(),
        )
        assertEquals(listOf("research"), outcome.activatedSkills.map { it.id })
        assertEquals(listOf("missing"), outcome.unmatchedTargets)
        assertEquals(setOf("research"), outcome.updatedTurnScopedSkillIds)
    }
}
