package me.rerere.rikkahub.data.memory

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.dao.MemoryConversationStateDao
import me.rerere.rikkahub.data.db.entity.MemBudgetCategory
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemoryConversationStateEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant

/**
 * The encoding (extraction) pass (§5.1). One model call per pass turns an un-encoded window of chat
 * messages into a list of [me.rerere.rikkahub.data.model.MemoryOp]s, which the [MemoryOpApplier]
 * then applies under its guardrails.
 *
 * Watermark integrity is the hard invariant: the watermark never advances past a message no pass has
 * read (see [MemoryWindow]). Long windows are chunked oldest-first so a deferral delays memory but
 * can never lose it. Never runs on the generation hot path — only from the extraction worker.
 */
class MemoryEncoder(
    private val providerManager: ProviderManager,
    private val applier: MemoryOpApplier,
    private val repository: MemoryGraphRepository,
    private val conversationStateDao: MemoryConversationStateDao,
    private val budget: MemoryBudget,
    private val profileGenerator: CharacterProfileGenerator,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "MemoryEncoder"
        private const val MAX_WINDOW_MESSAGES = 30 // chunk cap for the extraction window
        private const val CONTEXT_LOOKBACK = 2 // processed messages before the window, for antecedents
        private const val DIGEST_NODE_LIMIT = 30
    }

    sealed interface Outcome {
        data object Skipped : Outcome // nothing to do / disabled / no model
        data object Deferred : Outcome // budget exhausted or call failed; watermark untouched
        data class Encoded(val processedUpToIndex: Int, val opCount: Int) : Outcome
    }

    suspend fun encode(conversation: Conversation, assistant: Assistant, settings: Settings): Outcome {
        if (!assistant.enableMemory) return Outcome.Skipped

        val messages = conversation.currentMessages
        val convId = conversation.id.toString()

        // §7.3: reconcile abandoned-branch demotions before choosing the window. This also lets the
        // watermark clamp back — a demoted/abandoned anchor re-resolves to a surviving earlier message.
        repository.reconcileBranchDemotions(conversation, clock())

        // Fix 1: the watermark is anchored on a message id, re-resolved to an index against the
        // current branch (self-correcting under deletion/reordering/branch switch).
        val messageIds = messages.map { it.id.toString() }
        val resolvedWatermark = repository.resolveWatermarkIndex(convId, messageIds)

        val chunk = MemoryWindow.nextChunk(resolvedWatermark, messages.size, MAX_WINDOW_MESSAGES)
        if (chunk.isEmpty) return Outcome.Skipped

        val caps = MemoryBudgetCaps.of(MemoryModels.presetFor(settings, assistant.id.toString()))
        if (!caps.extractionEnabled) return Outcome.Skipped

        // No fallback chain: an unset parser model pauses extraction (watermark holds, nothing lost).
        val resolved = MemoryModels.resolveParser(settings) ?: return Outcome.Skipped

        // Budget admission — deferral leaves the watermark untouched (nothing lost).
        if (!budget.tryConsumeDaily(MemBudgetCategory.EXTRACTION, caps.extractionDailyCap)) return Outcome.Deferred

        val window = messages.subList(chunk.startInclusive, chunk.endExclusive)
        val contextBefore = messages.subList((chunk.startInclusive - CONTEXT_LOOKBACK).coerceAtLeast(0), chunk.startInclusive)

        // §6.1/6.3: the character profile (frames, care-abouts, persona relation) steers classification
        // and frame tagging. refresh() is a no-op model-call-wise when the persona hash is unchanged.
        val profile = try {
            profileGenerator.refresh(assistant, settings)
        } catch (e: Exception) {
            PlatformLog.e(TAG, "profile refresh failed: ${e.message}")
            CharacterProfileLogic.defaultProfile()
        }
        val openGoals = collectOpenGoals(assistant.id.toString())

        val digest = buildNeighborhoodDigest(assistant.id.toString(), window)
        val prompt = buildPrompt(window, contextBefore, digest, profile, openGoals)

        val responseText = try {
            callModel(resolved.first, resolved.second, prompt, settings)
        } catch (e: Exception) {
            PlatformLog.e(TAG, "extraction model call failed: ${e.message}")
            // Do NOT advance the watermark on failure: the window is retried next pass.
            return Outcome.Deferred
        }

        val ops = MemoryOpParser.parse(responseText)
        if (ops.isNotEmpty()) {
            applier.apply(
                ops = ops,
                ctx = MemoryApplyContext(
                    assistantId = assistant.id.toString(),
                    conversationId = convId,
                    messageIds = window.map { it.id.toString() },
                    source = MemSource.EXTRACTED,
                    now = clock(),
                ),
            )
        }

        // Advance the watermark to this chunk's end, anchored on the last processed message's id
        // (only on a successful pass — deferral/failure leaves it where it was).
        conversationStateDao.upsert(
            MemoryConversationStateEntity(
                conversationId = convId,
                assistantId = assistant.id.toString(),
                extractedUpToMessageId = messageIds[chunk.processedUpToIndex],
                lastExtractAt = clock(),
            )
        )
        return Outcome.Encoded(chunk.processedUpToIndex, ops.size)
    }

    // ---------------- prompt building ----------------

    private suspend fun buildNeighborhoodDigest(assistantId: String, window: List<UIMessage>): List<Pair<String, String>> {
        val tokens = window.flatMap { MemoryText.contentTokens(it.toText()) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(24).map { it.key }
        val ftsQuery = MemoryText.ftsOrQuery(tokens) ?: return emptyList()
        val ids = repository.searchFts(assistantId, ftsQuery, DIGEST_NODE_LIMIT)
        if (ids.isEmpty()) return emptyList()
        return repository.getNodes(ids)
            .filter { it.type != MemNodeType.ENTITY && it.type != MemNodeType.FRAME }
            .map { it.id to it.content }
    }

    /** Open (non-terminal) curiosity goals so extraction can RESOLVE_GOAL when a chat answers one (§6.4). */
    private suspend fun collectOpenGoals(assistantId: String): List<Pair<String, String>> {
        return repository.getVisibleByType(assistantId, MemNodeType.GOAL, MemoryGraphRepository.INJECTABLE_STATUSES)
            .filter { !CuriosityLogic.isTerminal(decodeGoalState(it.extra)) }
            .map { it.id to (decodeGoalQuestion(it.extra) ?: it.content) }
            .take(6)
    }

    private fun decodeGoalState(json: String): String? =
        runCatching { JsonInstant.decodeFromString<me.rerere.rikkahub.data.model.MemoryExtra>(json).goalState }.getOrNull()

    private fun decodeGoalQuestion(json: String): String? =
        runCatching { JsonInstant.decodeFromString<me.rerere.rikkahub.data.model.MemoryExtra>(json).goalQuestion }.getOrNull()

    private fun buildPrompt(
        window: List<UIMessage>,
        contextBefore: List<UIMessage>,
        digest: List<Pair<String, String>>,
        profile: CharacterMemoryProfile,
        openGoals: List<Pair<String, String>>,
    ): String = buildString {
        appendLine("You are the memory-encoding subsystem for an AI character. Read the NEW MESSAGES and extract durable memories about the user and your shared history, as a strict JSON array of operations. Output ONLY the JSON array.")
        appendLine()
        appendLine("Current time (epoch millis): ${clock()}")
        appendLine()
        // §6.1/6.3 character context: what this character is to the user, what it cares to remember,
        // and its interaction frames — used to classify literal/joke/fiction and to tag episodes.
        if (profile.personaRelation.isNotBlank() || profile.careAbouts.isNotEmpty() || profile.frames.isNotEmpty()) {
            appendLine("CHARACTER CONTEXT:")
            if (profile.personaRelation.isNotBlank()) appendLine("  Relation to user: ${profile.personaRelation}")
            if (profile.careAbouts.isNotEmpty()) appendLine("  Cares about remembering: ${profile.careAbouts.joinToString(", ")}")
            if (profile.frames.isNotEmpty()) {
                appendLine("  Frames (tag each EPISODE with the best-matching \"frame\" label; roleplay frames imply reality=FICTION):")
                profile.frames.forEach { f ->
                    appendLine("    - ${f.label}${if (f.roleplay) " [roleplay]" else ""}${if (f.descriptor.isNotBlank()) ": ${f.descriptor}" else ""}")
                }
            }
            appendLine()
        }
        if (openGoals.isNotEmpty()) {
            appendLine("OPEN CURIOSITY GOALS (if the NEW MESSAGES clearly answer one, emit RESOLVE_GOAL with its id, outcome=CONFIRMED and a \"learned\" fact; if the user brushed it off, outcome=DECLINED):")
            openGoals.forEach { (id, q) -> appendLine("  [$id] $q") }
            appendLine()
        }
        if (digest.isNotEmpty()) {
            appendLine("EXISTING BELIEFS (reference these ids to REINFORCE/UPDATE/CLOSE instead of adding duplicates):")
            digest.forEach { (id, content) -> appendLine("  [$id] $content") }
            appendLine()
        }
        if (contextBefore.isNotEmpty()) {
            appendLine("EARLIER CONTEXT (already processed — for antecedent resolution only, do NOT re-extract):")
            contextBefore.forEach { appendLine("  ${roleLabel(it)}: ${it.toText().take(400)}") }
            appendLine()
        }
        appendLine("NEW MESSAGES to extract from:")
        window.forEach { appendLine("  ${roleLabel(it)}: ${it.toText()}") }
        appendLine()
        appendLine(OP_INSTRUCTIONS)
    }

    private fun roleLabel(msg: UIMessage): String = when (msg.role) {
        MessageRole.USER -> "User"
        MessageRole.ASSISTANT -> "You"
        else -> msg.role.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    private suspend fun callModel(provider: ProviderSetting, model: Model, prompt: String, settings: Settings): String {
        val handler = providerManager.getProviderByType(provider)
        val response = handler.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.user(prompt)),
            params = settings.buildSummarizerGenerationParams(model = model, temperature = 0.3f),
        )
        return response.choices.firstOrNull()?.message?.toContentText().orEmpty()
    }

}

