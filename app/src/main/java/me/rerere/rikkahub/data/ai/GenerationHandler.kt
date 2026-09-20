package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.context.ContextUsageBreakdown
import me.rerere.ai.context.adaptiveImageLimit
import me.rerere.ai.context.compactToTokenBudget
import me.rerere.ai.context.limitImagesForModel
import me.rerere.ai.context.smartInputBudget
import me.rerere.ai.context.smartOutputTokenBudget
import me.rerere.ai.context.smartFitContext
import me.rerere.ai.context.smartPrepareHistory
import me.rerere.ai.context.effectiveHistoryForContext
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.contextCapacityTokens
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Provider
import me.rerere.ai.generation.PortableChatEngine
import me.rerere.ai.generation.PortableGenerationLoop
import me.rerere.ai.generation.PortableGenerationPrepare
import me.rerere.ai.generation.PortableGenerationSession
import me.rerere.ai.generation.PortableGenerationUpdate
import me.rerere.ai.generation.PortableInputTransformer
import me.rerere.ai.generation.PortableMemoryToolRuntime
import me.rerere.ai.generation.PortablePrepareRequest
import me.rerere.ai.generation.PortablePlaceholderTransformer
import me.rerere.ai.generation.PortableRecentChat
import me.rerere.ai.generation.PortableSkillToolBinding
import me.rerere.ai.generation.PortableTemplateRuntime
import me.rerere.ai.generation.PortableToolAssemblyOptions
import me.rerere.ai.generation.PortableToolRuntimes
import me.rerere.ai.generation.PortableTransformerContext
import me.rerere.ai.generation.PortableTurnRequest
import me.rerere.ai.generation.PortableWorkspaceReminder
import me.rerere.ai.generation.assemblePortableTools
import me.rerere.ai.generation.createPortableManageSkillsTool
import me.rerere.ai.generation.defaultPortableInputTransformers
import me.rerere.ai.generation.withoutPortableRuntimeTools
import me.rerere.ai.generation.SKILL_MANAGEMENT_TOOL_NAME
import me.rerere.ai.provider.OnDeviceLlmProvider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.ai.ui.toTurnGroups
import me.rerere.ai.ui.truncate
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.tools.recoverInlineAskUserToolCall
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.InputTransformResult
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateRenderer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext
import me.rerere.rikkahub.data.ai.transformers.UnsupportedFileTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.androidPortableDocumentRuntime
import me.rerere.rikkahub.data.ai.transformers.androidPortableOcrRuntime
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
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.SkillExportImport
import me.rerere.rikkahub.utils.toLocalDate
import me.rerere.rikkahub.utils.toLocalTime
import me.rerere.workspace.WorkspaceShellStatus
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"
internal const val MEMORY_SEARCH_TOOL_NAME = "search_memory"

private val SMART_CONTEXT_PROTECTED_BODY_KEYS = setOf(
    "messages",
    "input",
    "contents",
    "system",
    "system_instruction",
    "systemInstruction",
    "tools",
    "max_tokens",
    "max_completion_tokens",
    "max_output_tokens",
)

/** Prevent advanced body overrides from silently bypassing the smart manager's hard contract. */
internal fun smartContextSafeCustomBodies(bodies: List<CustomBody>): List<CustomBody> =
    bodies.mapNotNull { body ->
        if (body.key in SMART_CONTEXT_PROTECTED_BODY_KEYS) return@mapNotNull null
        if (body.key == "generationConfig" || body.key == "generation_config") {
            val value = body.value as? JsonObject ?: return@mapNotNull body
            val safeValue = JsonObject(
                value.filterKeys { key ->
                    key !in setOf("maxOutputTokens", "max_output_tokens", "maxTokens", "max_tokens")
                }
            )
            if (safeValue.isEmpty()) null else body.copy(value = safeValue)
        } else {
            body
        }
    }

/**
 * Reserved key a tool may put in its (JSON object) result to hand the model one or more
 * images that providers can't carry inside a tool result. The value is a JSON array of
 * image URLs / data-URLs. [extractInjectedImageParts] strips the key from the tool result
 * (so the raw base64 never reaches the provider — which would 400) and re-delivers the
 * images as a synthetic USER message right after the tool results, where every provider
 * accepts them. Used by the assistant-overlay `look_at_screen` tool.
 */
