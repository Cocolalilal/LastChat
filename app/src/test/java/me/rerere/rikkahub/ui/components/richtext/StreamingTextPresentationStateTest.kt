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
    fun revealFollowsTheBufferInsteadOfWaitingForAWholeWord() {
        assertEquals(
            4,
            chooseStreamingRevealCount(
                pending = "hello world",
                budget = 4,
                starved = false
            )
        )
        assertEquals(
            5,
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
    fun settleRangeIsOnePiece() {
        val content = "Hello smooth world"
        val ranges = streamingSettleRangesForReveal(
            content = content,
            revealStart = 6,
            revealEnd = content.length,
            nowMillis = 400L
        )

        assertEquals(1, ranges.size)
        assertEquals(6, ranges.first().startOffset)
        assertEquals(content.length, ranges.first().endOffset)
        assertEquals(400L, ranges.first().revealedAtMillis)
    }

    @Test
    fun laterCharactersJoinTheOpenRun() {
        val first = listOf(StreamingSettleRange(startOffset = 0, endOffset = 4, revealedAtMillis = 1_000L))
        val joined = joinStreamingSettleRun(
            ranges = first,
            revealStart = 4,
            revealEnd = 9,
            nowMillis = 1_100L,
            settleMillis = 280L,
        )

        assertEquals(1, joined.size)
        assertEquals(0, joined.first().startOffset)
        assertEquals(9, joined.first().endOffset)
        assertEquals(1_000L, joined.first().revealedAtMillis)

        val next = joinStreamingSettleRun(
            ranges = joined,
            revealStart = 9,
            revealEnd = 12,
            nowMillis = 1_400L,
            settleMillis = 280L,
        )
        assertEquals(2, next.size)
        assertEquals(9, next[1].startOffset)
        assertEquals(1_400L, next[1].revealedAtMillis)
    }

    @Test
    fun revealedWordSettlesAsOnePiece() {
        val state = StreamingTextPresentationState("Hello ", nowMillis = 0L)

        state.acceptRawContent("Hello world", nowMillis = 16L)
        var now = 16L
        while (state.displayContent != "Hello world" && now < 2_000L) {
            now += 16L
            state.step(nowMillis = now, elapsedMillis = 16L)
        }

        assertEquals("Hello world", state.displayContent)
        val ranges = state.settleRanges
        assertEquals(1, ranges.size)
        assertEquals(6, ranges.first().startOffset)
        assertEquals(11, ranges.first().endOffset)
    }

    @Test
    fun uiBlurAddsBlurWhileKeepingTheStreamingFade() {
        val visuals = streamingRevealVisuals(
            progress = 0.25f,
            startAlpha = 0.4f,
            blurEnabled = true,
        )

        assertEquals(0.494f, visuals.alpha, 0.02f)
        assertTrue(visuals.blurRadius > 8f)
    }

    @Test
    fun disabledUiBlurUsesFadeOnly() {
        val visuals = streamingRevealVisuals(
            progress = 0.25f,
            startAlpha = 0.4f,
            blurEnabled = false,
        )

        assertEquals(0.494f, visuals.alpha, 0.02f)
        assertEquals(0f, visuals.blurRadius, 0.001f)
    }

    @Test
    fun blurSettlesToCrispOpaqueText() {
        val visuals = streamingRevealVisuals(
            progress = 1f,
            startAlpha = 0.4f,
            blurEnabled = true,
        )

        assertEquals(1f, visuals.alpha, 0.001f)
        assertEquals(0f, visuals.blurRadius, 0.001f)
    }

    @Test
    fun fastTokensCompressTheSameSettle() {
        val slow = streamingSettleMillis(20f)
        val fast = streamingSettleMillis(200f)
        val mid = streamingSettleMillis(90f)
        assertEquals(340L, slow)
        assertEquals(170L, fast)
        assertTrue(mid in (fast + 1) until slow)
    }

    @Test
    fun halfwayThroughTheFadeTheGlyphIsStillBlurred() {
        val visuals = streamingRevealVisuals(
            progress = 0.5f,
            startAlpha = 0f,
            blurEnabled = true,
        )

        assertEquals(0.5f, visuals.alpha, 0.02f)
        assertTrue(visuals.blurRadius >= STREAMING_SETTLE_MAX_BLUR_RADIUS * 0.45f)
    }

    @Test
    fun everySpeedStartsFromTheSameFade() {
        val slow = streamingRevealVisuals(
            progress = 0f,
            startAlpha = 0f,
            blurEnabled = true,
        )
        val fast = streamingRevealVisuals(
            progress = 0.5f,
            startAlpha = 0f,
            blurEnabled = true,
        )
        assertEquals(0f, slow.alpha, 0.001f)
        assertTrue(slow.blurRadius > fast.blurRadius)
        assertTrue(fast.alpha in 0.4f..0.6f)
        assertTrue(fast.blurRadius > 0f)
    }
}
