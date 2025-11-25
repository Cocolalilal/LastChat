package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.FormItem

@Composable
fun AssistantNotificationSubPage(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Spontaneous Messaging
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Spontaneous Messaging",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()

                FormItem(
                    label = { Text("Enable Spontaneous Messages") },
                    description = { Text("Allow assistant to send messages without user prompt.") },
                    tail = {
                        val permissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (isGranted) {
                                onUpdateAssistant(assistant.copy(enableSpontaneous = true))
                            } else {
                                // Still enable it, but maybe show a warning? Or just enable it.
                                // The user asked to "request" it, not strictly block it.
                                onUpdateAssistant(assistant.copy(enableSpontaneous = true))
                            }
                        }

                        Switch(
                            checked = assistant.enableSpontaneous,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onUpdateAssistant(assistant.copy(enableSpontaneous = true))
                                    }
                                } else {
                                    onUpdateAssistant(assistant.copy(enableSpontaneous = false))
                                }
                            }
                        )
                    }
                )

                if (assistant.enableSpontaneous) {
                    FormItem(
                        label = { Text("Active Hours") },
                        description = { Text("Time window when messages can be sent.") }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = assistant.notificationStartHour.toString(),
                                onValueChange = { 
                                    val hour = it.toIntOrNull()?.coerceIn(0, 23) ?: 7
                                    onUpdateAssistant(assistant.copy(notificationStartHour = hour))
                                },
                                label = { Text("Start Hour") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = assistant.notificationEndHour.toString(),
                                onValueChange = { 
                                    val hour = it.toIntOrNull()?.coerceIn(0, 23) ?: 22
                                    onUpdateAssistant(assistant.copy(notificationEndHour = hour))
                                },
                                label = { Text("End Hour") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    FormItem(
                        label = { Text("Frequency (Hours)") },
                        description = { Text("Minimum time between messages.") }
                    ) {
                        OutlinedTextField(
                            value = assistant.notificationFrequencyHours.toString(),
                            onValueChange = { 
                                val hours = it.toIntOrNull()?.coerceAtLeast(1) ?: 4
                                onUpdateAssistant(assistant.copy(notificationFrequencyHours = hours))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    FormItem(
                        label = { Text("Custom Prompt") },
                        description = { Text("Instructions for spontaneous messages. Use {{history}} and {{memories}}.") }
                    ) {
                        OutlinedTextField(
                            value = assistant.spontaneousPrompt,
                            onValueChange = { 
                                onUpdateAssistant(assistant.copy(spontaneousPrompt = it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }
            }
        }


        // Personalized Greetings
        if (assistant.backgroundModelId != null) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Personalized Greetings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()

                    FormItem(
                        label = { Text("Enable Personalized Greetings") },
                        description = { Text("Generate unique greetings based on assistant's persona.") },
                        tail = {
                            Switch(
                                checked = assistant.enablePersonalizedGreetings,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(enablePersonalizedGreetings = it))
                                }
                            )
                        }
                    )

                    if (assistant.enablePersonalizedGreetings) {

                    }

                    if (assistant.enablePersonalizedGreetings) {
                        val vm = org.koin.androidx.compose.koinViewModel<AssistantDetailVM>()
                        val isGeneratingGreetings by vm.isGeneratingGreetings.collectAsStateWithLifecycle()

                        Button(
                            onClick = {
                                vm.generateGreetings(assistant.id)
                            },
                            enabled = !isGeneratingGreetings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isGeneratingGreetings) {
                                Text("Generating...")
                            } else {
                                Text("Regenerate Greetings")
                            }
                        }
                        
                        if (assistant.personalizedGreetings.isNotEmpty()) {
                            Text(
                                text = "Greetings generated: ${assistant.personalizedGreetings.values.sumOf { it.size }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }

}
