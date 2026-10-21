package me.rerere.rikkahub.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.context.ContextCompressionStyle
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.normalizeContextManagement
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextManagementMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.canApplyContextCompression
import me.rerere.rikkahub.data.model.effectiveContextManagementMode
import me.rerere.rikkahub.data.model.preserveNewerContextSummary
import me.rerere.rikkahub.data.model.restorePreviousContextCheckpoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ContextCompressionTest {
    private val model = Model(modelId = "chat", contextWindowTokens = 32_000)
    private fun chat(turns: Int = 10): Conversation = Conversation(
        assistantId = Uuid.random(),
        messageNodes = (0 until turns).flatMap {
            listOf(MessageNode.of(UIMessage.user("Requirement $it " + "detail ".repeat(40))),
                MessageNode.of(UIMessage.assistant("Decision $it " + "outcome ".repeat(40))))
        },
    )

    @Test
    fun legacySwitchesMigrateToOneModeAndNoCompetingPruningLimits() {
        val assistants = listOf(
            Assistant(),
            Assistant(smartContextManagement = false, autoRegenerateSummary = true, maxHistoryMessages = 5),
            Assistant(smartContextManagement = false, autoRegenerateSummary = false),
            Assistant(contextManagementMode = ContextManagementMode.MANUAL, smartContextManagement = true),
        )
        val normalized = Settings(assistants = assistants).normalizeContextManagement()
        assertEquals(listOf(ContextManagementMode.AUTOMATIC, ContextManagementMode.AUTOMATIC,
            ContextManagementMode.MANUAL, ContextManagementMode.MANUAL),
            normalized.assistants.map { it.effectiveContextManagementMode })
        assertTrue(normalized.assistants.all { it.smartContextManagement && it.maxHistoryMessages == null })
        assertEquals(normalized, normalized.normalizeContextManagement())
    }

    @Test
    fun previewProtectsRequestedExchangesAndDoesNotAdvertiseNothingToCompress() {
        val assistant = Assistant(contextRecentTurns = 6)
        val conversation = chat()
        val preview = previewContextCompression(conversation, assistant, model)!!
        assertEquals(6, preview.recentExchanges)
        assertEquals(8, preview.messagesToCompress)
        assertEquals(0, preview.slice.startIndex)
        assertEquals(7, preview.slice.lastIndexToSummarize)
        assertNull(previewContextCompression(chat(6), assistant, model))
    }

    @Test
    fun explicitContextResetIsNotSummarizedBackIntoTheConversation() {
        val conversation = chat(10).copy(truncateIndex = 10)
        val preview = previewContextCompression(conversation, Assistant(), model)!!
        assertEquals(10, preview.slice.startIndex)
        assertEquals(10, activeContextMessages(conversation).size)
        assertNull(previewContextCompression(conversation.copy(truncateIndex = 20), Assistant(), model))
    }

    @Test
    fun applyingCheckpointAllowsNewRepliesButRejectsEditsAndChangedContext() {
        val source = chat(6)
        val appended = source.copy(messageNodes = source.messageNodes + MessageNode.of(UIMessage.user("New request")))
        assertTrue(canApplyContextCompression(source, appended, 5))
        val edited = source.copy(messageNodes = listOf(MessageNode.of(UIMessage.user("Edited"))) + source.messageNodes.drop(1))
        assertFalse(canApplyContextCompression(source, edited, 5))
        assertFalse(canApplyContextCompression(source, source.copy(lastRefreshTime = 1), 5))
        assertFalse(canApplyContextCompression(source, source.copy(truncateIndex = 4), 5))
    }

    @Test
    fun streamingSnapshotCannotUndoANewerCheckpointOrAnExplicitRestore() {
        val before = chat(6)
        val compressed = before.copy(contextSummary = "checkpoint", contextSummaryUpToIndex = 5, lastRefreshTime = 10)
        assertEquals("checkpoint", preserveNewerContextSummary(compressed, before).contextSummary)
        val restored = compressed.copy(contextSummary = null, contextSummaryUpToIndex = -1, lastRefreshTime = 20)
        assertNull(preserveNewerContextSummary(restored, compressed).contextSummary)
        val edited = before.copy(messageNodes = listOf(MessageNode.of(UIMessage.user("Different branch"))))
        assertNull(preserveNewerContextSummary(compressed, edited).contextSummary)
    }

    @Test
    fun undoRestoresThePrecedingCheckpointWithoutDeletingMessages() {
        val first = "### [Milestone 1] <!-- lastchat:coverage=0..3 -->\nFirst checkpoint"
        val conversation = chat(6).copy(
            contextSummary = first + "\n\n### [Milestone 2] <!-- lastchat:coverage=4..7 -->\nSecond checkpoint",
            contextSummaryUpToIndex = 7,
            lastRefreshTime = 5,
        )
        val undone = restorePreviousContextCheckpoint(conversation)
        assertEquals(first, undone.contextSummary)
        assertEquals(3, undone.contextSummaryUpToIndex)
        assertEquals(conversation.messageNodes, undone.messageNodes)
        assertTrue(undone.lastRefreshTime > conversation.lastRefreshTime)
        val fullyRestored = restorePreviousContextCheckpoint(undone)
        assertNull(fullyRestored.contextSummary)
        assertEquals(-1, fullyRestored.contextSummaryUpToIndex)
        assertEquals(conversation.messageNodes, fullyRestored.messageNodes)
    }

    @Test
    fun legacySummaryCanRestoreAllOriginalContext() {
        val original = chat(6).copy(contextSummary = "old checkpoint", contextSummaryUpToIndex = 5)
        assertNull(restorePreviousContextCheckpoint(original).contextSummary)
        assertEquals(original.messageNodes, restorePreviousContextCheckpoint(original).messageNodes)
    }

    @Test
    fun closingAWaiterDoesNotCancelBackgroundWorkAndOtherWaitersJoinIt() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val jobs = ContextCompressionJobs<String, Int>(scope)
                val release = CompletableDeferred<Unit>()
                val started = CompletableDeferred<Unit>()
                var calls = 0
                val first = async { jobs.run("chat") { calls++; started.complete(Unit); release.await(); 42 } }
                started.await()
                val second = async { jobs.run("chat") { calls++; -1 } }
                first.cancelAndJoin()
                release.complete(Unit)
                assertEquals(42, second.await())
                assertEquals(1, calls)
            } finally { scope.cancel() }
        }
    }

    @Test
    fun stoppingACoordinatedJobCancelsItAndAllowsRetry() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val jobs = ContextCompressionJobs<String, Int>(scope)
                val started = CompletableDeferred<Unit>()
                val waiting = async {
                    jobs.run("chat") { started.complete(Unit); CompletableDeferred<Unit>().await(); 1 }
                }
                started.await()
                jobs.cancel("chat")
                assertTrue(runCatching { waiting.await() }.isFailure)
                assertEquals(7, jobs.run("chat") { 7 })
            } finally { scope.cancel() }
        }
    }
}
