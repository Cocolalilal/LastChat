package me.rerere.rikkahub

import me.rerere.rikkahub.service.SpontaneousMessageRelation
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteActivityTest {
    @Test
    fun resolveSpontaneousNotificationRelationUsesExplicitRelation() {
        val relation = resolveSpontaneousNotificationRelation(
            relationExtra = SpontaneousMessageRelation.UNRELATED.wireValue,
            conversationId = "123",
        )

        assertEquals(SpontaneousMessageRelation.UNRELATED, relation)
    }

    @Test
    fun resolveSpontaneousNotificationRelationDefaultsToRecentChatWhenConversationExists() {
        val relation = resolveSpontaneousNotificationRelation(
            relationExtra = null,
            conversationId = "123",
        )

        assertEquals(SpontaneousMessageRelation.RECENT_CHAT, relation)
    }

    @Test
    fun resolveSpontaneousNotificationRelationDefaultsToUnrelatedWithoutConversation() {
        val relation = resolveSpontaneousNotificationRelation(
            relationExtra = null,
            conversationId = null,
        )

        assertEquals(SpontaneousMessageRelation.UNRELATED, relation)
    }
}
