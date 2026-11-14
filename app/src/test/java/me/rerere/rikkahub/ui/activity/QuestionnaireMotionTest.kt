package me.rerere.rikkahub.ui.activity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import me.rerere.rikkahub.data.ai.tools.AskUserOption
import me.rerere.rikkahub.data.ai.tools.AskUserQuestion
import me.rerere.rikkahub.data.ai.tools.AskUserQuestionnaire
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.AnimatedQuestionnaireCard
import me.rerere.rikkahub.ui.components.ai.ComposerLeadingReveal
import me.rerere.rikkahub.ui.components.ai.QuestionnaireCardState
import me.rerere.rikkahub.ui.components.ai.rememberComposerLeadingProgress
import me.rerere.rikkahub.ui.components.chat.LastChatComposerRow
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The actual card and leading-pill components used by MinimalChatInput in the retained assistant scene. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class QuestionnaireMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val questions = AskUserQuestionnaire(listOf(
        AskUserQuestion("route", "Choose a route", listOf(AskUserOption("First option"))),
        AskUserQuestion("details", "Choose the details for the next part of this task",
            listOf(AskUserOption("Second option", "More information"),
                AskUserOption("Third option", "A different choice"), AskUserOption("Fourth option"))),
    ))
    private val first = QuestionnaireCardState("tool-one", questions, 0, null)
    private var state by mutableStateOf<QuestionnaireCardState?>(null)
    private var cardHeight = 0
    private var inputX = 0f
    private var answered = 0
    private var leadingCoordinates: LayoutCoordinates? = null
    private var inputCoordinates: LayoutCoordinates? = null
    private var direction = LayoutDirection.Ltr
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun content() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Column(Modifier.size(360.dp, 760.dp)) {
                        AnimatedQuestionnaireCard(state, onPrevious = {}, onNext = {}, onDismiss = {},
                            onSelectOption = { answered++ },
                            modifier = Modifier.onSizeChanged { cardHeight = it.height })
                        val progress = rememberComposerLeadingProgress(state == null)
                        LastChatComposerRow(spacing = 8.dp * progress) {
                            ComposerLeadingReveal(progress, state == null) {
                                Box(Modifier.size(48.dp).onGloballyPositioned { leadingCoordinates = it })
                            }
                            Box(Modifier.weight(1f).height(48.dp).onGloballyPositioned {
                                inputX = it.positionInParent().x
                                inputCoordinates = it
                            })
                        }
                    }
                }
            }
        }
        frames(40)
    }

    private fun frames(count: Int): List<Pair<Int, Float>> = List(count) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val leading = leadingCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
        val input = inputCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
        // Compose maps a zero-scale layer to Rect.Zero, regardless of its RTL anchor.
        // A contour with no drawn area cannot overlap; check every non-empty visual bound.
        if (leading != null && input != null && !leading.isEmpty) {
            if (direction == LayoutDirection.Ltr) {
                assertTrue("The shrinking add pill must fit its slot without crossing the input surface",
                    leading.right <= input.left + 1f)
            } else {
                assertTrue("The RTL add pill must stay beyond the input: leading=$leading input=$input",
                    leading.left >= input.right - 1f)
            }
        }
        cardHeight to inputX
    }

    private fun continuous(samples: List<Pair<Int, Float>>) {
        samples.zipWithNext().forEach { (before, after) ->
            assertTrue("Question surface jumped between frames: $samples", abs(after.first - before.first) < 60 * density)
            assertTrue("Composer leading pill/gap jumped: $samples", abs(after.second - before.second) < 12 * density)
        }
    }

    @Test fun questionKeepsItsOutgoingContentAndMorphsTheComposerWithoutAnEightDpCut() {
        content()
        assertEquals(0, cardHeight)
        assertEquals(56 * density, inputX, 1f)
        compose.runOnIdle { state = first }
        val opening = frames(40)
        continuous(opening)
        val openHeight = cardHeight
        assertTrue(openHeight > 100 * density)
        assertEquals(0f, inputX, 1f)
        val oldAnswer = compose.onNodeWithText("First option").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action
        compose.runOnIdle { state = null }
        val early = frames(2)
        assertTrue("Nulling the pending tool must retain its card through the exit", cardHeight > openHeight / 2)
        compose.onNodeWithText("Choose a route", useUnmergedTree = true).assertExists()
        compose.runOnIdle { oldAnswer?.invoke() }
        assertEquals("A queued click cannot answer a questionnaire that has finished", 0, answered)
        val closing = early + frames(40)
        continuous(closing)
        assertEquals(0, cardHeight)
        assertEquals(56 * density, inputX, 1f)
        assertTrue("Both transitions need real intermediate frames",
            opening.any { it.first in 1 until openHeight } && closing.any { it.first in 1 until openHeight })
    }

    @Test fun pageChangesAndReversingDismissalKeepOneContinuousSurface() {
        content()
        compose.runOnIdle { state = first }
        frames(40)
        val shortHeight = cardHeight
        compose.runOnIdle { state = first.copy(currentIndex = 1) }
        val page = frames(40)
        val tallHeight = cardHeight
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { directory ->
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height,
                    android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val file = java.io.File(directory, "questionnaire-expanded.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        assertTrue(tallHeight > shortHeight + 30 * density)
        continuous(page)
        assertTrue("Page size must morph rather than swap", page.any { it.first in shortHeight + 1 until tallHeight })
        compose.runOnIdle { state = null }
        val leaving = frames(6)
        compose.runOnIdle { state = first.copy(toolCallId = "tool-two", currentIndex = 1) }
        val returning = frames(40)
        continuous(leaving + returning)
        assertEquals(tallHeight, cardHeight)
        assertEquals(0f, inputX, 1f)
        compose.onNodeWithText("Second option").assertExists()
    }

    @Test fun reducedMotionSettlesBothCardAndComposerImmediately() {
        android.provider.Settings.Global.putFloat(compose.activity.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        content()
        compose.runOnIdle { state = first }
        // AnimatedContent must first subcompose and measure the incoming page. Snap
        // removes interpolation, not that Android measure pass; sample its first real size.
        repeat(6) { if (cardHeight == 0) frames(1) }
        val height = cardHeight
        assertTrue(height > 0)
        assertEquals(0f, inputX, 1f)
        frames(30).forEach { (measured, _) -> assertEquals(height, measured) }
        compose.runOnIdle { state = null }
        repeat(6) { if (cardHeight != 0) frames(1) }
        assertEquals(0, cardHeight)
        assertEquals(56 * density, inputX, 1f)
    }

    @Test fun rtlLeadingPillRemainsInsideItsSlotWhileInputExpandsAndReturns() {
        direction = LayoutDirection.Rtl
        content()
        fun width() = requireNotNull(inputCoordinates).boundsInRoot().width
        val narrow = width()
        assertEquals(304 * density, narrow, 1f)
        compose.runOnIdle { state = first }
        val expanding = List(40) { frames(1); width() }
        assertEquals(360 * density, width(), 1f)
        compose.runOnIdle { state = null }
        val returning = List(40) { frames(1); width() }
        assertEquals(narrow, width(), 1f)
        (expanding + returning).zipWithNext().forEach { (before, after) ->
            assertTrue("RTL input width must follow the pill continuously", abs(after - before) < 12 * density)
        }
        assertTrue(expanding.any { it > narrow + 1f && it < 360 * density - 1f })
        assertTrue(returning.any { it > narrow + 1f && it < 360 * density - 1f })
    }
}
