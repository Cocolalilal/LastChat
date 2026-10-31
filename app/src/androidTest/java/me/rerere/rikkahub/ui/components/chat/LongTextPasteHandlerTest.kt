package me.rerere.rikkahub.ui.components.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import me.rerere.rikkahub.data.datastore.LongTextPasteMode
import me.rerere.rikkahub.ui.components.ai.rememberLongTextPasteHandler
import me.rerere.rikkahub.ui.components.ui.AppToasterState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LongTextPasteHandlerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largePasteWaitsForChoiceAndReplacesOnlyTheSelectedText() {
        val state = ChatInputState().apply { setMessageText("before REPLACE after") }
        val paste = "Entire pasted text 😀\n".repeat(500)
        composeRule.setContent {
            CompositionLocalProvider(LocalToaster provides AppToasterState()) {
                MaterialTheme {
                    TextField(
                        state = state.textContent,
                        inputTransformation = rememberLongTextPasteHandler(
                            state = state,
                            mode = LongTextPasteMode.ASK,
                            enabled = true,
                            onImportStarted = {},
                            onImportFinished = {},
                        ),
                        modifier = Modifier.testTag("composer"),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("composer").performClick()
        composeRule.runOnIdle { state.textContent.edit { selection = TextRange(7, 14) } }
        composeRule.onNodeWithTag("composer").performTextInput(paste)

        composeRule.onNodeWithText("Large text").assertExists()
        composeRule.runOnIdle {
            assertEquals("before REPLACE after", state.textContent.text.toString())
            assertEquals(TextRange(7, 14), state.textContent.selection)
        }
        composeRule.onNodeWithText("Paste as text").performClick()
        composeRule.runOnIdle {
            assertEquals("before $paste after", state.textContent.text.toString())
            assertEquals(TextRange(7 + paste.length), state.textContent.selection)
            assertEquals(0, state.messageContent.size)
        }
    }
}
