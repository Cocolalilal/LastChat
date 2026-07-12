package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MemorySourceKind {
    @SerialName("entry") ENTRY,
    @SerialName("user_profile") USER_PROFILE,
    @SerialName("character_memory") CHARACTER_MEMORY,
    @SerialName("continuity_digest") CONTINUITY_DIGEST,
    @SerialName("graph_node") GRAPH_NODE,
    @SerialName("graph_relation") GRAPH_RELATION,
    @SerialName("raw_chat") RAW_CHAT,
}

data class HybridMemorySearchResult(
    val sourceKind: MemorySourceKind,
    val sourceId: String,
    val text: String,
    val recordedAt: Long,
    val conversationId: String? = null,
    val messageId: String? = null,
    val speaker: String? = null,
    val frame: String? = null,
    val score: Float = 0f,
    val embeddingBlob: ByteArray? = null,
    val embeddingModelId: String? = null,
)

data class DocumentUpdateResult(
    val applied: Boolean,
    val revision: Long,
    val codePointCount: Int,
    val error: String? = null,
)

@Serializable
enum class MemoryConversionDirection { ENTRY_TO_DOCUMENT, DOCUMENT_TO_ENTRY }

@Serializable
data class MemoryConversionEntryProposal(
    val content: String,
    val existingEntryId: Int? = null,
    val reason: String = "Converted memory",
)

@Serializable
data class MemoryConversionPreview(
    val id: String,
    val assistantId: String,
    val direction: MemoryConversionDirection,
    val sourceRevision: Long,
    val previousWatermark: Long,
    val expectedUserProfileRevision: Long? = null,
    val expectedCharacterMemoryRevision: Long? = null,
    val userProfileReplacement: String? = null,
    val characterMemoryReplacement: String? = null,
    val entryProposals: List<MemoryConversionEntryProposal> = emptyList(),
    val summary: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class MemoryConversionInput(
    val assistantId: String,
    val direction: MemoryConversionDirection,
    val sourceRevision: Long,
    val previousWatermark: Long,
    val userProfileRevision: Long,
    val characterMemoryRevision: Long,
    val userProfile: String,
    val characterMemory: String,
    val entries: List<AssistantMemory>,
    val changedEvidence: List<String>,
)

data class MemoryConversionApplyResult(
    val applied: Boolean,
    val addedEntries: Int = 0,
    val updatedEntries: Int = 0,
    val error: String? = null,
)

fun String.memoryCodePointCount(): Int = codePointCount(0, length)
