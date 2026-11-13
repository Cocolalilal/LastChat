package me.rerere.rikkahub.phoneuse

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.core.view.WindowCompat
import me.rerere.rikkahub.R

/** Configure the native renderer and full display viewport before WindowManager.addView. */
internal fun createFixedPhoneOverlayDialog(context: Context, root: View): ComponentDialog =
    ComponentDialog(context, R.style.Theme_Rikkahub_PhoneOverlay_Fixed).apply {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        requireNotNull(window).apply {
            setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            setFormat(PixelFormat.TRANSLUCENT)
            // ViewRootImpl selects its renderer once, during addView. Adding this later
            // cannot turn a service-context software window into a hardware window.
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            setWindowAnimations(0)
            // The setter marks this explicit, preventing installDecor's theme defaults
            // from resetting it before the first native attachment.
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0; y = 0
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                if (Build.VERSION.SDK_INT >= 30) {
                    setFitInsetsTypes(0)
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
        }
        setContentView(root)
    }
