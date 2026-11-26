package me.rerere.rikkahub.ui.pages.assistant.detail

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssistantPromptSubPageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val appScope = AppScope()

    @Before
    fun setUp() {
        val context = compose.activity.applicationContext
        val store = SettingsStore(
            context,
            appScope,
            QuickSettingsCache(context),
            SecretKeyManager(SecureStore(context)),
            StandardTestDispatcher(),
        )
        startKoin { modules(module { single { store } }) }
    }

    @After
    fun tearDown() {
        appScope.cancel()
        stopKoin()
    }

    @Test
    fun inlineSystemPromptPersistsAfterDebounceWithoutLeavingPage() {
        var assistant by mutableStateOf(Assistant(name = "Furina", systemPrompt = ""))
        val promptTitle = compose.activity.getString(R.string.assistant_page_system_prompt)

        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        AssistantPromptSubPageContent(
                            assistant = assistant,
                            onUpdate = { assistant = it },
                        )
                    }
                }
            }
        }

        compose.onNodeWithContentDescription(promptTitle)
            .performTextReplacement("You are Furina from Fontaine.")
        compose.waitForIdle()
        compose.onNodeWithContentDescription(promptTitle)
            .assertTextEquals("You are Furina from Fontaine.")
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        assertEquals("You are Furina from Fontaine.", assistant.systemPrompt)
    }

    @Test
    fun expandedSystemPromptSurvivesPauseAndStateRestorationWithoutRevertingToEmpty() {
        var assistant by mutableStateOf(Assistant(name = "Furina", systemPrompt = ""))
        var shown by mutableStateOf(true)
        val promptTitle = compose.activity.getString(R.string.assistant_page_system_prompt)
        val restoration = StateRestorationTester(compose)

        restoration.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        if (shown) {
                            AssistantPromptSubPageContent(
                                assistant = assistant,
                                onUpdate = { assistant = it },
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Expand $promptTitle").performClick()
        compose.onNodeWithContentDescription("$promptTitle editor")
            .performTextReplacement("You are Furina, Hydro Archon of Fontaine.")

        compose.runOnIdle {
            (compose.activity.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            (compose.activity.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()

        // Leaving the page after restoration must preserve the saved prompt instead of wiping it back to "".
        compose.runOnIdle { shown = false }
        compose.waitForIdle()

        assertEquals("You are Furina, Hydro Archon of Fontaine.", assistant.systemPrompt)
    }

    @Test
    fun persistedExternalPromptUpdateSyncsIntoUneditedInlineFieldAndIsNotOverwrittenOnLeave() {
        val id = Assistant(name = "Furina", systemPrompt = "").id
        var assistant by mutableStateOf(Assistant(id = id, name = "Furina", systemPrompt = ""))
        var shown by mutableStateOf(true)
        val promptTitle = compose.activity.getString(R.string.assistant_page_system_prompt)

        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        if (shown) {
                            AssistantPromptSubPageContent(
                                assistant = assistant,
                                onUpdate = { assistant = it },
                            )
                        }
                    }
                }
            }
        }

        compose.runOnIdle {
            assistant = assistant.copy(systemPrompt = "Loaded custom prompt")
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(promptTitle).assertTextEquals("Loaded custom prompt")

        compose.runOnIdle { shown = false }
        compose.waitForIdle()

        assertEquals("Loaded custom prompt", assistant.systemPrompt)
    }
}
