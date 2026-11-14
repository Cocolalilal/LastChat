package me.rerere.rikkahub.phoneuse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.activity.AssistantInputReveal
import me.rerere.rikkahub.ui.activity.FloatingOverlayViewport
import me.rerere.rikkahub.ui.activity.TranscriptPanel
import me.rerere.rikkahub.ui.components.ai.MinimalChatInput
import me.rerere.rikkahub.ui.components.chat.ActivityState
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSTTState
import me.rerere.rikkahub.ui.hooks.rememberCustomSttState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.modifier.LastChatBlur
import me.rerere.rikkahub.ui.modifier.LocalLastChatBlur
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

/**
 * Exercises production composables in a real, hardware accelerated native window. No fake
 * Compose test clock, fixed-size Box, model provider, account or API key is involved.
 * The checkerboard is a second native window underneath the dialog, including input dispatch.
 */
class FloatingOverlayLabActivity : ComponentActivity() {
    private val assistant = Assistant(name = "LastChat", avatar = Avatar.Animated())
    private val manager = PhoneUseManager()
    private val highlighter: Highlighter by inject()
    private val mcpManager: McpManager by inject()
    private val input = ChatInputState()
    private val conversation = Conversation(assistantId = assistant.id, messageNodes = emptyList())
    private val task = PhoneUseTask(conversation.id, assistant.id, true, "observe")
    private val settings = Settings(assistants = listOf(assistant), assistantId = assistant.id)
    private var compact by mutableStateOf(false)
    private var normalPopup by mutableStateOf(false)
    private var running by mutableStateOf(true)
    private var reply by mutableStateOf("I found the result. **Everything is ready.**")
    private lateinit var root: FrameLayout
    private lateinit var content: ComposeView
    private lateinit var dialog: androidx.activity.ComponentDialog
    private lateinit var viewport: FloatingOverlayViewport
    private val metricsThread = HandlerThread("overlay-lab-frame-metrics")
    private val frames = mutableListOf<JSONObject>()
    private val durations = mutableListOf<Long>()
    var inputHeight = 0
        private set
    var inputHidden = false
        private set
    var compactSettled = false
        private set
    var outsideTaps = 0
        private set
    var phase = "initial"
        private set
    var visualBounds = Rect()
        private set

