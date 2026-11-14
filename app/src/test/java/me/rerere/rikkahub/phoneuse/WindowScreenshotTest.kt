package me.rerere.rikkahub.phoneuse

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class WindowScreenshotTest {
    @Test fun windowCaptureKeepsScreenCoordinatesAndDoesNotStretchIntoSystemBars() {
        val window = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val screen = alignWindowScreenshot(window, Rect(10, 20, 110, 170), 120, 200)
        assertEquals(120, screen.width)
        assertEquals(200, screen.height)
        assertEquals(Color.BLACK, screen.getPixel(50, 19))
        assertEquals(Color.RED, screen.getPixel(10, 20))
        assertEquals(Color.RED, screen.getPixel(109, 169))
        assertEquals(Color.BLACK, screen.getPixel(110, 170))
        assertEquals(Color.BLACK, screen.getPixel(50, 190))
        assertFalse(window.isRecycled)
        screen.recycle()
        window.recycle()
    }
}
