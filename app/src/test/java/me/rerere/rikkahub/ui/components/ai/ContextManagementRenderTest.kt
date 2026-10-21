package me.rerere.rikkahub.ui.components.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ContextCompressionStatus
import me.rerere.rikkahub.service.ContextManagementActivity
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.motion.MotionPolicy
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration

/** Opt-in screenshots of the actual native sheet; uses the existing repository render harness contract. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class ContextManagementRenderTest {
    private val output = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let(::File)
    private val noHaptics = PremiumHaptics(object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }, null, false)

    @Test
    fun rendersReadyAndRunningSheetsInBothThemes() {
        assumeTrue(output != null)
        output?.mkdirs()
        val assistant = Assistant()
        val conversation = Conversation(
            assistantId = assistant.id,
            messageNodes = (0 until 14).flatMap {
                listOf(MessageNode.of(UIMessage.user("Let's work through the next part of the story. ".repeat(20))),
                    MessageNode.of(UIMessage.assistant("The characters agreed to meet tomorrow. ".repeat(20))))
            },
            contextSummary = "### [Milestone 1] <!-- lastchat:coverage=0..3 -->\n" +
                "Keep the setting coastal, maintain Mira's cautious voice, and carry the unfinished journey forward.",
            contextSummaryUpToIndex = 3,
        )
        for (dark in listOf(true, false)) for (running in listOf(false, true)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = controller.get()
            activity.setContent {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    CompositionLocalProvider(LocalMotionPolicy provides MotionPolicy(reduceMotion = true)) {
                        ContextManagementSheet(
                            conversation = conversation, assistant = assistant,
                            model = Model(modelId = "chat", contextWindowTokens = 32_000),
                            status = if (running) ContextCompressionStatus(ContextManagementActivity.SUMMARIZING, 1, 3) else null,
                            onOptimize = {}, onCancel = {}, onUpdateConversation = {}, onDismiss = {},
                            haptics = noHaptics,
                        )
                    }
                }
            }
            repeat(5) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200)) }
            val view = ShadowDialog.getLatestDialog()?.window?.decorView ?: activity.window.decorView
            val width = (360 * activity.resources.displayMetrics.density).toInt()
            val height = (800 * activity.resources.displayMetrics.density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            repeat(3) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120))
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val name = "context-${if (dark) "dark" else "light"}-${if (running) "running" else "ready"}.png"
            File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(view.width > 0 && view.height > 0)
            ShadowDialog.getLatestDialog()?.dismiss()
            controller.pause().stop().destroy()
        }
    }
}
