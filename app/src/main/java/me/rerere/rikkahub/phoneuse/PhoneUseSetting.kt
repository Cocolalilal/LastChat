package me.rerere.rikkahub.phoneuse

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import org.koin.compose.koinInject

@Composable
fun PhoneUseSetting(assistant: Assistant, onUpdate: (Assistant) -> Unit) {
    val manager = koinInject<PhoneUseManager>()
    val controller by manager.controller.collectAsStateWithLifecycle()
    val enabled = LocalToolOption.PhoneUse in assistant.localTools
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }
    fun openSettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra("android.provider.extra.EXTRA_COMPONENT_NAME", ComponentName(context, PhoneUseAccessibilityService::class.java).flattenToString())
        }
        context.startActivity(intent)
    }
    SettingGroupItem(
        title = "Phone use",
        subtitle = if (PhoneUseManager.isSupported) "Read the screen and operate apps" else "Requires Android 11 or later",
        trailing = {
            HapticSwitch(checked = enabled, enabled = PhoneUseManager.isSupported, onCheckedChange = { checked ->
                if (checked) showDisclosure = true
                else {
                    if (manager.session.value?.assistantId == assistant.id) manager.stop()
                    onUpdate(assistant.copy(localTools = assistant.localTools - LocalToolOption.PhoneUse))
                }
            })
        },
    )
    if (enabled && PhoneUseManager.isSupported) SettingGroupItem(
        title = if (controller != null) "Accessibility connected" else "Accessibility access needed",
        subtitle = if (controller == null) "Enable LastChat Phone use in Android settings" else "Pause or stop from the on-screen controls",
        trailing = { TextButton(onClick = ::openSettings) { Text("Settings") } },
    )
    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false },
        title = { Text("Enable phone use?") },
        text = { Column {
            Text("This character can read your screen, tap, scroll, type, and open apps. Screen contents and requested screenshots are sent to your selected model.")
            Text("Use the on-screen controls to pause or stop. Android accessibility access is required.")
        } },
        confirmButton = { TextButton(onClick = {
            showDisclosure = false
            onUpdate(assistant.copy(localTools = (assistant.localTools + LocalToolOption.PhoneUse).distinct()))
            if (controller == null) openSettings()
        }) { Text("Enable") } },
        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Cancel") } },
    )
}
