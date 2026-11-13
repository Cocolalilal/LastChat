package me.rerere.rikkahub.phoneuse

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import me.rerere.rikkahub.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowWindowManagerImpl

/** The renderer is selected at first addView; later window flags cannot repair it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [OverlayAddViewShadow::class])
class PhoneOverlayHardwareTest {
    class OverlayService : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }

    @Test fun `service overlay requests hardware rendering at its first native attachment`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()
        val dialog = createFixedPhoneOverlayDialog(service, FrameLayout(service))
        try {
            dialog.show()
            val added = OverlayAddViewShadow.added.single {
                it.view === dialog.window?.decorView
            }
            assertTrue(added.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
            assertTrue(added.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertEquals(WindowManager.LayoutParams.MATCH_PARENT, added.width)
            assertEquals(WindowManager.LayoutParams.MATCH_PARENT, added.height)
            assertEquals(0, added.x)
            assertEquals(0, added.y)
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                added.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
        } finally {
            dialog.dismiss()
            controller.destroy()
        }
    }

    @Test fun `late hardware flag does not change the flags used for the service window attachment`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()
        val dialog = ComponentDialog(service, R.style.Theme_Rikkahub_PhoneOverlay).apply {
            window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            setContentView(FrameLayout(service))
        }
        try {
            dialog.show()
            val added = OverlayAddViewShadow.added.single {
                it.view === dialog.window?.decorView
            }
            assertFalse(added.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            assertTrue(requireNotNull(dialog.window).attributes.flags and
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
            // Capture by value: checking the mutable attributes after show missed this bug.
            assertFalse(added.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
        } finally {
            dialog.dismiss()
            controller.destroy()
        }
    }
}

@Implements(className = "android.view.WindowManagerImpl", isInAndroidSdk = false)
class OverlayAddViewShadow : ShadowWindowManagerImpl() {
    data class InitialWindow(
        val view: View,
        val flags: Int,
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int,
        val softInputMode: Int,
    )

    companion object {
        val added = mutableListOf<InitialWindow>()

        @JvmStatic
        @Resetter
        fun clearRecordedWindows() { added.clear() }
    }

    @Implementation
    override fun addView(view: View, params: ViewGroup.LayoutParams) {
        if (params is WindowManager.LayoutParams &&
            params.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            added += InitialWindow(view, params.flags, params.width, params.height,
                params.x, params.y, params.softInputMode)
        }
        super.addView(view, params)
    }
}
