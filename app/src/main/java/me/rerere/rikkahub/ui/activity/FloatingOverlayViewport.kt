package me.rerere.rikkahub.ui.activity

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import java.util.function.Consumer
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A stable surface buffer. Animation changes child geometry, blur bounds and input insets;
 * it never resizes or moves the native window on Android 13+.
 *
 * All calls use public Android APIs. In particular, the touch region belongs to
 * AttachedSurfaceControl, not a reflected ViewTreeObserver internal-insets listener.
 */
internal class FloatingOverlayViewport(
    private val window: Window,
    private val root: FrameLayout,
    val content: View,
    private var passThrough: Boolean,
    private val radiusDp: Float = 28f,
) {
    companion object {
        val supportsFixedViewport: Boolean get() = Build.VERSION.SDK_INT >= 33
    }

    val fixed: Boolean = supportsFixedViewport || !passThrough
    var availableFrame = Rect()
        private set
    var visualBounds = Rect()
        private set
    var onFrame: ((Rect) -> Unit)? = null
    var onAvailableFrameChanged: ((Rect) -> Unit)? = null
    var onBlurChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            // Mount order differs between Activity and service hosts. A late listener must
            // receive an already-active blur state without waiting for a capability change.
            reportedBlur = null
            reportBlur()
        }

    private val density = root.resources.displayMetrics.density
    private val manager = root.context.getSystemService(WindowManager::class.java)
    private val location = IntArray(2)
    private val screenLocation = IntArray(2)
    private val matrix = Matrix()
    private val mappedBounds = RectF()
    private val mappedOrigin = FloatArray(2)
    private var generatedBackground: Drawable? = null
    private val originalBackground = object : GradientDrawable() {
        override fun getPadding(padding: Rect): Boolean {
            // LayerDrawable queries padding while constructing its children. Capture it
            // here: View later clears the original drawable's callback when installing
            // the generated blur wrapper, and DecorView's getter hides that wrapper.
            (callback as? Drawable)?.let { generatedBackground = it }
            return super.getPadding(padding)
        }
    }.apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = radiusDp * density
    }
    private var requestedBlur = false
    private var blurAvailable = false
    private var appliedBlur = -1
    private var reportedBlur: Boolean? = null
    private var inputEnabled = false
    private var touchEnabled = true
    private var lastRegion: Region? = null
    private var appliedBackground: Drawable? = null
    private var appliedBlurBounds = Rect()
    private val blurListener = Consumer<Boolean> {
        blurAvailable = it
        reportBlur()
    }
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        syncGeometry()
        true
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateAvailableFrame()
        syncGeometry()
    }

    fun attach() {
        window.setWindowAnimations(0)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        // Install the original background before requesting blur. Never replace decor.background:
        // DecorView would treat its generated blur LayerDrawable as a new original background.
        window.setBackgroundDrawable(originalBackground)
        window.decorView.clipToOutline = false
        // Child springs can briefly extend past their measured layout box. In particular the
        // Activity fallback must not clip an upward lift at the root's padding boundary.
        root.clipChildren = false
        root.clipToPadding = false
        if (fixed) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val attrs = window.attributes
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT
            attrs.height = WindowManager.LayoutParams.MATCH_PARENT
            attrs.gravity = Gravity.TOP or Gravity.LEFT
            attrs.x = 0
            attrs.y = 0
            attrs.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            if (Build.VERSION.SDK_INT >= 30) {
                attrs.setFitInsetsTypes(0)
                attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            window.attributes = attrs
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                applyInsets(insets)
                insets
            }
            ViewCompat.setWindowInsetsAnimationCallback(root,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onProgress(insets: WindowInsetsCompat,
                        runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                        applyInsets(insets)
                        return insets
                    }
                })
            ViewCompat.requestApplyInsets(root)
        }
        root.addOnLayoutChangeListener(layoutListener)
        content.addOnLayoutChangeListener(layoutListener)
        root.viewTreeObserver.addOnPreDrawListener(preDraw)
        if (Build.VERSION.SDK_INT >= 31) {
            blurAvailable = manager.isCrossWindowBlurEnabled
            manager.addCrossWindowBlurEnabledListener(root.context.mainExecutor, blurListener)
        }
        reportBlur()
        updateAvailableFrame()
        setInputEnabled(false)
        syncGeometry()
    }

    private fun applyInsets(insets: WindowInsetsCompat) {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        val bottom = maxOf(bars.bottom, ime.bottom)
        if (root.paddingLeft != bars.left || root.paddingTop != bars.top ||
            root.paddingRight != bars.right || root.paddingBottom != bottom) {
            root.setPadding(bars.left, bars.top, bars.right, bottom)
            updateAvailableFrame()
        }
    }

    private fun updateAvailableFrame() {
        val next = Rect(root.paddingLeft, root.paddingTop,
            (root.width - root.paddingRight).coerceAtLeast(root.paddingLeft),
            (root.height - root.paddingBottom).coerceAtLeast(root.paddingTop))
        if (next != availableFrame) {
            appliedBackground = null
            lastRegion = null
            availableFrame.set(next)
            onAvailableFrameChanged?.invoke(Rect(next))
        }
    }

    /** Call immediately after changing translation; pre-draw also catches Compose size changes. */
    fun syncGeometry() {
        if (root.width == 0 || root.height == 0) return
        mappedBounds.set(0f, 0f, content.width.toFloat(), content.height.toFloat())
        if (Build.VERSION.SDK_INT >= 29) {
            matrix.reset()
            content.transformMatrixToGlobal(matrix)
            matrix.mapRect(mappedBounds)
            // transformMatrixToGlobal is in screen coordinates; SurfaceControl and native
            // background drawables both consume window coordinates, including in multiwindow.
            window.decorView.getLocationOnScreen(screenLocation)
            window.decorView.getLocationInWindow(location)
            mappedBounds.offset((location[0] - screenLocation[0]).toFloat(),
                (location[1] - screenLocation[1]).toFloat())
        } else {
            // API28 has no public transformMatrixToGlobal. This fallback host has untransformed
            // ancestors, so combine the child's public matrix with its mapped window origin.
            matrix.set(content.matrix)
            matrix.mapRect(mappedBounds)
            mappedOrigin[0] = 0f; mappedOrigin[1] = 0f
            matrix.mapPoints(mappedOrigin)
            content.getLocationInWindow(location)
            mappedBounds.offset(location[0] - mappedOrigin[0], location[1] - mappedOrigin[1])
        }
        val next = Rect(floor(mappedBounds.left).toInt(), floor(mappedBounds.top).toInt(),
            ceil(mappedBounds.right).toInt(), ceil(mappedBounds.bottom).toInt())
        if (!content.isShown) next.setEmpty()
        val changed = visualBounds != next
        if (changed) visualBounds.set(next)
        updateInputRegion()
        updateBlurBounds()
        if (changed) onFrame?.invoke(Rect(next))
    }

    private fun updateInputRegion() {
        if (Build.VERSION.SDK_INT < 33) return
        val surface = root.rootSurfaceControl ?: return
        val region = when {
            !touchEnabled -> Region()
            passThrough && content.alpha <= 0f -> Region()
            passThrough -> Region(visualBounds).apply {
                op(0, 0, window.decorView.width, window.decorView.height, Region.Op.INTERSECT)
            }
            else -> Region(0, 0, root.width, root.height)
        }
        if (lastRegion != region) {
            surface.setTouchableRegion(region)
            lastRegion = Region(region)
        }
    }

    private fun updateBlurBounds() {
        val background = windowBackground()
        val bounds = if (content.alpha > 0f) visualBounds else Rect()
        if (background !== appliedBackground || appliedBlurBounds != bounds || !hasLayerBounds(background, bounds)) {
            // Keep the native outline concentric with the scaled Compose surface too.
            originalBackground.cornerRadius = radiusDp * density *
                minOf(kotlin.math.abs(content.scaleX), kotlin.math.abs(content.scaleY))
            insetLayers(background, bounds)
            appliedBackground = background
            appliedBlurBounds.set(bounds)
        }
        val alpha = (content.alpha.coerceIn(0f, 1f) * 255).toInt()
        if (background.alpha != alpha) background.alpha = alpha
    }

    /**
     * DecorView.getBackground() deliberately returns the original drawable, hiding its
     * generated blur layer. Drawable callbacks expose the containing wrappers through
     * public APIs, so follow those wrappers instead of reflecting into DecorView.
     */
    private fun windowBackground(): Drawable {
        val callback = originalBackground.callback
        var current: Drawable = when (callback) {
            is Drawable -> callback
            null -> generatedBackground ?: originalBackground
            else -> originalBackground
        }
        repeat(8) {
            val parent = current.callback as? Drawable ?: return current
            if (parent === current) return current
            current = parent
        }
        return current
    }

    private fun insetLayers(drawable: Drawable, rect: Rect) {
        when (drawable) {
            is InsetDrawable -> drawable.drawable?.let { insetLayers(it, rect) }
            is LayerDrawable -> {
                val parent = drawable.bounds
                for (index in 0 until drawable.numberOfLayers) {
                    drawable.setLayerInset(index,
                        (rect.left - parent.left).coerceAtLeast(0),
                        (rect.top - parent.top).coerceAtLeast(0),
                        (parent.right - rect.right).coerceAtLeast(0),
                        (parent.bottom - rect.bottom).coerceAtLeast(0))
                    // Insets persist through a future native background bounds reset. Assign
                    // child bounds as well so this frame doesn't wait for a second layout.
                    drawable.getDrawable(index).bounds = rect
                }
            }
        }
    }

    private fun hasLayerBounds(drawable: Drawable, rect: Rect): Boolean = when (drawable) {
        is InsetDrawable -> drawable.drawable?.let { hasLayerBounds(it, rect) } ?: true
        is LayerDrawable -> (0 until drawable.numberOfLayers).all { drawable.getDrawable(it).bounds == rect }
        else -> true
    }

    fun setBlurEnabled(enabled: Boolean) {
        requestedBlur = enabled
        val radius = if (enabled && Build.VERSION.SDK_INT >= 31) (24f * density).toInt() else 0
        if (radius != appliedBlur) {
            if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(radius)
            appliedBlur = radius
            appliedBackground = null
        }
        reportBlur()
    }

    private fun reportBlur() {
        val active = requestedBlur && blurAvailable && Build.VERSION.SDK_INT >= 31
        if (reportedBlur != active) {
            reportedBlur = active
            onBlurChanged?.invoke(active)
        }
    }

    fun setInputEnabled(enabled: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (inputEnabled == enabled && (window.attributes.flags and flag == 0) == enabled) return
        inputEnabled = enabled
        if (enabled) window.clearFlags(flag) else window.addFlags(flag)
    }

    fun setTouchEnabled(enabled: Boolean) {
        if (touchEnabled == enabled) return
        touchEnabled = enabled
        if (Build.VERSION.SDK_INT >= 33) updateInputRegion()
        else if (enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        else window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    fun setPassThrough(enabled: Boolean) {
        if (passThrough == enabled) return
        // Before API33 a fullscreen modal cannot expose a bounded public touch region.
        // Its Activity fallback stays modal rather than intercepting an entire hidden screen.
        if (enabled && fixed && !supportsFixedViewport) return
        passThrough = enabled
        lastRegion = null
        updateInputRegion()
    }

    fun close() {
        root.removeOnLayoutChangeListener(layoutListener)
        content.removeOnLayoutChangeListener(layoutListener)
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(preDraw)
        ViewCompat.setOnApplyWindowInsetsListener(root, null)
        ViewCompat.setWindowInsetsAnimationCallback(root, null)
        if (Build.VERSION.SDK_INT >= 31) manager.removeCrossWindowBlurEnabledListener(blurListener)
        generatedBackground = null
    }
}
