package me.rerere.rikkahub.phoneuse

import android.app.Application
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneOverlayDialogTest {
    @Test fun nativeFloatingWindowKeepsThePillWidthAndDoesNotBlurTheWholeScreen() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val density = activity.resources.displayMetrics.density
        // Exercise the platform dialog's measurement policy independently of Compose's clock.
        val content = object : View(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(resolveSize((108 * density).toInt(), widthMeasureSpec),
                    resolveSize((56 * density).toInt(), heightMeasureSpec))
            }
        }
        val popup = createPhoneOverlayDialog(activity, content)
        popup.show()
        val window = popup.window!!
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setBackgroundBlurRadius(24)
        val root = window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec((891 * density).toInt(), View.MeasureSpec.AT_MOST))
        assertEquals((108 * density).toInt(), root.measuredWidth)
        assertEquals((56 * density).toInt(), root.measuredHeight)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, window.attributes.type)
        assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        assertEquals(0, window.attributes.windowAnimations)
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0)
        popup.dismiss()
        controller.pause().stop().destroy()
    }
    @Test fun composeContentStaysVisibleInsideTheNativeAccessibilityWindow() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val uiContext = androidx.compose.ui.platform.AndroidUiDispatcher.CurrentThread
        val recomposer = androidx.compose.runtime.Recomposer(uiContext)
        val scope = kotlinx.coroutines.CoroutineScope(uiContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        val content = androidx.compose.ui.platform.ComposeView(activity).apply {
            visibility = View.INVISIBLE
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setParentCompositionContext(recomposer)
            setContent { androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.size(108.dp, 56.dp)) }
        }
        val popup = createPhoneOverlayDialog(activity, content)
        popup.show()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(64))
        val density = activity.resources.displayMetrics.density
        val root = popup.window!!.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec((891 * density).toInt(), View.MeasureSpec.AT_MOST))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        assertEquals(View.VISIBLE, content.visibility)
        assertEquals((108 * density).toInt(), content.measuredWidth)
        assertEquals((56 * density).toInt(), content.measuredHeight)
        popup.dismiss()
        content.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        controller.pause().stop().destroy()
    }

}
