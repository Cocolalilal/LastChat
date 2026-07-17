package me.rerere.rikkahub.data.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.utils.JsonInstant

class TemporalMemoryModelsTest {
    @Test
    fun extractionEnvelopeAcceptsTemporalOperations() {
        val envelope = JsonInstant.decodeFromString<MemoryExtractionEnvelope>(
            """
            {
              "operations": [{
                "op": "supersede",
                "subject": "user",
                "predicate": "lives_in",
                "object": "Vienna",
                "statement": "User moved from Bucharest to Vienna.",
                "kind": "durative",
                "reality": "real",
                "confidence": 0.95,
                "importance": 4,
                "valid_from": 1784246400000,
                "source_message_id": "message-1"
              }],
              "episode": {
                "title": "Moving to Vienna",
                "summary": "The user said they moved to Vienna.",
                "scene_key": "move-vienna",
                "importance": 4
              }
            }
            """.trimIndent()
        )
        assertEquals(MemoryOperationType.SUPERSEDE, envelope.operations.single().op)
        assertEquals("Vienna", envelope.operations.single().objectValue)
        assertEquals("move-vienna", envelope.episode?.sceneKey)
    }

    @Test
    fun recallPacketSeparatesCurrentHistoryAndPastChats() {
        val packet = TemporalRecallPacket(
            projection = "- User now lives in Vienna.",
            items = listOf(
                TemporalRecallItem("claim:1", "User used to live in Bucharest.", 1, 1f, RecallKind.HISTORICAL_FACT, 1f),
                TemporalRecallItem("source:2", "We discussed packing boxes.", 2, 0.8f, RecallKind.PAST_CHAT, 1f),
            ),
        )
        val prompt = packet.toPromptBlock()
        assertTrue("[historical]" in prompt)
        assertTrue("[past chat]" in prompt)
    }
}
