package me.rerere.ai.generation

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.context.adaptiveImageLimit
import me.rerere.ai.context.ContextPlanner
import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.context.ContextUsageBreakdown
import me.rerere.ai.context.effectiveHistoryForContext
import me.rerere.ai.context.limitImagesForModel
import me.rerere.ai.context.smartFitContext
import me.rerere.ai.context.smartInputBudget
import me.rerere.ai.context.smartOutputTokenBudget
import me.rerere.ai.context.smartPrepareHistory
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.contextCapacityTokens
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.UsedLorebookEntry
import me.rerere.ai.ui.UsedMemory
import me.rerere.ai.ui.UsedMode
import me.rerere.ai.ui.toTurnGroups
import me.rerere.ai.util.buildTimeAwarenessPromptBlock
import me.rerere.rikkahub.data.prompt.ActivatedLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionEngine
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition
import kotlin.time.ExperimentalTime

enum class PortableContextPriority {
    CHAT_HISTORY,
    BALANCED,
    MEMORIES,
}

data class PortablePrepareAssistant(
    val id: String,
    val systemPrompt: String = "",
    val learningMode: Boolean = false,
    val enableMemory: Boolean = false,
    val useRagMemoryRetrieval: Boolean = true,
    val smartContextManagement: Boolean = true,
    val maxTokens: Int? = null,
    val maxTokenUsage: Int = 81_920,
    val maxHistoryMessages: Int? = null,
    val maxSearchResultsRetained: Int? = null,
    val enableTimeAwareness: Boolean = false,
    val temperature: Float? = null,
    val topP: Float? = null,
    val thinkingBudget: Int? = null,
    val enabledSkillIds: Set<String> = emptySet(),
    val enabledLorebookIds: Set<String> = emptySet(),
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val contextPriority: PortableContextPriority = PortableContextPriority.BALANCED,
    val workspaceEnabled: Boolean = false,
    val archiveImagesAfterMessageAge: Int? = null,
    val enableRecentChatsReference: Boolean = false,
    val messageTemplate: String = "{{ message }}",
)

data class PortableMemoryRecord(
    val id: Int,
    val content: String,
    val type: Int = 0,
    val timestamp: Long = 0L,
)

data class PortableRecentChat(
    val id: String,
    val title: String,
    val updatedAtEpochMs: Long,
    val isToday: Boolean = false,
)

data class PortablePrepareRequest(
    val messages: List<UIMessage>,
    val model: Model,
    val tools: List<Tool>,
    val assistant: PortablePrepareAssistant,
    val skills: List<PortableSkill> = emptyList(),
    val lorebooks: List<PortableLorebook> = emptyList(),
    val memories: List<PortableMemoryRecord> = emptyList(),
    val conversationSkillIds: Set<String> = emptySet(),
    val turnScopedSkillIds: Set<String> = emptySet(),
    val conversationLorebookIds: Set<String>? = null,
    val contextSummary: String? = null,
    val contextSummaryUpToIndex: Int = -1,
    val truncateIndex: Int = -1,
    val learningModePrompt: String = "",
    val queryEmbedding: List<Float>? = null,
    val memoryPromptOverride: String? = null,
    val transformers: List<PortableInputTransformer> = emptyList(),
    val transformerContext: PortableTransformerContext? = null,
    val contextBudgetScale: Double = 1.0,
    val contextUsageSourceKey: Int? = null,
    val episodeGroup: (Long) -> String = { "Older" },
    val timeZoneId: String = TimeZone.currentSystemDefault().id,
    val timeZoneShortName: String = TimeZone.currentSystemDefault().id,
    val recentChats: List<PortableRecentChat> = emptyList(),
    val activeConversationId: String? = null,
)

