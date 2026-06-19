package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTextPresentationStateTest {
    @Test
    fun fastBurstRevealsAcrossMultipleSteps() {
        val state = StreamingTextPresentationState("", nowMillis = 0L)

        state.acceptRawContent("This is a fast burst of streamed text.", nowMillis = 16L)

        assertEquals("", state.displayContent)
        assertTrue(state.step(nowMillis = 80L, elapsedMillis = 64L))
        val firstStep = state.displayContent

        assertTrue(firstStep.isNotEmpty())
        assertTrue(firstStep.length < state.rawContent.length)
        assertTrue(state.step(nowMillis = 128L, elapsedMillis = 48L))
        assertTrue(state.displayContent.length > firstStep.length)
        assertTrue(state.displayContent.length < state.rawContent.length)
    }

    @Test
    fun slowSingleTokenAppearsAfterShortStarvationWindow() {
        val state = StreamingTextPresentationState("Hello", nowMillis = 0L)

        state.acceptRawContent("Hello!", nowMillis = 500L)

        assertFalse(state.step(nowMillis = 540L, elapsedMillis = 40L))
        assertTrue(state.step(nowMillis = 620L, elapsedMillis = 80L))
        assertEquals("Hello!", state.displayContent)
    }

    @Test
    fun suddenRateChangeIsSmoothed() {
        val first = smoothStreamingRate(
            previousCharsPerSecond = 44f,
            instantCharsPerSecond = 220f
        )
        val second = smoothStreamingRate(
            previousCharsPerSecond = first,
            instantCharsPerSecond = 18f
        )

        assertTrue(first > 44f)
        assertTrue(first < 220f)
        assertTrue(second < first)
        assertTrue(second > 18f)
    }

    @Test
    fun largeBacklogCatchesUpWithoutSingleJump() {
        val state = StreamingTextPresentationState("", nowMillis = 0L)
        val raw = "x".repeat(300)

        state.acceptRawContent(raw, nowMillis = 16L)
        assertTrue(state.step(nowMillis = 620L, elapsedMillis = 604L))

        assertTrue(state.displayContent.isNotEmpty())
        assertTrue(state.displayContent.length < raw.length)
    }

    @Test
    fun stalledApiDeceleratesInsteadOfDrainingAllPendingText() {
        val state = StreamingTextPresentationState("", nowMillis = 0L)
        val raw = "x".repeat(180)

        state.acceptRawContent(raw, nowMillis = 16L)
        assertTrue(state.step(nowMillis = 80L, elapsedMillis = 64L))
        val earlyLength = state.displayContent.length

        assertTrue(state.step(nowMillis = 980L, elapsedMillis = 900L))

        assertTrue(state.displayContent.length > earlyLength)
        assertTrue(state.displayContent.length < raw.length)
    }

    @Test
    fun resumedApiDoesNotSnapBackToFullSpeedInOneFrame() {
        val state = StreamingTextPresentationState("", nowMillis = 0L)
        val firstRaw = "x".repeat(120)
        val resumedRaw = firstRaw + "y".repeat(120)

        state.acceptRawContent(firstRaw, nowMillis = 16L)
        assertTrue(state.step(nowMillis = 80L, elapsedMillis = 64L))
        assertTrue(state.step(nowMillis = 980L, elapsedMillis = 900L))
        val stalledLength = state.displayContent.length

        state.acceptRawContent(resumedRaw, nowMillis = 1_000L)
        assertTrue(state.step(nowMillis = 1_016L, elapsedMillis = 16L))

        val resumedDelta = state.displayContent.length - stalledLength
        assertTrue(resumedDelta in 1..12)
    }

    @Test
    fun nonAppendEditSnapsToRawContent() {
        val state = StreamingTextPresentationState("The old answer", nowMillis = 0L)

        assertTrue(state.acceptRawContent("A replacement answer", nowMillis = 20L))

        assertEquals("A replacement answer", state.displayContent)
        assertEquals(state.rawContent, state.displayContent)
        assertTrue(state.settleRanges.isEmpty())
    }

    @Test
    fun semanticChunkingPrefersNearbyWordBoundaries() {
        assertEquals(
            "hello ".length,
            chooseStreamingRevealCount(
                pending = "hello world",
                budget = 4,
                starved = false
            )
        )
    }

    @Test
    fun normalWordsWaitForWholeWordReveal() {
        assertEquals(
            0,
            chooseStreamingRevealCount(
                pending = "smoothness",
                budget = 5,
                starved = false
            )
        )

        assertEquals(
            "smoothness".length,
            chooseStreamingRevealCount(
                pending = "smoothness",
                budget = 10,
                starved = false
            )
        )
    }

    @Test
    fun settleRangesSkipWhitespaceAndTrackWords() {
        val ranges = streamingSettleRangesForReveal(
            content = "Hello smooth world",
            revealStart = 5,
            revealEnd = "Hello smooth world".length,
            nowMillis = 24L
        )

        assertEquals(
            listOf(
                StreamingSettleRange(startOffset = 6, endOffset = 12, revealedAtMillis = 24L),
                StreamingSettleRange(startOffset = 13, endOffset = 18, revealedAtMillis = 24L)
            ),
            ranges
        )
    }

    @Test
    fun revealedWordGetsAStableSettleRange() {
        val state = StreamingTextPresentationState("Hello ", nowMillis = 0L)

        state.acceptRawContent("Hello world", nowMillis = 16L)
        assertTrue(state.step(nowMillis = 130L, elapsedMillis = 114L))

        assertEquals("Hello world", state.displayContent)
        assertEquals(
            listOf(StreamingSettleRange(startOffset = 6, endOffset = 11, revealedAtMillis = 130L)),
            state.settleRanges
        )
    }
}
