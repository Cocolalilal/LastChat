package me.rerere.rikkahub.phoneuse

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PhoneUseAvatarShapeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var active by mutableStateOf(false)
    private var animate by mutableStateOf(true)
    private lateinit var outline: Shape
    // Geometry is measured at 100px: this permits at most 0.6dp of contour motion per
    // display frame in the actual 40dp avatar, while rejecting an endpoint shape swap.
    private val maxFrameContourChange = 1.5f

    private fun content() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = rememberPhoneUseShape(active, animate)
            SideEffect { outline = shape }
            Box(Modifier.size(40.dp).clip(shape).background(Color.Blue))
        }
        frames(2)
    }

    private fun deviation(): Float {
        val path = (outline.createOutline(Size(100f, 100f), LayoutDirection.Ltr, Density(1f)) as Outline.Generic)
            .path.asAndroidPath().approximate(0.01f)
        return (path.indices step 3).maxOf { index ->
            assertTrue("A rotating contour must remain inside the avatar's clipping bounds",
                path[index + 1] in -0.15f..100.15f && path[index + 2] in -0.15f..100.15f)
            abs(hypot(path[index + 1] - 50f, path[index + 2] - 50f) - 50f)
        }
    }

    private fun frames(count: Int): List<Float> = List(count) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        deviation()
    }

    @Test fun staticAvatarMorphIsVisibleWithinHalfASecond() {
        content()
        assertTrue("Idle silhouette must be a full circle: ${deviation()}", deviation() < 0.15f)
        compose.runOnIdle { active = true }
        val samples = frames(30)
        assertTrue("A working profile picture must visibly morph without a multi-second wait: $samples",
            samples.last() > 1f)
    }

    @Test fun completionReturnsFromItsCurrentOutlineAlongsideThePillSpring() {
        active = true
        content()
        frames(35)
        val before = deviation()
        assertTrue(before > 1f)
        compose.runOnIdle { active = false }
        val samples = frames(36)
        assertTrue("Finishing must preserve the current contour on its first frame", samples.first() > 0.8f)
        (listOf(before) + samples).zipWithNext().forEach { (a, b) ->
            assertTrue("The outline must settle continuously instead of swapping to CircleShape: $a -> $b",
                abs(a - b) < maxFrameContourChange)
        }
        assertTrue("Circle return must finish with the pill expansion, without slow-motion tail", samples.last() < 0.15f)
    }

    @Test fun loopChangesVariationsContinuouslyAndCanReverseAnUnfinishedCompletion() {
        active = true
        content()
        val loop = frames(360)
        loop.zipWithNext().forEach { (a, b) ->
            assertTrue("Contour changed abruptly: $a -> $b", abs(a - b) < maxFrameContourChange)
        }
        assertTrue(loop.max() > 1f)
        compose.runOnIdle { active = false }
        frames(4)
        val before = deviation()
        compose.runOnIdle { active = true }
        val resumed = frames(2)
        assertTrue("Resuming must retain its partially settled shape",
            abs(before - resumed.first()) < maxFrameContourChange)
        frames(30)
        compose.runOnIdle { animate = false }
        frames(2)
        assertTrue("Reduced motion must stop the loop at a circle", deviation() < 0.15f)
    }

    @Test fun contourRotatesAtTheNormalChatSpeedAndSettlesWithoutAFullRewind() {
        var angle = 0f
        active = true
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val current = rememberPhoneUseRotation(active, animate)
            SideEffect { angle = current }
        }
        fun step() {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        repeat(3) { step() }
        val startTime = compose.mainClock.currentTime
        var previous = angle
        var travelled = 0f
        repeat(120) {
            step()
            val delta = ((angle - previous + 540f) % 360f) - 180f
            assertTrue("Rotation must stay continuous through the angle wrap: $delta", delta in 0f..2.1f)
            travelled += delta
            previous = angle
        }
        val expected = 120f * (compose.mainClock.currentTime - startTime) / 1000f
        assertTrue("Phone use must match chat's 120 degrees per second: $travelled vs $expected",
            abs(travelled - expected) < 2.1f)
        compose.runOnIdle { active = false }
        val before = angle
        step()
        assertTrue("Angular settling must remain continuous: $before -> $angle", abs(before - angle) < 10f)
        repeat(40) { step() }
        assertTrue("Completion should settle directly toward zero", abs(angle) < 0.02f)
    }
}
