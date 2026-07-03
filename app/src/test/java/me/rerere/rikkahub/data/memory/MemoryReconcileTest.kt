package me.rerere.rikkahub.data.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Property tests for the three §7.2/§7.3 corrections: message-id watermark integrity across
 * deletion, branch demotion only when all provenance is beyond the divergence, and per-scope
 * write serialization.
 */
class MemoryReconcileTest {

    // ---------------- Fix 1: watermark integrity (MemoryWatermark) ----------------

    @Test
    fun presentAnchorResolvesToItsCurrentIndex() {
        val ids = listOf("a", "b", "c", "d", "e")
        assertEquals(2, MemoryWatermark.resolveIndex("c", ids, setOf("a", "b", "c")))
    }

    @Test
    fun deletingAMessageBelowTheAnchorDoesNotSkipUnread() {
        // Index-based bug: watermark index 2 pointed at "c"; deleting "b" shifts "d" into index 2,
        // and an index window would resume at "e", silently skipping the unread "d".
        val afterDelete = listOf("a", "c", "d", "e") // "b" removed; anchor "c" survives
        val resolved = MemoryWatermark.resolveIndex("c", afterDelete, setOf("a", "b", "c"))
        assertEquals(1, resolved) // "c" is now at index 1 → next window starts at "d", nothing skipped
    }

    @Test
    fun deletingTheAnchorReanchorsToNearestSurvivingProcessedMessage() {
        // "c" was the anchor and is deleted; "d"/"e" were never processed. We must fall back to "b"
        // (the newest surviving message we can prove was processed), never forward to "d"/"e".
        val afterDelete = listOf("a", "b", "d", "e")
        val resolved = MemoryWatermark.resolveIndex("c", afterDelete, setOf("a", "b", "c"))
        assertEquals(1, resolved) // "b" → next window starts at "d"; no unread message skipped
    }

    @Test
    fun deletingTheAnchorWithNoSurvivingProcessedMessageResetsToStart() {
        val afterDelete = listOf("x", "y") // none of the processed ids survive
        assertEquals(-1, MemoryWatermark.resolveIndex("c", afterDelete, setOf("a", "b", "c")))
    }

    @Test
    fun nullAnchorMeansNothingProcessed() {
        assertEquals(-1, MemoryWatermark.resolveIndex(null, listOf("a", "b"), emptySet()))
    }

    // ---------------- Fix 2: branch demotion (MemoryBranchLogic) ----------------

    private fun row(convId: String?, vararg ids: String) = MemoryBranchLogic.ProvRow(convId, ids.toList())

    @Test
    fun demotesWhenAllEvidenceIsOnAnAbandonedBranch() {
        // Node's only evidence is message "m2", which is gone from the selected branch but still
        // exists as an unselected version → abandoned branch → demote.
        val rows = listOf(row("conv", "m2"))
        val demote = MemoryBranchLogic.shouldDemote(
            rows,
            conversationId = "conv",
            survivingMessageIds = setOf("m1", "m3"),
            allExistingMessageIds = setOf("m1", "m2", "m3"),
        )
        assertTrue(demote)
    }

    @Test
    fun keepsWhenAlsoEvidencedOnTheSurvivingBranch() {
        val rows = listOf(row("conv", "m2"), row("conv", "m1"))
        val demote = MemoryBranchLogic.shouldDemote(
            rows,
            conversationId = "conv",
            survivingMessageIds = setOf("m1", "m3"), // m1 survives
            allExistingMessageIds = setOf("m1", "m2", "m3"),
        )
        assertFalse(demote)
    }

    @Test
    fun keepsWhenEvidencedInAnotherConversation() {
        val rows = listOf(row("conv", "m2"), row("other", "z9"))
        val demote = MemoryBranchLogic.shouldDemote(
            rows,
            conversationId = "conv",
            survivingMessageIds = setOf("m1"),
            allExistingMessageIds = setOf("m1", "m2"),
        )
        assertFalse(demote)
    }