internal const val TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY = PortableGenerationLoop.INJECTED_IMAGE_KEY

/**
 * Pull any [TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY] payloads out of [results], returning
 * the sanitized tool results (key removed) plus the image parts to inject as a follow-up
 * USER message. Non-injecting results pass through untouched.
 */
internal fun extractInjectedImageParts(
    results: List<UIMessagePart.ToolResult>,
): Pair<List<UIMessagePart.ToolResult>, List<UIMessagePart.Image>> {
    return me.rerere.ai.generation.extractInjectedImageParts(results)
}

internal fun shouldRegisterMemorySearchTool(assistant: Assistant): Boolean {
    return assistant.enableMemory && assistant.enableMemorySearchTool
}

internal data class PreparedGenerationTurn(
    val conversationMessages: List<UIMessage>,
    val providerMessages: List<UIMessage>,
    val params: TextGenerationParams,
    val transformedUsage: ContextUsageBreakdown?,
    val usedLorebookEntries: List<me.rerere.ai.ui.UsedLorebookEntry>,
    val usedModes: List<me.rerere.ai.ui.UsedMode>,
    val usedMemories: List<me.rerere.ai.ui.UsedMemory>,
) {
    fun attachContextSources(messages: List<UIMessage>): List<UIMessage> {
        val hasContextSources = usedLorebookEntries.isNotEmpty() ||
            usedModes.isNotEmpty() ||
            usedMemories.isNotEmpty()
        if (!hasContextSources) return messages
        return messages.mapIndexed { index, message ->
            if (index == messages.lastIndex && message.role == MessageRole.ASSISTANT) {
                message.copy(
                    usedLorebookEntries = usedLorebookEntries.ifEmpty { null },
                    usedModes = usedModes.ifEmpty { null },
                    usedMemories = usedMemories.ifEmpty { null },
                )
            } else {
                message
            }
        }
    }
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
    val usedMemories: List<me.rerere.ai.ui.UsedMemory> = emptyList(),
    val contextUsage: ContextUsageBreakdown? = null,
    val effectiveTools: List<Tool> = emptyList(),
    val messageBudgetTokens: Int? = null,
    /** Inclusive prompt budget before tool definitions are allocated out of message history. */
    val effectiveInputBudgetTokens: Int? = null,
)

internal data class SkillToolState(
    val activeSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val availableSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val blockedSkills: List<me.rerere.rikkahub.data.model.Skill>,
    val activeSkillIds: Set<Uuid>,
)

