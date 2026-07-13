package me.rerere.ai.memory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stable identifiers used in assistant JSON and by the engine registry. */
object BuiltInMemoryEngines {
    const val OFF = "off"
    const val SIMPLE = "simple"
    const val GRAPH = "graph_mem0"
}

@Serializable
enum class MemoryScopeKind {
    @SerialName("assistant") ASSISTANT,
    @SerialName("session") SESSION,
}

@Serializable
data class MemoryScope(
    val assistantId: String,
    val kind: MemoryScopeKind = MemoryScopeKind.ASSISTANT,
    val conversationId: String? = null,
) {
    val stableKey: String
        get() = when (kind) {
            MemoryScopeKind.ASSISTANT -> "assistant:$assistantId"
            MemoryScopeKind.SESSION -> "session:$assistantId:${requireNotNull(conversationId)}"
        }
}

@Serializable
enum class MemorySpeaker {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
}

@Serializable
enum class MemoryOrigin {
    CHAT,
    SESSION,
    IMPORTED,
    MANUAL,
}

@Serializable
enum class MemoryObjectKind {
    MEMORY,
    ENTITY,
}

@Serializable
data class MemoryInputMessage(
    val role: MemorySpeaker,
    val content: String,
    val messageId: String? = null,
    val observedAtEpochMillis: Long,
)

@Serializable
data class ExtractedMemory(
    val id: String,
    val text: String,
    @SerialName("attributed_to")
    val attributedTo: MemorySpeaker? = null,
    @SerialName("linked_memory_ids")
    val linkedMemoryIds: List<String> = emptyList(),
)

@Serializable
data class Mem0ExtractionEnvelope(
    @SerialName("memory")
    val memories: List<ExtractedMemory> = emptyList(),
)

data class MemorySearchCandidate(
    val id: String,
    val semanticScore: Float,
)

data class MemoryScoreDetails(
    val semanticScore: Float,
    val bm25Score: Float,
    val entityBoost: Float,
    val rawScore: Float,
    val maxPossibleScore: Float,
    val finalScore: Float,
    val threshold: Float,
)

data class RankedMemory(
    val id: String,
    val score: Float,
    val details: MemoryScoreDetails,
)

data class ExtractedEntity(
    val type: String,
    val text: String,
)

data class MemoryEngineCapabilities(
    val graph: Boolean = false,
    val entities: Boolean = false,
    val automaticRecall: Boolean = false,
    val manualCrud: Boolean = true,
    val transfer: Boolean = true,
)
