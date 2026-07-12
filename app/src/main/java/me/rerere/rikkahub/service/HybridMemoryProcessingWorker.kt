package me.rerere.rikkahub.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.entity.MemoryConversationDigestEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphNodeEntity
import me.rerere.rikkahub.data.model.MemoryDocumentKind
import me.rerere.rikkahub.data.model.MemorySystemType
import me.rerere.rikkahub.data.model.effectiveAdvancedEntryMemoryEnabled
import me.rerere.rikkahub.data.model.effectiveRecentContinuityEnabled
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.HybridMemoryRepository
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

class HybridMemoryProcessingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val hybridMemoryRepository: HybridMemoryRepository by inject()
    private val providerManager: ProviderManager by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: return@withContext Result.failure()
        val assistantId = inputData.getString(KEY_ASSISTANT_ID) ?: return@withContext Result.failure()
        val conversation = conversationRepository.getConversationById(runCatching { Uuid.parse(conversationId) }.getOrNull() ?: return@withContext Result.failure())
            ?: return@withContext Result.success()
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getAssistantById(runCatching { Uuid.parse(assistantId) }.getOrNull() ?: return@withContext Result.failure())
            ?: return@withContext Result.success()
        if (!assistant.enableMemory || conversation.currentMessages.isEmpty()) return@withContext Result.success()

        val signature = hybridMemoryRepository.conversationBranchSignature(conversation)
        val fallbackSummary = conversation.currentMessages.takeLast(8)
            .joinToString(" / ") { "${it.role}: ${it.toContentText().replace(Regex("\\s+"), " ").take(180)}" }
            .take(700)
        val now = System.currentTimeMillis()

        try {
            hybridMemoryRepository.indexConversation(conversation)
            val shouldDigest = assistant.memorySystem == MemorySystemType.DOCUMENT_BASED ||
                assistant.effectiveRecentContinuityEnabled() || assistant.effectiveAdvancedEntryMemoryEnabled()
            val inputDocuments = if (assistant.memorySystem == MemorySystemType.DOCUMENT_BASED) {
                hybridMemoryRepository.getDocuments(assistant).associateBy { it.kind }
            } else {
                emptyMap()
            }
            val model = settings.summarizerModelId?.let(settings::findModelById)
            val provider = model?.findProvider(settings.providers)
            val parsed = if (shouldDigest && model != null && provider != null) {
                val handler = providerManager.getProviderByType(provider)
                val transcript = conversation.currentMessages.joinToString("\n") { "${it.role}: ${it.toContentText().take(1500)}" }.takeLast(24_000)
                val prompt = """
                    Update this character's memory from one completed or inactive conversation.
                    Return JSON only with keys: digest (string <=700 chars), importance (1..5), open_threads (array of strings), user_profile (complete replacement <=${assistant.userProfileCharLimit} chars), character_memory (complete replacement <=${assistant.characterMemoryCharLimit} chars), nodes (array of {label,kind,summary,frame,confidence,importance}), edges (array of {subject,predicate,object,statement,confidence,importance,frame}).
                    Preserve useful existing document content, correct stale statements, do not invent facts, and keep fictional continuity distinct.

                    Current User Profile:
                    ${inputDocuments[MemoryDocumentKind.USER_PROFILE.name]?.content.orEmpty()}

                    Current Character Memory:
                    ${inputDocuments[MemoryDocumentKind.CHARACTER_MEMORY.name]?.content.orEmpty()}

                    Conversation:
                    $transcript
                """.trimIndent()
                val response = handler.generateText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = settings.buildSummarizerGenerationParams(model, temperature = 0.2f),
                ).choices.firstOrNull()?.message?.toContentText().orEmpty()
                val jsonText = response.substringAfter('{', "").substringBeforeLast('}', "")
                if (jsonText.isBlank()) null else JsonInstant.parseToJsonElement("{$jsonText}") as? JsonObject
            } else null

            val applied = hybridMemoryRepository.inTransaction {
                val currentConversation = conversationRepository.getConversationById(Uuid.parse(conversationId))
                    ?: return@inTransaction true
                if (hybridMemoryRepository.conversationBranchSignature(currentConversation) != signature) {
                    return@inTransaction false
                }
                if (assistant.memorySystem == MemorySystemType.DOCUMENT_BASED) {
                    val currentDocuments = hybridMemoryRepository.getDocuments(assistant).associateBy { it.kind }
                    val documentsChanged = MemoryDocumentKind.entries.any { kind ->
                        currentDocuments[kind.name]?.revision != inputDocuments[kind.name]?.revision
                    }
                    if (documentsChanged) return@inTransaction false
                }

                if (shouldDigest) {
                    val digestText = parsed?.get("digest")?.jsonPrimitive?.contentOrNull?.take(700).orEmpty().ifBlank { fallbackSummary }
                    val openThreads = parsed?.get("open_threads") as? JsonArray
                    hybridMemoryRepository.upsertDigest(
                        MemoryConversationDigestEntity(
                            id = "digest:$conversationId",
                            assistantId = assistantId,
                            conversationId = conversationId,
                            summary = digestText,
                            openThreads = openThreads?.toString() ?: "[]",
                            importance = parsed?.get("importance")?.jsonPrimitive?.intOrNull?.coerceIn(1, 5) ?: 3,
                            branchSignature = signature,
                            eventStart = conversation.createAt.toEpochMilli(),
                            eventEnd = conversation.updateAt.toEpochMilli(),
                            recordedAt = now,
                        )
                    )
                }

                if (assistant.memorySystem == MemorySystemType.DOCUMENT_BASED && parsed != null) {
                    listOf(
                        MemoryDocumentKind.USER_PROFILE to "user_profile",
                        MemoryDocumentKind.CHARACTER_MEMORY to "character_memory",
                    ).forEach { (kind, key) ->
                        val input = inputDocuments[kind.name] ?: return@forEach
                        val replacement = parsed[key]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val result = hybridMemoryRepository.updateDocument(
                            assistant, kind, replacement, input.revision, "Conversation processed", "automatic"
                        )
                        if (!result.applied) error(result.error ?: "Document update was rejected")
                    }
                    applyGraph(parsed, assistantId, conversationId, fallbackSummary, now)
                }
                hybridMemoryRepository.markProcessed(conversationId, assistantId, signature)
                true
            }
            if (!applied) return@withContext Result.retry()
            runCatching { hybridMemoryRepository.backfillEmbeddings(assistantId) }
            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            hybridMemoryRepository.markProcessed(conversationId, assistantId, signature, error.message)
            Result.retry()
        }
    }

    private suspend fun applyGraph(parsed: JsonObject, assistantId: String, conversationId: String, excerpt: String, now: Long) {
        val nodes = (parsed["nodes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val ids = mutableMapOf<String, String>()
        nodes.forEach { node ->
            val label = node["label"]?.jsonPrimitive?.contentOrNull?.trim()?.take(80).orEmpty()
            if (label.isBlank()) return@forEach
            val id = "node:$assistantId:${label.lowercase().hashCode().toUInt().toString(16)}"
            ids[label.lowercase()] = id
            hybridMemoryRepository.upsertGraphNode(
                MemoryGraphNodeEntity(
                    id = id,
                    assistantId = assistantId,
                    label = label,
                    normalizedLabel = label.lowercase(),
                    kind = node["kind"]?.jsonPrimitive?.contentOrNull ?: "topic",
                    summary = node["summary"]?.jsonPrimitive?.contentOrNull?.take(240),
                    createdAt = now,
                    updatedAt = now,
                    frame = node["frame"]?.jsonPrimitive?.contentOrNull ?: "real",
                    confidence = node["confidence"]?.jsonPrimitive?.floatOrNull?.coerceIn(0f, 1f) ?: 0.6f,
                    importance = node["importance"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 5) ?: 3,
                ),
                conversationId,
                excerpt,
            )
        }
        (parsed["edges"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEach { edge ->
            val subject = edge["subject"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val objectLabel = edge["object"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val predicate = edge["predicate"]?.jsonPrimitive?.contentOrNull?.trim()?.take(64).orEmpty()
            if (subject.isBlank() || objectLabel.isBlank() || predicate.isBlank()) return@forEach
            val subjectId = ids[subject.lowercase()] ?: return@forEach
            val objectId = ids[objectLabel.lowercase()]
            val statement = edge["statement"]?.jsonPrimitive?.contentOrNull?.take(240) ?: "$subject $predicate $objectLabel"
            val id = "edge:$assistantId:${"$subjectId|$predicate|${objectId ?: objectLabel}".hashCode().toUInt().toString(16)}"
            hybridMemoryRepository.upsertGraphEdge(
                MemoryGraphEdgeEntity(
                    id = id, assistantId = assistantId, subjectId = subjectId, predicate = predicate,
                    objectId = objectId, objectValue = objectLabel.takeIf { objectId == null }, statement = statement,
                    confidence = edge["confidence"]?.jsonPrimitive?.floatOrNull?.coerceIn(0f, 1f) ?: 0.6f,
                    importance = edge["importance"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 5) ?: 3,
                    createdAt = now, updatedAt = now,
                    frame = edge["frame"]?.jsonPrimitive?.contentOrNull ?: "real",
                ),
                conversationId,
                excerpt,
            )
        }
    }

    companion object {
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_ASSISTANT_ID = "assistant_id"
    }
}