data class PortablePrepareResult(
    val providerMessages: List<UIMessage>,
    val usedLorebookEntries: List<UsedLorebookEntry> = emptyList(),
    val usedModes: List<UsedMode> = emptyList(),
    val usedMemories: List<UsedMemory> = emptyList(),
    val effectiveTools: List<Tool> = emptyList(),
    val messageBudgetTokens: Int? = null,
    val effectiveInputBudgetTokens: Int? = null,
    val contextUsage: ContextUsageBreakdown? = null,
    val annotations: List<me.rerere.ai.ui.UIMessageAnnotation> = emptyList(),
) {
    fun toTurnRequest(
        conversationMessages: List<UIMessage>,
        params: TextGenerationParams,
    ): PortableTurnRequest = PortableTurnRequest(
        conversationMessages = conversationMessages,
        providerMessages = providerMessages,
        params = params.copy(tools = effectiveTools.ifEmpty { params.tools }),
    )
}

object PortableGenerationPrepare {
    const val SKILL_REASON_ASSISTANT = "Enabled for assistant"
    const val SKILL_REASON_CONVERSATION = "Enabled for chat"
    const val SKILL_REASON_TURN = "Activated for this turn"
    const val SKILL_REASON_ALWAYS = "Always enabled"

    @OptIn(ExperimentalTime::class)
    suspend fun prepare(request: PortablePrepareRequest): PortablePrepareResult {
        val assistant = request.assistant
        val model = request.model
        fun estimateTokens(text: String) = ContextTokenEstimator.textTokens(text, model)
        fun estimateTokens(message: UIMessage) = ContextTokenEstimator.messageTokens(message, model)

        val smartEnabled = assistant.smartContextManagement &&
            model.contextCapacityTokens?.let { it > 0 } == true
        val maxTokens = if (smartEnabled) {
            smartInputBudget(model, assistant.maxTokens) ?: assistant.maxTokenUsage
        } else {
            assistant.maxTokenUsage
        }.let { budget ->
            if (smartEnabled) {
                (budget * request.contextBudgetScale.coerceIn(0.08, 1.0)).toInt().coerceAtLeast(1)
            } else budget
        }
        val history = preprocessImages(request, smartEnabled, maxTokens)
        val extraMemories = recentChatMemories(request, history)
        val request = request.copy(
            messages = history,
            memories = (request.memories + extraMemories).distinctBy { it.content },
        )
        val smartPlan = if (smartEnabled) {
            ContextPlanner.plan(
                messages = request.messages,
                model = model,
                customBudgetTokens = maxTokens,
                systemPromptTokens = estimateTokens(assistant.systemPrompt),
                toolDefinitionTokens = request.tools.sumOf {
                    ContextTokenEstimator.toolDefinitionTokens(it, model)
                },
            )
        } else null

        val effectiveTools = when {
            ModelAbility.TOOL !in model.abilities -> emptyList()
            smartEnabled && smartPlan?.allowToolDistillation == true ->
                selectSmartTools(request.tools, request.messages, model, maxTokens)
            else -> request.tools
        }

        val recentMessagesForScan = request.messages.takeLast(10).map { it.toText() }
        val lorebookIds = request.conversationLorebookIds ?: assistant.enabledLorebookIds
        val activated = PromptInjectionEngine.activateLorebookEntries(
            lorebooks = request.lorebooks,
            enabledLorebookIds = lorebookIds,
            recentMessages = recentMessagesForScan,
            queryEmbedding = request.queryEmbedding,
        )
        val enabledSkills = PromptInjectionEngine.enabledSkills(
            skills = request.skills,
            assistantId = assistant.id,
            assistantDefaultSkillIds = assistant.enabledSkillIds,
            conversationSkillIds = request.conversationSkillIds,
            turnScopedSkillIds = request.turnScopedSkillIds,
        )
        val usedModes = buildUsedModes(
            skills = enabledSkills,
            conversationSkillIds = request.conversationSkillIds,
            turnScopedSkillIds = request.turnScopedSkillIds,
        )
        val usedLorebookEntries = activated.mapIndexed { priority, item ->
            UsedLorebookEntry(
                lorebookId = item.lorebook.id,
                lorebookName = item.lorebook.name,
                lorebookCover = item.lorebook.coverJson,
                entryId = item.entry.id,
                entryName = item.entry.name,
                entryIndex = item.entryIndex,
                priority = activated.size - priority,
                activationReason = item.reason,
                contextTokenCount = estimateTokens(item.entry.prompt),
            )
        }

        val toolGuide = effectiveTools.joinToString("\n") { tool ->
            tool.systemPrompt(model, request.messages)
        }.trim()
        val toolDefinitionText = effectiveTools.joinToString("\n") { tool ->
            ContextTokenEstimator.toolDefinitionText(tool)
        }
        val toolDefinitionTokens = effectiveTools
            .sumOf { tool -> ContextTokenEstimator.toolDefinitionTokens(tool, model).toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        val baseSystem = buildString {
            if (assistant.systemPrompt.isNotBlank()) append(assistant.systemPrompt)
            if (assistant.learningMode && request.learningModePrompt.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(request.learningModePrompt)
            }
        }
        val assembledSystem = PromptInjectionEngine.assembleSystemPrompt(
            baseSystemPrompt = baseSystem,
            skills = enabledSkills,
            lorebookEntries = activated.map(ActivatedLorebookEntry::entry),
            toolGuide = toolGuide,
        )
        var currentTokens = estimateTokens(assembledSystem) + toolDefinitionTokens

        val historyLimited = effectiveHistoryForContext(
            messages = request.messages,
            smartManagement = smartEnabled,
            summaryUpToIndex = request.contextSummaryUpToIndex.takeIf {
                !request.contextSummary.isNullOrBlank()
            } ?: -1,
            truncateIndex = request.truncateIndex,
            manualHistoryLimit = assistant.maxHistoryMessages,
        )
        val searchPruned = if (smartEnabled) {
            smartPrepareHistory(
                messages = historyLimited,
                model = model,
                availableBudgetTokens = (maxTokens - currentTokens).coerceAtLeast(1),
            )
        } else {
            pruneSearchResults(historyLimited, assistant.maxSearchResultsRetained)
        }
        val imageLimited = if (smartEnabled) {
            searchPruned
        } else {
            searchPruned.limitImagesForModel(model)
        }

        val selectedMemories: List<PortableMemoryRecord>
        val selectedMemoryPromptText: String
        if (assistant.enableMemory) {
            val selection = selectMemoryContext(
                candidates = request.memories,
                model = model,
                inputBudgetTokens = maxTokens,
                requiredContextTokens = currentTokens,
                historyMessages = imageLimited,
                contextPriority = assistant.contextPriority,
                smartEnabled = smartEnabled,
                episodeGroup = request.episodeGroup,
                overridePrompt = request.memoryPromptOverride,
            )
            selectedMemories = selection.first
            selectedMemoryPromptText = selection.second
        } else {
            selectedMemories = emptyList()
            selectedMemoryPromptText = request.memoryPromptOverride.orEmpty()
        }

        val contextAttachments = attachmentParts(enabledSkills, activated.map { it.entry })
        val orderedHistory = if (smartEnabled) imageLimited else {
            imageLimited
        }
        val timeAwareness = buildTimeAwarenessPromptBlock(
            enabled = assistant.enableTimeAwareness,
            fullMessageTimes = request.messages.filter(::isConversational).map { it.createdAt },
            retainedMessageTimes = orderedHistory.filter(::isConversational).map { it.createdAt },
            now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            timeZone = TimeZone.currentSystemDefault(),
            timeZoneId = request.timeZoneId,
            timeZoneShortName = request.timeZoneShortName,
        )
        val rawBuilt = assembleProviderMessages(
            systemPrompt = assembledSystem,
            skills = enabledSkills,
            lorebookEntries = activated.map { it.entry },
            history = orderedHistory,
            contextSummary = request.contextSummary,
            memoryPrompt = selectedMemoryPromptText,
            timeAwareness = timeAwareness,
            attachments = contextAttachments,
        ).limitImagesForModel(model)

        val smartMessageBudget = if (smartEnabled) {
            (maxTokens - toolDefinitionTokens).coerceAtLeast(1)
        } else null
        var built = if (smartMessageBudget != null) {
            smartFitContext(
                messages = rawBuilt,
                model = model,
                messageBudgetTokens = smartMessageBudget,
            )
        } else rawBuilt

        val transformCtx = request.transformerContext ?: PortableTransformerContext(
            model = model,
            workspaceEnabled = assistant.workspaceEnabled,
        )
        val transformed = if (request.transformers.isEmpty()) {
            PortableInputTransformResult(built, emptyList())
        } else {
            built.applyPortableInputTransformers(request.transformers, transformCtx)
        }
        built = transformed.messages
        if (smartMessageBudget != null) {
            built = smartFitContext(
                messages = built,
                model = model,
                messageBudgetTokens = smartMessageBudget,
            )
        }

        val usedMemories = selectedMemories.mapIndexed { index, memory ->
            val reason = when {
                memory.id < 0 -> "Recent episode boost"
                assistant.useRagMemoryRetrieval -> "Contextually relevant"
                else -> "Always included"
            }
            UsedMemory(
                memoryId = memory.id,
                memoryContent = memory.content.take(50) + if (memory.content.length > 50) "..." else "",
                memoryType = memory.type,
                priority = selectedMemories.size - index,
                activationReason = reason,
                contextTokenCount = estimateTokens(memory.content),
            )
        }
        val skillText = buildString {
            enabledSkills.forEach { skill ->
                appendLine(skill.name)
                appendLine(skill.instructions)
            }
        }
        val lorebookText = activated.joinToString("\n") { it.entry.prompt }
        val contextUsage = model.contextCapacityTokens?.let {
            ContextTokenEstimator.breakdown(
                messages = built,
                model = model,
                systemPromptText = buildString {
                    append(assistant.systemPrompt)
                    if (assistant.learningMode && request.learningModePrompt.isNotBlank()) {
                        appendLine()
                        append(request.learningModePrompt)
                    }
                },
                summaryText = request.contextSummary.orEmpty(),
                memoryText = selectedMemoryPromptText,
                skillText = skillText,
                lorebookText = lorebookText,
                toolDefinitionText = toolDefinitionText,
                toolDefinitionTokensOverride = toolDefinitionTokens,
                embeddedToolText = toolGuide,
                namedContextEmbeddedInMessages = true,
                usableInputTokens = if (smartEnabled) maxTokens else null,
                sourceKey = request.contextUsageSourceKey,
            )
        }
        return PortablePrepareResult(
            providerMessages = built,
            usedLorebookEntries = usedLorebookEntries,
            usedModes = usedModes,
            usedMemories = usedMemories,
            effectiveTools = effectiveTools,
            messageBudgetTokens = smartMessageBudget,
            effectiveInputBudgetTokens = maxTokens.takeIf { smartEnabled },
            contextUsage = contextUsage,
            annotations = transformed.annotations,
        )
    }

    fun applyInContextInjections(
        messages: List<UIMessage>,
        skills: List<PortableSkill>,
        lorebookEntries: List<PortableLorebookEntry>,
    ): List<UIMessage> {
        val topSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.TOP_OF_CHAT }
        val topEntries = lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.TOP_OF_CHAT }
        val beforeLatestSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.BEFORE_LATEST }
        val beforeLatestEntries = lorebookEntries.filter {
            it.injectionPosition == PromptInjectionPosition.BEFORE_LATEST
        }
        val depthSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.AT_DEPTH }
        val depthEntries = lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.AT_DEPTH }
        if (
            topSkills.isEmpty() && topEntries.isEmpty() &&
            beforeLatestSkills.isEmpty() && beforeLatestEntries.isEmpty() &&
            depthSkills.isEmpty() && depthEntries.isEmpty()
        ) {
            return messages
        }
        return assembleProviderMessages(
            systemPrompt = "",
            skills = skills,
            lorebookEntries = lorebookEntries,
            history = messages,
            contextSummary = null,
            memoryPrompt = "",
            timeAwareness = null,
            attachments = emptyList(),
        ).filterNot { it.role == MessageRole.SYSTEM && it.toText().isBlank() }
    }

    fun selectSmartTools(
        tools: List<Tool>,
        messages: List<UIMessage>,
        model: Model,
        inputBudgetTokens: Int,
    ): List<Tool> {
        if (tools.isEmpty()) return tools
        val recentMessages = messages.takeLast(12)
        val recentlyUsedNames = recentMessages.flatMap { message ->
            message.getToolCalls().map { it.toolName }
        }.toSet()
        val queryTerms = recentMessages.lastOrNull { it.role == MessageRole.USER }
            ?.toText()
            .orEmpty()
            .lowercase()
            .split(Regex("[^a-z0-9_]+"))
            .filter { it.length >= 3 }
            .toSet()
        data class RankedTool(val index: Int, val tool: Tool, val tokens: Int, val score: Int)
        val ranked = tools.mapIndexed { index, tool ->
            val systemPromptTokens = runCatching { tool.systemPrompt(model, messages) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { ContextTokenEstimator.textTokens(it, model) }
                ?: 0
            val searchable = "${tool.name} ${tool.description}".lowercase()
            val semanticHits = queryTerms.count { term -> searchable.contains(term) }
            val score = when {
                tool.name in recentlyUsedNames -> 10_000
                tool.name == "look_at_screen" -> 8_000
                tool.name == SKILL_MANAGEMENT_TOOL_NAME || tool.name == "ask_user" -> 2_000
                else -> semanticHits * 100 - index
            }
            RankedTool(
                index = index,
                tool = tool,
                tokens = (ContextTokenEstimator.toolDefinitionTokens(tool, model).toLong() + systemPromptTokens)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt(),
                score = score,
            )
        }
        val total = ranked.sumOf { it.tokens }
        val allocation = (inputBudgetTokens * 0.22).toInt()
            .coerceAtLeast(128)
            .coerceAtMost((inputBudgetTokens - 128).coerceAtLeast(0))
        if (total <= allocation) return tools
        var remaining = allocation
        val selected = mutableSetOf<Int>()
        ranked.sortedByDescending { it.score }.forEach { candidate ->
            if (candidate.tokens <= remaining) {
                selected += candidate.index
                remaining = (remaining - candidate.tokens).coerceAtLeast(0)
            }
        }
        return ranked.filter { it.index in selected }.map { it.tool }
    }

    fun renderMemoryContextPrompt(
        model: Model,
        memories: List<PortableMemoryRecord>,
        episodeGroup: (Long) -> String,
    ): String {
        val hasToolAbility = ModelAbility.TOOL in model.abilities
        if (memories.isEmpty() && !hasToolAbility) return ""
        val coreMemories = memories.filter { it.type == 0 }
        val episodicMemories = memories.filter { it.type == 1 }
        return buildString {
            if (memories.isNotEmpty()) {
                append("## Memories\n")
                append("These are memories that you can reference in future conversations.\n")
                if (coreMemories.isNotEmpty()) {
                    append("### Core Memories\n")
                    coreMemories.forEach { memory ->
                        append("- [ID: ${memory.id}] ${memory.content}\n")
                    }
                }
                if (episodicMemories.isNotEmpty()) {
                    append("### Episodic Memories\n")
                    val grouped = episodicMemories.groupBy { memory -> episodeGroup(memory.timestamp) }
                    listOf("Today", "Yesterday", "This Week", "Older").forEach { group ->
                        grouped[group].orEmpty()
                            .sortedByDescending { it.timestamp }
                            .takeIf { it.isNotEmpty() }
                            ?.let { groupMemories ->
                                append("#### $group\n")
                                groupMemories.forEach { memory -> append("- ${memory.content}\n") }
                            }
                    }
                }
            }
            if (hasToolAbility) {
                if (memories.isNotEmpty()) append("\n\n")
                append(
                    """
                    ## Memory Tool
                    You are a stateless large language model; you **cannot store memories** internally. To remember information, you must use **memory tools**.
                    Memory tools allow you (the assistant) to store multiple pieces of information (records) to recall details across conversations.
                    You can use the `create_memory`, `edit_memory`, and `delete_memory` tools to create, update, or delete memories.
                    - If there is no relevant information in memory, call `create_memory` to create a new record.
                    - If a relevant record already exists, call `edit_memory` to update it.
                    - If a memory is outdated or no longer useful, call `delete_memory` to remove it.
                    **Note:** You can only edit or delete **Core Memories** (which have an ID). Episodic Memories are read-only context.

                    **Do not store sensitive information.** Sensitive information includes: ethnicity, religious beliefs, sexual orientation, political views, sexual life, criminal records, etc.
                    During chats, act like a personal secretary and **proactively** record user-related information, including but not limited to:
                    - Name/Nickname
                    - Age/Gender/Hobbies
                    - Plans/To-do items
                    """.trimIndent(),
                )
            }
        }
    }

    private fun assembleProviderMessages(
        systemPrompt: String,
        skills: List<PortableSkill>,
        lorebookEntries: List<PortableLorebookEntry>,
        history: List<UIMessage>,
        contextSummary: String?,
        memoryPrompt: String,
        timeAwareness: String?,
        attachments: List<UIMessagePart>,
    ): List<UIMessage> {
        val topSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.TOP_OF_CHAT }
        val afterChatSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.BEFORE_LATEST }
        val depthSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.AT_DEPTH }
        val topEntries = lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.TOP_OF_CHAT }
        val beforeLatestEntries = lorebookEntries.filter {
            it.injectionPosition == PromptInjectionPosition.BEFORE_LATEST
        }
        val depthEntries = lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.AT_DEPTH }
        fun skillMessage(skill: PortableSkill) =
            UIMessage.user(PromptInjectionEngine.inContextSkillText(skill))
        fun lorebookMessage(entry: PortableLorebookEntry) =
            UIMessage.user(PromptInjectionEngine.inContextLorebookText(entry))
        return buildList {
            if (systemPrompt.isNotBlank()) add(UIMessage.system(systemPrompt))
            topSkills.forEach { add(skillMessage(it)) }
            topEntries.forEach { add(lorebookMessage(it)) }
            val dynamicContext = buildList {
                if (!contextSummary.isNullOrBlank()) {
                    add("[Conversation Summary (Earlier context)]:\n$contextSummary")
                }
                if (memoryPrompt.isNotBlank()) add(memoryPrompt)
                if (!timeAwareness.isNullOrBlank()) add(timeAwareness)
            }.joinToString("\n")
            if (history.isNotEmpty()) {
                val turnGroups = history.toTurnGroups()
                val latestTurnGroup = turnGroups.last()
                val historicalTurnGroups = turnGroups.dropLast(1)
                val depthByGroupIndex = depthSkills.groupBy { skill ->
                    (historicalTurnGroups.size - skill.depth.coerceAtLeast(0))
                        .coerceIn(0, historicalTurnGroups.size)
                }
                val lorebookDepthByGroupIndex = depthEntries.groupBy { entry ->
                    (historicalTurnGroups.size - entry.depth.coerceAtLeast(0))
                        .coerceIn(0, historicalTurnGroups.size)
                }
                for (groupIndex in 0..historicalTurnGroups.size) {
                    depthByGroupIndex[groupIndex].orEmpty().forEach { add(skillMessage(it)) }
                    lorebookDepthByGroupIndex[groupIndex].orEmpty().forEach { add(lorebookMessage(it)) }
                    if (groupIndex < historicalTurnGroups.size) {
                        addAll(historicalTurnGroups[groupIndex].messages)
                    }
                }
                afterChatSkills.forEach { add(skillMessage(it)) }
                beforeLatestEntries.forEach { add(lorebookMessage(it)) }
                val latestMessages = latestTurnGroup.messages
                val initiatingUserIndex = latestMessages.indexOfFirst { it.role == MessageRole.USER }
                if (initiatingUserIndex != -1) {
                    latestMessages.forEachIndexed { idx, msg ->
                        if (idx == initiatingUserIndex) {
                            var userParts = msg.parts
                            if (attachments.isNotEmpty()) userParts = attachments + userParts
                            if (dynamicContext.isNotBlank()) {
                                userParts = listOf(
                                    UIMessagePart.Text("<system>\n$dynamicContext\n</system>\n\n"),
                                ) + userParts
                            }
                            add(msg.copy(parts = userParts))
                        } else {
                            add(msg)
                        }
                    }
                } else {
                    if (dynamicContext.isNotBlank()) add(UIMessage.system(dynamicContext))
                    if (attachments.isNotEmpty()) {
                        add(UIMessage(role = MessageRole.USER, parts = attachments))
                    }
                    addAll(latestMessages)
                }
            } else {
                depthSkills.forEach { add(skillMessage(it)) }
                depthEntries.forEach { add(lorebookMessage(it)) }
                afterChatSkills.forEach { add(skillMessage(it)) }
                beforeLatestEntries.forEach { add(lorebookMessage(it)) }
                if (dynamicContext.isNotBlank()) add(UIMessage.system(dynamicContext))
                if (attachments.isNotEmpty()) {
                    add(UIMessage(role = MessageRole.USER, parts = attachments))
                }
            }
        }
    }

    private fun pruneSearchResults(
        messages: List<UIMessage>,
        maxSearchResultsRetained: Int?,
    ): List<UIMessage> {
        val maxSearches = maxSearchResultsRetained?.takeIf { it > 0 } ?: return messages
        val searchResultIndices = messages.mapIndexedNotNull { index, msg ->
            val hasSearchResult = msg.parts.any { part ->
                part is UIMessagePart.ToolResult && part.toolName == "search_web"
            }
            if (hasSearchResult) index else null
        }
        val indicesToPrune = searchResultIndices.dropLast(maxSearches).toSet()
        if (indicesToPrune.isEmpty()) return messages
        return messages.mapIndexed { index, msg ->
            if (index !in indicesToPrune) {
                msg
            } else {
                msg.copy(
                    parts = msg.parts.map { part ->
                        if (part is UIMessagePart.ToolResult && part.toolName == "search_web") {
                            part.copy(
                                content = kotlinx.serialization.json.buildJsonObject {
                                    put(
                                        "note",
                                        kotlinx.serialization.json.JsonPrimitive(
                                            "Earlier search results pruned to save context",
                                        ),
                                    )
                                },
                            )
                        } else part
                    },
                )
            }
        }
    }

    private fun selectMemoryContext(
        candidates: List<PortableMemoryRecord>,
        model: Model,
        inputBudgetTokens: Int,
        requiredContextTokens: Int,
        historyMessages: List<UIMessage>,
        contextPriority: PortableContextPriority,
        smartEnabled: Boolean,
        episodeGroup: (Long) -> String,
        overridePrompt: String?,
    ): Pair<List<PortableMemoryRecord>, String> {
        if (!overridePrompt.isNullOrBlank() && candidates.isEmpty()) {
            return emptyList<PortableMemoryRecord>() to overridePrompt
        }
        if (!smartEnabled) {
            val selected = candidates.take(maxOf(2, candidates.size))
            val prompt = overridePrompt?.takeIf { it.isNotBlank() }
                ?: renderMemoryContextPrompt(model, selected, episodeGroup)
            return selected to prompt
        }
        val remaining = (inputBudgetTokens - requiredContextTokens).coerceAtLeast(0)
        val memoryShare = when (contextPriority) {
            PortableContextPriority.CHAT_HISTORY -> 0.20
            PortableContextPriority.BALANCED -> 0.40
            PortableContextPriority.MEMORIES -> 0.65
        }
        val allocation = (remaining * memoryShare).toInt().coerceIn(0, remaining)
        val selected = mutableListOf<PortableMemoryRecord>()
        var rendered = ""
        candidates.forEach { candidate ->
            val proposed = selected + candidate
            val proposedText = renderMemoryContextPrompt(model, proposed, episodeGroup)
            val proposedTokens = ContextTokenEstimator.textTokens(proposedText, model)
            if (proposedTokens <= allocation) {
                selected += candidate
                rendered = proposedText
            }
        }
        if (rendered.isBlank() && (selected.isNotEmpty() || ModelAbility.TOOL in model.abilities)) {
            rendered = renderMemoryContextPrompt(model, selected, episodeGroup)
        }
        if (!overridePrompt.isNullOrBlank()) rendered = overridePrompt
        return selected to rendered
    }

    private fun attachmentParts(
        skills: List<PortableSkill>,
        entries: List<PortableLorebookEntry>,
    ): List<UIMessagePart> {
        return (skills.flatMap { it.attachments } + entries.flatMap { it.attachments }).map { attachment ->
            when (attachment.type.lowercase()) {
                "image" -> UIMessagePart.Image(url = attachment.url)
                "video" -> UIMessagePart.Video(url = attachment.url)
                "audio" -> UIMessagePart.Audio(url = attachment.url)
                else -> UIMessagePart.Document(
                    url = attachment.url,
                    fileName = attachment.fileName,
                    mime = attachment.mime.ifBlank { "application/octet-stream" },
                )
            }
        }
    }

    private fun buildUsedModes(
        skills: List<PortableSkill>,
        conversationSkillIds: Set<String>,
        turnScopedSkillIds: Set<String>,
    ): List<UsedMode> {
        return skills.mapIndexed { index, skill ->
            val reason = when {
                skill.alwaysEnabled -> SKILL_REASON_ALWAYS
                turnScopedSkillIds.contains(skill.id) -> SKILL_REASON_TURN
                conversationSkillIds.contains(skill.id) -> SKILL_REASON_CONVERSATION
                else -> SKILL_REASON_ASSISTANT
            }
            UsedMode(
                modeId = skill.id,
                modeName = skill.name,
                modeIcon = skill.icon,
                priority = skills.size - index,
                activationReason = reason,
            )
        }
    }

    private fun isConversational(message: UIMessage): Boolean {
        return message.parts.any { part ->
            when (part) {
                is UIMessagePart.Text -> part.text.isNotBlank()
                is UIMessagePart.Image -> part.url.isNotBlank()
                is UIMessagePart.Document -> part.url.isNotBlank()
                else -> true
            }
        }
    }

    private suspend fun preprocessImages(
        request: PortablePrepareRequest,
        smartEnabled: Boolean,
        maxTokens: Int,
    ): List<UIMessage> {
        val messages = request.messages
        val ocr = request.transformerContext?.ocrRuntime
        if (smartEnabled) {
            val imageLimit = adaptiveImageLimit(request.model, maxTokens)
            var retainedImages = 0
            return messages.asReversed().map { message ->
                val reversedParts = message.parts.asReversed().map { part ->
                    if (part !is UIMessagePart.Image) return@map part
                    if (retainedImages < imageLimit) {
                        retainedImages++
                        return@map part
                    }
                    val ocrText = ocr?.describeImage(part.url)
                    if (ocrText.isNullOrBlank()) {
                        part
                    } else {
                        UIMessagePart.Text("[Earlier image OCR]\n$ocrText")
                    }
                }.asReversed()
                message.copy(parts = reversedParts)
            }.asReversed()
        }
        val threshold = request.assistant.archiveImagesAfterMessageAge?.takeIf { it > 0 } ?: return messages
        val archiveBeforeIndex = (messages.size - threshold).coerceAtLeast(0)
        if (archiveBeforeIndex <= 0) return messages
        return messages.mapIndexed { index, message ->
            if (index >= archiveBeforeIndex) return@mapIndexed message
            var changed = false
            val updatedParts = message.parts.map { part ->
                if (part is UIMessagePart.Image) {
                    val ocrText = ocr?.describeImage(part.url)
                    if (!ocrText.isNullOrBlank()) {
                        changed = true
                        UIMessagePart.Text("[Archived image OCR]\n$ocrText")
                    } else {
                        part
                    }
                } else {
                    part
                }
            }
            if (changed) message.copy(parts = updatedParts) else message
        }
    }

    private fun recentChatMemories(
        request: PortablePrepareRequest,
        history: List<UIMessage>,
    ): List<PortableMemoryRecord> {
        if (!request.assistant.enableMemory ||
            !request.assistant.enableRecentChatsReference ||
            history.size > 2
        ) {
            return emptyList()
        }
        return request.recentChats
            .filter { chat ->
                chat.isToday &&
                    chat.title.isNotBlank() &&
                    chat.id != request.activeConversationId
            }
            .take(3)
            .map { chat ->
                PortableMemoryRecord(
                    id = -1,
                    content = "Participated in conversation: ${chat.title}",
                    type = 1,
                    timestamp = chat.updatedAtEpochMs,
                )
            }
    }
}
