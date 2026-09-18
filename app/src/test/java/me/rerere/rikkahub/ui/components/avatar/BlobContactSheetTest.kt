package me.rerere.rikkahub.ui.components.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders deterministic PNG contact sheets using the app's REAL renderer
 * ([AvatarDraw] on `android.graphics`) under Robolectric native (Skia) graphics —
 * so the sheets are pixel-faithful to the on-device avatar, no emulator needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class BlobContactSheetTest {

    private val outDir: File = run {
        val artifacts = File("/opt/cursor/artifacts")
        val target = when {
            artifacts.isDirectory && artifacts.canWrite() -> File(artifacts, "create-avatar-v3")
            else -> File("build/blob-contact-sheets")
        }
        target.apply { mkdirs() }
    }

    private val darkBg = AColor.rgb(0x14, 0x18, 0x1C)
    private val lightBg = AColor.rgb(0xF3, 0xF5, 0xF7)

    @Test
    fun lifecycleContactSheet() {
        val lives = BlobLifecycle.entries
        val cell = 128
        val pad = 16
        val labelH = 26
        val headerH = 34
        val w = pad + lives.size * (cell + pad)
        val h = headerH + 2 * (labelH + cell + pad) + pad
        val (img, c) = canvas(w, h, darkBg)

        title(c, "Create Avatar v3 - lifecycle (Grok slits / Generical sheet)")
        val packs = listOf("Grok" to Avatar.Blob.grok(), "Generical" to Avatar.Blob.generical())
        var y = headerH
        for ((name, spec) in packs) {
            label(c, name, pad.toFloat(), y + 18f, 14f, AColor.rgb(0xB8, 0xC2, 0xCC))
            var x = pad
            for (life in lives) {
                val frame = BlobRuntime(life).sample(0f, spec, life, motion = 0f)
                cell(c, frame, x, y + labelH, cell)
                label(c, life.name, x + 6f, y + labelH + cell + 16f, 12f, AColor.rgb(0x8A, 0x93, 0x9C))
                x += cell + pad
            }
            y += labelH + cell + pad
        }
        finish(img, c, "avatar-lifecycle.png")
    }

    @Test
    fun backgroundPortabilitySheet() {
        // The key fix: light eyes read on ANY background (not holes -> dark).
        val cell = 132
        val pad = 18
        val headerH = 34
        val specs = listOf(
            Avatar.Blob.grok(),
            Avatar.Blob.generical(),
            Avatar.Blob.grok().copy(color = "#F1EFE9"),
            Avatar.Blob.generical().copy(color = "#111827"),
        )
        val w = pad + specs.size * (cell + pad)
        val h = headerH + 2 * (cell + pad) + pad
        val (img, c) = canvas(w, h, AColor.rgb(0x20, 0x24, 0x28))
        title(c, "Eyes read on any background")
        var y = headerH
        for (bg in listOf(darkBg, lightBg)) {
            var x = pad
            for (spec in specs) {
                tile(c, x, y, cell, bg)
                val frame = BlobRuntime(BlobLifecycle.Idle).sample(0f, spec, BlobLifecycle.Idle, motion = 0f)
                cell(c, frame, x, y, cell)
                x += cell + pad
            }
            y += cell + pad
        }
        finish(img, c, "avatar-backgrounds.png")
    }

    @Test
    fun shapesSheet() {
        val shapes = BlobShape.entries
        val cell = 108
        val pad = 14
        val labelH = 24
        val headerH = 34
        val w = pad + shapes.size * (cell + pad)
        val h = headerH + 2 * (labelH + cell + pad)
        val (img, c) = canvas(w, h, darkBg)
        title(c, "Body shapes (shared engine)")
        var y = headerH
        for (base in listOf(Avatar.Blob.grok(), Avatar.Blob.generical())) {
            var x = pad
            for (shape in shapes) {
                val spec = base.copy(shape = shape)
                val frame = BlobRuntime(BlobLifecycle.Idle).sample(0f, spec, BlobLifecycle.Idle, motion = 0f)
                cell(c, frame, x, y, cell)
                label(c, shape.name, x + 4f, y + cell + 14f, 11f, AColor.rgb(0x8A, 0x93, 0x9C))
                x += cell + pad
            }
            y += labelH + cell + pad
        }
        finish(img, c, "avatar-shapes.png")
    }

    @Test
    fun genericalJulianExpressionSheet() {
        val poses = GenericalPose.entries
        val cell = 168
        val pad = 18
        val labelH = 22
        val headerH = 36
        val cols = 5
        val rows = (poses.size + cols - 1) / cols
        val w = pad + cols * (cell + pad)
        val h = headerH + rows * (labelH + cell + pad) + pad
        val (img, c) = canvas(w, h, darkBg)
        title(c, "Generical — Julian expression poses (white border + gradient)")
        val spec = Avatar.Blob.generical()
        var i = 0
        for (row in 0 until rows) {
            var x = pad
            val y = headerH + row * (labelH + cell + pad)
            for (col in 0 until cols) {
                if (i >= poses.size) break
                val pose = poses[i]
                val frame = frozenFaceFrame(spec, genericalPose(pose))
                cell(c, frame, x, y, cell)
                label(c, pose.name, x + 6f, y + cell + 16f, 12f, AColor.rgb(0xB8, 0xC2, 0xCC))
                x += cell + pad
                i++
            }
        }
        finish(img, c, "avatar-generical-expressions.png")
    }

    @Test
    fun grokMarkSheet() {
        val cell = 160
        val pad = 16
        val labelH = 22
        val headerH = 36
        val lives = BlobLifecycle.entries
        val w = pad + lives.size * (cell + pad)
        val h = headerH + labelH + cell + pad
        val (img, c) = canvas(w, h, darkBg)
        title(c, "Grok — black slits on the coloured mark")
        var x = pad
        val y = headerH
        val spec = Avatar.Blob.grok()
        for (life in lives) {
            val frame = BlobRuntime(life).sample(0f, spec, life, motion = 0f)
            cell(c, frame, x, y, cell)
            label(c, life.name, x + 6f, y + cell + 16f, 12f, AColor.rgb(0xB8, 0xC2, 0xCC))
            x += cell + pad
        }
        finish(img, c, "avatar-grok-marks.png")
    }

    // ---- helpers -------------------------------------------------------------

    private fun canvas(w: Int, h: Int, bg: Int): Pair<Bitmap, Canvas> {
        val img = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(img)
        c.drawColor(bg)
        return img to c
    }

    private fun cell(c: Canvas, frame: BlobFrame, x: Int, y: Int, size: Int) {
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        AvatarDraw.draw(c, frame, size.toFloat(), size.toFloat())
        c.restore()
    }

    private fun tile(c: Canvas, x: Int, y: Int, size: Int, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawRoundRect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat(), 28f, 28f, p)
    }

    private fun title(c: Canvas, text: String) =
        label(c, text, 16f, 24f, 18f, AColor.WHITE, bold = true)

    private fun label(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            isFakeBoldText = bold
        }
        c.drawText(text, x, y, p)
    }

    private fun finish(img: Bitmap, c: Canvas, name: String) {
        val file = File(outDir, name)
        FileOutputStream(file).use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("wrote $file", file.exists() && file.length() > 1000L)
        assertTrue("image has ink", distinctColors(img) > 3)
        println("contact-sheet: ${file.absolutePath} (${file.length()} bytes)")
    }

    private fun distinctColors(img: Bitmap): Int {
        val seen = HashSet<Int>()
        var x = 0
        while (x < img.width) {
            var y = 0
            while (y < img.height) {
                seen.add(img.getPixel(x, y))
                if (seen.size > 6) return seen.size
                y += 9
            }
            x += 9
        }
        return seen.size
    }

    @Suppress("unused")
    private fun packName(pack: BlobEyePack) = pack.name
}
