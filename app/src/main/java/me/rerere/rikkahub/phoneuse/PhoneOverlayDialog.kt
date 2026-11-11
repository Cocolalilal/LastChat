package me.rerere.rikkahub.phoneuse

import androidx.activity.ComponentDialog
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import me.rerere.rikkahub.R

/** A real floating Window exposes native background blur without private Android APIs. */
internal fun createPhoneOverlayDialog(context: Context, content: View, radiusDp: Float = 28f): ComponentDialog =
    ComponentDialog(context, R.style.Theme_Rikkahub_PhoneOverlay).apply {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        window!!.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            setFormat(PixelFormat.TRANSLUCENT)
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = radiusDp * context.resources.displayMetrics.density
            })
            decorView.clipToOutline = true
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }
        content.visibility = View.VISIBLE
        setContentView(content)
        // BackgroundBlurDrawable is maintained by DecorView, outside Compose's draw layer.
        // Synchronize it at pre-draw, including its first frame and after capability changes.
        window!!.decorView.viewTreeObserver.addOnPreDrawListener {
            val background = window!!.decorView.background
            val alpha = (content.alpha.coerceIn(0f, 1f) * 255).toInt()
            if (background != null && background.alpha != alpha) background.alpha = alpha
            true
        }
    }
