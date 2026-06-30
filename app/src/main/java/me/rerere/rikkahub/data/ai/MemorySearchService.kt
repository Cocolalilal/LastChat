package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.MemorySearchTimeRange
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import kotlinx.datetime.toInstant
import kotlin.math.min
import kotlin.uuid.Uuid

private const val MEMORY_SEARCH_MAX_LIMIT = 8
private const val MEMORY_SEARCH_CHAT_SUMMARY_LIMIT = 2
private const val MEMORY_SEARCH_MAX_QUERIES = 8

internal data class ConversationRecallSpan(
    val conversationId: Uuid,
    val conversationTitle: String,
    val messageIndex: Int,
    val messages: List<UIMessage>,
    val timestampMillis: Long,
    val score: Int,
    val matchedText: String? = null,
)

internal data class MemoryRecallSearchQuery(
    val text: String,
    val source: String,
)

internal fun fuzzyMemoryAgeLabel(
    timestampMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    return me.rerere.ai.util.fuzzyMemoryAgeLabel(
        timestampMillis = timestampMillis,
        nowMillis = nowMillis,
    )
}

internal fun findConversationRecallSpans(
    conversation: Conversation,
    query: String,
    maxSpans: Int = 3,
    radius: Int = 2,
    timeRange: MemorySearchTimeRange? = null,
): List<ConversationRecallSpan> {
    val plan = MemoryRecallQueryPlan.from(query)
    val tokens = plan.tokens
    if (tokens.isEmpty()) return emptyList()

    val messages = conversation.currentMessages.filter { message -> message.toContentText().isNotBlank() }
    val scored = messages.mapIndexedNotNull { index, message ->
        val timestampMillis = message.createdAt
            .toInstant(kotlinx.datetime.TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        if (timeRange != null && !timeRange.contains(timestampMillis)) {
            return@mapIndexedNotNull null
        }
        val text = message.toContentText()
            val messageScore = scoreMemorySearchText(text, query, tokens)
            if (messageScore <= 0) {
                null
            } else {
                val titleScore = scoreMemorySearchText(conversation.title, query, tokens) / 2
                ScoredMessageHit(
                    index = index,
                    score = messageScore + titleScore,
                    matchedText = text.toRecallSnippet(limit = 260),
                    timestampMillis = timestampMillis,
                )
            }
    }.sortedByDescending { it.score }

    val usedIndices = mutableSetOf<Int>()
    return scored.mapNotNull { hit ->
        val index = hit.index
        if (usedIndices.any { kotlin.math.abs(it - index) <= radius }) {
            return@mapNotNull null
        }
        usedIndices += index
        val start = (index - radius).coerceAtLeast(0)
        val endExclusive = (index + radius + 1).coerceAtMost(messages.size)
        ConversationRecallSpan(
            conversationId = conversation.id,
            conversationTitle = conversation.title,
            messageIndex = index,
            messages = messages.subList(start, endExclusive),
            timestampMillis = hit.timestampMillis,
            score = hit.score,
            matchedText = hit.matchedText,
        )
    }.take(maxSpans)
}

internal fun buildFallbackRecallSummary(span: ConversationRecallSpan): String {
    return span.messages
        .mapNotNull { message ->
            val text = message.toContentText().replace(Regex("\\s+"), " ").trim()
            if (text.isBlank()) {
                null
            } else {
                val speaker = when (message.role) {
                    MessageRole.USER -> "User"
                    MessageRole.ASSISTANT -> "Assistant"
                    else -> message.role.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                "$speaker: ${text.take(220)}"
            }
        }
        .joinToString(" / ")
        .take(700)
}

internal fun memorySearchTokens(query: String): List<String> {
    val base = query
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .map { it.trim() }
        .filter { it.length >= 3 }
        .distinct()

    return (base + base.flatMap { MEMORY_SEARCH_EXPANSIONS[it].orEmpty() })
        .distinct()
}

internal fun buildDeterministicMemoryRecallQueries(
    query: String,
    maxQueries: Int = MEMORY_SEARCH_MAX_QUERIES,
): List<MemoryRecallSearchQuery> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return emptyList()

    val plan = MemoryRecallQueryPlan.from(trimmed)
    val queries = mutableListOf(
        MemoryRecallSearchQuery(trimmed, "original")
    )

    fun add(text: String, source: String) {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        if (compact.length >= 3) {
            queries += MemoryRecallSearchQuery(compact, source)
        }
    }

    if (plan.originalTokens.size > 1) {
        add(plan.originalTokens.joinToString(" "), "tokens")
    }

    val expandedTokens = plan.tokens
    if (expandedTokens.size > plan.originalTokens.size) {
        add(expandedTokens.take(20).joinToString(" "), "expanded")
    }

    plan.originalTokens
        .windowed(size = 3, step = 2, partialWindows = true)
        .filter { it.size >= 2 }
        .forEach { tokens -> add(tokens.joinToString(" "), "chunk") }

    plan.phrases.forEach { phrase -> add(phrase, "phrase") }

    return queries
        .distinctBy { it.text.normalizedRecallText() }
        .take(maxQueries)
}

internal fun scoreMemorySearchText(
    text: String,
    query: String,
    tokens: List<String> = memorySearchTokens(query),
): Int {
    if (text.isBlank() || tokens.isEmpty()) return 0
    val normalized = text.normalizedRecallText()
    val plan = MemoryRecallQueryPlan.from(query)
    var score = tokens.sumOf { token ->
        when {
            normalized.contains(token) -> if (token in plan.originalTokens) 3 else 2
            token.length >= 5 && normalized.contains(token.take(5)) -> 1
            else -> 0
        }
    }
    val compactQuery = query.trim().lowercase()
    if (compactQuery.length >= 3 && normalized.contains(compactQuery.normalizedRecallText())) {
        score += 6
    }
    plan.phrases.forEach { phrase ->
        if (normalized.contains(phrase.normalizedRecallText())) {
            score += 8
        }
    }
    val queryMentionsBed = plan.tokens.any { it in BED_TERMS }
    val queryMentionsUnder = plan.tokens.any { it in UNDER_TERMS }
    val queryMentionsFright = plan.tokens.any { it in FRIGHT_TERMS }
    if ((queryMentionsBed || queryMentionsUnder) && normalized.hasNearRecallTerms(BED_TERMS, UNDER_TERMS, maxGap = 4)) {
        score += 10
    }
    if ((queryMentionsBed || queryMentionsFright) && normalized.hasNearRecallTerms(BED_TERMS, FRIGHT_TERMS, maxGap = 8)) {
        score += 6
    }
    if (plan.originalTokens.isNotEmpty() && plan.originalTokens.all { normalized.contains(it) }) {
        score += plan.originalTokens.size * 2
    }
    return score
}

private data class ScoredMessageHit(
    val index: Int,
    val score: Int,
    val matchedText: String,
    val timestampMillis: Long,
)

private data class ScoredMemoryCandidate(
    val memory: AssistantMemory,
    val score: Int,
    val confidence: Float,
    val matchedQuery: String,
)

private fun MutableMap<Int, ScoredMemoryCandidate>.addOrUpgrade(
    memory: AssistantMemory,
    score: Int,
    confidence: Float,
    matchedQuery: String,
) {
    val existing = this[memory.id]
    val upgraded = if (existing == null) {
        ScoredMemoryCandidate(
            memory = memory,
            score = score,
            confidence = confidence,
            matchedQuery = matchedQuery,
        )
    } else {
        existing.copy(
            score = maxOf(existing.score, score) + 1,
            confidence = maxOf(existing.confidence, confidence),
            matchedQuery = if (score > existing.score) matchedQuery else existing.matchedQuery,
        )
    }
    this[memory.id] = upgraded
}

internal fun parseMemorySearchTimeRange(
    raw: String?,
    nowMillis: Long = System.currentTimeMillis(),
): MemorySearchTimeRange? {
    return me.rerere.ai.util.parseMemorySearchTimeRange(
        raw = raw,
        nowMillis = nowMillis,
    )
}

private data class MemoryRecallQueryPlan(
    val originalTokens: List<String>,
    val tokens: List<String>,
    val phrases: List<String>,
) {
    companion object {
        fun from(query: String): MemoryRecallQueryPlan {
            val originalTokens = query
                .lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .map { it.trim() }
                .filter { it.length >= 3 }
                .distinct()
            val tokens = memorySearchTokens(query)
            val phrases = buildList {
                val lowered = query.lowercase()
                if (originalTokens.any { it in BED_TERMS }) {
                    addAll(listOf("under the bed", "under bed", "beneath the bed", "below the bed", "underneath the bed"))
                }
                if (originalTokens.any { it in UNDER_TERMS } && originalTokens.any { it in BED_TERMS }) {
                    addAll(listOf("hiding under", "hid under", "from under", "under your bed", "under my bed"))
                }
                if (lowered.contains("spook") || lowered.contains("scare") || lowered.contains("startle")) {
                    addAll(listOf("scared me", "startled me", "spooked me", "jump scare"))
                }
            }.distinct()

            return MemoryRecallQueryPlan(
                originalTokens = originalTokens,
                tokens = tokens,
                phrases = phrases,
            )
        }
    }
}

private val MEMORY_SEARCH_EXPANSIONS = mapOf(
    "body" to listOf("figure", "size", "shape"),
    "measurement" to listOf("measurements", "measure", "sizes"),
    "measurements" to listOf("measurement", "measure", "sizes"),
    "weight" to listOf("weigh", "weighed", "pounds", "lbs", "kg", "kilograms"),
    "bust" to listOf("chest", "breast", "breasts"),
    "waist" to listOf("midsection", "middle"),
    "hips" to listOf("hip"),
    "hip" to listOf("hips"),
    "bed" to listOf("beds", "bedroom", "mattress", "blanket", "pillow"),
    "under" to listOf("beneath", "underneath", "below"),
    "beneath" to listOf("under", "underneath", "below"),
    "underneath" to listOf("under", "beneath", "below"),
    "spook" to listOf("spooked", "scare", "scared", "startle", "startled", "frighten", "frightened"),
    "spooked" to listOf("spook", "scare", "scared", "startle", "startled", "frighten", "frightened"),
    "scare" to listOf("scared", "spook", "spooked", "startle", "startled", "frighten", "frightened"),
    "scared" to listOf("scare", "spook", "spooked", "startle", "startled", "frighten", "frightened"),
    "startle" to listOf("startled", "spook", "spooked", "scare", "scared"),
    "startled" to listOf("startle", "spook", "spooked", "scare", "scared"),
    "hide" to listOf("hid", "hiding", "hidden"),
    "hid" to listOf("hide", "hiding", "hidden")
)

private val BED_TERMS = setOf("bed", "beds", "bedroom", "mattress", "blanket", "pillow")
private val UNDER_TERMS = setOf("under", "beneath", "underneath", "below")
private val FRIGHT_TERMS = setOf("spook", "spooked", "scare", "scared", "startle", "startled", "frighten", "frightened")

private fun String.normalizedRecallText(): String {
    return lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.hasNearRecallTerms(
    firstTerms: Set<String>,
    secondTerms: Set<String>,
    maxGap: Int,
): Boolean {
    val words = split(' ').filter { it.isNotBlank() }
    val firstIndices = words.mapIndexedNotNull { index, word ->
        if (firstTerms.any { word.contains(it) }) index else null
    }
    if (firstIndices.isEmpty()) return false
    val secondIndices = words.mapIndexedNotNull { index, word ->
        if (secondTerms.any { word.contains(it) }) index else null
    }
    return firstIndices.any { first -> secondIndices.any { second -> kotlin.math.abs(first - second) <= maxGap } }
}

private fun String.toRecallSnippet(limit: Int): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= limit) {
        normalized
    } else {
        normalized.take(limit).trimEnd() + "..."
    }
}

