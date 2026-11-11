package me.rerere.rikkahub.ui.activity

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import java.util.function.Consumer

/** Android blurs the live surfaces behind this small window; no screenshot is drawn. */
internal class AssistantFloatingWindow(
    private val activity: Activity,
    private val onBlurChanged: (Boolean) -> Unit,
) {
    private val manager = activity.getSystemService(WindowManager::class.java)
    private var requested = false
    private var available = false
    private val listener = Consumer<Boolean> { available = it; applyBlur() }

    fun attach() {
        activity.window.apply {
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 28f * activity.resources.displayMetrics.density
            })
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
        val active = requested && available && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.window.setBackgroundBlurRadius(if (active) (24 * activity.resources.displayMetrics.density).toInt() else 0)
        }
        onBlurChanged(active)
    }

    fun close() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.removeCrossWindowBlurEnabledListener(listener)
    }
}
