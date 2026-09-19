package me.rerere.rikkahub.ui.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputSttTest {
    @Test
    fun pendingSttTranscriptIsSkippedAfterSendClearsComposer() {
        assertTrue(shouldApplyPendingSttTranscript(accept = true, epochAtCapture = 0, epochNow = 0))
        assertFalse(shouldApplyPendingSttTranscript(accept = true, epochAtCapture = 0, epochNow = 1))
        assertFalse(shouldApplyPendingSttTranscript(accept = false, epochAtCapture = 0, epochNow = 0))
    }

    @Test
    fun committedSttTranscriptDoesNotDuplicateLiveOverlayText() {
        assertEquals("hello world", mergeCommittedSttTranscript("", "hello world"))
        assertEquals("hello world", mergeCommittedSttTranscript("hello world", "hello world"))
        assertEquals("please hello world", mergeCommittedSttTranscript("please hello world", "hello world"))
        assertEquals("please hello world", mergeCommittedSttTranscript("please", "hello world"))
        assertEquals("please", mergeCommittedSttTranscript("please", "   "))
    }
}
