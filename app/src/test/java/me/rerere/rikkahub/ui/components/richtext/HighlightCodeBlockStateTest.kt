package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightCodeBlockStateTest {
    @Test
    fun incompleteCodeBlockStartsInPreviewState() {
        assertEquals(
            CodeBlockState.Preview,
            initialCodeBlockState(autoCollapse = true, completeCodeBlock = false)
        )
    }

    @Test
    fun incompletePreviewReportsExpandAffordance() {
        assertEquals(
            CodeBlockFooterAction.Expand,
            codeBlockFooterAction(CodeBlockState.Preview)
        )
    }

    @Test
    fun incompleteExpandedReportsCollapseAffordance() {
        assertEquals(
            CodeBlockFooterAction.Collapse,
            codeBlockFooterAction(CodeBlockState.Expanded)
        )
    }

    @Test
    fun completedCodeBlockHonorsAutoCollapse() {
        assertEquals(
            CodeBlockState.Collapsed,
            initialCodeBlockState(autoCollapse = true, completeCodeBlock = true)
        )
        assertEquals(
            CodeBlockState.Expanded,
            initialCodeBlockState(autoCollapse = false, completeCodeBlock = true)
        )
    }

    @Test
    fun previewUsesCollapsedHeight() {
        assertEquals(108, codeBlockMaxHeight(CodeBlockState.Preview))
        assertEquals(108, codeBlockMaxHeight(CodeBlockState.Collapsed))
        assertEquals(null, codeBlockMaxHeight(CodeBlockState.Expanded))
    }

    @Test
    fun normalizeCodeBlockLanguageStripsInfoStringsAndAliases() {
        assertEquals("typescript", normalizeCodeBlockLanguage("ts title=\"demo\""))
        assertEquals("javascript", normalizeCodeBlockLanguage("{.language-js}"))
        assertEquals("cpp", normalizeCodeBlockLanguage("c++"))
        assertEquals("csharp", normalizeCodeBlockLanguage("C#"))
        assertEquals("plaintext", normalizeCodeBlockLanguage(""))
        assertEquals("json", normalizeCodeBlockLanguage("jsonl"))
    }

    @Test
    fun userScrollingUpPausesPreviewAutoFollowUntilBottom() {
        val paused = updatePreviewAutoFollowPaused(
            currentlyPaused = false,
            isAtBottom = false,
            scrollDelta = -12,
            userScrollInProgress = true,
            programmaticScrollInProgress = false
        )
        assertTrue(paused)

        val resumed = updatePreviewAutoFollowPaused(
            currentlyPaused = paused,
            isAtBottom = true,
            scrollDelta = 0,
            userScrollInProgress = false,
            programmaticScrollInProgress = false
        )
        assertFalse(resumed)
    }
}
