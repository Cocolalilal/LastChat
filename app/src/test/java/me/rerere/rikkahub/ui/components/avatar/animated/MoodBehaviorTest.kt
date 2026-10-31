package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/** Mood engine, parametric face and UI reactions over long simulated sessions. */
class MoodBehaviorTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadClips() {
            val f = listOf(File("src/main/assets/avatar/clips068.json"), File("app/src/main/assets/avatar/clips068.json")).first { it.exists() }
            AvatarPackData.parseClips(f.readText())
        }
        private const val DT = 1f / 60f
        // 2026-10-13 15:00 and 03:00 local-ish (only the hour matters).
        private fun at(hour: Int): Long = java.util.Calendar.getInstance().apply {
            set(2026, 9, 13, hour, 0, 0)
        }.timeInMillis
    }

    private fun session(seed: Int, hour: Int, seconds: Float, events: Boolean): Triple<AvatarMotionState, Set<FaceAccent>, Float> {
        val stage = AvatarStage(Random(seed))
        val s = AvatarMotionState(Random(seed))
        s.stage = stage
        s.stageId = stage.newId()
        stage.update(s.stageId, 1, androidx.compose.ui.geometry.Offset(100f, 400f), 50f, "blob|grok", 1080f, 2200f)
        val other = stage.newId()
        stage.update(other, 1, androidx.compose.ui.geometry.Offset(900f, 1500f), 50f, "blob|generical", 1080f, 2200f)
        val seen = HashSet<FaceAccent>()
        var maxFaceStep = 0f
        var prev: FaceParams? = null
        var t = 0f
        val now0 = at(hour)
        val kinds = listOf(
            AvatarStage.EventKind.Copied, AvatarStage.EventKind.Deleted, AvatarStage.EventKind.KeyboardOpened,
            AvatarStage.EventKind.MenuOpened, AvatarStage.EventKind.PillExpanded, AvatarStage.EventKind.Regenerated,
            AvatarStage.EventKind.Error, AvatarStage.EventKind.AppResumed, AvatarStage.EventKind.Fling,
        )
        val rng = Random(seed + 99)
        var nextEvent = 5f
        while (t < seconds) {
            val nowMs = now0 + (t * 1000).toLong()
            if (events && t >= nextEvent) {
                nextEvent = t + 4f + rng.nextFloat() * 10f
                val k = kinds[rng.nextInt(kinds.size)]
                stage.emit(k, at = androidx.compose.ui.geometry.Offset(500f, 900f), nowMs = nowMs, magnitude = 30f)
                if (rng.nextFloat() < 0.1f) s.poke()
            }
            s.tick(DT, AvatarMotionHints(), nowMs)
            seen += s.currentAccent
            val f = s.frame.face
            prev?.let { p ->
                for (side in 0..1) {
                    val a = p.eye(side); val b = f.eye(side)
                    maxFaceStep = maxOf(maxFaceStep, abs(a.sx - b.sx), abs(a.sy - b.sy), abs(a.upper - b.upper), abs(a.lower - b.lower), abs(a.dx - b.dx) * 4f)
                }
            }
            prev = f
            t += DT
        }
        return Triple(s, seen, maxFaceStep)
    }

    @Test
    fun reactionsAreVariedAndContinuous() {
        for (seed in 1..4) {
            val (_, seen, step) = session(seed, 15, 600f, events = true)
            assertTrue("seed $seed accents $seen", seen.size >= 6)
            assertTrue("seed $seed face step $step", step < 0.09f) // ~5 frames for a full lid travel at most
        }
    }

    @Test
    fun lateNightIsSleepierThanAfternoon() {
        val (day, _, _) = session(5, 15, 400f, events = false)
        val (night, _, _) = session(5, 3, 400f, events = false)
        assertTrue("arousal day ${day.moodArousal} night ${night.moodArousal}", night.moodArousal < day.moodArousal - 0.1f)
    }

    @Test
    fun errorsLowerTheMood() {
        val stage = AvatarStage(Random(1))
        val s = AvatarMotionState(Random(1))
        s.stage = stage; s.stageId = stage.newId()
        stage.update(s.stageId, 1, androidx.compose.ui.geometry.Offset(100f, 400f), 50f, "blob|grok", 1080f, 2200f)
        repeat(60) { s.tick(DT, AvatarMotionHints(), at(15)) }
        val before = s.moodValence
        stage.emit(AvatarStage.EventKind.Error)
        repeat(60) { s.tick(DT, AvatarMotionHints(), at(15)) }
        // Engine17.1: the retired Worried face → calm concentrated reaction (right away).
        val f = s.frame
        assertTrue("${f.exprFrom}/${f.exprTo}/${f.exprTop}", "angered or concentrated" in listOf(f.exprFrom, f.exprTo, f.exprTop))
        // Engine18: the mood itself only drifts — no jump on the event, clearly lower minutes later.
        assertTrue("no instant mood jump", s.moodValence > before - 0.05f)
        repeat((180 / DT).toInt()) { s.tick(DT, AvatarMotionHints(), at(15)) }
        assertTrue("mood lower after minutes (${s.moodValence} vs $before)", s.moodValence < before - 0.15f)
    }

    @Test
    fun pokedWhileCrossNeverShowsHappyOrGlee() {
        val stage = AvatarStage(Random(42))
        val s = AvatarMotionState(Random(42))
        s.stage = stage; s.stageId = stage.newId()
        stage.update(s.stageId, 1, androidx.compose.ui.geometry.Offset(100f, 400f), 50f, "blob|grok", 1080f, 2200f)
        repeat(60) { s.tick(DT, AvatarMotionHints(), at(15)) }

        // 1. Make it cross through repeated rapid pokes
        repeat(3) {
            s.poke()
            repeat(15) { s.tick(DT, AvatarMotionHints(), at(15)) }
        }
        assertTrue("avatar must be cross after rapid poke burst", s.crossNow())

        // Tick until forgiveness plays and crossNow() becomes false again (capped at 6 s)
        var forgiven = false
        val maxForgiveFrames = (6.0f / DT).toInt()
        for (i in 0 until maxForgiveFrames) {
            s.tick(DT, AvatarMotionHints(), at(15))
            if (!s.crossNow()) {
                forgiven = true
                break
            }
        }
        assertTrue("forgiveness beat must happen within 6 s so crossNow() clears", forgiven)

        // Allow any forgiveness accent to clear before putting it back into cross state
        repeat((1.6f / DT).toInt()) { s.tick(DT, AvatarMotionHints(), at(15)) }

        // 2. Put it back in a cross state
        repeat(3) {
            s.poke()
            repeat(15) { s.tick(DT, AvatarMotionHints(), at(15)) }
        }
        assertTrue("avatar must be cross again", s.crossNow())

        // 3. Poke ONCE while cross and watch ~1.5 s (well inside fresh forgiveIn window)
        s.poke()
        var gleeOnsets = 0
        var prevAccent = s.currentAccent
        var sawHappyExpr = false
        val watchFrames = (1.5f / DT).toInt()
        repeat(watchFrames) {
            s.tick(DT, AvatarMotionHints(), at(15))
            val curAccent = s.currentAccent
            if (prevAccent != FaceAccent.Glee && curAccent == FaceAccent.Glee) {
                gleeOnsets++
            }
            prevAccent = curAccent
            val f = s.frame
            if (f.exprTo == "Happy" || f.exprFrom == "Happy" || f.exprTop == "Happy") {
                sawHappyExpr = true
            }
        }
        assertEquals("no Glee accent onset must occur after poke while cross", 0, gleeOnsets)
        assertTrue("no Happy expression must appear after poke while cross", !sawHappyExpr)

        // Case 2: Avatar has an Angry face on screen
        val s2 = AvatarMotionState(Random(7))
        s2.stage = stage; s2.stageId = stage.newId()
        stage.update(s2.stageId, 1, androidx.compose.ui.geometry.Offset(100f, 400f), 50f, "blob|generical", 1080f, 2200f)
        repeat(30) { s2.tick(DT, AvatarMotionHints(), at(15)) }
        s2.debugReact("Angry", 3f)
        repeat(10) { s2.tick(DT, AvatarMotionHints(), at(15)) }
        assertTrue("avatar must be cross when showing Angry", s2.crossNow())

        s2.poke()
        var gleeOnsets2 = 0
        var prevAccent2 = s2.currentAccent
        var sawHappyExpr2 = false
        repeat((1.5f / DT).toInt()) {
            s2.tick(DT, AvatarMotionHints(), at(15))
            val curAccent = s2.currentAccent
            if (prevAccent2 != FaceAccent.Glee && curAccent == FaceAccent.Glee) {
                gleeOnsets2++
            }
            prevAccent2 = curAccent
            val f = s2.frame
            if (f.exprTo == "Happy" || f.exprFrom == "Happy" || f.exprTop == "Happy") {
                sawHappyExpr2 = true
            }
        }
        assertEquals("no Glee accent onset must occur after poke while angry", 0, gleeOnsets2)
        assertTrue("no Happy expression must appear after poke while angry", !sawHappyExpr2)
    }

    @Test
    fun pokedWhileNotCrossReachesAmusedAndHappy() {
        val k = MindKnobs()
        val appraised = Appraisal.poke(1, familiar = 4f, valence = 0.3f, k)
        assertEquals(Meaning.Amused, appraised)

        val stage = AvatarStage(Random(123))
        val s = AvatarMotionState(Random(123))
        s.stage = stage; s.stageId = stage.newId()
        stage.update(s.stageId, 1, androidx.compose.ui.geometry.Offset(100f, 400f), 50f, "blob|grok", 1080f, 2200f)
        repeat(60) { s.tick(DT, AvatarMotionHints(), at(15)) }

        s.debugMoodNudge(0.5f, 0f)
        repeat((180 / DT).toInt()) { s.tick(DT, AvatarMotionHints(), at(15)) }

        repeat(4) { s.stm.poked(s.frame.timeSec) }

        assertTrue("valence must be > 0.2f for Amused: ${s.moodValence}", s.moodValence > 0.2f)
        assertTrue("familiar must be > 3f: ${s.stm.pokeFamiliar}", s.stm.pokeFamiliar > 3f)
        assertTrue("avatar should not be cross", !s.crossNow())

        s.poke()
        var sawGlee = false
        repeat(60) {
            s.tick(DT, AvatarMotionHints(), at(15))
            if (s.currentAccent == FaceAccent.Glee) sawGlee = true
        }
        assertTrue("poking when not cross and amused reaches Glee accent", sawGlee)
    }
}
