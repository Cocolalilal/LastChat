package me.rerere.rikkahub.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.memory.MemoryVectorMath
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.dao.MemorySummaryDao
import me.rerere.rikkahub.data.db.entity.MemorySummaryEntity
import me.rerere.rikkahub.data.memory.DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT
import me.rerere.rikkahub.data.memory.MemoryChangeType
import me.rerere.rikkahub.data.memory.MemorySummaryCadence
import me.rerere.rikkahub.data.memory.MemorySummaryData
import me.rerere.rikkahub.data.memory.buildAskOrUpdatePrompt
import me.rerere.rikkahub.data.memory.buildFullSummarizerUserPrompt
import me.rerere.rikkahub.data.memory.buildIncrementalSummarizerPrompt
import me.rerere.rikkahub.data.memory.parseMemorySummaryJson
import me.rerere.rikkahub.data.memory.parseAskOrUpdateResponse
import me.rerere.rikkahub.data.memory.renderMemorySummaryToMarkdown
import me.rerere.rikkahub.data.memory.sanitizeMemorySummaryData
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.MemoryRepository
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "MemorySummaryService"

class MemorySummaryService(
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val memorySummaryDao: MemorySummaryDao,
    private val providerManager: ProviderManager,
    private val secretKeyManager: SecretKeyManager,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val updateMutexes = ConcurrentHashMap<String, Mutex>()
    private val debounceJobs = ConcurrentHashMap<String, Job>()
    @Volatile
    private var isSyncingRawUpdates = false

    private fun getMutex(assistantId: String): Mutex {
        return updateMutexes.computeIfAbsent(assistantId) { Mutex() }
    }

    private fun resolveModelAndProvider(
        settings: Settings,
        assistant: Assistant,
    ): Pair<Model, ProviderSetting>? {
        val model = settings.findModelById(settings.summarizerModelId)
            ?: settings.findModelById(assistant.backgroundModelId)
            ?: settings.findModelById(assistant.chatModelId)
            ?: settings.findModelById(settings.chatModelId)
            ?: return null
        val rawProvider = model.findProvider(settings.providers) ?: return null
        val populatedProvider = secretKeyManager.populateProviderSecrets(rawProvider)
        return model to populatedProvider
    }

    fun getSummaryFlow(assistantId: String): Flow<MemorySummaryEntity?> {
        return memorySummaryDao.getSummaryFlow(assistantId)
    }

    suspend fun getSummary(assistantId: String): MemorySummaryEntity? {
        return memorySummaryDao.getSummary(assistantId)
    }

    /**
     * Re-synthesizes the entire memory baseline from all currently stored memories.
     */
    suspend fun fullRegenerate(assistantId: String): Result<MemorySummaryData> {
        return getMutex(assistantId).withLock {
            try {
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(assistantId)
                    ?: return Result.failure(IllegalStateException("Assistant $assistantId not found"))

                val allMemories = memoryRepository.getCombinedMemoriesOfAssistant(assistantId)
                if (allMemories.isEmpty()) {
                    val emptyData = MemorySummaryData(
                        title = "Your Summary",
                        subtitle = "No memories recorded yet.",
                        sections = emptyList(),
                        diveDeeper = emptyList(),
                    )
                    persistSummary(assistantId, emptyData, 0)
                    return Result.success(emptyData)
                }

                val (model, provider) = resolveModelAndProvider(settings, assistant)
                    ?: return Result.failure(IllegalStateException("No valid model or provider with API key found for assistant ${assistant.name}"))
                val providerHandler = providerManager.getProviderByType(provider)

                val tokenBudget = assistant.memorySummaryTokenBudget.coerceIn(200, 10000)
                val systemPrompt = assistant.customMemorySummaryPrompt
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT

                val userPrompt = buildFullSummarizerUserPrompt(
                    memories = allMemories.map { it.content },
                    tokenBudget = tokenBudget,
                )

                val response = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(
                        UIMessage.system(systemPrompt),
                        UIMessage.user(userPrompt),
                    ),
                    params = settings.buildSummarizerGenerationParams(
                        model = model,
                        temperature = 0.3f,
                    ),
                )

                val rawResponse = response.choices.firstOrNull()?.message?.toContentText()
                    ?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(IllegalStateException("Empty summarizer response"))

                val parsed = parseMemorySummaryJson(rawResponse)
                    ?: return Result.failure(IllegalStateException("Could not parse summarizer response"))

                val renderedMd = renderMemorySummaryToMarkdown(parsed)
                val tokenCount = ContextTokenEstimator.textTokens(renderedMd, model)
                persistSummary(assistantId, parsed, tokenCount)

                Result.success(parsed)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "fullRegenerate failed for $assistantId", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Incrementally patches the summary when a single memory is created, updated, or deleted.
     */
    suspend fun applyIncrementalUpdate(
        assistantId: String,
        changeType: MemoryChangeType,
        memoryContent: String,
    ): Result<MemorySummaryData> {
        return getMutex(assistantId).withLock {
            try {
                val existingEntity = memorySummaryDao.getSummary(assistantId)
                if (existingEntity == null || existingEntity.summaryJson.isBlank()) {
                    return@withLock fullRegenerateInternal(assistantId)
                }

                val currentSummary = parseMemorySummaryJson(existingEntity.summaryJson)
                    ?: return@withLock fullRegenerateInternal(assistantId)

                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(assistantId)
                    ?: return Result.failure(IllegalStateException("Assistant $assistantId not found"))

                val (model, provider) = resolveModelAndProvider(settings, assistant)
                    ?: return Result.failure(IllegalStateException("No valid model or provider with API key found for assistant ${assistant.name}"))
                val providerHandler = providerManager.getProviderByType(provider)

                val tokenBudget = assistant.memorySummaryTokenBudget.coerceIn(200, 10000)
                val systemPrompt = assistant.customMemorySummaryPrompt
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT

                val userPrompt = buildIncrementalSummarizerPrompt(
                    currentSummary = currentSummary,
                    changeType = changeType,
                    memoryContent = memoryContent,
                    tokenBudget = tokenBudget,
                )

                val response = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(
                        UIMessage.system(systemPrompt),
                        UIMessage.user(userPrompt),
                    ),
                    params = settings.buildSummarizerGenerationParams(
                        model = model,
                        temperature = 0.2f,
                    ),
                )

                val rawResponse = response.choices.firstOrNull()?.message?.toContentText()
                    ?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(IllegalStateException("Empty incremental update response"))

                val parsed = parseMemorySummaryJson(rawResponse)
                    ?: return Result.failure(IllegalStateException("Could not parse incremental response"))

                val renderedMd = renderMemorySummaryToMarkdown(parsed)
                val tokenCount = ContextTokenEstimator.textTokens(renderedMd, model)
                persistSummary(assistantId, parsed, tokenCount)

                Result.success(parsed)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "applyIncrementalUpdate failed for $assistantId", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Applies a conversational instruction from the "Ask or update" bottom bar.
     */
    suspend fun askOrUpdate(
        assistantId: String,
        userInstruction: String,
    ): Result<MemorySummaryData> {
        return getMutex(assistantId).withLock {
            try {
                val existingEntity = memorySummaryDao.getSummary(assistantId)
                val currentSummary = existingEntity?.summaryJson?.let { parseMemorySummaryJson(it) }
                    ?: run {
                        val regenerated = fullRegenerateInternal(assistantId)
                        if (regenerated.isFailure) return regenerated
                        regenerated.getOrThrow()
                    }

                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(assistantId)
                    ?: return Result.failure(IllegalStateException("Assistant $assistantId not found"))

                val (model, provider) = resolveModelAndProvider(settings, assistant)
                    ?: return Result.failure(IllegalStateException("No valid model or provider with API key found for assistant ${assistant.name}"))
                val providerHandler = providerManager.getProviderByType(provider)

                // 1. Perform fast, offline keyword retrieval over local memories (no external embedding call)
                val allMemories = memoryRepository.getCombinedMemoriesOfAssistant(assistantId)
                val relevantMemories = allMemories
                    .map { it to MemoryVectorMath.keywordScore(userInstruction, it.content) }
                    .filter { it.second > 0f }
                    .sortedByDescending { it.second }
                    .take(6)
                    .map { it.first.content }

                val tokenBudget = assistant.memorySummaryTokenBudget.coerceIn(200, 10000)
                val systemPrompt = assistant.customMemorySummaryPrompt
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT

                val userPrompt = buildAskOrUpdatePrompt(
                    currentSummary = currentSummary,
                    userInstruction = userInstruction,
                    tokenBudget = tokenBudget,
                    relevantMemories = relevantMemories,
                )

                val response = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(
                        UIMessage.system(systemPrompt),
                        UIMessage.user(userPrompt),
                    ),
                    params = settings.buildSummarizerGenerationParams(
                        model = model,
                        temperature = 0.3f,
                    ),
                )

                val rawResponse = response.choices.firstOrNull()?.message?.toContentText()
                    ?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(IllegalStateException("Empty askOrUpdate response from model"))

                Log.d(TAG, "askOrUpdate raw model response: $rawResponse")

                val (updatedSummary, rawUpdates) = parseAskOrUpdateResponse(rawResponse, currentSummary)
                    ?: return Result.failure(IllegalStateException("Could not parse askOrUpdate response: ${rawResponse.take(150)}"))

                // 2. Synchronize raw memory updates to SQLite database if specified (using offline keyword matching)
                if (rawUpdates.isNotEmpty()) {
                    isSyncingRawUpdates = true
                    try {
                        val assistantMemories = memoryRepository.getMemoriesOfAssistant(assistantId)
                        for (update in rawUpdates) {
                            when (update.action.lowercase()) {
                                "create", "add" -> {
                                    val content = update.content?.trim()
                                    if (!content.isNullOrBlank()) {
                                        memoryRepository.addMemory(assistantId, content)
                                    }
                                }
                                "update", "modify" -> {
                                    val content = update.content?.trim()
                                    val search = update.search?.trim() ?: content
                                    if (!content.isNullOrBlank() && !search.isNullOrBlank()) {
                                        val target = assistantMemories
                                            .map { it to MemoryVectorMath.keywordScore(search, it.content) }
                                            .filter { it.second > 0f }
                                            .maxByOrNull { it.second }?.first

                                        if (target != null && target.id > 0) {
                                            memoryRepository.updateContent(target.id, content)
                                        } else {
                                            memoryRepository.addMemory(assistantId, content)
                                        }
                                    }
                                }
                                "delete", "remove" -> {
                                    val search = update.search?.trim()
                                    if (!search.isNullOrBlank()) {
                                        val target = assistantMemories
                                            .map { it to MemoryVectorMath.keywordScore(search, it.content) }
                                            .filter { it.second > 0f }
                                            .maxByOrNull { it.second }?.first

                                        if (target != null && target.id > 0) {
                                            memoryRepository.deleteMemory(target.id)
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        isSyncingRawUpdates = false
                    }
                }

                val renderedMd = renderMemorySummaryToMarkdown(updatedSummary)
                val tokenCount = ContextTokenEstimator.textTokens(renderedMd, model)
                persistSummary(assistantId, updatedSummary, tokenCount)

                Result.success(updatedSummary)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "askOrUpdate failed for $assistantId", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Entry-point called when any memory is created, updated, or deleted.
     */
    fun onMemoryChanged(
        assistantId: String,
        changeType: MemoryChangeType,
        memoryContent: String,
    ) {
        scope.launch {
            if (isSyncingRawUpdates) return@launch
            try {
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(assistantId) ?: return@launch
                if (!assistant.enableMemory || !assistant.enableMemorySummary) return@launch

                when (assistant.memorySummaryCadence) {
                    MemorySummaryCadence.IMMEDIATE -> {
                        applyIncrementalUpdate(assistantId, changeType, memoryContent)
                    }

                    MemorySummaryCadence.DEBOUNCE_10M -> {
                        debounceJobs[assistantId]?.cancel()
                        debounceJobs[assistantId] = scope.launch {
                            delay(10 * 60 * 1000L) // 10 minutes debounce
                            fullRegenerate(assistantId)
                        }
                    }

                    MemorySummaryCadence.HOURLY,
                    MemorySummaryCadence.DAILY -> {
                        // Left for periodic background consolidation check
                    }

                    MemorySummaryCadence.MANUAL -> {
                        // User triggers explicitly
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "onMemoryChanged background trigger failed", e)
            }
        }
    }

    private suspend fun fullRegenerateInternal(assistantId: String): Result<MemorySummaryData> {
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(assistantId)
            ?: return Result.failure(IllegalStateException("Assistant $assistantId not found"))

        val allMemories = memoryRepository.getCombinedMemoriesOfAssistant(assistantId)
        val (model, provider) = resolveModelAndProvider(settings, assistant)
            ?: return Result.failure(IllegalStateException("No valid model or provider with API key found for assistant ${assistant.name}"))
        val providerHandler = providerManager.getProviderByType(provider)

        val tokenBudget = assistant.memorySummaryTokenBudget.coerceIn(200, 10000)
        val systemPrompt = assistant.customMemorySummaryPrompt
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT

        val userPrompt = buildFullSummarizerUserPrompt(
            memories = allMemories.map { it.content },
            tokenBudget = tokenBudget,
        )

        val response = providerHandler.generateText(
            providerSetting = provider,
            messages = listOf(
                UIMessage.system(systemPrompt),
                UIMessage.user(userPrompt),
            ),
            params = settings.buildSummarizerGenerationParams(
                model = model,
                temperature = 0.3f,
            ),
        )

        val rawResponse = response.choices.firstOrNull()?.message?.toContentText()
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Empty summarizer response"))

        val parsed = parseMemorySummaryJson(rawResponse)
            ?: return Result.failure(IllegalStateException("Could not parse summarizer response"))

        val renderedMd = renderMemorySummaryToMarkdown(parsed)
        val tokenCount = ContextTokenEstimator.textTokens(renderedMd, model)
        persistSummary(assistantId, parsed, tokenCount)

        return Result.success(parsed)
    }

    private suspend fun persistSummary(
        assistantId: String,
        data: MemorySummaryData,
        tokenCount: Int,
    ) {
        val clean = sanitizeMemorySummaryData(data)
        val jsonStr = Json.encodeToString(MemorySummaryData.serializer(), clean)
        val markdown = renderMemorySummaryToMarkdown(clean)
        memorySummaryDao.upsert(
            MemorySummaryEntity(
                assistantId = assistantId,
                summaryJson = jsonStr,
                rawMarkdown = markdown,
                updatedAt = System.currentTimeMillis(),
                tokenCount = tokenCount,
                sourceHash = null,
            )
        )
    }
}
