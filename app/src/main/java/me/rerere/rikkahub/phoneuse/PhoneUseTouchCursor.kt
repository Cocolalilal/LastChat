package me.rerere.rikkahub.phoneuse

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
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
import me.rerere.rikkahub.ui.theme.cachedAssistantColorScheme
import me.rerere.rikkahub.ui.theme.withLastChatContrast
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

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
            cursor.palette = cursorPalette()
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

    private fun cursorPalette(): PhoneCursorHazePalette {
        val preferences = settings.settingsFlow.value
        val dark = when (service.readStringPreference("colorMode", "SYSTEM")) {
            "DARK" -> true
            "LIGHT" -> false
            else -> service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val base = if (preferences.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(service) else dynamicLightColorScheme(service)
        } else findPresetTheme(preferences.themeId).getColorScheme(dark)
        val contrast = if (Build.VERSION.SDK_INT >= 34) {
            service.getSystemService(UiModeManager::class.java)?.contrast ?: 0f
        } else 0f
        val assistantId = manager.session.value?.assistantId
        val assistant = preferences.assistants.firstOrNull { it.id == assistantId }
        val scheme = cachedAssistantColorScheme(assistant, base.withLastChatContrast(dark, contrast), dark)
            .withLastChatContrast(dark, contrast)
        return PhoneCursorHazePalette(scheme.primary.toArgb(), scheme.tertiary.toArgb(), scheme.secondary.toArgb())
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
        private val haze = PhoneCursorHazeRenderer(density)
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
        private var flowX = 0f
        private var flowY = 0f
        private var reducedMotion = false
        var palette = PhoneCursorHazePalette(Color.WHITE, Color.WHITE, Color.WHITE)
            set(value) { field = value; haze.palette = value }

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
            val startPress = press
            val startRipple = ripple
            reducedMotion = reduced
            val distance = hypot(x - startX, y - startY) / density
            visibility = VISIBLE
            if (reduced) {
                pointX = x; pointY = y; opacity = 1f
                press = 0f; ripple = 0f; flowX = 0f; flowY = 0f
                invalidate()
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = (110 + distance * 0.14f).toLong().coerceAtMost(190)
                interpolator = curve
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    moveTo(startX + (x - startX) * fraction, startY + (y - startY) * fraction)
                    opacity = startOpacity + (1f - startOpacity) * fraction
                    press = startPress * (1f - fraction)
                    ripple = startRipple * (1f - fraction)
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
            reducedMotion = reduced
            pointX = stroke.fromX; pointY = stroke.fromY
            destinationX = stroke.toX; destinationY = stroke.toY
            val started = SystemClock.uptimeMillis()
            // Real elapsed time, rather than Animator duration scale, keeps a swipe's
            // cursor at the injected stroke position even with 0.5x/2x system animations.
            val frame = object : Runnable {
                override fun run() {
                    val elapsed = SystemClock.uptimeMillis() - started
                    val fraction = phoneCursorStrokeProgress(elapsed, durationMs)
                    moveTo(stroke.fromX + (stroke.toX - stroke.fromX) * fraction,
                        stroke.fromY + (stroke.toY - stroke.fromY) * fraction)
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
            moveTo(destinationX, destinationY)
            invalidate()
        }

        fun release(reduced: Boolean, lingerMs: Long = 180) {
            if (visibility != VISIBLE) return
            cancelAnimations()
            val startPress = press
            val startOpacity = opacity
            val startRipple = ripple
            val startFlowX = flowX
            val startFlowY = flowY
            reducedMotion = reduced
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
                    flowX = startFlowX * (1f - release)
                    flowY = startFlowY * (1f - release)
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
            flowX = 0f; flowY = 0f
            visibility = INVISIBLE
        }

        private fun cancelAnimations() {
            gestureFrame?.let(::removeCallbacks)
            gestureFrame = null
            motion?.cancel(); motion = null
            feedback?.cancel(); feedback = null
        }

        private fun moveTo(x: Float, y: Float) {
            flowX = flowX * 0.62f + (x - pointX) * 0.38f
            flowY = flowY * 0.62f + (y - pointY) * 0.38f
            pointX = x; pointY = y
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (opacity <= 0f) return
            getLocationOnScreen(origin)
            val x = pointX - origin[0]
            val y = pointY - origin[1]
            haze.draw(canvas, x, y, opacity, press, ripple,
                if (reducedMotion) 0f else (SystemClock.uptimeMillis() % 43_982L) / 1400f,
                if (reducedMotion) 0f else flowX, if (reducedMotion) 0f else flowY)
        }
    }
}

internal fun phoneCursorStrokeProgress(elapsedMs: Long, durationMs: Long): Float =
    (elapsedMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)

internal data class PhoneCursorHazePalette(val primary: Int, val tertiary: Int, val secondary: Int)

/** The edge glow's three vivid hues gathered into a small field around the true touch point. */
internal class PhoneCursorHazeRenderer(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var palette = PhoneCursorHazePalette(Color.WHITE, Color.WHITE, Color.WHITE)
        set(value) {
            if (field == value) return
            field = value
            primary = glowShader(vivid(value.primary))
            tertiary = glowShader(vivid(value.tertiary))
            secondary = glowShader(vivid(value.secondary))
            focus = glowShader(blendWhite(vivid(value.primary), 0.78f))
        }
    private var primary = glowShader(Color.WHITE)
    private var tertiary = glowShader(Color.WHITE)
    private var secondary = glowShader(Color.WHITE)
    private var focus = glowShader(Color.WHITE)

    fun draw(
        canvas: Canvas, x: Float, y: Float, opacity: Float, press: Float, release: Float,
        phase: Float, flowX: Float = 0f, flowY: Float = 0f,
    ) {
        if (opacity <= 0f) return
        val pressure = press.coerceIn(0f, 1f)
        val bloom = release.coerceIn(0f, 1f)
        val radius = (19f - pressure * 3.5f + bloom * 5f) * density
        val drift = (3f - pressure) * density
        val strength = opacity.coerceIn(0f, 1f)
        val flow = (hypot(flowX, flowY) / (14f * density)).coerceIn(0f, 1f)
        if (flow > 0.001f) {
            val angle = Math.toDegrees(kotlin.math.atan2(flowY.toDouble(), flowX.toDouble())).toFloat()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(angle)
            canvas.translate(-10f * density * flow, 0f)
            canvas.scale(1f + flow * 0.85f, 0.78f)
            glow(canvas, 0f, 0f, radius * 1.05f, primary, strength * flow * 0.27f)
            canvas.restore()
        }
        // Diffuse lobes overlap like SilkGlow; no opaque dot, outline ring or stroke trail.
        glow(canvas, x + cos(phase) * drift, y + sin(phase) * drift, radius, primary, strength * 0.55f)
        glow(canvas, x - drift * 1.3f, y + cos(phase * 0.8f) * drift,
            radius * 0.8f, tertiary, strength * 0.46f)
        glow(canvas, x + drift, y - drift * 1.15f, radius * 0.7f, secondary, strength * 0.42f)
        glow(canvas, x, y, (16f + bloom * 18f) * density, primary,
            strength * (1f - bloom) * bloom * 0.45f)
        // A feathered highlight keeps the exact target legible through the haze.
        glow(canvas, x, y, (5f - pressure * 0.7f) * density, focus, strength * (0.78f + pressure * 0.16f))
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, shader: Shader, opacity: Float) {
        if (opacity <= 0f) return
        paint.shader = shader
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
    }

    private fun glowShader(color: Int) = RadialGradient(0f, 0f, 1f,
        intArrayOf(color, (color and 0x00ffffff) or 0x60000000, color and 0x00ffffff),
        floatArrayOf(0f, 0.36f, 1f), Shader.TileMode.CLAMP)

    private fun vivid(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = maxOf(hsv[1] * 1.5f, 0.6f).coerceIn(0f, 1f)
        hsv[2] = hsv[2].coerceIn(0.5f, 0.92f)
        return Color.HSVToColor(hsv)
    }

    private fun blendWhite(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt(),
    )
}
