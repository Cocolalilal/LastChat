package me.rerere.rikkahub.ui.activity

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import java.util.function.Consumer
import me.rerere.common.platform.PlatformLog

/** A fixed transparent surface: streaming and Compose animations never resize the OS window. */
internal class AssistantFloatingWindow(
    private val activity: Activity,
    private val onBlurChanged: (Boolean) -> Unit,
) {
    private val manager = activity.getSystemService(WindowManager::class.java)
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

    fun setBlurEnabled(enabled: Boolean) {
        if (requested == enabled) return
        requested = enabled
        applyBlur()
    }

    private fun applyBlur() {
        // Keep the request installed while the OS temporarily disables blur. The compositor
        // owns availability; capability callbacks only change the readable surface tint.
        val radius = if (requested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (24 * activity.resources.displayMetrics.density).toInt()
        } else 0
        if (appliedRadius != radius) {
            val params = activity.window.attributes
            params.flags = if (radius > 0) params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                else params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) params.setBlurBehindRadius(radius)
            activity.window.attributes = params
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
