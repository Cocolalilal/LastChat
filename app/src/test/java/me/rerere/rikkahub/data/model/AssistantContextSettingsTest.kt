package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantContextSettingsTest {
    @Test
    fun manualSummarizationIgnoresLegacyContextRefreshToggle() {
        val assistant = Assistant(enableContextRefresh = false)

        assertTrue(assistant.canManuallySummarizeConversation(messageCount = 3))
        assertFalse(assistant.canManuallySummarizeConversation(messageCount = 2))
    }

    @Test
    fun enablingAutoSummarySeedsDefaultHistoryLimit() {
        val assistant = Assistant(
            autoRegenerateSummary = false,
            maxHistoryMessages = null
        )

        val updated = assistant.withAutoSummaryEnabled(true)

        assertTrue(updated.autoRegenerateSummary)
        assertEquals(DEFAULT_AUTO_SUMMARY_HISTORY_LIMIT, updated.maxHistoryMessages)
    }

    @Test
    fun reEnablingAutoSummaryPreservesCustomHistoryLimit() {
        val assistant = Assistant(
            autoRegenerateSummary = true,
            maxHistoryMessages = 24
        )

        val toggled = assistant
            .withAutoSummaryEnabled(false)
            .withAutoSummaryEnabled(true)

        assertTrue(toggled.autoRegenerateSummary)
        assertEquals(24, toggled.maxHistoryMessages)
    }

    @Test
    fun autoSummaryRequiresToggleAndHistoryLimit() {
        assertFalse(
            Assistant(
                autoRegenerateSummary = false,
                maxHistoryMessages = 10
            ).shouldAutoSummarizeMessages()
        )
        assertFalse(
            Assistant(
                autoRegenerateSummary = true,
                maxHistoryMessages = null
            ).shouldAutoSummarizeMessages()
        )
        assertTrue(
            Assistant(
                autoRegenerateSummary = true,
                maxHistoryMessages = 10
            ).shouldAutoSummarizeMessages()
        )
    }

    @Test
    fun legacyEnableContextRefreshFieldStillDeserializes() {
        val assistant = Json.decodeFromString<Assistant>(
            """
            {
              "id": "00000000-0000-0000-0000-000000000020",
              "name": "Legacy Assistant",
              "enableContextRefresh": true,
              "autoRegenerateSummary": false
            }
            """.trimIndent()
        )

        assertTrue(assistant.enableContextRefresh)
        assertFalse(assistant.autoRegenerateSummary)
    }
}
