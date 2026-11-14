package me.rerere.rikkahub.ui.activity

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssistantModelPickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualModelSelectorYieldsTheServiceOverlayToItsActivityOwnedSheet() {
        val context = compose.activity.applicationContext
        val scope = AppScope()
        val store = SettingsStore(context, scope, QuickSettingsCache(context), SecretKeyManager(SecureStore(context)),
            StandardTestDispatcher())
        val host = AssistantDialogHost()
        val parent = requireNotNull(ComponentDialog(compose.activity).window)
        parent.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        host.bindWindow(parent)
        var modal = false
        var shown by mutableStateOf(true)
        host.onModalChanged = { modal = it }
        startKoin { modules(module {
            single { store }
            single { ModelMetadataResolver { null } }
        }) }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAssistantDialogHost provides host, LocalSettings provides Settings()) {
                    MaterialTheme {
                        if (shown) ModelSelector(modelId = null, providers = emptyList(), type = ModelType.CHAT,
                            onlyIcon = true, modifier = Modifier.testTag("modelPicker"), onSelect = {})
                    }
                }
            }
            compose.onNodeWithTag("modelPicker").performClick()
            compose.waitForIdle()
            val dialog = requireNotNull(ShadowDialog.getLatestDialog())
            val window = requireNotNull(dialog.window)
            assertTrue("The model sheet must yield the service card and decoration", modal)
            assertTrue(dialog.isShowing)
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, window.attributes.type)
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            assertFalse(modal)
            assertFalse(dialog.isShowing)
        } finally {
            compose.runOnIdle { shown = false }
            host.close()
            scope.cancel()
            stopKoin()
        }
    }
}
