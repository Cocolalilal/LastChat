package me.rerere.rikkahub.ui.components.chat

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class ActivityTimelineInlineScrollHandoffTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstTopEdgeSwipeStaysInTimelineAndSecondSwipeMovesParentChat() {
        var outerState: LazyListState? = null

        composeRule.setContent {
            outerState = rememberLazyListState(initialFirstVisibleItemIndex = PANEL_ITEM_INDEX)
            TimelineScrollHandoffHost(
                outerState = outerState!!,
                innerState = rememberLazyListState(),
            )
        }

        composeRule.waitForIdle()
        val baseline = composeRule.captureOuterPosition(outerState)

        composeRule.onNodeWithTag("activity_timeline_list").performTouchInput {
            swipeDown()
        }
        composeRule.waitForIdle()
        composeRule.assertOuterPositionEquals(outerState, baseline)

        composeRule.finishGestureWindow()

        composeRule.onNodeWithTag("activity_timeline_list").performTouchInput {
            swipeDown()
        }
        composeRule.waitForIdle()
        composeRule.assertOuterPositionChanged(outerState, baseline)
    }

    @Test
    fun firstBottomEdgeSwipeStaysInTimelineAndSecondSwipeMovesParentChat() {
        var outerState: LazyListState? = null

        composeRule.setContent {
            outerState = rememberLazyListState(initialFirstVisibleItemIndex = PANEL_ITEM_INDEX)
            TimelineScrollHandoffHost(
                outerState = outerState!!,
                innerState = rememberLazyListState(initialFirstVisibleItemIndex = timelineEntries().lastIndex),
            )
        }

        composeRule.waitForIdle()
        val baseline = composeRule.captureOuterPosition(outerState)

        composeRule.onNodeWithTag("activity_timeline_list").performTouchInput {
            swipeUp()
        }
        composeRule.waitForIdle()
        composeRule.assertOuterPositionEquals(outerState, baseline)

        composeRule.finishGestureWindow()

        composeRule.onNodeWithTag("activity_timeline_list").performTouchInput {
            swipeUp()
        }
        composeRule.waitForIdle()
        composeRule.assertOuterPositionChanged(outerState, baseline)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.finishGestureWindow() {
        waitForIdle()
        SystemClock.sleep(220L)
        waitForIdle()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.captureOuterPosition(
        outerState: LazyListState?
    ): Pair<Int, Int> {
        var position = 0 to 0
        runOnIdle {
            val state = requireNotNull(outerState)
            position = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }
        return position
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.assertOuterPositionEquals(
        outerState: LazyListState?,
        expected: Pair<Int, Int>
    ) {
        val actual = captureOuterPosition(outerState)
        assertEquals(expected, actual)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.assertOuterPositionChanged(
        outerState: LazyListState?,
        baseline: Pair<Int, Int>
    ) {
        val actual = captureOuterPosition(outerState)
        assertNotEquals(baseline, actual)
    }
}

private const val PANEL_ITEM_INDEX = 8

@Composable
private fun TimelineScrollHandoffHost(
    outerState: LazyListState,
    innerState: LazyListState,
) {
    MaterialTheme {
        LazyColumn(
            state = outerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("outer_chat_list")
        ) {
            items((0..16).toList()) { index ->
                if (index == PANEL_ITEM_INDEX) {
                    ActivityTimelinePanel(
                        entries = timelineEntries(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        scrollHandoffMode = TimelineScrollHandoffMode.EdgeGatedToParent,
                        listState = innerState,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .testTag("outer_item_$index")
                    )
                }
            }
        }
    }
}

private fun timelineEntries(): List<TimelineEntry> {
    return List(24) { index ->
        TimelineEntry.Reasoning(
            id = "reasoning_$index",
            content = "Timeline entry $index " + "x".repeat(180),
            durationMs = 1_000L,
        )
    }
}
