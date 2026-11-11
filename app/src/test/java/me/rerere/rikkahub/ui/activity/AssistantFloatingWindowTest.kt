package me.rerere.rikkahub.ui.activity

import android.app.Application
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = Application::class)
class AssistantFloatingWindowTest {
    @Test fun `assistant window wraps content and allows touches outside without dimming`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var blurActive = false
        val chrome = AssistantFloatingWindow(activity) { blurActive = it }
        chrome.attach()
        val params = activity.window.attributes
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.height)
        assertEquals(Gravity.BOTTOM, params.gravity and Gravity.VERTICAL_GRAVITY_MASK)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH != 0)
        chrome.setBlurEnabled(true)
        if (Build.VERSION.SDK_INT >= 31) {
            assertEquals(activity.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled, blurActive)
        } else assertFalse(blurActive)
        chrome.setBlurEnabled(false)
        assertFalse(blurActive)
        chrome.close()
        controller.pause().stop().destroy()
    }
}
