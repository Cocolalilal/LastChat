package me.rerere.rikkahub.ui.activity

import android.app.Application
import android.os.Build
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
    @Test fun `assistant uses a fixed transparent host and compositor blur without dimming`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var blurActive = false
        val chrome = AssistantFloatingWindow(activity) { blurActive = it }
        chrome.attach()
        val foreground = me.rerere.rikkahub.phoneuse.createPhoneOverlayDialog(activity, android.view.View(activity))
        chrome.setContentWindow(foreground.window!!)
        val params = activity.window.attributes
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(0, params.windowAnimations)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        chrome.setBlurEnabled(true)
        if (Build.VERSION.SDK_INT >= 31) {
            assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            assertEquals(0, foreground.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            assertEquals(0, foreground.window!!.attributes.blurBehindRadius)
            assertNotNull(foreground.window!!.decorView.background)
            assertEquals(activity.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled, blurActive)
        } else assertFalse(blurActive)
        chrome.setBlurEnabled(false)
        assertFalse(blurActive)
        assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        chrome.close()
        controller.pause().stop().destroy()
    }
    @Test fun `repeated state updates do not relayout or reannounce the window`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var notifications = 0
        val chrome = AssistantFloatingWindow(activity) { notifications++ }
        chrome.attach()
        val foreground = me.rerere.rikkahub.phoneuse.createPhoneOverlayDialog(activity, android.view.View(activity))
        chrome.setContentWindow(foreground.window!!)
        chrome.setBlurEnabled(true)
        val count = notifications
        val current = foreground.window!!.callback
        var relayouts = 0
        foreground.window!!.callback = object : android.view.Window.Callback by current {
            override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams) {
                relayouts++
                current.onWindowAttributesChanged(attrs)
            }
        }
        repeat(120) { chrome.setBlurEnabled(true) }
        assertEquals(0, relayouts)
        assertEquals(count, notifications)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, activity.window.attributes.height)
        chrome.close()
        controller.pause().stop().destroy()
    }

}
