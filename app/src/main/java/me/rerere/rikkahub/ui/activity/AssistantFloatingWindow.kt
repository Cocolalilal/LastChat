package me.rerere.rikkahub.ui.activity

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout

/** Fixed native viewport: Compose changes card geometry without reallocating a Window. */
internal class AssistantFloatingWindow(
    private val activity: Activity,
    private val session: AssistantOverlaySession,
) {
    private val root = FrameLayout(activity).apply {
        clipChildren = false
        clipToPadding = false
        setOnClickListener { session.onBackPressedDispatcher.onBackPressed() }
    }
    private val viewport: FloatingOverlayViewport
    private var blurRequest: ((Boolean) -> Unit)? = null
    init {
        activity.window.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
        }
        (session.view.parent as? android.view.ViewGroup)?.removeView(session.view)
        (session.decorationView.parent as? android.view.ViewGroup)?.removeView(session.decorationView)
        val container = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        container.addView(session.decorationView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(session.view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply {
            val density = activity.resources.displayMetrics.density
            setMargins((16 * density).toInt(), 0, (16 * density).toInt(), (32 * density).toInt())
        })
        activity.setContentView(container)
        viewport = FloatingOverlayViewport(activity.window, root, session.view, passThrough = false)
        viewport.onBlurChanged = { session.nativeBlurActive = it }
        val request: (Boolean) -> Unit = { viewport.setBlurEnabled(it) }
        blurRequest = request
        session.onBlurRequested = request
        session.bindWindow(activity.window)
        viewport.attach()
        viewport.setInputEnabled(true)
    }
    fun close() {
        // The service may already have rebound these callbacks and reparented the persistent
        // scene. Clean up only this Activity's native chrome, without touching the new host.
        if (session.onBlurRequested === blurRequest) session.onBlurRequested = {}
        viewport.onBlurChanged = null
        viewport.setBlurEnabled(false)
        viewport.close()
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}
