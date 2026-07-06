package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.db.entity.MemEntityKind
import me.rerere.rikkahub.data.db.entity.MemFactKind
import me.rerere.rikkahub.data.db.entity.MemGoalState
import me.rerere.rikkahub.data.db.entity.MemReality
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryEpisodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryFactEntity
import me.rerere.rikkahub.data.db.entity.MemoryFrameEntity
import me.rerere.rikkahub.data.db.entity.MemoryGoalEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.utils.JsonInstant

/**
 * Kotlin model layer for the Memory v2 graph store (plan §4.12).
 *
 * Read-side domain representations decoupled from the Room `@Entity` rows, plus the closed
 * extraction op vocabulary ([MemoryOp], §6.3). Int codes live alongside the entities in
 * `me.rerere.rikkahub.data.db.entity.MemoryGraphCodes`. Per repo rules, no `!!` on JSON.
 *
 * Write-side mapping (domain → entity, embedding/extra handling) is the applier's concern (P1b) and
 * intentionally not modelled here; these mappers are read-only (`toModel`).
 */

// ---------------------------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------------------------

/** A graph node — a *thing* (see [MemEntityKind]). Aliases are loaded separately (§4.2). */
data class MemoryEntityNode(
    val id: String,
    val scope: Int,
    val ownerAssistantId: String?,
    val name: String,
    val kind: Int,
    val reality: Int,
    val summary: String?,
    val pinned: Boolean,
    val status: Int,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val timesRetrieved: Int,
    val aliases: List<String> = emptyList(),
)

/** A relationship edge carrying belief metadata (§4.3). Exactly one of [objectId]/[objectValue]. */
data class MemoryFact(
    val id: String,
    val subjectId: String,
    val predicate: String,
    val objectId: String?,
    val objectValue: String?,
    val statement: String,
    val kind: Int,
    val scope: Int,
    val ownerAssistantId: String?,
    val reality: Int,
    val frameId: String?,
    val importance: Int,
    val confidence: Double,
    val sensitivity: Int,
    val category: String?,
    val status: Int,
    val pinned: Boolean,
    val validFrom: Long?,
    val validUntil: Long?,
    val recordedAt: Long,
    val expiredAt: Long?,
    val lastConfirmedAt: Long,
    val lastAccessedAt: Long,
    val timesReinforced: Int,
    val timesRetrieved: Int,
    val stability: Double,
    val source: Int,
)

/** A scene-level event, the only prose-carrying node (§4.5). */
data class MemoryEpisode(
    val id: String,
    val ownerAssistantId: String,
    val title: String,
    val summary: String,
    val frameId: String?,
    val reality: Int,
    val eventStart: Long,
    val eventEnd: Long?,
    val importance: Int,
    val status: Int,
    val pinned: Boolean,
    val stability: Double,
    val isGist: Boolean,
    val conversationId: String?,
    val recordedAt: Long,
    val lastAccessedAt: Long,
    val timesRetrieved: Int,
)

/** A mode/storyline frame per character (§4.7). */
data class MemoryFrame(
    val id: String,
    val ownerAssistantId: String,
    val label: String,
    val descriptor: String,
    val source: Int,
    val createdAt: Long,
    val status: Int,
)

/** Curiosity state, deliberately outside the graph (§4.10, §9). */
data class MemoryGoal(
    val id: String,
    val ownerAssistantId: String,
    val question: String,
    val valueNote: String,
    val entityIds: List<String>,
    val state: Int,
    val webDraft: String?,
    val askedAt: Long?,
    val createdAt: Long,
    val resolvedAt: Long?,
)

// ---------------------------------------------------------------------------------------------
// Entity → model mappers
// ---------------------------------------------------------------------------------------------

fun MemoryNodeEntity.toModel(aliases: List<String> = emptyList()): MemoryEntityNode = MemoryEntityNode(
    id = id,
    scope = scope,
    ownerAssistantId = ownerAssistantId,
    name = name,
    kind = kind,
    reality = reality,
    summary = summary,
    pinned = pinned,
    status = status,
    createdAt = createdAt,
    lastAccessedAt = lastAccessedAt,
    timesRetrieved = timesRetrieved,
    aliases = aliases,
)

fun MemoryFactEntity.toModel(): MemoryFact = MemoryFact(
    id = id,
    subjectId = subjectId,
    predicate = predicate,
    objectId = objectId,
    objectValue = objectValue,
    statement = statement,
    kind = kind,
    scope = scope,
    ownerAssistantId = ownerAssistantId,
    reality = reality,
    frameId = frameId,
    importance = importance,
    confidence = confidence,
    sensitivity = sensitivity,
    category = category,
    status = status,
    pinned = pinned,
    validFrom = validFrom,
    validUntil = validUntil,
    recordedAt = recordedAt,
    expiredAt = expiredAt,
    lastConfirmedAt = lastConfirmedAt,
    lastAccessedAt = lastAccessedAt,
    timesReinforced = timesReinforced,
    timesRetrieved = timesRetrieved,
    stability = stability,
    source = source,
)

fun MemoryEpisodeEntity.toModel(): MemoryEpisode = MemoryEpisode(
    id = id,
    ownerAssistantId = ownerAssistantId,
    title = title,
    summary = summary,
    frameId = frameId,
    reality = reality,
    eventStart = eventStart,
    eventEnd = eventEnd,
    importance = importance,
    status = status,
    pinned = pinned,
    stability = stability,
    isGist = isGist,
    conversationId = conversationId,
    recordedAt = recordedAt,
    lastAccessedAt = lastAccessedAt,
    timesRetrieved = timesRetrieved,
)

