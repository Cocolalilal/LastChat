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
    val viewModel = ViewModelProvider(viewModelStore, object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AssistantOverlayVM(settings, koin.get<ChatService>(), manager) as T
        }
    })[AssistantOverlayVM::class.java]
    val view = ComposeView(context).apply { alpha = 0f }

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeOnBackPressedDispatcherOwner(this)
        view.setParentCompositionContext(recomposer)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        activity.intent.getStringExtra(RouteActivity.EXTRA_ASSISTANT_ID)?.let { value ->
            runCatching { Uuid.parse(value) }.getOrNull()?.let { id ->
                if (settings.settingsFlow.value.assistants.any { it.id == id }) viewModel.overlayAssistantId = id
            }
        }
        viewModel.initialize(existingId)
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
                                persistentPhoneMode = mode == AssistantPresentation.PHONE,
                                onPhoneUseYield = {
                                    setPhoneMode(true)
                                    activityOwner.moveTaskToBack(true)
                                },
                                onSurfaceMotion = { motion ->
                                    view.alpha = motion.alpha
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
        if (android.os.Build.VERSION.SDK_INT >= 33 && window != null) {
            onBackPressedDispatcher.setOnBackInvokedDispatcher(window.onBackInvokedDispatcher)
        }
    }
    fun setOverlayWindowManager(windowManager: WindowManager?) {
        serviceHosted = windowManager != null
        context.overlayManager = windowManager
        updateChildVisibility()
    }
    private fun updateChildVisibility() {
        context.childrenVisible = activityResumed && presentation.value == AssistantPresentation.NORMAL
        context.children.forEach { it.visibility = if (context.childrenVisible) View.VISIBLE else View.GONE }
    }
    fun close(stopPhoneUse: Boolean = true) {
        if (presentation.value == AssistantPresentation.CLOSED) return
        val id = viewModel.conversationId.value
        if (stopPhoneUse && manager.session.value?.let { it.systemAssistant && it.conversationId == id } == true) {
            manager.stop()
        }
        presentation.value = AssistantPresentation.CLOSED
        if (manager.assistantOverlay.value === this) manager.assistantOverlay.value = null
        context.closeChildren()
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        activityOwner.finish()
        context.baseContext = activityOwner.applicationContext
    }

    /** Compose dialogs must share the trusted overlay layer while the service owns the scene. */
    private class OverlayContext(base: Context) : MutableContextWrapper(base) {
        var overlayManager: WindowManager? = null
        val children = mutableSetOf<View>()
        var childrenVisible = true
        private val owners = mutableMapOf<View, WindowManager>()
        override fun getSystemService(name: String): Any? {
            val original = super.getSystemService(name)
            if (name != Context.WINDOW_SERVICE || original !is WindowManager) return original
            return object : WindowManager by original {
                override fun addView(view: View, params: android.view.ViewGroup.LayoutParams) {
                    val wm = overlayManager ?: original
                    if (overlayManager != null && params is WindowManager.LayoutParams && params.type in 1..99) {
                        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        params.token = null
                    }
                    view.visibility = if (childrenVisible) View.VISIBLE else View.GONE
                    wm.addView(view, params)
                    children += view
                    owners[view] = wm
                }
                override fun updateViewLayout(view: View, params: android.view.ViewGroup.LayoutParams) {
                    (owners[view] ?: original).updateViewLayout(view, params)
                }
                override fun removeView(view: View) {
                    children -= view
                    (owners.remove(view) ?: original).removeView(view)
                }
                override fun removeViewImmediate(view: View) {
                    children -= view
                    (owners.remove(view) ?: original).removeViewImmediate(view)
                }
            }
        }
        fun closeChildren() {
            owners.toMap().forEach { (view, owner) -> runCatching { owner.removeViewImmediate(view) } }
            children.clear(); owners.clear()
        }
    }
}
