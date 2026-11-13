package me.rerere.rikkahub.phoneuse

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PhonePopupTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val assistant = Assistant(name = "Assistant", avatar = Avatar.Emoji("A"))
    private var task by mutableStateOf(PhoneUseTask(Uuid.random(), assistant.id, false, "finish",
        running = false, reply = "Done"))
    private var pixels = IntSize.Zero
    private val density get() = compose.activity.resources.displayMetrics.density
    private val noHaptics = PremiumHaptics(object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }, null, false)

    private fun content() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    Box(Modifier.wrapContentSize(Alignment.TopStart).onSizeChanged { pixels = it }) {
                        PhoneUseControls(task, assistant, PhoneUseManager(), floating = true,
                            haptics = noHaptics, onOpen = {}, onSendReply = {})
                    }
                }
            }
        }
        settle()
    }

    private fun settle() {
        // Subcomposition and target-channel effects need the Android measure pass
        // between frames, just as they do on a real display. A single clock jump
        // can finish before the new phase has even reached the popup composition.
        repeat(60) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun stateDescription(): String {
        val avatar = compose.onNodeWithContentDescription("Assistant, phone use controls").fetchSemanticsNode()
        val label = avatar.config[SemanticsActions.OnClick].label
        return "phase=${phonePopupPhase(task, false)}, action=${task.action}, click=$label, pixels=$pixels"
    }

    @Test fun sameConversationReturnsToCompactWhenItsNextPhoneStepStarts() {
        content()
        assertEquals((352 * density).toInt(), pixels.width)
        compose.runOnIdle { task = task.copy(running = true, action = "tap", reply = "") }
        settle()
        assertEquals(stateDescription(), (120 * density).toInt(), pixels.width)
        assertEquals((72 * density).toInt(), pixels.height)
    }

    @Test fun manualExpansionSurvivesToolUpdatesButResumeReturnsToCompact() {
        task = task.copy(running = true, action = "tap", reply = "")
        content()
        assertEquals(stateDescription(), (120 * density).toInt(), pixels.width)
        compose.onNodeWithContentDescription("Assistant, phone use controls").performClick()
        settle()
        assertEquals((352 * density).toInt(), pixels.width)
        compose.runOnIdle { task = task.copy(action = "scroll") }
        settle()
        assertEquals((352 * density).toInt(), pixels.width)
        compose.runOnIdle { task = task.copy(paused = true) }
        settle()
        compose.runOnIdle { task = task.copy(paused = false) }
        settle()
        assertEquals(stateDescription(), (120 * density).toInt(), pixels.width)
    }
}
