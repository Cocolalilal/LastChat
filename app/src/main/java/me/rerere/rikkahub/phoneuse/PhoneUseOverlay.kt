package me.rerere.rikkahub.phoneuse

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.activity.*
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

/** Normal assist remains usable over LastChat; phone controls yield to its in-app UI. */
internal fun shouldShowAssistantOverlay(
    presentation: AssistantPresentation,
    otherAppUiVisible: Boolean,
    capturing: Boolean,
): Boolean = !capturing && when (presentation) {
    AssistantPresentation.NORMAL -> true
    AssistantPresentation.PHONE -> !otherAppUiVisible
    AssistantPresentation.HIDDEN, AssistantPresentation.CLOSED -> false
}

/** Android 13+ keeps a fixed buffer; every animated coordinate belongs to the child View. */
internal class PhoneUseOverlay(private val service: AccessibilityService, private val manager: PhoneUseManager) {
    private val legacy = if (Build.VERSION.SDK_INT < 33) LegacyPhoneUseOverlay(service, manager) else null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windows = service.getSystemService(WindowManager::class.java)
    private val settings = GlobalContext.get().get<SettingsStore>()
    private val chat by lazy { GlobalContext.get().get<ChatService>() }
    private val density = service.resources.displayMetrics.density
    private var host: ComponentDialog? = null
    private var viewport: FloatingOverlayViewport? = null
    private var assistant: AssistantOverlaySession? = null
    private var content: View? = null
    private var decoration: View? = null
    private var contentLayoutListener: View.OnLayoutChangeListener? = null
    private var popupScene: PopupScene? = null
    private var heldConversation: Uuid? = null
    private var visible = false
    private var captureHidden = false
    private var inputRequested = false
    private var returnJob: Job? = null
    private var xMotion: SpringAnimation? = null
    private var yMotion: SpringAnimation? = null
    private var position = PhoneOverlayPositionState(initialY = 24f * density)
    private val drag = PhoneOverlayDragCallbacks(::beginDrag, ::dragBy, ::releaseDrag)

    fun start() {
        legacy?.let { it.start(); return }
        manager.assistantOverlayHostReady.value = true
        scope.launch {
            val assistantMode = manager.assistantOverlay.flatMapLatest { scene ->
                scene?.presentation?.map { scene to it } ?: flowOf(null to AssistantPresentation.HIDDEN)
            }
            val visibility = combine(
                manager.appUiVisible, manager.otherAppUiVisible, manager.capturing, manager.acting,
            ) {
                app, other, capture, acting -> Visibility(app, other, capture, acting)
            }
            combine(manager.session, assistantMode, visibility, settings.settingsFlow) {
                    task, scene, state, preferences ->
                State(task, scene.first, scene.second, state, preferences.displaySetting.enableBlurEffect)
            }.collect(::render)
        }
    }

    private fun render(state: State) {
        val task = state.task
        val scene = state.assistant?.takeIf { scene ->
            state.mode != AssistantPresentation.CLOSED &&
                (state.mode != AssistantPresentation.HIDDEN || task != null || assistant === scene) &&
                (task == null ||
                (task.systemAssistant && (scene.viewModel.conversationId.value == task.conversationId ||
                    scene.viewModel.conversationId.value == null)))
        }
        if (scene == null && task == null) {
            popupScene?.setPresented(false)
            show(false, capture = false)
            if (assistant != null) removeHost()
            releaseReference()
            return
        }
        if (task?.conversationId != heldConversation) {
            releaseReference()
            heldConversation = task?.conversationId
            heldConversation?.let(chat::addConversationReference)
        }
        if (scene != null) {
            if (assistant !== scene || host == null) mountAssistant(scene)
            val normal = state.mode == AssistantPresentation.NORMAL
            val show = shouldShowAssistantOverlay(state.mode, state.visibility.other, state.visibility.capture)
            viewport?.setInputEnabled(show && normal && !scene.modalActive)
            viewport?.setTouchEnabled(show && !state.visibility.acting && !scene.modalActive)
            viewport?.setBlurEnabled(state.blur)
            show(show, state.visibility.capture)
        } else {
            if (assistant != null || host == null) mountPopup()
            val show = shouldShowPhoneOverlay(task, state.visibility.app, state.visibility.capture)
            if (task?.running == true || !show) inputRequested = false
            viewport?.setInputEnabled(show && inputRequested)
            viewport?.setTouchEnabled(show && !state.visibility.acting)
            viewport?.setBlurEnabled(state.blur)
            show(show, state.visibility.capture)
        }
        content?.let(::publishControlsReady)
        if (task != null && shouldReturnToAssistant(task) && manager.claimAssistantReturn(task.conversationId)) {
            openConversation(task, pause = false)
        }
    }

