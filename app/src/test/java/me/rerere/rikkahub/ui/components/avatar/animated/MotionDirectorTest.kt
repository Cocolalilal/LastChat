package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * Motion continuity: no hard cuts, no size jumps, busy phases finish their phrase,
 * idle never settles into a visible loop.
 */
class MotionDirectorTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadClips() {
            val f = listOf(
                File("src/main/assets/avatar/clips068.json"),
                File("app/src/main/assets/avatar/clips068.json"),
            ).first { it.exists() }
            AvatarPackData.parseClips(f.readText())
        }

        private const val DT = 1f / 60f
    }

    private fun director(seed: Int) = MotionDirector(Random(seed)) { AvatarPackData.clips[it] }

    /** Scripted session: idle, quick replies, long replies, tools, typing, pokes. */
    private fun lifecycleAt(t: Float): MarkLifecycle {
        val s = t % 120f
        return when {
            s < 40f -> MarkLifecycle.Idle
            s < 40.4f -> MarkLifecycle.Thinking // reply came back almost instantly
            s < 55f -> MarkLifecycle.Idle
            s < 58f -> MarkLifecycle.Listening
            s < 60f -> MarkLifecycle.Thinking
            s < 68f -> MarkLifecycle.Streaming
            s < 80f -> MarkLifecycle.Idle
            s < 86f -> MarkLifecycle.Working
            s < 87f -> MarkLifecycle.Streaming
            else -> MarkLifecycle.Idle
        }
    }

    private class Track(val name: String) {
        var maxStep = 0f
        var worstAt = 0f
    }

    private fun simulate(seed: Int, seconds: Float, csv: StringBuilder? = null): Map<String, Float> {
        val d = director(seed)
        val rng = Random(seed * 31 + 7)
        var prevOut: BodyPose? = null
        val raw = Track("raw")
        val out = Track("out")
        var maxScaleStep = 0f
        var maxSquashStep = 0f
        var maxPosStep = 0f
        var t = 0f
        var nextGesture = 15f
        while (t < seconds) {
            d.setDesired(lifecycleAt(t))
            if (t >= nextGesture) {
                nextGesture = t + 6f + rng.nextFloat() * 20f
                d.gesture(MotionDirector.Gesture.entries[rng.nextInt(MotionDirector.Gesture.entries.size)])
            }
            val o = d.update(DT)
            csv?.append("%.3f,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.4f,%s,%s\n".format(
                t, o.pitch, o.yaw, o.roll, o.posY, o.squashX, o.squashY, o.scale, d.lifecycle, d.activeName))
            fun rotStep(a: BodyPose, b: BodyPose) = maxOf(
                abs(MarkClipEngine.wrap180(a.pitch - b.pitch)),
                abs(MarkClipEngine.wrap180(a.yaw - b.yaw)),
                abs(MarkClipEngine.wrap180(a.roll - b.roll)),
            )
            // Hand-overs (layer pushed / folded / dropped) must not move the pose at all:
            // compared at the same instant before and after the stack edit.
            if (d.handOverDelta > raw.maxStep) { raw.maxStep = d.handOverDelta; raw.worstAt = t }
            prevOut?.let { p ->
                val s = rotStep(o, p)
                if (s > out.maxStep) { out.maxStep = s; out.worstAt = t }
                maxScaleStep = maxOf(maxScaleStep, abs(o.scale - p.scale))
                maxSquashStep = maxOf(maxSquashStep, abs(o.squashX - p.squashX), abs(o.squashY - p.squashY))
                maxPosStep = maxOf(maxPosStep, abs(o.posY - p.posY))
            }
            assertTrue("scale out of band at $t: ${o.scale}", o.scale in 0.95f..1.04f)
            prevOut = o
            t += DT
        }
        return mapOf(
            "rawRot" to raw.maxStep, "rawAt" to raw.worstAt,
            "outRot" to out.maxStep, "outAt" to out.worstAt,
            "scale" to maxScaleStep, "squash" to maxSquashStep, "pos" to maxPosStep,
        )
    }

    @Test
    fun noHardCutsAcrossLongSessions() {
        for (seed in 1..6) {
            val m = simulate(seed, 600f)
            // Stack edits are invisible (new layers enter at weight ~0, folds are exact).
            assertTrue("seed $seed hand-over step ${m["rawRot"]} at ${m["rawAt"]}", m.getValue("rawRot") < 0.5f)
            // Output turn speed is capped (700°/s ≈ 11.7°/frame at 60 fps).
            assertTrue("seed $seed out rot step ${m["outRot"]} at ${m["outAt"]}", m.getValue("outRot") < 12f)
            assertTrue("seed $seed scale step ${m["scale"]}", m.getValue("scale") < 0.004f)
            assertTrue("seed $seed squash step ${m["squash"]}", m.getValue("squash") < 0.012f)
            assertTrue("seed $seed pos step ${m["pos"]}", m.getValue("pos") < 0.02f)
        }
    }

    @Test
    fun quickReplyStillFinishesThePhrase() {
        val d = director(3)
        repeat(240) { d.setDesired(MarkLifecycle.Idle); d.update(DT) }
        d.setDesired(MarkLifecycle.Thinking)
        d.update(DT)
        assertEquals(MarkLifecycle.Thinking, d.lifecycle)
        var t = 0f
        d.setDesired(MarkLifecycle.Idle)
        while (d.lifecycle == MarkLifecycle.Thinking && t < 10f) {
            d.update(DT); t += DT
        }
        assertTrue("busy phase cut short after ${t}s", t >= MotionDirector.MIN_BUSY_SEC - 0.05f)
        assertTrue("busy phase never released (${t}s)", t <= MotionDirector.MIN_BUSY_SEC + MotionDirector.MAX_FINISH_WAIT + 0.2f)
        assertTrue(d.finishedBusy)
    }

    @Test
    fun idleIsVariedAndHasStillMoments() {
        val d = director(11)
        val names = ArrayList<String>()
        var last = ""
        var t = 0f
        while (t < 900f) {
            d.setDesired(MarkLifecycle.Idle)
            d.update(DT)
            val n = d.activeName
            if (n != last) { names += n; last = n }
            t += DT
        }
        val holds = names.count { it == "settle" || it == "tilt" }
        assertTrue("expected 'do nothing' holds, got $names", holds >= 10)
        assertTrue("expected several beat types", names.toSet().size >= 3)
        // Never the same beat three times in a row.
        for (i in 2 until names.size) {
            assertTrue("repeat at $i: ${names.subList(i - 2, i + 1)}",
                !(names[i] == names[i - 1] && names[i] == names[i - 2]))
        }
        // Not periodic: the gap between successive Idle_B starts varies.
        val starts = names.indices.filter { names[it] == "Idle_B" }
        val gaps = starts.zipWithNext { a, b -> b - a }.toSet()
        assertTrue("Idle_B spacing looks periodic: $gaps", gaps.size >= 3)
    }

    @Test
    fun clipLidsAreOpenness() {
        val clip = AvatarPackData.clips.getValue("Working_F")
        assertEquals(1f, MarkClipEngine.sample(clip, 0.5f).lids, 1e-3f)
        assertTrue(MarkClipEngine.sample(clip, 1.28f).lids < 0.1f)
        assertEquals(1f, MarkClipEngine.sample(AvatarPackData.clips.getValue("Idle_B"), 1f).lids, 1e-3f)
    }

    @Test
    fun upwardLookIsSoftenedContinuously() {
        var prev = MotionDirector.softenUp(-20f, -10f)
        var y = -10f
        while (y < 40f) {
            y += 0.5f
            val v = MotionDirector.softenUp(-20f, y)
            assertTrue(abs(v - prev) < 0.5f)
            prev = v
        }
        assertEquals(10f, MotionDirector.softenUp(10f, 30f), 1e-4f)
        assertTrue(abs(MotionDirector.softenUp(-20f, 30f)) < 5f)
    }

    /** Writes a CSV of one session for the render sheets (only when a render dir is set). */
    @Test
    fun dumpSessionCsv() {
        val dir = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() } ?: return
        val sb = StringBuilder("t,pitch,yaw,roll,posY,squashX,squashY,scale,lifecycle,layer\n")
        simulate(5, 240f, sb)
        File(dir).mkdirs()
        File(dir, "motion-session.csv").writeText(sb.toString())
    }
}
