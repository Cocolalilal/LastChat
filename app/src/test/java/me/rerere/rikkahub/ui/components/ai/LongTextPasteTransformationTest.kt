package me.rerere.rikkahub.ui.components.ai

import android.app.Application
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import me.rerere.rikkahub.data.datastore.LongTextPasteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LongTextPasteTransformationTest {
    private fun paste(state: TextFieldState, text: String, transformation: InputTransformation) {
        state.edit {
            val start = selection.min
            replace(start, selection.max, text)
            selection = TextRange(start + text.length)
            with(transformation) { transformInput() }
        }
    }

    @Test
    fun askInterceptsBeforeLayoutAndPreservesDraftAndReversedSelection() {
        val original = "before REPLACE after"
        val state = TextFieldState(initialText = original, initialSelection = TextRange(14, 7))
        val captures = mutableListOf<PendingLongTextPaste>()
        val pasted = "Complete Unicode content: 😀 中文\r\n".repeat(1_000)
        val transformation = longTextPasteTransformation({ true }, { LongTextPasteMode.ASK }, { 9 }, captures::add)

        paste(state, pasted, transformation)

        assertEquals(original, state.text.toString())
        assertEquals(TextRange(14, 7), state.selection)
        assertEquals(pasted, captures.single().text)
        assertEquals(TextRange(7, 14), captures.single().replacedRange)
        assertEquals(original, captures.single().originalDraft)
        assertEquals(9, captures.single().draftEpoch)
    }

    @Test
    fun rememberedTextModeAcceptsTheEntirePasteAtTheSelection() {
        val state = TextFieldState(initialText = "before REPLACE after", initialSelection = TextRange(7, 14))
        val pasted = "😀 abc\n".repeat(2_000)
        val transformation = longTextPasteTransformation({ true }, { LongTextPasteMode.TEXT }, { 0 }) {
            error("Remembered Text mode must not open a chooser")
        }
        paste(state, pasted, transformation)
        assertEquals("before $pasted after", state.text.toString())
        assertEquals(TextRange(7 + pasted.length), state.selection)
    }

    @Test
    fun attachmentModeCapturesEveryRapidPasteWithoutInsertingEitherIntoTheEditor() {
        val state = TextFieldState(initialText = "draft")
        val captures = mutableListOf<PendingLongTextPaste>()
        val transformation = longTextPasteTransformation(
            { true }, { LongTextPasteMode.ATTACHMENT }, { 0 }, captures::add,
        )
        val pasted = "a".repeat(1_000_000)
        paste(state, pasted, transformation)
        paste(state, pasted, transformation)
        assertEquals("draft", state.text.toString())
        assertEquals(listOf(pasted, pasted), captures.map { it.text })
        assertNotEquals(captures[0].id, captures[1].id)
    }

    @Test
    fun disabledHandlerLeavesQuestionnaireAndApprovalInputAlone() {
        val state = TextFieldState()
        val pasted = "a".repeat(10_000)
        val transformation = longTextPasteTransformation({ false }, { LongTextPasteMode.ASK }, { 0 }) {
            error("Disabled handler must not intercept")
        }
        paste(state, pasted, transformation)
        assertEquals(pasted, state.text.toString())
    }

    @Test
    fun shortTextIsInsertedNormallyAndManyShortLinesAreIntercepted() {
        val state = TextFieldState()
        val captures = mutableListOf<PendingLongTextPaste>()
        val transformation = longTextPasteTransformation({ true }, { LongTextPasteMode.ASK }, { 0 }, captures::add)
        paste(state, "short", transformation)
        assertEquals("short", state.text.toString())
        assertTrue(captures.isEmpty())
        paste(state, "\n".repeat(119), transformation)
        assertEquals("short", state.text.toString())
        assertEquals("\n".repeat(119), captures.single().text)
    }
}
