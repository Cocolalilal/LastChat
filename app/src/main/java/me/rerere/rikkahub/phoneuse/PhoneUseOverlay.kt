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
import kotlinx.coroutines.delay
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

/** Gestures pass through the existing window. Only a real screenshot hides it. */
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
    private var inputRequested = false
    private var heldConversation: Uuid? = null
    private val chatService by lazy { GlobalContext.get().get<ChatService>() }

    fun start() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        val settingsStore = GlobalContext.get().get<SettingsStore>()
        scope.launch {
            combine(manager.session, manager.acting, manager.capturing, manager.appUiVisible) {
                    task, acting, capturing, appVisible ->
                OverlayState(task, acting, capturing, appVisible)
            }.collect { (task, acting, capturing, appVisible) ->
                if (heldConversation != task?.conversationId) {
                    heldConversation?.let(chatService::removeConversationReference)
                    heldConversation = task?.conversationId
                    heldConversation?.let(chatService::addConversationReference)
                }
                if (task == null) {
                    remove()
                    return@collect
                }
                if (task.running || appVisible || capturing) inputRequested = false
                if (view == null || currentSystemMode != task.systemAssistant) {
                    remove()
                    currentSystemMode = task.systemAssistant
                    val content = ComposeView(service).apply {
                        visibility = View.INVISIBLE // Never flash an extra avatar over a starting app activity.
                        setViewTreeLifecycleOwner(this@PhoneUseOverlay)
                        setViewTreeSavedStateRegistryOwner(this@PhoneUseOverlay)
                        setContent {
                            PhoneUseOverlayContent(manager, settingsStore, { openConversation(it) }, ::drag, ::setInputEnabled)
                        }
                    }
                    val params = WindowManager.LayoutParams(
                        if (task.systemAssistant) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        gravity = Gravity.BOTTOM or (if (task.systemAssistant) Gravity.CENTER_HORIZONTAL else Gravity.END)
                        y = if (task.systemAssistant) 0 else (24 * service.resources.displayMetrics.density).toInt()
                    }
                    runCatching { windows.addView(content, params) }
                        .onSuccess { view = content }
                        .onFailure { content.disposeComposition(); manager.stop() }
                }
                updateFlags(acting)
                view?.visibility = if (shouldShowPhoneOverlay(task, appVisible, capturing)) View.VISIBLE else View.INVISIBLE
                if (view?.isAttachedToWindow == true) manager.controlsReady.value = task.conversationId

                if (task.systemAssistant && (!task.running || task.awaitingReply) &&
                    manager.claimAssistantReturn(task.conversationId)) {
                    if (appVisible) {
                        if (!task.running) manager.markAssistantPresented(task.conversationId)
                    } else {
                        openConversation(task, pause = false)
                        scope.launch {
                            delay(1200)
                            val pending = manager.session.value
                            if (!manager.appUiVisible.value && pending?.conversationId == task.conversationId &&
                                pending.returnRequested && (!pending.running || pending.awaitingReply)) {
                                manager.markReturnFailed(task.conversationId)
                            }
                        }
                    }
                }
                if (task.systemAssistant && !task.running && appVisible && !task.resultPresented) {
                    manager.markAssistantPresented(task.conversationId)
                }
            }
        }
    }

    private fun openConversation(task: PhoneUseTask, pause: Boolean = true) {
        if (pause && task.running && !task.paused && !task.awaitingReply) manager.togglePause()
        val target = if (task.systemAssistant) AssistantOverlayActivity::class.java else RouteActivity::class.java
        runCatching {
            service.startActivity(Intent(service, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(RouteActivity.EXTRA_CONVERSATION_ID, task.conversationId.toString())
                putExtra(RouteActivity.EXTRA_ASSISTANT_ID, task.assistantId.toString())
            })
        }.onFailure { manager.markReturnFailed(task.conversationId) }
    }

    private fun setInputEnabled(enabled: Boolean) {
        inputRequested = enabled
        updateFlags(manager.acting.value)
    }

    private fun updateFlags(acting: Boolean) {
        val content = view ?: return
        val params = content.layoutParams as WindowManager.LayoutParams
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (inputRequested) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            (if (acting) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)
        if (params.flags != flags) {
            params.flags = flags
            runCatching { windows.updateViewLayout(content, params) }
        }
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
        inputRequested = false
        manager.controlsReady.value = null
    }

    fun close() {
        remove()
        heldConversation?.let(chatService::removeConversationReference)
        heldConversation = null
        scope.cancel()
        registry.currentState = Lifecycle.State.DESTROYED
    }

    private data class OverlayState(val task: PhoneUseTask?, val acting: Boolean, val capturing: Boolean, val appVisible: Boolean)
}
