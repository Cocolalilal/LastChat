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

    @Test fun renderControlSurfaces() {
        val output = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() } ?: return
        File(output).mkdirs()
        val assistant = Assistant(name = "Generical", avatar = Avatar.Animated.defaultGenerical())
        for ((name, system, paused) in listOf(
            Triple("phone-floating", false, false),
            Triple("phone-assistant", true, false),
            Triple("phone-paused", true, true),
        )) {
            val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = activityController.get()
            // Freeze through the real system motion policy; Robolectric cannot idle a live avatar loop.
            android.provider.Settings.Global.putFloat(activity.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            val task = PhoneUseTask(Uuid.random(), assistant.id, system, "observe", paused = paused)
            activity.setContent {
                CompositionLocalProvider(LocalSettings provides Settings()) {
                    MaterialTheme(colorScheme = PresetThemes[0].getColorScheme(true), shapes = Shapes, typography = Typography) {
                        Box(Modifier.requiredSize(411.dp, 120.dp)) {
                            PhoneUseControls(task, assistant, PhoneUseManager(), onOpen = {}, floating = true, haptics = noHaptics)
                        }
                    }
                }
            }
            val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
            val width = (411 * activity.resources.displayMetrics.density).toInt()
            val height = (120 * activity.resources.displayMetrics.density).toInt()
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
