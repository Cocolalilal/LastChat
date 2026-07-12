package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UsedMemory
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemorySystemType
import me.rerere.rikkahub.data.model.effectiveRagMemoryEnabled
import me.rerere.rikkahub.data.model.effectiveRecentContinuityEnabled
import me.rerere.rikkahub.data.repository.HybridMemoryRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import kotlin.uuid.Uuid

data class CoordinatedMemoryContext(
    val memories: List<AssistantMemory>,
    val promptText: String,
    val usedSources: List<UsedMemory>,
)

internal fun List<AssistantMemory>.excludingConversationSources(
    excludedConversationId: Uuid?,
): List<AssistantMemory> {
    if (excludedConversationId == null) return this
    val excludedId = excludedConversationId.toString()
    return filterNot { it.conversationId == excludedId }
}

class MemoryContextCoordinator(
    private val memoryRepository: MemoryRepository?,
    private val hybridMemoryRepository: HybridMemoryRepository,
) {
    private val snapshotLock = Any()
    private val conversationSnapshots = mutableMapOf<Uuid, CoordinatedMemoryContext>()

    suspend fun contextFor(
        assistant: Assistant,
        query: String,
        conversationId: Uuid? = null,
        excludedConversationId: Uuid? = null,
        stableConversationSnapshot: Boolean = false,
        allowMemory: Boolean = true,
    ): CoordinatedMemoryContext {
        if (!allowMemory || !assistant.enableMemory) return CoordinatedMemoryContext(emptyList(), "", emptyList())
        if (stableConversationSnapshot && conversationId != null) {
            synchronized(snapshotLock) { conversationSnapshots[conversationId] }?.let { return it }
        }
        val memories = if (assistant.memorySystem == MemorySystemType.DOCUMENT_BASED) {
            buildList {
                hybridMemoryRepository.getDocuments(assistant).filter { it.content.isNotBlank() }.forEach { document ->
                    add(AssistantMemory(
                        id = document.id.hashCode(),
                        content = document.content,
                        timestamp = document.updatedAt,
                        sourceId = document.id,
                        sourceKind = document.kind,
                        sourceTitle = document.kind.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                    ))
                }
                addAll(digestMemories(assistant, query))
            }
        } else {
            val memoryRepository = checkNotNull(memoryRepository) { "Entry-based memory repository is unavailable" }
            val entries = if (assistant.effectiveRagMemoryEnabled() && query.isNotBlank()) {
                memoryRepository.retrieveRelevantMemories(
                    assistantId = assistant.id.toString(),
                    query = query,
                    limit = if (assistant.ragLimit > 50) 9999 else assistant.ragLimit,
                    similarityThreshold = assistant.ragSimilarityThreshold,
                    includeCore = assistant.ragIncludeCore,
                    includeEpisodes = false,
                )
            } else {
                memoryRepository.getMemoriesOfAssistant(assistant.id.toString()).take(50)
            }
            entries + if (assistant.effectiveRecentContinuityEnabled()) digestMemories(assistant, query) else emptyList()
        }
            .excludingConversationSources(excludedConversationId)
            .distinctBy { it.sourceId ?: "entry:${it.id}" }
        val sources = memories.mapIndexed { index, memory ->
            UsedMemory(
                memoryId = memory.id,
                memoryContent = memory.content.take(80),
                memoryType = memory.type,
                priority = memories.size - index,
                activationReason = if (memory.sourceKind == "CONTINUITY_DIGEST") "Recent continuity" else "Memory context",
                sourceId = memory.sourceId ?: memory.id.toString(),
                sourceKind = memory.sourceKind ?: "ENTRY",
                title = memory.sourceTitle,
                conversationId = memory.conversationId,
                messageId = memory.messageId,
            )
        }
        val result = CoordinatedMemoryContext(
            memories = memories,
            promptText = memories.joinToString("\n") { "- ${it.content}" },
            usedSources = sources,
        )
        if (stableConversationSnapshot && conversationId != null) {
            synchronized(snapshotLock) { conversationSnapshots.putIfAbsent(conversationId, result) }
            return synchronized(snapshotLock) { conversationSnapshots.getValue(conversationId) }
        }
        return result
    }

    fun clearConversationSnapshot(conversationId: Uuid) {
        synchronized(snapshotLock) { conversationSnapshots.remove(conversationId) }
    }

    private suspend fun digestMemories(assistant: Assistant, query: String) = hybridMemoryRepository.hotDigests(assistant.id.toString(), query).map { digest ->
        AssistantMemory(
            id = digest.id.hashCode(),
            content = digest.summary,
            type = 1,
            timestamp = digest.recordedAt,
            significance = digest.importance,
            sourceId = digest.id,
            sourceKind = "CONTINUITY_DIGEST",
            sourceTitle = "Recent continuity",
            conversationId = digest.conversationId,
        )
    }
}
