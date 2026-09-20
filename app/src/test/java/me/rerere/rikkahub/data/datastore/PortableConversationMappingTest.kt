package me.rerere.rikkahub.data.datastore

import me.rerere.ai.core.MessageRole
import me.rerere.ai.generation.PortableDailyActivity
import me.rerere.ai.generation.PortableSaveOptions
import me.rerere.ai.generation.PortableUsageTotals
import me.rerere.rikkahub.data.db.entity.DailyActivityEntity
import me.rerere.rikkahub.data.db.entity.UsageStatsEntity
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class PortableConversationMappingTest {
    @Test
    fun roomAdapterRoundTripKeepsNodesSkillsAndSummary() {
        val id = Uuid.random()
        val assistantId = Uuid.random()
        val skillId = Uuid.random()
        val conversation = Conversation(
            id = id,
            assistantId = assistantId,
            title = "Castle walk",
            messageNodes = listOf(MessageNode.of(UIMessage.user("hello"))),
            truncateIndex = 2,
            isPinned = true,
            enabledModeIds = setOf(skillId),
            contextSummary = "Earlier we walked.",
            contextSummaryUpToIndex = 3,
            createAt = Instant.ofEpochMilli(1_700_000_000_000L),
            updateAt = Instant.ofEpochMilli(1_700_000_100_000L),
            isFork = true,
        )
        val restored = conversation.toPortableRecord().toConversation()
        assertEquals(id, restored.id)
        assertEquals(assistantId, restored.assistantId)
        assertEquals("Castle walk", restored.title)
        assertEquals(1, restored.messageNodes.size)
        assertEquals(MessageRole.USER, restored.messageNodes.single().currentMessage.role)
        assertEquals(setOf(skillId), restored.enabledModeIds)
        assertEquals("Earlier we walked.", restored.contextSummary)
        assertEquals(3, restored.contextSummaryUpToIndex)
        assertEquals(2, restored.truncateIndex)
        assertTrue(restored.isPinned)
        assertTrue(restored.isFork)
        assertEquals(PortableSaveOptions().preserveConsolidation, false)
    }

    @Test
    fun usageTotalsRoundTripThroughRoomEntity() {
        val totals = PortableUsageTotals(
            conversationCount = 4,
            messageCount = 12,
            inputTokens = 100,
            outputTokens = 80,
            cachedTokens = 9,
        )
        val entity = totals.toUsageStatsEntity()
        assertEquals(4, entity.totalConversations)
        assertEquals(12, entity.totalMessages)
        assertEquals(totals, entity.toPortableUsageTotals())
        assertEquals(0L, UsageStatsEntity().toPortableUsageTotals().conversationCount)
    }

    @Test
    fun dailyActivityRoundTripThroughRoomEntity() {
        val activity = PortableDailyActivity(date = "2026-09-20", messageCount = 4, lastMessageEpochMs = 9L)
        val entity = DailyActivityEntity(
            date = activity.date,
            messageCount = activity.messageCount,
            lastMessageTime = activity.lastMessageEpochMs,
        )
        assertEquals(activity, entity.toPortableDailyActivity())
    }
}
