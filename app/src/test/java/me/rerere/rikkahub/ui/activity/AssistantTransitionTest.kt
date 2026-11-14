package me.rerere.rikkahub.ui.activity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.phoneuse.PhoneUseTask
import me.rerere.rikkahub.ui.components.chat.ActivityState
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AssistantTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val task = PhoneUseTask(Uuid.random(), Uuid.random(), true, "observe")
    private var messages by mutableStateOf(emptyList<UIMessage>())
    private var compact by mutableStateOf(false)
    private var panelVisible by mutableStateOf(true)
    private var viewportHeight = 700
    private var panelPixels = 0
    private var inputPixels = 0
    private var hidden = false
    private var compactSettled = false
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun content() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    Box(Modifier.size(400.dp, viewportHeight.dp)) {
                        Column(Modifier.align(Alignment.BottomCenter)) {
                            TranscriptPanel(assistantAvatar = { Box(it) }, messages = messages,
                                activityState = ActivityState.Waiting, isGenerating = true, isSpeaking = false,
                                onOpenInApp = {}, onCopy = {}, onRegenerate = {}, onToggleTts = {},
                                compact = compact, visible = panelVisible, phoneTask = task,
                                modifier = Modifier.weight(1f, fill = false).onSizeChanged { panelPixels = it.height },
                                onCompactSettled = { compactSettled = it })
                            AssistantInputReveal(visible = !compact, onHidden = { hidden = it },
                                modifier = Modifier.onSizeChanged { inputPixels = it.height }) {
                                Box(Modifier.fillMaxWidth().height(80.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun frames(count: Int): List<Pair<Int, Int>> = buildList {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            add(panelPixels to inputPixels)
        }
    }

    private fun assertContinuous(samples: List<Pair<Int, Int>>) {
        samples.forEach { (panel, input) ->
            assertTrue("Panel outside its surface: $samples", panel in (55 * density).toInt()..(221 * density).toInt())
            assertTrue("Input outside its reveal: $samples", input in 0..(81 * density).toInt())
        }
        samples.zipWithNext().forEach { (a, b) ->
            assertTrue("Panel jumped between frames: $samples", abs(a.first - b.first) < 45 * density)
            assertTrue("Input jumped between frames: $samples", abs(a.second - b.second) < 25 * density)
        }
    }

    @Test fun panelAndInputReachThePhonePoseTogetherAndRestoreSmoothly() {
        content()
        compose.runOnIdle { compact = true }
        val collapse = frames(50)
        assertContinuous(collapse)
        assertEquals((56 * density).toInt(), panelPixels)
        assertEquals(0, inputPixels)
        assertTrue(hidden && compactSettled)
        compose.runOnIdle { compact = false }
        val expansion = frames(50)
        assertContinuous(expansion)
        assertEquals((220 * density).toInt(), panelPixels)
        assertEquals((80 * density).toInt(), inputPixels)
        assertFalse(hidden || compactSettled)
    }

    @Test fun reversingThePhoneTransitionKeepsTheCurrentVisualPosition() {
        content()
        val samples = mutableListOf(panelPixels to inputPixels)
        for (target in listOf(true, false, true, false)) {
            compose.runOnIdle { compact = target }
            samples += frames(7)
        }
        samples += frames(50)
        assertContinuous(samples)
        assertEquals((220 * density).toInt(), panelPixels)
        assertEquals((80 * density).toInt(), inputPixels)
    }

    @Test fun resizeHandleOffersAccessibleExpansionWithoutChangingTheWindow() {
        content()
        val action = compose.onNodeWithContentDescription("Resize assistant").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first { it.label == "Minimize" }
        compose.runOnIdle { action.action() }
        frames(50)
        assertEquals((76 * density).toInt(), panelPixels)
        assertEquals((80 * density).toInt(), inputPixels)
    }
    @Test fun fastResizeAndGrabDuringSettlingStayWithinTheRoundedPanel() {
        content()
        compose.onNodeWithContentDescription("Resize assistant").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -180f))
            advanceEventTime(8)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -240f))
            up()
        }
        val first = frames(5)
        compose.onNodeWithContentDescription("Resize assistant").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 180f))
            advanceEventTime(8)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 240f))
            up()
        }
        val second = frames(50)
        val maximum = compose.activity.resources.configuration.screenHeightDp * 0.66f * density
        (first + second).forEach { (panel, input) ->
            assertTrue(panel >= 75 * density && panel <= maximum + 1)
            assertEquals((80 * density).toInt(), input)
        }
        second.zipWithNext().forEach { (a, b) ->
            assertTrue("A released resize must settle continuously: $second", abs(a.first - b.first) < 60 * density)
        }
    }

    @Test fun newlyVisibleTranscriptGrowsItsOwnSurfaceInsteadOfClippingAnOversizedChild() {
        panelVisible = false
        content()
        assertEquals(0, panelPixels)
        compose.runOnIdle { panelVisible = true }
        val samples = frames(50)
        samples.forEach { assertTrue(it.first in 0..(221 * density).toInt()) }
        samples.zipWithNext().forEach { (a, b) -> assertTrue(abs(a.first - b.first) < 45 * density) }
        assertEquals((220 * density).toInt(), panelPixels)
        compose.runOnIdle { panelVisible = false }
        frames(50)
        assertEquals(0, panelPixels)
        assertEquals((80 * density).toInt(), inputPixels)
    }

    @Test fun constrainedExpandedPanelStartsPhoneMorphFromItsVisibleHeight() {
        viewportHeight = 280
        content()
        val expand = compose.onNodeWithContentDescription("Resize assistant").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first { it.label == "Expand" }
        compose.runOnIdle { expand.action() }
        frames(50)
        assertEquals((200 * density).toInt(), panelPixels)
        compose.runOnIdle { compact = true }
        val early = frames(4)
        assertTrue("A constrained surface must move immediately, without traversing invisible height: $early",
            early.last().first < (199 * density).toInt())
        frames(50)
        assertEquals((56 * density).toInt(), panelPixels)
        assertEquals(0, inputPixels)
    }

    @Test fun grabbingAConstrainedPanelUsesItsVisibleHeightAndKeepsInputAvailable() {
        viewportHeight = 280
        content()
        assertEquals((200 * density).toInt(), panelPixels)
        val before = panelPixels
        compose.onNodeWithContentDescription("Resize assistant").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 30 * density))
        }
        frames(2)
        assertTrue("Dragging must start at the rendered size, not its unconstrained target",
            panelPixels < before - 10 * density)
        assertEquals((80 * density).toInt(), inputPixels)
        compose.onNodeWithContentDescription("Resize assistant").performTouchInput { up() }
        frames(50)
        assertEquals((80 * density).toInt(), inputPixels)
    }

    @Test fun latestTwoMessagesGrowThePanelWhileOlderHistoryRemainsScrollable() {
        val question = UIMessage(role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text((1..6).joinToString("\n") { "Question line $it" })))
        val answer = UIMessage(role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text((1..4).joinToString("\n\n") { "Answer paragraph $it" })))
        messages = listOf(question, answer)
        content()
        frames(70)
        val readableHeight = panelPixels
        assertTrue("The latest exchange should grow beyond the old fixed height", readableHeight > 300 * density)
        val maximum = compose.activity.resources.configuration.screenHeightDp * 0.66f * density
        assertTrue(readableHeight <= maximum + 1)
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { directory ->
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height,
                    android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val file = java.io.File(directory, "assistant-latest-exchange.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val old = UIMessage(role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text((1..30).joinToString("\n") { "Older line $it" })))
        compose.runOnIdle { messages = listOf(old, question, answer) }
        frames(70)
        assertEquals("Older history must not make the surface fill the screen", readableHeight, panelPixels)
        val minimize = compose.onNodeWithContentDescription("Resize assistant").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first { it.label == "Minimize" }
        compose.runOnIdle { minimize.action() }
        frames(50)
        assertEquals("Automatic sizing must respect a user's resize", (76 * density).toInt(), panelPixels)
    }

}
