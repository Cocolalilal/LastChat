package me.rerere.rikkahub.data.memory

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TemporalMemoryModelsTest {
    @Test
    fun extractionPromptKeepsEvidenceScopeAndClaimIds() {
        val prompt = buildTemporalMemoryExtractionPrompt(
            pending = listOf(SourceMessage("message-2", 0, "I switched to tea.", 1234L)),
            existing = listOf(
                TemporalRecallItem(
                    stableId = "claim:17",
                    text = "User likes coffee.",
                    timestamp = 1000L,
                    score = 0.8f,
                    kind = RecallKind.CURRENT_STATE,
                    confidence = 0.9f,
                )
            ),
        )

        assertContains(prompt, "claim_id=17 User likes coffee.")
        assertContains(prompt, "id=message-2 observed_at=1234 role=user: I switched to tea.")
        assertContains(prompt, "Never invent. Never infer sensitive traits.")
    }

    @Test
    fun recallPacketLabelsCurrentHistoricalAndEpisodes() {
        val block = TemporalRecallPacket(
            projection = "- User likes tea.",
            items = listOf(
                TemporalRecallItem("claim:1", "Current", 1, 1f, RecallKind.CURRENT_STATE, 1f),
                TemporalRecallItem("claim:2", "Historical", 1, 1f, RecallKind.HISTORICAL_FACT, 1f),
                TemporalRecallItem("episode:3", "Episode", 1, 1f, RecallKind.EPISODE, 1f),
            ),
        ).toPromptBlock()

        assertContains(block, "[current] Current")
        assertContains(block, "[historical] Historical")
        assertContains(block, "[episode] Episode")
        assertEquals(1, block.lines().count { it == "### Current understanding" })
    }
}
