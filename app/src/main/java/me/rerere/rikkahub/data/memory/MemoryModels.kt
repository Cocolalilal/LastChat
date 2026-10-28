package me.rerere.rikkahub.data.memory

import kotlinx.serialization.Serializable
import java.security.MessageDigest

enum class MemoryExperience { BASIC, ADVANCED }

@Serializable
data class MemoryEvidence(
    val conversationId: String = "",
    val messageId: String = "",
    val revision: String = "",
    val quote: String = "",
    val origin: String = "chat",
    val paragraphId: String? = null,
)

/** Basic's authoritative paragraphs; no graph or embedding is needed to read a note. */
@Serializable
data class MemoryParagraph(
    val id: String,
    val text: String,
    val evidence: List<MemoryEvidence> = emptyList(),
    val authored: Boolean = false,
    val validUntil: Long? = null,
    val originFact: String? = null,
)

@Serializable
data class MemoryNote(
    val id: String,
    val title: String,
    val category: String = "Topics",
    val description: String = "",
    val aliases: List<String> = emptyList(),
    val paragraphs: List<MemoryParagraph> = emptyList(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val sourceOnly: Boolean = false,
    val context: String = "real",
) {
    val body: String get() = paragraphs.joinToString("\n\n") { it.text }
}

@Serializable
data class MemoryNode(
    val id: String,
    val name: String,
    val type: String = "Thing",
    val aliases: List<String> = emptyList(),
    val context: String = "real",
    val revision: Long = 1,
)

@Serializable
data class MemoryFact(
    val id: String,
    val text: String,
    val topic: String = "Overview",
    val subject: String = "user",
    val relation: String = "related to",
    val objectId: String? = null,
    val kind: String = "fact",
    val context: String = "real",
    val evidence: List<MemoryEvidence> = emptyList(),
    val revision: Long = 1,
    val recordedAt: Long = System.currentTimeMillis(),
    val occurredAt: String? = null,
    val validUntil: Long? = null,
    val supersedes: String? = null,
    val status: String = "active",
    val pinned: Boolean = false,
    val authored: Boolean = false,
)

@Serializable
data class MemoryMetadata(
    val migrated: Boolean = false,
    val revision: Long = 0,
    val policyRevision: Long = 0,
    val rules: String = "",
    val decisionAssistance: Boolean = false,
    val decisionModel: String = "jev-1.14.0",
    val paused: Boolean = false,
    val preparing: Boolean = false,
    val activeAdvanced: Boolean = false,
    val preparationId: String? = null,
    val cancelled: Boolean = false,
    val learningEnabledAt: Long = 0,
    val updatedAt: Long = 0,
    val summary: List<MemorySummarySection> = emptyList(),
    val progress: String = "",
    val error: String? = null,
    val inFlight: String? = null,
)

@Serializable
data class MemorySummarySection(val title: String, val text: String, val factIds: List<String> = emptyList())

/** A bounded whole-fact fallback; the canonical graph is never serialized into one summary. */
internal fun compactMemoryOverview(facts: List<MemoryFact>, now: Long = System.currentTimeMillis()): List<MemorySummarySection> =
    facts.filter { isCurrentMemory(it,now) && it.kind in setOf("fact","habit") }
        .sortedWith(compareByDescending<MemoryFact> { it.pinned }.thenByDescending { it.evidence.distinct().size }.thenByDescending { it.recordedAt })
        .take(80).groupBy { it.topic }.entries
        .sortedWith(compareByDescending<Map.Entry<String,List<MemoryFact>>> { it.key=="Overview" }.thenByDescending { it.value.size })
        .take(5).mapNotNull { (topic,entries) ->
            var length=0
            val chosen=entries.filter { fact ->
                if(length+fact.text.length+1>2200)false else { length+=fact.text.length+1;true }
            }
            if(chosen.isEmpty())null else MemorySummarySection(topic,chosen.joinToString(" "){it.text},chosen.map{it.id})
        }

@Serializable
data class MemoryVector(val revision: Long, val recipe: String, val values: List<Float>)

@Serializable
data class MemoryExclusion(val conversationId: String, val messageId: String, val revision: String,
                           val start: Int, val end: Int, val fragmentHash: String)
@Serializable
data class MemorySuppression(val exclusions: List<MemoryExclusion> = emptyList(), val textHash: String = "")

/** Union overlapping erasures before applying offsets, preserving independent neighboring text. */
internal fun redactMemoryRanges(text: String, exclusions: List<MemoryExclusion>): String? {
    if (exclusions.isEmpty()) return text
    val revision = memoryHash(text)
    if (exclusions.any { it.revision != revision || it.start < 0 || it.end > text.length || it.start > it.end ||
            memoryHash(text.substring(it.start, it.end)) != it.fragmentHash }) return null
    val ranges = mutableListOf<Pair<Int,Int>>()
    exclusions.sortedBy { it.start }.forEach { exclusion ->
        val last = ranges.lastOrNull()
        if(last!=null && exclusion.start<=last.second) ranges[ranges.lastIndex]=last.first to maxOf(last.second,exclusion.end)
        else ranges+=exclusion.start to exclusion.end
    }
    return ranges.asReversed().fold(text) { result, (start,end) -> result.removeRange(start,end) }
}

@Serializable
data class MemoryChange(
    val kind: String,
    val id: String,
    val expectedRevision: Long? = null,
    val note: MemoryNote? = null,
    val fact: MemoryFact? = null,
    val node: MemoryNode? = null,
    val delete: Boolean = false,
)

@Serializable
data class MemoryDraft(
    val id: String,
    val request: String,
    val changes: List<MemoryChange>,
    val policyRevision: Long,
    val summary: List<MemorySummarySection> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val configuration: String = "",
    val reviewNote: String? = null,
)

@Serializable
data class MemoryCommit(
    val id: String,
    val label: String,
    val at: Long,
    val before: List<MemoryChange>,
    val after: List<MemoryChange>,
)

data class CharacterMemory(
    val metadata: MemoryMetadata = MemoryMetadata(),
    val notes: List<MemoryNote> = emptyList(),
    val facts: List<MemoryFact> = emptyList(),
    val nodes: List<MemoryNode> = emptyList(),
    val drafts: List<MemoryDraft> = emptyList(),
    val history: List<MemoryCommit> = emptyList(),
)

@Serializable
data class CharacterMemoryPackage(
    val version: Int = 1,
    val notes: List<MemoryNote> = emptyList(),
    val facts: List<MemoryFact> = emptyList(),
    val nodes: List<MemoryNode> = emptyList(),
    val rememberingRules: String = "",
)

fun memoryHash(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun sameMemoryText(a: String, b: String): Boolean =
    a.trim().replace(Regex("\\s+"), " ").equals(b.trim().replace(Regex("\\s+"), " "), ignoreCase = true)

internal fun isCurrentMemory(fact: MemoryFact, now: Long): Boolean =
    fact.status == "active" && (fact.validUntil == null || fact.validUntil > now)

internal fun validateMemoryFact(fact: MemoryFact) {
    require(fact.text.isNotBlank() && fact.text.length <= 4000) { "Memory text must be between 1 and 4000 characters." }
    require(fact.kind in setOf("fact", "habit", "plan", "event", "temporary")) { "Unknown memory type." }
    require(fact.context.isNotBlank() && fact.context.length <= 120)
    require(fact.relation.length in 1..120)
    require(fact.evidence.isNotEmpty() || fact.authored) { "A memory needs a source." }
    require(fact.kind !in setOf("plan", "temporary") || fact.validUntil != null) { "Temporary memories need an expiry." }
}

/** Manual Save grants authorship only to changed paragraphs, never all pre-existing content. */
internal fun editNoteParagraphs(note: MemoryNote, body: String, newId: () -> String): List<MemoryParagraph> =
    body.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.map { text ->
        note.paragraphs.firstOrNull { it.text == text.trim() }
            ?: MemoryParagraph(newId(), text.trim(), authored = true)
    }