internal fun selectSmartTools(
    tools: List<Tool>,
    messages: List<UIMessage>,
    model: Model,
    inputBudgetTokens: Int,
): List<Tool> = PortableGenerationPrepare.selectSmartTools(
    tools = tools,
    messages = messages,
    model = model,
    inputBudgetTokens = inputBudgetTokens,
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
    // A skill may be enabled by the user but opt out of model-driven discovery.
    // Keep it active when selected, while preventing the model from activating it.
    val toggleableSkills = usableSkills.filterNot { it.disableModelInvocation }
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
    automaticInvocationEnabled: Boolean = true,
    onUpdateTurnScopedSkillIds: suspend (Set<Uuid>) -> Unit,
): Tool? {
    val skills = (state.availableSkills + state.activeSkills + state.blockedSkills).distinctBy { it.id }
    return createPortableManageSkillsTool(
        binding = PortableSkillToolBinding(
            skills = skills.map { it.toPortableSkill() },
            assistantId = skills.firstOrNull()?.let { skill ->
                if (skill.availableForAllAssistants) {
                    "all"
                } else {
                    skill.availableAssistantIds.firstOrNull()?.toString() ?: "all"
                }
            } ?: "all",
            assistantDefaultSkillIds = state.activeSkillIds.map { it.toString() }.toSet() -
                currentTurnScopedSkillIds.map { it.toString() }.toSet(),
            conversationSkillIds = emptySet(),
            turnScopedSkillIds = currentTurnScopedSkillIds.map { it.toString() }.toSet(),
            onUpdateTurnScopedSkillIds = { updated ->
                onUpdateTurnScopedSkillIds(updated.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet())
            },
        ),
        automaticInvocationEnabled = automaticInvocationEnabled,
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
    private val onDeviceLlm: me.rerere.common.runtime.OnDeviceLlmRuntime,
    private val templateRenderer: MessageTemplateRenderer,
    private val workspaceRepository: WorkspaceRepository,
    private val runtimeInfo: GenerationRuntimeInfo = AndroidGenerationRuntimeInfo(),
    private val chatEngine: PortableChatEngine = PortableChatEngine(),
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
        contextSummary: String? = null,
        contextSummaryUpToIndex: Int = -1,
        onContextUsage: suspend (ContextUsageBreakdown) -> Unit = {},
        contextUsageSourceKey: Int? = null,
        contextBudgetScale: Double = 1.0,
        workspaceCwd: String? = null,
    ): Flow<GenerationChunk> = channelFlow {
        // Older app-created skills predate package storage. Materialize their
        // canonical SKILL.md before a workspace starts so resource paths in the
        // prompt always point to a real package.
        settings.skills.forEach { skill ->
            runCatching { SkillExportImport.ensureManagedSkillPackage(context, skill) }
                .onFailure { Log.w(TAG, "Could not sync skill package ${skill.name}", it) }
        }
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = resolveProvider(provider)

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
        var lastPrepared: PreparedGenerationTurn? = null

        chatEngine.generate(
            PortableGenerationSession(
                model = model,
                initialMessages = messages,
                tools = tools,
                stream = assistant.streamOutput,
                maxSteps = maxSteps,
                streamText = { providerMessages, params ->
                    providerImpl.streamText(
                        providerSetting = provider,
                        messages = providerMessages,
                        params = params,
                    )
                },
                generateText = { providerMessages, params ->
                    providerImpl.generateText(
                        providerSetting = provider,
                        messages = providerMessages,
                        params = params,
                    )
                },
                rebuildTools = { stepIndex, _ ->
                    Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")
                    assemblePortableTools(
                        options = PortableToolAssemblyOptions(
                            model = model,
                            includeMemory = assistant.enableMemory,
                            includeMemorySearch = shouldRegisterMemorySearchTool(assistant),
                            includeSkills = true,
                            automaticSkillInvocation = assistant.enableAutomaticSkillInvocation &&
                                model.abilities.contains(ModelAbility.TOOL),
                        ),
                        runtimes = PortableToolRuntimes(
                            memory = if (assistant.enableMemory) {
                                memoryToolRuntime(
                                    assistant = assistant,
                                    activeConversationId = activeConversationId,
                                )
                            } else {
                                null
                            },
                            skills = PortableSkillToolBinding(
                                skills = settings.skills.map { it.toPortableSkill() },
                                assistantId = assistant.id.toString(),
                                assistantDefaultSkillIds = assistantDefaultSkillIds.map { it.toString() }.toSet(),
                                conversationSkillIds = conversationSkillIds.map { it.toString() }.toSet(),
                                turnScopedSkillIds = currentTurnScopedSkillIds.map { it.toString() }.toSet(),
                                onUpdateTurnScopedSkillIds = { updatedIds ->
                                    currentTurnScopedSkillIds = updatedIds
                                        .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
                                        .toSet()
                                        .intersect(allSkillIds)
                                },
                            ),
                            extraTools = tools.withoutPortableRuntimeTools(),
                        ),
                    )
                },
                prepareTurn = { _, conversationMessages, stepTools ->
                    val prepared = prepareGenerationTurn(
                        assistant = assistant,
                        settings = settings,
                        messages = conversationMessages,
                        onUpdateMessages = { updated ->
                            send(
                                GenerationChunk.Messages(
                                    updated.visualTransforms(
                                        transformers = outputTransformers,
                                        context = context,
                                        model = model,
                                        assistant = assistant,
                                        onlyLatest = true,
                                    )
                                )
                            )
                        },
                        transformers = inputTransformers,
                        model = model,
                        tools = stepTools,
                        memories = memories ?: emptyList(),
                        truncateIndex = truncateIndex,
                        conversationEnabledModeIds = conversationSkillIds,
                        turnScopedEnabledModeIds = currentTurnScopedSkillIds,
                        conversationEnabledLorebookIds = enabledLorebookIds,
                        activeConversationId = activeConversationId,
                        contextSummary = contextSummary,
                        contextSummaryUpToIndex = contextSummaryUpToIndex,
                        onContextUsage = onContextUsage,
                        contextUsageSourceKey = contextUsageSourceKey,
                        contextBudgetScale = contextBudgetScale,
                        providerImpl = providerImpl,
                        provider = provider,
                        workspaceCwd = workspaceCwd,
                    )
                    lastPrepared = prepared
                    aiLoggingManager.addLog(
                        AILogging.Generation(
                            params = prepared.params,
                            messages = prepared.conversationMessages,
                            providerSetting = provider,
                            stream = assistant.streamOutput,
                        )
                    )
                    PortableTurnRequest(
                        conversationMessages = prepared.conversationMessages,
                        providerMessages = prepared.providerMessages,
                        params = prepared.params,
                    )
                },
                visualTransform = { current ->
                    current.visualTransforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        onlyLatest = true,
                    )
                },
                onStreamChunk = { current, chunk, chunkModel ->
                    var next = current.handleMessageChunk(chunk = chunk, model = chunkModel)
                    chunk.usage?.let { usage ->
                        lastPrepared?.transformedUsage?.let { estimate ->
                            onContextUsage(
                                ContextTokenEstimator.reconcileProviderCount(
                                    breakdown = estimate,
                                    promptTokens = usage.promptTokens,
                                    model = model,
                                    confidence = if (provider is ProviderSetting.LiteRtLocal) {
                                        me.rerere.ai.context.ContextCountConfidence.EXACT
                                    } else {
                                        me.rerere.ai.context.ContextCountConfidence.PROVIDER_COUNTED
                                    },
                                )
                            )
                        }
                        next = next.mapIndexed { index, message ->
                            if (index == next.lastIndex) {
                                message.copy(usage = message.usage.merge(usage))
                            } else {
                                message
                            }
                        }
                    }
                    next
                },
                afterAssistantTurn = { current ->
                    var next = lastPrepared?.attachContextSources(current) ?: current
                    next = next.visualTransforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                    )
                    next = next.onGenerationFinish(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                    )
                    next = next.recoverInlineAskUserToolCall(json)
                    next.lastOrNull()?.usage?.let { usage ->
                        if (usage.promptTokens > 0 || usage.completionTokens > 0) {
                            try {
                                conversationRepo.addTokenUsage(
                                    inputTokens = usage.promptTokens.toLong(),
                                    outputTokens = usage.completionTokens.toLong(),
                                    cachedTokens = usage.cachedTokens.toLong(),
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to persist token usage", e)
                            }
                        }
                    }
                    next
                },
                afterToolResults = { current ->
                    current.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                    )
                },
                onMessages = { current, reason ->
                    if (reason != PortableGenerationUpdate.Streaming) {
                        messages = current
                    }
                    send(GenerationChunk.Messages(current))
                },
            )
        )

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
        activeConversationId: Uuid? = null,
        contextSummary: String? = null,
        contextSummaryUpToIndex: Int = -1,
        contextUsageSourceKey: Int? = null,
        contextBudgetScale: Double = 1.0,
        transformers: List<PortableInputTransformer> = emptyList(),
        transformerContext: PortableTransformerContext? = null,
    ): BuildMessagesResult {
        val recentMessagesForScan = messages.takeLast(10).map { it.toText() }
        val lorebooksForAssistant = settings.lorebooks.filter { lorebook ->
            lorebook.enabled && (conversationEnabledLorebookIds ?: assistant.enabledLorebookIds).contains(lorebook.id)
        }
        val hasRagEntries = lorebooksForAssistant.any { lorebook ->
            lorebook.entries.any { it.activationType == LorebookActivationType.RAG && it.enabled }
        }
        val queryEmbedding: List<Float>? = if (hasRagEntries) {
            try {
                val queryText = recentMessagesForScan.takeLast(3).joinToString("\n")
                if (queryText.isNotBlank()) embeddingService.embed(queryText) else null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to compute query embedding for RAG", e)
                null
            }
        } else null
        val recentChats = if (assistant.enableMemory && assistant.enableRecentChatsReference) {
            conversationRepo.getRecentConversations(assistant.id, limit = 4).map { conversation ->
                PortableRecentChat(
                    id = conversation.id.toString(),
                    title = conversation.title,
                    updatedAtEpochMs = conversation.updateAt.toEpochMilli(),
                    isToday = runtimeInfo.isToday(conversation.updateAt),
                )
            }
        } else {
            emptyList()
        }
        val portableMemories = memories.map { it.toPortableMemory() }
        val portableSkills = settings.skills.map { it.toPortableSkill(context) }
        val portableLorebooks = settings.lorebooks.map { lorebook ->
            val coverJson = lorebook.cover?.let { cover ->
                runCatching { json.encodeToString(me.rerere.rikkahub.data.model.Avatar.serializer(), cover) }.getOrNull()
            }
            lorebook.toPortableLorebook(coverJson)
        }
        val prepared = PortableGenerationPrepare.prepare(
            PortablePrepareRequest(
                messages = messages,
                model = model,
                tools = tools,
                assistant = assistant.toPortablePrepareAssistant(),
                skills = portableSkills,
                lorebooks = portableLorebooks,
                memories = portableMemories,
                conversationSkillIds = conversationEnabledModeIds.map { it.toString() }.toSet(),
                turnScopedSkillIds = turnScopedEnabledModeIds.map { it.toString() }.toSet(),
                conversationLorebookIds = conversationEnabledLorebookIds?.map { it.toString() }?.toSet(),
                contextSummary = contextSummary,
                contextSummaryUpToIndex = contextSummaryUpToIndex,
                truncateIndex = truncateIndex,
                learningModePrompt = settings.learningModePrompt.ifEmpty { DEFAULT_LEARNING_MODE_PROMPT },
                queryEmbedding = queryEmbedding,
                transformers = transformers,
                transformerContext = transformerContext,
                contextBudgetScale = contextBudgetScale,
                contextUsageSourceKey = contextUsageSourceKey,
                episodeGroup = runtimeInfo::episodicMemoryGroup,
                recentChats = recentChats,
                activeConversationId = activeConversationId?.toString(),
            ),
        )
        return BuildMessagesResult(
            messages = prepared.providerMessages,
            activatedLorebookEntries = prepared.usedLorebookEntries,
            usedModes = prepared.usedModes,
            usedMemories = prepared.usedMemories,
            effectiveTools = prepared.effectiveTools,
            messageBudgetTokens = prepared.messageBudgetTokens,
            effectiveInputBudgetTokens = prepared.effectiveInputBudgetTokens,
            contextUsage = prepared.contextUsage,
        )
    }

    private suspend fun prepareGenerationTurn(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        truncateIndex: Int,
        conversationEnabledModeIds: Set<Uuid> = emptySet(),
        turnScopedEnabledModeIds: Set<Uuid> = emptySet(),
        conversationEnabledLorebookIds: Set<Uuid>? = null,
        activeConversationId: Uuid? = null,
        contextSummary: String? = null,
        contextSummaryUpToIndex: Int = -1,
        onContextUsage: suspend (ContextUsageBreakdown) -> Unit = {},
        contextUsageSourceKey: Int? = null,
        contextBudgetScale: Double = 1.0,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        workspaceCwd: String? = null,
    ): PreparedGenerationTurn {
        var uiMessages = messages
        val now = Instant.now()
        val workspaceId = assistant.workspaceId?.toString()
        val workspace = workspaceId?.let { workspaceRepository.getById(it) }
        val toolCapable = model.abilities.contains(ModelAbility.TOOL)
        val workspaceReminder = workspace?.let { entity ->
            val ready = entity.shellStatus == WorkspaceShellStatus.READY.name
            PortableWorkspaceReminder(
                name = entity.name,
                ready = ready,
                toolCapable = toolCapable,
                cwd = workspaceCwd,
                unavailableReason = when {
                    !toolCapable ->
                        "The selected model is not marked as tool-capable, so workspace_shell, workspace_read_file, workspace_write_file, and workspace_edit_file are not available in this chat."
                    !ready ->
                        "The workspace rootfs is not ready. The user must install or repair the rootfs before shell and file tools can run."
                    else -> null
                },
            )
        }
        val transformerCtx = PortableTransformerContext(
            model = model,
            workspaceEnabled = assistant.workspaceId != null,
            documentRuntime = androidPortableDocumentRuntime(context),
            ocrRuntime = androidPortableOcrRuntime(chatAttachmentRepository),
            messageTemplate = assistant.messageTemplate,
            templateRuntime = PortableTemplateRuntime { _, templateContext ->
                templateRenderer.render(assistant.id.toString(), templateContext)
            },
            templateTime = now.toLocalTime(),
            templateDate = now.toLocalDate(),
            workspaceReminder = workspaceReminder,
            onProgressAnnotationsChanged = { annotations ->
                val updatedMessages = uiMessages.upsertOcrPlaceholder(annotations)
                if (updatedMessages != uiMessages) {
                    uiMessages = updatedMessages
                    onUpdateMessages(uiMessages)
                }
            },
        )
        val androidPlaceholder = PortableInputTransformer { ctx, msgs ->
            PlaceholderTransformer.transform(
                TransformerContext(context, ctx.model, assistant),
                msgs,
            )
        }
        val portableTransformers = listOf(androidPlaceholder) +
            defaultPortableInputTransformers().filter { it !== PortablePlaceholderTransformer }
        val extraAndroid = transformers.filterNot {
            it === PlaceholderTransformer ||
                it === DocumentAsPromptTransformer ||
                it === OcrTransformer ||
                it === UnsupportedFileTransformer ||
                it is TemplateTransformer ||
                it is WorkspaceReminderTransformer
        }
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
            activeConversationId = activeConversationId,
            contextSummary = contextSummary,
            contextSummaryUpToIndex = contextSummaryUpToIndex,
            contextUsageSourceKey = contextUsageSourceKey,
            contextBudgetScale = contextBudgetScale,
            transformers = portableTransformers,
            transformerContext = transformerCtx,
        )
        val extraCtx = TransformerContext(context, model, assistant)
        var extraTransformed = buildResult.messages
        extraAndroid.forEach { transformer ->
            extraTransformed = transformer.transform(extraCtx, extraTransformed)
        }
        val transformedInput = InputTransformResult(
            messages = extraTransformed,
            annotations = transformerCtx.annotations,
        )
        val transformedMessages = transformedInput.messages.limitImagesForModel(model)
        var internalMessages = buildResult.messageBudgetTokens?.let { budget ->
            smartFitContext(
                messages = transformedMessages,
                model = model,
                messageBudgetTokens = budget,
            )
        } ?: transformedMessages
        var transformedUsage = buildResult.contextUsage?.let { usage ->
            val before = ContextTokenEstimator.messagesTokens(buildResult.messages, model)
            val after = ContextTokenEstimator.messagesTokens(internalMessages, model)
            val delta = after - before
            usage.copy(
                conversationTokens = (usage.conversationTokens + delta).coerceAtLeast(0),
                usedTokens = (usage.usedTokens + delta).coerceAtLeast(0),
                imageCount = internalMessages.sumOf { message ->
                    message.parts.count { it is UIMessagePart.Image }
                },
            )
        }
        transformedUsage?.let { onContextUsage(it) }
        val usedLorebookEntries = buildResult.activatedLorebookEntries
        val usedModes = buildResult.usedModes
        val usedMemories = buildResult.usedMemories

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
        var params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            topK = null,
            maxTokens = if (buildResult.messageBudgetTokens != null) {
                smartOutputTokenBudget(model, assistant.maxTokens) ?: assistant.maxTokens
            } else {
                assistant.maxTokens
            },
            tools = buildResult.effectiveTools,
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
            }.let { bodies ->
                if (buildResult.messageBudgetTokens != null) {
                    smartContextSafeCustomBodies(bodies)
                } else {
                    bodies
                }
            },
        )
        if ((model.contextCapacityTokens ?: 0) > 0) {
            val inputBudget = buildResult.effectiveInputBudgetTokens
            if (inputBudget != null || buildResult.messageBudgetTokens == null) {
                var lastCountedTokens: Int? = null
                for (attempt in 0 until 8) {
                    val countedTokens = runCatching {
                        providerImpl.countInputTokens(provider, internalMessages, params)
                    }.getOrNull()?.takeIf { it > 0 } ?: break
                    lastCountedTokens = countedTokens
                    transformedUsage?.let { estimate ->
                        val counted = ContextTokenEstimator.reconcileProviderCount(
                            breakdown = estimate,
                            promptTokens = countedTokens,
                            model = model,
                        )
                        transformedUsage = counted
                        onContextUsage(counted)
                    }
                    if (inputBudget == null || countedTokens <= inputBudget) break

                    val oldMessageTokens = ContextTokenEstimator.messagesTokens(internalMessages, model)
                    val reduction = (inputBudget.toDouble() / countedTokens * 0.94).coerceIn(0.1, 0.94)
                    val reducedBudget = (oldMessageTokens * reduction).toInt().coerceAtLeast(1)
                    val reducedMessages = smartFitContext(
                        messages = internalMessages,
                        model = model,
                        messageBudgetTokens = reducedBudget,
                    )
                    val newMessageTokens = ContextTokenEstimator.messagesTokens(reducedMessages, model)
                    if (newMessageTokens < oldMessageTokens) {
                        internalMessages = reducedMessages
                    } else if (params.tools.isNotEmpty()) {
                        val reducedTools = selectSmartTools(
                            tools = params.tools,
                            messages = internalMessages,
                            model = model,
                            inputBudgetTokens = (inputBudget * 0.55).toInt().coerceAtLeast(1),
                        ).let { selected ->
                            if (selected.size < params.tools.size) selected else params.tools.dropLast(1)
                        }
                        params = params.copy(tools = reducedTools)
                    } else if (params.builtInTools.isNotEmpty()) {
                        params = params.copy(builtInTools = emptySet())
                    } else {
                        internalMessages = listOfNotNull(
                            listOfNotNull(internalMessages.lastOrNull { it.role == MessageRole.USER })
                                .compactToTokenBudget(model, inputBudget)
                                .firstOrNull()
                        )
                    }
                    transformedUsage = transformedUsage?.let { usage ->
                        val delta = newMessageTokens - oldMessageTokens
                        usage.copy(
                            conversationTokens = (usage.conversationTokens + delta).coerceAtLeast(0),
                            usedTokens = (usage.usedTokens + delta).coerceAtLeast(0),
                            confidence = me.rerere.ai.context.ContextCountConfidence.ESTIMATED,
                        )
                    }
                }
                if (inputBudget != null && lastCountedTokens != null && lastCountedTokens > inputBudget) {
                    val finalCount = runCatching {
                        providerImpl.countInputTokens(provider, internalMessages, params)
                    }.getOrNull()?.takeIf { it > 0 }
                    if (finalCount == null || finalCount > inputBudget) {
                        throw IllegalStateException(
                            "Smart context management could not create a request within the model's context window"
                        )
                    }
                    transformedUsage?.let { estimate ->
                        val counted = ContextTokenEstimator.reconcileProviderCount(
                            breakdown = estimate,
                            promptTokens = finalCount,
                            model = model,
                        )
                        transformedUsage = counted
                        onContextUsage(counted)
                    }
                }
            }
        }
        return PreparedGenerationTurn(
            conversationMessages = messages,
            providerMessages = internalMessages,
            params = params,
            transformedUsage = transformedUsage,
            usedLorebookEntries = usedLorebookEntries,
            usedModes = usedModes,
            usedMemories = usedMemories,
        )
    }

    private fun memoryToolRuntime(
        assistant: Assistant,
        activeConversationId: Uuid?,
    ): PortableMemoryToolRuntime = PortableMemoryToolRuntime(
        onCreate = { content ->
            json.encodeToJsonElement(
                AssistantMemory.serializer(),
                memoryRepo.addMemory(assistant.id.toString(), content),
            )
        },
        onUpdate = { id, content ->
            val before = memoryRepo.getMemoryById(id)
            val updated = memoryRepo.updateContent(id, content)
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
        },
        onDelete = { id ->
            val before = memoryRepo.getMemoryById(id)
            memoryRepo.deleteMemory(id)
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
        },
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T : ProviderSetting> resolveProvider(setting: T): me.rerere.ai.provider.Provider<T> {
        if (setting is ProviderSetting.LiteRtLocal) {
            return OnDeviceLlmProvider(onDeviceLlm) as me.rerere.ai.provider.Provider<T>
        }
        return providerManager.getProviderByType(setting)
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

        val providerHandler = resolveProvider(provider)

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

}

internal fun formatToolExecutionError(throwable: Throwable): String {
    return me.rerere.ai.generation.formatToolExecutionError(throwable)
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
