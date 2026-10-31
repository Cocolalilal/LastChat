package me.rerere.rikkahub.ui.components.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ExpandableMessageContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longMessageExpandsInChunksAndThenMinimizes() {
        composeRule.setContent {
            MaterialTheme {
                ExpandableMessageContent(
                    content = "a".repeat(9_000),
                    fadeColor = MaterialTheme.colorScheme.background,
                ) { visible -> Text("Rendered ${visible.length} characters") }
            }
        }
        composeRule.onNodeWithText("Rendered 1200 characters").assertExists()
        composeRule.onNodeWithText("Show more").performClick()
        composeRule.onNodeWithText("Rendered 5200 characters").assertExists()
        composeRule.onNodeWithText("Show more").performClick()
        composeRule.onNodeWithText("Rendered 9000 characters").assertExists()
        composeRule.onNodeWithText("Show more").assertDoesNotExist()
        composeRule.onNodeWithText("Show less").performClick()
        composeRule.onNodeWithText("Rendered 1200 characters").assertExists()
    }

    @Test
    fun millionCharacterMessageOnlyReachesRendererAsASmallPreview() {
        composeRule.setContent {
            MaterialTheme {
                ExpandableMessageContent(
                    content = "a".repeat(1_000_000),
                    fadeColor = MaterialTheme.colorScheme.background,
                ) { visible -> Text("Rendered ${visible.length} characters") }
            }
        }
        composeRule.onNodeWithText("Rendered 1200 characters").assertExists()
        composeRule.onNodeWithText("Show more").assertExists()
    }
}
