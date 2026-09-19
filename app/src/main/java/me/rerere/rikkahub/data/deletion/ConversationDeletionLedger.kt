package me.rerere.rikkahub.data.deletion

import kotlinx.coroutines.Job
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

/**
 * Tracks recently deleted conversations so Undo can restore them, and so in-flight
 * generation persist cannot re-insert a row the user just deleted.
 *
 * After the undo window expires the snapshot is dropped (files/episodes may then be
 * cleaned up) but the tombstone remains for the process lifetime.
 */
internal class ConversationDeletionLedger {
    private val lock = Any()
    private val snapshots = mutableMapOf<Uuid, Conversation>()
    private val tombstones = mutableSetOf<Uuid>()
    private val jobs = mutableMapOf<Uuid, Job>()

    fun tombstone(conversationId: Uuid) = synchronized(lock) {
        tombstones.add(conversationId)
    }

    fun isTombstoned(conversationId: Uuid): Boolean = synchronized(lock) {
        conversationId in tombstones
    }

    fun remember(conversationId: Uuid, conversation: Conversation, job: Job) = synchronized(lock) {
        tombstones.add(conversationId)
        snapshots[conversationId] = conversation
        jobs[conversationId] = job
    }

    /**
     * Upgrade the undo snapshot only if this conversation is still waiting to be undone.
     * Returns false if the user already undid (tombstone lifted) so a late DB fetch cannot
     * re-delete the restored row.
     */
    fun rememberIfTombstoned(conversationId: Uuid, conversation: Conversation, job: Job): Boolean =
        synchronized(lock) {
            if (conversationId !in tombstones) return false
            snapshots[conversationId] = conversation
            jobs[conversationId] = job
            true
        }

    fun updateSnapshotIfPresent(conversationId: Uuid, conversation: Conversation) = synchronized(lock) {
        if (snapshots.containsKey(conversationId)) {
            snapshots[conversationId] = conversation
        }
    }

    fun cancelJob(conversationId: Uuid): Job? = synchronized(lock) {
        jobs.remove(conversationId)
    }

    /**
     * Undo: restore snapshot and lift the persist tombstone.
     * Returns null when the undo window has already expired (or never started).
     */
    fun takeForUndo(conversationId: Uuid): Conversation? = synchronized(lock) {
        val snapshot = snapshots.remove(conversationId) ?: return null
        jobs.remove(conversationId)
        tombstones.remove(conversationId)
        snapshot
    }

    /**
     * Undo window expired: drop the snapshot (caller should finalize file/episode cleanup)
     * but keep the tombstone so persist still cannot resurrect the row.
     */
    fun expireUndoWindow(conversationId: Uuid): Conversation? = synchronized(lock) {
        jobs.remove(conversationId)
        snapshots.remove(conversationId)
    }

    fun hasUndoSnapshot(conversationId: Uuid): Boolean = synchronized(lock) {
        snapshots.containsKey(conversationId)
    }
}
