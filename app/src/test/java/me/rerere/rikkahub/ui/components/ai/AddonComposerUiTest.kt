package me.rerere.rikkahub.ui.components.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.setting.AdditionalContextContent
import me.rerere.rikkahub.ui.pages.setting.AdditionalContextEditor
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SeafoamMintThemeId
import me.rerere.rikkahub.ui.theme.Shapes
import me.rerere.rikkahub.ui.theme.Typography
import me.rerere.rikkahub.ui.theme.findPresetTheme
import me.rerere.rikkahub.ui.theme.withLastChatAmoledSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class AddonComposerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var dark by mutableStateOf(false)
    private val assistant = Assistant(name = "Mira")
    private val snippets = listOf(
        AdditionalContext(name = "Writing style", prompt = "Use short paragraphs and natural dialogue.", assistantIds = setOf(assistant.id)),
        AdditionalContext(name = "Story setting", prompt = "The story takes place in a coastal town."),
    )

    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(), LocalDarkMode provides dark) {
                MaterialTheme(
                    colorScheme = findPresetTheme(SeafoamMintThemeId).getColorScheme(dark).withLastChatAmoledSurface(dark),
                    typography = Typography, shapes = Shapes,
                ) { Surface(Modifier.fillMaxSize()) { body() } }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { directory ->
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File(directory, "$name-${if (dark) "dark" else "light"}.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun skillPagesSelectAndDismissWithoutRetainingOutgoingActions() {
        val skills = listOf("write", "rewrite", "summarize", "translate").map {
            Skill(name = it, description = "Help with $it", instructions = "Instructions")
        }
        var state by mutableStateOf<SkillCommandCardState?>(SkillCommandCardState("", skills))
        var selected: String? = null
        content {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                SkillCommandCard(state, onPage = { page -> state = state?.copy(page = page) },
                    onSelect = { selected = it.name }, onDismiss = { state = null })
                OutlinedTextField(value = "/", onValueChange = {}, modifier = Modifier.fillMaxWidth())
            }
        }
        compose.onNodeWithText("/write").assertExists()
        compose.onNodeWithText("/translate").assertDoesNotExist()
        capture("skill-commands")
        compose.runOnIdle { dark = true }
        capture("skill-commands")
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithText("/translate").performClick()
        assertEquals("translate", selected)
        val oldAction = compose.onNodeWithText("/translate").fetchSemanticsNode().config[SemanticsActions.OnClick].action
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.runOnIdle { selected = null; oldAction?.invoke() }
        assertEquals(null, selected)
        compose.onNodeWithText("/translate").assertDoesNotExist()
    }

    @Test fun contextListOpensEditorWithoutAnInlineToggleOrSheet() {
        var selected: AdditionalContext? = null
        content { AdditionalContextContent(Settings(additionalContexts = snippets), onEdit = { selected = it }) }
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        capture("additional-context-list")
        compose.runOnIdle { dark = true }
        capture("additional-context-list")
        compose.onNodeWithText("Writing style").performClick()
        assertEquals(snippets.first().id, selected?.id)
    }

    @Test fun contextEditorKeepsCharacterAssignmentsWhenAllCharactersIsToggled() {
        var snippet by mutableStateOf(snippets.first())
        content { AdditionalContextEditor(snippet, listOf(assistant), onUpdate = { snippet = it(snippet) }) }
        capture("additional-context-editor")
        compose.runOnIdle { dark = true }
        capture("additional-context-editor")
        compose.onNodeWithText("All characters").performClick()
        assertTrue(snippet.allAssistants)
        compose.onNodeWithText("Mira").assertDoesNotExist()
        compose.onNodeWithText("All characters").performClick()
        assertFalse(snippet.allAssistants)
        assertTrue(assistant.id in snippet.assistantIds)
        compose.onNodeWithText("Mira").performClick()
        assertFalse(assistant.id in snippet.assistantIds)
    }
}
