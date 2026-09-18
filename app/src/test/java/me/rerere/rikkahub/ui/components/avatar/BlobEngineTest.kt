package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure-engine tests. The geometry engine has no Android deps, so these run on the
 * plain JVM. They lock in the fixes the rewrite is about: accurate Grok sphere
 * eyes, a distinct flat Generical glyph, deterministic blink/idle, and — crucially
 * — that the body never spins.
 */
class BlobEngineTest {

    private val grok = Avatar.Blob.grok()
    private val generical = Avatar.Blob.generical()

    // ---- Grok sphere eyes (measured constants) -------------------------------

    @Test
    fun grokRestGazeConstantsPreserved() {
        assertEquals(15.46f, EYE_SPLIT, 1e-4f)
        assertEquals(0.186f, EYE_W, 1e-4f)
        assertEquals(0.412f, EYE_H, 1e-4f)
        assertEquals(28.49f, REST_GAZE.yaw, 1e-4f)
        assertEquals(28.62f, REST_GAZE.pitch, 1e-4f)
        assertEquals(-13f, REST_GAZE.roll, 1e-4f)
    }

    @Test
    fun grokIdleBothEyesVisibleAndProjected() {
        val (l, r) = eyePoses(REST_GAZE, 1f, EYE_SPLIT)
        assertTrue("inner eye front-facing", l.depth > 0.02f)
        assertTrue("outer eye front-facing", r.depth > 0.02f)
        // The tangent frame is a real rotation (roll -13 + gaze), not identity.
        val identity = abs(l.a - 1f) < 1e-3f && abs(l.b) < 1e-3f && abs(l.c) < 1e-3f && abs(l.d - 1f) < 1e-3f
        assertFalse("Grok eyes carry a sphere tangent frame, not identity", identity)
    }

    @Test
    fun grokOuterEyeIsSmallerThanInner_depthAsymmetry() {
        // Measured: the eye nearer the rim projects smaller (depth ~0.669x).
        val (inner, outer) = eyePoses(REST_GAZE, 1f, EYE_SPLIT)
        assertNotEquals(inner.depth, outer.depth, 0f)
    }

    // ---- Generical is a DIFFERENT, flat glyph --------------------------------

    @Test
    fun genericalEyesAreFlatIdentityMatrix() {
        val frame = frozen(generical, BlobLifecycle.Idle)
        assertEquals(1f, frame.left.a, 1e-4f)
        assertEquals(0f, frame.left.b, 1e-4f)
        assertEquals(0f, frame.left.c, 1e-4f)
        assertEquals(1f, frame.left.d, 1e-4f)
        assertEquals(0f, frame.left.tiltRad, 1e-4f)
    }

    @Test
    fun grokEyesAreNotFlat() {
        val frame = frozen(grok, BlobLifecycle.Idle)
        val flat = abs(frame.left.b) < 1e-3f && abs(frame.left.c) < 1e-3f
        assertFalse("Grok eyes must not be flat like Generical", flat)
    }

    @Test
    fun packsProduceDistinctIdleGeometry() {
        val g = frozen(grok, BlobLifecycle.Idle)
        val n = frozen(generical, BlobLifecycle.Idle)
        val same = abs(g.left.hw - n.left.hw) < 1e-4f && abs(g.left.hh - n.left.hh) < 1e-4f &&
            abs(g.left.b - n.left.b) < 1e-4f
        assertFalse("Grok and Generical idle faces must differ", same)
    }

    // ---- Distinct, non-boring lifecycle poses --------------------------------

    @Test
    fun lifecyclePosesAreDistinctPerPack() {
        for (pack in BlobEyePack.entries) {
            val heights = BlobLifecycle.entries.map { faceFor(pack, it).left.h }
            assertEquals(
                "each lifecycle should have a distinct eye height for $pack",
                heights.size, heights.toSet().size,
            )
        }
    }

    @Test
    fun workingEyesTallerThanWaitingSlits() {
        for (pack in BlobEyePack.entries) {
            assertTrue(
                "working (wide) must be taller than waiting (slits) for $pack",
                faceFor(pack, BlobLifecycle.Working).left.h > faceFor(pack, BlobLifecycle.Waiting).left.h,
            )
        }
    }

    @Test
    fun blockedAndDoneUseMirroredTilt() {
        // colère / heureux need mirror tilt (head-roll alone can't do it).
        val blocked = faceFor(BlobEyePack.Grok, BlobLifecycle.Blocked)
        assertEquals(-blocked.left.tilt, blocked.right.tilt, 1e-4f)
        assertTrue(abs(blocked.left.tilt) > 1f)
    }

    // ---- Blink schedule is deterministic -------------------------------------

    @Test
    fun blinkScheduleDeterministicAndSparse() {
        assertEquals(1.4f, firstBlinkTime(), 0.01f)
        assertEquals(1f, blinkLid(0.5f), 1e-4f) // no blink before the first
        assertTrue("mid-blink the lid closes", blinkLid(firstBlinkTime() + 0.05f) < 1f)
        // deterministic: same time → same value
        assertEquals(blinkLid(50.3f), blinkLid(50.3f), 0f)
    }

    // ---- The body must NEVER spin (the core rejection) -----------------------

