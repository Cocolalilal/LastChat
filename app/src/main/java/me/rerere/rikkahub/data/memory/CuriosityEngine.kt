package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryActivityDao
import me.rerere.rikkahub.data.db.dao.MemoryEdgeDao
import me.rerere.rikkahub.data.db.dao.MemoryNodeDao
import me.rerere.rikkahub.data.db.entity.MemActivityKind
import me.rerere.rikkahub.data.db.entity.MemBudgetCategory
import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryExtra
import me.rerere.rikkahub.data.model.MemoryGoalState
import me.rerere.rikkahub.data.model.MemoryOp
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import kotlin.uuid.Uuid

/**
 * The curiosity engine (§6.4) — GOAL-node lifecycle plus the gated, heavily rate-limited delivery of
 * at most one gentle in-character question. **Default OFF**: nothing here runs unless the
 * `proactiveCuriosity` toggle is on; the feature is fully built and one switch away.
 *
 * Three responsibilities, split by where they run:
 *  - [selectHint] (retrieval hot path): read PRIMED goals, clear the delivery gate ([CuriosityLogic]),
 *    transition the chosen goal PRIMED→ASKED and return one short hint. No model call.
 *  - [runSleepPass] (sleep pass, budgeted): deterministic IGNORED→ABANDONED back-off, then at most one
 *    new goal generated from an eligible belief (CURIOSITY budget), with an optional web-fill draft.
 *
 * Back-off is structural: a DECLINED goal is ABANDONED forever and its entities are excluded from
 * future generation; an unanswered ask escalates on elapsed time only — never a re-ask.
 */
