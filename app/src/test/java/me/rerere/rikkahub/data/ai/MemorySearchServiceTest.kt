package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class MemorySearchServiceTest {
    @Test
    fun fuzzyMemoryAgeLabelUsesHumanBuckets() {
        val now = Instant.parse("2026-05-23T12:00:00Z").toEpochMilli()

        assertEquals("earlier today", fuzzyMemoryAgeLabel(Instant.parse("2026-05-23T08:00:00Z").toEpochMilli(), now))
        assertEquals("yesterday", fuzzyMemoryAgeLabel(Instant.parse("2026-05-22T08:00:00Z").toEpochMilli(), now))
        assertEquals("a few days ago", fuzzyMemoryAgeLabel(Instant.parse("2026-05-20T08:00:00Z").toEpochMilli(), now))
        assertEquals("a couple months ago", fuzzyMemoryAgeLabel(Instant.parse("2026-03-12T08:00:00Z").toEpochMilli(), now))
        assertEquals("a long time ago", fuzzyMemoryAgeLabel(Instant.parse("2024-01-01T08:00:00Z").toEpochMilli(), now))
    }

    @Test
    fun findConversationRecallSpansBuildsSmallWindowAroundKeyword() {
        val conversation = Conversation(
            id = Uuid.parse("00000000-0000-0000-0000-000000000301"),
            assistantId = Uuid.parse("00000000-0000-0000-0000-000000000302"),
            title = "Trip planning",
            createAt = Instant.parse("2026-05-20T08:00:00Z"),
            updateAt = Instant.parse("2026-05-20T09:00:00Z"),
            messageNodes = listOf(
                UIMessage.user("hello").toMessageNode(),
                UIMessage.assistant("hi").toMessageNode(),
                UIMessage.user("I want to visit Lisbon in June").toMessageNode(),
                UIMessage.assistant("Lisbon in June sounds warm and bright.").toMessageNode(),
                UIMessage.user("Also remember I prefer trains.").toMessageNode(),
            )
        )

        val spans = findConversationRecallSpans(conversation, "Lisbon train", maxSpans = 1, radius = 1)

        assertEquals(1, spans.size)
        assertEquals(2, spans.single().messageIndex)
        assertTrue(buildFallbackRecallSummary(spans.single()).contains("Lisbon"))
        assertFalse(buildFallbackRecallSummary(spans.single()).contains("hello"))
    }

    @Test
    fun shouldRegisterMemorySearchToolRequiresMemoryAndToggle() {
        assertFalse(shouldRegisterMemorySearchTool(Assistant(enableMemory = false, enableMemorySearchTool = true)))
        assertFalse(shouldRegisterMemorySearchTool(Assistant(enableMemory = true, enableMemorySearchTool = false)))
        assertTrue(shouldRegisterMemorySearchTool(Assistant(enableMemory = true, enableMemorySearchTool = true)))
    }
}
