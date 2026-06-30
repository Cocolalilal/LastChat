package me.rerere.rikkahub.web

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class WebDtosTest {
    @Test
    fun toUiMessageParts_preservesDocumentFields() {
        val converted = listOf(
            WebMessagePartDto.Document(
                url = "file:///tmp/report.pdf",
                fileName = "report.pdf",
                mime = "application/pdf",
            )
        ).toUiMessageParts()

        assertEquals(1, converted.size)
        assertTrue(converted.single() is UIMessagePart.Document)

        val document = converted.single() as UIMessagePart.Document
        assertEquals("file:///tmp/report.pdf", document.url)
        assertEquals("report.pdf", document.fileName)
        assertEquals("application/pdf", document.mime)
    }

    @Test
    fun toListDto_preservesConversationConsolidationMetadata() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            assistantId = Uuid.random(),
        ).copy(
            title = "Memory chat",
            isConsolidated = true,
            contextSummary = "The important setup.",
            contextSummaryUpToIndex = 8,
            lastPruneTime = 1234L,
            lastPruneMessageCount = 5,
            lastRefreshTime = 5678L,
        )

        val dto = conversation.toListDto()

        assertTrue(dto.isConsolidated)
        assertEquals("The important setup.", dto.contextSummary)
        assertEquals(8, dto.contextSummaryUpToIndex)
        assertEquals(1234L, dto.lastPruneTime)
        assertEquals(5, dto.lastPruneMessageCount)
        assertEquals(5678L, dto.lastRefreshTime)
    }

    @Test
    fun conversationDto_serializesConversationConsolidationMetadata() {
        val encoded = Json.encodeToString(
            ConversationDto(
                id = Uuid.random().toString(),
                assistantId = Uuid.random().toString(),
                title = "Memory chat",
                messages = emptyList(),
                enabledSkillIds = emptyList(),
                truncateIndex = -1,
                chatSuggestions = emptyList(),
                isPinned = false,
                createAt = 1L,
                updateAt = 2L,
                isConsolidated = true,
                contextSummary = "The important setup.",
                contextSummaryUpToIndex = 8,
                lastPruneTime = 1234L,
                lastPruneMessageCount = 5,
                lastRefreshTime = 5678L,
            )
        )

        assertTrue(encoded.contains("\"isConsolidated\":true"))
        assertTrue(encoded.contains("\"contextSummary\":\"The important setup.\""))
        assertTrue(encoded.contains("\"contextSummaryUpToIndex\":8"))
        assertTrue(encoded.contains("\"lastPruneTime\":1234"))
        assertTrue(encoded.contains("\"lastPruneMessageCount\":5"))
        assertTrue(encoded.contains("\"lastRefreshTime\":5678"))
    }
}