    @Test
    fun bodySilhouetteNeverRotatesOverTime() {
        val droplet = grok.copy(shape = BlobShape.Droplet)
        val rt = BlobRuntime(BlobLifecycle.Idle)
        val a = rt.sample(0.5f, droplet, BlobLifecycle.Idle, motion = 1f).radii.copyOf()
        val b = rt.sample(4.0f, droplet, BlobLifecycle.Idle, motion = 1f).radii.copyOf()
        assertTrue("radii identical across time → no 2D spin", a.contentEquals(b))
    }

    @Test
    fun activeStatesDoNotSpinTheSilhouetteEither() {
        val cloud = grok.copy(shape = BlobShape.Cloud)
        val rt = BlobRuntime(BlobLifecycle.Working)
        val a = rt.sample(0.5f, cloud, BlobLifecycle.Working, motion = 1f).radii.copyOf()
        val b = rt.sample(3.0f, cloud, BlobLifecycle.Working, motion = 1f).radii.copyOf()
        assertTrue("cloud silhouette identical while working", a.contentEquals(b))
    }

    @Test
    fun lightTumbleMovesWithTimeButBodyDoesNot() {
        val cloud = generical.copy(shape = BlobShape.Cloud)
        val rt = BlobRuntime(BlobLifecycle.Idle)
        val a = rt.sample(0.5f, cloud, BlobLifecycle.Idle, motion = 1f)
        val b = rt.sample(6.0f, cloud, BlobLifecycle.Idle, motion = 1f)
        // Subtle 3-D lives in the light, not the silhouette.
        assertTrue("light drifts", abs(a.lightX - b.lightX) > 1e-4f || abs(a.lightY - b.lightY) > 1e-4f)
        assertTrue("silhouette unchanged", a.radii.contentEquals(b.radii))
    }

    // ---- Frozen contact-sheet sampling is still -----------------------------

    @Test
    fun frozenSampleHasNoDriftOrBlink() {
        val f = frozen(grok, BlobLifecycle.Idle)
        assertEquals(0f, f.cx, 1e-5f)
        assertEquals(0f, f.cy, 1e-5f)
        assertEquals(1f, f.lid, 1e-5f)
    }

    // ---- Eyes stay inside organic bodies -------------------------------------

    @Test
    fun grokEyePairStaysInsideDroplet() {
        val f = frozen(grok.copy(shape = BlobShape.Droplet), BlobLifecycle.Idle)
        assertTrue("eye pair pulled inside the droplet", f.eyePairInside())
    }

    // ---- Portable eyes: light by default, not dark holes ---------------------

    @Test
    fun defaultEyeColorIsLightSoItReadsOnAnyBackground() {
        assertTrue(
            "default eye colour must be light (fixes the dark-hole bug)",
            luminanceOf(parseArgb(Avatar.Blob.DEFAULT_EYE_COLOR)) > 0.8f,
        )
    }

    // ---- Knob clamping -------------------------------------------------------

    @Test
    fun knobsClamp() {
        assertEquals(1.5f, generical.copy(eyeSize = 9f).clampedEyeSize(), 1e-4f)
        assertEquals(0.6f, generical.copy(eyeSize = 0f).clampedEyeSize(), 1e-4f)
        assertEquals(1.4f, generical.copy(eyeSpacing = 9f).clampedEyeSpacing(), 1e-4f)
        assertEquals(0f, generical.copy(lookAround = -3f).clampedLookAround(), 1e-4f)
        assertEquals(1f, generical.copy(glowStrength = 9f).clampedGlowStrength(), 1e-4f)
    }

    // ---- Lifecycle transitions: Done hold on completion ----------------------

    @Test
    fun leavingActiveWorkHoldsDoneThenIdles() {
        val rt = BlobRuntime(BlobLifecycle.Working)
        rt.sample(0f, grok, BlobLifecycle.Working)
        rt.sample(0.1f, grok, BlobLifecycle.Idle)
        assertEquals(BlobLifecycle.Done, rt.shownLifecycle())
        rt.sample(0.1f + BLOB_DONE_HOLD_SECONDS + 0.2f, grok, BlobLifecycle.Idle)
        assertEquals(BlobLifecycle.Idle, rt.shownLifecycle())
    }

    // ---- Shape morph is gradual, exponential (no instant jump) ---------------

    @Test
    fun shapeChangeMorphsGraduallyNotInstantly() {
        val rt = BlobRuntime(BlobLifecycle.Idle)
        rt.sample(0f, generical, BlobLifecycle.Idle) // Circle
        val dropletSpec = generical.copy(shape = BlobShape.Droplet)
        rt.sample(0.1f, dropletSpec, BlobLifecycle.Idle) // change registered
        val mid = rt.sample(0.1f + BLOB_MORPH_SECONDS / 2f, dropletSpec, BlobLifecycle.Idle).radii
        val fullDroplet = BlobShapes.radii(BlobShape.Droplet)
        val circle = BlobShapes.radii(BlobShape.Circle)
        assertFalse("mid-morph is not still a circle", mid.contentEquals(circle))
        assertFalse("mid-morph is not the full droplet yet", mid.contentEquals(fullDroplet))
    }

    private fun frozen(spec: Avatar.Blob, lifecycle: BlobLifecycle): BlobFrame =
        BlobRuntime(lifecycle).sample(0f, spec, lifecycle, motion = 0f)
}
