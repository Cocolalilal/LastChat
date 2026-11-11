package me.rerere.rikkahub.phoneuse

import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import me.rerere.rikkahub.R

/** A real floating Window exposes native background blur without private Android APIs. */
internal fun createPhoneOverlayDialog(context: Context, content: View): Dialog =
    Dialog(context, R.style.Theme_Rikkahub_PhoneOverlay).apply {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        window!!.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            setFormat(PixelFormat.TRANSLUCENT)
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 28f * context.resources.displayMetrics.density
            })
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }
        content.visibility = View.VISIBLE
        setContentView(content)
    }
