package me.rerere.rikkahub.ui.components.avatar

import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import me.rerere.rikkahub.ui.components.chat.ActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BlobEngineTest {
    @Test
    fun grokIdleUsesFittedRestGaze() {
        val face = faceFor(BlobEyePack.Grok, BlobLifecycle.Idle)
        assertEquals(REST_GAZE.yaw, face.gaze.yaw, 0.001f)
        assertEquals(REST_GAZE.pitch, face.gaze.pitch, 0.001f)
        assertEquals(REST_GAZE.roll, face.gaze.roll, 0.001f)
        assertEquals(EYE_SPLIT, face.split, 0.001f)
        assertEquals(EYE_W, face.left.w, 0.001f)
        assertEquals(EYE_H, face.left.h, 0.001f)
        assertEquals(0f, face.left.tilt, 0.001f)
        assertEquals(0f, face.right.tilt, 0.001f)
    }

    @Test
    fun genericalIdleIsCenteredAndStraight() {
        val face = faceFor(BlobEyePack.Generical, BlobLifecycle.Idle)
        assertEquals(0f, face.gaze.yaw, 0.001f)
        assertEquals(0f, face.gaze.pitch, 0.001f)
        assertEquals(0f, face.gaze.roll, 0.001f)
        assertEquals(0f, face.lookX, 0.001f)
        assertEquals(0f, face.lookY, 0.001f)
        assertEquals(0f, face.left.tilt, 0.001f)
        assertEquals(0f, face.right.tilt, 0.001f)
        assertTrue("Generical idle eyes are taller than wide", face.left.h > face.left.w)
    }

    @Test
    fun genericalBlockedUsesMirrorTilts() {
        val face = faceFor(BlobEyePack.Generical, BlobLifecycle.Blocked)
        assertTrue(face.left.tilt > 0f)
        assertTrue(face.right.tilt < 0f)
        assertEquals(face.left.tilt, -face.right.tilt, 0.001f)
    }

    @Test
    fun grokBlockedUsesMirrorTilts() {
        val face = faceFor(BlobEyePack.Grok, BlobLifecycle.Blocked)
        assertEquals(30f, face.left.tilt, 0.001f)
        assertEquals(-30f, face.right.tilt, 0.001f)
    }

    @Test
    fun grokFarEyeIsCompressedBySphereDepth() {
        val (left, right) = eyePoses(REST_GAZE, 1f, EYE_SPLIT)
        assertTrue("both eyes face the camera", left.depth > 0.02f && right.depth > 0.02f)
        val leftWidth = hypot2(left.a, left.b)
        val rightWidth = hypot2(right.a, right.b)
        // Fitted observation: the far eye is ~0.69 the near eye's projected width.
        val ratio = minOf(leftWidth, rightWidth) / maxOf(leftWidth, rightWidth)
        assertTrue("far-eye depth compression is in the Grok range, got $ratio", ratio in 0.60f..0.85f)
    }

    @Test
    fun blinkCalendarClosesAtFirstBlink() {
        assertEquals(1.4f, firstBlinkTime(), 0.001f)
        assertEquals(1f, blinkLid(0f), 0.001f)
        val closed = blinkLid(firstBlinkTime() + 0.45f * BLINK_DUR)
        assertTrue("lid should be nearly closed, was $closed", closed < 0.05f)
    }

    @Test
    fun chatMappingUsesFaceLifecycle() {
        assertEquals(
            BlobLifecycle.Waiting,
            blobLifecycleFromChat(ActivityState.Waiting, loading = true),
        )
        assertEquals(
            BlobLifecycle.Thinking,
            blobLifecycleFromChat(ActivityState.Reasoning(), loading = true),
        )
        assertEquals(
            BlobLifecycle.Working,
            blobLifecycleFromChat(ActivityState.ToolUse("shell", "Shell"), loading = true),
        )
        assertEquals(
            BlobLifecycle.Working,
            blobLifecycleFromChat(ActivityState.Replying, loading = true),
        )
        assertEquals(
            BlobLifecycle.Idle,
            blobLifecycleFromChat(
                ActivityState.CompletedSingle(me.rerere.rikkahub.ui.components.chat.ActivityType.REASONING),
                loading = false,
            ),
        )
        assertEquals(
            BlobLifecycle.Blocked,
            blobLifecycleFromChat(ActivityState.Waiting, loading = true, blocked = true),
        )
        assertEquals(
            BlobLifecycle.Working,
            blobLifecycleFromChat(ActivityState.Hidden, loading = true),
        )
    }

    @Test
    fun pendingToolApprovalIsDetected() {
        val parts = listOf(
            UIMessagePart.ToolCall(
                toolCallId = "1",
                toolName = "shell",
                arguments = "{}",
                approvalState = ToolApprovalState.Pending,
            )
        )
        assertTrue(parts.hasPendingToolApproval())
        assertFalse(
            listOf(
                UIMessagePart.ToolCall(
                    toolCallId = "1",
                    toolName = "shell",
                    arguments = "{}",
                    approvalState = ToolApprovalState.Auto,
                )
            ).hasPendingToolApproval()
        )
    }

    @Test
    fun runtimeHoldsDoneWhenLeavingWork() {
        val runtime = BlobRuntime()
        val spec = Avatar.Blob.generical()
        runtime.sample(0f, spec, BlobLifecycle.Working)
        runtime.sample(0.05f, spec, BlobLifecycle.Idle)
        assertEquals(BlobLifecycle.Done, runtime.shownLifecycle())
        val duringHold = runtime.sample(0.05f + BLOB_MORPH_SECONDS + 0.02f, spec, BlobLifecycle.Idle)
        assertEquals(BlobLifecycle.Done, runtime.shownLifecycle())
        assertTrue("done eyes are a happy squint", duringHold.left.h < duringHold.left.w)
        runtime.sample(0.05f + BLOB_DONE_HOLD_SECONDS + 0.05f, spec, BlobLifecycle.Idle)
        assertEquals(BlobLifecycle.Idle, runtime.shownLifecycle())
    }

    @Test
    fun genericalLookAroundTranslatesInsteadOfTilting() {
        val runtime = BlobRuntime()
        val spec = Avatar.Blob.generical()
        val a = runtime.sample(2.4f, spec, BlobLifecycle.Idle)
        val b = runtime.sample(3.8f, spec, BlobLifecycle.Idle)
        assertTrue(
            "idle look-around should move the pair",
            abs(a.lookX - b.lookX) > 0.001f || abs(a.lookY - b.lookY) > 0.001f,
        )
        assertEquals(0f, a.left.tilt, 0.2f)
        assertEquals(0f, a.right.tilt, 0.2f)
        assertEquals(0f, a.gaze.yaw, 0.001f)
        assertEquals(0f, a.gaze.pitch, 0.001f)
        assertEquals(0f, a.gaze.roll, 0.001f)
    }

    @Test
    fun allShapesProduceFiniteRadii() {
        for (shape in BlobShape.entries) {
            val radii = BlobShapes.radii(shape)
            assertEquals(BLOB_PROFILE_SAMPLES, radii.size)
            assertTrue(radii.all { it.isFinite() && it > 0.2f && it < 2.5f })
        }
    }

    private fun hypot2(x: Float, y: Float): Float = kotlin.math.sqrt(x * x + y * y)
}
