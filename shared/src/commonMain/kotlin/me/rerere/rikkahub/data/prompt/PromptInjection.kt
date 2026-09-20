package me.rerere.rikkahub.data.prompt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.memory.MemoryVectorMath

@Serializable
enum class PromptInjectionPosition {
    @SerialName("before_system")
    BEFORE_SYSTEM,

    @SerialName("after_system")
    AFTER_SYSTEM,

    @SerialName("top_of_chat")
    TOP_OF_CHAT,

    @SerialName("before_latest")
    BEFORE_LATEST,

    @SerialName("at_depth")
    AT_DEPTH,
}

@Serializable
enum class LorebookActivationKind {
    @SerialName("always")
    ALWAYS,

    @SerialName("keywords")
    KEYWORDS,

    @SerialName("rag")
    RAG,
}

@Serializable
data class PortablePromptAttachment(
    val type: String = "document",
    val url: String = "",
    val fileName: String = "",
    val mime: String = "",
)

@Serializable
data class PortableSkill(
    val id: String,
    val name: String = "",
    val description: String = "",
    val instructions: String = "",
    val enabled: Boolean = true,
    val alwaysEnabled: Boolean = false,
    val availableForAllAssistants: Boolean = true,
    val availableAssistantIds: Set<String> = emptySet(),
    val injectionPosition: PromptInjectionPosition = PromptInjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0,
    val disableModelInvocation: Boolean = false,
    val icon: String? = null,
    val compatibility: String? = null,
    val workspaceDirectory: String? = null,
    val bundledResources: List<String> = emptyList(),
    val attachments: List<PortablePromptAttachment> = emptyList(),
) {
    fun isAvailableForAssistant(assistantId: String): Boolean {
        return availableForAllAssistants || availableAssistantIds.contains(assistantId)
    }

    fun formattedInstructions(): String = buildString {
        if (name.isNotBlank()) {
            append("[Skill: $name]")
            appendLine()
        }
        append(instructions)
        val directory = workspaceDirectory?.takeIf { it.isNotBlank() }
        if (directory != null) {
            appendLine()
            append("Skill package: $directory")
            if (bundledResources.isNotEmpty()) {
                appendLine()
                appendLine("Bundled resources (load only when needed):")
                bundledResources.take(64).forEach { path ->
                    appendLine("- $path")
                }
            }
        }
    }
}

@Serializable
data class PortableLorebookEntry(
    val id: String,
    val name: String = "",
    val prompt: String = "",
    val enabled: Boolean = true,
    val injectionPosition: PromptInjectionPosition = PromptInjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0,
    val activationType: LorebookActivationKind = LorebookActivationKind.KEYWORDS,
    val keywords: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val scanDepth: Int = 10,
    val embedding: List<Float>? = null,
    val attachments: List<PortablePromptAttachment> = emptyList(),
)

@Serializable
data class PortableLorebook(
    val id: String,
    val name: String = "",
    val description: String = "",
    val entries: List<PortableLorebookEntry> = emptyList(),
    val enabled: Boolean = true,
    val coverJson: String? = null,
)

data class ActivatedLorebookEntry(
    val lorebook: PortableLorebook,
    val entry: PortableLorebookEntry,
    val entryIndex: Int,
    val reason: String,
)

data class PortableSkillToolState(
    val activeSkills: List<PortableSkill>,
    val availableSkills: List<PortableSkill>,
    val blockedSkills: List<PortableSkill>,
    val activeSkillIds: Set<String>,
)

data class PortableSkillActivationOutcome(
    val activatedSkills: List<PortableSkill>,
    val alreadyActiveSkills: List<PortableSkill>,
    val unmatchedTargets: List<String>,
    val updatedTurnScopedSkillIds: Set<String>,
)

object PromptInjectionEngine {
    const val RAG_SIMILARITY_THRESHOLD = 0.7f
    const val DEFAULT_SCAN_DEPTH = 10
    const val SKILL_SELECTION_OVERRIDE_ID = "00000000-0000-0000-0000-000000000001"

