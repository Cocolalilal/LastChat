package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.currentVersionMessages
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

@Serializable data class MemoryReceipt(val revision: String, val at: Long)

/** One durable queue per character; batching limits one run, never the number of daily requests. */
class MemoryLearningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val repository: CharacterMemoryRepository by inject()
    private val processor: MemoryProcessor by inject()
    private val settings: SettingsStore by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        settings.settingsFlow.first { !it.init }
        val owner = inputData.getString("owner") ?: return@withContext Result.failure()
        try {
            val assistant = repository.owner(owner)
            val configuration = repository.configuration(owner)
            if (!assistant.enableMemory) return@withContext Result.success()
            repository.initialise(owner)
            var meta = repository.metadata(owner)
            if (meta.paused || meta.cancelled) return@withContext Result.success()
            if (meta.inFlight != null) {
                repository.database.withTransaction {
                    val latest = repository.metadata(owner)
                    repository.put(owner,"meta","meta",latest.copy(paused=true,error="Memory processing was interrupted and may have been billed. Use Resume to retry the unfinished work."))
                }
                return@withContext Result.success()
            }
            val explicitUpdate = inputData.getBoolean("refresh",false)
            if (assistant.memoryMode() == MemoryExperience.BASIC && !assistant.memoryDreaming && !explicitUpdate) return@withContext Result.success()
            if (assistant.memoryMode() == MemoryExperience.ADVANCED && !meta.activeAdvanced && !meta.preparing) {
                // Historical preparation is started from the memory page; old notes stay usable meanwhile.
                return@withContext Result.success()
            }
            val staged = meta.preparing && assistant.memoryMode() == MemoryExperience.ADVANCED
            val preparationId = meta.preparationId
            val receiptKind = if (staged) "candidate-receipt" else "receipt"
            var calls = 0
            if (staged) {
                for (note in repository.view(owner).notes.filter{!it.sourceOnly&&it.context==assistant.memoryContext}) {
                    for (paragraph in note.paragraphs) {
                        val revision = memoryHash(paragraph.text)
                        val receipt = "note:${note.id}:${paragraph.id}:$revision"
                        if (repository.dao.get(owner, receiptKind, receipt) != null) continue
                        val evidence = MemoryEvidence(messageId = note.id, revision = revision, origin = "saved note", paragraphId = paragraph.id)
                        for ((index, text) in paragraph.text.chunked(6000).withIndex()) {
                            val chunkReceipt = "$receipt:$index"
                            if (repository.dao.get(owner, receiptKind, chunkReceipt) != null) continue
                            val before = repository.metadata(owner)
                            if (before.paused || before.cancelled || repository.configuration(owner) != configuration) return@withContext Result.success()
                            repository.database.withTransaction {
                                val latest = repository.metadata(owner)
                                check(latest.policyRevision == before.policyRevision && !latest.paused && !latest.cancelled)
                                repository.put(owner,"meta","meta",latest.copy(inFlight=chunkReceipt))
                            }
                            val draft = processor.extract(owner, listOf(MemoryInput("note-$index", text, evidence.copy(quote = text))), staged = true)
                            repository.database.withTransaction {
                                check(draft.configuration == configuration) { "Memory configuration changed during preparation." }
                                repository.applyCandidate(owner, draft)
                                repository.put(owner, receiptKind, chunkReceipt, MemoryReceipt(revision, System.currentTimeMillis()))
                                repository.put(owner,"meta","meta",repository.metadata(owner).copy(inFlight=null))
                            }
                            calls++
                            if (calls >= 8) { enqueue(applicationContext, owner, 5); return@withContext Result.success() }
                        }
                        repository.put(owner, receiptKind, receipt, MemoryReceipt(revision, System.currentTimeMillis()))
                    }
                }
            }
            val conversations = repository.database.conversationDao().getConversationsOfAssistant(owner).first().sortedBy { it.createAt }
            for ((conversationIndex, conversation) in conversations.withIndex()) {
                currentCoroutineContext().ensureActive()
                meta = repository.metadata(owner)
                if (meta.paused || meta.cancelled || repository.configuration(owner) != configuration) return@withContext Result.success()
                val messages = JsonInstant.decodeFromString<List<MessageNode>>(conversation.nodes).currentVersionMessages()
                    .filter { it.role == MessageRole.USER && it.toText().isNotBlank() && (it.memoryContext == null || it.memoryContext == assistant.memoryContext) }
                for ((messageIndex, message) in messages.withIndex()) {
                    if (assistant.memoryMode() == MemoryExperience.BASIC && !explicitUpdate && meta.learningEnabledAt > 0 &&
                        (message.sentAtEpochMillis ?: message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()) < meta.learningEnabledAt) continue
                    val revision = memoryHash(message.toText())
                    val receiptId = "${message.id}:$revision"
                    if (repository.dao.get(owner, receiptKind, receiptId) != null) continue
                    // Context is bounded by chunks, never by dropping the tail of a long message.
                    val wholeSource = MemoryEvidence(conversation.id, message.id.toString(), revision)
                    val safeText = repository.redact(owner, wholeSource, message.toText())
                    val chunks = safeText.chunked(6000)
                    for ((chunkIndex, chunk) in chunks.withIndex()) {
                        val chunkId = "$receiptId:$chunkIndex"
                        if (repository.dao.get(owner, receiptKind, chunkId) != null) continue
                        val source = MemoryEvidence(conversation.id, message.id.toString(), revision, chunk)
                        val policy = repository.metadata(owner)
                        if (policy.paused || policy.cancelled) return@withContext Result.success()
                        repository.database.withTransaction {
                            val latest = repository.metadata(owner)
                            check(latest.policyRevision == policy.policyRevision && !latest.paused && !latest.cancelled)
                            repository.put(owner, "meta", "meta", latest.copy(progress = "${conversationIndex + 1}/${conversations.size} chats", error = null,inFlight=chunkId))
                        }
                        val earlier = messages.take(messageIndex).takeLast(3).map { previous ->
                            repository.redact(owner,MemoryEvidence(conversation.id,previous.id.toString(),memoryHash(previous.toText())),previous.toText()).takeLast(1500)
                        }.joinToString("\n")
                        val draft = processor.extract(owner, listOf(MemoryInput("source", chunk, source, (message.sentAtEpochMillis?.let { java.time.Instant.ofEpochMilli(it).toString() + " zone " + message.sentAtZoneId } ?: message.createdAt.toString() + " (offset unknown)"))), staged, earlier)
                        currentCoroutineContext().ensureActive()
                        repository.database.withTransaction {
                            val latest = repository.metadata(owner)
                            check(!latest.paused && !latest.cancelled && latest.policyRevision == policy.policyRevision) { "Learning was paused or its settings changed." }
                            check(draft.configuration == configuration) { "Memory configuration changed during learning." }
                            if (draft.changes.isNotEmpty()) {
                                if (staged) repository.applyCandidate(owner, draft) else repository.apply(owner, draft)
                            }
                            repository.put(owner, receiptKind, chunkId, MemoryReceipt(revision, System.currentTimeMillis()))
                            repository.put(owner,"meta","meta",repository.metadata(owner).copy(inFlight=null))
                        }
                        calls++
                        if (calls >= 8) {
                            enqueue(applicationContext, owner, delaySeconds = 5, refresh = explicitUpdate)
                            return@withContext Result.success()
                        }
                    }
                    repository.put(owner, receiptKind, receiptId, MemoryReceipt(revision, System.currentTimeMillis()))
                }
            }
            if (staged && !repository.activateCandidate(owner, preparationId, requireCatchUp = true)) {
                enqueue(applicationContext, owner, delaySeconds = 5)
                return@withContext Result.success()
            }
            if(repository.configuration(owner)!=configuration || repository.metadata(owner).let { it.paused || it.cancelled }) return@withContext Result.success()
            if (assistant.memoryMode() == MemoryExperience.ADVANCED) repository.index(owner)
            if(repository.configuration(owner)!=configuration || repository.metadata(owner).let { it.paused || it.cancelled }) return@withContext Result.success()
            if (assistant.memoryMode() == MemoryExperience.ADVANCED && (calls>0||staged||inputData.getBoolean("refresh",false))) processor.refreshSummary(owner)
            repository.database.withTransaction {
                val latest = repository.metadata(owner)
                if (!latest.paused && !latest.cancelled) repository.put(owner, "meta", "meta", latest.copy(
                    preparing = false, progress = "", error = null, updatedAt = System.currentTimeMillis()))
            }
            Result.success()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            repository.database.withTransaction {
                if(runCatching{repository.owner(owner)}.isSuccess){
                    val latest = repository.metadata(owner)
                    repository.put(owner, "meta", "meta", latest.copy(error = error.message ?: "Memory processing failed. Retry when ready.", progress = ""))
                }
            }
            // Explicit retry after failure avoids infinite or unexpectedly billed replay loops.
            Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context, owner: String, delaySeconds: Long = 0, refresh: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork("character-memory:$owner", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<MemoryLearningWorker>().setInputData(workDataOf("owner" to owner,"refresh" to refresh))
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS).build())
        }
        fun cancel(context: Context, owner: String) { WorkManager.getInstance(context).cancelUniqueWork("character-memory:$owner") }
    }
}
