package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
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
    fun memorySearchTokensPreserveUserTermsAndExpandRecallClues() {
        val tokens = memorySearchTokens("under the bed test memory")

        assertTrue(tokens.contains("under"))
        assertTrue(tokens.contains("beneath"))
        assertTrue(tokens.contains("bed"))
        assertTrue(tokens.contains("test"))
        assertTrue(tokens.contains("memory"))
    }

    @Test
    fun scoreMemorySearchTextMatchesUnderBedSceneWithDifferentWording() {
        val score = scoreMemorySearchText(
            text = "You startled me when you crawled out from beneath your bed.",
            query = "under the bed test memory"
        )

        assertTrue(score > 0)
    }

    @Test
    fun findConversationRecallSpansUsesOnlySelectedMessageVersions() {
        val conversation = Conversation(
            id = Uuid.parse("00000000-0000-0000-0000-000000000401"),
            assistantId = Uuid.parse("00000000-0000-0000-0000-000000000402"),
            title = "Old scene",
            createAt = Instant.parse("2026-05-18T08:00:00Z"),
            updateAt = Instant.parse("2026-05-18T09:00:00Z"),
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(
                        UIMessage.user("I hid under your bed and spooked you."),
                        UIMessage.user("Nothing relevant here."),
                    ),
                    selectIndex = 1
                )
            )
        )

        val spans = findConversationRecallSpans(conversation, "under the bed test memory", maxSpans = 1)

        assertTrue(spans.isEmpty())
    }

    @Test
    fun shouldRegisterMemorySearchToolRequiresMemoryAndToggle() {
        assertFalse(shouldRegisterMemorySearchTool(Assistant(enableMemory = false, enableMemorySearchTool = true)))
        assertFalse(shouldRegisterMemorySearchTool(Assistant(enableMemory = true, enableMemorySearchTool = false)))
        assertTrue(shouldRegisterMemorySearchTool(Assistant(enableMemory = true, enableMemorySearchTool = true)))
    }
}
