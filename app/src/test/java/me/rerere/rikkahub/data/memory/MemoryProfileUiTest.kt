package me.rerere.rikkahub.data.memory

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.pages.assistant.detail.MemoryProfileSheet
import me.rerere.rikkahub.ui.pages.assistant.detail.memoryNoteTitle
import me.rerere.rikkahub.ui.theme.Typography
import me.rerere.rikkahub.ui.theme.seafoamMintColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryProfileUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(view: CharacterMemory, onOpen: (String) -> Unit = {}, onEdit: (String) -> Unit = {}, onAdd: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme(colorScheme = seafoamMintColorScheme(true), typography = Typography) {
                Surface(Modifier.fillMaxSize()) {
                    MemoryProfileSheet(view, "Cocolalilal", Avatar.Dummy, {}, onOpen, onEdit, onAdd)
                }
            }
        }
    }

    @Test fun openingEmptyProfileDoesNotOpenOrTriggerAnEditor() {
        var addCalls = 0
        show(CharacterMemory(), onAdd = { addCalls++ })
        compose.onNodeWithText("Cocolalilal").assertIsDisplayed()
        compose.onNodeWithText("No profile details saved yet.").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.runOnIdle { assertEquals(0, addCalls) }
        compose.onNodeWithText("Add details").performClick()
        compose.runOnIdle { assertEquals(1, addCalls) }
    }

    @Test fun populatedProfileReadsFactsAndOnlyEditsOnExplicitAction() {
        val note = MemoryNote("profile", "Preferences", category = "You", paragraphs = listOf(MemoryParagraph("p", "Plays the cello.", authored = true)))
        var edited: String? = null
        var opened: String? = null
        show(CharacterMemory(notes = listOf(note)), onOpen = { opened = it }, onEdit = { edited = it })
        compose.onNodeWithText("Plays the cello.").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.runOnIdle { assertEquals(null, edited); assertEquals(null, opened) }
        compose.onNodeWithText("Details").performClick()
        compose.runOnIdle { assertEquals("profile", opened); assertEquals(null, edited) }
        compose.onNodeWithContentDescription("Edit Preferences").performClick()
        compose.runOnIdle { assertEquals("profile", edited) }
    }

    @Test fun otherNotesAreNotMisrepresentedAsMissingMemory() {
        show(CharacterMemory(notes = listOf(MemoryNote("legacy", "Saved memory", paragraphs = listOf(MemoryParagraph("p", "Has four cats.", authored = true))))))
        compose.onNodeWithText("Your saved notes haven’t been organized into a profile yet.").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun legacyRowsUseDistinctContentLabelsWithoutMutatingTheirTitles() {
        val first = MemoryNote("one", "Saved memory", paragraphs = listOf(MemoryParagraph("p1", "Has four cats.")))
        val second = MemoryNote("two", "Saved memory", paragraphs = listOf(MemoryParagraph("p2", "Plays the cello.")))
        assertEquals("Has four cats.", memoryNoteTitle(first))
        assertEquals("Plays the cello.", memoryNoteTitle(second))
        assertEquals("Saved memory", first.title)
        assertFalse(memoryNoteTitle(first) == memoryNoteTitle(second))
    }
}
