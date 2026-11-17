package me.rerere.rikkahub.ui.components.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class DebouncedTextFieldPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun displayedInput(): String {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.text.text
    }

    @Test fun compactRequestCannotBypassSecureMaskingOrItsVisibilityControl() {
        val secret = "private-value"
        compose.setContent {
            MaterialTheme {
                DebouncedTextField(secret, onValueChange = {}, isSecure = true, outlined = false)
            }
        }
        assertEquals("•".repeat(secret.length), displayedInput())
        compose.onNodeWithContentDescription("Show").performClick()
        assertEquals(secret, displayedInput())
        compose.onNodeWithContentDescription("Hide").assertExists()
    }

    @Test fun compactRequestKeepsMaterialLabelsValidationAndTrailingContent() {
        compose.setContent {
            MaterialTheme {
                DebouncedTextField(
                    "value", onValueChange = {}, outlined = false, label = "Label", isError = true,
                    trailingIcon = { Text("Suffix") },
                )
            }
        }
        compose.onNodeWithText("Label").assertExists()
        compose.onNodeWithText("Suffix").assertExists()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }
}
