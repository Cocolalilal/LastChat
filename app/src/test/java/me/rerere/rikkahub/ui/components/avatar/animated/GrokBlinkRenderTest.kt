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
        fun render(shapeId: String, expression: String): Bitmap {
            val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
            val shape = requireNotNull(MarkShapes.resolve(shapeId))
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(240f, 240f)) {
                drawMark(shape, shapeId, true, MotionFrame(
                    exprFrom = expression, exprTo = expression, exprWeights = listOf(expression to 1f),
                    lid = 0f, maskBlink = 1f,
                ), false, 1f, Color(0xFF2A92FE), Color.White, Color.White)
            }
            return bitmap
        }
        for (shape in MarkShapes.PICKER_IDS) {
            val a = render(shape, "Happy"); val b = render(shape, "Neutral")
            assertTrue("$shape: hidden expression swap changed the visible closed shape", a.sameAs(b))
            a.recycle(); b.recycle()
        }
    }
    @Test fun aQuietFocusHasNoClockDrivenRenderedAdjustments() {
        AvatarPackData.ensureLoaded(RuntimeEnvironment.getApplication())
        fun render(shapeId: String, time: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
            val shape = requireNotNull(MarkShapes.resolve(shapeId))
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(240f, 240f)) {
                drawMark(shape, shapeId, true, MotionFrame(
                    body = BodyPose(yaw = 8f, pitch = 2f, roll = 3f),
                    headLookX = 0.2f, leadX = 0.01f, timeSec = time, incidentalMotion = 0f,
                ), false, 1f, Color(0xFF2A92FE), Color.White, Color.White)
            }
            return bitmap
        }
        for (shape in listOf("blob", "heart", "cloud", "square")) {
            val first = render(shape, 0f)
            for (t in listOf(0.5f, 2f, 10f)) {
                val next = render(shape, t)
                assertTrue("$shape moves despite an unchanged attention target", first.sameAs(next))
                next.recycle()
            }
            first.recycle()
        }
    }

}
