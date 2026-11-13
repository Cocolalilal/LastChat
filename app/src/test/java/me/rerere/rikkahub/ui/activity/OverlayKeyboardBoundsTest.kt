package me.rerere.rikkahub.ui.activity

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = Application::class)
class OverlayKeyboardBoundsTest {
    @Test fun `only the overlays own focused input reserves keyboard space`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val content = View(activity)
        root.addView(content, FrameLayout.LayoutParams(120, 72))
        activity.setContentView(root)
        val viewport = FloatingOverlayViewport(activity.window, root, content, passThrough = false)
        viewport.attach()
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1000, 1800)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 600))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        try {
            ViewCompat.dispatchApplyWindowInsets(root, insets)
            assertEquals(48, root.paddingBottom)
            assertEquals(1752, viewport.availableFrame.bottom)
            viewport.setInputEnabled(true)
            assertEquals(600, root.paddingBottom)
            assertEquals(1200, viewport.availableFrame.bottom)
            // Disabling focus clears a stale IME inset immediately, even before another
            // target-app keyboard event is delivered to our non-focusable window.
            viewport.setInputEnabled(false)
            assertEquals(48, root.paddingBottom)
            assertEquals(1752, viewport.availableFrame.bottom)
        } finally {
            viewport.close()
            controller.pause().stop().destroy()
        }
    }
}
