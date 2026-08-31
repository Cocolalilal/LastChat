package me.rerere.rikkahub.data.memory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemoryExtractionEnvelope(
    val operations: List<MemoryWriteOperation> = emptyList(),
    val episode: ExtractedEpisode? = null,
)

@Serializable
data class MemoryWriteOperation(
    val op: MemoryOperationType,
    val subject: String = "user",
    val predicate: String = "notes",
    @SerialName("object")
    val objectValue: String? = null,
    val statement: String,
    val kind: String = "durative",
    val reality: String = "real",
    val frame: String? = null,
    val confidence: Float = 0.7f,
    val importance: Int = 3,
    @SerialName("valid_from")
    val validFrom: Long? = null,
    @SerialName("valid_until")
    val validUntil: Long? = null,
    @SerialName("replaces_claim_id")
    val replacesClaimId: Long? = null,
    val sensitive: Boolean = false,
    @SerialName("source_message_id")
    val sourceMessageId: String? = null,
)

@Serializable
enum class MemoryOperationType {
    @SerialName("add") ADD,
    @SerialName("reinforce") REINFORCE,
    @SerialName("supersede") SUPERSEDE,
    @SerialName("close") CLOSE,
}

@Serializable
data class ExtractedEpisode(
    val title: String,
    val summary: String,
    @SerialName("scene_key")
    val sceneKey: String,
    val importance: Int = 3,
    val reality: String = "real",
    val frame: String? = null,
    @SerialName("event_start")
    val eventStart: Long? = null,
    @SerialName("event_end")
    val eventEnd: Long? = null,
)

@Serializable
data class TemporalMemoryExport(
    val version: Int = 1,
    val claims: List<ExportedTemporalClaim> = emptyList(),
    val episodes: List<ExportedTemporalEpisode> = emptyList(),
)

@Serializable
data class ExportedTemporalClaim(
    val subject: String,
    val predicate: String,
    val objectValue: String? = null,
    val statement: String,
    val kind: Int,
    val reality: Int,
    val frame: String? = null,
    val status: Int,
    val confidence: Float,
    val importance: Int,
    val sensitivity: Int,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val observedAt: Long,
    val recordedAt: Long,
    val source: Int,
)

@Serializable
data class ExportedTemporalEpisode(
    val title: String,
    val summary: String,
    val eventStart: Long,
    val eventEnd: Long? = null,
    val reality: Int,
    val frame: String? = null,
    val importance: Int,
    val status: Int,
    val confidence: Float,
)

data class SourceMessage(
    val id: String,
    val role: Int,
    val text: String,
    val observedAt: Long,
)

data class TemporalRecallItem(
    val stableId: String,
    val text: String,
    val timestamp: Long,
    val score: Float,
    val kind: RecallKind,
    val confidence: Float,
    val validUntil: Long? = null,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null,
    val legacyMemoryId: Int? = null,
)

enum class RecallKind {
    CURRENT_STATE,
    HISTORICAL_FACT,
    EPISODE,
    PAST_CHAT,
    LEGACY,
}

data class TemporalRecallPacket(
    val projection: String?,
    val items: List<TemporalRecallItem>,
) {
    fun toPromptBlock(): String = buildString {
        append("## Character memory\n")
        append("Use these as evidence-backed recollections. Prefer current facts over superseded history and express uncertainty when confidence is low.\n")
        projection?.takeIf { it.isNotBlank() }?.let {
            append("### Current understanding\n")
            append(it.trim()).append('\n')
        }
        if (items.isNotEmpty()) {
            append("### Relevant recollections\n")
            items.forEach { item ->
                val label = when (item.kind) {
                    RecallKind.CURRENT_STATE -> "current"
                    RecallKind.HISTORICAL_FACT -> "historical"
                    RecallKind.EPISODE -> "episode"
                    RecallKind.PAST_CHAT -> "past chat"
                    RecallKind.LEGACY -> "note"
                }
                append("- [").append(label).append("] ").append(item.text.trim()).append('\n')
            }
        }
    }.trim()
}

/** Exact evidence-bound extraction prompt consumed by Android and iOS. */
fun buildTemporalMemoryExtractionPrompt(
    pending: List<SourceMessage>,
    existing: List<TemporalRecallItem>,
): String = """
    You are LastChat's evidence-bound temporal memory encoder for one AI character.
    Extract only durable personal facts, preferences, relationships, plans, meaningful changes, and one coherent scene from NEW_MESSAGES.
    Never invent. Never infer sensitive traits. Treat jokes, hypothetical discussion, and roleplay as non-real and label reality accordingly.
    Resolve relative dates from each message's observed_at timestamp, not from today's date.
    Existing memories are only for deduplication and detecting changes.

    Operations:
    - add: a new claim
    - reinforce: the same claim is confirmed
    - supersede: a current state changed; include replaces_claim_id only when an id is known
    - close: a plan/state ended

    Use concise snake_case predicates. A scene should span the new messages and be omitted for trivial filler.
    Output strict JSON only:
    {"operations":[{"op":"add","subject":"user","predicate":"likes","object":"tea","statement":"User likes tea.","kind":"durative","reality":"real","confidence":0.9,"importance":3,"valid_from":null,"valid_until":null,"replaces_claim_id":null,"sensitive":false,"source_message_id":"..."}],"episode":{"title":"...","summary":"...","scene_key":"stable-topic-key","importance":3,"reality":"real","frame":null,"event_start":null,"event_end":null}}

    EXISTING_MEMORIES:
    ${existing.joinToString("\n") { item ->
        val claimId = item.stableId.removePrefix("claim:").toLongOrNull()
        if (claimId != null) "- claim_id=$claimId ${item.text}" else "- ${item.text}"
    }}

    NEW_MESSAGES:
    ${pending.joinToString("\n") { message ->
        "id=${message.id} observed_at=${message.observedAt} role=${if (message.role == 0) "user" else "assistant"}: ${message.text.take(2_000)}"
    }}
""".trimIndent()
