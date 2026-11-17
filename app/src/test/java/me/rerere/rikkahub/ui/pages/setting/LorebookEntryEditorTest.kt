package me.rerere.rikkahub.ui.pages.setting

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SeafoamMintThemeId
import me.rerere.rikkahub.ui.theme.Shapes
import me.rerere.rikkahub.ui.theme.Typography
import me.rerere.rikkahub.ui.theme.findPresetTheme
import me.rerere.rikkahub.ui.theme.withLastChatAmoledSurface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class LorebookEntryEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val appScope = AppScope()
    private var dark by mutableStateOf(false)
    private val original = LorebookEntry(
        name = "Harbor", prompt = "A quiet harbor with a lighthouse.", enabled = false,
        activationType = LorebookActivationType.KEYWORDS, keywords = listOf("harbor", "lighthouse"),
        injectionPosition = InjectionPosition.AT_DEPTH, depth = 7,
        imageContent = "legacy image metadata",
    )
    private fun text(id: Int) = compose.activity.getString(id)

    @Before fun setUp() {
        val context = compose.activity.applicationContext
        val store = SettingsStore(context, appScope, QuickSettingsCache(context),
            SecretKeyManager(SecureStore(context)), StandardTestDispatcher())
        startKoin { modules(module { single { store } }) }
    }

    @After fun tearDown() { appScope.cancel(); stopKoin() }

    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, 1.3f),
                LocalSettings provides Settings(), LocalDarkMode provides dark,
                LocalToaster provides rememberAppToasterState(),
            ) {
                MaterialTheme(
                    colorScheme = findPresetTheme(SeafoamMintThemeId).getColorScheme(dark)
                        .withLastChatAmoledSurface(dark),
                    typography = Typography, shapes = Shapes,
                ) { Surface(Modifier.fillMaxSize()) { body() } }
            }
        }
    }

    private fun settleDraft() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { directory ->
            compose.waitForIdle()
            compose.runOnIdle {
                val view = requireNotNull(ShadowDialog.getLatestDialog()?.window?.decorView)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val output = File(directory, "$name-${if (dark) "dark" else "light"}.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun cancelDiscardsTheDraftWithoutCallingSave() {
        var shown by mutableStateOf(true)
        var saved: LorebookEntry? = null
        var dismissed = 0
        content {
            if (shown) LorebookEntryEditorSheet(original,
                onDismiss = { dismissed++; shown = false }, onSave = { saved = it })
        }
        compose.onNodeWithContentDescription(text(R.string.lorebook_entry_prompt))
            .performScrollTo().performTextReplacement("Discard this change")
        settleDraft()
        compose.onNodeWithText(text(R.string.cancel)).performScrollTo().performClick()
        assertNull(saved)
        assertEquals(1, dismissed)
        assertEquals("A quiet harbor with a lighthouse.", original.prompt)
    }

    @Test fun saveUsesTheCurrentDraftAndPreservesImportedMetadata() {
        var saved: LorebookEntry? = null
        content { LorebookEntryEditorSheet(original, onDismiss = {}, onSave = { saved = it }) }
        capture("lorebook-entry")
        compose.runOnIdle { dark = true }
        capture("lorebook-entry")
        compose.onNodeWithContentDescription(text(R.string.lorebook_entry_prompt))
            .performScrollTo().performTextReplacement("The lighthouse is closed for repairs.")
        settleDraft()
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        val result = requireNotNull(saved)
        assertEquals(original.id, result.id)
        assertEquals("The lighthouse is closed for repairs.", result.prompt)
        assertFalse(result.enabled)
        assertEquals(7, result.depth)
        assertEquals(original.imageContent, result.imageContent)
        assertEquals(original.keywords, result.keywords)
    }

    @Test fun retryKeepsTheSameNewEntryId() {
        val attempts = mutableListOf<LorebookEntry>()
        content { LorebookEntryEditorSheet(null, onDismiss = {}, onSave = { attempts += it }) }
        compose.onNodeWithContentDescription(text(R.string.lorebook_entry_prompt))
            .performScrollTo().performTextReplacement("A new location")
        settleDraft()
        compose.onNodeWithText(text(R.string.create)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.create)).performClick()
        assertEquals(2, attempts.size)
        assertEquals(attempts.first().id, attempts.last().id)
    }

    @Test fun savingDisablesEditingCancellationAndDuplicateSave() {
        var saves = 0
        var dismissals = 0
        content {
            LorebookEntryEditorSheet(original, onDismiss = { dismissals++ }, onSave = { saves++ }, saving = true)
        }
        compose.onNodeWithContentDescription(text(R.string.lorebook_entry_name)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.lorebook_entry_prompt)).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Expand ${text(R.string.lorebook_entry_prompt)}").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.cancel)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.save)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.modes_page_add_file)).performScrollTo().assertIsNotEnabled()
        assertEquals(0, saves)
        assertEquals(0, dismissals)
    }
}
