package me.rerere.rikkahub.ui.activity

import android.app.Application
import android.graphics.Rect
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Geometry and flag contracts; actual SurfaceControl input and blur are checked on-device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = Application::class)
class AssistantFloatingWindowTest {
    @Test fun `native blur wrappers are reached when DecorView returns only the original background`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val content = View(activity)
        root.addView(content, FrameLayout.LayoutParams(200, 100))
        activity.setContentView(root)
        val viewport = FloatingOverlayViewport(activity.window, root, content, passThrough = false)
        viewport.attach()
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1000, 1800)
        content.layout(200, 250, 400, 350)
        val original = requireNotNull(activity.window.decorView.background)
        val nativeBlur = ColorDrawable(Color.WHITE)
        val generated = InsetDrawable(LayerDrawable(arrayOf(nativeBlur, original)), 0)
        generated.setBounds(0, 0, 1000, 1800)
        // View.setBackgroundDrawable clears the previous original's callback after
        // DecorView has constructed its replacement LayerDrawable.
        original.callback = null
        assertSame(original, activity.window.decorView.background)
        viewport.syncGeometry()
        assertEquals(viewport.visualBounds, nativeBlur.bounds)
        // DecorView can reset its generated background bounds without moving the content.
        generated.setBounds(0, 0, 1100, 1900)
        viewport.syncGeometry()
        assertEquals(viewport.visualBounds, nativeBlur.bounds)
        content.alpha = 0.5f
        viewport.syncGeometry()
        // ColorDrawable rounds its channel modulation differently from the platform blur.
        assertEquals(0.5f, nativeBlur.alpha / 255f, 0.01f)
        assertSame(generated, (nativeBlur.callback as LayerDrawable).callback)
        viewport.close()
        controller.pause().stop().destroy()
    }

    @Test fun `assistant fallback keeps a fixed buffer and does not clip child springs`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val content = View(activity)
        root.addView(content, FrameLayout.LayoutParams(200, 100))
        activity.setContentView(root)
        val viewport = FloatingOverlayViewport(activity.window, root, content, passThrough = false)
        viewport.attach()
        val params = activity.window.attributes
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(0, params.windowAnimations)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        assertFalse(root.clipChildren)
        assertFalse(root.clipToPadding)
        assertFalse(activity.window.decorView.clipToOutline)
        viewport.close()
        controller.pause().stop().destroy()
    }

    @Test fun `blur bounds follow the actual scaled child without relayout or background recursion`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val content = View(activity)
        root.addView(content, FrameLayout.LayoutParams(200, 100))
        activity.setContentView(root)
        val viewport = FloatingOverlayViewport(activity.window, root, content, passThrough = false)
        viewport.attach()
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1000, 1800)
        content.layout(200, 250, 400, 350)
        viewport.syncGeometry()
        val before = Rect(viewport.visualBounds)
        content.pivotX = 100f
        content.pivotY = 50f
        content.scaleX = 2f
        content.scaleY = 2f
        content.translationX = 20f
        content.translationY = 30f
        viewport.syncGeometry()
        assertEquals(Rect(before.left - 80, before.top - 20, before.right + 120, before.bottom + 80),
            viewport.visualBounds)
        viewport.setBlurEnabled(true)
        var notifications = 0
        var reportedBlur: Boolean? = null
        viewport.onBlurChanged = { notifications++; reportedBlur = it }
        assertEquals(1, notifications)
        val available = Build.VERSION.SDK_INT >= 31 &&
            activity.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        assertEquals(available, reportedBlur)
        val background = activity.window.decorView.background
        val count = notifications
        val callback = activity.window.callback
        var relayouts = 0
        activity.window.callback = object : android.view.Window.Callback by callback {
            override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams) {
                relayouts++
                callback.onWindowAttributesChanged(attrs)
            }
        }
        repeat(120) {
            content.translationY += 0.5f
            viewport.syncGeometry()
            viewport.setBlurEnabled(true)
        }
        assertSame(background, activity.window.decorView.background)
        assertEquals(0, relayouts)
        assertEquals(count, notifications)
        assertEquals(1000, root.width)
        assertEquals(1800, root.height)
        viewport.setBlurEnabled(false)
        if (Build.VERSION.SDK_INT >= 31) assertEquals(0, activity.window.attributes.blurBehindRadius)
        viewport.close()
        controller.pause().stop().destroy()
    }
}
