package me.rerere.rikkahub.data.memory

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemReality
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.model.MemoryNodeTypeCodec
import me.rerere.rikkahub.data.model.MemoryOp
import me.rerere.rikkahub.utils.JsonInstant

/**
 * Tolerant parser from the extraction model's JSON output to the closed [MemoryOp] vocabulary.
 *
 * Pure and dependency-free (JSON only), so the encoder→ops mapping is unit-testable without a model
 * or Room. Extraction on small models is noisy: malformed entries are skipped individually and the
 * array is located leniently, so one bad op never discards the whole pass.
 */
object MemoryOpParser {

    fun parse(text: String): List<MemoryOp> {
        val array = extractJsonArray(text) ?: return emptyList()
        val ops = mutableListOf<MemoryOp>()
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            val op = runCatching { parseOp(obj) }.getOrNull() ?: continue
            ops += op
        }
        return ops
    }

    private fun parseOp(obj: JsonObject): MemoryOp? = when (obj.str("op")?.uppercase()) {
        "ADD_NODE", "ADD" -> {
            val content = obj.str("content")?.trim().orEmpty()
            if (content.isEmpty()) null
            else MemoryOp.AddNode(
                type = MemoryNodeTypeCodec.fromString(obj.str("type")) ?: MemNodeType.FACT,
                content = content,
                entities = obj.strList("entities"),
                aliases = obj.strList("aliases"),
                category = obj.str("category"),
                importance = obj.int("importance", 3),
                confidence = obj.float("confidence", 0.7f),
                sensitivity = if (obj.str("sensitivity")?.equals("SENSITIVE", true) == true) MemSensitivity.SENSITIVE else MemSensitivity.NORMAL,
                reality = if (obj.str("reality")?.equals("FICTION", true) == true) MemReality.FICTION else MemReality.REAL,
                frame = obj.str("frame"),
                eventStart = obj.long("event_start"),
                eventEnd = obj.long("event_end"),
                validFrom = obj.long("valid_from"),
                validUntil = obj.long("valid_until"),
                rationale = obj.str("rationale").orEmpty(),
                excerpt = obj.str("excerpt").orEmpty(),
            )
        }
        "REINFORCE" -> obj.str("id")?.let { MemoryOp.Reinforce(it, obj.str("note"), obj.str("rationale").orEmpty()) }
        "UPDATE" -> obj.str("old_id")?.let { old ->
            val content = obj.str("content")?.trim().orEmpty()
            if (content.isEmpty()) null
            else MemoryOp.UpdateNode(old, content, obj.intOrNull("importance"), rationale = obj.str("rationale").orEmpty())
        }
        "CLOSE" -> obj.str("id")?.let { MemoryOp.CloseNode(it, obj.long("ended_at"), obj.str("rationale").orEmpty()) }
        "ADD_EDGE" -> {
            val from = obj.str("from")
            val to = obj.str("to")
            val edgeType = when (obj.str("type")?.uppercase()) {
                "CONTRADICTS" -> MemEdgeType.CONTRADICTS
                "RELATES_TO", "RELATES" -> MemEdgeType.RELATES_TO
                else -> null
            }
            if (from != null && to != null && edgeType != null) MemoryOp.AddEdge(from, to, edgeType) else null
        }
        "OPEN_GOAL" -> obj.str("question")?.let { MemoryOp.OpenGoal(it, obj.strList("entities"), obj.str("value_note").orEmpty()) }
        "RESOLVE_GOAL" -> obj.str("id")?.let { MemoryOp.ResolveGoal(it, obj.str("outcome").orEmpty(), obj.str("learned")) }
        else -> null
    }

    private fun extractJsonArray(text: String): JsonArray? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { JsonInstant.parseToJsonElement(text.substring(start, end + 1)) as? JsonArray }.getOrNull()
    }

    // ---- lenient JsonObject accessors ----
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.int(key: String, default: Int): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: default
    private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.float(key: String, default: Float): Float = (this[key] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: default
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } } ?: emptyList()
}

/**
 * Pure helper capturing the watermark-integrity chunking rule (§5.1): given the current watermark
 * and message count, the next extraction chunk starts right after the watermark and spans at most
 * [maxWindow] messages. [processedUpToIndex] is where the watermark advances *after* a successful
 * pass — never past a message the pass did not read.
 */
object MemoryWindow {
    data class Chunk(val startInclusive: Int, val endExclusive: Int, val processedUpToIndex: Int) {
        val isEmpty: Boolean get() = startInclusive >= endExclusive
    }

    fun nextChunk(watermark: Int, messageCount: Int, maxWindow: Int): Chunk {
        val start = (watermark + 1).coerceIn(0, messageCount)
        val end = minOf(messageCount, start + maxWindow)
        return Chunk(start, end, end - 1)
    }
}
