package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * Advanced tab - Notifications and custom request settings.
 * Designed with cohesive SettingsGroup pattern.
 */
@Composable
fun AssistantAdvancedSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onUpdate(assistant.copy(enableSpontaneous = true))
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // NOTIFICATIONS GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Spontaneous Messaging") {
            // Enable toggle
            SettingGroupItem(
                title = "Enable Spontaneous Messages",
                subtitle = "Character can message you without prompting",
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableSpontaneous,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onUpdate(assistant.copy(enableSpontaneous = true))
                                }
                            } else {
                                onUpdate(assistant.copy(enableSpontaneous = false))
                            }
                        }
                    )
                }
            )
            
            // Settings (only when enabled)
            AnimatedVisibility(
                visible = assistant.enableSpontaneous,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Active Hours
                    SettingGroupItem(
                        title = "Active Hours",
                        subtitle = "Time window for messages",
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = assistant.notificationStartHour.toString(),
                                    onValueChange = { 
                                        val hour = it.toIntOrNull()?.coerceIn(0, 23) ?: 7
                                        onUpdate(assistant.copy(notificationStartHour = hour))
                                    },
                                    label = { Text("Start") },
                                    modifier = Modifier.width(70.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                OutlinedTextField(
                                    value = assistant.notificationEndHour.toString(),
                                    onValueChange = { 
                                        val hour = it.toIntOrNull()?.coerceIn(0, 23) ?: 22
                                        onUpdate(assistant.copy(notificationEndHour = hour))
                                    },
                                    label = { Text("End") },
                                    modifier = Modifier.width(70.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    )

                    // Frequency
                    SettingGroupItem(
                        title = "Frequency",
                        subtitle = "Minimum hours between messages",
                        trailing = {
                            OutlinedTextField(
                                value = assistant.notificationFrequencyHours.toString(),
                                onValueChange = { 
                                    val hours = it.toIntOrNull()?.coerceAtLeast(1) ?: 4
                                    onUpdate(assistant.copy(notificationFrequencyHours = hours))
                                },
                                modifier = Modifier.width(70.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                suffix = { Text("h") }
                            )
                        }
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // CUSTOM REQUEST GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Custom Request") {
            // Custom Headers - component has its own title
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CustomHeaders(
                        headers = assistant.customHeaders,
                        onUpdate = { onUpdate(assistant.copy(customHeaders = it)) }
                    )
                }
            }
            
            // Custom Bodies - component has its own title
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CustomBodies(
                        customBodies = assistant.customBodies,
                        onUpdate = { onUpdate(assistant.copy(customBodies = it)) }
                    )
                }
            }
        }
    }
}
