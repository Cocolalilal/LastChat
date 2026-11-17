package me.rerere.rikkahub.ui.components.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import me.rerere.rikkahub.ui.pages.setting.components.AddonPromptField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PromptEditorBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun expandUsesTheLatestInlineDraftAndBackCommitsTheExpandedDraft() {
        var value by mutableStateOf("Original prompt")
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            AddonPromptField("Prompt", value, onValueChange = { value = it }, stateKey = "prompt")
        } } }
        compose.onNodeWithContentDescription("Prompt").performTextReplacement("Latest inline draft")
        compose.onNodeWithContentDescription("Expand Prompt").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Prompt editor").assertTextEquals("Latest inline draft")
            .performTextReplacement("Latest expanded draft")
        compose.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertEquals("Latest expanded draft", value)
        compose.onNodeWithContentDescription("Prompt").assertTextEquals("Latest expanded draft")
    }

    @Test fun leavingInsideTheDebounceWindowKeepsTheLastKeystrokes() {
        var value = "Original prompt"
        var shown by mutableStateOf(true)
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            if (shown) AddonPromptField("Prompt", value, onValueChange = { value = it }, stateKey = "prompt")
        } } }
        compose.onNodeWithContentDescription("Prompt").performTextReplacement("Keep these last keystrokes")
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        assertEquals("Keep these last keystrokes", value)
    }
    @Test fun movingBetweenFieldsDoesNotReplaceThePendingDraftWithItsOldValue() {
        var first by mutableStateOf("Old name")
        var second by mutableStateOf("")
        compose.setContent { MaterialTheme { Column {
            DebouncedTextField(first, { first = it }, modifier = Modifier.testTag("name"))
            DebouncedTextField(second, { second = it }, modifier = Modifier.testTag("description"))
        } } }
        compose.onNodeWithTag("name").performClick().performTextReplacement("Latest name")
        compose.onNodeWithTag("description").performClick().performTextReplacement("Description")
        compose.waitForIdle()
        assertEquals("Latest name", first)
        compose.onNodeWithTag("name").assertTextEquals("Latest name")
    }

    @Test fun restoringWhileExpandedKeepsTheExpandedDraft() {
        var value by mutableStateOf("Original prompt")
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            AddonPromptField("Prompt", value, onValueChange = { value = it }, stateKey = "prompt")
        } } }
        compose.onNodeWithContentDescription("Expand Prompt").performClick()
        compose.onNodeWithContentDescription("Prompt editor").performTextReplacement("Expanded draft survives rotation")
        compose.runOnIdle {
            (compose.activity.lifecycle as androidx.lifecycle.LifecycleRegistry)
                .handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            (compose.activity.lifecycle as androidx.lifecycle.LifecycleRegistry)
                .handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals("Expanded draft survives rotation", value)
        compose.onNodeWithContentDescription("Prompt").assertTextEquals("Expanded draft survives rotation")
    }

}
