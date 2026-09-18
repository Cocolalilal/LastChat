package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure-engine tests. The geometry engine has no Android deps, so these run on the
 * plain JVM. They lock in the rewrite: Grok = small slanted dark slits on the
 * mark, Generical = large white-bordered glyphs that morph by path/pose, and the
 * body never spins.
 */
class BlobEngineTest {

    private val grok = Avatar.Blob.grok()
    private val generical = Avatar.Blob.generical()

    @Test
    fun genericalIdleIsFlatSymmetricAndTall() {
        val frame = frozen(generical, BlobLifecycle.Idle)
        assertEquals(1f, frame.left.a, 1e-4f)
        assertEquals(0f, frame.left.b, 1e-4f)
        assertEquals(0f, frame.left.c, 1e-4f)
        assertEquals(1f, frame.left.d, 1e-4f)
        assertEquals(0f, frame.left.tiltRad, 1e-4f)
        assertEquals(frame.left.hw, frame.right.hw, 1e-4f)
        assertEquals(frame.left.hh, frame.right.hh, 1e-4f)
        assertTrue("base face is a tall rounded-rect, not a thin capsule", frame.left.hh > frame.left.hw * 1.4f)
        assertEquals(0f, frame.left.cy, 0.08f)
    }

    @Test
    fun grokIdleIsSmallSlantedSlitsInTheUpperHalf() {
        val frame = frozen(grok, BlobLifecycle.Idle)
        assertTrue("Grok slits lean \\\\", frame.left.tiltRad < -0.15f)
        assertEquals(frame.left.tiltRad, frame.right.tiltRad, 1e-4f)
        assertTrue("Grok slits sit above centre", frame.left.cy < -0.12f)
        assertTrue("Grok slits are much shorter than they are wide", frame.left.hh < frame.left.hw)
        val gen = frozen(generical, BlobLifecycle.Idle)
        assertTrue("Grok eyes are much smaller than Generical", frame.left.hh < gen.left.hh * 0.25f)
    }

