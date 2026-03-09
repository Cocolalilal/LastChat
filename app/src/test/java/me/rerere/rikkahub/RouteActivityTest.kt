package me.rerere.rikkahub

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.service.ChatPersistenceMode
import me.rerere.rikkahub.service.SpontaneousMessageRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

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

    @Test
    fun resolveSpontaneousNotificationTargetAppendsToExistingConversation() = runBlocking {
        val assistantId = Uuid.random()
        val conversationId = Uuid.random()
        var consumedEventId: String? = null
        var selectedAssistantId: Uuid? = null

        val target = resolveSpontaneousNotificationTarget(
            data = SpontaneousNotificationData(
                assistantId = assistantId.toString(),
                conversationId = conversationId.toString(),
                eventId = "event-1",
                message = "Follow-up",
                relation = SpontaneousMessageRelation.RECENT_CHAT,
            ),
            isEventConsumed = { false },
            updateAssistantSelection = { selectedAssistantId = it },
            hasConversation = { it == conversationId },
            appendToConversation = { resolvedAssistantId, message, targetConversationId ->
                assertEquals(assistantId, resolvedAssistantId)
                assertEquals("Follow-up", message)
                assertEquals(conversationId, targetConversationId)
                conversationId
            },
            seedDraftConversation = { _, _ -> error("Draft seeding should not be used") },
            markEventConsumed = { consumedEventId = it },
        )

        assertEquals(assistantId, selectedAssistantId)
        assertEquals("event-1", consumedEventId)
        assertEquals(
            ResolvedSpontaneousChatTarget(
                conversationId = conversationId,
                persistenceMode = ChatPersistenceMode.NORMAL,
                assistantId = assistantId,
            ),
            target,
        )
    }

    @Test
    fun resolveSpontaneousNotificationTargetFallsBackToDraftWhenRecentChatMissing() = runBlocking {
        val assistantId = Uuid.random()
        val draftConversationId = Uuid.random()
        var appended = false
        var consumed = false

        val target = resolveSpontaneousNotificationTarget(
            data = SpontaneousNotificationData(
                assistantId = assistantId.toString(),
                conversationId = Uuid.random().toString(),
                eventId = "event-2",
                message = "New branch",
                relation = SpontaneousMessageRelation.RECENT_CHAT,
            ),
            isEventConsumed = { false },
            updateAssistantSelection = { },
            hasConversation = { false },
            appendToConversation = { _, _, _ ->
                appended = true
                null
            },
            seedDraftConversation = { _, _ -> draftConversationId },
            markEventConsumed = { consumed = true },
        )

        assertFalse(appended)
        assertTrue(consumed)
        assertEquals(ChatPersistenceMode.PERSIST_ON_REPLY, target?.persistenceMode)
        assertEquals(draftConversationId, target?.conversationId)
    }

    @Test
    fun resolveSpontaneousNotificationTargetLeavesEventUnconsumedWhenInvalid() = runBlocking {
        var consumed = false

        val target = resolveSpontaneousNotificationTarget(
            data = SpontaneousNotificationData(
                assistantId = "invalid",
                conversationId = null,
                eventId = "event-3",
                message = "Hello",
                relation = SpontaneousMessageRelation.UNRELATED,
            ),
            isEventConsumed = { false },
            updateAssistantSelection = { },
            hasConversation = { false },
            appendToConversation = { _, _, _ -> null },
            seedDraftConversation = { _, _ -> Uuid.random() },
            markEventConsumed = { consumed = true },
        )

        assertNull(target)
        assertFalse(consumed)
    }

    @Test
    fun determineInitialChatScreenPrefersResolvedSpontaneousTarget() {
        val spontaneousTarget = ResolvedSpontaneousChatTarget(
            conversationId = Uuid.parse("00000000-0000-0000-0000-000000000123"),
            persistenceMode = ChatPersistenceMode.PERSIST_ON_REPLY,
            assistantId = Uuid.parse("00000000-0000-0000-0000-000000000456"),
        )

        val screen = determineInitialChatScreen(
            defaultScreen = Screen.Chat(id = "default"),
            deepLinkedConversationId = "deep-link",
            spontaneousTarget = spontaneousTarget,
        )

        assertEquals(spontaneousTarget.conversationId.toString(), screen.id)
        assertEquals(ChatPersistenceMode.PERSIST_ON_REPLY.routeValue, screen.persistenceMode)
    }
}
