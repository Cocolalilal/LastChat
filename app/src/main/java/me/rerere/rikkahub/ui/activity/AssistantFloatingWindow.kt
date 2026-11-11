package me.rerere.rikkahub.ui.activity

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowInsets
import android.view.Gravity
import android.view.WindowManager
import java.util.function.Consumer
import me.rerere.common.platform.PlatformLog

/** A transparent outside-tap host and tightly sized controls window with local native blur. */
internal class AssistantFloatingWindow(
    private val activity: Activity,
    private val onBlurChanged: (Boolean) -> Unit,
) {
    private val manager = activity.getSystemService(WindowManager::class.java)
    private var contentWindow: Window? = null
    private var requested = false
    private var available = false
    private var appliedRadius = -1
    private var reportedActive: Boolean? = null
    private var lastDiagnostic: String? = null
    private val listener = Consumer<Boolean> { available = it; applyBlur() }

    fun attach() {
        activity.window.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            setWindowAnimations(0)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            available = manager.isCrossWindowBlurEnabled
            manager.addCrossWindowBlurEnabledListener(activity.mainExecutor, listener)
        }
        applyBlur()
    }

    fun setContentWindow(window: Window) {
        contentWindow = window
        appliedRadius = -1
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        updateContentBounds()
        applyBlur()
    }

    fun updateContentBounds() {
        val window = contentWindow ?: return
        val availableWidth = if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            metrics.bounds.width() - insets.left - insets.right
        } else activity.resources.displayMetrics.widthPixels
        val density = activity.resources.displayMetrics.density
        val width = (availableWidth - 32f * density).toInt().coerceAtLeast(1)
        val params = window.attributes
        val y = (24f * density).toInt()
        if (params.width == width && params.height == WindowManager.LayoutParams.WRAP_CONTENT && params.y == y) return
        params.width = width
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = y
        window.attributes = params
    }

    fun setBlurEnabled(enabled: Boolean) {
        if (requested == enabled) return
        requested = enabled
        applyBlur()
    }

    private fun applyBlur() {
        val window = contentWindow ?: return
        // Keep the request installed while the OS temporarily disables blur. The compositor
        // owns availability; capability callbacks only change the readable surface tint.
        val radius = if (requested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (24 * activity.resources.displayMetrics.density).toInt()
        } else 0
        if (appliedRadius != radius) {
            // Background blur follows the exact floating controls window. The full-screen
            // outside-tap host stays transparent, so switching modes never blurs the whole app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(radius)
                window.attributes.setBlurBehindRadius(0)
            }
            appliedRadius = radius
        }
        val active = radius > 0 && available
        val diagnostic = "blur requested=$requested available=$available radius=$radius " +
            "device=${Build.MANUFACTURER}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT}"
        if (lastDiagnostic != diagnostic) {
            lastDiagnostic = diagnostic
            PlatformLog.i("AssistantWindow", diagnostic)
        }
        if (reportedActive != active) {
            reportedActive = active
            onBlurChanged(active)
        }
    }

    fun close() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.removeCrossWindowBlurEnabledListener(listener)
    }
}
