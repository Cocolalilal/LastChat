package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.utils.JsonInstant

/**
 * Resolves the message-id-anchored extraction watermark (§7.3) to an index into the currently
 * selected branch, and captures the hard invariant in one pure, testable place.
 *
 * The watermark is stored as the id of the last processed message, not a list index: deleting or
 * reordering messages below it shifts indices and would silently corrupt an index-based window.
 * When the anchored message is still present, its current index is authoritative and self-correcting.
 * When it is gone (deleted, or on an abandoned branch), we re-resolve to the nearest surviving
 * *earlier* message we can prove was processed — the newest current message whose id appears in this
 * conversation's provenance. That is always ≤ the true processed boundary, so the watermark can only
 * ever be pulled *back* (harmless re-reading, absorbed by the dedup gate), never pushed forward past
 * a message no pass has read.
 */
object MemoryWatermark {

    /**
     * @param anchorMessageId id of the last processed message, or null if nothing processed yet
     * @param currentMessageIds ids of the currently selected branch, in order
     * @param processedMessageIds ids known to have been processed (union of this conversation's
     *   provenance message ids) — used only as the fallback when the anchor is gone
     * @return the index up to and including which extraction has run, or -1 if none
     */
    fun resolveIndex(
        anchorMessageId: String?,
        currentMessageIds: List<String>,
        processedMessageIds: Set<String>,
    ): Int {
        if (anchorMessageId == null) return -1
        val direct = currentMessageIds.indexOf(anchorMessageId)
        if (direct >= 0) return direct
        // Anchor is gone: fall back to the newest surviving message we can prove was processed.
        for (i in currentMessageIds.indices.reversed()) {
            if (currentMessageIds[i] in processedMessageIds) return i
        }
        return -1
    }

    /** Parse a `memory_provenance.message_ids` JSON array column into ids. */
    fun parseMessageIds(json: String): List<String> =
        runCatching { JsonInstant.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())
}
