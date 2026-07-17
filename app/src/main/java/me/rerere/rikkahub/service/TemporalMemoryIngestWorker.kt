package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.memory.MemoryExtractionEnvelope
import me.rerere.rikkahub.data.memory.SourceMessage
import me.rerere.rikkahub.data.memory.TemporalRecallItem
import me.rerere.rikkahub.data.memory.TemporalMemoryRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TemporalMemoryIngestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val temporalMemoryRepository: TemporalMemoryRepository by inject()
    private val providerManager: ProviderManager by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            ingest()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            PlatformLog.w(TAG, "Memory v3 ingest failed: ${throwable::class.simpleName}: ${throwable.message}")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun ingest(): Result {
        val assistantId = inputData.getString(KEY_ASSISTANT_ID) ?: return Result.failure()
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: return Result.failure()
        val conversation = conversationRepository.getConversationById(Uuid.parse(conversationId))
            ?: return Result.success()
        if (conversation.assistantId.toString() != assistantId) {
            PlatformLog.w(TAG, "Rejected mismatched memory scope assistant=$assistantId conversation=$conversationId")
            return Result.failure()
        }
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getAssistantById(conversation.assistantId) ?: return Result.success()
        if (!assistant.enableMemory) return Result.success()

        val selectedMessages = conversation.currentMessages
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .filter { it.toText().isNotBlank() }
            .map { message ->
                SourceMessage(
                    id = message.id.toString(),
                    role = if (message.role == MessageRole.USER) 0 else 1,
                    text = message.toText(),
                    observedAt = message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
                )
            }
        if (selectedMessages.isEmpty()) return Result.success()
        temporalMemoryRepository.recordSources(assistantId, conversationId, selectedMessages)
        temporalMemoryRepository.importLegacyMemories(assistantId)

        val state = temporalMemoryRepository.getIngestState(conversationId)
        val lastIndex = state?.lastMessageId?.let { id -> selectedMessages.indexOfLast { it.id == id } } ?: -1
        val pending = if (lastIndex >= 0) selectedMessages.drop(lastIndex + 1) else selectedMessages.takeLast(MAX_PENDING_MESSAGES)
        if (pending.isEmpty()) return Result.success()

        // Searchable/basic modes still advance an evidence watermark without incurring a model call.
        val indexOnly = inputData.getBoolean(KEY_INDEX_ONLY, false)
        if (indexOnly || !assistant.enableMemoryConsolidation) {
            temporalMemoryRepository.applyExtraction(
                assistantId = assistantId,
                conversationId = conversationId,
                messages = pending,
                extraction = MemoryExtractionEnvelope(),
            )
            return Result.success()
        }

        val modelId = settings.summarizerModelId ?: assistant.backgroundModelId ?: settings.chatModelId
        val model = settings.findModelById(modelId) ?: return Result.retry()
        val providerSetting = model.findProvider(settings.providers) ?: return Result.retry()
        val provider = providerManager.getProviderByType(providerSetting)
        val nearby = temporalMemoryRepository.recall(
            assistantId = assistantId,
            query = pending.joinToString(" ") { it.text }.take(1_500),
            limit = 10,
        )
        val response = provider.generateText(
            providerSetting = providerSetting,
            messages = listOf(UIMessage.user(buildExtractionPrompt(pending, nearby.items))),
            params = settings.buildSummarizerGenerationParams(model = model, temperature = 0.1f),
        )
        val text = response.choices.firstOrNull()?.message?.toContentText().orEmpty()
        val extraction = parseExtraction(text)
        temporalMemoryRepository.applyExtraction(assistantId, conversationId, pending, extraction)
        return Result.success()
    }

    private fun buildExtractionPrompt(pending: List<SourceMessage>, existing: List<TemporalRecallItem>): String = """
        You are LastChat's evidence-bound temporal memory encoder for one AI character.
        Extract only durable personal facts, preferences, relationships, plans, meaningful changes, and one coherent scene from NEW_MESSAGES.
        Never invent. Never infer sensitive traits. Treat jokes, hypothetical discussion, and roleplay as non-real and label reality accordingly.
        Resolve relative dates from each message's observed_at timestamp, not from today's date.
        Existing memories are only for deduplication and detecting changes.

        Operations:
        - add: a new claim
        - reinforce: the same claim is confirmed
        - supersede: a current state changed; include replaces_claim_id only when an id is known
        - close: a plan/state ended

        Use concise snake_case predicates. A scene should span the new messages and be omitted for trivial filler.
        Output strict JSON only:
        {"operations":[{"op":"add","subject":"user","predicate":"likes","object":"tea","statement":"User likes tea.","kind":"durative","reality":"real","confidence":0.9,"importance":3,"valid_from":null,"valid_until":null,"replaces_claim_id":null,"sensitive":false,"source_message_id":"..."}],"episode":{"title":"...","summary":"...","scene_key":"stable-topic-key","importance":3,"reality":"real","frame":null,"event_start":null,"event_end":null}}

        EXISTING_MEMORIES:
        ${existing.joinToString("\n") { item ->
            val claimId = item.stableId.removePrefix("claim:").toLongOrNull()
            if (claimId != null) "- claim_id=$claimId ${item.text}" else "- ${item.text}"
        }}

        NEW_MESSAGES:
        ${pending.joinToString("\n") { message ->
            "id=${message.id} observed_at=${message.observedAt} role=${if (message.role == 0) "user" else "assistant"}: ${message.text.take(2_000)}"
        }}
    """.trimIndent()

    private fun parseExtraction(text: String): MemoryExtractionEnvelope {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return MemoryExtractionEnvelope()
        return runCatching {
            JsonInstant.decodeFromString<MemoryExtractionEnvelope>(text.substring(start, end + 1))
        }.getOrElse {
            PlatformLog.w(TAG, "Memory extraction returned invalid JSON")
            MemoryExtractionEnvelope()
        }
    }

    companion object {
        private const val TAG = "TemporalMemoryIngest"
        private const val MAX_PENDING_MESSAGES = 20
        const val KEY_ASSISTANT_ID = "assistant_id"
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_INDEX_ONLY = "index_only"

        fun enqueue(context: Context, assistantId: String, conversationId: String, indexOnly: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<TemporalMemoryIngestWorker>()
                .setInputData(
                    workDataOf(
                        KEY_ASSISTANT_ID to assistantId,
                        KEY_CONVERSATION_ID to conversationId,
                        KEY_INDEX_ONLY to indexOnly,
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "memory-v3-$assistantId-$conversationId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
