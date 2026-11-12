package me.rerere.rikkahub.data.ai.tools

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import me.rerere.rikkahub.ui.components.ui.FloatingOverlayLayout
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.ui.theme.Shapes
import me.rerere.rikkahub.ui.theme.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.phoneuse.*
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneUseControlsRenderTest {
    private val noHaptics = PremiumHaptics(object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }, null, false)

    @Test fun visibilityTracksOverlappingActivities() {
        val manager = PhoneUseManager()
        val tracker = PhoneUseVisibilityTracker(manager)
        val first = Robolectric.buildActivity(ComponentActivity::class.java).get()
        val second = Robolectric.buildActivity(me.rerere.rikkahub.ui.activity.AssistantOverlayActivity::class.java).get()
        tracker.onActivityStarted(first)
        tracker.onActivityStarted(second)
        tracker.onActivityStopped(first)
        org.junit.Assert.assertTrue(manager.assistantUiVisible.value)
        org.junit.Assert.assertTrue(manager.appUiVisible.value)
        tracker.onActivityDestroyed(first)
        org.junit.Assert.assertTrue(manager.appUiVisible.value)
        tracker.onActivityStopped(second)
        org.junit.Assert.assertFalse(manager.appUiVisible.value)
        org.junit.Assert.assertFalse(manager.assistantUiVisible.value)
    }

    @Test fun emptyToasterDoesNotExpandFloatingWindow() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        activity.setContent {
            val toaster = rememberAppToasterState()
            FloatingOverlayLayout(
                content = { Box(Modifier.size(112.dp, 76.dp)) },
                decoration = { AppToasterHost(toaster) },
            )
        }
        val view = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val density = activity.resources.displayMetrics.density
        repeat(3) {
            view.measure(View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec((891 * density).toInt(), View.MeasureSpec.AT_MOST))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        }
        org.junit.Assert.assertEquals((112 * density).toInt(), view.measuredWidth)
        org.junit.Assert.assertEquals((76 * density).toInt(), view.measuredHeight)
        controller.pause().stop().destroy()
    }

    @Test fun startingBarIsVisibleWithoutConversationOrOpeningAnimation() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        android.provider.Settings.Global.putFloat(activity.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        activity.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme(colorScheme = PresetThemes[0].getColorScheme(true), shapes = Shapes, typography = Typography) {
                    me.rerere.rikkahub.ui.activity.AssistantStartingBar(
                        Assistant(name = "Assistant", avatar = Avatar.Animated.defaultGenerical()), recording = false,
                        onToggleMicrophone = {},
                    )
                }
            }
        }
        val view = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val density = activity.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((891 * density).toInt(), View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        org.junit.Assert.assertTrue(view.measuredHeight >= (64 * density).toInt())
        org.junit.Assert.assertTrue(view.measuredHeight < (200 * density).toInt())
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { output ->
            File(output).mkdirs()
            val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(output, "assistant-first-frame.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        controller.pause().stop().destroy()
    }

    @Test fun minimizedPillHasCompactCapsuleDimensionsWithItsRealToastHost() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        android.provider.Settings.Global.putFloat(activity.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val assistant = Assistant(name = "Generical", avatar = Avatar.Animated.defaultGenerical())
        activity.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    val toast = rememberAppToasterState()
                    FloatingOverlayLayout(content = {
                        PhoneUseControls(PhoneUseTask(Uuid.random(), assistant.id, false, "observe"),
                            assistant, PhoneUseManager(), floating = true, haptics = noHaptics)
                    }, decoration = { AppToasterHost(toast) })
                }
            }
        }
        val view = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val density = activity.resources.displayMetrics.density
        repeat(3) {
            view.measure(View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec((891 * density).toInt(), View.MeasureSpec.AT_MOST))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        }
        org.junit.Assert.assertEquals((120 * density).toInt(), view.measuredWidth)
        org.junit.Assert.assertEquals((72 * density).toInt(), view.measuredHeight)
        controller.pause().stop().destroy()
    }

    @Test fun compactAssistantHasNoResizeHandleAndKeepsCapsuleDimensions() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        android.provider.Settings.Global.putFloat(activity.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val task = PhoneUseTask(Uuid.random(), Uuid.random(), true, "observe")
        activity.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    me.rerere.rikkahub.ui.activity.TranscriptPanel(
                        assistantAvatar = { Box(it) }, messages = emptyList(),
                        activityState = me.rerere.rikkahub.ui.components.chat.ActivityState.Waiting,
                        isGenerating = true, isSpeaking = false, onOpenInApp = {},
                        onCopy = {}, onRegenerate = {}, onToggleTts = {}, compact = true, phoneTask = task,
                    )
                }
            }
        }
        val view = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val density = activity.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((891 * density).toInt(), View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        org.junit.Assert.assertEquals((56 * density).toInt(), view.measuredHeight)
        controller.pause().stop().destroy()
    }

    @Test fun finishedPopupExposesReplyAndComposerOnFirstLayout() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        android.provider.Settings.Global.putFloat(activity.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val assistant = Assistant(name = "Assistant", avatar = Avatar.Emoji("A"))
        val task = PhoneUseTask(Uuid.random(), assistant.id, false, "finish", running = false, reply = "Done")
        activity.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    PhoneUseControls(task, assistant, PhoneUseManager(), floating = true,
                        haptics = noHaptics, onSendReply = {})
                }
            }
        }
        val view = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val density = activity.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec((891 * density).toInt(), View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        org.junit.Assert.assertEquals((376 * density).toInt(), view.measuredWidth)
        org.junit.Assert.assertTrue(view.measuredHeight > (128 * density).toInt())
        controller.pause().stop().destroy()
    }

    @Test fun renderControlSurfaces() {
        val output = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() } ?: return
        File(output).mkdirs()
        val assistant = Assistant(name = "Generical", avatar = Avatar.Animated.defaultGenerical())
        for ((name, system, paused) in listOf(
            Triple("phone-floating", false, false),
            Triple("phone-assistant", true, false),
            Triple("phone-paused", true, true),
            Triple("phone-reply", false, false),
            Triple("phone-assistant-reply", true, false),
        )) {
            val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = activityController.get()
            // Freeze through the real system motion policy; Robolectric cannot idle a live avatar loop.
            android.provider.Settings.Global.putFloat(activity.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            val task = PhoneUseTask(Uuid.random(), assistant.id, system, "observe", paused = paused,
                running = !name.endsWith("reply"), reply = if (name.endsWith("reply")) "The address is **12 Main Street**. It closes at 6 pm." else "")
            val heightDp = if (name.endsWith("reply")) 300 else 120
            activity.setContent {
                CompositionLocalProvider(LocalSettings provides Settings()) {
                    MaterialTheme(colorScheme = PresetThemes[0].getColorScheme(true), shapes = Shapes, typography = Typography) {
                        Box(Modifier.requiredSize(411.dp, heightDp.dp)) {
                            if (system && !task.running) androidx.compose.foundation.layout.Column(
                                Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp)) {
                                me.rerere.rikkahub.ui.activity.TranscriptPanel(
                                assistantAvatar = { PhoneUseAvatar(assistant, it, active = false) },
                                messages = listOf(me.rerere.ai.ui.UIMessage.assistant(task.reply)),
                                activityState = me.rerere.rikkahub.ui.components.chat.ActivityState.Hidden,
                                isGenerating = false, isSpeaking = false, onOpenInApp = {}, onCopy = {},
                                onRegenerate = {}, onToggleTts = {}, phoneTask = task, onDismiss = {},
                                openDescription = "Open assistant",
                                )
                                PhoneUseReplyComposer(task, PhoneUseManager(), {}, {}, Modifier.padding(top = 8.dp))
                            }
                            else if (system) AssistantPhoneUseDock(task, assistant, PhoneUseManager(), onOpen = {})
                            else PhoneUseControls(task, assistant, PhoneUseManager(), onOpen = {}, floating = true,
                                haptics = noHaptics, onSendReply = {})
                        }
                    }
                }
            }
            val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
            val width = (411 * activity.resources.displayMetrics.density).toInt()
            val height = (heightDp * activity.resources.displayMetrics.density).toInt()
            val view = root.getChildAt(0)
            repeat(3) {
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            activityController.pause().stop().destroy()
        }
    }
}
