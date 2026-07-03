package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryProvenanceEntity

/**
 * Serializable snapshot of the graph memory store for the §10 user-facing "Export memory…" flow.
 *
 * This is a *separate* format from the whole-DB backup (which rides the Room database through
 * `WebdavSync`/`BackupArchiveFormat` automatically, §12.2). The Room entities are not themselves
 * `@Serializable`, so these DTOs mirror their columns; the per-node embedding blob is intentionally
 * dropped (large, model-specific, and re-derivable) — only `embeddingModelId` is retained.
 */
@Serializable
data class MemoryExport(
    val format: String = "lastchat-memory-export",
    val version: String = "1",
    val scope: String,
    val exportedAt: Long,
    val nodes: List<MemoryNodeExport>,
    val edges: List<MemoryEdgeExport>,
    val provenance: List<MemoryProvenanceExport>,
)

@Serializable
data class MemoryNodeExport(
    val id: String,
    val type: Int,
    val scope: Int,
    val ownerAssistantId: String? = null,
    val content: String,
    val displayLabel: String? = null,
    val importance: Int,
    val confidence: Float,
    val sensitivity: Int,
    val status: Int,
    val pinned: Boolean,
    val reality: Int,
    val eventStart: Long? = null,
    val eventEnd: Long? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val recordedAt: Long,
    val lastConfirmedAt: Long,
    val lastAccessedAt: Long,
    val timesReinforced: Int,
    val timesRetrieved: Int,
    val source: Int,
    val embeddingModelId: String? = null,
    val extra: String = "{}",
    val adjudicationPending: Boolean = false,
)

@Serializable
data class MemoryEdgeExport(
    val id: String,
    val fromId: String,
    val toId: String,
    val type: Int,
    val weight: Float,
    val createdAt: Long,
    val extra: String = "{}",
)

@Serializable
data class MemoryProvenanceExport(
    val id: String,
    val nodeId: String,
    val conversationId: String? = null,
    val messageIds: String = "[]",
    val excerpt: String = "",
    val rationale: String = "",
    val createdAt: Long,
)

fun MemoryNodeEntity.toExport(): MemoryNodeExport = MemoryNodeExport(
    id = id, type = type, scope = scope, ownerAssistantId = ownerAssistantId, content = content,
    displayLabel = displayLabel, importance = importance, confidence = confidence, sensitivity = sensitivity,
    status = status, pinned = pinned, reality = reality, eventStart = eventStart, eventEnd = eventEnd,
    validFrom = validFrom, validUntil = validUntil, recordedAt = recordedAt, lastConfirmedAt = lastConfirmedAt,
    lastAccessedAt = lastAccessedAt, timesReinforced = timesReinforced, timesRetrieved = timesRetrieved,
    source = source, embeddingModelId = embeddingModelId, extra = extra, adjudicationPending = adjudicationPending,
)

fun MemoryEdgeEntity.toExport(): MemoryEdgeExport = MemoryEdgeExport(
    id = id, fromId = fromId, toId = toId, type = type, weight = weight, createdAt = createdAt, extra = extra,
)

fun MemoryProvenanceEntity.toExport(): MemoryProvenanceExport = MemoryProvenanceExport(
    id = id, nodeId = nodeId, conversationId = conversationId, messageIds = messageIds,
    excerpt = excerpt, rationale = rationale, createdAt = createdAt,
)