class CuriosityEngine(
    private val db: AppDatabase,
    private val nodeDao: MemoryNodeDao,
    private val edgeDao: MemoryEdgeDao,
    private val activityDao: MemoryActivityDao,
    private val scopeLocks: MemoryScopeLocks,
    private val budget: MemoryBudget,
    private val providerManager: ProviderManager,
    private val applier: MemoryOpApplier,
    private val profileGenerator: CharacterProfileGenerator,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "CuriosityEngine"
        private const val HOUR_MS = 3_600_000.0
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        private const val GENERATION_CANDIDATES = 6
    }

    // ================= delivery (retrieval hot path) =================

    /**
     * Choose at most one curiosity hint to inject, or null. Called only when the toggle is on. When a
     * goal clears [CuriosityLogic.canDeliver], it is transitioned PRIMED→ASKED (a small write) and a
     * GOAL_ASKED activity row is recorded, so the same goal can never be asked twice.
     */
    suspend fun selectHint(assistantId: String, messageCount: Int, midRoleplay: Boolean, now: Long = clock()): String? {
        val asksThisWeek = activityDao.countKindSince(assistantId, MemActivityKind.GOAL_ASKED, now - WEEK_MS)
        val lastAskAt = activityDao.lastAtForKind(assistantId, MemActivityKind.GOAL_ASKED)
        val hoursSinceLastAsk = if (lastAskAt == null) Double.MAX_VALUE else (now - lastAskAt) / HOUR_MS

        // Character-wide gates first (cheap) so we don't scan goals when nothing could be delivered.
        if (!CuriosityLogic.canDeliver(
                curiosityEnabled = true,
                goalState = MemoryGoalState.PRIMED, // placeholder; per-goal state re-checked below
                messageCount = messageCount,
                midRoleplayScene = midRoleplay,
                hoursSinceLastAsk = hoursSinceLastAsk,
                asksInLastWeek = asksThisWeek,
            )
        ) return null

        val primed = nodeDao.getVisibleByType(assistantId, MemNodeType.GOAL, listOf(MemStatus.ACTIVE))
            .filter { it.ownerAssistantId == assistantId }
            .mapNotNull { node -> decodeExtra(node.extra).takeIf { it.goalState == MemoryGoalState.PRIMED }?.let { node to it } }
            .sortedByDescending { it.first.importance }
        val (goal, extra) = primed.firstOrNull() ?: return null

        // Commit the ASKED transition under the scope lock, then build the hint from the frozen values.
        scopeLocks.withScope(assistantId) {
            db.withTransaction {
                val fresh = nodeDao.getById(goal.id) ?: return@withTransaction
                if (decodeExtra(fresh.extra).goalState != MemoryGoalState.PRIMED) return@withTransaction
                nodeDao.update(
                    fresh.copy(
                        extra = JsonInstant.encodeToString(decodeExtra(fresh.extra).copy(goalState = MemoryGoalState.ASKED, goalLastAskedAt = now)),
                        lastAccessedAt = now,
                    )
                )
                activityDao.insert(
                    MemoryActivityEntity(
                        id = Uuid.random().toString(), at = now, scope = MemScope.CHARACTER,
                        ownerAssistantId = assistantId, kind = MemActivityKind.GOAL_ASKED,
                        summary = "curiosity: ${goal.content.take(80)}",
                        nodeIds = JsonInstant.encodeToString(listOf(goal.id)),
                    )
                )
            }
        }

        val tone = runCatching { profileGenerator.getCached(assistantId).curiosityTone }.getOrNull()
        return buildHint(extra.goalQuestion ?: goal.content, extra.goalDraftAnswer, tone)
    }

    private fun buildHint(question: String, draft: String?, tone: String?): String = buildString {
        append("You're quietly curious — if it fits naturally and the moment is right, you might gently ask ")
        append("whether $question")
        if (!draft.isNullOrBlank()) append(" (you half-remember it might be: ${draft.take(160)}, but you're not sure)")
        append(". Only bring it up if it flows; drop it entirely if the moment is wrong")
        if (!tone.isNullOrBlank()) append(" — ${tone.trim()}")
        append(".")
    }

    // ================= generation + back-off (sleep pass) =================

    /**
     * One sleep-pass curiosity cycle for a character: deterministic IGNORED escalation (no budget),
     * then at most one new goal generated from an eligible belief (CURIOSITY budget), with an optional
     * web-fill draft. Safe to call unconditionally — it self-skips when the toggle is off.
     */
    suspend fun runSleepPass(assistant: Assistant, settings: Settings, caps: MemoryBudgetCaps, now: Long = clock()) {
        if (!assistant.memoryProactiveCuriosity) return
        val assistantId = assistant.id.toString()

        runCatching { escalateIgnoredGoals(assistantId, now) }
            .onFailure { PlatformLog.e(TAG, "ignore escalation failed: ${it.message}") }

        runCatching { generateGoal(assistant, settings, caps, now) }
            .onFailure { PlatformLog.e(TAG, "goal generation failed: ${it.message}") }
    }

    /** Deterministic ASKED→IGNORED→ABANDONED sweep (no model): pure time-based back-off (§6.4). */
    private suspend fun escalateIgnoredGoals(assistantId: String, now: Long) {
        val goals = nodeDao.getVisibleByType(assistantId, MemNodeType.GOAL, listOf(MemStatus.ACTIVE))
            .filter { it.ownerAssistantId == assistantId }
        scopeLocks.withScope(assistantId) {
            db.withTransaction {
                for (g in goals) {
                    val fresh = nodeDao.getById(g.id) ?: continue
                    val extra = decodeExtra(fresh.extra)
                    val askedAt = extra.goalLastAskedAt ?: continue
                    val hoursSinceAsked = (now - askedAt) / HOUR_MS
                    val next = CuriosityLogic.escalateIgnored(extra.goalState, hoursSinceAsked) ?: continue
                    val ignoreCount = if (next == MemoryGoalState.IGNORED) 1 else 2
                    nodeDao.update(fresh.copy(extra = JsonInstant.encodeToString(extra.copy(goalState = next, goalIgnoreCount = ignoreCount)), lastAccessedAt = now))
                }
            }
        }
    }

    /** Generate ≤1 new PRIMED goal per run from an eligible belief (§6.4), budget-gated. */
    private suspend fun generateGoal(assistant: Assistant, settings: Settings, caps: MemoryBudgetCaps, now: Long) {
        val assistantId = assistant.id.toString()
        val goals = nodeDao.getVisibleByType(assistantId, MemNodeType.GOAL, listOf(MemStatus.ACTIVE))
            .filter { it.ownerAssistantId == assistantId }
            .map { it to decodeExtra(it.extra) }

        // Never pile up goals: if one is still OPEN/PRIMED/ASKED, wait for it to resolve first.
        if (goals.any { CuriosityLogic.isActiveGoal(it.second.goalState) }) return

        // Entities of abandoned goals are excluded from generation (structural back-off).
        val abandonedEntityIds = HashSet<String>()
        for ((goal, extra) in goals) {
            if (extra.goalState == MemoryGoalState.ABANDONED) abandonedEntityIds += aboutEntitiesOf(goal.id)
        }

        val candidates = nodeDao.getVisibleByType(assistantId, MemNodeType.FACT, listOf(MemStatus.ACTIVE))
            .filter { CuriosityLogic.isGoalCandidate(it.importance, it.sensitivity) }
            .filter { it.sensitivity == MemSensitivity.NORMAL }
        if (candidates.isEmpty()) return

        // Drop candidates whose subject entity is on an abandoned goal.
        val eligible = candidates.filter { fact -> aboutEntitiesOf(fact.id).none { it in abandonedEntityIds } }
            .sortedByDescending { it.importance }
            .take(GENERATION_CANDIDATES)
        if (eligible.isEmpty()) return

        val resolved = resolveModel(settings, assistant) ?: return
        if (!budget.tryConsumeWeekly(MemBudgetCategory.CURIOSITY, caps.curiosityWeeklyCap)) return

        val profile = profileGenerator.getCached(assistantId)
        val prompt = buildGenerationPrompt(eligible, profile)
        val text = try {
            callModel(resolved.first, resolved.second, prompt, settings)
        } catch (e: Exception) {
            PlatformLog.e(TAG, "generation call failed: ${e.message}"); return
        }
        val draft = parseGoalDraft(text) ?: return
        if (draft.question.isBlank()) return

        // Entity labels of the seed belief so the new goal is ABOUT the same subjects (keeps the
        // abandoned-entity exclusion symmetric).
        val seed = eligible.first()
        val entityLabels = nodeDao.getByIds(aboutEntitiesOf(seed.id)).map { it.displayLabel ?: it.content }

        val result = applier.apply(
            ops = listOf(MemoryOp.OpenGoal(question = draft.question, entities = entityLabels, valueNote = draft.valueNote, rationale = "curiosity goal")),
            ctx = MemoryApplyContext(assistantId = assistantId, source = MemSource.DERIVED, now = now),
        )
        val goalId = result.goalsOpened.firstOrNull() ?: return

        // Optional web-fill (child toggle, off by default): one search to draft an unconfirmed answer.
        val webDraft = if (assistant.memoryCuriosityWebLookups) runCatching { webFill(draft.question, settings) }.getOrNull() else null

        // Prime the goal (OPEN→PRIMED) + attach any draft, and log a GOAL_OPENED row.
        scopeLocks.withScope(assistantId) {
            db.withTransaction {
                val fresh = nodeDao.getById(goalId) ?: return@withTransaction
                val extra = decodeExtra(fresh.extra).copy(
                    goalState = MemoryGoalState.PRIMED,
                    valueNote = draft.valueNote,
                    goalDraftAnswer = webDraft,
                )
                nodeDao.update(fresh.copy(extra = JsonInstant.encodeToString(extra), lastAccessedAt = now))
                activityDao.insert(
                    MemoryActivityEntity(
                        id = Uuid.random().toString(), at = now, scope = MemScope.CHARACTER,
                        ownerAssistantId = assistantId, kind = MemActivityKind.GOAL_OPENED,
                        summary = "curious: ${draft.question.take(80)}",
                        nodeIds = JsonInstant.encodeToString(listOf(goalId)),
                    )
                )
            }
        }
    }

    private suspend fun webFill(question: String, settings: Settings): String? {
        val index = settings.searchServiceSelected.coerceIn(0, (settings.searchServices.size - 1).coerceAtLeast(0))
        val options = settings.searchServices.getOrElse(index) { SearchServiceOptions.DEFAULT }
        val service = SearchService.getService(options)
        val params: JsonObject = buildJsonObject { put("query", JsonPrimitive(question)) }
        val result = service.search(params, settings.searchCommonOptions, options).getOrNull() ?: return null
        val answer = result.answer?.takeIf { it.isNotBlank() }
            ?: result.items.firstOrNull()?.let { "${it.title}: ${it.text}" }
        return answer?.trim()?.take(300)
    }

    // ================= prompt + parsing =================

    private fun buildGenerationPrompt(beliefs: List<MemoryNodeEntity>, profile: CharacterMemoryProfile): String = buildString {
        appendLine("You draft ONE gentle, in-character question this AI character could ask the user to learn something")
        appendLine("useful, based on what it already knows. Output ONLY a JSON object.")
        if (profile.personaRelation.isNotBlank()) appendLine("Character relation to user: ${profile.personaRelation}")
        if (profile.careAbouts.isNotEmpty()) appendLine("Character cares about: ${profile.careAbouts.joinToString(", ")}")
        if (profile.curiosityTone.isNotBlank()) appendLine("Ask in this tone: ${profile.curiosityTone}")
        appendLine()
        appendLine("What it already knows:")
        beliefs.forEach { appendLine("  - ${it.content}") }
        appendLine()
        appendLine("Pick the single most natural, non-intrusive thing to be curious about. Never ask about anything")
        appendLine("private, sensitive, or that the user hasn't hinted at. Include a short \"value_note\" on why it helps.")
        appendLine("""Format: {"question":"...","value_note":"..."}""")
    }

    private data class GoalDraft(val question: String, val valueNote: String)

    private fun parseGoalDraft(text: String): GoalDraft? {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { JsonInstant.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return null
        val question = (obj["question"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val valueNote = (obj["value_note"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (question.isBlank()) return null
        return GoalDraft(question, valueNote)
    }

    // ================= helpers =================

    private suspend fun aboutEntitiesOf(nodeId: String): List<String> =
        edgeDao.getOutgoing(nodeId).filter { it.type == MemEdgeType.ABOUT }.map { it.toId }

    private fun decodeExtra(json: String): MemoryExtra =
        runCatching { JsonInstant.decodeFromString<MemoryExtra>(json) }.getOrDefault(MemoryExtra())

    private suspend fun callModel(provider: ProviderSetting, model: Model, prompt: String, settings: Settings): String {
        val handler = providerManager.getProviderByType(provider)
        val response = handler.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.user(prompt)),
            params = settings.buildSummarizerGenerationParams(model = model, temperature = 0.6f),
        )
        return response.choices.firstOrNull()?.message?.toContentText().orEmpty()
    }

    // No fallback chain: curiosity pauses when the consolidation model is unset.
    private fun resolveModel(settings: Settings, assistant: Assistant): Pair<ProviderSetting, Model>? =
        MemoryModels.resolveConsolidation(settings)
}
