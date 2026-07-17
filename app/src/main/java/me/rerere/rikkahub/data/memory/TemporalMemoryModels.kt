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
    @SerialName("add")
    ADD,

    @SerialName("reinforce")
    REINFORCE,

    @SerialName("supersede")
    SUPERSEDE,

    @SerialName("close")
    CLOSE,
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

data class TemporalRecallItem(
    val stableId: String,
    val text: String,
    val timestamp: Long,
    val score: Float,
    val kind: RecallKind,
    val confidence: Float,
    val validUntil: Long? = null,
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
