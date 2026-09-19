package me.rerere.rikkahub.data.deletion

import kotlinx.coroutines.Job
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationDeletionLedgerTest {
    @Test
    fun persistIsBlockedAfterTombstoneUntilUndo() {
        val ledger = ConversationDeletionLedger()
        val id = Uuid.random()
        val conversation = Conversation.ofId(id)
        val job = Job()

        ledger.remember(id, conversation, job)

        assertTrue(ledger.isTombstoned(id))
        assertTrue(ledger.hasUndoSnapshot(id))

        val restored = ledger.takeForUndo(id)
        assertEquals(id, restored?.id)
        assertFalse(ledger.isTombstoned(id))
        assertFalse(ledger.hasUndoSnapshot(id))
        assertTrue(job.isCancelled.not())
    }

    @Test
    fun expireDropsSnapshotButKeepsTombstone() {
        val ledger = ConversationDeletionLedger()
        val id = Uuid.random()
        val conversation = Conversation.ofId(id)
        val job = Job()
        ledger.remember(id, conversation, job)

        val expired = ledger.expireUndoWindow(id)
        assertNotNull(expired)
        assertTrue(ledger.isTombstoned(id))
        assertFalse(ledger.hasUndoSnapshot(id))
        assertNull(ledger.takeForUndo(id))
        assertTrue(ledger.isTombstoned(id))
    }

    @Test
    fun undoAfterExpiryIsANoOp() {
        val ledger = ConversationDeletionLedger()
        val id = Uuid.random()
        ledger.tombstone(id)

        assertTrue(ledger.isTombstoned(id))
        assertNull(ledger.takeForUndo(id))
        assertTrue(
            "Expired undo must not lift the persist tombstone",
            ledger.isTombstoned(id),
        )
    }

    @Test
    fun rememberIfTombstonedDoesNotReviveAfterUndo() {
        val ledger = ConversationDeletionLedger()
        val id = Uuid.random()
        val conversation = Conversation.ofId(id)
        ledger.remember(id, conversation, Job())
        ledger.takeForUndo(id)

        assertFalse(ledger.isTombstoned(id))
        assertFalse(ledger.rememberIfTombstoned(id, conversation.copy(title = "late"), Job()))
        assertFalse(ledger.isTombstoned(id))
        assertNull(ledger.takeForUndo(id))
    }

    @Test
    fun updateSnapshotOnlyWhileUndoIsPending() {
        val ledger = ConversationDeletionLedger()
        val id = Uuid.random()
        val original = Conversation.ofId(id)
        ledger.remember(id, original, Job())

        val richer = original.copy(title = "Full copy")
        ledger.updateSnapshotIfPresent(id, richer)
        assertEquals("Full copy", ledger.takeForUndo(id)?.title)

        ledger.updateSnapshotIfPresent(id, original)
        assertNull(ledger.takeForUndo(id))
    }
}
