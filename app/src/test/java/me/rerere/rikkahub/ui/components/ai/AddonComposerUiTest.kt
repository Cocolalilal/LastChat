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
import androidx.navigation.compose.rememberNavController
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.components.ui.AppToasterState
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.pages.setting.LorebookEditorSheet
import me.rerere.rikkahub.ui.pages.setting.AdditionalContextEditorPage
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.ui.pages.setting.SkillEditorPage
import org.robolectric.shadows.ShadowDialog
import org.junit.Assert.assertNull
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.withLorebookDefault
import me.rerere.rikkahub.data.datastore.withLorebookAssignment
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
import org.junit.Before
import org.junit.After
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
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
    private val appScope = AppScope()

    @Before fun setUp() {
        val context = compose.activity.applicationContext
        val store = SettingsStore(context, appScope, QuickSettingsCache(context), SecretKeyManager(SecureStore(context)),
            StandardTestDispatcher())
        startKoin { modules(module { single { store } }) }
    }

    @After fun tearDown() {
        appScope.cancel()
        stopKoin()
    }

    private var dark by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)
    private val assistant = Assistant(name = "Mira")
    private val snippets = listOf(
        AdditionalContext(name = "Writing style", prompt = "Use short paragraphs and natural dialogue.", assistantIds = setOf(assistant.id)),
        AdditionalContext(name = "Story setting", prompt = "The story takes place in a coastal town."),
    )

    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalSettings provides Settings(), LocalDarkMode provides dark,
                LocalNavController provides rememberNavController(),
                LocalToaster provides rememberAppToasterState(),
            ) {
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
                val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
                    ?: compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File(directory, "$name-${if (dark) "dark" else "light"}.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun skillOptionsScrollWithoutBoxesOrNavigationControlsAndDiscardStaleActions() {
        val skills = listOf("write", "rewrite", "summarize", "translate").map {
            Skill(name = it, description = "Help with $it", instructions = "Instructions")
        }
        var state by mutableStateOf<SkillCommandCardState?>(SkillCommandCardState("", skills))
        var selected: String? = null
        content {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                SkillCommandCard(state, onSelect = { selected = it.name })
                OutlinedTextField(value = "/", onValueChange = {}, modifier = Modifier.fillMaxWidth())
            }
        }
        compose.onNodeWithText("/write").assertExists()
        capture("skill-commands")
        compose.runOnIdle { dark = true }
        capture("skill-commands")
        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dismiss").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("/translate"))
        compose.onNodeWithText("/translate").performClick()
        assertEquals("translate", selected)
        val oldAction = compose.onNodeWithText("/translate").fetchSemanticsNode().config[SemanticsActions.OnClick].action
        compose.runOnIdle { state = null }
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

    @Test fun contextDeletionUsesTheListSwipeActionAndUndoRestoresThePosition() {
        var settings by mutableStateOf(Settings(additionalContexts = snippets))
        lateinit var toaster: AppToasterState
        var opened = false
        content {
            toaster = LocalToaster.current
            AdditionalContextContent(settings, onEdit = { opened = true }, onUpdateSettings = { settings = it(settings) })
        }
        compose.onNodeWithText("Writing style").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Delete").onFirst().performClick()
        compose.waitForIdle()
        assertEquals(listOf(snippets[1].id), settings.additionalContexts.map { it.id })
        assertFalse(opened)
        compose.runOnIdle { requireNotNull(toaster.toasts.last().action).onClick() }
        compose.waitForIdle()
        assertEquals(snippets.map { it.id }, settings.additionalContexts.map { it.id })
    }

    @Test fun contextEditorUsesTheEntryNameAndHasNoToolbarDeleteAction() {
        content { AdditionalContextEditorPage(snippets.first(), listOf(assistant), onUpdate = {}) }
        compose.onNode(hasText("Writing style") and !hasSetTextAction()).assertExists()
        compose.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test fun contextDefaultsResetExceptionsAndKeepCharactersIndividuallyEditable() {
        var snippet by mutableStateOf(snippets.first())
        content { AdditionalContextEditorPage(snippet, listOf(assistant), onUpdate = { snippet = it(snippet) }) }
        compose.onNodeWithText("Enabled").assertDoesNotExist()
        capture("additional-context-editor")
        compose.runOnIdle { dark = true }
        capture("additional-context-editor")
        compose.onNodeWithText("Characters").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Enabled by default").assertIsOff().performClick()
        assertTrue(snippet.allAssistants)
        capture("context-character-defaults")
        assertTrue(snippet.assistantIds.isEmpty())
        compose.onNodeWithContentDescription("Mira").assertIsOn().performClick()
        assertTrue(assistant.id in snippet.excludedAssistantIds)
        compose.onNodeWithContentDescription("Mira").assertIsOff()
        compose.onNodeWithContentDescription("Enabled by default").performClick()
        assertFalse(snippet.allAssistants)
        assertTrue(snippet.excludedAssistantIds.isEmpty())
        compose.onNodeWithContentDescription("Mira").assertIsOff().performClick()
        assertTrue(assistant.id in snippet.assistantIds)
        compose.onNodeWithContentDescription("Enabled by default").performClick()
        assertTrue(snippet.assistantIds.isEmpty())
        compose.onNodeWithContentDescription("Mira").assertIsOn()
    }
    @Test fun skillCreationUsesThePageWithoutOpeningAModal() {
        content { SkillEditorPage(skill = null, assistants = listOf(assistant), onSave = {}) }
        assertNull(ShadowDialog.getLatestDialog())
        capture("skill-editor")
        compose.runOnIdle { dark = true }
        capture("skill-editor")
    }

    @Test fun existingSkillAssignmentsAutosaveAndActivationTogglesAreAbsent() {
        val original = Skill(name = "write", instructions = "Write clearly.", enabled = false)
        var saved = original
        content { SkillEditorPage(original, listOf(assistant), onSave = {}, onAutoSave = { saved = it }) }
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Assistant can use").assertDoesNotExist()
        compose.onNodeWithText("Slash command").assertDoesNotExist()
        compose.onNodeWithText("Automatic activation").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Enabled by default").assertIsOn()
        compose.onNodeWithContentDescription("Mira").assertIsOn().performClick()
        compose.waitForIdle()
        assertTrue(assistant.id in saved.availableExcludedAssistantIds)
        capture("skill-character-exceptions")
        compose.onNodeWithContentDescription("Enabled by default").performClick()
        assertFalse(saved.availableForAllAssistants)
        assertTrue(saved.availableExcludedAssistantIds.isEmpty())
        compose.onNodeWithContentDescription("Mira").assertIsOff().performClick()
        compose.waitForIdle()
        assertTrue(assistant.id in saved.availableAssistantIds)
    }

    @Test fun longSkillPromptUsesAnExpandableFieldAtLargerFontScale() {
        fontScale = 1.3f
        val skill = Skill(name = "character-design", description = "Create and refine character prompts.",
            instructions = "Name: {{char}}\nPersonality: observant and kind.\n\n".repeat(100))
        content { SkillEditorPage(skill, listOf(assistant), onSave = {}, onAutoSave = {}) }
        compose.onNodeWithText("Instructions").performScrollTo()
        capture("skill-editor-long")
        compose.runOnIdle { dark = true }
        capture("skill-editor-long")
        compose.onNodeWithContentDescription("Expand Instructions").performClick()
        compose.onAllNodes(hasText("Instructions")).assertCountEquals(1)
        compose.onNodeWithText("Save").assertExists()
        capture("skill-prompt-expanded")
    }

    @Test fun lorebookCharactersRemainEditableWithEitherDefault() {
        val book = Lorebook(name = "Coastal town")
        var settings by mutableStateOf(Settings(assistants = listOf(assistant), lorebooks = listOf(book)))
        content {
            me.rerere.rikkahub.ui.pages.setting.AssistantLorebookToggleSheet(
                settings.lorebooks.single(), settings.assistants,
                onDefaultChange = { settings = settings.withLorebookDefault(book.id, it) },
                onAssignmentChange = { id, checked -> settings = settings.withLorebookAssignment(book.id, id, checked) },
                onDismiss = {},
            )
        }
        compose.onNodeWithContentDescription("Enabled by default").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Mira").assertIsOn().performClick()
        assertEquals(setOf(assistant.id), settings.lorebooks.single().excludedAssistantIds)
        capture("lorebook-character-exceptions")
        compose.onNodeWithContentDescription("Enabled by default").performClick()
        compose.onNodeWithContentDescription("Mira").assertIsOff().performClick()
        assertEquals(setOf(book.id), settings.assistants.single().enabledLorebookIds)
        compose.onNodeWithContentDescription("Enabled by default").performClick()
        assertTrue(settings.assistants.single().enabledLorebookIds.isEmpty())
        assertTrue(settings.lorebooks.single().excludedAssistantIds.isEmpty())
        compose.onNodeWithContentDescription("Mira").assertIsOn()
    }

    @Test fun lorebookMetadataKeepsTheOriginalSheetAndCancelBehavior() {
        fontScale = 1.3f
        var book = Lorebook(name = "Coastal town", description = "Locations, characters, and local history.")
        var shown by mutableStateOf(true)
        content {
            if (shown) LorebookEditorSheet(book, onDismiss = { shown = false },
                onSave = { book = it; shown = false })
        }
        capture("lorebook-metadata")
        compose.runOnIdle { dark = true }
        capture("lorebook-metadata")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("Coastal town", book.name)
    }

    @Test fun attachmentSheetContextSelectionInheritsThenOverridesOnlyThisChat() {
        val settings = Settings(additionalContexts = snippets)
        val otherChat = me.rerere.rikkahub.data.model.Conversation.ofId(kotlin.uuid.Uuid.random(), assistant.id)
        var chat by mutableStateOf(otherChat.copy(id = kotlin.uuid.Uuid.random()))
        var navigate: String? = null
        content { AdditionalContextPickerItem(settings, assistant, chat, { chat = it }, { navigate = it }) }
        compose.onNodeWithText("1 active").assertExists()
        compose.onNodeWithText("Additional context").performClick()
        compose.onNodeWithContentDescription("Writing style").assertIsOn().performClick()
        compose.onNodeWithContentDescription("Story setting").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Story setting").assertIsOn()
        assertEquals(setOf(snippets[1].id), chat.enabledAdditionalContextIds)
        assertNull(otherChat.enabledAdditionalContextIds)
        assertEquals(setOf(assistant.id), snippets[0].assistantIds)
        capture("additional-context-picker")
        compose.runOnIdle { dark = true }
        capture("additional-context-picker")
        compose.onNodeWithText("Story setting").performClick()
        assertEquals(snippets[1].id.toString(), navigate)
        compose.onNodeWithText("Additional context").performClick()
        compose.onNodeWithContentDescription("Story setting").assertIsOn().performClick()
        assertEquals(emptySet<kotlin.uuid.Uuid>(), chat.enabledAdditionalContextIds)
        compose.onNodeWithContentDescription("Writing style").assertIsOff()
    }

    @Test fun additionalContextDragReordersTheActualListWithoutChangingPrompts() {
        var settings by mutableStateOf(Settings(additionalContexts = snippets))
        content { AdditionalContextContent(settings, onEdit = {}, onUpdateSettings = { settings = it(settings) }) }
        compose.mainClock.autoAdvance = false
        val handles = compose.onAllNodesWithContentDescription("Drag to reorder")
        val distance = compose.onNodeWithText("Story setting").fetchSemanticsNode().boundsInRoot.center.y -
            compose.onNodeWithText("Writing style").fetchSemanticsNode().boundsInRoot.center.y
        val handleId = handles.onFirst().fetchSemanticsNode().id
        val handle = compose.onNode(SemanticsMatcher("dragged context handle") { it.id == handleId })
        handle.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(650)
        compose.waitForIdle()
        handle.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, distance + 16f)) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        handle.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(snippets.reversed(), settings.additionalContexts)
    }

    @Test fun pickerIgnoresLegacyEnabledFlagAndKeepsEmptyPromptSelections() {
        var settings by mutableStateOf(Settings(additionalContexts = snippets.mapIndexed { index, snippet ->
            if (index == 0) snippet.copy(enabled = false, prompt = "") else snippet
        }))
        var chat by mutableStateOf(me.rerere.rikkahub.data.model.Conversation.ofId(kotlin.uuid.Uuid.random(), assistant.id)
            .copy(enabledAdditionalContextIds = snippets.map { it.id }.toSet()))
        content { AdditionalContextPickerItem(settings, assistant, chat, { chat = it }, {}) }
        compose.onNodeWithText("Additional context").performClick()
        compose.onNodeWithContentDescription("Writing style").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Story setting").performClick()
        assertEquals(setOf(snippets[0].id), chat.enabledAdditionalContextIds)
        compose.runOnIdle { settings = settings.copy(additionalContexts = snippets) }
        compose.onNodeWithContentDescription("Writing style").assertIsOn().assertIsEnabled()
        assertEquals(setOf(snippets[0].id), chat.enabledAdditionalContextIds)
    }

}
