package me.rerere.lastchat.ios

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class IosMemoryStateTest {
    @Test
    fun memoryRecordsRemainStrictlyAssistantScoped() {
        val first = IosAssistantPreferences(id = "first", name = "First")
        val second = IosAssistantPreferences(id = "second", name = "Second")
        val state = IosAppState(
            loading = false,
            assistants = listOf(first, second),
            selectedAssistantId = first.id,
            memories = listOf(
                IosMemoryRecord(1, first.id, "first memory"),
                IosMemoryRecord(2, second.id, "second memory"),
            ),
        )

        assertEquals(listOf("first memory"), state.assistantMemories.map(IosMemoryRecord::content))
    }

    @Test
    fun persistedMemoryConfigurationContainsNoProviderSecret() {
        val assistant = IosAssistantPreferences(
            id = "assistant",
            memoryMode = IosMemoryMode.SEARCHABLE,
            embeddingProviderType = IosProviderType.OPENAI,
            embeddingModelId = "text-embedding-3-small",
        )
        val encoded = Json.encodeToString(assistant)

        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("secret", ignoreCase = true))
        assertEquals(IosMemoryMode.SEARCHABLE, Json.decodeFromString<IosAssistantPreferences>(encoded).memoryMode)
    }

    @Test
    fun localToolSelectionIsAssistantScopedAndSerializable() {
        val assistant = IosAssistantPreferences(
            id = "assistant",
            localTools = setOf(
                IosLocalToolOption.NOTIFICATIONS,
                IosLocalToolOption.TTS,
                IosLocalToolOption.ASK_USER,
                IosLocalToolOption.IMAGE_GENERATION,
            ),
        )
        val decoded = Json.decodeFromString<IosAssistantPreferences>(Json.encodeToString(assistant))

        assertEquals(assistant.localTools, decoded.localTools)
        assertFalse(Json.encodeToString(assistant).contains("apiKey", ignoreCase = true))
    }

    @Test
    fun vectorsRoundTripWithTheirModelIdentity() {
        val record = IosMemoryRecord(
            id = 7,
            assistantId = "assistant",
            content = "Likes seafoam green",
            embeddings = listOf(listOf(0.25f, 0.75f)),
            embeddingModelId = "embedding-model",
        )
        val decoded = Json.decodeFromString<IosMemoryRecord>(Json.encodeToString(record))

        assertEquals(record.embeddings, decoded.embeddings)
        assertEquals("embedding-model", decoded.embeddingModelId)
    }

    @Test
    fun adaptiveWatermarkAndEpisodeMetadataRoundTrip() {
        val conversation = IosConversation(
            id = "conversation",
            assistantId = "assistant",
            memoryLastMessageId = "message-8",
        )
        val episode = IosMemoryRecord(
            id = 9,
            assistantId = "assistant",
            content = "Trip planning: User chose Bucharest.",
            type = 1,
            significance = 4,
            sourceConversationId = conversation.id,
            sceneKey = "trip-planning",
        )

        assertEquals("message-8", Json.decodeFromString<IosConversation>(Json.encodeToString(conversation)).memoryLastMessageId)
        assertEquals("trip-planning", Json.decodeFromString<IosMemoryRecord>(Json.encodeToString(episode)).sceneKey)
    }

    @Test
    fun adaptiveBackgroundDeadlineUsesThresholdAndEarliestInactivity() {
        assertNull(earliestAdaptiveMemoryDeadline(1_000L, emptyList()))
        assertEquals(
            1_000L,
            earliestAdaptiveMemoryDeadline(
                nowEpochMs = 1_000L,
                candidates = listOf(500L to 8, 900L to 1),
            ),
        )
        assertEquals(
            1_500L,
            earliestAdaptiveMemoryDeadline(
                nowEpochMs = 1_000L,
                candidates = listOf(900L to 2, 500L to 1),
                inactivityMs = 1_000L,
            ),
        )
    }

    @Test
    fun scheduledMessagesAndNotificationHistoryRemainSerializable() {
        val scheduled = IosScheduledMessage(
            id = "scheduled",
            assistantId = "assistant",
            conversationId = "conversation",
            reason = "Check in",
            createdAtEpochMs = 1_000L,
            scheduledAtEpochMs = 2_000L,
            nextAttemptEpochMs = 3_000L,
            attemptCount = 2,
        )
        val notification = IosNotificationRecord("Assistant", "How did it go?", 4_000L)

        assertEquals(scheduled, Json.decodeFromString<IosScheduledMessage>(Json.encodeToString(scheduled)))
        assertEquals(notification, Json.decodeFromString<IosNotificationRecord>(Json.encodeToString(notification)))
    }

    @Test
    fun scheduledMessageDeadlineAndRetryBackoffAreDeterministic() {
        val later = IosScheduledMessage(
            id = "later",
            assistantId = "assistant",
            conversationId = "conversation",
            reason = "Later",
            createdAtEpochMs = 0L,
            scheduledAtEpochMs = 5_000L,
            nextAttemptEpochMs = 5_000L,
        )
        val sooner = later.copy(id = "sooner", nextAttemptEpochMs = 2_000L)

        assertNull(earliestScheduledMessageDeadline(emptyList()))
        assertEquals(2_000L, earliestScheduledMessageDeadline(listOf(later, sooner)))
        assertEquals(30_000L, scheduledMessageRetryDelayMs(0))
        assertEquals(60_000L, scheduledMessageRetryDelayMs(1))
        assertEquals(1_800_000L, scheduledMessageRetryDelayMs(20))
    }

    @Test
    fun askUserParserEnforcesLimitsAndAnswerPayloadIsNormalized() {
        val arguments = Json.parseToJsonElement(
            """{"questions":[
                {"id":"choice","question":"Choose?","options":[
                    {"label":"A"},{"label":"B","description":"Second"},{"label":"C"},{"label":"D"}
                ]},
                {"id":"invalid","question":""}
            ]}"""
        )
        val questions = parseIosAskUserQuestions(arguments)
        assertEquals(1, questions.size)
        assertEquals(listOf("A", "B", "C"), questions.single().options.map(IosAskUserOption::label))

        val pending = IosPendingQuestionnaire("call", "conversation", questions)
        val payload = buildAskUserAnswerPayload(
            pending = pending,
            selectedOptions = mapOf("choice" to "A"),
            customAnswers = mapOf("choice" to "Custom answer"),
            dismissed = false,
        )
        assertEquals(
            """{"answers":[{"id":"choice","status":"answered","source":"custom","value":"Custom answer"}],"dismissed":false}""",
            payload.toString(),
        )
    }

    @Test
    fun imageGenerationSelectionPersistsWithoutCredentials() {
        val preferences = IosImageGenerationPreferences(
            enabled = true,
            providerType = IosProviderType.GOOGLE,
            modelId = "imagen-3.0-generate-002",
        )
        val encoded = Json.encodeToString(preferences)
        assertEquals(preferences, Json.decodeFromString<IosImageGenerationPreferences>(encoded))
        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("secret", ignoreCase = true))
    }
}
