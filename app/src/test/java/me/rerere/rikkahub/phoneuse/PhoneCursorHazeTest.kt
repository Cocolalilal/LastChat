package me.rerere.rikkahub.phoneuse

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.material3.darkColorScheme
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.theme.CUSTOM_MATERIAL_YOU_COLOR_INDEX
import me.rerere.rikkahub.ui.theme.cachedAssistantColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PhoneCursorHazeTest {
    private val palette = PhoneCursorHazePalette(0xff399cce.toInt(), 0xffa260e6.toInt(), 0xff4bb7b1.toInt())

    private fun render(opacity: Float = 1f, press: Float = 0f, release: Float = 0f, flowX: Float = 0f): Bitmap {
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val renderer = PhoneCursorHazeRenderer(1f).apply { palette = this@PhoneCursorHazeTest.palette }
        renderer.draw(Canvas(bitmap), 80f, 80f, opacity, press, release, 0.65f, flowX)
        return bitmap
    }

    @Test fun `haze is feathered and centered on the exact injected touch rather than an opaque marker`() {
        val bitmap = render(press = 1f)
        try {
            val center = Color.alpha(bitmap.getPixel(80, 80))
            val inner = Color.alpha(bitmap.getPixel(87, 80))
            val edge = Color.alpha(bitmap.getPixel(97, 80))
            assertTrue("The target must remain legible through the haze", center in 150..254)
            assertTrue("Light must soften away from the touch point", center > inner && inner > edge)
            assertEquals("The field must stay compact", 0, Color.alpha(bitmap.getPixel(115, 80)))
        } finally { bitmap.recycle() }
    }

    @Test fun `swipe vapor follows movement and disappears completely with the cursor`() {
        val moving = render(press = 1f, flowX = 20f)
        val still = render(press = 1f)
        val hidden = render(opacity = 0f, release = 1f, flowX = 20f)
        try {
            assertTrue("A swipe should carry soft light behind its real touch point",
                Color.alpha(moving.getPixel(55, 80)) > Color.alpha(still.getPixel(55, 80)))
            assertTrue(Color.alpha(moving.getPixel(80, 80)) > Color.alpha(moving.getPixel(55, 80)))
            val pixels = IntArray(160 * 160)
            hidden.getPixels(pixels, 0, 160, 0, 0, 160, 160)
            assertTrue("Capture/hide cannot leave behind a light trail", pixels.all { Color.alpha(it) == 0 })
        } finally { moving.recycle(); still.recycle(); hidden.recycle() }
    }

    @Test fun `gesture feedback follows physical time for taps swipes and long presses`() {
        assertEquals(0f, phoneCursorStrokeProgress(-1, 250), 0f)
        assertEquals(0.5f, phoneCursorStrokeProgress(25, 50), 0f)
        assertEquals(0.5f, phoneCursorStrokeProgress(250, 500), 0f)
        assertEquals(0.5f, phoneCursorStrokeProgress(600, 1200), 0f)
        assertEquals(1f, phoneCursorStrokeProgress(1800, 1200), 0f)
        assertTrue(phoneCursorStrokeProgress(0, 0).isFinite())
    }

    @Test fun `native feedback reuses assistant customization and falls back without extracting images`() {
        val base = darkColorScheme()
        val assistant = Assistant(useAssistantMaterialYouColors = true,
            materialYouColorIndex = CUSTOM_MATERIAL_YOU_COLOR_INDEX, customMaterialYouColor = "#e94f6b")
        val custom = cachedAssistantColorScheme(assistant, base, true)
        assertNotEquals(base.primary, custom.primary)
        assertNotEquals(base.tertiary, custom.tertiary)
        assertSame(base, cachedAssistantColorScheme(assistant.copy(useAssistantMaterialYouColors = false), base, true))
        assertSame(base, cachedAssistantColorScheme(assistant.copy(materialYouColorIndex = 0,
            avatar = Avatar.Image("https://invalid.example/not-loaded.png")), base, true))
        assertSame(base, cachedAssistantColorScheme(null, base, true))
    }

    @Test fun renderCursorPhases() {
        val directory = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() } ?: return
        val output = File(directory, "cursor").apply { mkdirs() }
        data class Pose(val name: String, val opacity: Float, val press: Float, val release: Float,
            val flowX: Float = 0f, val flowY: Float = 0f)
        val poses = listOf(
            Pose("approach", 0.65f, 0f, 0f, 18f, -7f),
            Pose("tap", 1f, 1f, 0f),
            Pose("swipe", 1f, 1f, 0.15f, -22f, 12f),
            Pose("long-press", 1f, 1f, 0.3f),
            Pose("release", 0.65f, 0.15f, 0.7f),
        )
        for (pose in poses) {
            val bitmap = Bitmap.createBitmap(420, 280, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(0xff101419.toInt())
            paint.color = 0xff242c35.toInt()
            canvas.drawRoundRect(24f, 40f, 396f, 240f, 28f, 28f, paint)
            paint.color = 0xff54616f.toInt()
            canvas.drawRoundRect(64f, 80f, 356f, 92f, 6f, 6f, paint)
            canvas.drawRoundRect(64f, 111f, 280f, 123f, 6f, 6f, paint)
            paint.color = 0xff214f69.toInt()
            canvas.drawRoundRect(154f, 156f, 266f, 212f, 28f, 28f, paint)
            val renderer = PhoneCursorHazeRenderer(2f).apply { palette = this@PhoneCursorHazeTest.palette }
            renderer.draw(canvas, 210f, 184f, pose.opacity, pose.press, pose.release, 0.65f,
                pose.flowX, pose.flowY)
            File(output, "cursor-${pose.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
