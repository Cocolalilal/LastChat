package me.rerere.rikkahub.ui.components.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BlobScreenshotTest {
    @Test
    fun writeLifecycleAndShapeSheets() {
        val cell = 192
        val labelH = 28
        val packs = listOf(BlobEyePack.Generical, BlobEyePack.Grok)
        val lives = BlobLifecycle.entries
        val outDir = qaDir()

        val sheet = Bitmap.createBitmap(
            cell * lives.size,
            (cell + labelH) * packs.size,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF1B1F24.toInt())
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
        }

        packs.forEachIndexed { row, pack ->
            lives.forEachIndexed { col, life ->
                val spec = if (pack == BlobEyePack.Grok) {
                    Avatar.Blob.grok().copy(shape = BlobShape.Circle)
                } else {
                    Avatar.Blob.generical().copy(shape = BlobShape.Circle)
                }
                val face = BlobBitmap.render(spec, cell, life, tSeconds = 2.6f)
                val x = col * cell
                val y = row * (cell + labelH)
                canvas.drawBitmap(face, x.toFloat(), y.toFloat(), null)
                canvas.drawText(
                    "${pack.name.lowercase()} ${life.name.lowercase()}",
                    x + 8f,
                    (y + cell + 20).toFloat(),
                    labelPaint,
                )
                FileOutputStream(File(outDir, "${pack.name.lowercase()}-${life.name.lowercase()}.png")).use {
                    face.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        FileOutputStream(File(outDir, "lifecycle-contact-sheet.png")).use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        val shapes = BlobShape.entries
        val shapeSheet = Bitmap.createBitmap(
            cell * shapes.size,
            (cell + labelH) * packs.size,
            Bitmap.Config.ARGB_8888,
        )
        val shapeCanvas = Canvas(shapeSheet)
        shapeCanvas.drawColor(0xFF1B1F24.toInt())
        packs.forEachIndexed { row, pack ->
            shapes.forEachIndexed { col, shape ->
                val spec = if (pack == BlobEyePack.Grok) {
                    Avatar.Blob.grok().copy(shape = shape)
                } else {
                    Avatar.Blob.generical().copy(shape = shape)
                }
                val face = BlobBitmap.render(spec, cell, BlobLifecycle.Idle, tSeconds = 1.4f)
                val x = col * cell
                val y = row * (cell + labelH)
                shapeCanvas.drawBitmap(face, x.toFloat(), y.toFloat(), null)
                shapeCanvas.drawText(
                    "${pack.name.take(3).lowercase()} ${shape.name.lowercase()}",
                    x + 6f,
                    (y + cell + 20).toFloat(),
                    labelPaint,
                )
                FileOutputStream(File(outDir, "shape-${pack.name.lowercase()}-${shape.name.lowercase()}.png")).use {
                    face.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        FileOutputStream(File(outDir, "shape-contact-sheet.png")).use {
            shapeSheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        val pebble = BlobBitmap.render(
            Avatar.Blob.grok().copy(shape = BlobShape.Pebble),
            256,
            BlobLifecycle.Idle,
            tSeconds = 1.1f,
        )
        FileOutputStream(File(outDir, "grok-pebble-idle.png")).use {
            pebble.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val cloud = BlobBitmap.render(
            Avatar.Blob.grok().copy(shape = BlobShape.Cloud),
            256,
            BlobLifecycle.Working,
            tSeconds = 2.2f,
        )
        FileOutputStream(File(outDir, "grok-cloud-working.png")).use {
            cloud.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun qaDir(): File {
        val candidates = listOf(
            File("/opt/cursor/artifacts/avatar-qa"),
            File("app/build/reports/blob-qa"),
            File("build/reports/blob-qa"),
        )
        for (dir in candidates) {
            if (dir.exists() || dir.mkdirs()) return dir
        }
        return File(System.getProperty("java.io.tmpdir"), "blob-qa").apply { mkdirs() }
    }
}
