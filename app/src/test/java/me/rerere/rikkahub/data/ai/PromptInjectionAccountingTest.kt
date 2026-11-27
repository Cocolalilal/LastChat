package me.rerere.rikkahub.data.ai

import me.rerere.ai.context.ContextMapCategory
import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.TmdbSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.InstructionItem
import me.rerere.rikkahub.data.model.Skill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PromptInjectionAccountingTest {

    @Test
    fun buildPromptInjectionBundle_accountsForAllFeatureAndInstructionInjections() {
        val inst = InstructionItem(
            id = Uuid.random(),
            name = "Concise Style",
            content = "Keep replies crisp and structured.",
            enabled = true,
        )
        val assistant = Assistant(
            id = Uuid.random(),
            name = "Velvet",
            systemPrompt = "You are Velvet.",
            learningMode = true,
            enableTimeAwareness = true,
            enabledInstructionIds = setOf(inst.id),
        )
        val settings = Settings(
            instructions = listOf(inst),
            displaySetting = DisplaySetting(enableInWorldArtifacts = true),
            tmdbSetting = TmdbSetting(enabled = true),
            enableVoiceMemosMemoryBridge = true,
        )
        val model = Model(
            modelId = "test-model",
            contextWindowTokens = 32_000,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            abilities = listOf(ModelAbility.TOOL),
        )
        val messages = listOf(UIMessage.user("What did I record yesterday?"))

        val bundle = buildPromptInjectionBundle(
            assistant = assistant,
            settings = settings,
            model = model,
            rawMessages = messages,
            retainedMessages = messages,
            hasVisualTool = true,
            allowGlobalPromptInjections = true,
        )

        assertEquals(1, bundle.activeInstructions.size)
        assertTrue(bundle.customInstructionsText.contains("Concise Style"))
        assertTrue(bundle.featurePromptSegments.any { it.id == "feature:artifacts" })
        assertTrue(bundle.featurePromptSegments.any { it.id == "feature:tmdb" })
        assertTrue(bundle.featurePromptSegments.any { it.id == "feature:voice_memos" })
        assertTrue(bundle.featurePromptSegments.any { it.id == "feature:time_awareness" })
        assertTrue(bundle.featurePromptSegments.any { it.id == "feature:visual_privacy" })

        val isolated = buildPromptInjectionBundle(
            assistant = assistant,
            settings = settings,
            model = model,
            rawMessages = messages,
            retainedMessages = messages,
            hasVisualTool = false,
            allowGlobalPromptInjections = false,
        )
        assertTrue(isolated.activeInstructions.isEmpty())
        assertTrue(isolated.featurePromptSegments.isEmpty())
        assertFalse(isolated.systemPromptText.contains("Learning Mode", ignoreCase = true))
    }

    @Test
    fun computePreRequestToolAccounting_includesTmdbMemorySearchSkillAndWorkspaceTools() {
        val skill = Skill(
            id = Uuid.random(),
            name = "deep-research",
            description = "Research deeply",
            instructions = "Follow multi-step verification.",
            autonomousForAllAssistants = true,
        )
        val assistant = Assistant(
            id = Uuid.random(),
            name = "Agent",
            searchMode = AssistantSearchMode.Provider(0),
            enableMemory = true,
            enableMemorySummary = true,
            enableMemorySearchTool = true,
            enableAutomaticSkillInvocation = true,
            workspaceId = Uuid.random(),
        )
        val settings = Settings(
            skills = listOf(skill),
            tmdbSetting = TmdbSetting(enabled = true),
        )
        val model = Model(
            modelId = "tool-model",
            contextWindowTokens = 32_000,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            abilities = listOf(ModelAbility.TOOL),
        )

        val accounting = computePreRequestToolAccounting(
            model = model,
            assistant = assistant,
            settings = settings,
            includeMemoryTools = true,
            availableSkills = listOf(skill),
            assistantDefaultSkillIds = emptySet(),
            conversationSkillIds = emptySet(),
            workspaceReady = true,
            includePythonTools = true,
        )

        assertTrue("search_web" in accounting.toolNames)
        assertTrue("search_media" in accounting.toolNames)
        assertTrue("create_memory" in accounting.toolNames)
        assertTrue("get_memories" in accounting.toolNames)
        assertTrue("manage_memory_summary" in accounting.toolNames)
        assertTrue(MEMORY_SEARCH_TOOL_NAME in accounting.toolNames)
        assertTrue("manage_skills" in accounting.toolNames)
        assertTrue("workspace_read_file" in accounting.toolNames)
        assertTrue("python_server_start" in accounting.toolNames)
        assertTrue(accounting.toolDefinitionTokens > 0)
        assertTrue(accounting.workspaceTokens > 0)
        assertTrue(accounting.localAndSearchTokens > 0)
    }

    @Test
    fun buildContextMapEntries_emitsLabeledEntriesMatchingCategories() {
        val model = Model(modelId = "test", contextWindowTokens = 32_000)
        val inst = InstructionItem(
            id = Uuid.random(),
            name = "Tone",
            content = "Be direct.",
            enabled = true,
        )
        val assistant = Assistant(
            id = Uuid.random(),
            name = "Velvet",
            systemPrompt = "System prompt",
            enabledInstructionIds = setOf(inst.id),
        )
        val settings = Settings(
            instructions = listOf(inst),
            displaySetting = DisplaySetting(enableInWorldArtifacts = true),
        )
        val messages = listOf(UIMessage.user("Hello"))
        val bundle = buildPromptInjectionBundle(
            assistant = assistant,
            settings = settings,
            model = model,
            rawMessages = messages,
            retainedMessages = messages,
            hasVisualTool = false,
        )
        val entries = buildContextMapEntries(
            model = model,
            assistant = assistant,
            bundle = bundle,
            memorySummaryTokens = 120,
            memoryTokens = 80,
            memoryCount = 2,
            summaryTokens = 200,
            enabledSkills = emptyList(),
            skillsRoot = null,
            activatedLorebookEntries = emptyList(),
            toolEntries = emptyList(),
            toolCallTokens = 0,
            conversationTokens = 50,
            conversationMessageCount = 1,
            mediaTokens = 0,
            imageCount = 0,
        )

        assertTrue(entries.any { it.category == ContextMapCategory.SYSTEM_PROMPT && it.id == "system_prompt" })
        assertTrue(entries.any { it.category == ContextMapCategory.CUSTOM_INSTRUCTIONS && it.id == "instruction:${inst.id}" })
        assertTrue(entries.any { it.category == ContextMapCategory.MEMORY_SUMMARY && it.id == "memory_summary" })
        assertTrue(entries.any { it.category == ContextMapCategory.MEMORIES && it.id == "memories" })
        assertTrue(entries.any { it.category == ContextMapCategory.CONVERSATION_SUMMARY && it.id == "conversation_summary" })
        assertTrue(entries.any { it.category == ContextMapCategory.FEATURE_PROMPTS && it.id == "feature:artifacts" })
        assertTrue(entries.any { it.category == ContextMapCategory.CONVERSATION && it.id == "conversation" })
        assertEquals(120, entries.first { it.id == "memory_summary" }.tokens)
        assertTrue(ContextTokenEstimator.textTokens("Be direct.", model) > 0)
    }
}
