package me.rerere.rikkahub.ui.activity

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
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
    @Test fun `keyboard follows animation progress without jumping to endpoint before first frame`() {
        withViewport { viewport, root ->
            viewport.setInputEnabled(true)
            val hidden = keyboardInsets(0)
            val shown = keyboardInsets(600)
            ViewCompat.dispatchApplyWindowInsets(root, hidden)
            val animation = WindowInsetsAnimationCompat(WindowInsetsCompat.Type.ime(), null, 300)
            val callback = viewport.insetsAnimationCallback
            callback.onPrepare(animation)
            ViewCompat.dispatchApplyWindowInsets(root, shown)
            assertEquals("endpoint layout must not move the composer early", 48, root.paddingBottom)
            listOf(60, 180, 320, 480, 600).forEach { bottom ->
                callback.onProgress(keyboardInsets(bottom), mutableListOf(animation))
                assertEquals(bottom, root.paddingBottom)
            }
            callback.onEnd(animation)
            assertEquals(600, root.paddingBottom)
            assertEquals(1200, viewport.availableFrame.bottom)
        }
    }

    @Test fun `reversing an unfinished keyboard animation does not commit its stale destination`() {
        withViewport { viewport, root ->
            viewport.setInputEnabled(true)
            ViewCompat.dispatchApplyWindowInsets(root, keyboardInsets(0))
            val opening = WindowInsetsAnimationCompat(WindowInsetsCompat.Type.ime(), null, 300)
            val closing = WindowInsetsAnimationCompat(WindowInsetsCompat.Type.ime(), null, 300)
            val callback = viewport.insetsAnimationCallback
            callback.onPrepare(opening)
            ViewCompat.dispatchApplyWindowInsets(root, keyboardInsets(600))
            callback.onProgress(keyboardInsets(240), mutableListOf(opening))
            callback.onPrepare(closing)
            ViewCompat.dispatchApplyWindowInsets(root, keyboardInsets(0))
            callback.onEnd(opening)
            assertEquals(240, root.paddingBottom)
            val closingFrame = WindowInsetsCompat.Builder(keyboardInsets(160))
                .setVisible(WindowInsetsCompat.Type.ime(), false).build()
            callback.onProgress(closingFrame, mutableListOf(closing))
            assertEquals(160, root.paddingBottom)
            callback.onProgress(keyboardInsets(60), mutableListOf(closing))
            assertEquals(60, root.paddingBottom)
            callback.onEnd(closing)
            assertEquals(48, root.paddingBottom)
        }
    }

    @Test fun `floating assistant preserves navigation clearance when target hides the gesture bar`() {
        withViewport { _, root ->
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 0))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .setVisible(WindowInsetsCompat.Type.navigationBars(), false)
                .build()
            ViewCompat.dispatchApplyWindowInsets(root, insets)
            assertEquals(48, root.paddingBottom)
        }
    }

    private fun keyboardInsets(bottom: Int): WindowInsetsCompat = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48))
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
        .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
        .build()

    private fun withViewport(block: (FloatingOverlayViewport, FrameLayout) -> Unit) {
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
        try {
            block(viewport, root)
        } finally {
            viewport.close()
            controller.pause().stop().destroy()
        }
    }

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
