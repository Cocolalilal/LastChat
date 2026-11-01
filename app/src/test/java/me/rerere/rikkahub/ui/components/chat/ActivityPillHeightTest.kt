package me.rerere.rikkahub.ui.components.chat

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ActivityPillHeightTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun minimizedReasoningPillWithLongTitleStaysAt36dp() {
        val longTitle = "Folie 6: \"Das bekannteste Motiv des Saarlandes ist die Saarschleife bei Mettlach\" und viele weitere Details"
        val state = ActivityState.Reasoning(
            startTimeMs = System.currentTimeMillis() - 5000,
            title = longTitle,
            reasoningText = "Some internal thought"
        )

        compose.setContent {
            MaterialTheme {
                ActivityPillRow(
                    state = state,
                    onClick = {},
                    maxBubbleWidth = 320.dp
                )
            }
        }

        compose.onNodeWithTag("activity_pill_reasoning")
            .assertHeightIsEqualTo(36.dp)
    }

    @Test
    fun minimizedToolUsePillWithLongTitleStaysAt36dp() {
        val state = ActivityState.ToolUse(
            toolName = "workspace_read_file",
            displayName = "Very long tool displayName that should never cause the minimized pill to expand vertically"
        )

        compose.setContent {
            MaterialTheme {
                ActivityPillRow(
                    state = state,
                    onClick = {},
                    maxBubbleWidth = 320.dp
                )
            }
        }

        compose.onNodeWithTag("activity_pill_workspace")
            .assertHeightIsEqualTo(36.dp)
    }
}