    fun lorebookActivationReason(
        entry: PortableLorebookEntry,
        recentMessages: List<String>,
        queryEmbedding: List<Float>? = null,
    ): String? {
        if (!entry.enabled) return null
        return when (entry.activationType) {
            LorebookActivationKind.ALWAYS -> "Always Active"
            LorebookActivationKind.KEYWORDS -> {
                val scanText = recentMessages.takeLast(entry.scanDepth.coerceAtLeast(1)).joinToString(" ")
                val matchingKeyword = entry.keywords.firstOrNull { keyword ->
                    keywordMatches(scanText, keyword, entry.caseSensitive, entry.useRegex)
                }
                matchingKeyword?.let { "Keyword: $it" }
            }
            LorebookActivationKind.RAG -> {
                val embedding = entry.embedding
                if (embedding.isNullOrEmpty() || queryEmbedding == null) return null
                val similarity = MemoryVectorMath.cosineSimilarity(embedding, queryEmbedding)
                if (similarity >= RAG_SIMILARITY_THRESHOLD) {
                    "RAG Match (${similarity.toString().take(4)})"
                } else null
            }
        }
    }

    fun activateLorebookEntries(
        lorebooks: List<PortableLorebook>,
        enabledLorebookIds: Set<String>,
        recentMessages: List<String>,
        queryEmbedding: List<Float>? = null,
    ): List<ActivatedLorebookEntry> {
        return lorebooks
            .filter { it.enabled && enabledLorebookIds.contains(it.id) }
            .flatMap { lorebook ->
                lorebook.entries.mapIndexedNotNull { index, entry ->
                    val reason = lorebookActivationReason(entry, recentMessages, queryEmbedding)
                    if (reason != null) {
                        ActivatedLorebookEntry(lorebook, entry, index, reason)
                    } else null
                }
            }
    }

    fun resolveActiveSkillIds(
        assistantDefaultSkillIds: Set<String>,
        conversationSkillIds: Set<String>,
        turnScopedSkillIds: Set<String>,
        allSkillIds: Set<String>,
        alwaysEnabledSkillIds: Set<String> = emptySet(),
    ): Set<String> {
        val hasManualOverride = conversationSkillIds.contains(SKILL_SELECTION_OVERRIDE_ID)
        val defaultEnabledSkillIds = if (hasManualOverride) emptySet() else alwaysEnabledSkillIds
        val manualIds = if (hasManualOverride) {
            conversationSkillIds - SKILL_SELECTION_OVERRIDE_ID
        } else if (conversationSkillIds.isNotEmpty()) {
            conversationSkillIds
        } else {
            assistantDefaultSkillIds
        }
        return (manualIds + turnScopedSkillIds + defaultEnabledSkillIds).intersect(allSkillIds)
    }

    fun enabledSkills(
        skills: List<PortableSkill>,
        assistantId: String,
        assistantDefaultSkillIds: Set<String>,
        conversationSkillIds: Set<String> = emptySet(),
        turnScopedSkillIds: Set<String> = emptySet(),
    ): List<PortableSkill> {
        val usable = skills.filter { it.enabled && it.instructions.isNotBlank() && it.isAvailableForAssistant(assistantId) }
        val allIds = usable.map(PortableSkill::id).toSet()
        val alwaysEnabled = usable.filter { it.alwaysEnabled }.map(PortableSkill::id).toSet()
        val activeIds = resolveActiveSkillIds(
            assistantDefaultSkillIds = assistantDefaultSkillIds.intersect(allIds),
            conversationSkillIds = conversationSkillIds,
            turnScopedSkillIds = turnScopedSkillIds,
            allSkillIds = allIds,
            alwaysEnabledSkillIds = alwaysEnabled,
        )
        return usable.filter { activeIds.contains(it.id) }
    }

