package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.ai.core.merge
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.ai.ui.truncate
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.tools.parseJsonElementWithRecovery
import me.rerere.rikkahub.data.ai.tools.recoverInlineAskUserToolCall
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transformInput
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.data.model.hasManualSkillSelectionOverride
import me.rerere.rikkahub.data.model.withoutSkillSelectionOverride
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"
private const val SKILL_MANAGEMENT_TOOL_NAME = "manage_skills"
internal const val MEMORY_SEARCH_TOOL_NAME = "search_memory"

internal fun shouldRegisterMemorySearchTool(assistant: Assistant): Boolean {
    return assistant.enableMemory && assistant.enableMemorySearchTool
}
private const val SKILL_REASON_ASSISTANT = "Enabled for assistant"
private const val SKILL_REASON_CONVERSATION = "Enabled for chat"
private const val SKILL_REASON_TURN = "Activated for this turn"
private const val SKILL_REASON_ALWAYS = "Always enabled"

/**
 * Result of building messages, includes both the messages and info about activated context sources.
 */
data class BuildMessagesResult(
    val messages: List<UIMessage>,
    val activatedLorebookEntries: List<me.rerere.ai.ui.UsedLorebookEntry>,
    val usedModes: List<me.rerere.ai.ui.UsedMode> = emptyList(),
    val usedMemories: List<me.rerere.ai.ui.UsedMemory> = emptyList()
)

internal data class SkillToolState(
    val activeSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val availableSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val blockedSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val activeSkillIds: Set<Uuid>,
)

internal data class SkillActivationOutcome(
    val activatedSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val alreadyActiveSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val blockedSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val unmatchedTargets: List<String>,
    val updatedTurnScopedSkillIds: Set<Uuid>,
    val activeSkillIds: Set<Uuid>,
)

internal fun resolveManualSkillIds(
    assistantDefaultSkillIds: Set<Uuid>,
    conversationSkillIds: Set<Uuid>,
    allSkillIds: Set<Uuid>,
): Set<Uuid> {
    val baseSkillIds = if (conversationSkillIds.hasManualSkillSelectionOverride() || conversationSkillIds.isNotEmpty()) {
        conversationSkillIds.withoutSkillSelectionOverride()
    } else {
        assistantDefaultSkillIds
    }
    return baseSkillIds.intersect(allSkillIds)
}

internal fun resolveActiveSkillIds(
    assistantDefaultSkillIds: Set<Uuid>,
    conversationSkillIds: Set<Uuid>,
    turnScopedSkillIds: Set<Uuid>,
    allSkillIds: Set<Uuid>,
    alwaysEnabledSkillIds: Set<Uuid> = emptySet(),
): Set<Uuid> {
    val defaultEnabledSkillIds = if (conversationSkillIds.hasManualSkillSelectionOverride()) {
        emptySet()
    } else {
        alwaysEnabledSkillIds
    }
    return (
        resolveManualSkillIds(
            assistantDefaultSkillIds = assistantDefaultSkillIds,
            conversationSkillIds = conversationSkillIds,
            allSkillIds = allSkillIds,
        ) + turnScopedSkillIds + defaultEnabledSkillIds
        ).intersect(allSkillIds)
}

internal fun buildSkillToolState(
    skills: List<me.rerere.rikkahub.data.model.Skill>,
    assistantId: Uuid,
    assistantDefaultSkillIds: Set<Uuid>,
    conversationSkillIds: Set<Uuid>,
    turnScopedSkillIds: Set<Uuid>,
): SkillToolState {
    val usableSkills = skills.filter { skill ->
        skill.instructions.isNotBlank() && skill.isAvailableForAssistant(assistantId)
    }
    val allSkillIds = usableSkills.map { it.id }.toSet()
    val alwaysEnabledSkillIds = usableSkills.filter { it.alwaysEnabled }.map { it.id }.toSet()
    val activeSkillIds = resolveActiveSkillIds(
        assistantDefaultSkillIds = assistantDefaultSkillIds,
        conversationSkillIds = conversationSkillIds,
        turnScopedSkillIds = turnScopedSkillIds,
        allSkillIds = allSkillIds,
        alwaysEnabledSkillIds = alwaysEnabledSkillIds,
    )
    // Always-enabled skills are invisible to the manage_skills tool:
    // they cannot be toggled by the AI so they don't appear in any tool list.
    val toggleableSkills = usableSkills
    val autonomousSkills = toggleableSkills.filter { skill ->
        skill.canAssistantAutonomouslyToggle(assistantId)
    }

    return SkillToolState(
        activeSkills = autonomousSkills.filter { skill -> activeSkillIds.contains(skill.id) },
        availableSkills = autonomousSkills.filterNot { skill -> activeSkillIds.contains(skill.id) },
        blockedSkills = toggleableSkills.filterNot { skill -> skill.canAssistantAutonomouslyToggle(assistantId) },
        activeSkillIds = activeSkillIds,
    )
}

private fun normalizeSkillKey(value: String): String {
    return value.trim().lowercase(Locale.ROOT)
}

private fun buildSkillLookup(
    skills: List<me.rerere.rikkahub.data.model.Skill>,
): Map<String, me.rerere.rikkahub.data.model.Skill> {
    return buildMap {
        skills.forEach { skill ->
            put(skill.id.toString().lowercase(Locale.ROOT), skill)
            val normalizedName = skill.name.trim().lowercase(Locale.ROOT)
            if (normalizedName.isNotBlank()) {
                put(normalizedName, skill)
            }
        }
    }
}

internal fun activateSkillsForTurn(
    targets: List<String>,
    availableSkills: List<me.rerere.rikkahub.data.model.Skill>,
    activeSkills: List<me.rerere.rikkahub.data.model.Skill>,
    blockedSkills: List<me.rerere.rikkahub.data.model.Skill>,
    currentTurnScopedSkillIds: Set<Uuid>,
): SkillActivationOutcome {
    val availableByKey = buildSkillLookup(availableSkills)
    val activeByKey = buildSkillLookup(activeSkills)
    val blockedByKey = buildSkillLookup(blockedSkills)

    val activated = linkedSetOf<me.rerere.rikkahub.data.model.Skill>()
    val alreadyActive = linkedSetOf<me.rerere.rikkahub.data.model.Skill>()
    val blocked = linkedSetOf<me.rerere.rikkahub.data.model.Skill>()
    val unmatched = mutableListOf<String>()

    targets.forEach { rawTarget ->
        val targetKey = normalizeSkillKey(rawTarget)
        when {
            availableByKey.containsKey(targetKey) -> activated += availableByKey.getValue(targetKey)
            activeByKey.containsKey(targetKey) -> alreadyActive += activeByKey.getValue(targetKey)
            blockedByKey.containsKey(targetKey) -> blocked += blockedByKey.getValue(targetKey)
            else -> unmatched += rawTarget
        }
    }

    return SkillActivationOutcome(
        activatedSkills = activated.toList(),
        alreadyActiveSkills = alreadyActive.toList(),
        blockedSkills = blocked.toList(),
        unmatchedTargets = unmatched,
        updatedTurnScopedSkillIds = currentTurnScopedSkillIds + activated.map { it.id },
        activeSkillIds = activeSkills.map { it.id }.toSet() + activated.map { it.id },
    )
}

