package me.rerere.rikkahub.data.ai

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.MemorySummaryDao
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemorySummaryEntity
import me.rerere.rikkahub.data.memory.ALLOWED_SUMMARY_ICONS
import me.rerere.rikkahub.data.memory.DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT
import me.rerere.rikkahub.data.memory.DELTA_PATCH_SYSTEM_PROMPT
import me.rerere.rikkahub.data.memory.MemoryChangeType
import me.rerere.rikkahub.data.memory.MemoryPointerParser
import me.rerere.rikkahub.data.memory.MemorySectionPatch
import me.rerere.rikkahub.data.memory.MemorySummaryData
import me.rerere.rikkahub.data.memory.MemorySummaryMarkdownCodec
import me.rerere.rikkahub.data.memory.MemorySummarySection
import me.rerere.rikkahub.data.memory.RawMemoryAction
import me.rerere.rikkahub.data.memory.SectionLineEdit
import me.rerere.rikkahub.data.memory.SummaryPatchEngine
import me.rerere.rikkahub.data.memory.TokenEstimator
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.worker.MemorySummaryScheduler
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class SummarySnapshot(
    val data: MemorySummaryData,
    val revision: Long,
    val tokenCount: Int,
    val updatedAt: Long,
    val lastManualEditAt: Long,
)

data class SummaryToolMutationResult(
    val ok: Boolean,
    val message: String,
    val revision: Long? = null,
    val unverifiedCitedIds: List<Int> = emptyList(),
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("ok", ok)
        put("message", message)
        revision?.let { put("revision", it) }
        if (unverifiedCitedIds.isNotEmpty()) {
            putJsonArray("unverified_cited_ids") {
                unverifiedCitedIds.forEach { add(JsonPrimitive(it)) }
            }
            put("citation_warning", "Some cited [#ID] values do not exist in SQLite for this assistant.")
        }
    }
}

interface SummaryLlmExecutor {
    suspend fun completeJson(
        assistantId: String,
        systemPrompt: String,
        userPrompt: String,
        onProgress: ((stage: String, reasoning: String, delta: String) -> Unit)? = null,
    ): String
}

class DefaultSummaryLlmExecutor(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val secretKeyManager: SecretKeyManager,
) : SummaryLlmExecutor {
    override suspend fun completeJson(
        assistantId: String,
        systemPrompt: String,
        userPrompt: String,
        onProgress: ((stage: String, reasoning: String, delta: String) -> Unit)?,
    ): String {
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(assistantId)
            ?: throw IllegalStateException("Assistant $assistantId not found")
        val (model, provider) = resolveModelAndProvider(settings, assistant)
            ?: throw IllegalStateException("No valid model or provider with API key found for assistant ${assistant.name}")
        val providerHandler = providerManager.getProviderByType(provider)

        val messages = listOf(
            UIMessage.system(systemPrompt),
            UIMessage.user(userPrompt),
        )
        val params = settings.buildSummarizerGenerationParams(
            model = model,
            temperature = 0.2f,
        )

        val responseBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        onProgress?.invoke("Connecting to ${model.displayName}...", "", "")

        providerHandler.streamText(
            providerSetting = provider,
            messages = messages,
            params = params,
        ).collect { chunk ->
            var updatedReasoning = false
            var updatedText = false
            chunk.choices.firstOrNull()?.delta?.parts?.forEach { part ->
                when (part) {
                    is UIMessagePart.Reasoning -> {
                        reasoningBuilder.append(part.reasoning)
                        updatedReasoning = true
                    }
                    is UIMessagePart.Thinking -> {
                        reasoningBuilder.append(part.thinking)
                        updatedReasoning = true
                    }
                    is UIMessagePart.Text -> {
                        responseBuilder.append(part.text)
                        updatedText = true
                    }
                    else -> {}
                }
            }
            if (updatedReasoning || updatedText) {
                val stage = if (responseBuilder.isNotEmpty()) "Synthesizing summary sections..." else "Thinking & analyzing memories..."
                onProgress?.invoke(stage, reasoningBuilder.toString(), responseBuilder.toString())
            }
        }
        val rawResponse = responseBuilder.toString()
        if (rawResponse.isBlank()) {
            if (reasoningBuilder.isNotBlank()) {
                throw IllegalStateException("Model generated reasoning thoughts but no structured JSON content.")
            }
            throw IllegalStateException("Empty response received from model for assistant $assistantId")
        }
        return rawResponse
    }

    private fun resolveModelAndProvider(
        settings: Settings,
        assistant: Assistant,
    ): Pair<Model, ProviderSetting>? {
        val candidates = listOfNotNull(
            assistant.backgroundModelId,
            assistant.chatModelId,
            settings.summarizerModelId,
            settings.chatModelId,
        )
        for (candidateId in candidates) {
            val model = settings.findModelById(candidateId) ?: continue
            val rawProv = model.findProvider(settings.providers) ?: continue
            val populated = secretKeyManager.populateProviderSecrets(rawProv)
            return model to populated
        }
        for (prov in settings.providers) {
            if (!prov.enabled) continue
            val populated = secretKeyManager.populateProviderSecrets(prov)
            val model = prov.models.firstOrNull() ?: continue
            return model to populated
        }
        return null
    }
}

