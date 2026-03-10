package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.utils.PermissionChecker

@Composable
fun AssistantLocalToolSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    var pendingNotificationAccess by remember {
        mutableStateOf(PermissionChecker.MissingFeatureAccess())
    }
    var showNotificationAccessDialog by remember { mutableStateOf(false) }

    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val remainingAccess = PermissionChecker.getMissingNotificationAccess(context)
        pendingNotificationAccess = remainingAccess
        showNotificationAccessDialog = remainingAccess.specialAccesses.isNotEmpty()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val remainingAccess = PermissionChecker.getMissingNotificationAccess(context)
        pendingNotificationAccess = remainingAccess
        showNotificationAccessDialog = remainingAccess.specialAccesses.isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // JavaScript å¼•æ“Žå·¥å…·å¡ç‰‡
        LocalToolCard(
            title = stringResource(R.string.assistant_page_local_tools_javascript_engine_title),
            description = stringResource(R.string.assistant_page_local_tools_javascript_engine_desc),
            isEnabled = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
            onToggle = { enabled ->
                val newLocalTools = if (enabled) {
                    assistant.localTools + LocalToolOption.JavascriptEngine
                } else {
                    assistant.localTools - LocalToolOption.JavascriptEngine
                }
                onUpdate(assistant.copy(localTools = newLocalTools))
            }
        )

        // Notifications
        LocalToolCard(
            title = "Notifications",
            description = "Notifications, notification reading, scheduled follow-ups",
            isEnabled = assistant.localTools.contains(LocalToolOption.Notifications),
            onToggle = { enabled ->
                if (enabled) {
                    val newLocalTools = assistant.localTools + LocalToolOption.Notifications
                    onUpdate(assistant.copy(localTools = newLocalTools))
                    val missingAccess = PermissionChecker.getMissingNotificationAccess(context)
                    pendingNotificationAccess = missingAccess
                    when {
                        missingAccess.runtimePermissions.isNotEmpty() -> {
                            notificationPermissionLauncher.launch(missingAccess.runtimePermissions.toTypedArray())
                        }
                        missingAccess.specialAccesses.isNotEmpty() -> {
                            showNotificationAccessDialog = true
                        }
                    }
                } else {
                    val newLocalTools = assistant.localTools - LocalToolOption.Notifications
                    onUpdate(assistant.copy(localTools = newLocalTools))
                }
            }
        )

        // Python Engine
        val pythonOption = assistant.localTools.filterIsInstance<LocalToolOption.PythonEngine>().firstOrNull()
        LocalToolCard(
            title = stringResource(R.string.assistant_page_local_tools_python_engine_title),
            description = stringResource(R.string.assistant_page_local_tools_python_engine_desc),
            isEnabled = pythonOption != null,
            onToggle = { enabled ->
                val newLocalTools = if (enabled) {
                    assistant.localTools + LocalToolOption.PythonEngine
                } else {
                    assistant.localTools.filterNot { it is LocalToolOption.PythonEngine }
                }
                onUpdate(assistant.copy(localTools = newLocalTools))
            }
        )

        LocalToolCard(
            title = stringResource(R.string.assistant_page_local_tools_ask_user_title),
            description = stringResource(R.string.assistant_page_local_tools_ask_user_desc),
            isEnabled = assistant.localTools.contains(LocalToolOption.AskUser),
            onToggle = { enabled ->
                val newLocalTools = if (enabled) {
                    assistant.localTools + LocalToolOption.AskUser
                } else {
                    assistant.localTools - LocalToolOption.AskUser
                }
                onUpdate(assistant.copy(localTools = newLocalTools))
            }
        )
    }

    if (showNotificationAccessDialog && pendingNotificationAccess.specialAccesses.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNotificationAccessDialog = false },
            title = { Text("Notification Access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enable the remaining access below so notification tools and scheduled follow-ups work reliably:")
                    PermissionChecker.getFeatureAccessDescriptions(pendingNotificationAccess).forEach { description ->
                        Text("- $description")
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showNotificationAccessDialog = false
                        val nextAccess = pendingNotificationAccess.specialAccesses.firstOrNull() ?: return@TextButton
                        notificationSettingsLauncher.launch(
                            PermissionChecker.createSpecialAccessIntent(nextAccess)
                        )
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showNotificationAccessDialog = false }
                ) {
                    Text("Not now")
                }
            }
        )
    }
}

@Composable
private fun LocalToolCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor()
        )
    ) {
        FormItem(
            modifier = Modifier.padding(8.dp),
            label = {
                Text(title)
            },
            description = {
                Text(description)
            },
            tail = {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            },
            content = {
                if (isEnabled && content != null) {
                    content()
                } else {
                    null
                }
            }
        )
    }
}