    @Test
    fun packsProduceDistinctIdleGeometry() {
        val g = frozen(grok, BlobLifecycle.Idle)
        val n = frozen(generical, BlobLifecycle.Idle)
        val same = abs(g.left.hw - n.left.hw) < 1e-4f && abs(g.left.hh - n.left.hh) < 1e-4f &&
            abs(g.left.tiltRad - n.left.tiltRad) < 1e-4f
        assertFalse("Grok and Generical idle faces must differ", same)
    }

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
                "working must be taller than waiting for $pack",
                faceFor(pack, BlobLifecycle.Working).left.h > faceFor(pack, BlobLifecycle.Waiting).left.h,
            )
        }
    }

    @Test
    fun genericalSheetPosesMorphPathNotJustFade() {
        val base = genericalPose(GenericalPose.Base)
        val look = genericalPose(GenericalPose.LookRight)
        val sad = genericalPose(GenericalPose.Sad)
        val wink = genericalPose(GenericalPose.Wink)
        val happy = genericalPose(GenericalPose.Happy)
        val pills = genericalPose(GenericalPose.Pills)
        val up = genericalPose(GenericalPose.LookUpRight)
        val goggle = genericalPose(GenericalPose.Goggle)
        assertTrue("look-right actually shifts", look.lookX > 0.15f)
        assertTrue("sad inner tops drop", sad.left.innerTopDrop > 0.2f)
        assertTrue("wink is asymmetric", abs(wink.left.h - wink.right.h) > 0.15f)
        assertTrue("happy is a thin squint", happy.left.h < 0.12f)
        assertTrue("happy uses mirrored tilt", abs(happy.left.tilt + happy.right.tilt) < 1e-4f)
        assertTrue("pills are horizontal", pills.left.w > pills.left.h * 3f)
        assertTrue("look-up-right clusters up-right", up.lookX > 0.15f && up.lookY < -0.15f)
        assertTrue("goggles have a flat top", goggle.left.topRound < 0.4f && goggle.left.bottomRound > 0.8f)
        assertTrue("base is not a goggle", base.left.topRound > 0.9f)
    }

    @Test
    fun blockedAndDoneUseMirroredTiltOnGrok() {
        val blocked = faceFor(BlobEyePack.Grok, BlobLifecycle.Blocked)
        assertEquals(-blocked.left.tilt, blocked.right.tilt, 1e-4f)
        assertTrue(abs(blocked.left.tilt) > 1f)
        val done = faceFor(BlobEyePack.Generical, BlobLifecycle.Done)
        assertEquals(-done.left.tilt, done.right.tilt, 1e-4f)
    }

    @Test
    fun blinkScheduleDeterministicAndSparse() {
        assertEquals(1.4f, firstBlinkTime(), 0.01f)
        assertEquals(1f, blinkLid(0.5f), 1e-4f)
        assertTrue("mid-blink the lid closes", blinkLid(firstBlinkTime() + 0.05f) < 1f)
        assertEquals(blinkLid(50.3f), blinkLid(50.3f), 0f)
    }

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
        assertTrue("light drifts", abs(a.lightX - b.lightX) > 1e-4f || abs(a.lightY - b.lightY) > 1e-4f)
        assertTrue("silhouette unchanged", a.radii.contentEquals(b.radii))
    }

    @Test
    fun frozenSampleHasNoDriftOrBlink() {
        val f = frozen(grok, BlobLifecycle.Idle)
        assertEquals(0f, f.cx, 1e-5f)
        assertEquals(0f, f.cy, 1e-5f)
        assertEquals(1f, f.lid, 1e-5f)
    }

    @Test
    fun grokEyePairStaysInsideDroplet() {
        val f = frozen(grok.copy(shape = BlobShape.Droplet), BlobLifecycle.Idle)
        assertTrue("eye pair pulled inside the droplet", f.eyePairInside())
    }

    @Test
    fun defaultGenericalEyeColorIsLight() {
        assertTrue(
            luminanceOf(parseArgb(Avatar.Blob.DEFAULT_EYE_COLOR)) > 0.8f,
        )
    }

    @Test
    fun defaultGrokEyeColorIsDark() {
        assertTrue(
            "Grok slits default dark so they read as marks, not capsules",
            luminanceOf(parseArgb(Avatar.Blob.DEFAULT_GROK_EYE_COLOR)) < 0.2f,
        )
    }

    @Test
    fun grokSlitResolverTurnsWhiteDefaultIntoBlack() {
        val body = parseArgb("#4A7DC7")
        val slit = resolveGrokSlitColor(body, parseArgb(Avatar.Blob.DEFAULT_EYE_COLOR))
        assertTrue("white default becomes a dark slit", luminanceOf(slit) < 0.2f)
    }

    @Test
    fun grokSlitResolverKeepsContrastOnDarkBodies() {
        val body = parseArgb("#0A0A0C")
        val slit = resolveGrokSlitColor(body, parseArgb("#171717"))
        assertTrue("black-on-black lifts to a light mark", luminanceOf(slit) > 0.7f)
    }

    @Test
    fun knobsClamp() {
        assertEquals(1.5f, generical.copy(eyeSize = 9f).clampedEyeSize(), 1e-4f)
        assertEquals(0.6f, generical.copy(eyeSize = 0f).clampedEyeSize(), 1e-4f)
        assertEquals(1.4f, generical.copy(eyeSpacing = 9f).clampedEyeSpacing(), 1e-4f)
        assertEquals(0f, generical.copy(lookAround = -3f).clampedLookAround(), 1e-4f)
        assertEquals(1f, generical.copy(glowStrength = 9f).clampedGlowStrength(), 1e-4f)
    }

    @Test
    fun leavingActiveWorkHoldsDoneThenIdles() {
        val rt = BlobRuntime(BlobLifecycle.Working)
        rt.sample(0f, grok, BlobLifecycle.Working)
        rt.sample(0.1f, grok, BlobLifecycle.Idle)
        assertEquals(BlobLifecycle.Done, rt.shownLifecycle())
        rt.sample(0.1f + BLOB_DONE_HOLD_SECONDS + 0.2f, grok, BlobLifecycle.Idle)
        assertEquals(BlobLifecycle.Idle, rt.shownLifecycle())
    }

    @Test
    fun shapeChangeMorphsGraduallyNotInstantly() {
        val rt = BlobRuntime(BlobLifecycle.Idle)
        rt.sample(0f, generical, BlobLifecycle.Idle)
        val dropletSpec = generical.copy(shape = BlobShape.Droplet)
        rt.sample(0.1f, dropletSpec, BlobLifecycle.Idle)
        val mid = rt.sample(0.1f + BLOB_MORPH_SECONDS / 2f, dropletSpec, BlobLifecycle.Idle).radii
        val fullDroplet = BlobShapes.radii(BlobShape.Droplet)
        val circle = BlobShapes.radii(BlobShape.Circle)
        assertFalse("mid-morph is not still a circle", mid.contentEquals(circle))
        assertFalse("mid-morph is not the full droplet yet", mid.contentEquals(fullDroplet))
    }

    private fun frozen(spec: Avatar.Blob, lifecycle: BlobLifecycle): BlobFrame =
        BlobRuntime(lifecycle).sample(0f, spec, lifecycle, motion = 0f)
}
