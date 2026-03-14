package me.rerere.rikkahub.ui.components.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityTimelineScrollHandoffTest {
    @Test
    fun firstHitAtTopBlocksParentHandoff() {
        val state = TimelineScrollHandoffState()
            .beginGesture(TimelineScrollDirection.TowardTop)

        val (nextState, decision) = state.onEdgeReached(TimelineScrollEdge.Top)

        assertEquals(TimelineScrollHandoffDecision.ConsumeInsidePanel, decision)
        assertEquals(TimelineScrollEdge.Top, nextState.armedEdge)
        assertEquals(1L, nextState.armedSessionId)
    }

    @Test
    fun secondUpwardGestureAtTopReleasesParentHandoff() {
        val armedState = TimelineScrollHandoffState()
            .beginGesture(TimelineScrollDirection.TowardTop)
            .onEdgeReached(TimelineScrollEdge.Top)
            .first
            .endGesture()
            .beginGesture(TimelineScrollDirection.TowardTop)

        val (_, decision) = armedState.onEdgeReached(TimelineScrollEdge.Top)

        assertEquals(TimelineScrollHandoffDecision.ReleaseToParent, decision)
    }

    @Test
    fun firstHitAtBottomBlocksParentHandoff() {
        val state = TimelineScrollHandoffState()
            .beginGesture(TimelineScrollDirection.TowardBottom)

        val (nextState, decision) = state.onEdgeReached(TimelineScrollEdge.Bottom)

        assertEquals(TimelineScrollHandoffDecision.ConsumeInsidePanel, decision)
        assertEquals(TimelineScrollEdge.Bottom, nextState.armedEdge)
        assertEquals(1L, nextState.armedSessionId)
    }

    @Test
    fun secondDownwardGestureAtBottomReleasesParentHandoff() {
        val armedState = TimelineScrollHandoffState()
            .beginGesture(TimelineScrollDirection.TowardBottom)
            .onEdgeReached(TimelineScrollEdge.Bottom)
            .first
            .endGesture()
            .beginGesture(TimelineScrollDirection.TowardBottom)

        val (_, decision) = armedState.onEdgeReached(TimelineScrollEdge.Bottom)

        assertEquals(TimelineScrollHandoffDecision.ReleaseToParent, decision)
    }

    @Test
    fun reversingDirectionClearsLatchedEdge() {
        val reversedState = TimelineScrollHandoffState()
            .beginGesture(TimelineScrollDirection.TowardTop)
            .onEdgeReached(TimelineScrollEdge.Top)
            .first
            .endGesture()
            .beginGesture(TimelineScrollDirection.TowardBottom)

        assertNull(reversedState.armedEdge)
        assertNull(reversedState.armedSessionId)
    }

    @Test
    fun movingAwayFromEdgeReArmsGate() {
        val resetState = TimelineScrollHandoffState()
            .beginGesture(TimelineScrollDirection.TowardTop)
            .onEdgeReached(TimelineScrollEdge.Top)
            .first
            .endGesture()
            .beginGesture(TimelineScrollDirection.TowardTop)
            .onEdgeReached(TimelineScrollEdge.Top)
            .first
            .onTimelineMoved()
            .endGesture()
            .beginGesture(TimelineScrollDirection.TowardTop)

        val (_, decision) = resetState.onEdgeReached(TimelineScrollEdge.Top)

        assertEquals(TimelineScrollHandoffDecision.ConsumeInsidePanel, decision)
    }
}