internal fun buildUsedModes(
    availableSkills: List<me.rerere.rikkahub.data.model.Skill>,
    assistantDefaultSkillIds: Set<Uuid>,
    conversationSkillIds: Set<Uuid>,
    turnScopedSkillIds: Set<Uuid>,
    alwaysEnabledSkillIds: Set<Uuid> = availableSkills.filter { it.alwaysEnabled }.map { it.id }.toSet(),
): List<me.rerere.ai.ui.UsedMode> {
    val allSkillIds = availableSkills.map { it.id }.toSet()
    val activeSkillIds = resolveActiveSkillIds(
        assistantDefaultSkillIds = assistantDefaultSkillIds,
        conversationSkillIds = conversationSkillIds,
        turnScopedSkillIds = turnScopedSkillIds,
        allSkillIds = allSkillIds,
        alwaysEnabledSkillIds = alwaysEnabledSkillIds,
    )
    val enabledSkills = availableSkills.filter { skill ->
        activeSkillIds.contains(skill.id)
    }

    return enabledSkills.mapIndexed { index, skill ->
        val reason = when {
            skill.alwaysEnabled -> SKILL_REASON_ALWAYS
            turnScopedSkillIds.contains(skill.id) -> SKILL_REASON_TURN
            conversationSkillIds.contains(skill.id) -> SKILL_REASON_CONVERSATION
            else -> SKILL_REASON_ASSISTANT
        }
        me.rerere.ai.ui.UsedMode(
            modeId = skill.id.toString(),
            modeName = skill.name,
            modeIcon = skill.icon,
            priority = enabledSkills.size - index,
            activationReason = reason,
        )
    }
}

