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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.FormItem

@Composable
fun AssistantLocalToolSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // JavaScript 引擎工具卡片
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

        val deviceControlPermissionLauncher = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) {
            // We don't strictly block if denied, just proceed
            val newLocalTools = assistant.localTools + LocalToolOption.DeviceControl
            onUpdate(assistant.copy(localTools = newLocalTools))
        }

        // Device Control
        LocalToolCard(
            title = "Device Control",
            description = "Allow assistant to control device settings (Brightness, Volume, Torch, etc.) and send notifications.",
            isEnabled = assistant.localTools.contains(LocalToolOption.DeviceControl),
            onToggle = { enabled ->
                if (enabled) {
                    val permissions = mutableListOf<String>()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissions.add(android.Manifest.permission.CAMERA)
                    
                    if (permissions.isNotEmpty()) {
                        deviceControlPermissionLauncher.launch(permissions.toTypedArray())
                    } else {
                        val newLocalTools = assistant.localTools + LocalToolOption.DeviceControl
                        onUpdate(assistant.copy(localTools = newLocalTools))
                    }
                } else {
                    val newLocalTools = assistant.localTools - LocalToolOption.DeviceControl
                    onUpdate(assistant.copy(localTools = newLocalTools))
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
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