class MemorySummaryService(
    private val database: AppDatabase,
    private val memoryDAO: MemoryDAO,
    private val memorySummaryDao: MemorySummaryDao,
    private val settingsStore: SettingsStore,
    private val scheduler: MemorySummaryScheduler,
    private val llmExecutor: SummaryLlmExecutor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val assistantMutexes = ConcurrentHashMap<String, Mutex>()
    private val rawSyncSuppression = ConcurrentHashMap<String, AtomicInteger>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private fun mutexFor(assistantId: String): Mutex =
        assistantMutexes.computeIfAbsent(assistantId) { Mutex() }

    private suspend inline fun <T> withRawSyncSuppressed(assistantId: String, block: () -> T): T {
        val counter = rawSyncSuppression.computeIfAbsent(assistantId) { AtomicInteger(0) }
        counter.incrementAndGet()
        return try {
            block()
        } finally {
            counter.decrementAndGet()
        }
    }

    private fun isRawSyncSuppressed(assistantId: String): Boolean =
        (rawSyncSuppression[assistantId]?.get() ?: 0) > 0

    /**
     * Entry point called whenever raw memories are created, edited, or deleted.
     * Replaces ephemeral coroutines and empty stubs with persistent WorkManager scheduling.
     */
    fun onMemoryChanged(
        assistantId: String,
        changeType: MemoryChangeType,
        memoryContent: String,
    ) {
        if (isRawSyncSuppressed(assistantId)) return
        scope.launch {
            try {
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(assistantId) ?: return@launch
                if (!assistant.enableMemory || !assistant.enableMemorySummary) return@launch

                val now = System.currentTimeMillis()
                memorySummaryDao.markDirty(assistantId = assistantId, dirtySince = now)

                scheduler.onMemoryChanged(
                    assistantId = assistantId,
                    cadence = assistant.memorySummaryCadence,
                    changeType = changeType,
                    snippet = memoryContent.take(1_500),
                    triggeredAtMs = now,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "onMemoryChanged scheduling failed for $assistantId", e)
            }
        }
    }

    suspend fun readSummarySnapshot(assistantId: String): SummarySnapshot = withContext(Dispatchers.IO) {
        val entity = memorySummaryDao.getSummaryOnce(assistantId)
        val parsed = entity?.let(::decodeSummaryEntity) ?: MemorySummaryData()
        SummarySnapshot(
            data = parsed,
            revision = entity?.revision ?: 0L,
            tokenCount = entity?.tokenCount ?: 0,
            updatedAt = entity?.updatedAt ?: 0L,
            lastManualEditAt = entity?.lastManualEditAt ?: 0L,
        )
    }

    fun getSummaryFlow(assistantId: String): Flow<MemorySummaryEntity?> =
        memorySummaryDao.getSummaryFlow(assistantId)

    suspend fun getSummary(assistantId: String): MemorySummaryEntity? =
        memorySummaryDao.getSummary(assistantId)

    /**
     * Interactive Ask/Update from MemorySummaryPage.
     * Snapshots revision BEFORE network I/O (never holds Mutex across LLM calls),
     * then commits atomically under optimistic locking.
     */
    suspend fun askOrUpdate(
        assistantId: String,
        userInstruction: String,
        onProgress: ((stage: String, reasoning: String, delta: String) -> Unit)? = null,
    ): Result<Pair<String, Boolean>> = runCatching {
        val snapshot = readSummarySnapshot(assistantId)
        val memories = withContext(Dispatchers.IO) { memoryDAO.getMemoriesByAssistant(assistantId) }

        val userPrompt = buildDeltaPrompt(
            currentSummary = snapshot.data,
            memories = memories,
            instruction = userInstruction,
        )

        onProgress?.invoke("Processing instruction with assistant model...", "", "")
        val rawResponse = llmExecutor.completeJson(
            assistantId = assistantId,
            systemPrompt = DELTA_PATCH_SYSTEM_PROMPT,
            userPrompt = userPrompt,
            onProgress = onProgress,
        )

        // 1. Try parsing as a structured patch
        val patch = runCatching { parsePatchJson(rawResponse) }.getOrNull()
        if (patch != null && (patch.operations.isNotEmpty() || patch.rawMemoryUpdates.isNotEmpty() || patch.action.isNotBlank())) {
            val allowFullRewrite = SummaryPatchEngine.userExplicitlyRequestedFullRewrite(userInstruction)

            if (patch.action.equals("no_op", ignoreCase = true) && patch.rawMemoryUpdates.isEmpty()) {
                return@runCatching (patch.assistantReply ?: "No changes were required.") to false
            }

            return@runCatching mutexFor(assistantId).withLock {
                database.withTransaction {
                    val latestEntity = memorySummaryDao.getSummaryOnce(assistantId)
                    val latestData = latestEntity?.let(::decodeSummaryEntity) ?: snapshot.data
                    val baseRevision = latestEntity?.revision ?: 0L

                    val deletedIds = applyRawMemoryUpdates(assistantId, patch.rawMemoryUpdates)

                    var applyResult = SummaryPatchEngine.applyPatch(
                        current = latestData,
                        patch = patch,
                        allowFullRewrite = allowFullRewrite,
                    )

                    if (deletedIds.isNotEmpty()) {
                        applyResult = applyResult.copy(
                            updated = prunePointerIdsFromSummary(applyResult.updated, deletedIds)
                        )
                    }

                    if (applyResult.modifiedSectionKeys.isNotEmpty() || deletedIds.isNotEmpty()) {
                        commitSummaryLocked(
                            assistantId = assistantId,
                            data = applyResult.updated,
                            expectedRevision = baseRevision,
                            isManualEdit = false,
                        )
                    }

                    val reply = patch.assistantReply?.takeIf { it.isNotBlank() }
                        ?: if (applyResult.modifiedSectionKeys.isNotEmpty()) {
                            "Updated ${applyResult.modifiedSectionKeys.size} section(s)."
                        } else {
                            "Summary is already up to date."
                        }
                    reply to (applyResult.modifiedSectionKeys.isNotEmpty() || deletedIds.isNotEmpty())
                }
            }
        }

        // 2. Try parsing as full replacement MemorySummaryData
        val fullData = runCatching { parseSummaryJson(rawResponse) }.getOrNull()
        if (fullData != null && fullData.sections.isNotEmpty()) {
            val nextRev = updateSummaryInteractive(assistantId, fullData, snapshot.revision)
            return@runCatching "Updated ${fullData.sections.size} section(s) in summary (rev $nextRev)." to true
        }

        // 3. Conversational answer to user question
        val answer = rawResponse.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        answer to false
    }

    /**
     * Direct user manual edit from MemorySummaryPage Pencil Mode.
     * Always increments revision and records lastManualEditAt so stale background jobs yield.
     */
    suspend fun saveManualEdit(
        assistantId: String,
        editedMarkdown: String,
        editedTitle: String,
        editedSubtitle: String,
    ): SummarySnapshot = mutexFor(assistantId).withLock {
        database.withTransaction {
            val existing = memorySummaryDao.getSummaryOnce(assistantId)
            val base = existing?.let(::decodeSummaryEntity) ?: MemorySummaryData()
            val updated = MemorySummaryMarkdownCodec.decodeSectionsFromMarkdown(
                markdown = editedMarkdown,
                base = base,
                editedTitle = editedTitle,
                editedSubtitle = editedSubtitle,
            )
            val nextRevision = commitSummaryLocked(
                assistantId = assistantId,
                data = updated,
                expectedRevision = existing?.revision ?: 0L,
                isManualEdit = true,
            )
            val now = System.currentTimeMillis()
            val md = MemorySummaryMarkdownCodec.encodeFullDocument(updated)
            SummarySnapshot(
                data = updated,
                revision = nextRevision,
                tokenCount = TokenEstimator.estimate(md),
                updatedAt = now,
                lastManualEditAt = now,
            )
        }
    }

    /**
     * Surgically removes a stale/deleted pointer ID from the summary without invoking an LLM.
     */
    suspend fun removeStaleCitation(assistantId: String, deletedMemoryId: Int): Boolean =
        mutexFor(assistantId).withLock {
            database.withTransaction {
                val existing = memorySummaryDao.getSummaryOnce(assistantId) ?: return@withTransaction false
                val current = decodeSummaryEntity(existing)
                val cleaned = prunePointerIdsFromSummary(current, setOf(deletedMemoryId))
                if (cleaned == current) return@withTransaction false
                commitSummaryLocked(
                    assistantId = assistantId,
                    data = cleaned,
                    expectedRevision = existing.revision,
                    isManualEdit = true,
                )
                true
            }
        }

    // --- Conversational Agent Tool Entry Points (manage_memory_summary) ---

    suspend fun agentUpdateSection(
        assistantId: String,
        sectionTitle: String,
        newTitle: String?,
        icon: String?,
        findText: String?,
        replaceText: String?,
        appendLine: Boolean,
        fullSectionContent: String?,
    ): SummaryToolMutationResult = mutexFor(assistantId).withLock {
        val existing = memorySummaryDao.getSummaryOnce(assistantId)
        val current = existing?.let(::decodeSummaryEntity) ?: MemorySummaryData()

        val lineEdits = buildList {
            when {
                appendLine && !replaceText.isNullOrBlank() ->
                    add(SectionLineEdit(op = "append", replace = replaceText))
                !findText.isNullOrBlank() ->
                    add(SectionLineEdit(op = "replace", find = findText, replace = replaceText.orEmpty()))
            }
        }

        val patch = MemorySectionPatch(
            action = "update_section",
            targetSection = sectionTitle,
            section = MemorySummarySection(
                title = newTitle?.takeIf { it.isNotBlank() } ?: sectionTitle,
                icon = icon?.takeIf { it in ALLOWED_SUMMARY_ICONS } ?: "star",
                content = fullSectionContent.orEmpty(),
            ),
            lineEdits = lineEdits,
        )

        val result = SummaryPatchEngine.applyPatch(current, patch, allowFullRewrite = false)
        if (result.modifiedSectionKeys.isEmpty()) {
            return@withLock SummaryToolMutationResult(
                ok = false,
                message = result.warnings.joinToString("; ").ifBlank { "No matching section modified." },
                revision = existing?.revision,
            )
        }

        val unverified = verifyCitedIds(assistantId, result.updated)
        val nextRev = database.withTransaction {
            commitSummaryLocked(
                assistantId = assistantId,
                data = result.updated,
                expectedRevision = existing?.revision ?: 0L,
                isManualEdit = false,
            )
        }
        SummaryToolMutationResult(
            ok = true,
            message = "Section '$sectionTitle' updated.",
            revision = nextRev,
            unverifiedCitedIds = unverified,
        )
    }

    suspend fun agentAddSection(
        assistantId: String,
        sectionTitle: String,
        icon: String,
        content: String,
    ): SummaryToolMutationResult = mutexFor(assistantId).withLock {
        val existing = memorySummaryDao.getSummaryOnce(assistantId)
        val current = existing?.let(::decodeSummaryEntity) ?: MemorySummaryData()
        val patch = MemorySectionPatch(
            action = "add_section",
            section = MemorySummarySection(title = sectionTitle, icon = icon, content = content),
        )
        val result = SummaryPatchEngine.applyPatch(current, patch, allowFullRewrite = false)
        val unverified = verifyCitedIds(assistantId, result.updated)
        val nextRev = database.withTransaction {
            commitSummaryLocked(assistantId, result.updated, existing?.revision ?: 0L, isManualEdit = false)
        }
        SummaryToolMutationResult(
            ok = true,
            message = "Section '$sectionTitle' added.",
            revision = nextRev,
            unverifiedCitedIds = unverified,
        )
    }

    suspend fun agentDeleteSection(
        assistantId: String,
        sectionTitle: String,
    ): SummaryToolMutationResult = mutexFor(assistantId).withLock {
        val existing = memorySummaryDao.getSummaryOnce(assistantId)
        val current = existing?.let(::decodeSummaryEntity) ?: return@withLock SummaryToolMutationResult(
            ok = false,
            message = "Summary does not exist yet.",
        )
        val patch = MemorySectionPatch(action = "delete_section", targetSection = sectionTitle)
        val result = SummaryPatchEngine.applyPatch(current, patch, allowFullRewrite = false)
        if (result.modifiedSectionKeys.isEmpty()) {
            return@withLock SummaryToolMutationResult(
                ok = false,
                message = result.warnings.joinToString("; ").ifBlank { "Section '$sectionTitle' not found." },
                revision = existing.revision,
            )
        }
        val nextRev = database.withTransaction {
            commitSummaryLocked(assistantId, result.updated, existing.revision, isManualEdit = false)
        }
        SummaryToolMutationResult(ok = true, message = "Section '$sectionTitle' removed.", revision = nextRev)
    }

    suspend fun agentReplaceSummary(
        assistantId: String,
        markdown: String,
    ): SummaryToolMutationResult = mutexFor(assistantId).withLock {
        val existing = memorySummaryDao.getSummaryOnce(assistantId)
        val base = existing?.let(::decodeSummaryEntity) ?: MemorySummaryData()
        val replaced = MemorySummaryMarkdownCodec.decodeSectionsFromMarkdown(markdown, base)
        if (replaced.sections.isEmpty()) {
            return@withLock SummaryToolMutationResult(
                ok = false,
                message = "Replacement markdown contained zero valid '##' sections.",
                revision = existing?.revision,
            )
        }
        val unverified = verifyCitedIds(assistantId, replaced)
        val nextRev = database.withTransaction {
            commitSummaryLocked(assistantId, replaced, existing?.revision ?: 0L, isManualEdit = false)
        }
        SummaryToolMutationResult(
            ok = true,
            message = "Summary replaced (${replaced.sections.size} sections).",
            revision = nextRev,
            unverifiedCitedIds = unverified,
        )
    }

    /**
     * Re-synthesizes the entire memory baseline from all currently stored memories.
     */
    suspend fun fullRegenerate(
        assistantId: String,
        onProgress: ((stage: String, reasoning: String, delta: String) -> Unit)? = null,
    ): Result<MemorySummaryData> = runCatching {
        val memories = withContext(Dispatchers.IO) { memoryDAO.getMemoriesByAssistant(assistantId) }
        if (memories.isEmpty()) {
            val emptyData = MemorySummaryData(
                title = "Your Summary",
                subtitle = "No memories recorded yet.",
                sections = emptyList(),
                diveDeeper = emptyList(),
            )
            mutexFor(assistantId).withLock {
                database.withTransaction {
                    val existing = memorySummaryDao.getSummaryOnce(assistantId)
                    commitSummaryLocked(
                        assistantId = assistantId,
                        data = emptyData,
                        expectedRevision = existing?.revision ?: 0L,
                        isManualEdit = false,
                    )
                }
            }
            return@runCatching emptyData
        }

        onProgress?.invoke("Preparing ${memories.size} raw memories for synthesis...", "", "")
        val rawFull = llmExecutor.completeJson(
            assistantId = assistantId,
            systemPrompt = DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT,
            userPrompt = buildFullSynthesisPrompt(memories),
            onProgress = onProgress,
        )
        onProgress?.invoke("Structuring sections and verifying provenance pointers...", "", "")
        val parsed = parseSummaryJson(rawFull)
        val hydrated = MemoryPointerParser.hydrateProvenance(parsed)

        mutexFor(assistantId).withLock {
            database.withTransaction {
                val existing = memorySummaryDao.getSummaryOnce(assistantId)
                commitSummaryLocked(
                    assistantId = assistantId,
                    data = hydrated,
                    expectedRevision = existing?.revision ?: 0L,
                    isManualEdit = false,
                )
            }
        }
        hydrated
    }

    /**
     * Invoked by MemorySummaryWorker for background incremental or full consolidation.
     * Yields safely if the user saved a manual edit after jobStartedAtMs.
     */
    suspend fun runBackgroundConsolidation(
        assistantId: String,
        incrementalOnly: Boolean,
        changeSnippet: String?,
        jobStartedAtMs: Long,
    ): Boolean {
        val entityBefore = withContext(Dispatchers.IO) { memorySummaryDao.getSummaryOnce(assistantId) }
        val baseRevision = entityBefore?.revision ?: 0L
        val memories = withContext(Dispatchers.IO) { memoryDAO.getMemoriesByAssistant(assistantId) }
        if (memories.isEmpty()) return true

        val existingData = entityBefore?.let(::decodeSummaryEntity)
        val useIncremental = incrementalOnly && existingData != null && existingData.sections.isNotEmpty()

        val synthesized: MemorySummaryData = if (useIncremental) {
            val deltaInstruction = buildString {
                appendLine("Incorporate recent memory updates into the existing summary using surgical section patches.")
                if (!changeSnippet.isNullOrBlank()) {
                    appendLine("Recent change context: $changeSnippet")
                }
            }
            val rawPatch = llmExecutor.completeJson(
                assistantId = assistantId,
                systemPrompt = DELTA_PATCH_SYSTEM_PROMPT,
                userPrompt = buildDeltaPrompt(existingData!!, memories, deltaInstruction),
            )
            val patch = parsePatchJson(rawPatch)
            SummaryPatchEngine.applyPatch(existingData, patch, allowFullRewrite = false).updated
        } else {
            val rawFull = llmExecutor.completeJson(
                assistantId = assistantId,
                systemPrompt = DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT,
                userPrompt = buildFullSynthesisPrompt(memories),
            )
            MemoryPointerParser.hydrateProvenance(parseSummaryJson(rawFull))
        }

        // Optimistic lock + manual-edit precedence check
        return mutexFor(assistantId).withLock {
            database.withTransaction {
                val latest = memorySummaryDao.getSummaryOnce(assistantId)
                if (latest != null && latest.lastManualEditAt >= jobStartedAtMs) {
                    Log.i(TAG, "Background consolidation yielded to newer manual edit for $assistantId")
                    memorySummaryDao.clearDirty(assistantId, System.currentTimeMillis())
                    return@withTransaction true
                }
                if (latest != null && latest.revision != baseRevision) {
                    Log.i(TAG, "Revision conflict detected (expected $baseRevision, actual ${latest.revision}); deferring.")
                    return@withTransaction false
                }
                commitSummaryLocked(
                    assistantId = assistantId,
                    data = synthesized,
                    expectedRevision = baseRevision,
                    isManualEdit = false,
                )
                true
            }
        }
    }

    private suspend fun commitSummaryLocked(
        assistantId: String,
        data: MemorySummaryData,
        expectedRevision: Long,
        isManualEdit: Boolean,
    ): Long {
        val hydrated = MemoryPointerParser.hydrateProvenance(data)
        val jsonStr = json.encodeToString(hydrated)
        val markdown = MemorySummaryMarkdownCodec.encodeFullDocument(hydrated)
        val tokenCount = TokenEstimator.estimate(markdown)
        val now = System.currentTimeMillis()

        val existing = memorySummaryDao.getSummaryOnce(assistantId)
        return if (existing == null) {
            memorySummaryDao.upsert(
                MemorySummaryEntity(
                    assistantId = assistantId,
                    summaryJson = jsonStr,
                    rawMarkdown = markdown,
                    updatedAt = now,
                    tokenCount = tokenCount,
                    revision = 1L,
                    lastManualEditAt = if (isManualEdit) now else 0L,
                    dirtySince = 0L,
                    lastSyncedAt = now,
                )
            )
            1L
        } else {
            val nextRev = expectedRevision + 1L
            val updatedRows = memorySummaryDao.updateIfRevisionMatches(
                assistantId = assistantId,
                summaryJson = jsonStr,
                rawMarkdown = markdown,
                updatedAt = now,
                tokenCount = tokenCount,
                expectedRevision = expectedRevision,
                lastManualEditAt = if (isManualEdit) now else existing.lastManualEditAt,
                lastSyncedAt = now,
            )
            if (updatedRows == 0) {
                throw ConcurrentSummaryModificationException(
                    "Concurrent summary update for $assistantId (expected rev $expectedRevision)."
                )
            }
            nextRev
        }
    }

    private suspend fun applyRawMemoryUpdates(
        assistantId: String,
        actions: List<RawMemoryAction>,
    ): Set<Int> {
        if (actions.isEmpty()) return emptySet()
        val deletedIds = linkedSetOf<Int>()
        withRawSyncSuppressed(assistantId) {
            for (item in actions) {
                when (item.action.lowercase()) {
                    "create" -> item.content?.takeIf { it.isNotBlank() }?.let { content ->
                        memoryDAO.insert(MemoryEntity(assistantId = assistantId, content = content))
                    }
                    "update" -> {
                        val id = item.id
                        val content = item.content
                        if (id != null && !content.isNullOrBlank()) {
                            memoryDAO.updateContentById(assistantId, id, content)
                        }
                    }
                    "delete" -> item.id?.let { id ->
                        memoryDAO.deleteById(assistantId, id)
                        deletedIds.add(id)
                    }
                }
            }
        }
        return deletedIds
    }

    private suspend fun verifyCitedIds(assistantId: String, data: MemorySummaryData): List<Int> {
        val cited = data.sections.flatMap { it.citedMemoryIds }.distinct()
        if (cited.isEmpty()) return emptyList()
        val existing = withContext(Dispatchers.IO) {
            cited.chunked(500).flatMap { chunk -> memoryDAO.getExistingIds(assistantId, chunk) }.toSet()
        }
        return cited.filter { it !in existing }
    }

    private fun prunePointerIdsFromSummary(data: MemorySummaryData, deletedIds: Set<Int>): MemorySummaryData {
        val cleanedSections = data.sections.map { section ->
            var text = section.content
            for (id in deletedIds) {
                text = MemoryPointerParser.removePointerId(text, id)
            }
            section.copy(content = text)
        }
        return MemoryPointerParser.hydrateProvenance(data.copy(sections = cleanedSections))
    }

    private fun buildFullSynthesisPrompt(memories: List<MemoryEntity>): String = buildString {
        appendLine("<memories>")
        for (m in memories) {
            val iso = if (m.createdAt > 0L) {
                DATE_FMT.format(Instant.ofEpochMilli(m.createdAt))
            } else null
            appendLine(MemoryPointerParser.formatForIngestion(m.id, m.content, iso))
        }
        appendLine("</memories>")
        appendLine(
            """
            Synthesize the user's raw memories into a structured Hierarchical Pointer Index JSON object.
            Requirements:
            1. Every factual statement or bullet MUST end with its backing citation pointer: [#ID] or [#ID1, #ID2].
            2. Assign each section an icon strictly from: "star", "heart", "code", "target", "devices", "sparkles", "profile", "book", "music".
               - Use 'heart' for passions, hobbies, relationships, entertainment.
               - Use 'devices' for hardware, gadgets, specs.
               - Use 'code' for programming, tools, dev environment.
               - Use 'star' for core identity, life goals.
            3. Respond ONLY with valid JSON matching this schema:
            {
              "title": "Your Summary",
              "subtitle": "Brief overview of what I know about you.",
              "sections": [
                {
                  "icon": "heart",
                  "title": "Interests & Passions",
                  "content": "- Enjoys science fiction and classical literature [#12]\n- Passionate about open-source mobile tools [#15]"
                }
              ],
              "dive_deeper": [
                {
                  "prompt": "What are my current goals?",
                  "context": "Goals"
                }
              ]
            }
            """.trimIndent()
        )
    }

    private fun buildDeltaPrompt(
        currentSummary: MemorySummaryData,
        memories: List<MemoryEntity>,
        instruction: String,
    ): String = buildString {
        appendLine("<current_summary_sections>")
        currentSummary.sections.forEachIndexed { idx, sec ->
            appendLine("[SECTION $idx] title=\"${sec.title}\" icon=\"${sec.icon}\"")
            appendLine(sec.content)
            appendLine()
        }
        appendLine("</current_summary_sections>")
        appendLine("<available_raw_memories>")
        for (m in memories.takeLast(120)) {
            val iso = if (m.createdAt > 0L) DATE_FMT.format(Instant.ofEpochMilli(m.createdAt)) else null
            appendLine(MemoryPointerParser.formatForIngestion(m.id, m.content, iso))
        }
        appendLine("</available_raw_memories>")
        appendLine("<instruction>")
        appendLine(instruction)
        appendLine("</instruction>")
        appendLine(
            """
            Respond ONLY with a JSON object. You may return:
            1. An updated MemorySummaryData object:
               {"title": "...", "subtitle": "...", "sections": [{"icon": "heart", "title": "...", "content": "..."}], "dive_deeper": [...]}
            OR
            2. A MemorySectionPatch object:
               {"operations": [...], "dive_deeper": [...]}
            Ensure all cited claims retain or add exact [#ID] pointers.
            """.trimIndent()
        )
    }

    fun decodeSummaryEntity(entity: MemorySummaryEntity): MemorySummaryData {
        if (entity.summaryJson.isNotBlank()) {
            runCatching {
                return MemoryPointerParser.hydrateProvenance(json.decodeFromString<MemorySummaryData>(entity.summaryJson))
            }
        }
        return MemorySummaryMarkdownCodec.decodeSectionsFromMarkdown(entity.rawMarkdown, MemorySummaryData())
    }

    private fun parsePatchJson(raw: String): MemorySectionPatch =
        json.decodeFromString(extractJsonObject(raw))

    private fun parseSummaryJson(raw: String): MemorySummaryData {
        val cleaned = extractJsonObject(raw)
        return parseMemorySummaryJson(cleaned)
            ?: parseMemorySummaryJson(raw)
            ?: runCatching { json.decodeFromString<MemorySummaryData>(cleaned) }.getOrNull()
            ?: MemorySummaryMarkdownCodec.decodeSectionsFromMarkdown(raw, MemorySummaryData())
    }

    private fun extractJsonObject(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start in 0 until end) cleaned.substring(start, end + 1) else cleaned
    }

    companion object {
        private const val TAG = "MemorySummaryService"
        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
    }
}

class ConcurrentSummaryModificationException(message: String) : IllegalStateException(message)
