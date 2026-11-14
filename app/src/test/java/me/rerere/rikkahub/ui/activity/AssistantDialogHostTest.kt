package me.rerere.rikkahub.ui.activity

import android.app.Application
import android.content.Context
import android.os.Binder
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssistantDialogHostTest {
    @Test fun `wrapped picker context keeps the real activity manager during construction and rebind`() {
        val first = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val replacement = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val context = AssistantOverlaySession.OverlayContext(first.get())
            assertSame(first.get().getSystemService(WindowManager::class.java),
                context.getSystemService(Context.WINDOW_SERVICE))
            // PhoneWindow requires the concrete framework manager, never a delegated proxy.
            assertNotNull(ComponentDialog(context).window)
            context.baseContext = replacement.get()
            assertSame(replacement.get().getSystemService(WindowManager::class.java),
                context.getSystemService(Context.WINDOW_SERVICE))
            assertNotNull(ComponentDialog(context).window)
        } finally {
            first.pause().stop().destroy()
            replacement.pause().stop().destroy()
        }
    }

    @Test fun `modal tracking preserves native identity and yields only while a service host is visible`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val parent = requireNotNull(ComponentDialog(activity).window)
        parent.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        val serviceToken = Binder()
        parent.attributes = parent.attributes.apply { token = serviceToken }
        val child = requireNotNull(ComponentDialog(activity).window)
        val originalType = child.attributes.type
        val originalToken = child.attributes.token
        val host = AssistantDialogHost()
        var modal = false
        host.onModalChanged = { modal = it }
        try {
            host.register(child)
            assertFalse(modal)
            host.bindWindow(parent)
            assertTrue(modal)
            assertEquals(originalType, child.attributes.type)
            assertSame(originalToken, child.attributes.token)
            host.setVisible(false)
            assertFalse(modal)
            assertEquals(View.GONE, child.decorView.visibility)
            host.setVisible(true)
            assertTrue(modal)
            assertEquals(View.VISIBLE, child.decorView.visibility)
            host.unregister(child)
            assertFalse(modal)
            host.setVisible(false)
            assertEquals(View.VISIBLE, child.decorView.visibility)
        } finally {
            host.close()
            controller.pause().stop().destroy()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssistantDialogHostComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `real attachment sheet keeps activity ownership and restores overlay after dismissal`() {
        val host = AssistantDialogHost()
        val parent = requireNotNull(ComponentDialog(compose.activity).window)
        parent.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        host.bindWindow(parent)
        var modal = false
        var shown by mutableStateOf(true)
        host.onModalChanged = { modal = it }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAssistantDialogHost provides host) {
                    if (shown) ModalBottomSheet(
                        onDismissRequest = { shown = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) {
                        PrepareAssistantDialog()
                        Text("Files")
                    }
                }
            }
        }
        compose.waitForIdle()
        val dialog = requireNotNull(ShadowDialog.getLatestDialog())
        val window = requireNotNull(dialog.window)
        assertTrue(modal)
        assertTrue(dialog.isShowing)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, window.attributes.type)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION,
            (window.decorView.layoutParams as WindowManager.LayoutParams).type)
        compose.runOnIdle { host.setVisible(false) }
        assertFalse(modal)
        assertEquals(View.GONE, window.decorView.visibility)
        compose.runOnIdle { host.setVisible(true) }
        assertTrue(modal)
        assertEquals(View.VISIBLE, window.decorView.visibility)
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        assertFalse(dialog.isShowing)
        assertFalse(modal)
        host.close()
    }

    @Test fun `enabling accessibility while a picker is open retains its activity window and token`() {
        val host = AssistantDialogHost()
        val parent = requireNotNull(ComponentDialog(compose.activity).window)
        parent.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        parent.attributes = parent.attributes.apply { token = Binder() }
        var modal = false
        host.onModalChanged = { modal = it }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAssistantDialogHost provides host) {
                    ModalBottomSheet(
                        onDismissRequest = {},
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) {
                        PrepareAssistantDialog()
                        Text("Files")
                    }
                }
            }
        }
        compose.waitForIdle()
        val original = requireNotNull(ShadowDialog.getLatestDialog())
        val window = requireNotNull(original.window)
        val originalToken = window.attributes.token
        assertTrue(original.isShowing)
        assertFalse(modal)
        compose.runOnIdle { host.bindWindow(parent) }
        compose.waitForIdle()
        assertTrue(modal)
        assertSame(original, ShadowDialog.getLatestDialog())
        assertTrue(original.isShowing)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, window.attributes.type)
        assertSame(originalToken, window.attributes.token)
        compose.runOnIdle { host.bindWindow(null) }
        assertFalse(modal)
        assertTrue(original.isShowing)
        assertSame(originalToken, window.attributes.token)
        host.close()
    }
}
