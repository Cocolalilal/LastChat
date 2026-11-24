package me.rerere.rikkahub.ui.components.avatar.animated

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GrokBlinkRenderTest {
    @Test fun renderBlinkPhasesWithRoundedCaps() {
        AvatarPackData.ensureLoaded(RuntimeEnvironment.getApplication())
        val bitmap = Bitmap.createBitmap(1_200, 720, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(28, 28, 28))
        val expressions = listOf("Neutral", "Angry", "Happy")
        for ((row, expression) in expressions.withIndex()) for (column in 0..4) {
            canvas.save()
            canvas.translate(column * 240f, row * 240f)
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(canvas), Size(240f, 240f)) {
                drawMark(requireNotNull(MarkShapes.resolve(MarkShapes.PICKER_IDS.first())), MarkShapes.PICKER_IDS.first(), true, MotionFrame(
                    exprFrom = expression, exprTo = expression, exprWeights = listOf(expression to 1f),
                    lid = 1f - column * 0.245f, maskBlink = 0f,
                ), false, 1f, Color(0xFF2A92FE), Color.White, Color.White)
            }
            canvas.restore()
        }
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { directory ->
            java.io.File(directory, "grok-blink-rounding.png").apply { parentFile?.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }

    @Test fun fullyClosedMaskHasTheSamePixelsOnHappyAndNeutral() {
        AvatarPackData.ensureLoaded(RuntimeEnvironment.getApplication())
        fun render(shapeId: String, expression: String, grok: Boolean, closure: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
            val shape = requireNotNull(MarkShapes.resolve(shapeId))
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(240f, 240f)) {
                drawMark(shape, shapeId, grok, MotionFrame(
                    exprFrom = expression, exprTo = expression, exprWeights = listOf(expression to 1f),
                    lid = 1f - closure, maskBlink = closure,
                ), false, 1f, Color(0xFF2A92FE), Color.White, Color.White)
            }
            return bitmap
        }
        for (grok in listOf(false, true)) for (shape in MarkShapes.PICKER_IDS) for (closure in listOf(0.98f, 1f)) {
            val a = render(shape, "Happy", grok, closure); val b = render(shape, "Neutral", grok, closure)
            assertTrue("$shape grok=$grok closure=$closure: hidden expression swap changed the visible closed shape", a.sameAs(b))
            a.recycle(); b.recycle()
        }
    }
}
