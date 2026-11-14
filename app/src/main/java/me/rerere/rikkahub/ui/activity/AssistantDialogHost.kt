package me.rerere.rikkahub.ui.activity

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Activity-owned dialogs keep Android's normal picker, input and back handling. */
internal class AssistantDialogHost {
    private var window: Window? = null
    private val children = mutableSetOf<Window>()
    private var visible = true
    private val modalOverOverlay: Boolean get() = window != null && visible && children.isNotEmpty()

    var onModalChanged: (Boolean) -> Unit = {}
        set(value) {
            field = value
            value(modalOverOverlay)
        }

    fun bindWindow(window: Window?) {
        this.window = window?.takeIf {
            it.attributes.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        onModalChanged(modalOverOverlay)
    }

    internal fun register(child: Window) {
        // Never mutate type/token after Dialog.show(). The real Activity WindowManager
        // owns this modal; the service card yields its focus and touch region instead.
        children += child
        child.decorView.visibility = if (visible) View.VISIBLE else View.GONE
        onModalChanged(modalOverOverlay)
    }

    internal fun unregister(child: Window) {
        children -= child
        onModalChanged(modalOverOverlay)
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
        children.forEach { it.decorView.visibility = if (visible) View.VISIBLE else View.GONE }
        onModalChanged(modalOverOverlay)
    }

    fun close() {
        // Compose owns dismissal. Removing its native view here would make the later
        // Dialog.dispose/dismiss path remove an already detached window.
        setVisible(false)
        children.clear()
        window = null
        onModalChanged(false)
    }
}

internal val LocalAssistantDialogHost = staticCompositionLocalOf<AssistantDialogHost?> { null }

/** Tracks a real Dialog/ModalBottomSheet without changing its native window identity. */
@Composable
internal fun PrepareAssistantDialog() {
    val host = LocalAssistantDialogHost.current ?: return
    val view = LocalView.current
    val window = assistantDialogWindow(view) ?: return
    DisposableEffect(host, window) {
        host.register(window)
        onDispose { host.unregister(window) }
    }
}

internal fun assistantDialogWindow(view: View): Window? {
    var current: Any? = view
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = (current as? View)?.parent
    }
    return null
}