internal fun createSkillManagementTool(
    state: SkillToolState,
    currentTurnScopedSkillIds: Set<Uuid>,
    onUpdateTurnScopedSkillIds: suspend (Set<Uuid>) -> Unit,
): Tool? {
    if (state.availableSkills.isEmpty()) {
        return null
    }

    fun parseTargets(args: kotlinx.serialization.json.JsonElement): List<String> {
        val params = args.jsonObject
        val targetsFromList = (params["skills"] as? JsonArray)
            ?.mapNotNull { item ->
                (item as? JsonPrimitive)?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            ?: emptyList()
        val targetFromSingle = (params["skill"] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return (targetsFromList + listOfNotNull(targetFromSingle))
            .flatMap { raw ->
                raw.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            .distinct()
    }

    fun summarizeSkill(skill: me.rerere.rikkahub.data.model.Skill): String {
        return skill.description
            .ifBlank { "No description provided." }
            .replace('\n', ' ')
            .trim()
            .take(160)
    }

    return Tool(
        name = SKILL_MANAGEMENT_TOOL_NAME,
        description = "Activate available skills for the current assistant turn only.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("skills", buildJsonObject {
                        put("type", "array")
                        put("description", "Skills to activate for this turn, referenced by exact id or exact skill name.")
                        put("items", buildJsonObject {
                            put("type", "string")
                        })
                    })
                    put("skill", buildJsonObject {
                        put("type", "string")
                        put("description", "Single skill to activate for this turn, referenced by exact id or exact skill name.")
                    })
                },
                required = emptyList(),
            )
        },
        systemPrompt = { _, _ ->
            buildString {
                appendLine("## Skill Management")
                appendLine("Use `manage_skills` only when one of the available skills is clearly needed for the current user request.")
                appendLine("Activations only apply to this assistant turn.")
                appendLine()
                appendLine(
                    if (state.activeSkills.isEmpty()) {
                        "Currently active skills: none"
                    } else {
                        "Currently active skills: ${state.activeSkills.joinToString(", ") { skill -> skill.name.ifBlank { skill.id.toString() } }}"
                    }
                )
                appendLine("Available skills:")
                append(
                    state.availableSkills.joinToString("\n") { skill ->
                        val label = skill.name.ifBlank { skill.id.toString() }
                        "- $label: ${summarizeSkill(skill)}"
                    }
                )
            }
        },
        execute = { args ->
            val targets = parseTargets(args)
            if (targets.isEmpty()) {
                error("Provide at least one skill target in `skills` or `skill`.")
            }

            val outcome = activateSkillsForTurn(
                targets = targets,
                availableSkills = state.availableSkills,
                activeSkills = state.activeSkills,
                blockedSkills = state.blockedSkills,
                currentTurnScopedSkillIds = currentTurnScopedSkillIds,
            )

            if (outcome.updatedTurnScopedSkillIds != currentTurnScopedSkillIds) {
                onUpdateTurnScopedSkillIds(outcome.updatedTurnScopedSkillIds)
            }

            buildJsonObject {
                put("updated", JsonPrimitive(outcome.updatedTurnScopedSkillIds != currentTurnScopedSkillIds))
                put(
                    "activated",
                    JsonArray(
                        outcome.activatedSkills.map { skill ->
                            buildJsonObject {
                                put("id", JsonPrimitive(skill.id.toString()))
                                put("name", JsonPrimitive(skill.name))
                            }
                        }
                    )
                )
                put(
                    "already_active",
                    JsonArray(
                        outcome.alreadyActiveSkills.map { skill ->
                            buildJsonObject {
                                put("id", JsonPrimitive(skill.id.toString()))
                                put("name", JsonPrimitive(skill.name))
                            }
                        }
                    )
                )
                put(
                    "blocked",
                    JsonArray(
                        outcome.blockedSkills.map { skill ->
                            buildJsonObject {
                                put("id", JsonPrimitive(skill.id.toString()))
                                put("name", JsonPrimitive(skill.name))
                            }
                        }
                    )
                )
                put(
                    "unmatched",
                    JsonArray(outcome.unmatchedTargets.map { JsonPrimitive(it) })
                )
                put(
                    "enabled_skill_ids",
                    JsonArray(outcome.activeSkillIds.map { JsonPrimitive(it.toString()) })
                )
            }
        }
    )
}

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
    private val chatAttachmentRepository: ChatAttachmentRepository,
    private val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
    private val embeddingService: me.rerere.rikkahub.data.ai.rag.EmbeddingService,
    private val memorySearchService: MemorySearchService,
    private val runtimeInfo: GenerationRuntimeInfo = AndroidGenerationRuntimeInfo(),
) {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        truncateIndex: Int = -1,
        maxSteps: Int = 256,
        enabledModeIds: Set<Uuid> = emptySet(),
        enabledLorebookIds: Set<Uuid>? = null,
        activeConversationId: Uuid? = null,
    ): Flow<GenerationChunk> = channelFlow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages
        val allSkillIds = settings.skills
            .filter { it.instructions.isNotBlank() }
            .map { it.id }
            .toSet()
        val assistantDefaultSkillIds = settings.skills
            .filter { it.instructions.isNotBlank() && it.isAvailableForAssistant(assistant.id) }
            .map { it.id }
            .toSet()
            .let { assistant.enabledSkillIds.intersect(it) }
        val conversationSkillIds = enabledModeIds
        var currentTurnScopedSkillIds = emptySet<Uuid>()

        for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            val toolsInternal = buildList {
                Log.i(TAG, "generateInternal: build tools($assistant)")
                // Add memory tools if memory is enabled for this assistant
                if (assistant.enableMemory) {
                    buildMemoryTools(
                        onCreation = { content ->
                            memoryRepo.addMemory(assistant.id.toString(), content)
                        },
                        onUpdate = { id, content ->
                            memoryRepo.updateContent(id, content)
                        },
                        onDelete = { id ->
                            memoryRepo.deleteMemory(id)
                        },
                        onSearch = if (shouldRegisterMemorySearchTool(assistant)) {
                            { query, limit, timeRange ->
                                memorySearchService.searchMemory(
                                    assistant = assistant,
                                    activeConversationId = activeConversationId,
                                    query = query,
                                    limit = limit,
                                    timeRange = timeRange,
                                )
                            }
                        } else {
                            null
                        }
                    ).let(this::addAll)
                }
                createSkillManagementTool(
                    state = buildSkillToolState(
                        skills = settings.skills,
                        assistantId = assistant.id,
                        assistantDefaultSkillIds = assistantDefaultSkillIds,
                        conversationSkillIds = conversationSkillIds,
                        turnScopedSkillIds = currentTurnScopedSkillIds,
                    ),
                    currentTurnScopedSkillIds = currentTurnScopedSkillIds,
                    onUpdateTurnScopedSkillIds = { updatedIds ->
                        currentTurnScopedSkillIds = updatedIds.intersect(allSkillIds)
                    },
                )?.let(this::add)
                addAll(tools)
            }

            generateInternal(
                assistant = assistant,
                settings = settings,
                messages = messages,
                onUpdateMessages = {
                    messages = it.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant
                    )
                    send(
                        GenerationChunk.Messages(
                            messages.visualTransforms(
                                transformers = outputTransformers,
                                context = context,
                                model = model,
                                assistant = assistant
                            )
                        )
                    )
                },
                transformers = inputTransformers,
                model = model,
                providerImpl = providerImpl,
                provider = provider,
                tools = toolsInternal,
                memories = memories ?: emptyList(),
                truncateIndex = truncateIndex,
                stream = assistant.streamOutput,
                conversationEnabledModeIds = conversationSkillIds,
                turnScopedEnabledModeIds = currentTurnScopedSkillIds,
                conversationEnabledLorebookIds = enabledLorebookIds,
                activeConversationId = activeConversationId,
            )
            messages = messages.visualTransforms(
                transformers = outputTransformers,
                context = context,
                model = model,
                assistant = assistant
            )
            messages = messages.onGenerationFinish(
                transformers = outputTransformers,
                context = context,
                model = model,
                assistant = assistant
            )
            messages = messages.recoverInlineAskUserToolCall(json)
            send(GenerationChunk.Messages(messages))

            val toolCalls = messages.last().getToolCalls()
            if (toolCalls.isEmpty()) {
                // no tool calls, break
                break
            }
            // handle tool calls
            val results = arrayListOf<UIMessagePart.ToolResult>()
            val pendingToolCallIds = mutableSetOf<String>()
            toolCalls.forEach { toolCall ->
                runCatching {
                    val tool = toolsInternal.find { tool -> tool.name == toolCall.toolName }
                        ?: error("Tool ${toolCall.toolName} not found")
                    val args = parseToolCallArguments(toolCall.arguments)
                    if (tool.approvalMode == ToolApprovalMode.RequiresApproval) {
                        pendingToolCallIds += toolCall.toolCallId
                        return@runCatching
                    }
                    Log.i(TAG, "generateText: executing tool ${tool.name} with args: $args")
                    val result = tool.execute(args)
                    results += UIMessagePart.ToolResult(
                        toolName = toolCall.toolName,
                        toolCallId = toolCall.toolCallId,
                        content = result,
                        arguments = args,
                        metadata = toolCall.metadata
                    )
                }.onFailure {
                    Log.e(TAG, "Tool execution failed: ${toolCall.toolName}", it)
                    results += UIMessagePart.ToolResult(
                        toolName = toolCall.toolName,
                        toolCallId = toolCall.toolCallId,
                        metadata = toolCall.metadata,
                        content = buildJsonObject {
                            put(
                                "error",
                                JsonPrimitive(formatToolExecutionError(it))
                            )
                        },
                        arguments = runCatching {
                            parseToolCallArguments(toolCall.arguments)
                        }.getOrElse { JsonObject(emptyMap()) }
                    )
                }
            }
            if (pendingToolCallIds.isNotEmpty()) {
                messages = messages.markPendingToolCalls(pendingToolCallIds)
                send(GenerationChunk.Messages(messages))
                if (results.isNotEmpty()) {
                    messages = messages + UIMessage(
                        role = MessageRole.TOOL,
                        parts = results
                    )
                    send(
                        GenerationChunk.Messages(
                            messages.transforms(
                                transformers = outputTransformers,
                                context = context,
                                model = model,
                                assistant = assistant
                            )
                        )
                    )
                }
                break
            }
            messages = messages + UIMessage(
                role = MessageRole.TOOL,
                parts = results
            )
            send(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant
                    )
                )
            )
        }

    }.flowOn(Dispatchers.IO)

    suspend fun buildMessages(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        model: Model,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        truncateIndex: Int,
        conversationEnabledModeIds: Set<Uuid> = emptySet(),
        turnScopedEnabledModeIds: Set<Uuid> = emptySet(),
        conversationEnabledLorebookIds: Set<Uuid>? = null,
    ): BuildMessagesResult {
        // Token estimator (rough estimate: 4 chars per token)
        fun estimateTokens(text: String) = text.length / 4
        fun estimateTokens(message: UIMessage) = estimateTokens(message.toText())

        val maxTokens = assistant.maxTokenUsage
        var currentTokens = 0

        // Cosine similarity for RAG matching
        fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
            if (a.size != b.size) return 0f
            var dotProduct = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dotProduct += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
            return if (denominator == 0f) 0f else dotProduct / denominator
        }
        
        // Helper to check if and why lorebook entry activated
        fun getLorebookEntryActivationReason(entry: LorebookEntry, recentMessages: List<String>, queryEmbedding: List<Float>? = null): String? {
            if (!entry.enabled) return null
            return when (entry.activationType) {
                LorebookActivationType.ALWAYS -> "Always Active"
                LorebookActivationType.KEYWORDS -> {
                    val searchText = recentMessages.joinToString(" ")
                    val matchingKeyword = entry.keywords.firstOrNull { keyword ->
                        if (entry.useRegex) {
                            try {
                                val regex = if (entry.caseSensitive) {
                                    Regex(keyword)
                                } else {
                                    Regex(keyword, RegexOption.IGNORE_CASE)
                                }
                                regex.containsMatchIn(searchText)
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid regex in lorebook entry: $keyword", e)
                                false
                            }
                        } else {
                            if (entry.caseSensitive) {
                                searchText.contains(keyword)
                            } else {
                                searchText.contains(keyword, ignoreCase = true)
                            }
                        }
                    }
                    if (matchingKeyword != null) "Keyword: $matchingKeyword" else null
                }
                LorebookActivationType.RAG -> {
                    // RAG activation uses embedding similarity
                    if (entry.embedding == null || entry.embedding.isEmpty()) {
                        Log.d(TAG, "RAG entry '${entry.name}' has no embedding, skipping")
                        null
                    } else if (queryEmbedding == null) {
                        Log.d(TAG, "No query embedding available for RAG matching")
                        null
                    } else {
                        // Compute cosine similarity
                        val similarity = cosineSimilarity(entry.embedding, queryEmbedding)
                        val threshold = 0.7f // Similarity threshold for activation
                        val activated = similarity >= threshold
                        if (activated) {
                            val scoreStr = try {
                                "%.2f".format(similarity)
                            } catch (e: Exception) {
                                similarity.toString().take(4)
                            }
                            Log.d(TAG, "RAG entry '${entry.name}' activated with similarity $similarity")
                            "RAG Match ($scoreStr)"
                        } else null
                    }
                }
            }
        }

        // Get recent message text for lorebook keyword scanning
        val recentMessagesForScan = messages.takeLast(10).map { it.toText() }

        val availableSkills = settings.skills.filter { skill ->
            skill.instructions.isNotBlank()
        }
        val allSkillIds = availableSkills.map { it.id }.toSet()
        val assistantAvailableSkillIds = settings.skills
            .filter { it.instructions.isNotBlank() && it.isAvailableForAssistant(assistant.id) }
            .map { it.id }
            .toSet()
        val alwaysEnabledSkillIds = availableSkills
            .filter { it.alwaysEnabled && assistantAvailableSkillIds.contains(it.id) }
            .map { it.id }
            .toSet()
        val assistantDefaultSkillIds = settings.skills
            .filter { it.instructions.isNotBlank() && it.isAvailableForAssistant(assistant.id) }
            .map { it.id }
            .toSet()
            .let { assistant.enabledSkillIds.intersect(it) }
        val activeSkillIds = resolveActiveSkillIds(
            assistantDefaultSkillIds = assistantDefaultSkillIds,
            conversationSkillIds = conversationEnabledModeIds,
            turnScopedSkillIds = turnScopedEnabledModeIds,
            allSkillIds = allSkillIds,
            alwaysEnabledSkillIds = alwaysEnabledSkillIds,
        )
        val enabledSkills = availableSkills.filter { activeSkillIds.contains(it.id) }
        val usedModes = buildUsedModes(
            availableSkills = availableSkills,
            assistantDefaultSkillIds = assistantDefaultSkillIds,
            conversationSkillIds = conversationEnabledModeIds,
            turnScopedSkillIds = turnScopedEnabledModeIds,
            alwaysEnabledSkillIds = alwaysEnabledSkillIds,
        )

        // Check if any lorebook entries use RAG activation
        val activeLorebookIds = conversationEnabledLorebookIds ?: assistant.enabledLorebookIds
        val lorebooksForAssistant = settings.lorebooks
            .filter { it.enabled && activeLorebookIds.contains(it.id) }
        val hasRagEntries = lorebooksForAssistant.any { lorebook ->
            lorebook.entries.any { it.activationType == LorebookActivationType.RAG && it.enabled }
        }
        
        // Compute query embedding only if there are RAG entries
        val queryEmbedding: List<Float>? = if (hasRagEntries) {
            try {
                val queryText = recentMessagesForScan.takeLast(3).joinToString("\n")
                if (queryText.isNotBlank()) {
                    embeddingService.embed(queryText)
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to compute query embedding for RAG", e)
                null
            }
        } else null

        // Collect activated lorebook entries from enabled lorebooks assigned to this assistant
        // Also track UsedLorebookEntry info for the UI display
        // Collect activated lorebook entries from enabled lorebooks assigned to this assistant
        // Also track UsedLorebookEntry info for the UI display
        data class ActivatedEntryWithLorebook(val lorebook: Lorebook, val entry: LorebookEntry, val entryIndex: Int, val reason: String)
        val activatedEntriesWithLorebook = lorebooksForAssistant
            .flatMap { lorebook -> 
                lorebook.entries.mapIndexedNotNull { index, entry ->
                    val reason = getLorebookEntryActivationReason(entry, recentMessagesForScan, queryEmbedding)
                    if (reason != null) {
                        ActivatedEntryWithLorebook(lorebook, entry, index, reason)
                    } else null
                }
            }
        val activatedEntries = activatedEntriesWithLorebook.map { it.entry }
        
        // Build UsedLorebookEntry list for UI display
        val usedLorebookEntries = activatedEntriesWithLorebook.mapIndexed { priority, activated ->
            // Serialize cover Avatar to JSON string for UI display
            val coverJson = activated.lorebook.cover?.let { cover ->
                try {
                    json.encodeToString(me.rerere.rikkahub.data.model.Avatar.serializer(), cover)
                } catch (e: Exception) {
                    null
                }
            }
            me.rerere.ai.ui.UsedLorebookEntry(
                lorebookId = activated.lorebook.id.toString(),
                lorebookName = activated.lorebook.name,
                lorebookCover = coverJson,
                entryId = activated.entry.id.toString(),
                entryName = activated.entry.name,
                entryIndex = activated.entryIndex,
                priority = activatedEntriesWithLorebook.size - priority, // Higher priority for first entries
                activationReason = activated.reason
            )
        }

        // Group injections by position
        val beforeSystemSkills = enabledSkills.filter { it.injectionPosition == InjectionPosition.BEFORE_SYSTEM }
        val afterSystemSkills = enabledSkills.filter { it.injectionPosition == InjectionPosition.AFTER_SYSTEM }
        val beforeSystemEntries = activatedEntries.filter { it.injectionPosition == InjectionPosition.BEFORE_SYSTEM }
        val afterSystemEntries = activatedEntries.filter { it.injectionPosition == InjectionPosition.AFTER_SYSTEM }

        // 1. Base System Prompt (BEFORE_SYSTEM skills/entries + System + Learning + AFTER_SYSTEM skills/entries + Tools)
        val baseSystemPromptBuilder = StringBuilder()

        fun appendSkillPrompt(skill: me.rerere.rikkahub.data.model.Skill) {
            if (skill.name.isNotBlank()) {
                baseSystemPromptBuilder.append("[Skill: ${skill.name}]")
                baseSystemPromptBuilder.appendLine()
            }
            baseSystemPromptBuilder.append(skill.instructions)
        }
        
        // BEFORE_SYSTEM injections
        beforeSystemSkills.forEach { skill ->
            appendSkillPrompt(skill)
            baseSystemPromptBuilder.appendLine()
        }
        beforeSystemEntries.forEach { entry ->
            baseSystemPromptBuilder.append(entry.prompt)
            baseSystemPromptBuilder.appendLine()
        }
        
        // Original system prompt  
        if (assistant.systemPrompt.isNotBlank()) {
            baseSystemPromptBuilder.append(assistant.systemPrompt)
        }
        
        // Learning mode (legacy - still supported)
        if (assistant.learningMode) {
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(settings.learningModePrompt.ifEmpty { DEFAULT_LEARNING_MODE_PROMPT })
            baseSystemPromptBuilder.appendLine()
        }
        
        // AFTER_SYSTEM injections
        afterSystemSkills.forEach { skill ->
            baseSystemPromptBuilder.appendLine()
            appendSkillPrompt(skill)
        }
        afterSystemEntries.forEach { entry ->
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(entry.prompt)
        }
        
        // Tool prompts
        tools.forEach { tool ->
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(tool.systemPrompt(model, messages))
        }
        val baseSystemPrompt = baseSystemPromptBuilder.toString()
        currentTokens += estimateTokens(baseSystemPrompt)

        // 2. Prepare Candidates
        // Apply message history limit if configured
        val historyLimitedMessages = assistant.maxHistoryMessages?.let { limit ->
            if (limit > 0) messages.limitContext(limit) else messages
        } ?: messages
        
        // Prune search results if configured
        val searchPrunedMessages = assistant.maxSearchResultsRetained?.let { maxSearches ->
            if (maxSearches > 0) {
                // Find all messages that contain search tool results
                val searchResultIndices = historyLimitedMessages.mapIndexedNotNull { index, msg ->
                    val hasSearchResult = msg.parts.any { part ->
                        part is UIMessagePart.ToolResult && part.toolName == "search_web"
                    }
                    if (hasSearchResult) index else null
                }
                
                // Keep only the last N search results
                val indicesToPrune = searchResultIndices.dropLast(maxSearches).toSet()
                
                if (indicesToPrune.isNotEmpty()) {
                    historyLimitedMessages.mapIndexed { index, msg ->
                        if (index in indicesToPrune) {
                            // Replace search result content with a minimal placeholder
                            msg.copy(parts = msg.parts.map { part ->
                                if (part is UIMessagePart.ToolResult && part.toolName == "search_web") {
                                    part.copy(content = kotlinx.serialization.json.buildJsonObject {
                                        put("note", kotlinx.serialization.json.JsonPrimitive("Earlier search results pruned to save context"))
                                    })
                                } else part
                            })
                        } else msg
                    }
                } else historyLimitedMessages
            } else historyLimitedMessages
        } ?: historyLimitedMessages
        val imageArchivedMessages = archiveOldImageMessages(
            messages = searchPrunedMessages,
            assistant = assistant,
        )
        
        // Chat History (reverse order to prioritize recent)
        val chatHistoryCandidates = imageArchivedMessages.truncate(truncateIndex).reversed()
        
        // Memories (Prepare effective memories including recent chats if enabled)
        val effectiveMemoriesCandidates = if (assistant.enableMemory) {
            val recentChatMemories = if (assistant.enableRecentChatsReference && messages.size <= 2) {
                val recentConversations = conversationRepo.getRecentConversations(
                    assistantId = assistant.id,
                    limit = 3,
                ).filter { 
                    runtimeInfo.isToday(it.updateAt)
                }
                recentConversations.map { conversation ->
                    AssistantMemory(
                        id = -1,
                        content = "Participated in conversation: ${conversation.title}",
                        type = 1,
                        timestamp = conversation.updateAt.toEpochMilli()
                    )
                }
            } else {
                emptyList()
            }
            (memories + recentChatMemories).distinctBy { it.content } // Avoid duplicates
        } else {
            emptyList()
        }

        // 3. Allocation Logic
        val selectedMessages = mutableListOf<UIMessage>()
        val selectedMemories = mutableListOf<AssistantMemory>()
        
        val remainingTokens = maxTokens - currentTokens
        if (remainingTokens <= 0) {
            // Edge case: System prompt too large. Just return minimums.
            Log.w(TAG, "buildMessages: System prompt exceeds max tokens!")
        }

        // Minimums
        val minChatHistory = 2.coerceAtMost(chatHistoryCandidates.size)
        val minMemories = if (assistant.enableMemory) 2.coerceAtMost(effectiveMemoriesCandidates.size) else 0

        // Add minimums first
        var usedTokens = 0
        
        // Add min chat history
        chatHistoryCandidates.take(minChatHistory).forEach {
            selectedMessages.add(it)
            usedTokens += estimateTokens(it)
        }
        
        // Add min memories
        effectiveMemoriesCandidates.take(minMemories).forEach {
            selectedMemories.add(it)
            usedTokens += estimateTokens(it.content)
        }

        // Distribute remaining tokens
        var availableTokens = remainingTokens - usedTokens
        if (availableTokens > 0) {
            val remainingChatHistory = chatHistoryCandidates.drop(minChatHistory)
            val remainingMemories = effectiveMemoriesCandidates.drop(minMemories)
            
            when (assistant.contextPriority) {
                me.rerere.rikkahub.data.model.ContextPriority.CHAT_HISTORY -> {
                    // Prioritize Chat History
                    for (msg in remainingChatHistory) {
                        val cost = estimateTokens(msg)
                        if (availableTokens >= cost) {
                            selectedMessages.add(msg)
                            availableTokens -= cost
                        } else break
                    }
                    for (mem in remainingMemories) {
                        val cost = estimateTokens(mem.content)
                        if (availableTokens >= cost) {
                            selectedMemories.add(mem)
                            availableTokens -= cost
                        }
                    }
                }
                me.rerere.rikkahub.data.model.ContextPriority.MEMORIES -> {
                    // Prioritize Memories
                    for (mem in remainingMemories) {
                        val cost = estimateTokens(mem.content)
                        if (availableTokens >= cost) {
                            selectedMemories.add(mem)
                            availableTokens -= cost
                        }
                    }
                    for (msg in remainingChatHistory) {
                        val cost = estimateTokens(msg)
                        if (availableTokens >= cost) {
                            selectedMessages.add(msg)
                            availableTokens -= cost
                        } else break
                    }
                }
                me.rerere.rikkahub.data.model.ContextPriority.BALANCED -> {
                    // Balanced (e.g. 50/50 split of remaining, or round-robin)
                    // Simple round-robin approach
                    var msgIndex = 0
                    var memIndex = 0
                    var addedSomething = true
                    while (addedSomething && availableTokens > 0) {
                        addedSomething = false
                        // Try add message
                        if (msgIndex < remainingChatHistory.size) {
                            val msg = remainingChatHistory[msgIndex]
                            val cost = estimateTokens(msg)
                            if (availableTokens >= cost) {
                                selectedMessages.add(msg)
                                availableTokens -= cost
                                msgIndex++
                                addedSomething = true
                            }
                        }
                        // Try add memory
                        if (memIndex < remainingMemories.size) {
                            val mem = remainingMemories[memIndex]
                            val cost = estimateTokens(mem.content)
                            if (availableTokens >= cost) {
                                selectedMemories.add(mem)
                                availableTokens -= cost
                                memIndex++
                                addedSomething = true
                            }
                        }
                    }
                }
            }
        }

        // 4. Construct Final List
        // Collect all attachments from enabled skills
        val skillAttachmentParts = enabledSkills.flatMap { skill ->
            skill.attachments.map { attachment ->
                when (attachment.type) {
                    ModeAttachmentType.IMAGE -> UIMessagePart.Image(url = attachment.url)
                    ModeAttachmentType.VIDEO -> UIMessagePart.Video(url = attachment.url)
                    ModeAttachmentType.AUDIO -> UIMessagePart.Audio(url = attachment.url)
                    ModeAttachmentType.DOCUMENT -> UIMessagePart.Document(
                        url = attachment.url,
                        fileName = attachment.fileName,
                        mime = attachment.mime
                    )
                }
            }
        }
        
        // Collect attachments from activated lorebook entries
        val lorebookAttachmentParts = activatedEntries.flatMap { entry ->
            entry.attachments.map { attachment ->
                when (attachment.type) {
                    ModeAttachmentType.IMAGE -> UIMessagePart.Image(url = attachment.url)
                    ModeAttachmentType.VIDEO -> UIMessagePart.Video(url = attachment.url)
                    ModeAttachmentType.AUDIO -> UIMessagePart.Audio(url = attachment.url)
                    ModeAttachmentType.DOCUMENT -> UIMessagePart.Document(
                        url = attachment.url,
                        fileName = attachment.fileName,
                        mime = attachment.mime
                    )
                }
            }
        }
        
        // Combine all context attachments
        val allContextAttachments = skillAttachmentParts + lorebookAttachmentParts
        
        val orderedSelectedMessages = selectedMessages.sortedBy { messages.indexOf(it) }
        val timeAwarenessPrompt = buildTimeAwarenessBlock(
            enabled = assistant.enableTimeAwareness,
            fullMessages = messages,
            retainedMessages = orderedSelectedMessages
        )

        val builtMessages = buildList {
            if (baseSystemPrompt.isNotBlank()) {
                add(UIMessage.system(baseSystemPrompt))
            }
            
            val dynamicContext = buildList {
                if (selectedMemories.isNotEmpty()) {
                    add(buildMemoryPrompt(model, selectedMemories))
                }
                if (!timeAwarenessPrompt.isNullOrBlank()) {
                    add(timeAwarenessPrompt)
                }
            }.joinToString(separator = "\n")

            if (orderedSelectedMessages.isNotEmpty()) {
                val lastMessage = orderedSelectedMessages.last()
                val history = orderedSelectedMessages.dropLast(1)
                
                addAll(history)
                
                var finalParts = lastMessage.parts
                
                if (allContextAttachments.isNotEmpty()) {
                    finalParts = allContextAttachments + finalParts
                }
                
                if (dynamicContext.isNotBlank()) {
                    finalParts = listOf(UIMessagePart.Text("<system>\n$dynamicContext\n</system>\n\n")) + finalParts
                }
                
                add(lastMessage.copy(parts = finalParts))
            } else {
                if (dynamicContext.isNotBlank()) {
                    add(UIMessage.system(dynamicContext))
                }
                
                if (allContextAttachments.isNotEmpty()) {
                    add(UIMessage(
                        role = me.rerere.ai.core.MessageRole.USER,
                        parts = allContextAttachments
                    ))
                }
            }
        }
        // Build UsedMemory list for UI display
        val usedMemories = selectedMemories.mapIndexed { index, memory ->
            val reason = when {
                memory.id == -1 -> "Recent episode boost"  // Recent chat reference
                assistant.useRagMemoryRetrieval -> "Contextually relevant"  // RAG mode
                else -> "Always included"  // Basic mode
            }
            me.rerere.ai.ui.UsedMemory(
                memoryId = memory.id,
                memoryContent = memory.content.take(50) + if (memory.content.length > 50) "..." else "",
                memoryType = memory.type,
                priority = selectedMemories.size - index,  // Higher priority for earlier memories
                activationReason = reason
            )
        }
        
        return BuildMessagesResult(
            messages = builtMessages,
            activatedLorebookEntries = usedLorebookEntries,
            usedModes = usedModes,
            usedMemories = usedMemories
        )
    }

    private suspend fun archiveOldImageMessages(
        messages: List<UIMessage>,
        assistant: Assistant,
    ): List<UIMessage> {
        val threshold = assistant.archiveImagesAfterMessageAge?.takeIf { it > 0 } ?: return messages
        val archiveBeforeIndex = (messages.size - threshold).coerceAtLeast(0)
        if (archiveBeforeIndex <= 0) {
            return messages
        }

        return messages.mapIndexed { index, message ->
            if (index >= archiveBeforeIndex) {
                return@mapIndexed message
            }

            var changed = false
            val updatedParts = buildList {
                message.parts.forEach { part ->
                    if (part is UIMessagePart.Image) {
                        val ocrText = chatAttachmentRepository.resolveAttachmentOcrText(
                            part = part,
                            ensureAvailable = true,
                        )
                        if (!ocrText.isNullOrBlank()) {
                            changed = true
                            add(
                                UIMessagePart.Text(
                                    """
                                    [Archived image OCR]
                                    $ocrText
                                    """.trimIndent()
                                )
                            )
                        } else {
                            add(part)
                        }
                    } else {
                        add(part)
                    }
                }
            }

            if (changed) {
                message.copy(parts = updatedParts)
            } else {
                message
            }
        }
    }

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        truncateIndex: Int,
        stream: Boolean,
        conversationEnabledModeIds: Set<Uuid> = emptySet(),
        turnScopedEnabledModeIds: Set<Uuid> = emptySet(),
        conversationEnabledLorebookIds: Set<Uuid>? = null,
        activeConversationId: Uuid? = null,
    ) {
        val buildResult = buildMessages(
            assistant = assistant,
            settings = settings,
            messages = messages,
            model = model,
            tools = tools,
            memories = memories,
            truncateIndex = truncateIndex,
            conversationEnabledModeIds = conversationEnabledModeIds,
            turnScopedEnabledModeIds = turnScopedEnabledModeIds,
            conversationEnabledLorebookIds = conversationEnabledLorebookIds,
        )
        var uiMessages = messages
        val transformedInput = buildResult.messages.transformInput(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            onProgressAnnotationsChanged = { annotations ->
                val updatedMessages = uiMessages.upsertOcrPlaceholder(annotations)
                if (updatedMessages != uiMessages) {
                    uiMessages = updatedMessages
                    onUpdateMessages(uiMessages)
                }
            },
        )
        val internalMessages = transformedInput.messages
        val usedLorebookEntries = buildResult.activatedLorebookEntries
        val usedModes = buildResult.usedModes
        val usedMemories = buildResult.usedMemories
        val hasContextSources = usedLorebookEntries.isNotEmpty() || usedModes.isNotEmpty() || usedMemories.isNotEmpty()

        var messages: List<UIMessage> = uiMessages
        if (transformedInput.annotations.isNotEmpty()) {
            val updatedMessages = messages.upsertOcrPlaceholder(transformedInput.annotations)
            if (updatedMessages != messages) {
                messages = updatedMessages
                onUpdateMessages(messages)
            }
        } else {
            val updatedMessages = messages.dropTrailingOcrPlaceholder()
            if (updatedMessages != messages) {
                messages = updatedMessages
                onUpdateMessages(messages)
            }
        }
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            topK = null,
            maxTokens = assistant.maxTokens,
            tools = tools,
            builtInTools = resolveActiveBuiltInTools(model, assistant),
            thinkingBudget = assistant.thinkingBudget,
            sessionId = activeConversationId?.toString(),
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            aiLoggingManager.addLog(AILogging.Generation(
                params = params,
                messages = messages,
                providerSetting = provider,
                stream = true
            ))
            providerImpl.streamText(
                providerSetting = provider,
                messages = internalMessages,
                params = params
            ).collect {
                messages = messages.handleMessageChunk(chunk = it, model = model)
                it.usage?.let { usage ->
                    messages = messages.mapIndexed { index, message ->
                        if (index == messages.lastIndex) {
                            message.copy(usage = message.usage.merge(usage))
                        } else {
                            message
                        }
                    }
                }
                onUpdateMessages(messages)
            }
            // Attach all context sources to the last assistant message after streaming completes
            if (hasContextSources) {
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex && message.role == me.rerere.ai.core.MessageRole.ASSISTANT) {
                        message.copy(
                            usedLorebookEntries = usedLorebookEntries.ifEmpty { null },
                            usedModes = usedModes.ifEmpty { null },
                            usedMemories = usedMemories.ifEmpty { null }
                        )
                    } else {
                        message
                    }
                }
                onUpdateMessages(messages)
            }
        } else {
            aiLoggingManager.addLog(AILogging.Generation(
                params = params,
                messages = messages,
                providerSetting = provider,
                stream = false
            ))
            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = internalMessages,
                params = params,
            )
            messages = messages.handleMessageChunk(chunk = chunk, model = model)
            chunk.usage?.let { usage ->
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex) {
                        message.copy(
                            usage = message.usage.merge(usage)
                        )
                    } else {
                        message
                    }
                }
            }
            // Attach all context sources to the last assistant message
            if (hasContextSources) {
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex && message.role == me.rerere.ai.core.MessageRole.ASSISTANT) {
                        message.copy(
                            usedLorebookEntries = usedLorebookEntries.ifEmpty { null },
                            usedModes = usedModes.ifEmpty { null },
                            usedMemories = usedMemories.ifEmpty { null }
                        )
                    } else {
                        message
                    }
                }
            }
            onUpdateMessages(messages)
        }
        
        // Persist token usage to cumulative stats
        messages.lastOrNull()?.usage?.let { usage ->
            if (usage.promptTokens > 0 || usage.completionTokens > 0) {
                try {
                    conversationRepo.addTokenUsage(
                        inputTokens = usage.promptTokens.toLong(),
                        outputTokens = usage.completionTokens.toLong(),
                        cachedTokens = usage.cachedTokens.toLong()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist token usage", e)
                }
            }
        }
    }

    private fun buildMemoryTools(
        onCreation: suspend (String) -> AssistantMemory,
        onUpdate: suspend (Int, String) -> AssistantMemory,
        onDelete: suspend (Int) -> Unit,
        onSearch: (suspend (String, Int, String?) -> JsonElement)? = null,
    ) = buildList {
        add(Tool(
            name = "create_memory",
            description = "Create a new memory record.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "Content of the memory.")
                        })
                    },
                    required = listOf("content")
                )
            },
            execute = {
                val params = it.jsonObject
                val content =
                    params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
                json.encodeToJsonElement(AssistantMemory.serializer(), onCreation(content))
            }
        ))
        add(Tool(
            name = "edit_memory",
            description = "Update an existing memory record.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "ID of the memory to update.")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "New content for the memory.")
                        })
                    },
                    required = listOf("id", "content"),
                )
            },
            execute = {
                val params = it.jsonObject
                val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                val content =
                    params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
                val before = memoryRepo.getMemoryById(id)
                val updated = onUpdate(id, content)
                buildJsonObject {
                    put("id", JsonPrimitive(updated.id))
                    put("content", JsonPrimitive(updated.content))
                    put("type", JsonPrimitive(updated.type))
                    put("hasEmbedding", JsonPrimitive(updated.hasEmbedding))
                    updated.embeddingModelId?.let { put("embeddingModelId", JsonPrimitive(it)) }
                    put("timestamp", JsonPrimitive(updated.timestamp))
                    updated.significance?.let { put("significance", JsonPrimitive(it)) }
                    before?.let { previous ->
                        put("before_content", JsonPrimitive(previous.content))
                        put("before_timestamp", JsonPrimitive(previous.timestamp))
                    }
                }
            }
        ))
        add(Tool(
            name = "delete_memory",
            description = "Delete a memory record.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "ID of the memory to delete.")
                        })
                    },
                    required = listOf("id")
                )
            },
            execute = {
                val params = it.jsonObject
                val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                val before = memoryRepo.getMemoryById(id)
                onDelete(id)
                buildJsonObject {
                    put("deleted", JsonPrimitive(true))
                    before?.let { memory ->
                        put("id", JsonPrimitive(memory.id))
                        put("content", JsonPrimitive(memory.content))
                        put("type", JsonPrimitive(memory.type))
                        put("hasEmbedding", JsonPrimitive(memory.hasEmbedding))
                        memory.embeddingModelId?.let { put("embeddingModelId", JsonPrimitive(it)) }
                        put("timestamp", JsonPrimitive(memory.timestamp))
                        memory.significance?.let { put("significance", JsonPrimitive(it)) }
                    }
                }
            }
        ))
        if (onSearch != null) {
            add(Tool(
                name = MEMORY_SEARCH_TOOL_NAME,
                description = "Search this character's core memories and actually used past chat messages for a remembered topic, scene, person, feeling, or detail. The memory subagent expands the query internally and searches multiple variants.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("query", buildJsonObject {
                                put("type", "string")
                                put("description", "The remembered topic, keyword, person, preference, event, or question to search for.")
                            })
                            put("limit", buildJsonObject {
                                put("type", "integer")
                                put("description", "Maximum number of memory results to return. Defaults to 5.")
                            })
                            put("time_range", buildJsonObject {
                                put("type", "string")
                                put("description", "Optional rough time span to filter recall, such as last week, this month, last month, 4 months ago, or yesterday.")
                            })
                        },
                        required = listOf("query")
                    )
                },
                systemPrompt = { _, _ ->
                    """
                    ## Memory search tool
                    You may call `$MEMORY_SEARCH_TOOL_NAME` when you are deliberately trying to remember something from core memories or older chats.
                    - Use it for genuine recall, not on every turn.
                    - It searches only this character's memories and actually used chat timeline, not other characters or discarded reply versions.
                    - You can pass `time_range` when the user asks about a rough time span, like "last day", "last 2 days", "yesterday", "last week", "this month", "last month", or "4 months ago".
                    - Preserve the user's recall terms, including odd meta words; the memory subagent will expand the query internally across several variants.
                    - If the returned summary says no clear memory was found and the user keeps pressing, you may try one more narrower query.
                    - Treat returned memories as approximate, human-like recollections.
                    - Time labels are fuzzy on purpose; do not expose exact timestamps unless the user asks.
                    - If confidence is low or results disagree, answer with natural uncertainty.
                    """.trimIndent()
                },
                execute = {
                    val params = it.jsonObject
                    val query = params["query"]?.jsonPrimitive?.contentOrNull ?: error("query is required")
                    val limit = params["limit"]?.jsonPrimitive?.intOrNull ?: 5
                    val timeRange = params["time_range"]?.jsonPrimitive?.contentOrNull
                    onSearch(query, limit, timeRange)
                }
            ))
        }
    }

    private suspend fun buildMemoryPrompt(model: Model, memories: List<AssistantMemory>): String {
        Log.d(TAG, "buildMemoryPrompt: Injecting ${memories.size} memories into prompt")
        if (memories.isEmpty()) {
            Log.w(TAG, "buildMemoryPrompt: WARNING - No memories to inject!")
            return ""
        }

        val coreMemories = memories.filter { it.type == 0 } // CORE
        val episodicMemories = memories.filter { it.type == 1 } // EPISODIC
        
        return buildString {
            append("## Memories\n")
            append("These are memories that you can reference in the future conversations.\n")
            
            if (coreMemories.isNotEmpty()) {
                append("### Core Memories\n")
                coreMemories.forEach { memory ->
                    append("- [ID: ${memory.id}] ${memory.content}\n")
                }
            }

            if (episodicMemories.isNotEmpty()) {
                append("### Episodic Memories\n")

                val groupedEpisodes = episodicMemories.groupBy { memory ->
                    runtimeInfo.episodicMemoryGroup(memory.timestamp)
                }
                
                // Order: Today -> Yesterday -> This Week -> Older
                listOf("Today", "Yesterday", "This Week", "Older").forEach { group ->
                    val memoriesInGroup = groupedEpisodes[group]
                    if (!memoriesInGroup.isNullOrEmpty()) {
                        append("#### $group\n")
                        memoriesInGroup.sortedByDescending { it.timestamp }.forEach { memory ->
                            append("- ${memory.content}\n")
                        }
                    }
                }
            }
            
            if (model.abilities.contains(ModelAbility.TOOL)) {
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
                    """.trimIndent()
                )
            }
        }
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        modelIdOverride: Uuid? = null,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val modelId = modelIdOverride ?: settings.translateModeId
        val model = settings.providers.findModelById(modelId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                translatedText = messages.lastOrNull()?.toContentText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                                )
                            }
                        )
                    )
                ),
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toContentText() ?: ""

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Attempts to sanitize malformed JSON from streamed tool call arguments.
     * Handles cases where the model outputs content after a valid JSON object.
     */
    private fun parseToolCallArguments(arguments: String): JsonElement {
        return parseJsonElementWithRecovery(arguments, json) ?: run {
            Log.w(TAG, "Failed to parse tool arguments after recovery: ${arguments.take(200)}")
            error("Invalid tool arguments")
        }
    }

}

internal fun formatToolExecutionError(throwable: Throwable): String {
    return throwable.message
        ?.takeIf { it.isNotBlank() }
        ?: (throwable::class.simpleName ?: "Tool execution failed")
}

internal fun List<UIMessage>.upsertOcrPlaceholder(
    annotations: List<UIMessageAnnotation>,
): List<UIMessage> {
    if (annotations.isEmpty()) {
        return dropTrailingOcrPlaceholder()
    }

    val lastMessage = lastOrNull()
    val placeholder = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = emptyList(),
        annotations = annotations,
    )

    return if (lastMessage.isTrailingOcrPlaceholder()) {
        dropLast(1) + placeholder
    } else if (
        lastMessage?.role == MessageRole.ASSISTANT &&
        lastMessage.parts.isEmpty() &&
        lastMessage.annotations.isEmpty()
    ) {
        dropLast(1) + lastMessage.copy(annotations = annotations)
    } else {
        this + placeholder
    }
}

internal fun List<UIMessage>.dropTrailingOcrPlaceholder(): List<UIMessage> {
    return if (lastOrNull().isTrailingOcrPlaceholder()) {
        dropLast(1)
    } else {
        this
    }
}

internal fun UIMessage?.isTrailingOcrPlaceholder(): Boolean {
    return this != null &&
        role == MessageRole.ASSISTANT &&
        parts.isEmpty() &&
        annotations.isNotEmpty() &&
        annotations.all { annotation -> annotation is UIMessageAnnotation.OcrActivity }
}

private fun List<UIMessage>.markPendingToolCalls(toolCallIds: Set<String>): List<UIMessage> {
    if (toolCallIds.isEmpty()) return this
    return mapIndexed { index, message ->
        if (index != lastIndex) {
            message
        } else {
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.ToolCall && toolCallIds.contains(part.toolCallId)) {
                        part.copy(approvalState = ToolApprovalState.Pending)
                    } else {
                        part
                    }
                }
            )
        }
    }
}
