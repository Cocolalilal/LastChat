package me.rerere.rikkahub.phoneuse

import android.app.UiAutomation
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import me.rerere.rikkahub.service.ChatService
import org.koin.core.context.GlobalContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Run on an actual Android runtime, not Robolectric. Native geometry and input dispatch are the
 * assertions; captured timing data is diagnostic, never a claim that a software emulator is smooth.
 *
 * adb shell am instrument -w -e class me.rerere.rikkahub.phoneuse.FloatingOverlayNativeWindowTest
 *   lastchat.rikkafork.cocolal.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class FloatingOverlayNativeWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun await(label: String, timeoutMs: Long = 90_000, check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var done = false
            main { done = check() }
            if (done) return
            SystemClock.sleep(30)
        }
        throw AssertionError("Timed out: $label")
    }

    private fun touch(action: Int, down: Long, now: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, now, action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Native input injection rejected", automation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, down, x, y)
        touch(MotionEvent.ACTION_UP, down, down + 32, x, y)
    }

    private fun swipe(from: Point, to: Point, durationMs: Long = 160) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, down, from.x.toFloat(), from.y.toFloat())
        repeat(10) { step ->
            val t = (step + 1) / 10f
            val timestamp = down + (durationMs * t).toLong()
            val wait = timestamp - SystemClock.uptimeMillis()
            if (wait > 0) SystemClock.sleep(wait)
            touch(MotionEvent.ACTION_MOVE, down, timestamp,
                from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
        }
        touch(MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), to.x.toFloat(), to.y.toFloat())
    }

    private fun screenshot(label: String, assertSharpBackdrop: Boolean = false) {
        automation.takeScreenshot()?.let { bitmap ->
            try {
                File(instrumentation.targetContext.getExternalFilesDir(null), "overlay-$label.png")
                    .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                if (assertSharpBackdrop) {
                    val colors = listOf(Color.rgb(210, 224, 235),
                        Color.rgb(56, 76, 94))
                    for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                        val pixel = bitmap.getPixel((bitmap.width * fraction).toInt(), bitmap.height / 3)
                        assertTrue("Blur leaked above the floating pill: ${pixel.toUInt().toString(16)}",
                            colors.any { expected ->
                                kotlin.math.abs(Color.red(pixel) - Color.red(expected)) <= 3 &&
                                    kotlin.math.abs(Color.green(pixel) - Color.green(expected)) <= 3 &&
                                    kotlin.math.abs(Color.blue(pixel) - Color.blue(expected)) <= 3
                            })
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun populatedAssistantAndPopupKeepOneNativeViewportThroughMorphResizeAndIme() {
        assumeTrue("Precise public touch regions require Android 13", Build.VERSION.SDK_INT >= 33)
        val context = instrumentation.targetContext
        val avatarOffset = (28 * context.resources.displayMetrics.density).toInt()
        val intent = Intent(context, FloatingOverlayLabActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        main {
            // ChatService registers a ProcessLifecycleOwner observer and must be created on main.
            GlobalContext.get().get<ChatService>()
            context.startActivity(intent)
        }
        var launched: FloatingOverlayLabActivity? = null
        await("animated lab activity resumed") {
            launched = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<FloatingOverlayLabActivity>().firstOrNull()
            launched != null
        }
        val activity = requireNotNull(launched)
        try {
            await("native window drawn") { activity.visualBounds.width() > 0 && activity.visualBounds.height() > 0 }
            SystemClock.sleep(600)
            var original = emptyList<Int>()
            var originalInputHeight = 0
            main { original = activity.nativeGeometry; originalInputHeight = activity.inputHeight }
            assertTrue("Real composer measured", originalInputHeight > 0)
            assertEquals("fixed viewport width", -1, original[0])
            assertEquals("fixed viewport height", -1, original[1])

            screenshot("initial-assistant")

            // The region above the visible card must target the checkerboard window, not an
            // invisible full-screen dialog input region. This is a real WMS hit test.
            var taps = 0
            main { taps = activity.outsideTaps }
            tap(32f, 100f)
            await("outside touch passed through native region") { activity.outsideTaps == taps + 1 }

            repeat(10) { cycle ->
                main { activity.setPhase("phone-$cycle", phone = true) }
                await("compact phone pose") { activity.compactSettled && activity.inputHidden }
                if (cycle == 0) screenshot("assistant-phone-pill", assertSharpBackdrop = true)
                main { assertEquals(original, activity.nativeGeometry) }
                main { activity.setPhase("return-$cycle", phone = false, generating = false) }
                await("input restored to measured height") {
                    !activity.inputHidden && !activity.compactSettled && activity.inputHeight >= originalInputHeight - 1
                }
                SystemClock.sleep(600)
                main { assertEquals(original, activity.nativeGeometry) }
            }

            // Reverse a transition before either endpoint; preserve the live composition.
            repeat(6) { cycle ->
                main { activity.setPhase("reverse-$cycle", phone = cycle % 2 == 0) }
                SystemClock.sleep(80)
            }
            main { activity.setPhase("restored", phone = false) }
            SystemClock.sleep(700)

            var handle = Point()
            var bottom = 0
            main {
                handle = Point(activity.visualBounds.centerX(), activity.visualBounds.top + 8)
                bottom = activity.visualBounds.bottom
            }
            swipe(handle, Point(handle.x, (handle.y - 160).coerceAtLeast(50)), 120)
            SystemClock.sleep(80)
            main { handle = Point(activity.visualBounds.centerX(), activity.visualBounds.top + 8) }
            swipe(handle, Point(handle.x, (bottom - 100).coerceAtLeast(handle.y + 40)), 100)
            SystemClock.sleep(700)

            var editor = Point()
            main {
                activity.setPhase("ime-show")
                editor = Point(activity.visualBounds.centerX(), activity.visualBounds.bottom - avatarOffset)
            }
            // Return native window focus after the deliberate outside tap before requesting IME.
            tap(editor.x.toFloat(), editor.y.toFloat())
            main { activity.showKeyboard() }
            await("real IME visible") { activity.keyboardVisible }
            screenshot("assistant-keyboard")
            main { assertEquals("IME must not resize the native window", original, activity.nativeGeometry) }
            main { activity.setPhase("ime-hide"); activity.hideKeyboard() }
            await("real IME hidden") { !activity.keyboardVisible }

            main { activity.setPhase("popup-working", popup = true, generating = true) }
            SystemClock.sleep(700)
            screenshot("normal-phone-pill")
            var compactPopupHeight = 0
            var avatar = Point()
            main {
                compactPopupHeight = activity.visualBounds.height()
                avatar = Point(activity.visualBounds.left + avatarOffset, activity.visualBounds.top + avatarOffset)
            }
            tap(avatar.x.toFloat(), avatar.y.toFloat())
            await("popup expands after native avatar tap") {
                activity.visualBounds.height() > compactPopupHeight + 40
            }
            SystemClock.sleep(500)
            screenshot("normal-phone-expanded")
            main { avatar = Point(activity.visualBounds.left + avatarOffset, activity.visualBounds.top + avatarOffset) }
            tap(avatar.x.toFloat(), avatar.y.toFloat())
            await("popup returns to a pill") { activity.visualBounds.height() <= compactPopupHeight + 1 }
            main { activity.setPhase("popup-finishing", generating = false) }
            SystemClock.sleep(700)
            repeat(4) { paragraph ->
                main { activity.setPhase("popup-reply-$paragraph", text =
                    "**Completed.**\n\n" + "A populated Markdown paragraph with a [link](https://example.com).\n\n".repeat(paragraph + 1)) }
                SystemClock.sleep(100)
            }
            SystemClock.sleep(700)

            var report = ""
            main { report = activity.report() }
            val json = JSONObject(report)
            val frames = json.getJSONArray("frames")
            assertTrue("Must sample real Android frames", frames.length() >= 10)
            val firstSurface = frames.getJSONObject(0).getJSONArray("surface")
            val surfaceSize = (0 until firstSurface.length()).map(firstSurface::getInt)
            assertTrue("Native root has a real buffer size", surfaceSize.all { it > 0 })
            for (index in 0 until frames.length()) {
                val frame = frames.getJSONObject(index)
                val native = frame.getJSONArray("native")
                assertEquals("Native buffer geometry changed during ${frame.getString("phase")}",
                    original, (0 until native.length()).map(native::getInt))
                val surface = frame.getJSONArray("surface")
                assertEquals("Measured native root resized during ${frame.getString("phase")}",
                    surfaceSize, (0 until surface.length()).map(surface::getInt))
                val rect = frame.getJSONArray("content")
                assertTrue("Inverted content frame", rect.getInt(2) >= rect.getInt(0) && rect.getInt(3) >= rect.getInt(1))
            }
            assertFalse("Input must return after phone use", activity.inputHidden)
            val output = File(context.getExternalFilesDir(null), "overlay-native-window-report.json")
            output.writeText(report)
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("overlay_report", output.absolutePath) })
        } finally {
            // Keep evidence even when an assertion exposes a regression.
            var report = ""
            main { report = activity.report() }
            File(context.getExternalFilesDir(null), "overlay-native-window-report.json").writeText(report)
            automation.takeScreenshot()?.let { bitmap ->
                File(context.getExternalFilesDir(null), "overlay-native-window-final.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            main { activity.finish() }
        }
    }
}
