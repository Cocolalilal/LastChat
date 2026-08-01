package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation

/** Identifies every input that can materially change the context assembled for a request. */
internal fun contextUsageSourceKey(
    conversation: Conversation,
    assistant: Assistant,
    model: Model,
    settings: Settings,
): Int = listOf(
    conversation.currentMessages,
    conversation.truncateIndex,
    conversation.contextSummary,
    conversation.contextSummaryUpToIndex,
    conversation.enabledModeIds,
    conversation.enabledLorebookIds,
    assistant.systemPrompt,
    assistant.messageTemplate,
    assistant.smartContextManagement,
    assistant.maxTokens,
    assistant.maxTokenUsage,
    assistant.contextPriority,
    assistant.learningMode,
    assistant.enableTimeAwareness,
    assistant.enableMemory,
    assistant.useRagMemoryRetrieval,
    assistant.ragLimit,
    assistant.ragSimilarityThreshold,
    assistant.ragIncludeCore,
    assistant.ragIncludeEpisodes,
    assistant.maxHistoryMessages,
    assistant.maxSearchResultsRetained,
    assistant.archiveImagesAfterMessageAge,
    assistant.enabledSkillIds,
    assistant.enabledLorebookIds,
    assistant.localTools,
    assistant.mcpServers,
    settings.learningModePrompt,
    settings.skills,
    settings.lorebooks,
    settings.mcpServers,
    model.id,
    model.modelId,
    model.contextWindowTokens,
    model.maxImagesInContext,
    model.inputModalities,
    model.abilities,
    model.tools,
    model.providerOverwrite,
).hashCode()
