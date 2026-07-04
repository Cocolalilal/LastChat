package me.rerere.rikkahub.data.memory

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.fuzzyMemoryAgeLabel
import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.model.MemoryExtra
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.math.ln
import kotlin.uuid.Uuid

/**
 * Deterministic, model-call-free retrieval + injection (§5.5). Assembles the memory section injected
 * into the system prompt before generation:
 *
 *  1. core sheet — pinned + top facts/habits, ranked by importance/reinforcement/recency (NOT
 *     retrieval count, to avoid a rich-get-richer loop); high-importance provisionals are hedged;
 *  2. query-relevant recall — last user message → FTS (widened by entity aliases) → 1-hop graph
 *     expansion → ranked top-N not already in the core sheet;
 *  3. recent episode strip — last few episodes, fuzzy-aged and frame-labelled;
 *  4. pending-tail digest — same-assistant conversations with un-encoded tails, raw and labelled,
 *     closing the cross-conversation "remembered too late" gap (§7.4).
 *
 * Everything is bounded by count so the section stays ~400–700 tokens.
 */
class MemoryRecall(
    private val repository: MemoryGraphRepository,
    private val conversationRepo: ConversationRepository,
    private val curiosityEngine: CuriosityEngine? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val CORE_LIMIT = 14
        private const val QUERY_LIMIT = 6
        private const val EPISODE_LIMIT = 3
        private const val PENDING_TAIL_CONVERSATIONS = 4
        private const val PENDING_TAIL_LINES = 4
        private const val PENDING_TAIL_LINE_CHARS = 160
        private const val PENDING_TAIL_WINDOW_MS = 24L * 60 * 60 * 1000
    }

    /**
     * @return the full memory system-prompt section, or "" if there is nothing to inject.
     */
    suspend fun buildInjectionSection(
        assistantId: String,
        activeConversationId: String?,
        messages: List<UIMessage>,
        timeAwareness: Boolean,
        curiosityEnabled: Boolean = false,
    ): String {
        val now = clock()
        val queryText = messages.lastOrNull { it.role == MessageRole.USER }?.toText().orEmpty()

        val visible = repository.getVisibleInjectable(assistantId)

        // Drop the older side of any unresolved adjudication pair so the character never voices both
        // sides of a pending correction.
        val suppressed = computeSuppressedAdjudicationIds(visible)

        val coreNodes = selectCoreSheet(visible, suppressed)
        val coreIds = coreNodes.map { it.id }.toSet()

        val queryNodes = selectQueryRecall(assistantId, queryText, coreIds, suppressed, now)
        val episodes = repository.getRecentEpisodes(assistantId, EPISODE_LIMIT)
            .filter { it.id !in coreIds && it.id !in suppressed }

        // §6.2 frames: resolve each episode's IN_FRAME → frame label + roleplay flag so retrieval can
        // label roleplay vs. chat memories ("*[roleplay, about a week ago]* …") — the shared-history split.
        val frameByEpisode = resolveEpisodeFrames(queryNodes + episodes)

        val pendingTail = buildPendingTail(assistantId, activeConversationId, now)

        // §6.4 curiosity hint (default OFF): at most one, gated + rate-limited by the engine, which also
        // transitions the goal PRIMED→ASKED. midRoleplay suppresses asks during an active fiction scene.
        val midRoleplay = episodes.take(1).any { frameByEpisode[it.id]?.roleplay == true }
        val curiosityHint = if (curiosityEnabled) {
            try {
                curiosityEngine?.selectHint(assistantId, messages.size, midRoleplay, now)
            } catch (e: Exception) {
                null
            }
        } else null

        if (coreNodes.isEmpty() && queryNodes.isEmpty() && episodes.isEmpty() && pendingTail.isEmpty() && curiosityHint.isNullOrBlank()) return ""

        return buildString {
            appendLine("<memory>")
            appendLine("Things you remember about this person and your shared history — fuzzy, human recollections. Don't recite them verbatim or dump them; let them colour how you respond.")
            if (coreNodes.isNotEmpty()) {
                appendLine()
                appendLine("What you know:")
                coreNodes.forEach { appendLine("- ${verbalizeFact(it, now, timeAwareness)}") }
            }
            if (queryNodes.isNotEmpty()) {
                appendLine()
                appendLine("Relevant to what they just said:")
                queryNodes.forEach { node ->
                    val line = if (node.type == MemNodeType.EPISODE) verbalizeEpisode(node, now, timeAwareness, frameByEpisode[node.id])
                    else verbalizeFact(node, now, timeAwareness)
                    appendLine("- $line")
                }
            }
            if (episodes.isNotEmpty()) {
                appendLine()
                appendLine("Recently, together:")
                episodes.forEach { appendLine("- ${verbalizeEpisode(it, now, timeAwareness, frameByEpisode[it.id])}") }
            }
            if (pendingTail.isNotEmpty()) {
                appendLine()
                appendLine("Recent unprocessed chat with you (raw and unclassified — may include roleplay or jokes; treat cautiously):")
                pendingTail.forEach { appendLine("  $it") }
            }
            if (!curiosityHint.isNullOrBlank()) {
                appendLine()
                appendLine(curiosityHint)
            }
            append("</memory>")
        }
    }

    // ---------------- frame resolution (§6.2) ----------------

    /** Frame descriptor for an episode: the FRAME node it points at via its IN_FRAME edge. */
    data class FrameRef(val label: String, val roleplay: Boolean)

    private suspend fun resolveEpisodeFrames(nodes: List<MemoryNodeEntity>): Map<String, FrameRef> {
        val episodeIds = nodes.filter { it.type == MemNodeType.EPISODE }.map { it.id }
        if (episodeIds.isEmpty()) return emptyMap()
        val inFrame = repository.getEdgesTouchingAny(episodeIds).filter { it.type == MemEdgeType.IN_FRAME }
        if (inFrame.isEmpty()) return emptyMap()
        val frameNodes = repository.getNodes(inFrame.map { it.toId }.distinct()).associateBy { it.id }
        val out = HashMap<String, FrameRef>()
        for (edge in inFrame) {
            if (edge.fromId in out) continue // first frame wins
            val frame = frameNodes[edge.toId] ?: continue
            val extra = decodeExtra(frame.extra)
            val label = frame.displayLabel ?: extra.frameLabel ?: frame.content
            out[edge.fromId] = FrameRef(label, extra.frameRoleplay)
        }
        return out
    }

    // ---------------- core sheet ----------------

    private fun selectCoreSheet(visible: List<MemoryNodeEntity>, suppressed: Set<String>): List<MemoryNodeEntity> {
        val candidates = visible.filter {
            it.id !in suppressed &&
                (it.type == MemNodeType.FACT || it.type == MemNodeType.HABIT) &&
                // ACTIVE always; PROVISIONAL only if high-importance (injected hedged).
                (it.status == MemStatus.ACTIVE || (it.status == MemStatus.PROVISIONAL && it.importance >= 4))
        }
        val pinned = candidates.filter { it.pinned }
        val rest = candidates.filter { !it.pinned }.sortedByDescending { coreRank(it) }
        return (pinned + rest).distinctBy { it.id }.take(CORE_LIMIT)
    }

    /** Deliberately excludes times_retrieved so always-injected items can't self-perpetuate. */
    private fun coreRank(node: MemoryNodeEntity): Double {
        val confirmAgeDays = (clock() - node.lastConfirmedAt).coerceAtLeast(0) / 86_400_000.0
        val confirmationRecency = 1.0 / (1.0 + confirmAgeDays / 14.0)
        return node.importance * 2.0 + ln(1.0 + node.timesReinforced) + confirmationRecency
    }

    // ---------------- query recall ----------------

    private suspend fun selectQueryRecall(
        assistantId: String,
        queryText: String,
        excludeIds: Set<String>,
        suppressed: Set<String>,
        now: Long,
    ): List<MemoryNodeEntity> {
        if (queryText.isBlank()) return emptyList()

        val queryTokens = MemoryText.contentTokens(queryText)
        if (queryTokens.isEmpty()) return emptyList()

        // Widen tokens through entity alias tables (additive candidate generation, never a rewrite).
        val expanded = queryTokens.toMutableSet()
        val entities = repository.getEntities(assistantId)
        for (entity in entities) {
            val label = entity.displayLabel ?: entity.content
            val labelTokens = MemoryText.contentTokens(label)
            val aliasTokens = decodeExtra(entity.extra).aliases.flatMap { MemoryText.contentTokens(it) }
            if ((labelTokens + aliasTokens).any { it in queryTokens }) {
                expanded += labelTokens
            }
        }

        val ftsQuery = MemoryText.ftsOrQuery(expanded) ?: return emptyList()
        val seedIds = repository.searchFts(assistantId, ftsQuery, QUERY_LIMIT * 4)
        if (seedIds.isEmpty()) return emptyList()

        // 1-hop expansion via ABOUT/RELATES_TO edges.
        val expandedIds = LinkedHashSet(seedIds)
        val edges = repository.getEdgesTouchingAny(seedIds)
        for (edge in edges) {
            if (edge.type == MemEdgeType.ABOUT || edge.type == MemEdgeType.RELATES_TO) {
                expandedIds += edge.fromId
                expandedIds += edge.toId
            }
        }

        val nodes = repository.getNodes(expandedIds.toList())
        return nodes
            .filter {
                it.id !in excludeIds && it.id !in suppressed &&
                    it.type != MemNodeType.ENTITY && it.type != MemNodeType.FRAME && it.type != MemNodeType.GOAL &&
                    (it.status == MemStatus.ACTIVE || it.status == MemStatus.PROVISIONAL || it.status == MemStatus.CLOSED)
            }
            .sortedByDescending { queryRank(it, now) }
            .take(QUERY_LIMIT)
    }

    private fun queryRank(node: MemoryNodeEntity, now: Long): Double {
        val anchor = node.eventStart ?: node.lastConfirmedAt
        val ageDays = (now - anchor).coerceAtLeast(0) / 86_400_000.0
        val recency = 1.0 / (1.0 + ageDays / 7.0)
        return node.importance * 1.5 + ln(1.0 + node.timesReinforced) + recency
    }

    // ---------------- adjudication suppression ----------------

    /**
     * For nodes flagged as pending adjudication, keep only the newest of each linked group; the
     * older siblings are suppressed from injection until the sleep pass resolves them.
     */
    private suspend fun computeSuppressedAdjudicationIds(visible: List<MemoryNodeEntity>): Set<String> {
        val flagged = visible.filter { it.adjudicationPending }
        if (flagged.isEmpty()) return emptySet()
        val suppressed = HashSet<String>()
        val byId = visible.associateBy { it.id }
        for (node in flagged) {
            val edges = repository.getEdgesTouching(node.id)
            for (edge in edges) {
                if (edge.type != MemEdgeType.RELATES_TO && edge.type != MemEdgeType.CONTRADICTS) continue
                val otherId = if (edge.fromId == node.id) edge.toId else edge.fromId
                val other = byId[otherId] ?: continue
                // Suppress whichever is older; ties keep the lexicographically larger id deterministically.
                val loser = when {
                    node.recordedAt < other.recordedAt -> node.id
                    node.recordedAt > other.recordedAt -> other.id
                    else -> minOf(node.id, other.id)
                }
                suppressed += loser
            }
        }
        return suppressed
    }

    // ---------------- pending tail (§7.4) ----------------

    private suspend fun buildPendingTail(assistantId: String, activeConversationId: String?, now: Long): List<String> {
        val assistantUuid = runCatching { Uuid.parse(assistantId) }.getOrNull() ?: return emptyList()
        val recent = runCatching { conversationRepo.getRecentConversations(assistantUuid, 8) }.getOrDefault(emptyList())
        val lines = mutableListOf<String>()
        for (conversation in recent) {
            if (lines.size >= PENDING_TAIL_CONVERSATIONS * PENDING_TAIL_LINES) break
            val convId = conversation.id.toString()
            if (convId == activeConversationId) continue
            if ((now - conversation.updateAt.toEpochMilli()) > PENDING_TAIL_WINDOW_MS) continue
            val msgs = conversation.currentMessages
            val watermark = repository.resolveWatermarkIndex(convId, msgs.map { it.id.toString() })
            val lag = msgs.size - (watermark + 1)
            if (lag <= 0) continue
            msgs.takeLast(PENDING_TAIL_LINES).forEach { msg ->
                val text = msg.toText().trim()
                if (text.isNotEmpty()) {
                    val who = if (msg.role == MessageRole.USER) "them" else "you"
                    lines += "[$who] ${text.take(PENDING_TAIL_LINE_CHARS)}"
                }
            }
        }
        return lines.take(PENDING_TAIL_CONVERSATIONS * PENDING_TAIL_LINES)
    }

    // ---------------- verbalization ----------------

    private fun verbalizeFact(node: MemoryNodeEntity, now: Long, timeAwareness: Boolean): String {
        val closed = node.status == MemStatus.CLOSED || node.validUntil != null
        val base = if (closed) "Used to be true: ${node.content}" else node.content
        val hedged = if (node.status == MemStatus.PROVISIONAL) "$base (you recall this, though it was only mentioned once)" else base
        if (!timeAwareness) return hedged
        return hedged
    }

    private fun verbalizeEpisode(node: MemoryNodeEntity, now: Long, timeAwareness: Boolean, frame: FrameRef?): String {
        // Frame comes from the IN_FRAME graph edge (§6.2); fall back to any label stashed on the node.
        val frameLabel = frame?.label ?: decodeExtra(node.extra).frameLabel
        val ageLabel = if (timeAwareness) fuzzyMemoryAgeLabel(node.eventStart ?: node.recordedAt, now) else null
        return MemoryRecallLogic.episodeLine(node.content, frameLabel, ageLabel)
    }

    private fun decodeExtra(json: String): MemoryExtra =
        runCatching { JsonInstant.decodeFromString<MemoryExtra>(json) }.getOrDefault(MemoryExtra())
}

/**
 * Pure verbalization helpers (§5.5/§6.2), model- and Room-free so the frame/tense formatting that
 * produces "*[roleplay, about a week ago]* we played at learning together" is unit-testable.
 */
object MemoryRecallLogic {
    /** The bracketed prefix combining an optional frame label and an optional fuzzy age label. */
    fun episodePrefix(frameLabel: String?, ageLabel: String?): String {
        val parts = buildList {
            if (!frameLabel.isNullOrBlank()) add(frameLabel.trim())
            if (!ageLabel.isNullOrBlank()) add(ageLabel.trim())
        }
        return if (parts.isEmpty()) "" else "*[${parts.joinToString(", ")}]* "
    }

    fun episodeLine(content: String, frameLabel: String?, ageLabel: String?): String =
        episodePrefix(frameLabel, ageLabel) + content
}
