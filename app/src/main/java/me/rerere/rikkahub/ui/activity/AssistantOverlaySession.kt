package me.rerere.rikkahub.ui.activity

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
    private val visibilityOpacity = OverlayOpacity(1f) { applySurfaceAlpha() }
    private val modalOpacity = OverlayOpacity(1f) { applySurfaceAlpha() }
    var isClosing = false
        private set
    private var closeFinishActivity = true
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
        if (isClosing || presentation.value == AssistantPresentation.CLOSED) return
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
        if (isClosing || presentation.value == AssistantPresentation.CLOSED) return
        if (presentation.value == AssistantPresentation.NORMAL) {
            presentation.value = AssistantPresentation.HIDDEN
            setPresented(false)
        } else if (!serviceHosted && presentation.value == AssistantPresentation.PHONE) {
            setPresented(false)
        }
        updateChildVisibility()
    }
    fun setPhoneMode(phone: Boolean) {
        if (isClosing || presentation.value == AssistantPresentation.CLOSED) return
        presentation.value = if (phone) AssistantPresentation.PHONE else AssistantPresentation.NORMAL
        updateChildVisibility()
    }
    fun setPresented(visible: Boolean, immediate: Boolean = false) {
        if (presentation.value == AssistantPresentation.CLOSED) return
        if (isClosing) {
            // Capture/host teardown may force completion, but an ordinary render must never
            // replace the close completion with the callback that merely pauses a hidden scene.
            if (!visible && immediate) visibilityOpacity.animateTo(0f, immediate = true) {
                dispose(closeFinishActivity)
            }
            return
        }
        dialogHost.setVisible(visible && activityResumed && presentation.value == AssistantPresentation.NORMAL)
        if (visible) {
            if (presentation.value == AssistantPresentation.HIDDEN) presentation.value = AssistantPresentation.NORMAL
            view.visibility = View.VISIBLE
            decorationView.visibility = View.VISIBLE
            recomposer.resumeCompositionFrameClock()
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
        visibilityOpacity.animateTo(if (visible) 1f else 0f, immediate) {
            if (!visible) {
                view.visibility = View.INVISIBLE
                decorationView.visibility = View.INVISIBLE
                lifecycleRegistry.currentState = Lifecycle.State.CREATED
                recomposer.pauseCompositionFrameClock()
            }
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
        if (isClosing) {
            if (!finishActivity) {
                // Replacement revokes the old scene's right to finish the shared Activity,
                // including a host handoff that happens before Activity.session is reassigned.
                closeFinishActivity = false
                visibilityOpacity.animateTo(0f, immediate = true) { dispose(false) }
            }
            return
        }
        isClosing = true
        closeFinishActivity = finishActivity
        val id = viewModel.conversationId.value
        if (stopPhoneUse && manager.session.value?.let { it.systemAssistant && it.conversationId == id } == true) {
            manager.stop()
        }
        // Keep both compositions and their native surface alive until the last visible frame.
        // Replacement scenes and a surface already faded by predictive back dispose immediately.
        view.isEnabled = false
        onModalInputChanged(true)
        dialogHost.setVisible(false)
        val immediate = !finishActivity || !view.isAttachedToWindow || view.alpha < 0.001f || activityOwner.isDestroyed
        visibilityOpacity.animateTo(0f, immediate) { dispose(closeFinishActivity) }
    }

    private fun dispose(finishActivity: Boolean) {
        if (presentation.value == AssistantPresentation.CLOSED) return
        presentation.value = AssistantPresentation.CLOSED
        if (manager.assistantOverlay.value === this) manager.assistantOverlay.value = null
        visibilityOpacity.cancel()
        modalOpacity.cancel()
        dialogHost.close()
        decorationView.disposeComposition()
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        if (finishActivity) activityOwner.finishOverlaySession(this)
        context.baseContext = activityOwner.applicationContext
    }

    private fun applySurfaceAlpha() {
        view.alpha = surfaceAlpha * visibilityOpacity.value * modalOpacity.value
        applyDecorationAlpha()
    }

    private fun applyDecorationAlpha() {
        decorationView.alpha = surfaceAlpha * visibilityOpacity.value * modalOpacity.value
    }

    private fun setModalActive(active: Boolean) {
        if (isClosing || presentation.value == AssistantPresentation.CLOSED || modalActive == active) return
        modalActive = active
        // A picker can temporarily pause/hide its Activity. Its retained opacity must still
        // animate back instead of snapping merely because View.isShown changed during dismissal.
        view.isEnabled = !active
        onModalInputChanged(active)
        modalOpacity.animateTo(if (active) 0f else 1f)
    }

    /** Dialogs stay owned by the current Activity, which also owns their result registry. */
    internal class OverlayContext(base: Context) : MutableContextWrapper(base)
}
