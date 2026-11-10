package me.rerere.rikkahub.phoneuse

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.activity.AssistantOverlayActivity
import org.koin.core.context.GlobalContext

/** Accessibility-owned controls survive leaving the app and need no draw-over-apps permission. */
internal class PhoneUseOverlay(
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
    private var currentSystemMode: Boolean? = null

    fun start() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        val settingsStore = GlobalContext.get().get<SettingsStore>()
        scope.launch {
            combine(manager.session, manager.acting, manager.assistantUiVisible) { task, acting, assistantVisible ->
                Triple(task, acting, assistantVisible)
            }.collect { (task, acting, assistantVisible) ->
                if (task == null) {
                    remove()
                } else {
                    if (view == null || currentSystemMode != task.systemAssistant) {
                        remove()
                        currentSystemMode = task.systemAssistant
                        val content = ComposeView(service).apply {
                            setViewTreeLifecycleOwner(this@PhoneUseOverlay)
                            setViewTreeSavedStateRegistryOwner(this@PhoneUseOverlay)
                            setContent { PhoneUseOverlayContent(manager, settingsStore, ::openConversation, ::drag) }
                        }
                        val params = WindowManager.LayoutParams(
                            if (task.systemAssistant) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                            PixelFormat.TRANSLUCENT,
                        ).apply {
                            gravity = Gravity.BOTTOM or (if (task.systemAssistant) Gravity.CENTER_HORIZONTAL else Gravity.END)
                            y = (24 * service.resources.displayMetrics.density).toInt()
                        }
                        runCatching { windows.addView(content, params) }
                            .onSuccess { view = content }
                            .onFailure { content.disposeComposition(); manager.stop() }
                    }
                    view?.visibility = if (acting || (task.systemAssistant && assistantVisible)) View.INVISIBLE else View.VISIBLE
                    // Actions cannot start until Android has attached the stop controls.
                    if (!acting && view?.isAttachedToWindow == true) manager.controlsReady.value = task.conversationId
                }
            }
        }
    }

    private fun openConversation(task: PhoneUseTask) {
        // Pause before bringing up approvals/chat so a pending action cannot hit the user's UI.
        if (task.running && !task.paused) manager.togglePause()
        val target = if (task.systemAssistant) AssistantOverlayActivity::class.java else RouteActivity::class.java
        service.startActivity(Intent(service, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(RouteActivity.EXTRA_CONVERSATION_ID, task.conversationId.toString())
            putExtra(RouteActivity.EXTRA_ASSISTANT_ID, task.assistantId.toString())
        })
    }

    private fun drag(dx: Float, dy: Float) {
        val content = view ?: return
        val params = content.layoutParams as WindowManager.LayoutParams
        val bounds = service.resources.displayMetrics
        params.x = (params.x - dx.toInt()).coerceIn(0, (bounds.widthPixels - content.width).coerceAtLeast(0))
        params.y = (params.y - dy.toInt()).coerceIn(0, (bounds.heightPixels - content.height).coerceAtLeast(0))
        runCatching { windows.updateViewLayout(content, params) }
    }

    private fun remove() {
        view?.let { runCatching { windows.removeViewImmediate(it) }; it.disposeComposition() }
        view = null
        manager.controlsReady.value = null
    }

    fun close() {
        remove()
        scope.cancel()
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
