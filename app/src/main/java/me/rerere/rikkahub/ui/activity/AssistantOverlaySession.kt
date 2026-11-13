package me.rerere.rikkahub.ui.activity

import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.service.assist.AssistScreenHolder
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.*
import androidx.navigation.NavHostController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.common.platform.PlatformLog
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import me.rerere.rikkahub.phoneuse.PhoneUseManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ui.*
import me.rerere.rikkahub.ui.context.*
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.theme.*
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

internal enum class AssistantPresentation { NORMAL, PHONE, HIDDEN, CLOSED }

/** A single scene, conversation and input owner, independent of the permission Activity. */
internal class AssistantOverlaySession(activity: AssistantOverlayActivity, existingId: Uuid?) :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    override val onBackPressedDispatcher = OnBackPressedDispatcher { close() }
    private val koin = GlobalContext.get()
    private val settings = koin.get<SettingsStore>()
    private val manager = koin.get<PhoneUseManager>()
    private val highlighter = koin.get<Highlighter>()
    private val context = OverlayContext(activity)
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.CurrentThread)
    private val recomposer = Recomposer(scope.coroutineContext)
    private var activityOwner by mutableStateOf(activity)
    val presentation = MutableStateFlow(AssistantPresentation.NORMAL)
    val inputState = ChatInputState()
    var nativeBlurActive by mutableStateOf(false)
    var onBlurRequested: (Boolean) -> Unit = {}
        set(value) {
            field = value
            value(settings.settingsFlow.value.displaySetting.enableBlurEffect)
        }
    var serviceHosted = false
        private set
    private var activityResumed = false
    private var boundWindow: Window? = null
    private val dialogHost = AssistantDialogHost()
    private var glowActive by mutableStateOf(false)
    private var surfaceAlpha = 0f
    private var modalOpacity = 1f
    private var modalAnimator: ValueAnimator? = null
    var modalActive = false
        private set
    var onModalInputChanged: (Boolean) -> Unit = {}
        set(value) {
            field = value
            value(modalActive)
        }
    val viewModel = ViewModelProvider(viewModelStore, object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AssistantOverlayVM(settings, koin.get<ChatService>(), manager) as T
        }
    })[AssistantOverlayVM::class.java]
    val view = ComposeView(context).apply { alpha = 0f }
    // The card wraps content, while the edge effect keeps the full display's coordinates.
    // It is a separate non-interactive child of the same native window, never a backdrop image.
    val decorationView = ComposeView(context).apply {
        alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    init {
        dialogHost.onModalChanged = ::setModalActive
        savedState.performAttach()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        listOf(view, decorationView).forEach { child ->
            child.setViewTreeLifecycleOwner(this)
            child.setViewTreeSavedStateRegistryOwner(this)
            child.setViewTreeViewModelStoreOwner(this)
            child.setViewTreeOnBackPressedDispatcherOwner(this)
            child.setParentCompositionContext(recomposer)
            child.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
        }
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        activity.intent.getStringExtra(RouteActivity.EXTRA_ASSISTANT_ID)?.takeIf { existingId != null }?.let { value ->
            runCatching { Uuid.parse(value) }.getOrNull()?.let { id ->
                if (settings.settingsFlow.value.assistants.any { it.id == id }) viewModel.overlayAssistantId = id
            }
        }
        viewModel.initialize(existingId)
        decorationView.setContent {
            val currentSettings by settings.settingsFlow.collectAsStateWithLifecycle()
            val mode by presentation.collectAsStateWithLifecycle()
            val phoneTask by manager.session.collectAsStateWithLifecycle()
            val conversationId by viewModel.conversationId.collectAsStateWithLifecycle()
            val completedPhone = phoneTask?.let {
                it.systemAssistant && it.conversationId == conversationId && !it.running
            } == true
            val reduced = me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy().reduceMotion
            val completionGlow by animateFloatAsState(
                if (completedPhone && !glowActive) 0f else 1f,
                if (reduced) snap() else tween(240), label = "assistantFinishedGlow",
            )
            val wave by AssistScreenHolder.screenReadSignal.collectAsStateWithLifecycle()
            val assistant = currentSettings.assistants.firstOrNull { it.id == viewModel.overlayAssistantId }
                ?: currentSettings.resolveAssistantOverlayAssistant()
            RikkahubTheme {
                AssistantChatTheme(assistant) {
                    val config = currentSettings.assistantOverlayConfig
                    SilkGlowLayer(
                        active = glowActive || mode == AssistantPresentation.PHONE,
                        waveSignal = wave, originX = config.waveOriginX, originY = config.waveOriginY,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = completionGlow },
                    )
                }
            }
        }
        view.setContent {
            val currentSettings by settings.settingsFlow.collectAsStateWithLifecycle()
            val mode by presentation.collectAsStateWithLifecycle()
            val conversationId by viewModel.conversationId.collectAsStateWithLifecycle()
            val assistant = currentSettings.assistants.firstOrNull { it.id == viewModel.overlayAssistantId }
                ?: currentSettings.resolveAssistantOverlayAssistant()
            val toast = rememberAppToasterState()
            SideEffect { onBlurRequested(currentSettings.displaySetting.enableBlurEffect) }
            RikkahubTheme {
                val nav = remember { NavHostController(context) }
                CompositionLocalProvider(
                    LocalSettings provides currentSettings, LocalHighlighter provides highlighter,
                    LocalToaster provides toast, LocalNavController provides nav,
                    LocalActivityResultRegistryOwner provides activityOwner,
                    LocalAssistantDialogHost provides dialogHost,
                ) {
                    AssistantChatTheme(assistant) {
                        FloatingOverlayLayout(content = {
                            AssistantOverlayScreen(
                                viewModel, onDismiss = { close() }, onOpenInApp = {
                                    val id = conversationId ?: return@AssistantOverlayScreen
                                    runCatching {
                                        val intent = Intent(activityOwner, RouteActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            putExtra(RouteActivity.EXTRA_CONVERSATION_ID, id.toString())
                                            putExtra(RouteActivity.EXTRA_ASSISTANT_ID,
                                                viewModel.overlayAssistantId.toString())
                                        }
                                        activityOwner.startActivity(intent)
                                    }.onSuccess {
                                        manager.releaseAssistantOverlay(id)
                                        close(stopPhoneUse = false)
                                    }.onFailure { error ->
                                        PlatformLog.e("AssistantOverlay",
                                            "Unable to open conversation: ${error.message}")
                                    }
                                },
                                autoStartListening = existingId == null, floatingContent = true,
                                nativeBlurActive = nativeBlurActive, inputState = inputState,
                                onGlowActive = { glowActive = it },
                                persistentPhoneMode = mode == AssistantPresentation.PHONE,
                                onPhoneUseYield = {
                                    setPhoneMode(true)
                                    activityOwner.moveTaskToBack(true)
                                },
                                onSurfaceMotion = { motion ->
                                    surfaceAlpha = motion.alpha
                                    applySurfaceAlpha()
                                    applyDecorationAlpha()
                                    view.pivotX = motion.pivotX * view.width
                                    view.pivotY = motion.pivotY * view.height
                                    view.scaleX = motion.scale
                                    view.scaleY = motion.scale
                                    view.translationX = motion.horizontalDrift * view.width
                                    view.translationY = motion.liftDp * view.resources.displayMetrics.density
                                },
                            )
                        }, decoration = { AppToasterHost(toast) })
                    }
                }
            }
        }
        setPresented(true)
    }

    fun bindActivity(activity: AssistantOverlayActivity) {
        activityOwner = activity
        context.baseContext = activity
    }
    fun openConversation(id: Uuid) {
        val previous = viewModel.conversationId.value
        if (previous == id) return
        if (manager.session.value?.let { it.systemAssistant && it.conversationId == previous } == true) manager.stop()
        inputState.clearInput()
        viewModel.initialize(id)
    }
    fun unbindActivity(activity: AssistantOverlayActivity) {
        if (activityOwner === activity) activityResumed = false
        updateChildVisibility()
    }
    fun onActivityResumed() {
        activityResumed = true
        val task = manager.session.value
        if (presentation.value == AssistantPresentation.PHONE && task?.systemAssistant == true &&
            task.conversationId == viewModel.conversationId.value && task.running &&
            !task.paused && !task.awaitingReply && !task.returnRequested) {
            // A fresh assist gesture takes control of ongoing work. Resuming it from the
            // panel then performs a new compact transition before yielding to the target app.
            manager.togglePause()
        }
        setPhoneMode(false)
        setPresented(true)
        updateChildVisibility()
    }
    fun onActivityPaused() {
        activityResumed = false
        if (presentation.value == AssistantPresentation.NORMAL) {
            presentation.value = AssistantPresentation.HIDDEN
            setPresented(false)
        } else if (!serviceHosted && presentation.value == AssistantPresentation.PHONE) {
            setPresented(false)
        }
        updateChildVisibility()
    }
    fun setPhoneMode(phone: Boolean) {
        if (presentation.value == AssistantPresentation.CLOSED) return
        presentation.value = if (phone) AssistantPresentation.PHONE else AssistantPresentation.NORMAL
        updateChildVisibility()
    }
    fun setPresented(visible: Boolean) {
        if (presentation.value == AssistantPresentation.CLOSED) return
        decorationView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        dialogHost.setVisible(visible && activityResumed && presentation.value == AssistantPresentation.NORMAL)
        if (visible) {
            if (presentation.value == AssistantPresentation.HIDDEN) presentation.value = AssistantPresentation.NORMAL
            recomposer.resumeCompositionFrameClock()
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        } else {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            recomposer.pauseCompositionFrameClock()
        }
    }
    fun bindWindow(window: Window?) {
        boundWindow = window
        dialogHost.bindWindow(window.takeIf { serviceHosted })
        if (android.os.Build.VERSION.SDK_INT >= 33 && window != null) {
            onBackPressedDispatcher.setOnBackInvokedDispatcher(window.onBackInvokedDispatcher)
        }
    }
    fun setOverlayWindowManager(windowManager: WindowManager?) {
        serviceHosted = windowManager != null
        dialogHost.bindWindow(boundWindow.takeIf { serviceHosted })
        updateChildVisibility()
    }
    private fun updateChildVisibility() {
        dialogHost.setVisible(activityResumed && presentation.value == AssistantPresentation.NORMAL)
    }
    fun close(stopPhoneUse: Boolean = true, finishActivity: Boolean = true) {
        if (presentation.value == AssistantPresentation.CLOSED) return
        val id = viewModel.conversationId.value
        if (stopPhoneUse && manager.session.value?.let { it.systemAssistant && it.conversationId == id } == true) {
            manager.stop()
        }
        presentation.value = AssistantPresentation.CLOSED
        if (manager.assistantOverlay.value === this) manager.assistantOverlay.value = null
        modalAnimator?.cancel()
        modalAnimator = null
        dialogHost.close()
        decorationView.disposeComposition()
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        if (finishActivity) activityOwner.finish()
        context.baseContext = activityOwner.applicationContext
    }

    private fun applySurfaceAlpha() {
        view.alpha = surfaceAlpha * modalOpacity
        applyDecorationAlpha()
    }

    private fun applyDecorationAlpha() { decorationView.alpha = surfaceAlpha * modalOpacity }

    private fun setModalActive(active: Boolean) {
        if (presentation.value == AssistantPresentation.CLOSED || modalActive == active) return
        modalActive = active
        // Activity-owned Material dialogs must receive focus and all pointer input. Keep
        // the retained composition alive, fading only the service card and its native blur.
        view.isEnabled = !active
        onModalInputChanged(active)
        modalAnimator?.cancel()
        val target = if (active) 0f else 1f
        if (!view.isShown || presentation.value != AssistantPresentation.NORMAL ||
            !ValueAnimator.areAnimatorsEnabled()) {
            modalAnimator = null
            modalOpacity = target
            applySurfaceAlpha()
            return
        }
        modalAnimator = ValueAnimator.ofFloat(modalOpacity, target).apply {
            duration = 180
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                modalOpacity = it.animatedValue as Float
                applySurfaceAlpha()
            }
            start()
        }
    }

    /** Dialogs stay owned by the current Activity, which also owns their result registry. */
    internal class OverlayContext(base: Context) : MutableContextWrapper(base)
}