    @Test
    fun plainDeletionDoesNotDemote() {
        // "m2" is gone from the conversation entirely (not an unselected version) → diary-burned, keep.
        val rows = listOf(row("conv", "m2"))
        val demote = MemoryBranchLogic.shouldDemote(
            rows,
            conversationId = "conv",
            survivingMessageIds = setOf("m1", "m3"),
            allExistingMessageIds = setOf("m1", "m3"), // m2 no longer exists anywhere
        )
        assertFalse(demote)
    }

    @Test
    fun noProvenanceMeansNoDemotion() {
        assertFalse(MemoryBranchLogic.shouldDemote(emptyList(), "conv", setOf("m1"), setOf("m1")))
    }

    @Test
    fun pureVersionSwitchWithoutRegenerationDemotesAbandonedBranch() {
        // A common user turn plus an assistant turn with two versions; version A is selected.
        val userMsg = UIMessage.user("I study CS at TU Wien")
        val versionA = UIMessage.assistant("Nice, computer science!")
        val versionB = UIMessage.assistant("Tell me more about that.")
        val branchedNode = MessageNode(messages = listOf(versionA, versionB), selectIndex = 0)
        val conversation = Conversation(
            id = Uuid.parse("00000000-0000-0000-0000-0000000004a1"),
            assistantId = Uuid.parse("00000000-0000-0000-0000-0000000004a2"),
            title = "branch switch",
            createAt = Instant.parse("2026-07-03T10:00:00Z"),
            updateAt = Instant.parse("2026-07-03T10:05:00Z"),
            messageNodes = listOf(userMsg.toMessageNode(), branchedNode),
        )
        val convId = conversation.id.toString()

        // Before the switch: version A is selected, so its facts are on the surviving branch — keep.
        val before = MemoryBranchLogic.branchSets(conversation)
        assertTrue(versionA.id.toString() in before.surviving)
        assertFalse(
            MemoryBranchLogic.shouldDemote(
                listOf(row(convId, versionA.id.toString())), convId, before.surviving, before.allExisting,
            )
        )

        // User switches to version B — no regeneration. Version A's branch is now abandoned but its
        // message still exists as an unselected version.
        val switched = conversation.copy(
            messageNodes = conversation.messageNodes.map { node ->
                if (node.id == branchedNode.id) node.copy(selectIndex = 1) else node
            }
        )
        val after = MemoryBranchLogic.branchSets(switched)
        assertTrue(versionB.id.toString() in after.surviving)
        assertFalse(versionA.id.toString() in after.surviving)
        assertTrue(versionA.id.toString() in after.allExisting)

        // A fact extracted from version A must demote immediately; the common user turn's facts stay.
        assertTrue(
            MemoryBranchLogic.shouldDemote(
                listOf(row(convId, versionA.id.toString())), convId, after.surviving, after.allExisting,
            )
        )
        assertFalse(
            MemoryBranchLogic.shouldDemote(
                listOf(row(convId, userMsg.id.toString())), convId, after.surviving, after.allExisting,
            )
        )
    }

    // ---------------- Fix 3: per-scope serialization (MemoryScopeLocks) ----------------

    @Test
    fun sameScopeSerializesConcurrentReadModifyWrite() = runBlocking {
        val locks = MemoryScopeLocks()
        var counter = 0
        val jobs = (1..200).map {
            launch(Dispatchers.Default) {
                locks.withScope("assistant-A") {
                    val snapshot = counter
                    yield() // force a suspension point inside the critical section
                    counter = snapshot + 1
                }
            }
        }
        jobs.joinAll()
        // Without the mutex this loses updates and lands well below 200.
        assertEquals(200, counter)
    }

    @Test
    fun differentScopesGetDistinctMutexes() {
        val locks = MemoryScopeLocks()
        assertSame(locks.forScope("A"), locks.forScope("A"))
        assertNotSame(locks.forScope("A"), locks.forScope("B"))
    }
}
