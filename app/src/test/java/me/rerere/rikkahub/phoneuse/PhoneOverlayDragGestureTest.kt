package me.rerere.rikkahub.phoneuse

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PhoneOverlayDragGestureTest {
    @Test fun releaseVelocitySurvivesTheWindowFollowingTheFinger() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        lateinit var view: View
        var starts = 0
        var releaseVelocity = 0f
        var releases = 0
        val trace = mutableListOf<String>()
        val drag = PhoneOverlayDragCallbacks(
            onStart = { starts++ },
            onMove = { dx, _ -> trace += "move=$dx"; view.translationX += dx },
            onRelease = { vx, _ -> releases++; releaseVelocity = vx; trace += "release=$vx" },
        )
        activity.setContent { Box(Modifier.size(128.dp, 56.dp).phoneOverlayDrag(drag)) }
        view = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val density = activity.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((128 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((56 * density).toInt(), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float) {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                origin[0] + x, origin[1] + 24 * density, 0)
            motion.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
            trace += "event=$action time=${SystemClock.uptimeMillis()} x=${motion.x} loc=${location[0]}"
            view.dispatchTouchEvent(motion)
            motion.recycle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8))
        }
        event(MotionEvent.ACTION_DOWN, 16 * density)
        repeat(5) { event(MotionEvent.ACTION_MOVE, (16 + (it + 1) * 48) * density) }
        event(MotionEvent.ACTION_UP, (16 + 5 * 48) * density)
        assertEquals(1, starts)
        assertTrue("The popup should follow the screen-space drag", view.translationX > 200 * density)
        assertEquals("trace=$trace", 1, releases)
        assertTrue("A moving window must retain the throw's velocity: $trace", releaseVelocity > 1000f)
        controller.pause().stop().destroy()
    }
}
