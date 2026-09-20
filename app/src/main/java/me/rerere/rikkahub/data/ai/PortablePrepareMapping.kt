package me.rerere.rikkahub.data.ai

import android.content.Context
import me.rerere.ai.generation.PortableContextPriority
import me.rerere.ai.generation.PortableMemoryRecord
import me.rerere.ai.generation.PortablePrepareAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.ContextPriority
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.prompt.LorebookActivationKind
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortablePromptAttachment
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition
import java.io.File

fun Assistant.toPortablePrepareAssistant(): PortablePrepareAssistant = PortablePrepareAssistant(
    id = id.toString(),
    systemPrompt = systemPrompt,
    learningMode = learningMode,
    enableMemory = enableMemory,
    useRagMemoryRetrieval = useRagMemoryRetrieval,
    smartContextManagement = smartContextManagement,
    maxTokens = maxTokens,
    maxTokenUsage = maxTokenUsage,
    maxHistoryMessages = maxHistoryMessages,
    maxSearchResultsRetained = maxSearchResultsRetained,
    enableTimeAwareness = enableTimeAwareness,
    temperature = temperature,
    topP = topP,
    thinkingBudget = thinkingBudget,
    enabledSkillIds = enabledSkillIds.map { it.toString() }.toSet(),
    enabledLorebookIds = enabledLorebookIds.map { it.toString() }.toSet(),
    customHeaders = customHeaders,
    customBodies = customBodies,
    contextPriority = contextPriority.toPortable(),
    workspaceEnabled = workspaceId != null,
)

fun AssistantMemory.toPortableMemory(): PortableMemoryRecord = PortableMemoryRecord(
    id = id,
    content = content,
    type = type,
    timestamp = timestamp,
)

fun ContextPriority.toPortable(): PortableContextPriority = when (this) {
    ContextPriority.CHAT_HISTORY -> PortableContextPriority.CHAT_HISTORY
    ContextPriority.BALANCED -> PortableContextPriority.BALANCED
    ContextPriority.MEMORIES -> PortableContextPriority.MEMORIES
}

fun InjectionPosition.toPortable(): PromptInjectionPosition = when (this) {
    InjectionPosition.BEFORE_SYSTEM -> PromptInjectionPosition.BEFORE_SYSTEM
    InjectionPosition.AFTER_SYSTEM -> PromptInjectionPosition.AFTER_SYSTEM
    InjectionPosition.TOP_OF_CHAT -> PromptInjectionPosition.TOP_OF_CHAT
    InjectionPosition.BEFORE_LATEST -> PromptInjectionPosition.BEFORE_LATEST
    InjectionPosition.AT_DEPTH -> PromptInjectionPosition.AT_DEPTH
}

fun LorebookActivationType.toPortable(): LorebookActivationKind = when (this) {
    LorebookActivationType.ALWAYS -> LorebookActivationKind.ALWAYS
    LorebookActivationType.KEYWORDS -> LorebookActivationKind.KEYWORDS
    LorebookActivationType.RAG -> LorebookActivationKind.RAG
}

fun Skill.toPortableSkill(
    context: Context? = null,
): PortableSkill {
    val resources = context?.let { listSkillResources(it, this) }.orEmpty()
    return PortableSkill(
        id = id.toString(),
        name = name,
        description = description,
        instructions = instructions,
        enabled = enabled,
        alwaysEnabled = alwaysEnabled,
        availableForAllAssistants = availableForAllAssistants,
        availableAssistantIds = availableAssistantIds.map { it.toString() }.toSet(),
        injectionPosition = injectionPosition.toPortable(),
        depth = depth,
        disableModelInvocation = disableModelInvocation,
        icon = icon,
        compatibility = compatibility,
        workspaceDirectory = workspaceDirectory(),
        bundledResources = resources,
        attachments = attachments.map { attachment ->
            PortablePromptAttachment(
                type = attachment.type.toPortableType(),
                url = attachment.url,
                fileName = attachment.fileName,
                mime = attachment.mime,
            )
        },
    )
}

fun Lorebook.toPortableLorebook(coverJson: String? = null): PortableLorebook = PortableLorebook(
    id = id.toString(),
    name = name,
    description = description,
    enabled = enabled,
    coverJson = coverJson,
    entries = entries.map { it.toPortableEntry() },
)

fun LorebookEntry.toPortableEntry(): PortableLorebookEntry = PortableLorebookEntry(
    id = id.toString(),
    name = name,
    prompt = prompt,
    enabled = enabled,
    injectionPosition = injectionPosition.toPortable(),
    depth = depth,
    activationType = activationType.toPortable(),
    keywords = keywords,
    caseSensitive = caseSensitive,
    useRegex = useRegex,
    scanDepth = scanDepth,
    embedding = embedding,
    attachments = attachments.map { attachment ->
        PortablePromptAttachment(
            type = attachment.type.toPortableType(),
            url = attachment.url,
            fileName = attachment.fileName,
            mime = attachment.mime,
        )
    },
)

private fun ModeAttachmentType.toPortableType(): String = when (this) {
    ModeAttachmentType.IMAGE -> "image"
    ModeAttachmentType.VIDEO -> "video"
    ModeAttachmentType.AUDIO -> "audio"
    ModeAttachmentType.DOCUMENT -> "document"
}

private fun listSkillResources(context: Context, skill: Skill): List<String> {
    val skillsRoot = File(context.filesDir, "skills")
    return File(skillsRoot, skill.workspaceDirectory().removePrefix("/skills/"))
        .takeIf { it.isDirectory }
        ?.walkTopDown()
        ?.filter { it.isFile && it.name != "SKILL.md" }
        ?.map { "/skills/${it.relativeTo(skillsRoot).invariantSeparatorsPath}" }
        ?.take(64)
        ?.toList()
        .orEmpty()
}
