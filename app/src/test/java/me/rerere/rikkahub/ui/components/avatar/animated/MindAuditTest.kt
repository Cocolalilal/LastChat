package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Engine18 mind audit: the director's rules hold over long mixed sessions (several seeds). */
class MindAuditTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadClips() = MindFrameAuditTest.loadClips()
    }

    @Test
    fun directorRulesHold() {
        val sb = StringBuilder("seed,faceChanges,interrupted,overlaps<0.4,minGap,blinkMidMorph,idleSpins,spinsWhileNegative,maxPerMinQuiet,uncaused,budgetViolations,interruptsPrevented,budgetDeferred,expired,spinsBlockedIdle,spinsBlockedNegative,blinksDeferred,repeatLooksAvoided,glanceBacks,rareEpisodes\n")
        for (seed in listOf(3, 11, 19, 27)) {
            val r = MindFrameAuditTest.run(seed)
            val m = r.state.mind
            val glanceBacks = m.log.count { it.name == "glance back" }
            sb.append("$seed,${r.faceChanges},${r.interrupted},${r.overlaps},${"%.2f".format(r.minGap)},${r.blinkMidMorph},${r.idleSpins},${r.negSpins},${r.maxPerMinQuiet},${r.uncaused},${m.budgetViolations},${m.interruptsPrevented},${m.budgetDeferred},${m.expired},${m.spinsBlockedIdle},${m.spinsBlockedNegative},${m.blinksDeferred},${r.state.debugGaze.auditRepeatAvoided},$glanceBacks,${r.rare}\n")
            // Engine19: retargets mid-morph and blinks over a morph are the point now (no
            // gates); continuity is asserted on the channel trace (ChannelMetricsTest).
            assertEquals("uncaused changes (seed $seed): " + r.state.behaviorLog.filter { it.contains("cause=unspecified") }.take(3), 0, r.uncaused)
            assertEquals("budget violations (seed $seed)", 0, m.budgetViolations)
            assertEquals("idle spins (seed $seed)", 0, r.idleSpins + r.state.auditIdleSpins)
            assertEquals("spins while negative (seed $seed)", 0, r.negSpins + r.state.auditSpinsNegative)
            assertTrue("quiet idle stays restrained (seed $seed: ${r.maxPerMinQuiet}/min)", r.maxPerMinQuiet <= 8)
            assertTrue("every mind event has a cause", m.log.none { it.cause.isBlank() || it.cause == "unspecified" })
        }
        println(sb)
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { File(it).mkdirs(); File(it, "mind_audit.csv").writeText(sb.toString()) }
    }

    @Test
    fun rareEpisodesKeepLongIdleCooldowns() {
        val now = java.util.Calendar.getInstance().apply { set(2026, 9, 13, 15, 0, 0) }.timeInMillis
        for (seed in listOf(5, 9)) {
            AvatarDebugMetrics.enabled = true
            val s = AvatarMotionState(Random(seed))
            repeat((45 * 60 * 30)) { s.tick(1f / 30f, AvatarMotionHints(), now) }
            AvatarDebugMetrics.enabled = false
            // Long idle incl. resting eyes / dozing / waking: still nothing uncaused.
            assertTrue(s.behaviorLog.filter { it.contains("cause=unspecified") }.joinToString(), s.behaviorLog.none { it.contains("cause=unspecified") })
            val plays = s.episodes.plays.filter { (e, _) -> (e.tier == Tier.Rare || e.tier == Tier.VeryRare) && e != Episode.RestEyes }
            for ((e, t) in plays) {
                val prev = plays.lastOrNull { it.first == e && it.second < t }
                if (prev != null) assertTrue("$e repeated after ${t - prev.second} s idle", t - prev.second >= e.cooldown)
                if (prev == null) assertTrue("first $e already at $t s idle", t >= e.cooldown * 0.55f)
                assertTrue("rare cooldowns are 15+ idle min", e.cooldown >= 900f)
            }
        }
    }

    @Test
    fun perceptionAppraisalMemoryKnobs() {
        val k = MindKnobs()
        // Perception: alert notices sooner than tired / drowsy.
        assertTrue(MindRules.perceptionScale(0.9f, 0.8f, false, k) < MindRules.perceptionScale(0.2f, 0.2f, true, k))
        // Appraisal: repeated poke = annoyed; patience moves the threshold; habituation calms it.
        assertEquals(Meaning.Startled, Appraisal.poke(1, 0f, 0.1f, k))
        assertTrue(Appraisal.poke(3, 0f, 0.1f, k) != Meaning.Annoyed)
        assertTrue(Appraisal.poke(9, 0f, 0.1f, k) != Meaning.Annoyed)
        assertEquals(Meaning.Annoyed, Appraisal.poke(12, 0f, 0.1f, k))
        assertTrue(Appraisal.poke(12, 0f, 0.1f, k.copy(patience = 1f)) != Meaning.Annoyed)
        assertEquals(Meaning.Habituated, Appraisal.poke(1, 6f, 0.1f, k))
        assertEquals(Meaning.Satisfied, Appraisal.replyEnd(8f, 0.1f))
        // Memory: no repeated looks, glance-back candidates.
        val m = ShortTermMemory()
        m.looked(0.5f, 0.1f, 10f)
        assertTrue(m.lookedRecently(0.52f, 0.12f, 20f)); assertTrue(!m.lookedRecently(0.52f, 0.12f, 40f))
        m.happened(0.6f, 0.4f, 10f, "MessageSent")
        assertTrue(m.glanceBackCandidate(12f) == null); assertTrue(m.glanceBackCandidate(25f) != null)
        repeat(4) { m.poked(0f) }; m.update(300f); assertTrue(m.pokeFamiliar < 1f)
        // Knobs API.
        val o = MindKnobs.resolve(MindKnobs(), mapOf("patience" to 0.9f, "bogus" to 1f, "curiosity" to 2f))
        assertEquals(0.9f, o.patience, 1e-4f); assertEquals(1f, o.curiosity, 1e-4f)
        assertTrue(MindRules.budget(false, 0.5f, k.copy(expressiveness = 1f)) > MindRules.budget(false, 0.5f, k.copy(expressiveness = 0f)))
        assertTrue(MindRules.hold(0.2f, k) > MindRules.hold(0.9f, k))
    }

    @Test
    fun moodDriftsOverMinutes() {
        val m = MoodEngine(Random(1))
        val v0 = m.valence
        m.nudge(-0.8f, 0.3f)
        m.update(0.1f, 0f)
        assertTrue("no jump on an event", kotlin.math.abs(m.valence - v0) < 0.01f)
        repeat(100) { m.update(0.1f, 0f) } // 10 s
        assertTrue("10 s moves it only a little", kotlin.math.abs(m.valence - v0) < 0.07f)
        repeat(1800) { m.update(0.1f, 0f) } // +3 min
        assertTrue("minutes later the mood has moved", m.valence < v0 - 0.2f)
    }
}
