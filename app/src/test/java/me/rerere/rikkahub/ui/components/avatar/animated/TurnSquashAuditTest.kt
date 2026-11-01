package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Turn audit: how much do the eyes change size when the head turns?
 *
 * A natural turn foreshortens each eye ALONG the turn direction (their width shrinks, their height
 * stays). A pair-wide uniform scale (the `EyeSafeRegion.shrink` guard) instead makes both eyes
 * smaller in every direction at a threshold angle, which reads as "the eyes just scale down".
 *
 * Writes turn_squash_<shape>.csv (yaw, eye, top, bottom, height, width) and the rendered strip into
 * `lastchat.renderDir` when set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TurnSquashAuditTest {
    private val yaws = listOf(0f, 6f, 12f, 18f, 24f)
    private val curvedShapes = setOf("blob", "cloud", "heart", "tablet")

    @Test
    fun measureEyeSizeAcrossYaw() {
        val dir = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.also { File(it).mkdirs() }
        AvatarPackData.ensureLoaded(org.robolectric.RuntimeEnvironment.getApplication())
        val px = 320
        for (mode in listOf("generical", "grok")) for (id in listOf("blob", "cloud", "square", "heart", "star", "clover", "flower", "hex", "sparkle", "tablet")) {
            val sh = MarkShapes.resolve(id) ?: continue
            val rows = StringBuilder("yaw,eye,top,bottom,height,width\n")
            val h0 = HashMap<String, Int>()
            val w0 = HashMap<String, Int>()
            var geomW0L = 0f
            var geomW0R = 0f
            var geomH0L = 0f
            var geomH0R = 0f
            for (yaw in yaws) {
                val img = ImageBitmap(px, px)
                val canvas = androidx.compose.ui.graphics.Canvas(img)
                canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(),
                    androidx.compose.ui.graphics.Paint().apply { color = Color(0xFF1B1D22) })
                val frame = MotionFrame(body = BodyPose(yaw = yaw), exprFrom = "Neutral", exprTo = "Neutral")
                val fade = MarkColors.genericalEyeFade(Color(0xFF009FE0), Color.White, 1f)
                val geom = MarkGeom()
                CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, canvas, Size(px.toFloat(), px.toFloat())) {
                    drawMark(sh, id, mode == "grok", frame, false, 0f, Color(0xFF009FE0), Color.White, fade)
                    drawMark(sh, id, mode == "grok", frame, false, 0f, Color(0xFF009FE0), Color.White, fade, sink = geom)
                }
                assertEquals("$mode/$id yaw=$yaw must seat 2 eyes", 2, geom.eyes.size)
                assertTrue(
                    "$mode/$id yaw=$yaw eyes must stay inside silhouette with GEN_CLIP_MARGIN",
                    EyeSafeRegion.inside(EyeSafeRegion.Body(geom.body), geom.eyes.map { it.path }, geom.he * GEN_CLIP_MARGIN),
                )
                val bL = geom.eyes[0].path.getBounds()
                val bR = geom.eyes[1].path.getBounds()
                if (yaw == 0f) {
                    geomW0L = bL.width; geomW0R = bR.width
                    geomH0L = bL.height; geomH0R = bR.height
                } else if (mode == "generical") {
                    assertTrue("$mode/$id yaw=$yaw path height must not shrink (L ${bL.height} vs $geomH0L, R ${bR.height} vs $geomH0R)",
                        bL.height >= geomH0L * 0.99f && bR.height >= geomH0R * 0.99f)
                    if (yaw == 24f && id !in curvedShapes) {
                        assertTrue("$mode/$id yaw=$yaw flat-seat width must foreshorten without vertical shrink",
                            bL.width < geomW0L && bR.width < geomW0R)
                    }
                }
                if (dir != null) {
                    File(dir, "turn_${mode}_${id}_yaw${yaw.toInt()}.png").outputStream()
                        .use { img.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
                val bmp = img.asAndroidBitmap()
                val w = bmp.width; val h = bmp.height
                val argb = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
                // Eye ink = white-ish pixels (the body is cyan, the background charcoal).
                val ink = ArrayList<IntArray>()
                for (y in 0 until h) for (x in 0 until w) {
                    val c = argb[y * w + x]
                    val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                    if (r > 200 && g > 200 && b > 200) ink += intArrayOf(x, y)
                }
                if (ink.isEmpty()) { rows.append("$yaw,none,,,,\n"); continue }
                val mid = (ink.minOf { it[0] } + ink.maxOf { it[0] }) / 2
                for ((name, sel) in listOf("L" to { p: IntArray -> p[0] < mid }, "R" to { p: IntArray -> p[0] >= mid })) {
                    val pts = ink.filter(sel)
                    if (pts.isEmpty()) { rows.append("$yaw,$name,,,,\n"); continue }
                    val top = pts.minOf { it[1] }; val bot = pts.maxOf { it[1] }
                    val left = pts.minOf { it[0] }; val right = pts.maxOf { it[0] }
                    val eh = bot - top + 1; val ew = right - left + 1
                    rows.append("$yaw,$name,$top,$bot,$eh,$ew\n")
                    if (yaw == 0f) {
                        h0[name] = eh; w0[name] = ew
                    } else {
                        val refH = h0[name] ?: eh
                        val refW = w0[name] ?: ew
                        assertTrue(
                            "$mode/$id yaw=$yaw eye=$name height $eh must be >= 97% of yaw-0 height $refH",
                            eh >= refH * 0.97f,
                        )
                        if (mode == "generical" && id in curvedShapes) {
                            if (name == "L") {
                                // Width is allowed to breathe a little with the projection (a lofted
                                // seat narrows both eyes slightly as it turns); what must never
                                // happen is a tall, pair-wide shrink — that is the height gate above.
                                assertTrue(
                                    "$mode/$id yaw=$yaw near eye (L) width $ew must not collapse vs yaw-0 $refW",
                                    ew >= refW * 0.92f && geom.eyes[0].path.getBounds().width >= geomW0L * 0.92f,
                                )
                            } else if (yaw == 24f) {
                                // The far eye must never GROW as the head turns; on a lofted seat
                                // (heart, tablet) it may end up barely narrower rather than
                                // clearly foreshortened, so this only forbids widening.
                                assertTrue(
                                    "$mode/$id yaw=$yaw far eye (R) width $ew must not widen vs yaw-0 $refW",
                                    ew <= refW && geom.eyes[1].path.getBounds().width <= geomW0R * 1.01f,
                                )
                            }
                        }
                    }
                }
            }
            if (dir != null) File(dir, "turn_squash_${mode}_$id.csv").writeText(rows.toString())
        }
    }

    /**
     * The circle case: a sphere body with a WIDE face (surprised) or an up/down gaze brings the eyes
     * near the limb, so the same guard can fire on a round shape too. Sweeps expression × yaw × pitch.
     */
    @Test
    fun measureSphereEyeSizeAcrossExpressionsAndPitch() {
        val dir = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.also { File(it).mkdirs() }
        AvatarPackData.ensureLoaded(org.robolectric.RuntimeEnvironment.getApplication())
        val px = 320
        val exprs = listOf("Neutral", "Surprised", "Happy", "Bored", "confused", "Sleeping")
        val yaws = listOf(0f, 12f, 24f)
        val pitches = listOf(0f, 10f, -10f)
        for (shapeId in listOf("blob", "cloud")) {
            val sh = MarkShapes.resolve(shapeId) ?: continue
            val rows = StringBuilder("expr,yaw,pitch,eye,top,bottom,height,width\n")
            for (e in exprs) {
                var geomH0L = 0f
                var geomH0R = 0f
                for (yaw in yaws) for (pitch in pitches) {
                    val img = ImageBitmap(px, px)
                    val canvas = androidx.compose.ui.graphics.Canvas(img)
                    canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(),
                        androidx.compose.ui.graphics.Paint().apply { color = Color(0xFF1B1D22) })
                    val frame = MotionFrame(body = BodyPose(yaw = yaw, pitch = pitch), exprFrom = e, exprTo = e)
                    val fade = MarkColors.genericalEyeFade(Color(0xFF009FE0), Color.White, 1f)
                    val geom = MarkGeom()
                    CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, canvas, Size(px.toFloat(), px.toFloat())) {
                        drawMark(sh, shapeId, false, frame, false, 0f, Color(0xFF009FE0), Color.White, fade)
                        drawMark(sh, shapeId, false, frame, false, 0f, Color(0xFF009FE0), Color.White, fade, sink = geom)
                    }
                    assertTrue(
                        "$shapeId/$e yaw=$yaw pitch=$pitch eyes must stay inside silhouette with GEN_CLIP_MARGIN",
                        EyeSafeRegion.inside(EyeSafeRegion.Body(geom.body), geom.eyes.map { it.path }, geom.he * GEN_CLIP_MARGIN),
                    )
                    if (pitch == 0f && geom.eyes.size == 2) {
                        val hL = geom.eyes[0].path.getBounds().height
                        val hR = geom.eyes[1].path.getBounds().height
                        if (yaw == 0f) {
                            geomH0L = hL; geomH0R = hR
                        } else {
                            assertTrue("$shapeId/$e yaw=$yaw L height $hL < 97% of $geomH0L", hL >= geomH0L * 0.97f)
                            assertTrue("$shapeId/$e yaw=$yaw R height $hR < 97% of $geomH0R", hR >= geomH0R * 0.97f)
                        }
                    }
                    val bmp = img.asAndroidBitmap()
                    val w = bmp.width; val h = bmp.height
                    val argb = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
                    val ink = ArrayList<IntArray>()
                    for (y in 0 until h) for (x in 0 until w) {
                        val c = argb[y * w + x]
                        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                        if (r > 200 && g > 200 && b > 200) ink += intArrayOf(x, y)
                    }
                    if (ink.isEmpty()) { rows.append("$e,$yaw,$pitch,none,,,,\n"); continue }
                    val mid = (ink.minOf { it[0] } + ink.maxOf { it[0] }) / 2
                    for ((name, sel) in listOf("L" to { p: IntArray -> p[0] < mid }, "R" to { p: IntArray -> p[0] >= mid })) {
                        val pts = ink.filter(sel)
                        if (pts.isEmpty()) { rows.append("$e,$yaw,$pitch,$name,,,,\n"); continue }
                        val top = pts.minOf { it[1] }; val bot = pts.maxOf { it[1] }
                        val left = pts.minOf { it[0] }; val right = pts.maxOf { it[0] }
                        rows.append("$e,$yaw,$pitch,$name,$top,$bot,${bot - top + 1},${right - left + 1}\n")
                    }
                }
            }
            if (dir != null) File(dir, "sphere_squash_$shapeId.csv").writeText(rows.toString())
        }
    }
}
