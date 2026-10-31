package me.rerere.rikkahub.ui.components.chat

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.rerere.rikkahub.ui.components.richtext.markdownBubbleRunCount
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExpandableMessagePresentationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sharedBubblePresentationExpandsInChunksAndMinimizes() {
        compose.setContent {
            MaterialTheme {
                val shown = rememberExpandableMessagePresentation("a".repeat(9_000))
                Column {
                    Text("Visible ${shown.visibleText.length}")
                    Button(onClick = shown.onToggle) { Text(if (shown.hasMore) "More" else "Less") }
                }
            }
        }
        compose.onNodeWithText("Visible 1200").assertExists()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Visible 5200").assertExists()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Visible 9000").assertExists()
        compose.onNodeWithText("Less").performClick()
        compose.onNodeWithText("Visible 1200").assertExists()
    }

    @Test
    fun splitBubbleCountingOnlyReceivesTheBoundedPreview() {
        val longText = "paragraph\n\n```text\ncode\n```\n\n".repeat(100_000)
        var parsedLength = 0
        var visibleRuns = 0
        var hasMore = false
        compose.setContent {
            val shown = rememberExpandableMessagePresentation(longText)
            parsedLength = shown.visibleText.length
            visibleRuns = markdownBubbleRunCount(shown.visibleText)
            hasMore = shown.hasMore
            Text("Count $visibleRuns; length $parsedLength")
        }
        compose.onNodeWithText("Count ", substring = true).assertExists()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(parsedLength in 1..1_200)
            org.junit.Assert.assertTrue(visibleRuns in 1..8)
            org.junit.Assert.assertTrue(hasMore)
        }
    }

    @Test
    fun replacingAnExpandedMessageResetsItsPreviewAndStreamingRemainsComplete() {
        val source = mutableStateOf("a".repeat(9_000))
        val streaming = mutableStateOf(false)
        compose.setContent {
            val shown = rememberExpandableMessagePresentation(source.value, streaming.value)
            Column {
                Text("Visible ${shown.visibleText.length}")
                Button(onClick = shown.onToggle) { Text("Toggle") }
            }
        }
        compose.onNodeWithText("Toggle").performClick()
        compose.onNodeWithText("Visible 5200").assertExists()
        compose.runOnIdle { source.value = "b".repeat(2_000) }
        compose.onNodeWithText("Visible 1200").assertExists()
        compose.runOnIdle { streaming.value = true }
        compose.onNodeWithText("Visible 2000").assertExists()
    }
}