private const val OP_INSTRUCTIONS = """
RULES:
- Output ONLY a JSON array of operation objects. No prose, no markdown fences.
- Classify each candidate as literal | joke | hypothetical | fiction | instruction. Drop jokes and
  hypotheticals unless they reveal a durable style trait (then a low-importance HABIT). Fiction →
  an EPISODE with "reality":"FICTION"; NEVER a global user fact.
- Prefer REINFORCE/UPDATE/CLOSE against an EXISTING BELIEF id over adding a near-duplicate.
- importance 1..5 (5 = identity-level: name, family, health; 1 = ambient trivia). Trivia should
  usually produce NO op. At most 6 ADD_NODE ops.
- Mark "sensitivity":"SENSITIVE" for secrets, health, sexuality, finances, anything confided.
- Tag user facts with "category": name | pronouns | language | timezone | occupation_study | other.
- Propose "aliases" when you introduce an entity (e.g. "uni" for "TU Wien").
- Resolve relative time ("next Friday") to absolute epoch millis in event_start/valid_until.
- Include a one-line "rationale" and a short source "excerpt" on ADD_NODE/UPDATE.

OPERATIONS:
{"op":"ADD_NODE","type":"FACT|EPISODE|HABIT","content":"...","entities":["User","TU Wien"],"aliases":["uni"],"category":"occupation_study","importance":3,"confidence":0.8,"sensitivity":"NORMAL","reality":"REAL","rationale":"...","excerpt":"..."}
{"op":"REINFORCE","id":"<existing belief id>","rationale":"restated"}
{"op":"UPDATE","old_id":"<existing belief id>","content":"corrected statement","rationale":"user corrected"}
{"op":"CLOSE","id":"<existing belief id>","ended_at":<epochMillis>}
{"op":"RESOLVE_GOAL","id":"<open goal id>","outcome":"CONFIRMED|DECLINED","learned":"the fact the user just revealed (CONFIRMED only)"}

If nothing is worth remembering, output [].
"""
