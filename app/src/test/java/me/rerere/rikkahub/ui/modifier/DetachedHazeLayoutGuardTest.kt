package me.rerere.rikkahub.ui.modifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetachedHazeLayoutGuardTest {
    @Test
    fun detectsDetachedLayoutCoordinateMessages() {
        assertTrue(
            isDetachedLayoutCoordinate(
                IllegalStateException("LayoutCoordinate operations are only valid when isAttached is true")
            )
        )
        assertFalse(
            isDetachedLayoutCoordinate(IllegalStateException("something else"))
        )
        assertFalse(isDetachedLayoutCoordinate(RuntimeException("isAttached")))
    }

    @Test
    fun swallowsDetachedLayoutCoordinateFailures() {
        val result = runCatchingDetachedLayout<Int> {
            throw IllegalStateException("LayoutCoordinate operations are only valid when isAttached is true")
        }
        assertNull(result)
    }

    @Test(expected = IllegalStateException::class)
    fun reraisesUnrelatedIllegalStateExceptions() {
        runCatchingDetachedLayout<Unit> {
            throw IllegalStateException("unrelated")
        }
    }

    @Test
    fun hazeCrashRequiresHazeFramesInTheStack() {
        val detached = IllegalStateException(
            "LayoutCoordinate operations are only valid when isAttached is true"
        )
        assertTrue(isDetachedLayoutCoordinate(detached))
        assertFalse(isHazeDetachedCoordinateCrash(detached))

        val hazeCrash = IllegalStateException(
            "LayoutCoordinate operations are only valid when isAttached is true"
        ).apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "dev.chrisbanes.haze.UtilsKt",
                    "positionForHaze",
                    "Utils.kt",
                    20,
                )
            )
        }
        assertTrue(isHazeDetachedCoordinateCrash(hazeCrash))
    }
}
