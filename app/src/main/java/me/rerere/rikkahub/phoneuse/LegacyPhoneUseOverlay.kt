package me.rerere.rikkahub.phoneuse

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.app.Dialog
import android.os.Build
import android.view.WindowInsets
import android.view.Choreographer
import java.util.function.Consumer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.activity.AssistantOverlayActivity
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

internal fun shouldShowPhoneOverlay(task: PhoneUseTask?, appVisible: Boolean, capturing: Boolean): Boolean =
    task != null && !appVisible && !capturing && !(task.systemAssistant && task.resultPresented)

internal fun shouldReturnToAssistant(task: PhoneUseTask): Boolean =
    task.systemAssistant && (task.awaitingReply || !task.running) && !task.resultPresented

internal fun clampPhoneOverlayOffset(
    x: Int, y: Int, frameWidth: Int, frameHeight: Int, contentWidth: Int, contentHeight: Int,
): Pair<Int, Int> = x.coerceIn(0, (frameWidth - contentWidth).coerceAtLeast(0)) to
    y.coerceIn(0, (frameHeight - contentHeight).coerceAtLeast(0))

/** Gestures pass through the existing window. Only a real screenshot hides it. */
internal class LegacyPhoneUseOverlay(
    private val service: AccessibilityService,
    private val manager: PhoneUseManager,
) : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedState.savedStateRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var popup: Dialog? = null
    private var currentSystemMode: Boolean? = null
    private var inputRequested = false
    private var removing = false
    private var hiddenForCapture = false
    private var returnJob: Job? = null
    private var xMotion: SpringAnimation? = null
    private var yMotion: SpringAnimation? = null
    private var offsetX = 0f
    private var offsetY = 24f * service.resources.displayMetrics.density
    private var lastBounds: PhoneOverlayBounds? = null
    private var positionPosted = false
    private var paramsDirty = false
    private var requestedBlur = false
    private var appliedBlurRadius = -1
    private var blurAvailable = false
    private val frameCallback = Choreographer.FrameCallback {
        positionPosted = false
        applyPositionNow()
    }
    private val blurListener = Consumer<Boolean> {
        blurAvailable = it
        updateFlags(manager.acting.value)
    }
    private var anchor: PhoneOverlaySnap? = null
    private val dragCallbacks = PhoneOverlayDragCallbacks(::beginDrag, ::drag, ::releaseDrag)
    private var heldConversation: Uuid? = null
    private val chatService by lazy { GlobalContext.get().get<ChatService>() }

    fun start() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        val settingsStore = GlobalContext.get().get<SettingsStore>()
        if (Build.VERSION.SDK_INT >= 31) {
            blurAvailable = windows.isCrossWindowBlurEnabled
            windows.addCrossWindowBlurEnabledListener(service.mainExecutor, blurListener)
        }
        scope.launch {
            val blockingUi = combine(manager.appUiVisible, manager.otherAppUiVisible, manager.assistantReturning) {
                    visible, otherVisible, returning -> visible && (otherVisible || !returning)
            }
            combine(manager.session, manager.acting, manager.capturing, blockingUi, settingsStore.settingsFlow) {
                    task, acting, capturing, appVisible, settings ->
                OverlayState(task, acting, capturing, appVisible, settings.displaySetting.enableBlurEffect)
            }.collect { (task, acting, capturing, appVisible, blur) ->
                requestedBlur = task != null && blur
                if (task != null && heldConversation != task.conversationId) {
                    heldConversation?.let(chatService::removeConversationReference)
                    heldConversation = task?.conversationId
                    heldConversation?.let(chatService::addConversationReference)
                }
                if (task == null) {
                    fadeOutAndRemove()
                    return@collect
                }
                if (removing) {
                    removing = false
                    view?.animate()?.cancel()
                    view?.animate()?.alpha(1f)?.setDuration(180)?.start()
                }
                if (task.running || appVisible || capturing) inputRequested = false
                if (view == null || currentSystemMode != task.systemAssistant) {
                    remove()
                    currentSystemMode = task.systemAssistant
                    val content = ComposeView(service).apply {
                        // Visibility belongs to the dialog decor, including its native blur background.
                        visibility = View.VISIBLE
                        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> onContentSizeChanged() }
                        setViewTreeLifecycleOwner(this@LegacyPhoneUseOverlay)
                        setViewTreeSavedStateRegistryOwner(this@LegacyPhoneUseOverlay)
                        setContent {
                            PhoneUseOverlayContent(manager, settingsStore, { openConversation(it) }, dragCallbacks, ::setInputEnabled)
                        }
                    }
                    val host = createPhoneOverlayDialog(service, content, if (task.systemAssistant) 28f else 36f)
                    appliedBlurRadius = if (requestedBlur && Build.VERSION.SDK_INT >= 31) {
                        (24f * service.resources.displayMetrics.density).toInt()
                    } else 0
                    if (Build.VERSION.SDK_INT >= 31) host.window!!.setBackgroundBlurRadius(appliedBlurRadius)
                    manager.overlayBlur.value = appliedBlurRadius > 0 && blurAvailable
                    runCatching {
                        host.show()
                        host.window!!.decorView.visibility = View.INVISIBLE
                        val params = host.window!!.attributes
                        params.width = if (task.systemAssistant) {
                            (usableFrame().first - 32f * service.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                        } else WindowManager.LayoutParams.WRAP_CONTENT
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        params.gravity = Gravity.BOTTOM or Gravity.RIGHT
                        params.x = if (task.systemAssistant) (16f * service.resources.displayMetrics.density).toInt() else 0
                        params.y = (24f * service.resources.displayMetrics.density).toInt()
                        offsetX = params.x.toFloat()
                        offsetY = params.y.toFloat()
                        host.window!!.attributes = params
                    }.onSuccess { popup = host; view = content }
                        .onFailure { host.dismiss(); content.disposeComposition(); manager.stop() }
                }
                if (appVisible || capturing) cancelMotion()
                updateFlags(acting)
                val decor = popup?.window?.decorView
                val show = shouldShowPhoneOverlay(task, appVisible, capturing)
                if (show && decor?.visibility != View.VISIBLE) {
                    val matchingHandoff = task.systemAssistant && manager.assistantDocked.value == task.conversationId
                    if (!matchingHandoff && !hiddenForCapture && !dragCallbacks.reduceMotion) {
                        view?.alpha = 0f
                        view?.animate()?.alpha(1f)?.setDuration(180)?.start()
                    } else view?.alpha = 1f
                    hiddenForCapture = false
                }
                decor?.visibility = if (show) View.VISIBLE else View.INVISIBLE
                if (capturing) hiddenForCapture = true
                if (appVisible) hiddenForCapture = false
                if (view?.isAttachedToWindow == true) manager.controlsReady.value = task.conversationId

                if (shouldReturnToAssistant(task) &&
                    manager.claimAssistantReturn(task.conversationId)) {
                    if (!appVisible) {
                        openConversation(task, pause = false)
                    }
                }

            }
        }
    }

    private fun openConversation(task: PhoneUseTask, pause: Boolean = true) {
        if (pause && task.running && !task.paused && !task.awaitingReply) manager.togglePause()
        if (task.systemAssistant) manager.prepareAssistantReturn(task.conversationId)
        returnJob?.cancel()
        returnJob = scope.launch {
            if (task.systemAssistant) {
                combine(manager.overlayCompactReady, manager.overlayInputHidden) { compact, hidden -> compact && hidden }
                    .first { it }
                val bounds = motionBounds() ?: return@launch
                val dock = assistantDockAnchor(bounds, service.resources.displayMetrics.density)
                anchor = dock
                if (dragCallbacks.reduceMotion) {
                    offsetX = dock.x; offsetY = dock.y; applyPosition()
                } else {
                    xMotion = retargetAxis(xMotion, bounds.clampX(offsetX), dock.x, 0f, bounds.maxX, followSize = false) {
                        offsetX = it; applyPosition()
                    }
                    yMotion = retargetAxis(yMotion, bounds.clampY(offsetY), dock.y, bounds.minY, bounds.maxY, followSize = false) {
                        offsetY = it; applyPosition()
                    }
                    while (xMotion?.isRunning == true || yMotion?.isRunning == true) awaitFrame()
                }
                // Publish the final native position before replacing its surface with the activity.
                awaitFrame()
                if (manager.session.value?.conversationId != task.conversationId || manager.appUiVisible.value) return@launch
                manager.assistantReturning.value = true
            }
            val target = if (task.systemAssistant) AssistantOverlayActivity::class.java else RouteActivity::class.java
            runCatching {
                service.startActivity(Intent(service, target).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    if (task.systemAssistant) addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra(RouteActivity.EXTRA_CONVERSATION_ID, task.conversationId.toString())
                    putExtra(RouteActivity.EXTRA_ASSISTANT_ID, task.assistantId.toString())
                })
            }.onFailure { manager.markReturnFailed(task.conversationId) }
            if (task.systemAssistant) {
                delay(1200)
                if (!manager.appUiVisible.value && manager.session.value?.conversationId == task.conversationId) {
                    manager.markReturnFailed(task.conversationId)
                }
            }
        }
    }

    private fun setInputEnabled(enabled: Boolean) {
        inputRequested = enabled
        updateFlags(manager.acting.value)
    }

    private fun updateFlags(acting: Boolean) {
        val window = popup?.window ?: return
        val params = window.attributes
        val blurRadius = if (requestedBlur && Build.VERSION.SDK_INT >= 31) {
            (24f * service.resources.displayMetrics.density).toInt()
        } else 0
        val managedFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        val flags = (params.flags and managedFlags.inv()) or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (inputRequested) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            (if (acting) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)
        val radiusChanged = appliedBlurRadius != blurRadius
        if (params.flags != flags || radiusChanged) {
            params.flags = flags
            if (Build.VERSION.SDK_INT >= 31 && radiusChanged) window.setBackgroundBlurRadius(blurRadius)
            appliedBlurRadius = blurRadius
            paramsDirty = true
            applyPosition()
        }
        manager.overlayBlur.value = blurRadius > 0 && blurAvailable
    }

    private fun cancelMotion() {
        xMotion?.cancel(); yMotion?.cancel()
        xMotion = null; yMotion = null
    }

    private fun beginDrag() {
        cancelMotion()
        anchor = null
    }

    private fun drag(dx: Float, dy: Float) {
        val bounds = motionBounds() ?: return
        offsetX = bounds.clampX(offsetX - dx)
        offsetY = bounds.clampY(offsetY - dy)
        applyPosition()
    }

    private fun releaseDrag(vx: Float, vy: Float) {
        val bounds = motionBounds() ?: return
        val density = service.resources.displayMetrics.density
        val velocityX = (if (vx.isFinite()) -vx else 0f).coerceIn(-8000f * density, 8000f * density)
        val velocityY = (if (vy.isFinite()) -vy else 0f).coerceIn(-8000f * density, 8000f * density)
        val target = if (currentSystemMode == true && manager.session.value?.returnRequested == true) {
            assistantDockAnchor(bounds, density)
        } else if (currentSystemMode == true) {
            PhoneOverlaySnap(16f * density, bounds.clampY(offsetY + velocityY * 0.28f),
                PhoneOverlaySide.RIGHT, null)
        } else phoneOverlaySnap(offsetX, offsetY, velocityX, velocityY, bounds, 48f * density)
        anchor = target
        if (dragCallbacks.reduceMotion) {
            offsetX = target.x; offsetY = target.y
            applyPosition()
            return
        }
        xMotion = springAxis(offsetX, target.x, velocityX, 0f, bounds.maxX) {
            offsetX = it; applyPosition()
        }
        yMotion = springAxis(offsetY, target.y, velocityY, bounds.minY, bounds.maxY) {
            offsetY = it; applyPosition()
        }
    }

    private fun springAxis(
        from: Float, target: Float, velocity: Float, min: Float, max: Float, update: (Float) -> Unit,
    ): SpringAnimation? {
        if (max <= min || (kotlin.math.abs(from - target) < 0.5f && kotlin.math.abs(velocity) < 8f)) {
            update(target)
            return null
        }
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = SpringForce(target).setStiffness(PHONE_MOTION_STIFFNESS).setDampingRatio(PHONE_MOTION_DAMPING)
            setStartVelocity(velocity)
            setMinValue(min); setMaxValue(max)
            setMinimumVisibleChange(0.5f)
            addUpdateListener { _, value, _ -> update(value) }
            start()
        }
    }

    private fun motionBounds(): PhoneOverlayBounds? {
        val content = view ?: return null
        val (width, height) = usableFrame()
        return phoneOverlayBounds(width, height, content.width, content.height,
            (if (currentSystemMode == true) 24f else 16f) * service.resources.displayMetrics.density)
    }

    private fun usableFrame(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return (metrics.bounds.width() - insets.left - insets.right) to
                (metrics.bounds.height() - insets.top - insets.bottom)
        }
        return service.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }

    fun onConfigurationChanged() {
        view?.post {
            if (currentSystemMode == true) popup?.window?.let { window ->
                window.attributes.width = (usableFrame().first - 32f * service.resources.displayMetrics.density)
                    .toInt().coerceAtLeast(1)
                paramsDirty = true
            }
            lastBounds = null
            onContentSizeChanged()
            applyPosition()
        }
    }

    private fun onContentSizeChanged() {
        val bounds = motionBounds() ?: return
        if (lastBounds == bounds) return
        lastBounds = bounds
        val dock = anchor
        val targetX = when (dock?.side) {
            PhoneOverlaySide.LEFT -> bounds.maxX
            PhoneOverlaySide.RIGHT -> if (currentSystemMode == true) 16f * service.resources.displayMetrics.density else 0f
            null -> bounds.clampX(offsetX)
        }
        val targetY = when (dock?.vertical) {
            PhoneOverlayVerticalEdge.TOP -> bounds.maxY
            PhoneOverlayVerticalEdge.BOTTOM -> bounds.minY
            null -> bounds.clampY(offsetY)
        }
        // A size change must not cancel an in-flight throw and teleport to its final anchor.
        if (dock != null && !dragCallbacks.reduceMotion) {
            xMotion = retargetAxis(xMotion, bounds.clampX(offsetX), targetX, 0f, bounds.maxX) {
                offsetX = it; applyPosition()
            }
            yMotion = retargetAxis(yMotion, bounds.clampY(offsetY), targetY, bounds.minY, bounds.maxY) {
                offsetY = it; applyPosition()
            }
        } else {
            offsetX = bounds.clampX(offsetX)
            offsetY = bounds.clampY(offsetY)
            applyPosition()
        }
    }

    private fun retargetAxis(
        motion: SpringAnimation?, from: Float, target: Float, min: Float, max: Float,
        followSize: Boolean = true, update: (Float) -> Unit,
    ): SpringAnimation? {
        if (motion?.isRunning == true && max > min) {
            motion.setMinValue(min).setMaxValue(max)
            motion.animateToFinalPosition(target)
            return motion
        }
        // Follow a morphing edge directly; its size is already spring-driven.
        if (followSize && kotlin.math.abs(from - target) < 32f * service.resources.displayMetrics.density) {
            update(target)
            return null
        }
        return springAxis(from, target, 0f, min, max, update)
    }

    private fun applyPosition() {
        if (!positionPosted && view != null) {
            positionPosted = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    private fun applyPositionNow() {
        val window = popup?.window ?: return
        val bounds = motionBounds() ?: return
        val params = window.attributes
        val x = bounds.clampX(offsetX).roundToInt()
        val y = bounds.clampY(offsetY).roundToInt()
        if (params.x == x && params.y == y && !paramsDirty) return
        paramsDirty = false
        params.x = x; params.y = y
        runCatching { window.attributes = params }
    }

    private fun fadeOutAndRemove() {
        if (removing) return
        val content = view
        if (content == null || dragCallbacks.reduceMotion) {
            remove()
            releaseConversation()
            return
        }
        removing = true
        cancelMotion()
        content.animate().alpha(0f).setDuration(180).withEndAction {
            if (manager.session.value == null) {
                remove()
                releaseConversation()
            }
        }.start()
    }

    private fun releaseConversation() {
        heldConversation?.let(chatService::removeConversationReference)
        heldConversation = null
    }

    private fun remove() {
        removing = false
        hiddenForCapture = false
        returnJob?.cancel()
        returnJob = null
        view?.animate()?.cancel()
        cancelMotion()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        positionPosted = false
        paramsDirty = false
        lastBounds = null
        manager.overlayBlur.value = false
        manager.overlayCompactReady.value = false
        manager.overlayInputHidden.value = true
        anchor = null
        popup?.let { runCatching { it.dismiss() } }
        view?.disposeComposition()
        popup = null
        appliedBlurRadius = -1
        view = null
        inputRequested = false
        manager.controlsReady.value = null
    }

    fun close() {
        if (Build.VERSION.SDK_INT >= 31) windows.removeCrossWindowBlurEnabledListener(blurListener)
        remove()
        releaseConversation()
        scope.cancel()
        registry.currentState = Lifecycle.State.DESTROYED
    }

    private data class OverlayState(val task: PhoneUseTask?, val acting: Boolean, val capturing: Boolean, val appVisible: Boolean, val blur: Boolean)
}
