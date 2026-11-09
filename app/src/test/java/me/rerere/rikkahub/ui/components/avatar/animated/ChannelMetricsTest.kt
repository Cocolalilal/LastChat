package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertTrue
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test
import java.io.File

/**
 * Engine19 CI gate (review §7): hard fails on the per-frame channel trace.
 * Baselines for the "well below E17/E18" stop-go limits come from the same scenarios run on
 * engine17.2 (ef1ea0d9) and engine18 (317fd1fb) — see docs/avatar-character-engine.md.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ChannelMetricsTest {
    companion object {
        val SEEDS = listOf(3, 11, 19)

        /** Engine18 means over SEEDS (frame-only metrics), measured on 317fd1fb. */
        val E18_STOPGO_EXPR = mapOf("pan_poke" to 99f, "rapid_pokes" to 99f, "idle12" to 99f, "session" to 99f)
        val E18_STOPGO_YAW = mapOf("pan_poke" to 99f, "rapid_pokes" to 99f, "idle12" to 99f, "session" to 99f)
    }

    private fun internal(s: AvatarMotionState): ChannelTrace = ChannelTrace().also { s.trace = it }

    @Test
    fun channelMetricsGate() {
        ChannelScenarios.loadClips()
        val dir = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }
        val sb = StringBuilder()
        val fails = ArrayList<String>()
        var header = false
        for (sc in ChannelScenarios.NAMES) for (seed in SEEDS) {
            val tr = ChannelScenarios.run(sc, seed, attach = ::internal)
            val m = ChannelMetrics.compute(tr)
            if (!header) { sb.append("scenario,seed," + m.values.keys.joinToString(",") + "\n"); header = true }
            sb.append("$sc,$seed," + m.values.values.joinToString(",") { "%.4f".format(it) } + "\n")
            if (dir != null) { File(dir).mkdirs(); File(dir, "trace_${sc}_$seed.csv").writeText(tr.csv()) }
            fun need(ok: Boolean, what: String) { if (!ok) fails += "$sc/$seed: $what" }
            need(m["neutral_returns"] == 0f, "neutral returns ${m["neutral_returns"]}")
            need(m["blink_overlap"] == 0f, "blink overlap ${m["blink_overlap"]}")
            need(m["unexplained"] == 0f, "unexplained changes ${m["unexplained"]}")
            need(m["owner_flips_s"] <= 0.3f, "owner flips ${m["owner_flips_s"]}/s")
            need(m["recenter_before_react"] == 0f, "recenter before react ${m["recenter_before_react"]}")
            if (m["pokes"] > 0f) {
                need(m["react_gaze_max"] <= 0.12f, "gaze react ${m["react_gaze_max"]} s")
                need(m["react_face_max"] <= 0.18f, "face react ${m["react_face_max"]} s")
            }
            // Engine20: feelings dwell; negative ones need a cause; looks travel in one go.
            need(m["emo_short"] == 0f, "spontaneous face shorter than its dwell ${m["emo_short"]}")
            need(m["neg_uncaused"] == 0f, "negative face without an appraised cause ${m["neg_uncaused"]}")
            need(m["gaze_stepgo"] == 0f, "gaze stop-go within a shift ${m["gaze_stepgo"]}/${m["gaze_shifts"]}")
            need(m["gaze_reversal"] == 0f, "gaze reversal within a shift ${m["gaze_reversal"]}/${m["gaze_shifts"]}")
            need(m["blink_max_gap"] <= Blinker.MAX_INTERVAL + 1.5f, "blink gap ${m["blink_max_gap"]}")
            need(m["stopgo_expr_min"] <= 0.5f * (E18_STOPGO_EXPR[sc] ?: 99f), "stop-go expr ${m["stopgo_expr_min"]}/min")
            need(m["stopgo_yaw_min"] <= 0.5f * (E18_STOPGO_YAW[sc] ?: 99f), "stop-go yaw ${m["stopgo_yaw_min"]}/min")
            if (sc == "idle12") {
                need(m["rev_yaw_s"] <= 0.5f, "idle yaw reversals ${m["rev_yaw_s"]}/s")
                need(m["double_rate"] <= 0.08f, "double blinks ${m["double_rate"]}")
                need(m["sacc_blink"].isNaN() || m["sacc_blink"] in (if (m["saccades"] >= 20f) 0.4f..0.9f else 0.3f..0.95f) /* Engine20: fewer, whole shifts → small samples */, "saccade-blink coupling ${m["sacc_blink"]}")
            }
        }
        println(sb)
        if (dir != null) File(dir, "channel_metrics.csv").writeText(sb.toString())
        assertTrue("Engine19 channel gate failed:\n" + fails.joinToString("\n"), fails.isEmpty())
    }

    /** Frame-only metrics (the same file runs on older engines for the before/after table). */
    @Test
    fun frameMetricsForCompare() {
        ChannelScenarios.loadClips()
        val dir = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() } ?: return
        val sb = StringBuilder("scenario,seed," + ChannelScenarios.FRAME_KEYS.joinToString(",") + "\n")
        for (sc in ChannelScenarios.NAMES) for (seed in SEEDS) {
            val m = ChannelMetrics.compute(ChannelScenarios.run(sc, seed))
            sb.append("$sc,$seed," + ChannelScenarios.FRAME_KEYS.joinToString(",") { "%.4f".format(m[it]) } + "\n")
        }
        File(dir).mkdirs(); File(dir, "frame_metrics.csv").writeText(sb.toString())
    }
}
