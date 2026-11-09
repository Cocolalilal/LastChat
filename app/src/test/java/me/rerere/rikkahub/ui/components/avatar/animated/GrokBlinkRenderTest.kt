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
