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
        assertTrue("Generical idle eyes are taller than wide", face.left.h > face.left.w * 2f)
        assertEquals(0.145f, face.left.w, 0.001f)
    }

    @Test
    fun genericalBlockedIsAWinkNotGrokTilts() {
        val face = faceFor(BlobEyePack.Generical, BlobLifecycle.Blocked)
        assertEquals(0f, face.left.tilt, 0.001f)
        assertEquals(0f, face.right.tilt, 0.001f)
        assertTrue("blocked wink keeps one tall eye", face.left.h > face.left.w)
        assertTrue("blocked wink collapses the other eye to a dash", face.right.h < face.right.w)
    }

    @Test
    fun grokLifecycleFacesAreVisiblyDistinct() {
        val idle = faceFor(BlobEyePack.Grok, BlobLifecycle.Idle)
        val thinking = faceFor(BlobEyePack.Grok, BlobLifecycle.Thinking)
        val working = faceFor(BlobEyePack.Grok, BlobLifecycle.Working)
        val waiting = faceFor(BlobEyePack.Grok, BlobLifecycle.Waiting)
        val blocked = faceFor(BlobEyePack.Grok, BlobLifecycle.Blocked)
        val done = faceFor(BlobEyePack.Grok, BlobLifecycle.Done)
        assertTrue("thinking looks down, not rest-up", thinking.gaze.pitch < 0f)
        assertTrue("thinking pitch flips vs rest", abs(thinking.gaze.pitch - idle.gaze.pitch) > 20f)
        assertTrue("working eyes are much taller than idle", working.left.h > idle.left.h * 1.4f)
        assertTrue("waiting eyes are slits", waiting.left.h < waiting.left.w)
        assertEquals(30f, blocked.left.tilt, 0.001f)
        assertTrue("done is a squint", done.left.h < done.left.w)
        val faces = listOf(idle, thinking, working, waiting, blocked, done)
        for (i in faces.indices) {
            for (j in i + 1 until faces.size) {
                val a = faces[i]
                val b = faces[j]
                val dist = abs(a.gaze.yaw - b.gaze.yaw) +
                    abs(a.gaze.pitch - b.gaze.pitch) +
                    abs(a.left.h - b.left.h) +
                    abs(a.left.tilt - b.left.tilt)
                assertTrue("Grok $i vs $j should differ, dist=$dist", dist > 8f)
            }
        }
    }

    @Test
    fun genericalLifecycleFacesAreVisiblyDistinct() {
        val idle = faceFor(BlobEyePack.Generical, BlobLifecycle.Idle)
        val thinking = faceFor(BlobEyePack.Generical, BlobLifecycle.Thinking)
        val waiting = faceFor(BlobEyePack.Generical, BlobLifecycle.Waiting)
        val blocked = faceFor(BlobEyePack.Generical, BlobLifecycle.Blocked)
        val done = faceFor(BlobEyePack.Generical, BlobLifecycle.Done)
        assertTrue("thinking looks up", thinking.lookY < -0.05f)
        assertTrue("waiting is a dash pair", waiting.left.h < 0.1f)
        assertTrue("blocked is a wink", blocked.left.h > blocked.right.h * 2f)
        assertTrue("done is a happy squint", done.left.h < done.left.w)
        assertTrue("idle stays a tall lozenge", idle.left.h > idle.left.w * 2f)
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
            assertTrue(radii.all { it.isFinite() && it > 0.2f && it <= 1.0001f })
            val peak = radii.maxOrNull() ?: 0f
            assertEquals("peak-normalized $shape", 1f, peak, 1e-4f)
        }
    }

    @Test
    fun knobsScaleGenericalEyesAndSplit() {
        val runtime = BlobRuntime()
        val base = runtime.sample(1.1f, Avatar.Blob.generical(), BlobLifecycle.Idle, reduceMotion = true)
        val big = BlobRuntime().sample(
            1.1f,
            Avatar.Blob.generical().copy(eyeSize = 1.4f, eyeSpacing = 1.3f),
            BlobLifecycle.Idle,
            reduceMotion = true,
        )
        assertTrue(big.left.w > base.left.w * 1.2f)
        assertTrue(big.split > base.split * 1.2f)
        assertEquals(1.4f, big.left.w / base.left.w, 0.02f)
    }

    @Test
    fun glanceMovesGenericalLookAndGrokGaze() {
        val glance = BlobGlance(x = 1f, y = 0.5f, strength = 1f, id = 1)
        val gen = BlobRuntime().sample(
            1.1f,
            Avatar.Blob.generical(),
            BlobLifecycle.Idle,
            reduceMotion = true,
            glance = glance,
        )
        val gen0 = BlobRuntime().sample(
            1.1f,
            Avatar.Blob.generical(),
            BlobLifecycle.Idle,
            reduceMotion = true,
        )
        assertTrue(gen.lookX > gen0.lookX)
        assertTrue(gen.lookY > gen0.lookY)

        val grok = BlobRuntime().sample(
            1.1f,
            Avatar.Blob.grok(),
            BlobLifecycle.Idle,
            reduceMotion = true,
            glance = glance,
        )
        val grok0 = BlobRuntime().sample(
            1.1f,
            Avatar.Blob.grok(),
            BlobLifecycle.Idle,
            reduceMotion = true,
        )
        assertTrue(grok.gaze.yaw > grok0.gaze.yaw)
        assertTrue("Grok stays a hole-cut pack", grok.flatFill)
        assertTrue(grok.pack == BlobEyePack.Grok)
    }

    @Test
    fun grokCloudSpinsWhileGenericalIdleGazeStaysFlat() {
        val a = BlobRuntime().sample(2.0f, Avatar.Blob.grok().copy(shape = BlobShape.Cloud), BlobLifecycle.Idle)
        val b = BlobRuntime().sample(2.6f, Avatar.Blob.grok().copy(shape = BlobShape.Cloud), BlobLifecycle.Idle)
        assertTrue(
            "cloud profile should rotate",
            a.radii.zip(b.radii).any { (x, y) -> abs(x - y) > 0.01f },
        )
        val gen = BlobRuntime().sample(4.1f, Avatar.Blob.generical(), BlobLifecycle.Idle)
        assertEquals(0f, gen.gaze.yaw, 0.001f)
        assertEquals(0f, gen.gaze.pitch, 0.001f)
        assertEquals(0f, gen.left.tilt, 0.2f)
    }

    @Test
    fun idleAccentEnvelopeIsZeroOutsideBeats() {
        val (_, w) = idleAccentEnvelope(0.2f)
        assertEquals(0f, w, 0.001f)
    }

    private fun hypot2(x: Float, y: Float): Float = kotlin.math.sqrt(x * x + y * y)
}
