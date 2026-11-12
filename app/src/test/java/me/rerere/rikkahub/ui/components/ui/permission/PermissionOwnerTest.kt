package me.rerere.rikkahub.ui.components.ui.permission

import android.Manifest
import android.app.Application
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PermissionOwnerTest {
    class PermissionHost : ComponentActivity() {
        var rationale = false
        override fun shouldShowRequestPermissionRationale(permission: String): Boolean = rationale
    }

    @Test fun `wrapped assistant context resolves its activity and replacement registry owner`() {
        val first = Robolectric.buildActivity(PermissionHost::class.java).setup()
        val replacement = Robolectric.buildActivity(PermissionHost::class.java).setup()
        try {
            val context = MutableContextWrapper(ContextWrapper(first.get()))
            assertSame(first.get(), permissionActivity(context, null))
            // Registry ownership changes before a retained scene's wrapped context is rebound.
            assertSame(replacement.get(), permissionActivity(context, replacement.get()))
            context.baseContext = replacement.get()
            assertSame(replacement.get(), permissionActivity(context, null))
        } finally {
            first.pause().stop().destroy()
            replacement.pause().stop().destroy()
        }
    }

    @Test fun `retained permission state uses recreated activity without forgetting denial history`() {
        val first = Robolectric.buildActivity(PermissionHost::class.java).setup()
        val replacement = Robolectric.buildActivity(PermissionHost::class.java).setup()
        try {
            var owner = first.get()
            owner.rationale = true
            val permission = Manifest.permission.CAMERA
            val state = PermissionState(
                setOf(PermissionInfo(permission, displayName = {}, usage = {})),
                MutableContextWrapper(first.get()),
            ) { owner }
            state.handlePermissionResult(mapOf(permission to false))
            assertEquals(PermissionStatus.Denied, state.permissionStates[permission])
            owner = replacement.get()
            assertEquals(PermissionStatus.Denied, state.permissionStates[permission])
            state.handlePermissionResult(mapOf(permission to false))
            assertEquals(PermissionStatus.DeniedPermanently, state.permissionStates[permission])
        } finally {
            first.pause().stop().destroy()
            replacement.pause().stop().destroy()
        }
    }
}
