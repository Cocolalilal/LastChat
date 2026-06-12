package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantSerializationTest {
    @Test
    fun olderAssistantJsonDefaultsSpontaneousMessagingFields() {
        val assistant = Json.decodeFromString<Assistant>(
            """
            {
              "id": "00000000-0000-0000-0000-000000000001",
              "name": "Legacy Assistant"
            }
            """.trimIndent()
        )

        assertFalse(assistant.enableSpontaneous)
        assertEquals(7, assistant.notificationStartHour)
        assertEquals(22, assistant.notificationEndHour)
        assertEquals(4, assistant.notificationFrequencyHours)
        assertEquals(0L, assistant.lastNotificationTime)
        assertEquals("", assistant.lastNotificationContent)
        assertEquals("", assistant.spontaneousPrompt)
        assertEquals(SpontaneousMessageMode.BOTH, assistant.spontaneousMessageMode)
    }

    @Test
    fun olderAssistantJsonDefaultsThinkingBudgetToAuto() {
        val assistant = Json.decodeFromString<Assistant>(
            """
            {
              "id": "00000000-0000-0000-0000-000000000002",
              "name": "Legacy Assistant"
            }
            """.trimIndent()
        )

        assertEquals(-1, assistant.thinkingBudget)
    }

    @Test
    fun olderAssistantJsonDefaultsMemorySearchToolOff() {
        val assistant = Json.decodeFromString<Assistant>(
            """
            {
              "id": "00000000-0000-0000-0000-000000000014",
              "name": "Legacy Memory Assistant",
              "enableMemory": true
            }
            """.trimIndent()
        )

        assertFalse(assistant.enableMemorySearchTool)
    }

    @Test
    fun spontaneousMessagingFieldsRoundTrip() {
        val assistant = Assistant(
            id = Uuid.parse("00000000-0000-0000-0000-000000000010"),
            name = "Iris",
            enableSpontaneous = true,
            notificationStartHour = 9,
            notificationEndHour = 1,
            notificationFrequencyHours = 6,
            lastNotificationTime = 123456789L,
            lastNotificationContent = "Thinking about you.",
            spontaneousPrompt = "Use {{history}} and {{memories}}",
        )

        val encoded = Json.encodeToString(Assistant.serializer(), assistant)
        val decoded = Json.decodeFromString(Assistant.serializer(), encoded)

        assertTrue(decoded.enableSpontaneous)
        assertEquals(9, decoded.notificationStartHour)
        assertEquals(1, decoded.notificationEndHour)
        assertEquals(6, decoded.notificationFrequencyHours)
        assertEquals(123456789L, decoded.lastNotificationTime)
        assertEquals("Thinking about you.", decoded.lastNotificationContent)
        assertEquals("Use {{history}} and {{memories}}", decoded.spontaneousPrompt)
        assertEquals(SpontaneousMessageMode.BOTH, decoded.spontaneousMessageMode)
    }

    @Test
    fun localToolsDeserializationKeepsSupportedEntriesAndDropsUnknownOnes() {
        val assistant = JsonInstant.decodeFromString<Assistant>(
            """
            {
              "id": "00000000-0000-0000-0000-000000000011",
              "name": "Legacy Notifications Assistant",
              "localTools": [
                {"type": "device_control"},
                {"type": "unknown_tool"},
                {"type": "future_tool_from_other_build"},
                {"oops": "missing_type"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, assistant.localTools.size)
        assertTrue(assistant.localTools.contains(LocalToolOption.Notifications))
    }

    @Test
    fun notificationsLocalToolRoundTripsThroughSerialization() {
        val assistant = Assistant(
            id = Uuid.parse("00000000-0000-0000-0000-000000000012"),
            name = "Nova",
            localTools = listOf(LocalToolOption.Notifications)
        )

        val encoded = JsonInstant.encodeToString(Assistant.serializer(), assistant)
        val decoded = JsonInstant.decodeFromString(Assistant.serializer(), encoded)

        assertTrue(encoded.contains("\"device_control\""))
        assertTrue(decoded.localTools.contains(LocalToolOption.Notifications))
    }

    @Test
    fun characterQuestionsLocalToolRoundTripsThroughSerialization() {
        val assistant = Assistant(
            id = Uuid.parse("00000000-0000-0000-0000-000000000013"),
            name = "Mina",
            localTools = listOf(LocalToolOption.AskUser)
        )

        val encoded = JsonInstant.encodeToString(Assistant.serializer(), assistant)
        val decoded = JsonInstant.decodeFromString(Assistant.serializer(), encoded)

        assertTrue(encoded.contains("\"character_questions\""))
        assertTrue(decoded.localTools.contains(LocalToolOption.AskUser))
    }

}