    val keyboardVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(content)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    val nativeGeometry: List<Int>
        get() = dialog.window!!.attributes.let { listOf(it.width, it.height, it.x, it.y) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(object : View(this) {
            val paint = Paint()
            override fun onDraw(canvas: Canvas) {
                val cell = (24 * resources.displayMetrics.density).toInt()
                for (y in 0..height / cell) for (x in 0..width / cell) {
                    paint.color = if ((x + y) % 2 == 0) Color.rgb(210, 224, 235) else Color.rgb(56, 76, 94)
                    canvas.drawRect((x * cell).toFloat(), (y * cell).toFloat(),
                        ((x + 1) * cell).toFloat(), ((y + 1) * cell).toFloat(), paint)
                }
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_UP) outsideTaps++
                return true
            }
        })
        root = FrameLayout(this)
        content = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                val density = resources.displayMetrics.density
                leftMargin = (16 * density).toInt()
                rightMargin = (16 * density).toInt()
                bottomMargin = (24 * density).toInt()
            }
            setContent { Scene() }
        }
        root.addView(content)
        dialog = createPhoneOverlayDialog(this, root).apply {
            window!!.setType(WindowManager.LayoutParams.TYPE_APPLICATION)
            show()
        }
        viewport = FloatingOverlayViewport(dialog.window!!, root, content, passThrough = true).apply {
            onFrame = { this@FloatingOverlayLabActivity.visualBounds = Rect(it) }
            attach()
            setInputEnabled(true)
            setBlurEnabled(true)
        }
        dialog.window!!.decorView.viewTreeObserver.addOnPreDrawListener {
            val rect = Rect()
            content.getGlobalVisibleRect(rect)
            frames += JSONObject().put("t", SystemClock.elapsedRealtimeNanos())
                .put("phase", phase).put("native", JSONArray(nativeGeometry))
                .put("content", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
                .put("inputHidden", inputHidden).put("inputHeight", inputHeight).put("compactSettled", compactSettled)
                .put("imeVisible", keyboardVisible)
                .put("surface", JSONArray(listOf(root.width, root.height)))
            true
        }
        metricsThread.start()
        dialog.window!!.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
            synchronized(durations) { durations += metrics.getMetric(FrameMetrics.TOTAL_DURATION) }
        }, Handler(metricsThread.looper))
    }

    @Composable
    private fun Scene() {
        val nav = remember { NavHostController(this) }
        val toaster = rememberAppToasterState()
        val stt = rememberCustomSttState()
        CompositionLocalProvider(LocalSettings provides settings, LocalNavController provides nav,
            LocalToaster provides toaster, LocalHighlighter provides highlighter, LocalSTTState provides stt,
            LocalLastChatBlur provides LastChatBlur(enabled = true, windowBlur = true)) {
            RikkahubTheme {
                if (normalPopup) {
                    PhoneUseControls(task.copy(systemAssistant = false, running = running,
                        action = if (running) "tap" else "finish"), assistant, manager,
                        floating = true, reply = reply, onSendReply = {}, onReplyFocus = {})
                } else Column(Modifier.fillMaxWidth()) {
                    TranscriptPanel(
                        assistantAvatar = { PhoneUseAvatar(assistant, it, active = running) },
                        messages = sampleMessages(),
                        activityState = if (running) ActivityState.Replying else ActivityState.Hidden,
                        isGenerating = running, isSpeaking = false,
                        onOpenInApp = {}, onCopy = {}, onRegenerate = {}, onToggleTts = {},
                        compact = compact, phoneTask = if (compact) task else null,
                        onCompactSettled = { compactSettled = it },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AssistantInputReveal(visible = !compact, onHidden = { inputHidden = it },
                        modifier = Modifier.onSizeChanged { inputHeight = it.height }) {
                        MinimalChatInput(
                            state = input, conversation = conversation, settings = settings,
                            mcpManager = mcpManager, enableSearch = false, onToggleSearch = {},
                            onUpdateChatModel = {}, onUpdateAssistant = {}, onUpdateConversation = {},
                            onToolApproval = { _, _, _, _ -> }, onUpdateSearchService = {},
                            onClearContext = {}, onCancelClick = {}, onSendClick = {}, onLongSendClick = {},
                            applyBottomInsets = false, bottomPadding = 0.dp, horizontalPadding = 0.dp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }

    private fun sampleMessages() = listOf(
        UIMessage(id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Find the display settings and check the result."))),
        UIMessage(id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
            role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("""
                I checked the screen and opened **Display settings**.

                - Brightness is set to automatic.
                - Motion smoothness is adaptive.

                $reply

                ```kotlin
                val display = "Adaptive"
                ```

                [Display settings](https://example.com/display)
            """.trimIndent()))),
    )

    /** Called only on the main thread by instrumentation. */
    fun setPhase(name: String, phone: Boolean = compact, popup: Boolean = normalPopup,
        generating: Boolean = running, text: String = reply) {
        phase = name
        val params = content.layoutParams as FrameLayout.LayoutParams
        val width = if (popup) FrameLayout.LayoutParams.WRAP_CONTENT else FrameLayout.LayoutParams.MATCH_PARENT
        if (params.width != width) {
            params.width = width
            content.layoutParams = params
        }
        compact = phone
        normalPopup = popup
        running = generating
        reply = text
    }

    fun showKeyboard() {
        input.focusRequester.requestFocus()
        androidx.core.view.WindowCompat.getInsetsController(dialog.window!!, content)
            .show(androidx.core.view.WindowInsetsCompat.Type.ime())
    }

    fun hideKeyboard() {
        androidx.core.view.WindowCompat.getInsetsController(dialog.window!!, content)
            .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        content.clearFocus()
    }

    fun report(): String = JSONObject()
        .put("device", android.os.Build.MODEL)
        .put("sdk", android.os.Build.VERSION.SDK_INT)
        .put("frames", JSONArray(frames))
        .put("frameDurationsNs", JSONArray(synchronized(durations) { durations.toList() }))
        .put("outsideTaps", outsideTaps)
        .put("performanceCaveat", "Software emulator timings cannot establish physical-device smoothness")
        .toString(2)

    override fun onDestroy() {
        if (::viewport.isInitialized) viewport.close()
        if (::dialog.isInitialized) dialog.dismiss()
        metricsThread.quitSafely()
        super.onDestroy()
    }
}