    private fun mountAssistant(scene: AssistantOverlaySession) {
        removeHost()
        assistant = scene
        scene.setOverlayWindowManager(windows)
        mount(scene.view, assistantMode = true)
        val window = host?.window ?: return
        scene.nativeBlurActive = false
        scene.bindWindow(window)
        scene.onModalInputChanged = { modal ->
            viewport?.setInputEnabled(visible && scene.presentation.value == AssistantPresentation.NORMAL && !modal)
            viewport?.setTouchEnabled(visible && !manager.acting.value && !modal)
        }
        scene.onBlurRequested = { viewport?.setBlurEnabled(it) }
        viewport?.onBlurChanged = { scene.nativeBlurActive = it; manager.overlayBlur.value = it }
    }

    private fun mountPopup() {
        removeHost()
        val scene = PopupScene()
        popupScene = scene
        mount(scene.view, assistantMode = false)
        manager.overlayBlur.value = false
        viewport?.onBlurChanged = { manager.overlayBlur.value = it }
    }

    private fun mount(view: View, assistantMode: Boolean) {
        (view.parent as? ViewGroup)?.removeView(view)
        content = view
        visible = false
        val root = FrameLayout(service).apply { clipChildren = false; clipToPadding = false }
        val container = FrameLayout(service).apply { clipChildren = false; clipToPadding = false }
        decoration = if (assistantMode) assistant?.decorationView else popupScene?.decorationView
        decoration?.let { background ->
            (background.parent as? ViewGroup)?.removeView(background)
            container.addView(background, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        container.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lp = FrameLayout.LayoutParams(
            if (assistantMode) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or if (assistantMode) Gravity.CENTER_HORIZONTAL else Gravity.RIGHT,
        )
        if (assistantMode) lp.setMargins((16*density).toInt(), 0, (16*density).toInt(), (24*density).toInt())
        root.addView(view, lp)
        val dialog = ComponentDialog(service, R.style.Theme_Rikkahub_PhoneOverlay).apply {
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window!!.apply {
                setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                setFormat(PixelFormat.TRANSLUCENT)
                addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
            setContentView(container)
        }
        host = dialog
        val bounds = FloatingOverlayViewport(dialog.window!!, root, view, passThrough = true,
            radiusDp = if (assistantMode) 28f else 36f)
        viewport = bounds
        bounds.onAvailableFrameChanged = { if (!assistantMode) updatePosition() }
        contentLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (content === view) {
                if (!assistantMode) updatePosition()
                publishControlsReady(view)
            }
        }.also(view::addOnLayoutChangeListener)
        view.visibility = View.INVISIBLE
        if (!assistantMode) view.alpha = 0f
        runCatching {
            dialog.show()
            bounds.attach()
            if (!assistantMode) updatePosition()
        }.onFailure { error ->
            PlatformLog.e("PhoneUseOverlay", "Unable to mount phone controls: ${error.stackTraceToString()}")
            removeHost()
            manager.stop()
        }
    }

    private fun publishControlsReady(view: View) {
        // Dialog.show() can return before its first layout. Tools must wait for a real,
        // measured control surface, and layout must publish readiness without another task event.
        if (content !== view || host == null || !view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
        manager.session.value?.let { manager.controlsReady.value = it.conversationId }
    }

    private fun show(show: Boolean, capture: Boolean) {
        val view = content ?: return
        if (assistant != null) {
            visible = show && !capture
            val scene = assistant
            manager.assistantReplyVisible.value = if (visible &&
                scene?.presentation?.value == AssistantPresentation.NORMAL) {
                scene.viewModel.conversationId.value
            } else null
            view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            assistant?.setPresented(visible)
            viewport?.syncGeometry()
            return
        }
        if (capture) {
            view.animate().cancel()
            view.alpha = 0f
            decoration?.alpha = 0f
            view.visibility = View.INVISIBLE
            viewport?.syncGeometry()
            captureHidden = true
            visible = false
            assistant?.setPresented(false)
            popupScene?.setPresented(false)
            return
        }
        if (show == visible) return
        visible = show
        view.animate().cancel()
        if (show) {
            assistant?.setPresented(true)
            popupScene?.setPresented(true)
            view.visibility = View.VISIBLE
            if (captureHidden) {
                view.alpha = 1f
                decoration?.alpha = 1f
            } else view.animate().alpha(1f).setDuration(180)
                .setUpdateListener { decoration?.alpha = view.alpha }.start()
            captureHidden = false
        } else {
            viewport?.setTouchEnabled(false)
            view.animate().alpha(0f).setDuration(160)
                .setUpdateListener { decoration?.alpha = view.alpha }.withEndAction {
                if (!visible) {
                    view.visibility = View.INVISIBLE
                    assistant?.setPresented(false)
                    popupScene?.setPresented(false)
                    viewport?.syncGeometry()
                    if (manager.session.value == null && manager.assistantOverlay.value?.presentation?.value !in
                        setOf(AssistantPresentation.NORMAL, AssistantPresentation.PHONE)) removeHost()
                }
            }.start()
        }
        viewport?.syncGeometry()
    }

    private fun updatePosition() {
        val view = content ?: return
        val frame = viewport?.availableFrame ?: return
        if (frame.isEmpty || view.width == 0 || view.height == 0) return
        val expandedWidth = (service.resources.configuration.screenWidthDp - 32).coerceIn(104, 336)
        val widthProgress = ((view.width / density - 120f) / (expandedWidth - 104).coerceAtLeast(1))
            .coerceIn(0f, 1f)
        viewport?.setCornerRadiusDp(36f + 8f * widthProgress)
        position.updateBounds(phoneOverlayBounds(frame.width(), frame.height(), view.width, view.height, 16f*density))
        applyPosition()
    }
    private fun applyPosition() {
        content?.translationX = -position.x
        content?.translationY = -position.y
        viewport?.syncGeometry()
    }
    private fun beginDrag() { cancelMotion(); position.beginDrag() }
    private fun dragBy(dx: Float, dy: Float) { position.dragBy(dx, dy); applyPosition() }
    private fun releaseDrag(vx: Float, vy: Float) {
        position.endDrag()
        val bounds = position.bounds ?: return
        val target = phoneOverlaySnap(position.x, position.y, -vx, -vy, bounds, 48f*density)
        if (drag.reduceMotion) { position.moveTo(target.x, target.y); applyPosition(); return }
        val spanY = (bounds.maxY - bounds.minY).coerceAtLeast(1f)
        val spanX = bounds.maxX.coerceAtLeast(1f)
        xMotion = spring(position.x/spanX, target.x/spanX, -vx/spanX) { fraction ->
            position.bounds?.let { position.moveTo(fraction*it.maxX, position.y); applyPosition() }
        }
        yMotion = spring((position.y-bounds.minY)/spanY, (target.y-bounds.minY)/spanY, -vy/spanY) { fraction ->
            position.bounds?.let { position.moveTo(position.x, it.minY+fraction*(it.maxY-it.minY)); applyPosition() }
        }
    }
    private fun spring(from: Float, to: Float, velocity: Float, update: (Float)->Unit): SpringAnimation =
        SpringAnimation(FloatValueHolder(from.coerceIn(0f,1f))).apply {
            spring = SpringForce(to.coerceIn(0f, 1f))
                .setStiffness(PHONE_MOTION_STIFFNESS)
                .setDampingRatio(PHONE_MOTION_DAMPING)
            setStartVelocity(velocity.coerceIn(-20f,20f))
            setMinValue(0f); setMaxValue(1f); setMinimumVisibleChange(0.001f)
            addUpdateListener { _, value, _ -> update(value) }
            start()
        }
    private fun cancelMotion() { xMotion?.cancel(); yMotion?.cancel(); xMotion = null; yMotion = null }

    private fun openConversation(task: PhoneUseTask, pause: Boolean = true) {
        if (pause && task.running && !task.paused && !task.awaitingReply) manager.togglePause()
        if (task.systemAssistant) manager.prepareAssistantReturn(task.conversationId)
        returnJob?.cancel()
        returnJob = scope.launch {
            val target = if (task.systemAssistant) AssistantOverlayActivity::class.java else RouteActivity::class.java
            repeat(2) { attempt ->
                if (task.systemAssistant) manager.assistantReturning.value = true
                val launched = runCatching {
                    service.startActivity(Intent(service, target).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        if (task.systemAssistant) addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        putExtra(RouteActivity.EXTRA_CONVERSATION_ID, task.conversationId.toString())
                        putExtra(RouteActivity.EXTRA_ASSISTANT_ID, task.assistantId.toString())
                    })
                }.isSuccess
                if (!task.systemAssistant) return@launch
                delay(if (attempt == 0) 800 else 1200)
                if (manager.assistantOverlay.value?.presentation?.value == AssistantPresentation.NORMAL) return@launch
                if (!launched && attempt == 1) manager.markReturnFailed(task.conversationId)
                if (manager.session.value?.conversationId != task.conversationId) return@launch
            }
            manager.markReturnFailed(task.conversationId)
            // Keep the same scene as the reply fallback when the OS denies background launch.
            assistant?.setPhoneMode(false)
        }
    }

    fun onConfigurationChanged() {
        legacy?.let { it.onConfigurationChanged(); return }
        content?.post { updatePosition(); viewport?.syncGeometry() }
    }
    private fun releaseReference() { heldConversation?.let(chat::removeConversationReference); heldConversation = null }
    private fun removeHost() {
        // A retiring host must not keep driving the next host's decoration alpha.
        content?.animate()?.setUpdateListener(null)?.cancel()
        manager.controlsReady.value = null
        manager.assistantReplyVisible.value = null
        contentLayoutListener?.let { content?.removeOnLayoutChangeListener(it) }
        contentLayoutListener = null
        cancelMotion()
        viewport?.close(); viewport = null
        assistant?.onModalInputChanged = {}
        assistant?.setPresented(false)
        assistant?.bindWindow(null)
        assistant?.onBlurRequested = {}
        assistant?.setOverlayWindowManager(null)
        assistant = null
        (content?.parent as? ViewGroup)?.removeView(content)
        (decoration?.parent as? ViewGroup)?.removeView(decoration)
        decoration = null
        popupScene?.close(); popupScene = null
        host?.dismiss(); host = null
        content = null
        visible = false
    }
    fun close() {
        legacy?.let { it.close(); return }
        manager.assistantOverlayHostReady.value = false
        returnJob?.cancel()
        removeHost()
        manager.assistantOverlay.value?.close()
        releaseReference()
        scope.cancel()
    }

    private inner class PopupScene : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        private val saved = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = saved.savedStateRegistry
        private val composeScope = CoroutineScope(SupervisorJob()+AndroidUiDispatcher.CurrentThread)
        private val recomposer = Recomposer(composeScope.coroutineContext)
        val view = ComposeView(service)
        val decorationView = ComposeView(service).apply {
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        init {
            saved.performAttach(); saved.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
            listOf(view, decorationView).forEach { child ->
                child.setViewTreeLifecycleOwner(this)
                child.setViewTreeSavedStateRegistryOwner(this)
                child.setParentCompositionContext(recomposer)
                child.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            }
            composeScope.launch { recomposer.runRecomposeAndApplyChanges() }
            decorationView.setContent {
                val preferences by settings.settingsFlow.collectAsStateWithLifecycle()
                val task by manager.session.collectAsStateWithLifecycle()
                val wave by me.rerere.rikkahub.service.assist.AssistScreenHolder.screenReadSignal
                    .collectAsStateWithLifecycle()
                val assistant = preferences.assistants.firstOrNull { it.id == task?.assistantId }
                    ?: preferences.assistants.firstOrNull() ?: return@setContent
                me.rerere.rikkahub.ui.theme.RikkahubTheme {
                    me.rerere.rikkahub.ui.theme.AssistantChatTheme(assistant) {
                        val config = preferences.assistantOverlayConfig
                        SilkGlowLayer(active = task?.let { it.running && !it.paused } == true,
                            waveSignal = wave, originX = config.waveOriginX, originY = config.waveOriginY,
                            modifier = androidx.compose.ui.Modifier.fillMaxSize())
                    }
                }
            }
            view.setContent {
                PhoneUseOverlayContent(manager, settings, { openConversation(it) }, drag) { enabled ->
                    inputRequested = enabled
                    viewport?.setInputEnabled(enabled && visible)
                }
            }
        }
        fun setPresented(show: Boolean) {
            decorationView.visibility = if (show) View.VISIBLE else View.INVISIBLE
            if (show) { recomposer.resumeCompositionFrameClock(); registry.currentState = Lifecycle.State.RESUMED }
            else { registry.currentState = Lifecycle.State.CREATED; recomposer.pauseCompositionFrameClock() }
        }
        fun close() {
            decorationView.disposeComposition()
            view.disposeComposition(); recomposer.cancel(); composeScope.cancel()
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
    private data class Visibility(val app: Boolean, val other: Boolean, val capture: Boolean, val acting: Boolean)
    private data class State(val task: PhoneUseTask?, val assistant: AssistantOverlaySession?,
        val mode: AssistantPresentation, val visibility: Visibility, val blur: Boolean)
}
