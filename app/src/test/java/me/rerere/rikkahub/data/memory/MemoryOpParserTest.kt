package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemReality
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.model.MemoryOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryOpParserTest {

    @Test
    fun parsesAddNodeWithAllFields() {
        val json = """
            [{"op":"ADD_NODE","type":"EPISODE","content":"Played chess","entities":["User","Dad"],
              "aliases":["father"],"category":"other","importance":4,"confidence":0.9,
              "sensitivity":"SENSITIVE","reality":"FICTION","event_start":1720000000000,
              "rationale":"they mentioned it","excerpt":"we played chess"}]
        """.trimIndent()
        val ops = MemoryOpParser.parse(json)
        assertEquals(1, ops.size)
        val add = ops.first() as MemoryOp.AddNode
        assertEquals(MemNodeType.EPISODE, add.type)
        assertEquals("Played chess", add.content)
        assertEquals(listOf("User", "Dad"), add.entities)
        assertEquals(listOf("father"), add.aliases)
        assertEquals(4, add.importance)
        assertEquals(MemSensitivity.SENSITIVE, add.sensitivity)
        assertEquals(MemReality.FICTION, add.reality)
        assertEquals(1720000000000L, add.eventStart)
    }

    @Test
    fun parsesEachOpKind() {
        val json = """
            [
              {"op":"REINFORCE","id":"a"},
              {"op":"UPDATE","old_id":"b","content":"now studies math"},
              {"op":"CLOSE","id":"c","ended_at":123},
              {"op":"ADD_EDGE","from":"d","to":"e","type":"CONTRADICTS"},
              {"op":"OPEN_GOAL","question":"what's your schedule?","entities":["User"],"value_note":"helps"},
              {"op":"RESOLVE_GOAL","id":"g","outcome":"CONFIRMED","learned":"mornings"}
            ]
        """.trimIndent()
        val ops = MemoryOpParser.parse(json)
        assertEquals(6, ops.size)
        assertEquals(MemoryOp.Reinforce("a"), ops[0])
        assertTrue(ops[1] is MemoryOp.UpdateNode)
        assertEquals("now studies math", (ops[1] as MemoryOp.UpdateNode).content)
        assertEquals(123L, (ops[2] as MemoryOp.CloseNode).endedAt)
        assertEquals(MemEdgeType.CONTRADICTS, (ops[3] as MemoryOp.AddEdge).type)
        assertEquals("what's your schedule?", (ops[4] as MemoryOp.OpenGoal).question)
        assertEquals("mornings", (ops[5] as MemoryOp.ResolveGoal).learned)
    }

    @Test
    fun extractsArrayFromSurroundingProseAndFences() {
        val text = "Sure! Here are the ops:\n```json\n[{\"op\":\"REINFORCE\",\"id\":\"x\"}]\n```\nDone."
        val ops = MemoryOpParser.parse(text)
        assertEquals(listOf(MemoryOp.Reinforce("x")), ops)
    }

    @Test
    fun skipsMalformedAndKeepsValidOps() {
        val json = """
            [
              {"op":"ADD_NODE","content":""},
              {"op":"NONSENSE","foo":1},
              {"op":"REINFORCE"},
              {"op":"REINFORCE","id":"keep"}
            ]
        """.trimIndent()
        val ops = MemoryOpParser.parse(json)
        assertEquals(listOf(MemoryOp.Reinforce("keep")), ops)
    }

    @Test
    fun emptyArrayAndGarbageYieldNoOps() {
        assertTrue(MemoryOpParser.parse("[]").isEmpty())
        assertTrue(MemoryOpParser.parse("no json here").isEmpty())
        assertTrue(MemoryOpParser.parse("").isEmpty())
    }

    // ---------------- MemoryWindow (watermark integrity) ----------------

    @Test
    fun firstChunkStartsAtZeroFromFreshWatermark() {
        val chunk = MemoryWindow.nextChunk(watermark = -1, messageCount = 5, maxWindow = 30)
        assertEquals(0, chunk.startInclusive)
        assertEquals(5, chunk.endExclusive)
        assertEquals(4, chunk.processedUpToIndex)
    }

    @Test
    fun fullyEncodedConversationYieldsEmptyChunk() {
        val chunk = MemoryWindow.nextChunk(watermark = 4, messageCount = 5, maxWindow = 30)
        assertTrue(chunk.isEmpty)
    }

    @Test
    fun longConversationIsChunkedOldestFirstWithoutSkipping() {
        // Simulate sequential passes; the watermark must advance contiguously and never past the end.
        var watermark = -1
        val total = 100
        val visited = mutableListOf<Int>()
        while (true) {
            val chunk = MemoryWindow.nextChunk(watermark, total, maxWindow = 30)
            if (chunk.isEmpty) break
            (chunk.startInclusive until chunk.endExclusive).forEach { visited += it }
            assertTrue("watermark never advances past a read message", chunk.processedUpToIndex < total)
            watermark = chunk.processedUpToIndex
        }
        // Every message index was read exactly once, in order.
        assertEquals((0 until total).toList(), visited)
        assertEquals(total - 1, watermark)
    }

    @Test
    fun watermarkBeyondSizeIsClampedToEmpty() {
        val chunk = MemoryWindow.nextChunk(watermark = 999, messageCount = 5, maxWindow = 30)
        assertTrue(chunk.isEmpty)
    }
}
