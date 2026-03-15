package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScheduledMessageWorkSpecTest {
    @Test
    fun uniqueWorkNameIsDeterministicForSameInputs() {
        val first = ScheduledMessageWorkSpec.buildUniqueWorkName(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            reason = "Drink water",
            scheduledAtMillis = 1_700_000_000_000L
        )
        val second = ScheduledMessageWorkSpec.buildUniqueWorkName(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            reason = "Drink water",
            scheduledAtMillis = 1_700_000_000_000L
        )

        assertEquals(first, second)
    }

    @Test
    fun uniqueWorkNameChangesWhenFingerprintChanges() {
        val base = ScheduledMessageWorkSpec.buildUniqueWorkName(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            reason = "Drink water",
            scheduledAtMillis = 1_700_000_000_000L
        )
        val changedReason = ScheduledMessageWorkSpec.buildUniqueWorkName(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            reason = "Stretch",
            scheduledAtMillis = 1_700_000_000_000L
        )
        val changedTime = ScheduledMessageWorkSpec.buildUniqueWorkName(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            reason = "Drink water",
            scheduledAtMillis = 1_700_000_000_001L
        )

        assertNotEquals(base, changedReason)
        assertNotEquals(base, changedTime)
    }

    @Test
    fun inputDataContainsSchedulingMetadata() {
        val data = ScheduledMessageWorkSpec.buildInputData(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            reason = "Drink water",
            createdAtMillis = 1_700_000_000_000L,
            scheduledAtMillis = 1_700_000_300_000L
        )

        assertEquals("assistant-1", data.getString(ScheduledMessageWorkSpec.KEY_ASSISTANT_ID))
        assertEquals("conversation-1", data.getString(ScheduledMessageWorkSpec.KEY_CONVERSATION_ID))
        assertEquals("Drink water", data.getString(ScheduledMessageWorkSpec.KEY_REASON))
        assertEquals(1_700_000_000_000L, data.getLong(ScheduledMessageWorkSpec.KEY_CREATED_AT, -1L))
        assertEquals(1_700_000_300_000L, data.getLong(ScheduledMessageWorkSpec.KEY_SCHEDULED_AT, -1L))
    }
}
