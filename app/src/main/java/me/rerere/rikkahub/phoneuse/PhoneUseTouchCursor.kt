package me.rerere.rikkahub.phoneuse

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.theme.findPresetTheme
import kotlin.coroutines.resume
import kotlin.math.hypot

/** One coordinate source for the injected stroke and its visual feedback. */
internal data class PhoneCursorStroke(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float) {
    companion object {
        fun withinScreen(
            fromX: Float, fromY: Float, toX: Float, toY: Float, width: Int, height: Int,
        ): PhoneCursorStroke {
            require(listOf(fromX, fromY, toX, toY).all(Float::isFinite)) { "Gesture coordinates must be finite." }
            val right = (width - 1).coerceAtLeast(0).toFloat()
            val bottom = (height - 1).coerceAtLeast(0).toFloat()
            return PhoneCursorStroke(fromX.coerceIn(0f, right), fromY.coerceIn(0f, bottom),
                toX.coerceIn(0f, right), toY.coerceIn(0f, bottom))
        }
    }
}

/**
 * Visual feedback only: this trusted window cannot receive focus or intercept a gesture.
 * It is independent of the assistant/popup host so both presentations share the cursor.
 */
internal class PhoneUseTouchCursor(
    private val service: AccessibilityService,
    private val manager: PhoneUseManager,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: CursorView? = null
    private var closed = false

    fun start() {
        scope.launch {
            combine(manager.session, manager.capturing, manager.appUiVisible) { task, capturing, app ->
                Triple(task?.let { it.running && !it.paused } == true, capturing, app)
            }.collect { (running, capturing, app) ->
                // Capture never sees our cursor. Do not resurrect a stale pointer afterwards.
                if (capturing) hide(immediate = true)
                else if (app || !running) hide(immediate = false)
            }
        }
    }

    suspend fun approach(stroke: PhoneCursorStroke) {
        if (closed || manager.capturing.value || manager.appUiVisible.value ||
            manager.session.value?.let { it.running && !it.paused } != true) return
        try {
            val cursor = view ?: mount() ?: return
            cursor.tint = cursorColor()
            cursor.approach(stroke.fromX, stroke.fromY, reduceMotion())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            hide(immediate = true)
            PlatformLog.e("PhoneUseCursor", "Unable to animate touch feedback: ${error.message}")
        }
    }

    fun press(stroke: PhoneCursorStroke, durationMs: Long) {
        view?.takeIf { it.visibility == View.VISIBLE }?.press(stroke, durationMs, reduceMotion())
    }

    fun completeStroke() { view?.completeStroke() }

    fun release() { view?.release(reduceMotion()) }

    fun hide(immediate: Boolean) {
        if (immediate) view?.hide() else view?.release(reduceMotion(), lingerMs = 0)
    }

    private fun mount(): CursorView? = runCatching {
        val cursor = CursorView(service)
        val attrs = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0; y = 0
            title = "LastChat touch feedback"
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        windows.addView(cursor, attrs)
        view = cursor
        cursor
    }.getOrElse {
        // Feedback must never prevent an otherwise valid phone action.
        PlatformLog.e("PhoneUseCursor", "Unable to show touch feedback: ${it.message}")
        null
    }

    private fun reduceMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled() || listOf(
        Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.WINDOW_ANIMATION_SCALE,
    ).any { Settings.Global.getFloat(service.contentResolver, it, 1f) <= 0f }

    private fun cursorColor(): Int {
        val preferences = settings.settingsFlow.value
        val dark = when (service.readStringPreference("colorMode", "SYSTEM")) {
            "DARK" -> true
            "LIGHT" -> false
            else -> service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val scheme = if (preferences.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(service) else dynamicLightColorScheme(service)
        } else findPresetTheme(preferences.themeId).getColorScheme(dark)
        return scheme.primary.toArgb()
    }

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        view?.let {
            it.hide()
            if (it.isAttachedToWindow) runCatching { windows.removeViewImmediate(it) }
        }
        view = null
    }

    private class CursorView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val origin = IntArray(2)
        private val curve = PathInterpolator(0.2f, 0f, 0f, 1f)
        private var motion: ValueAnimator? = null
        private var feedback: ValueAnimator? = null
        private var gestureFrame: Runnable? = null
        private var pointX = 0f
        private var pointY = 0f
        private var destinationX = 0f
        private var destinationY = 0f
        private var positioned = false
        private var opacity = 0f
        private var press = 0f
        private var ripple = 0f
        var tint: Int = Color.WHITE

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            visibility = INVISIBLE
        }

        suspend fun approach(x: Float, y: Float, reduced: Boolean) = suspendCancellableCoroutine { continuation ->
            cancelAnimations()
            // Retain the last point while faded out so the next action can flow from it.
            if (!positioned) { pointX = x; pointY = y; positioned = true }
            val startX = pointX
            val startY = pointY
            val startOpacity = opacity
            val distance = hypot(x - startX, y - startY) / density
            visibility = VISIBLE
            press = 0f; ripple = 0f
            if (reduced) {
                pointX = x; pointY = y; opacity = 1f
                invalidate()
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = (110 + distance * 0.14f).toLong().coerceAtMost(190)
                interpolator = curve
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    pointX = startX + (x - startX) * fraction
                    pointY = startY + (y - startY) * fraction
                    opacity = startOpacity + (1f - startOpacity) * fraction
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })
            }
            motion = animator
            continuation.invokeOnCancellation { post { animator.cancel() } }
            animator.start()
        }

        fun press(stroke: PhoneCursorStroke, durationMs: Long, reduced: Boolean) {
            cancelAnimations()
            opacity = 1f; press = 0f; ripple = 0f
            pointX = stroke.fromX; pointY = stroke.fromY
            destinationX = stroke.toX; destinationY = stroke.toY
            val started = SystemClock.uptimeMillis()
            // Real elapsed time, rather than Animator duration scale, keeps a swipe's
            // cursor at the injected stroke position even with 0.5x/2x system animations.
            val frame = object : Runnable {
                override fun run() {
                    val elapsed = SystemClock.uptimeMillis() - started
                    val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                    pointX = stroke.fromX + (stroke.toX - stroke.fromX) * fraction
                    pointY = stroke.fromY + (stroke.toY - stroke.fromY) * fraction
                    press = if (reduced) 1f else curve.getInterpolation((elapsed / 70f).coerceIn(0f, 1f))
                    ripple = if (reduced) 0f else fraction * 0.35f
                    invalidate()
                    if (fraction < 1f) postOnAnimation(this)
                }
            }
            gestureFrame = frame
            postOnAnimation(frame)
            invalidate()
        }

        fun completeStroke() {
            gestureFrame?.let(::removeCallbacks)
            gestureFrame = null
            pointX = destinationX; pointY = destinationY
            invalidate()
        }

        fun release(reduced: Boolean, lingerMs: Long = 180) {
            if (visibility != VISIBLE) return
            cancelAnimations()
            val startPress = press
            val startOpacity = opacity
            val startRipple = ripple
            val hold = if (reduced) 0L else lingerMs
            val fadeDuration = if (reduced) 100L else 260L
            val total = hold + fadeDuration
            feedback = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = total
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    val elapsed = fraction * total
                    val release = curve.getInterpolation((elapsed / 180f).coerceIn(0f, 1f))
                    val fade = curve.getInterpolation(((elapsed - hold) / fadeDuration).coerceIn(0f, 1f))
                    press = startPress * (1f - release)
                    ripple = if (reduced) 0f else startRipple + (1f - startRipple) * fraction
                    opacity = startOpacity * (1f - fade)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (opacity <= 0f) visibility = INVISIBLE
                    }
                })
                start()
            }
        }

        fun hide() {
            cancelAnimations()
            opacity = 0f
            visibility = INVISIBLE
        }

        private fun cancelAnimations() {
            gestureFrame?.let(::removeCallbacks)
            gestureFrame = null
            motion?.cancel(); motion = null
            feedback?.cancel(); feedback = null
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (opacity <= 0f) return
            getLocationOnScreen(origin)
            val x = pointX - origin[0]
            val y = pointY - origin[1]
            paint.style = Paint.Style.FILL
            paint.color = tint
            paint.alpha = (opacity * 35).toInt()
            canvas.drawCircle(x, y, 13f * density, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.alpha = (opacity * (1f - ripple) * 165).toInt()
            canvas.drawCircle(x, y, (9f + 17f * ripple) * density, paint)
            paint.style = Paint.Style.FILL
            paint.alpha = (opacity * 255).toInt()
            val radius = (5.5f - press) * density
            canvas.drawCircle(x, y, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = Color.WHITE
            paint.alpha = (opacity * 240).toInt()
            canvas.drawCircle(x, y, radius, paint)
        }
    }
}