class MemorySearchService(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
) {
    suspend fun searchMemory(
        assistant: Assistant,
        activeConversationId: Uuid?,
        query: String,
        limit: Int = 5,
        timeRange: String? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext buildJsonObject {
                put("query", query)
                put("summary", "No memory search was run because the query was blank.")
                put("results", JsonArray(emptyList()))
            }
        }

        val boundedLimit = limit.coerceIn(1, MEMORY_SEARCH_MAX_LIMIT)
        val parsedTimeRange = parseMemorySearchTimeRange(timeRange ?: trimmedQuery)
        val settings = settingsStore.settingsFlow.first()
        val warnings = mutableListOf<String>()
        val deterministicQueries = buildDeterministicMemoryRecallQueries(trimmedQuery)
        val agentQueries = runCatching {
            generateSubagentRecallQueries(
                settings = settings,
                assistant = assistant,
                query = trimmedQuery,
                timeRange = parsedTimeRange,
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            warnings += "Subagent query expansion was unavailable, so deterministic recall queries were used."
            emptyList()
        }
        val recallQueries = (deterministicQueries + agentQueries)
            .distinctBy { it.text.normalizedRecallText() }
            .take(MEMORY_SEARCH_MAX_QUERIES)

        val memoryResults = runCatching {
            searchStoredMemories(
                assistant = assistant,
                queries = recallQueries,
                limit = boundedLimit,
                timeRange = parsedTimeRange,
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            warnings += "Stored memory search fell back with no results."
            emptyList()
        }
        val chatSpans = runCatching {
            searchPastChatSpans(
                assistant = assistant,
                activeConversationId = activeConversationId,
                queries = recallQueries,
                limit = boundedLimit,
                timeRange = parsedTimeRange,
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            warnings += "Past chat search fell back with no results."
            emptyList()
        }

        val chatResults = chatSpans.take(MEMORY_SEARCH_CHAT_SUMMARY_LIMIT).map { span ->
            val summary = summarizeChatSpan(settings, assistant, span, trimmedQuery)
                ?: buildFallbackRecallSummary(span)
            RecallResult(
                source = "past_chat",
                id = span.conversationId.toString(),
                summary = summary,
                content = summary,
                timestampMillis = span.timestampMillis,
                confidence = confidenceFromScore(span.score),
                title = span.conversationTitle.takeIf { it.isNotBlank() },
                matchedText = span.matchedText,
                score = span.score,
            )
        } + chatSpans.drop(MEMORY_SEARCH_CHAT_SUMMARY_LIMIT).map { span ->
            val summary = buildFallbackRecallSummary(span)
            RecallResult(
                source = "past_chat",
                id = span.conversationId.toString(),
                summary = summary,
                content = summary,
                timestampMillis = span.timestampMillis,
                confidence = confidenceFromScore(span.score),
                title = span.conversationTitle.takeIf { it.isNotBlank() },
                matchedText = span.matchedText,
                score = span.score,
            )
        }

        val results = (memoryResults + chatResults)
            .sortedWith(
                compareByDescending<RecallResult> { it.confidence }
                    .thenByDescending { it.score ?: 0 }
                    .thenByDescending { it.timestampMillis }
            )
            .take(boundedLimit)
        val agentSummary = synthesizeMemoryFindings(
            settings = settings,
            assistant = assistant,
            query = trimmedQuery,
            timeRange = parsedTimeRange,
            results = results,
        )

        buildJsonObject {
            put("query", trimmedQuery)
            put("source", "memory_search")
            put("queries", JsonArray(recallQueries.map { JsonPrimitive(it.text) }))
            put("scope", "core_memories_and_current_character_past_chats")
            parsedTimeRange?.let { put("time_filter", it.label) }
            put("summary", agentSummary ?: buildOverallSummary(results))
            put("confidence", JsonPrimitive(results.maxOfOrNull { it.confidence } ?: 0f))
            put("results", JsonArray(results.map { it.toJson() }))
            put("note", "These are approximate memory search results. Time labels are intentionally fuzzy.")
            if (warnings.isNotEmpty()) {
                put("warnings", JsonArray(warnings.map(::JsonPrimitive)))
            }
        }
    }

    private suspend fun searchStoredMemories(
        assistant: Assistant,
        queries: List<MemoryRecallSearchQuery>,
        limit: Int,
        timeRange: MemorySearchTimeRange?,
    ): List<RecallResult> {
        if (queries.isEmpty()) return emptyList()
        val assistantId = assistant.id.toString()
        val merged = linkedMapOf<Int, ScoredMemoryCandidate>()
        val core = memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
            .filter { memory -> memory.type == MemoryType.CORE }
            .filter { memory -> timeRange?.contains(memory.timestamp) ?: true }
            .take(500)

        queries.forEachIndexed { queryIndex, recallQuery ->
            if (assistant.useRagMemoryRetrieval) {
                runCatching {
                    memoryRepository.retrieveRelevantMemoriesWithScores(
                        assistantId = assistantId,
                        query = recallQuery.text,
                        limit = (limit * 3).coerceAtLeast(limit),
                        similarityThreshold = 0.12f,
                        includeCore = true,
                        includeEpisodes = false,
                    )
                }.getOrElse { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emptyList()
                }.asSequence()
                    .filter { (memory, _) -> memory.type == MemoryType.CORE }
                    .filter { (memory, _) -> timeRange?.contains(memory.timestamp) ?: true }
                    .forEach { (memory, similarity) ->
                        val score = ((similarity.coerceIn(0f, 1f) * 24f).toInt() + 4 - queryIndex.coerceAtMost(3))
                            .coerceAtLeast(1)
                        merged.addOrUpgrade(
                            memory = memory,
                            score = score,
                            confidence = (0.45f + similarity.coerceIn(0f, 1f) * 0.45f).coerceIn(0f, 0.95f),
                            matchedQuery = recallQuery.text,
                        )
                    }
            }

            val tokens = memorySearchTokens(recallQuery.text)
            core.forEach { memory ->
                val textScore = scoreMemorySearchText(memory.content, recallQuery.text, tokens)
                if (textScore > 0) {
                    val score = (textScore + 3 - queryIndex.coerceAtMost(3)).coerceAtLeast(1)
                    merged.addOrUpgrade(
                        memory = memory,
                        score = score,
                        confidence = confidenceFromScore(score),
                        matchedQuery = recallQuery.text,
                    )
                }
            }
        }

        return merged.values
            .asSequence()
            .sortedWith(
                compareByDescending<ScoredMemoryCandidate> { it.score }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.memory.timestamp }
            )
            .take(limit)
            .map { candidate ->
                candidate.memory.toRecallResult(
                    confidence = candidate.confidence,
                    score = candidate.score,
                    matchedQuery = candidate.matchedQuery,
                )
            }
            .toList()
    }

    private suspend fun searchPastChatSpans(
        assistant: Assistant,
        activeConversationId: Uuid?,
        queries: List<MemoryRecallSearchQuery>,
        limit: Int,
        timeRange: MemorySearchTimeRange?,
    ): List<ConversationRecallSpan> {
        if (queries.isEmpty()) return emptyList()
        val merged = linkedMapOf<String, ConversationRecallSpan>()
        return conversationRepository
            .getConversationsOfAssistant(assistant.id)
            .first()
            .asSequence()
            .filter { it.id != activeConversationId }
            .onEach { conversation ->
                queries.forEachIndexed { queryIndex, recallQuery ->
                    runCatching {
                        findConversationRecallSpans(
                            conversation = conversation,
                            query = recallQuery.text,
                            maxSpans = 2,
                            timeRange = timeRange,
                        )
                    }.getOrElse { throwable ->
                        if (throwable is CancellationException) throw throwable
                        emptyList()
                    }.forEach { span ->
                        val key = "${span.conversationId}:${span.messageIndex}"
                        val adjusted = span.copy(score = (span.score + 2 - queryIndex.coerceAtMost(2)).coerceAtLeast(1))
                        val existing = merged[key]
                        if (existing == null || adjusted.score > existing.score) {
                            merged[key] = adjusted
                        }
                    }
                }
            }
            .toList()
            .let { merged.values.asSequence() }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    private suspend fun generateSubagentRecallQueries(
        settings: Settings,
        assistant: Assistant,
        query: String,
        timeRange: MemorySearchTimeRange?,
    ): List<MemoryRecallSearchQuery> {
        val (model, provider) = resolveSubagentModel(settings, assistant) ?: return emptyList()
        val providerHandler = providerManager.getProviderByType(provider)
        val prompt = """
            You are a recall-query planner for one character's private memory search.

            Original query:
            $query

            Time filter:
            ${timeRange?.label ?: "none"}

            Produce up to 5 alternate search queries that could find the same memory in core memories or past chats.
            Rules:
            - Preserve important exact words from the original query.
            - Add useful synonyms, paraphrases, related facts, and likely wording.
            - Do not search other characters or unrelated databases.
            - Do not add commentary.
            - Output one query per line.
        """.trimIndent()

        return runCatching {
            val response = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = settings.buildSubagentGenerationParams(
                    model = model,
                    temperature = 0.2f,
                ),
            )
            response.choices.firstOrNull()?.message?.toContentText()
                ?.lineSequence()
                ?.map { line ->
                    line.trim()
                        .removePrefix("-")
                        .removePrefix("*")
                        .replace(Regex("""^\d+[\).\s-]+"""), "")
                        .trim()
                        .trim('"')
                }
                ?.filter { it.length in 3..180 }
                ?.distinctBy { it.normalizedRecallText() }
                ?.take(5)
                ?.map { MemoryRecallSearchQuery(it, "subagent") }
                ?.toList()
                .orEmpty()
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            emptyList()
        }
    }

    private suspend fun summarizeChatSpan(
        settings: Settings,
        assistant: Assistant,
        span: ConversationRecallSpan,
        query: String,
    ): String? {
        val (model, provider) = resolveSubagentModel(settings, assistant) ?: return null
        val providerHandler = providerManager.getProviderByType(provider)
        val messagesText = span.messages.joinToString("\n") { message ->
            "${message.role}: ${message.toContentText().take(700)}"
        }
        val prompt = """
            Summarize this remembered chat span for a character recalling something.
            Query: $query
            Conversation title: ${span.conversationTitle}

            Keep it to 1-2 natural sentences. Preserve concrete facts, preferences, plans, and emotional context.
            Do not mention exact timestamps. It is okay to sound slightly uncertain if the span is ambiguous.

            Chat span:
            $messagesText
        """.trimIndent()

        return runCatching {
            val response = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = settings.buildSubagentGenerationParams(
                    model = model,
                    temperature = 0.2f,
                ),
            )
            response.choices.firstOrNull()?.message?.toContentText()?.trim()?.takeIf { it.isNotBlank() }?.take(700)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            null
        }
    }

    private suspend fun synthesizeMemoryFindings(
        settings: Settings,
        assistant: Assistant,
        query: String,
        timeRange: MemorySearchTimeRange?,
        results: List<RecallResult>,
    ): String? {
        val (model, provider) = resolveSubagentModel(settings, assistant) ?: return null
        val providerHandler = providerManager.getProviderByType(provider)
        val findingsText = if (results.isEmpty()) {
            "No direct core memory or past-chat hits were found."
        } else {
            results.take(MEMORY_SEARCH_MAX_LIMIT).mapIndexed { index, result ->
                buildString {
                    append("${index + 1}. source=${result.source}")
                    result.title?.let { append(", title=$it") }
                    append(", time=${fuzzyMemoryAgeLabel(result.timestampMillis)}")
                    append(", confidence=${"%.2f".format(result.confidence)}")
                    append("\nsummary: ${result.summary.take(700)}")
                    result.matchedText?.takeIf { it.isNotBlank() }?.let {
                        append("\nmatched: ${it.take(400)}")
                    }
                }
            }.joinToString("\n\n")
        }
        val prompt = """
            You are the memory subagent for one character. Your job is to read retrieval candidates and prepare a compact memory note for the character.

            Query: $query
            Time filter: ${timeRange?.label ?: "none"}

            Rules:
            - Use only the provided findings. Do not invent a memory.
            - If nothing clearly matches, say that no clear memory was found, then mention any nearby/useful clue only if the findings actually support it.
            - Prefer stable facts, exact measurements, names, preferences, plans, and emotionally important events.
            - Keep it concise: 1-4 short sentences.
            - Use fuzzy time language, not exact timestamps.

            Findings:
            $findingsText
        """.trimIndent()

        return runCatching {
            val response = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = settings.buildSubagentGenerationParams(
                    model = model,
                    temperature = 0.1f,
                ),
            )
            response.choices.firstOrNull()?.message?.toContentText()?.trim()?.takeIf { it.isNotBlank() }?.take(900)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            null
        }
    }

    private fun resolveSubagentModel(
        settings: Settings,
        assistant: Assistant,
    ): Pair<me.rerere.ai.provider.Model, me.rerere.ai.provider.ProviderSetting>? {
        val modelId = settings.subagentModelId
            ?: settings.summarizerModelId
            ?: assistant.backgroundModelId
            ?: settings.chatModelId
        val model = settings.findModelById(modelId) ?: return null
        val provider = model.findProvider(settings.providers) ?: return null
        return model to provider
    }

    private fun AssistantMemory.toRecallResult(
        confidence: Float,
        score: Int? = null,
        matchedQuery: String? = null,
    ): RecallResult {
        val compactContent = content.toRecallSnippet(limit = 900)
        return RecallResult(
            source = if (type == MemoryType.CORE) "core_memory" else "episodic_memory",
            id = id.toString(),
            summary = compactContent,
            content = compactContent,
            timestampMillis = timestamp,
            confidence = confidence,
            matchedQuery = matchedQuery,
            score = score,
        )
    }

    private fun buildOverallSummary(results: List<RecallResult>): String {
        return when {
            results.isEmpty() -> "I couldn't find a clear memory for that."
            results.size == 1 -> "I found one possible memory."
            else -> "I found ${results.size} possible memories."
        }
    }

    private fun confidenceFromScore(score: Int): Float {
        return min(0.95f, 0.35f + (score * 0.12f))
    }

    private data class RecallResult(
        val source: String,
        val id: String,
        val summary: String,
        val content: String,
        val timestampMillis: Long,
        val confidence: Float,
        val title: String? = null,
        val matchedText: String? = null,
        val matchedQuery: String? = null,
        val score: Int? = null,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("source", source)
            put("id", id)
            title?.let { put("conversation_title", it) }
            put("summary", summary)
            put("content", content)
            matchedText?.let { put("matched_text", it) }
            matchedQuery?.let { put("matched_query", it) }
            put("time_ago", fuzzyMemoryAgeLabel(timestampMillis))
            put("confidence", JsonPrimitive(confidence))
            score?.let { put("score", JsonPrimitive(it)) }
        }
    }
}
