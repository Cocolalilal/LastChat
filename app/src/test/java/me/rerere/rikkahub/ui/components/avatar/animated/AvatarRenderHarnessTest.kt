package me.rerere.rikkahub.ui.components.avatar.animated

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Visual render harness — NOT a pass/fail screenshot diff. Renders the real
 * AnimatedMarkAvatar (calm editor preview + live chat path at t=0) for every
 * picker shape × {Generical, Grok}, plus the editor sheet body, to PNGs.
 *
 * Run: ./gradlew :app:testStableReleaseUnitTest \
 *        --tests '*AvatarRenderHarnessTest*' -Dlastchat.renderDir=/path
 * Skipped when lastchat.renderDir is unset (keeps CI unit runs fast).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AvatarRenderHarnessTest {

    private val outDir: File? = System.getProperty("lastchat.renderDir")
        ?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

    private fun capture(name: String, widthDp: Int, heightDp: Int, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        activity.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) { content() }
            }
        }
        // Calm / debugPose renders have no infinite loops → safe to advance the
        // clock so the 450 ms shape-morph settles at full size.
        repeat(6) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200)) }
        val density = activity.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val view: View = root.getChildAt(0)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, w, h)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        controller.pause().stop().destroy()
    }

    private fun pose(pitch: Float, yaw: Float, roll: Float = 0f) = ClipSample(
        clipName = "Harness",
        time = 0f,
        duration = 1f,
        squashAnchor = 0f,
        rotationDeg = floatArrayOf(pitch, yaw, roll),
        position = floatArrayOf(0f, 0f),
        squash = floatArrayOf(1f, 1f),
        scale = MarkClipEngine.DEFAULT_ZOOM,
        lids = 1f,
        expression = "neutral",
    )

    /** calm = editor preview; rest = live chat path at clip t=0; rest = look-around. */
    private val poses: List<Pair<String, ClipSample?>> by lazy {
        listOf(
            "calm" to null,
            "rest" to pose(0f, 0f),
            "yawL" to pose(0f, -22f),
            "yawR" to pose(0f, 22f),
            "up" to pose(-16f, 0f),
            "down" to pose(16f, 0f),
            "roll" to pose(0f, 0f, 15f),
            "combo" to pose(10f, -26f, 8f),
            "yawFar" to pose(4f, 40f, -4f),
        )
    }

    @Test
    fun renderAllShapesBothModes() {
        assumeTrue(outDir != null)
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        AvatarPackData.ensureLoaded(ctx)
        for (mode in listOf("generical", "grok")) {
            for (shape in MarkShapes.SHAPES) {
                for ((tag, p) in poses) {
                    // 200dp canvas around a 160dp avatar: rolled / turned bodies overhang
                    // their box a little (the app doesn't clip them either).
                    capture("mark_${mode}_${shape}_$tag", 200, 200) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF14171C)), contentAlignment = Alignment.Center) {
                            AnimatedMarkAvatar(
                                shapeId = shape,
                                eyeType = mode,
                                colorHex = "#2A92FE",
                                colorPreset = "blue",
                                eyeColorHex = null,
                                isLoading = false,
                                calmPreview = p == null,
                                modifier = Modifier.size(160.dp),
                                debugPose = p,
                            )
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ strips

    @Composable
    private fun Strip(shape: String, mode: String, frames: List<MotionFrame>, cellDp: Int) {
        Row(Modifier.background(Color(0xFF14171C))) {
            for (f in frames) {
                Box(Modifier.size(cellDp.dp), contentAlignment = Alignment.Center) {
                    AnimatedMarkAvatar(
                        shapeId = shape,
                        eyeType = mode,
                        colorHex = "#2A92FE",
                        colorPreset = "blue",
                        isLoading = false,
                        modifier = Modifier.size((cellDp * 0.8f).dp),
                        debugFrame = f,
                    )
                }
            }
        }
    }

    private fun stripCapture(name: String, shape: String, mode: String, frames: List<MotionFrame>, cellDp: Int = 110) =
        capture(name, cellDp * frames.size, cellDp) { Strip(shape, mode, frames, cellDp) }

    /** Expression morphs: 7 frames across each transition, face-on and turned. */
    @Test
    fun renderExpressionMorphStrips() {
        assumeTrue(outDir != null)
        AvatarPackData.ensureLoaded(org.robolectric.RuntimeEnvironment.getApplication())
        val transitions = listOf(
            "Neutral" to "Happy",
            "Neutral" to "Surprised",
            "Happy" to "Sad",
            "Neutral" to "skeptical",
            "Neutral" to "angered or concentrated",
            "Surprised" to "Sleeping",
        )
        val ts = listOf(0f, 0.17f, 0.33f, 0.5f, 0.67f, 0.83f, 1f)
        for (mode in listOf("generical", "grok")) {
            for (shape in listOf("blob", "square", "heart", "cloud", "tablet")) {
                for ((a, b) in transitions) {
                    for (rot in listOf(false, true)) {
                        val frames = ts.map { t ->
                            MotionFrame(
                                body = if (rot) BodyPose(pitch = 8f, yaw = -22f, roll = 5f) else BodyPose.REST,
                                exprFrom = a, exprTo = b, exprT = MotionDirector.easeInOutCubic(t),
                            )
                        }
                        val tag = (if (rot) "_rot" else "")
                        stripCapture("morph_${mode}_${shape}_${a.replace(' ', '-')}__${b.replace(' ', '-')}$tag", shape, mode, frames)
                    }
                }
                // Blink: lids 1 → 0 → 1.
                val lids = listOf(1f, 0.75f, 0.5f, 0.25f, 0.05f, 0.4f, 1f)
                stripCapture("blink_${mode}_$shape", shape, mode, lids.map { MotionFrame(lid = it) })
            }
        }
    }

    /**
     * Live motion through lifecycle changes (idle → thinking → streaming → idle → typing),
     * sampled from the real [AvatarMotionState] with a fixed seed: every frame is the
     * actual pose, gaze, lids and expression morph the app would draw.
     */
    @Test
    fun renderClipTransitionStrips() {
        assumeTrue(outDir != null)
        AvatarPackData.ensureLoaded(org.robolectric.RuntimeEnvironment.getApplication())
        for ((mode, shape) in listOf("grok" to "blob", "generical" to "square", "generical" to "heart", "grok" to "wedge")) {
            val st = AvatarMotionState(Random(7))
            val dt = 1f / 60f
            var t = 0f
            val frames = ArrayList<Pair<Float, MotionFrame>>()
            var nextCap = 0f
            while (t < 24f) {
                val hints = when {
                    t < 5f -> AvatarMotionHints()
                    t < 7f -> AvatarMotionHints(isGenerating = true)
                    t < 11f -> AvatarMotionHints(isGenerating = true, hasStreamedTokens = true)
                    t < 18f -> AvatarMotionHints()
                    else -> AvatarMotionHints(isTyping = true, inputFocused = true)
                }
                st.tick(dt, hints, (t * 1000).toLong())
                if (t >= nextCap) {
                    frames += t to st.frame
                    nextCap += 0.2f
                }
                t += dt
            }
            // 12 frames per row (2.4 s of motion per row).
            frames.chunked(12).forEachIndexed { i, row ->
                stripCapture("trans_${mode}_${shape}_%02d".format(i), shape, mode, row.map { it.second }, cellDp = 96)
            }
            File(outDir, "trans_${mode}_${shape}.txt").writeText(frames.joinToString("\n") { (tt, f) ->
                "%.2f %s %s->%s %.2f lid=%.2f look=(%.2f,%.2f) p=%.1f y=%.1f r=%.1f s=%.3f".format(
                    tt, f.lifecycle, f.exprFrom, f.exprTo, f.exprT, f.lid, f.headLookX, f.headLookY,
                    f.body.pitch, f.body.yaw, f.body.roll, f.body.scale)
            })
        }
    }

    /**
     * Two avatars on a shared stage: run the real alive layer until one of them starts
     * a glance at the other, then capture both side by side through the moment.
     */
    @Test
    fun renderSocialGlance() {
        assumeTrue(outDir != null)
        AvatarPackData.ensureLoaded(org.robolectric.RuntimeEnvironment.getApplication())
        for ((pairTag, modes) in listOf("mixed" to ("grok" to "generical"), "twins" to ("generical" to "generical"))) {
            val stage = AvatarStage(Random(3))
            val a = AvatarMotionState(Random(21))
            val b = AvatarMotionState(Random(22))
            val idA = stage.newId(); val idB = stage.newId()
            a.stage = stage; a.stageId = idA
            b.stage = stage; b.stageId = idB
            stage.update(idA, 1, androidx.compose.ui.geometry.Offset(300f, 900f), 60f, "blob|${modes.first}", 1080f, 2400f)
            stage.update(idB, 1, androidx.compose.ui.geometry.Offset(780f, 900f), 60f, "blob|${modes.second}", 1080f, 2400f)
            val dt = 1f / 60f
            var t = 0f
            var ms = System.currentTimeMillis()
            val frames = ArrayList<Pair<MotionFrame, MotionFrame>>()
            var started = -1f
            var skepticSeen = false
            var cooldownUntil = 0f
            var glances = 0
            var nextNudge = 50f
            while (t < 1500f) {
                // Someone is using the app now and then (keeps them from dozing off).
                if (t >= nextNudge && started < 0f) {
                    stage.emit(AvatarStage.EventKind.Scroll, direction = 1, nowMs = ms)
                    nextNudge += 50f
                }
                a.tick(dt, AvatarMotionHints(), ms)
                b.tick(dt, AvatarMotionHints(), ms)
                val looking = a.frame.headLookX > 0.4f && b.frame.headLookX < -0.4f
                if (started < 0f && looking && t > cooldownUntil) {
                    started = t - 0.5f
                    frames.clear()
                    skepticSeen = false
                    glances++
                }
                if (started >= 0f) {
                    if (frames.isEmpty() || (t - started) >= frames.size * 0.4f) frames += a.frame to b.frame
                    if (a.frame.exprTo == "skeptical" || b.frame.exprTo == "skeptical") skepticSeen = true
                    if (t - started > 3.4f) {
                        if (skepticSeen) break
                        started = -1f
                        cooldownUntil = t + 5f
                    }
                }
                t += dt
                ms += 16
            }
            File(outDir, "social_$pairTag.txt").writeText("glances=$glances lastStart=$started skeptic=$skepticSeen frames=${frames.size} simulated=${t}s\n" +
                frames.joinToString("\n") { (fa, fb) ->
                    "A look=%.2f yaw=%.1f lead=%.3f %s->%s %.2f | B look=%.2f yaw=%.1f lead=%.3f %s->%s %.2f".format(
                        fa.headLookX, fa.body.yaw, fa.leadX, fa.exprFrom, fa.exprTo, fa.exprT,
                        fb.headLookX, fb.body.yaw, fb.leadX, fb.exprFrom, fb.exprTo, fb.exprT)
                })
            if (frames.isEmpty()) continue
            val cell = 96
            capture("social_$pairTag", cell * 2 * minOf(frames.size, 9) + 16 * minOf(frames.size, 9), cell) {
                Row(Modifier.background(Color(0xFF14171C))) {
                    for ((fa, fb) in frames.take(9)) {
                        Row(Modifier.size((cell * 2 + 16).dp, cell.dp)) {
                            Box(Modifier.size(cell.dp), contentAlignment = Alignment.Center) {
                                AnimatedMarkAvatar("blob", modes.first, "#2A92FE", false, colorPreset = "blue",
                                    modifier = Modifier.size((cell * 0.8f).dp), debugFrame = fa)
                            }
                            Box(Modifier.size(cell.dp), contentAlignment = Alignment.Center) {
                                AnimatedMarkAvatar("blob", modes.second, "#7C5CFF", false, colorPreset = null,
                                    modifier = Modifier.size((cell * 0.8f).dp), debugFrame = fb)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun renderEditorSheet() {
        assumeTrue(outDir != null)
        AvatarPackData.ensureLoaded(org.robolectric.RuntimeEnvironment.getApplication())
        // 411x820 = roomy phone; 360x600 = narrow/short sheet that MUST scroll
        // (5 tiles per row → 3 shape rows), scrolled to the end to prove the last
        // row clears the pinned Cancel/Save bar.
        val cases = listOf(
            Triple("blob", false, 411 to 820),
            Triple("square", true, 411 to 820),
            Triple("blob", false, 360 to 600),
            Triple("square", true, 360 to 600),
        )
        for (mode in listOf("generical", "grok")) {
            for ((shape, scrolled, dims) in cases) {
                val tag = (if (scrolled) "bottom" else "top") + "_${dims.first}x${dims.second}"
                capture("editor_${mode}_${shape}_$tag", dims.first, dims.second) {
                    // Int.MAX_VALUE is clamped to maxValue on first layout → bottom.
                    val scroll = rememberScrollState(if (scrolled) Int.MAX_VALUE else 0)
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        AnimatedAvatarEditorContent(
                            shape = shape,
                            onShape = {},
                            eyeType = mode,
                            onEyeType = {},
                            colorHex = "#2A92FE",
                            colorPreset = "blue",
                            onColor = { _, _ -> },
                            eyeColorHex = null,
                            onEyeColor = {},
                            onPickColor = {},
                            onPickEyeColor = {},
                            onDismiss = {},
                            onSave = {},
                            modifier = Modifier.fillMaxSize(),
                            scrollState = scroll,
                        )
                    }
                }
            }
        }
        @Suppress("UNUSED_VARIABLE") val unused = runBlocking { }
    }
}
