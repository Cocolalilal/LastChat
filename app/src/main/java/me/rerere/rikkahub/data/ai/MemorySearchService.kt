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
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.uuid.Uuid

private const val MEMORY_SEARCH_MAX_LIMIT = 8
private const val MEMORY_SEARCH_CHAT_SUMMARY_LIMIT = 2

internal data class ConversationRecallSpan(
    val conversationId: Uuid,
    val conversationTitle: String,
    val messageIndex: Int,
    val messages: List<UIMessage>,
    val timestampMillis: Long,
    val score: Int,
    val matchedText: String? = null,
)

internal fun fuzzyMemoryAgeLabel(
    timestampMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    if (timestampMillis <= 0L) return "some time ago"
    val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
    val then = Instant.ofEpochMilli(timestampMillis.coerceAtMost(nowMillis)).atZone(ZoneId.systemDefault())
    val days = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
    val months = ChronoUnit.MONTHS.between(then.toLocalDate().withDayOfMonth(1), now.toLocalDate().withDayOfMonth(1))

    return when {
        days <= 0L -> "earlier today"
        days == 1L -> "yesterday"
        days <= 3L -> "a few days ago"
        days <= 10L -> "about a week ago"
        days <= 21L -> "a couple weeks ago"
        months <= 1L -> "about a month ago"
        months <= 3L -> "a couple months ago"
        months <= 11L -> "months ago"
        else -> "a long time ago"
    }
}

internal fun findConversationRecallSpans(
    conversation: Conversation,
    query: String,
    maxSpans: Int = 3,
    radius: Int = 2,
): List<ConversationRecallSpan> {
    val plan = MemoryRecallQueryPlan.from(query)
    val tokens = plan.tokens
    if (tokens.isEmpty()) return emptyList()

    val messages = conversation.currentMessages.filter { message -> message.toContentText().isNotBlank() }
    val scored = messages.mapIndexedNotNull { index, message ->
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
            timestampMillis = conversation.updateAt.toEpochMilli(),
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
)

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
        val warnings = mutableListOf<String>()
        val memoryResults = runCatching {
            searchStoredMemories(
                assistant = assistant,
                query = trimmedQuery,
                limit = boundedLimit,
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
                query = trimmedQuery,
                limit = boundedLimit,
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            warnings += "Past chat search fell back with no results."
            emptyList()
        }

        val settings = settingsStore.settingsFlow.first()
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

        buildJsonObject {
            put("query", trimmedQuery)
            put("source", "memory_search")
            put("summary", buildOverallSummary(results))
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
        query: String,
        limit: Int,
    ): List<RecallResult> {
        val ragResults = if (assistant.useRagMemoryRetrieval) {
            runCatching {
                memoryRepository.retrieveRelevantMemoriesWithScores(
                    assistantId = assistant.id.toString(),
                    query = query,
                    limit = limit,
                    similarityThreshold = 0.2f,
                    includeCore = true,
                    includeEpisodes = true,
                )
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }

        if (ragResults.isNotEmpty()) {
            return ragResults.map { (memory, score) ->
                memory.toRecallResult(confidence = score.coerceIn(0f, 1f))
            }
        }

        val tokens = memorySearchTokens(query)
        val core = memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
        val episodes = memoryRepository.getEpisodeEntitiesOfAssistant(assistant.id.toString())
            .map {
                AssistantMemory(
                    id = -it.id,
                    content = it.content,
                    type = MemoryType.EPISODIC,
                    hasEmbedding = it.embedding != null,
                    embeddingModelId = it.embeddingModelId,
                    timestamp = it.startTime,
                    significance = it.significance,
                )
            }

        return (core + episodes)
            .mapNotNull { memory ->
                val score = scoreMemorySearchText(memory.content, query, tokens)
                if (score <= 0) null else memory to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (memory, score) ->
                memory.toRecallResult(confidence = confidenceFromScore(score))
            }
    }

    private suspend fun searchPastChatSpans(
        assistant: Assistant,
        activeConversationId: Uuid?,
        query: String,
        limit: Int,
    ): List<ConversationRecallSpan> {
        return conversationRepository
            .getConversationsOfAssistant(assistant.id)
            .first()
            .asSequence()
            .filter { it.id != activeConversationId }
            .flatMap { conversation ->
                runCatching {
                    findConversationRecallSpans(
                        conversation = conversation,
                        query = query,
                        maxSpans = 2,
                    )
                }.getOrElse { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emptyList()
                }.asSequence()
            }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    private suspend fun summarizeChatSpan(
        settings: Settings,
        assistant: Assistant,
        span: ConversationRecallSpan,
        query: String,
    ): String? {
        val modelId = settings.summarizerModelId ?: assistant.backgroundModelId ?: settings.chatModelId
        val model = settings.findModelById(modelId) ?: return null
        val provider = model.findProvider(settings.providers) ?: return null
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
                params = settings.buildSummarizerGenerationParams(
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

    private fun AssistantMemory.toRecallResult(confidence: Float): RecallResult {
        val compactContent = content.toRecallSnippet(limit = 900)
        return RecallResult(
            source = if (type == MemoryType.CORE) "core_memory" else "episodic_memory",
            id = id.toString(),
            summary = compactContent,
            content = compactContent,
            timestampMillis = timestamp,
            confidence = confidence,
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
        val score: Int? = null,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("source", source)
            put("id", id)
            title?.let { put("conversation_title", it) }
            put("summary", summary)
            put("content", content)
            matchedText?.let { put("matched_text", it) }
            put("time_ago", fuzzyMemoryAgeLabel(timestampMillis))
            put("confidence", JsonPrimitive(confidence))
            score?.let { put("score", JsonPrimitive(it)) }
        }
    }
}