    fun assembleSystemPrompt(
        baseSystemPrompt: String,
        skills: List<PortableSkill>,
        lorebookEntries: List<PortableLorebookEntry>,
        toolGuide: String = "",
    ): String = buildString {
        skills.filter { it.injectionPosition == PromptInjectionPosition.BEFORE_SYSTEM }.forEach { skill ->
            append(skill.formattedInstructions())
            appendLine()
        }
        lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.BEFORE_SYSTEM }.forEach { entry ->
            append(entry.prompt)
            appendLine()
        }
        if (baseSystemPrompt.isNotBlank()) {
            append(baseSystemPrompt)
        }
        skills.filter { it.injectionPosition == PromptInjectionPosition.AFTER_SYSTEM }.forEach { skill ->
            appendLine()
            append(skill.formattedInstructions())
        }
        lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.AFTER_SYSTEM }.forEach { entry ->
            appendLine()
            append(entry.prompt)
        }
        if (toolGuide.isNotBlank()) {
            appendLine()
            append(toolGuide)
        }
    }.trim()

    fun inContextSkillText(skill: PortableSkill): String = buildString {
        append("<system>\n")
        append("[Skill: ${skill.name}]\n")
        append(skill.instructions)
        skill.workspaceDirectory?.takeIf { it.isNotBlank() }?.let { directory ->
            append("\nSkill directory: $directory")
        }
        append("\n</system>")
    }

    fun inContextLorebookText(entry: PortableLorebookEntry): String =
        "<system>\n${entry.prompt}\n</system>"

    fun skillToolState(
        skills: List<PortableSkill>,
        assistantId: String,
        assistantDefaultSkillIds: Set<String>,
        conversationSkillIds: Set<String>,
        turnScopedSkillIds: Set<String>,
    ): PortableSkillToolState {
        val usable = skills.filter { it.enabled && it.instructions.isNotBlank() && it.isAvailableForAssistant(assistantId) }
        val allIds = usable.map(PortableSkill::id).toSet()
        val alwaysEnabled = usable.filter { it.alwaysEnabled }.map(PortableSkill::id).toSet()
        val activeIds = resolveActiveSkillIds(
            assistantDefaultSkillIds = assistantDefaultSkillIds.intersect(allIds),
            conversationSkillIds = conversationSkillIds,
            turnScopedSkillIds = turnScopedSkillIds,
            allSkillIds = allIds,
            alwaysEnabledSkillIds = alwaysEnabled,
        )
        val toggleable = usable.filterNot { it.disableModelInvocation }
        return PortableSkillToolState(
            activeSkills = toggleable.filter { activeIds.contains(it.id) },
            availableSkills = toggleable.filterNot { activeIds.contains(it.id) },
            blockedSkills = emptyList(),
            activeSkillIds = activeIds,
        )
    }

    fun parseSkillTargets(skills: List<String>, skill: String?): List<String> {
        val fromSingle = skill?.trim()?.takeIf { it.isNotBlank() }
        return (skills + listOfNotNull(fromSingle))
            .flatMap { raw -> raw.split(',').map { it.trim() }.filter { it.isNotBlank() } }
            .distinct()
    }

    fun activateSkillsForTurn(
        targets: List<String>,
        availableSkills: List<PortableSkill>,
        activeSkills: List<PortableSkill>,
        currentTurnScopedSkillIds: Set<String>,
    ): PortableSkillActivationOutcome {
        fun lookup(list: List<PortableSkill>): Map<String, PortableSkill> = buildMap {
            list.forEach { skill ->
                put(skill.id.lowercase(), skill)
                skill.name.trim().lowercase().takeIf { it.isNotBlank() }?.let { put(it, skill) }
            }
        }
        val availableByKey = lookup(availableSkills)
        val activeByKey = lookup(activeSkills)
        val activated = linkedSetOf<PortableSkill>()
        val alreadyActive = linkedSetOf<PortableSkill>()
        val unmatched = mutableListOf<String>()
        targets.forEach { raw ->
            val key = raw.trim().lowercase()
            when {
                availableByKey.containsKey(key) -> activated += availableByKey.getValue(key)
                activeByKey.containsKey(key) -> alreadyActive += activeByKey.getValue(key)
                else -> unmatched += raw
            }
        }
        return PortableSkillActivationOutcome(
            activatedSkills = activated.toList(),
            alreadyActiveSkills = alreadyActive.toList(),
            unmatchedTargets = unmatched,
            updatedTurnScopedSkillIds = currentTurnScopedSkillIds + activated.map { it.id },
        )
    }

    private fun keywordMatches(
        searchText: String,
        keyword: String,
        caseSensitive: Boolean,
        useRegex: Boolean,
    ): Boolean {
        if (keyword.isBlank()) return false
        return if (useRegex) {
            val regex = runCatching {
                if (caseSensitive) Regex(keyword) else Regex(keyword, RegexOption.IGNORE_CASE)
            }.getOrNull() ?: return false
            regex.containsMatchIn(searchText)
        } else {
            searchText.contains(keyword, ignoreCase = !caseSensitive)
        }
    }
}