fun MemoryFrameEntity.toModel(): MemoryFrame = MemoryFrame(
    id = id,
    ownerAssistantId = ownerAssistantId,
    label = label,
    descriptor = descriptor,
    source = source,
    createdAt = createdAt,
    status = status,
)

fun MemoryGoalEntity.toModel(): MemoryGoal = MemoryGoal(
    id = id,
    ownerAssistantId = ownerAssistantId,
    question = question,
    valueNote = valueNote,
    entityIds = decodeStringList(entityIds),
    state = state,
    webDraft = webDraft,
    askedAt = askedAt,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
)

/** Tolerant JSON string-array decode (no `!!`); returns empty on any malformed input. */
internal fun decodeStringList(json: String): List<String> =
    if (json.isBlank()) emptyList()
    else runCatching { JsonInstant.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())

// ---------------------------------------------------------------------------------------------
// Op vocabulary (closed) — proposed by extraction, applied by the applier (§6.3)
// ---------------------------------------------------------------------------------------------

/**
 * The complete, closed op vocabulary the extraction model may emit. Deterministic code (the applier)
 * decides how each resolves under guardrails — there is no DELETE and no in-place edit. Sleep-pass
 * and manual/UI ops are generated by code, never accepted from extraction, so they are not part of
 * this serialized vocabulary.
 *
 * `ref`/`entity`/`subject`/`object`/`frame` carry extraction-local handles or existing entity ids
 * from the neighborhood digest; the applier resolves them (§6.4).
 */
@Serializable
sealed class MemoryOp {
    /** Resolve-or-create an entity; the applier decides (canonical short-circuit → alias → FTS). */
    @Serializable
    @SerialName("ENSURE_ENTITY")
    data class EnsureEntity(
        val ref: String,
        val name: String,
        val kind: Int = MemEntityKind.CONCEPT,
        val aliases: List<String> = emptyList(),
        val reality: Int = MemReality.REAL,
        val scope: Int? = null,
    ) : MemoryOp()

    @Serializable
    @SerialName("ADD_ALIAS")
    data class AddAlias(
        val entity: String,
        val alias: String,
    ) : MemoryOp()

    @Serializable
    @SerialName("ADD_FACT")
    data class AddFact(
        val subject: String,
        val predicate: String,
        @SerialName("object") val objectRef: String? = null,
        val value: String? = null,
        val statement: String,
        val kind: Int = MemFactKind.DURATIVE,
        val importance: Int = 3,
        val confidence: Double = 0.0,
        val sensitivity: Int = MemSensitivity.NORMAL,
        val category: String? = null,
        val reality: Int = MemReality.REAL,
        val frame: String? = null,
        @SerialName("valid_from") val validFrom: Long? = null,
        @SerialName("valid_until") val validUntil: Long? = null,
    ) : MemoryOp()

    @Serializable
    @SerialName("REINFORCE")
    data class Reinforce(
        @SerialName("fact_id") val factId: String,
        val note: String? = null,
    ) : MemoryOp()

    @Serializable
    @SerialName("UPDATE_FACT")
    data class UpdateFact(
        @SerialName("old_fact_id") val oldFactId: String,
        val predicate: String? = null,
        @SerialName("object") val objectRef: String? = null,
        val value: String? = null,
        val statement: String? = null,
        val kind: Int? = null,
        val importance: Int? = null,
        val confidence: Double? = null,
        val sensitivity: Int? = null,
        val category: String? = null,
        val reality: Int? = null,
        val frame: String? = null,
        @SerialName("valid_from") val validFrom: Long? = null,
        @SerialName("valid_until") val validUntil: Long? = null,
    ) : MemoryOp()

    @Serializable
    @SerialName("CLOSE_FACT")
    data class CloseFact(
        @SerialName("fact_id") val factId: String,
        @SerialName("ended_at") val endedAt: Long? = null,
    ) : MemoryOp()

    @Serializable
    @SerialName("ADD_EPISODE")
    data class AddEpisode(
        val title: String,
        val summary: String,
        val entities: List<String> = emptyList(),
        val frame: String? = null,
        val reality: Int = MemReality.REAL,
        @SerialName("event_start") val eventStart: Long,
        @SerialName("event_end") val eventEnd: Long? = null,
        val importance: Int = 3,
    ) : MemoryOp()

    @Serializable
    @SerialName("EXTEND_EPISODE")
    data class ExtendEpisode(
        @SerialName("episode_id") val episodeId: String,
        val summary: String,
        @SerialName("event_end") val eventEnd: Long? = null,
    ) : MemoryOp()

    @Serializable
    @SerialName("OPEN_GOAL")
    data class OpenGoal(
        val question: String,
        val entities: List<String> = emptyList(),
        @SerialName("value_note") val valueNote: String = "",
    ) : MemoryOp()

    @Serializable
    @SerialName("RESOLVE_GOAL")
    data class ResolveGoal(
        @SerialName("goal_id") val goalId: String,
        val outcome: String,
        val learned: String? = null,
    ) : MemoryOp()
}

/** Convenience accessors matching plan status/kind vocabulary, guarding against unknown ints. */
val MemoryFact.isActive: Boolean get() = status == MemStatus.ACTIVE
val MemoryFact.isDurative: Boolean get() = kind == MemFactKind.DURATIVE
val MemoryFact.isSensitive: Boolean get() = sensitivity == MemSensitivity.SENSITIVE
val MemoryEntityNode.isCanonicalSelf: Boolean
    get() = kind == MemEntityKind.USER_SELF || kind == MemEntityKind.CHARACTER_SELF
val MemoryGoal.isAskable: Boolean get() = state == MemGoalState.PRIMED
