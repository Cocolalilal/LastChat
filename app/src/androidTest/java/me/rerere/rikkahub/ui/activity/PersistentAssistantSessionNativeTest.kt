package me.rerere.rikkahub.ui.activity

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.InputDevice
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.ComposeView
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.phoneuse.PhoneUseAccessibilityService
import me.rerere.rikkahub.phoneuse.FloatingOverlayLabActivity
import me.rerere.rikkahub.phoneuse.PhoneUseManager
import me.rerere.rikkahub.service.ChatService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real service, Activity and persistent scene. Only the phone action is a no-op; no provider is used. */
@RunWith(AndroidJUnit4::class)
class PersistentAssistantSessionNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun chatServiceOnMain(): ChatService {
        lateinit var service: ChatService
        main { service = GlobalContext.get().get<ChatService>() }
        return service
    }
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun argument(value: String): String {
        // UiAutomation uses Runtime.exec, not a shell: quotes would become part of the value.
        require(value.none { it.isWhitespace() })
        return value
    }
    private fun restoreSetting(key: String, value: String) {
        shell(if (value == "null" || value.isEmpty()) "settings delete secure $key"
            else "settings put secure $key ${argument(value)}")
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 90_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(30)
        }
        throw AssertionError("Timed out: $label")
    }

    private fun composeChild(view: View): ComposeView? {
        // The screen-edge decoration shares the window but is not the interactive scene.
        if (view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) return null
        if (view is ComposeView) {
            return view
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            composeChild(view.getChildAt(index))?.let { return it }
        }
        return null
    }
    private fun touch(action: Int, down: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    private fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, fromX, fromY)
        repeat(8) { step ->
            SystemClock.sleep(16)
            val progress = (step + 1) / 8f
            touch(MotionEvent.ACTION_MOVE, down, fromX + (toX - fromX) * progress, fromY + (toY - fromY) * progress)
        }
        touch(MotionEvent.ACTION_UP, down, toX, toY)
    }

    @Test
    fun actualServicePopupKeepsOneWindowDuringInterruptedFlingsAndExpansion() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val context = instrumentation.targetContext
        val manager = GlobalContext.get().get<PhoneUseManager>()
        val settings = GlobalContext.get().get<SettingsStore>()
        val chat = chatServiceOnMain()
        val previousDisplay = settings.settingsFlow.value.displaySetting
        val previousServices = shell("settings get secure enabled_accessibility_services")
        val previousEnabled = shell("settings get secure accessibility_enabled")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val frames = mutableListOf<JSONObject>()
        var phase = "launch"
        var root: View? = null
        var listener: ViewTreeObserver.OnPreDrawListener? = null
        try {
            runBlocking { settings.update { it.copy(displaySetting = previousDisplay.copy(enableBlurEffect = true)) } }
            main { manager.assistantOverlay.value?.close(); manager.stop() }
            val component = ComponentName(context, PhoneUseAccessibilityService::class.java).flattenToString()
            val services = previousServices.takeUnless { it == "null" || it.isEmpty() }
                ?.split(':').orEmpty().plus(component).distinct().joinToString(":")
            shell("settings put secure enabled_accessibility_services ${argument(services)}")
            shell("settings put secure accessibility_enabled 1")
            await("actual service connected") { manager.controller.value is PhoneUseAccessibilityService }
            val conversation = runBlocking { chat.createConversation(settings.settingsFlow.value.assistantId) }
            shell("input keyevent KEYCODE_HOME")
            await("LastChat is behind target content") { !manager.appUiVisible.value && !manager.assistantUiVisible.value }
            val action = scope.async {
                manager.runAction(conversation.id, conversation.assistantId, "observe", cancel = {}) { Unit }
            }
            var popup: ComposeView? = null
            await("actual regular popup mounted") {
                root = WindowInspector.getGlobalWindowViews().firstOrNull {
                    (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY &&
                        composeChild(it)?.let { child -> child.isShown && child.alpha > 0.9f && child.width > 0 } == true
                }
                popup = root?.let(::composeChild)
                popup != null && manager.controlsReady.value == conversation.id
            }
            runBlocking { withTimeout(90_000) { action.await() } }
            val blurExpected = context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
            await("actual popup blur follows platform capability") { manager.overlayBlur.value == blurExpected }
            val window = requireNotNull(root)
            val content = requireNotNull(popup)
            val density = context.resources.displayMetrics.density
            fun bounds(): Rect {
                val xy = IntArray(2)
                content.getLocationOnScreen(xy)
                return Rect(xy[0], xy[1], xy[0] + content.width, xy[1] + content.height)
            }
            fun geometry(): List<Int> = (window.layoutParams as WindowManager.LayoutParams).let {
                listOf(it.width, it.height, it.x, it.y)
            }
            var original = emptyList<Int>()
            main {
                original = geometry()
                listener = ViewTreeObserver.OnPreDrawListener {
                    val rect = bounds()
                    frames += JSONObject().put("phase", phase).put("timeNs", SystemClock.elapsedRealtimeNanos())
                        .put("native", JSONArray(geometry())).put("content", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
                        .put("root", JSONArray(listOf(window.width, window.height)))
                    true
                }
                window.viewTreeObserver.addOnPreDrawListener(requireNotNull(listener))
            }
            repeat(3) { cycle ->
                phase = "fling-$cycle"
                var rect = Rect()
                main { rect = bounds() }
                swipe(rect.left + 24*density, rect.top + 28*density,
                    if (cycle % 2 == 0) 48*density else window.width - 48*density,
                    if (cycle % 2 == 0) 160*density else window.height - 100*density)
                // Interrupt an in-flight spring with another physical drag.
                SystemClock.sleep(30)
                main { rect = bounds() }
                swipe(rect.left + 24*density, rect.top + 28*density,
                    if (cycle % 2 == 0) window.width - 48*density else 48*density,
                    if (cycle % 2 == 0) window.height - 100*density else 160*density)
                var last = Rect()
                var stable = 0
                await("popup spring settles against edge") {
                    val next = bounds()
                    stable = if (next == last) stable + 1 else 0
                    last = next
                    stable >= 8 && minOf(next.left, window.width - next.right) <= 36*density
                }
                main {
                    assertSame("Popup window replaced", window, content.rootView)
                    assertEquals("Native window moved during fling", original, geometry())
                }
            }
            phase = "expand"
            var pill = Rect()
            main { pill = bounds() }
            val down = SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN, down, pill.left + 28*density, pill.top + 28*density)
            touch(MotionEvent.ACTION_UP, down, pill.left + 28*density, pill.top + 28*density)
            await("real popup expands after avatar tap") { bounds().height() > pill.height() + 40*density }
            SystemClock.sleep(800)
            phase = "completion"
            main { manager.generationSettled(conversation.id, false, "**Completed.** The actual popup remains in its window.") }
            SystemClock.sleep(800)
            main { window.viewTreeObserver.removeOnPreDrawListener(requireNotNull(listener)) }
            assertTrue("Must sample real popup motion", frames.size > 10)
            frames.forEach { frame ->
                val attrs = frame.getJSONArray("native")
                assertEquals("Native window changed during ${frame.getString("phase")}", original,
                    (0 until attrs.length()).map(attrs::getInt))
                val rect = frame.getJSONArray("content")
                val screen = frame.getJSONArray("root")
                assertTrue("Popup escaped screen during ${frame.getString("phase")}", rect.getInt(0) >= -1 &&
                    rect.getInt(1) >= -1 && rect.getInt(2) <= screen.getInt(0)+1 && rect.getInt(3) <= screen.getInt(1)+1)
            }
        } finally {
            scope.cancel()
            try {
                var snapshot = emptyList<JSONObject>()
                main {
                    listener?.let { root?.viewTreeObserver?.removeOnPreDrawListener(it) }
                    snapshot = frames.toList()
                    manager.stop()
                }
                scope.cancel()
                File(context.getExternalFilesDir(null), "actual-popup-native-report.json").writeText(
                    JSONObject().put("frames", JSONArray(snapshot)).put("performanceCaveat",
                        "TCG emulator geometry evidence; physical-device smoothness remains unmeasured").toString(2))
            } finally {
                try {
                    runBlocking { settings.update { it.copy(displaySetting = previousDisplay) } }
                } finally {
                    try { restoreSetting("enabled_accessibility_services", previousServices) }
                    finally { restoreSetting("accessibility_enabled", previousEnabled) }
                }
            }
        }
    }

    @Test
    fun sameSceneWindowDraftAndConversationSurvivePhoneReturnCancelAndActivityRecreation() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val context = instrumentation.targetContext
        val manager = GlobalContext.get().get<PhoneUseManager>()
        val settings = GlobalContext.get().get<SettingsStore>()
        val chat = chatServiceOnMain()
        val previousServices = shell("settings get secure enabled_accessibility_services")
        val previousEnabled = shell("settings get secure accessibility_enabled")
        val previousConfig = settings.settingsFlow.value.assistantOverlayConfig
        val previousDisplay = settings.settingsFlow.value.displaySetting
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val frames = mutableListOf<JSONObject>()
        var phase = "launch"
        var detaches = 0
        var scene: AssistantOverlaySession? = null
        var activity: AssistantOverlayActivity? = null
        var underlay: FloatingOverlayLabActivity? = null
        try {
            // These changes affect the test runtime only and are restored even after a failure.
            runBlocking { settings.update { it.copy(
                displaySetting = previousDisplay.copy(enableBlurEffect = true),
                assistantOverlayConfig = previousConfig.copy(
                    autoStartStt = false, autoReadReply = false, attachScreenshot = false)) } }
            val component = ComponentName(context, PhoneUseAccessibilityService::class.java).flattenToString()
            val services = previousServices.takeUnless { it == "null" || it.isEmpty() }
                ?.split(':').orEmpty().plus(component).distinct().joinToString(":")
            shell("settings put secure enabled_accessibility_services ${argument(services)}")
            shell("settings put secure accessibility_enabled 1")
            await("real accessibility service connected") {
                manager.controller.value is PhoneUseAccessibilityService && manager.assistantOverlayHostReady.value
            }
            main { context.startActivity(Intent(context, FloatingOverlayLabActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            await("LastChat underlay started") {
                underlay = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FloatingOverlayLabActivity>().firstOrNull()
                underlay != null && manager.otherAppUiVisible.value
            }
            main { requireNotNull(underlay).setPhase("own-app-underlay", generating = false) }
            main { context.startActivity(Intent(context, AssistantOverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            await("assistant Activity resumed") {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<AssistantOverlayActivity>().firstOrNull()
                activity != null
            }
            await("persistent service scene mounted") {
                manager.assistantOverlay.value?.let { it.serviceHosted && it.view.isAttachedToWindow &&
                    it.view.width > 0 && it.viewModel.conversationId.value != null } == true
            }
            val original = manager.assistantOverlay.value!!
            scene = original
            val blurExpected = context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
            await("assistant blur follows platform capability") { original.nativeBlurActive == blurExpected }
            await("normal assistant remains visible over LastChat") {
                manager.otherAppUiVisible.value && original.view.isShown && original.view.alpha > 0.9f
            }
            main { requireNotNull(underlay).finish() }
            underlay = null
            await("own-app underlay closed before phone actions") { !manager.otherAppUiVisible.value }
            val view = original.view
            val model = original.viewModel
            val input = original.inputState
            val conversationId = model.conversationId.value!!
            val nativeRoot = view.rootView
            val draft = "Keep this unsent draft through phone use."
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) { detaches++ }
            }
            val preDraw = ViewTreeObserver.OnPreDrawListener {
                val attrs = view.rootView.layoutParams as? WindowManager.LayoutParams
                frames += JSONObject().put("phase", phase).put("timeNs", SystemClock.elapsedRealtimeNanos())
                    .put("native", JSONArray(attrs?.let { listOf(it.width, it.height, it.x, it.y) }.orEmpty()))
                    .put("mode", original.presentation.value.name).put("attached", view.isAttachedToWindow)
                true
            }
            main {
                input.setMessageText(draft)
                view.addOnAttachStateChangeListener(attachListener)
                nativeRoot.viewTreeObserver.addOnPreDrawListener(preDraw)
            }
            val messages = listOf(
                MessageNode.of(UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Check the screen.")))),
                MessageNode.of(UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text(
                    "I checked **Display settings**.\n\n- Adaptive motion is enabled.\n- Brightness is automatic.\n\n```kotlin\nval checked = true\n```"))))
            )
            runBlocking {
                val conversation = chat.getConversationFlow(conversationId).value
                chat.saveConversation(conversationId, conversation.copy(messageNodes = messages))
            }
            SystemClock.sleep(1000)
            fun assertIdentity() = main {
                assertSame("Session replaced", original, manager.assistantOverlay.value)
                assertSame("ComposeView replaced", view, original.view)
                assertSame("Native window replaced", nativeRoot, view.rootView)
                assertSame("ViewModel replaced", model, original.viewModel)
                assertSame("Input owner replaced", input, original.inputState)
                assertEquals("Conversation replaced", conversationId, model.conversationId.value)
                assertEquals("Draft lost", draft, input.textContent.text.toString())
                assertEquals("Scene detached during handoff", 0, detaches)
            }
            repeat(3) { cycle ->
                phase = "phone-$cycle"
                val cancellations = AtomicInteger()
                val action = scope.async {
                    manager.runAction(conversationId, model.overlayAssistantId, "observe",
                        cancel = { cancellations.incrementAndGet() }) { Unit }
                }
                await("actual phone handoff") {
                    original.presentation.value == AssistantPresentation.PHONE && !manager.assistantUiVisible.value
                }
                runBlocking { withTimeout(90_000) { action.await() } }
                assertIdentity()
                phase = "return-$cycle"
                main { if (cycle == 1) manager.cancelAndReturn() else manager.finish(conversationId) }
                await("actual Activity return after completion or cancellation") {
                    original.presentation.value == AssistantPresentation.NORMAL && manager.assistantUiVisible.value
                }
                assertIdentity()
                if (cycle == 1) assertEquals("Cancel callback", 1, cancellations.get())
                SystemClock.sleep(600)
            }
            phase = "activity-recreate"
            val previousActivity = requireNotNull(activity)
            main { previousActivity.recreate() }
            await("scene visible after Activity recreation") {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<AssistantOverlayActivity>().firstOrNull { it !== previousActivity }
                activity != null && original.presentation.value == AssistantPresentation.NORMAL &&
                    manager.assistantUiVisible.value
            }
            assertIdentity()
            main {
                nativeRoot.viewTreeObserver.removeOnPreDrawListener(preDraw)
                view.removeOnAttachStateChangeListener(attachListener)
            }
            assertTrue("Must sample the actual scene", frames.isNotEmpty())
            val first = frames.first().getJSONArray("native").toString()
            frames.forEach { assertEquals("Native geometry changed in ${it.getString("phase")}", first,
                it.getJSONArray("native").toString()) }
        } finally {
            scope.cancel()
            try {
                var report = ""
                main {
                    report = JSONObject().put("frames", JSONArray(frames.toList())).put("detachCount", detaches)
                        .put("device", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
                        .put("performanceCaveat", "Software emulator does not establish S24 smoothness").toString(2)
                }
                File(context.getExternalFilesDir(null), "persistent-assistant-native-report.json").writeText(report)
                scope.cancel()
                main { scene?.close(); manager.stop() }
                main { activity?.finish(); underlay?.finish() }
            } finally {
                try {
                    runBlocking { settings.update { it.copy(
                        assistantOverlayConfig = previousConfig, displaySetting = previousDisplay) } }
                } finally {
                    try { restoreSetting("enabled_accessibility_services", previousServices) }
                    finally { restoreSetting("accessibility_enabled", previousEnabled) }
                }
            }
        }
    }
}
