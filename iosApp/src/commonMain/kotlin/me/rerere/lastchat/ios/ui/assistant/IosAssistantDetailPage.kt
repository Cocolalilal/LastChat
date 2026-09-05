package me.rerere.lastchat.ios.ui.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.lastchat.ios.IosAppState
import me.rerere.lastchat.ios.IosAssistantPreferences
import me.rerere.rikkahub.ui.components.nav.LastChatBackButton
import me.rerere.rikkahub.ui.components.settings.LastChatFormItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsGroup
import androidx.compose.foundation.BorderStroke
import me.rerere.rikkahub.ui.theme.rememberDefaultGenericalPainter
import me.rerere.rikkahub.ui.theme.AppShapes

private enum class AssistantDetailSubRoute {
    Home,
    Profile,
    Prompt,
    Models,
}

@Composable
fun IosAssistantDetailPage(
    state: IosAppState,
    darkTheme: Boolean,
    onSaveAssistant: (String, String) -> Unit,
    onNewAssistant: () -> Unit,
    onSelectAssistant: (String) -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToModels: () -> Unit,
    onBack: () -> Unit,
    onHapticPop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var subRoute by remember { mutableStateOf(AssistantDetailSubRoute.Home) }
    var assistantName by remember(state.assistant.name) { mutableStateOf(state.assistant.name) }
    var systemPrompt by remember(state.assistant.systemPrompt) { mutableStateOf(state.assistant.systemPrompt) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = subRoute,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "assistant_detail_nav",
        modifier = modifier.fillMaxSize(),
    ) { currentSubRoute ->
        when (currentSubRoute) {
            AssistantDetailSubRoute.Home -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        // Assistant Switcher Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            state.assistants.forEach { assistant ->
                                val isSelected = assistant.id == state.selectedAssistantId
                                Surface(
                                    onClick = {
                                        onHapticPop()
                                        onSelectAssistant(assistant.id)
                                    },
                                    shape = AppShapes.ButtonPill,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    else null,
                                ) {
                                    Text(
                                        text = assistant.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    onHapticPop()
                                    onNewAssistant()
                                },
                            ) {
                                Text("+ New")
                            }
                        }
                    }

                    // Hero Header
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.assistant.name.equals("Generical", ignoreCase = true) || state.assistant.name.isBlank()) {
                                    Image(
                                        painter = rememberDefaultGenericalPainter(),
                                        contentDescription = "Generical",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = state.assistant.name.firstOrNull()?.uppercase() ?: "A",
                                                style = MaterialTheme.typography.displaySmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = state.assistant.name.ifBlank { "Generical" },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )

                            if (state.assistant.systemPrompt.isNotBlank()) {
                                Text(
                                    text = state.assistant.systemPrompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                    }

                    // Configuration Group
                    item {
                        LastChatSettingsGroup(title = "Configuration") {
                            LastChatSettingGroupItem(
                                title = "Profile",
                                subtitle = "Name, avatar, and persona tags",
                                icon = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) },
                                darkTheme = darkTheme,
                                onHaptic = onHapticPop,
                                onClick = { subRoute = AssistantDetailSubRoute.Profile },
                            )
                            LastChatSettingGroupItem(
                                title = "System instructions",
                                subtitle = "Prompt directives and character behavior",
                                icon = { Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = MaterialTheme.colorScheme.primary) },
                                darkTheme = darkTheme,
                                onHaptic = onHapticPop,
                                onClick = { subRoute = AssistantDetailSubRoute.Prompt },
                            )
                            LastChatSettingGroupItem(
                                title = "Default models",
                                subtitle = "Chat model: ${state.provider.modelId}",
                                icon = { Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary) },
                                darkTheme = darkTheme,
                                onHaptic = onHapticPop,
                                onClick = onNavigateToModels,
                            )
                        }
                    }

                    // Capabilities Group
                    item {
                        LastChatSettingsGroup(title = "Capabilities") {
                            LastChatSettingGroupItem(
                                title = "Memory & Recall",
                                subtitle = "Mode: ${state.assistant.memoryMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                icon = { Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary) },
                                darkTheme = darkTheme,
                                onHaptic = onHapticPop,
                                onClick = onNavigateToMemory,
                            )
                            LastChatSettingGroupItem(
                                title = "Tools & Search",
                                subtitle = "Local tools, web search, and MCP integrations",
                                icon = { Icon(Icons.Rounded.Build, null, tint = MaterialTheme.colorScheme.primary) },
                                darkTheme = darkTheme,
                                onHaptic = onHapticPop,
                                onClick = onNavigateToTools,
                            )
                        }
                    }

                    // Assistant Actions
                    if (state.assistants.size > 1) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showDeleteConfirmation = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                shape = AppShapes.ButtonPill,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Delete Assistant")
                            }
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }

            AssistantDetailSubRoute.Profile -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextButton(onClick = { subRoute = AssistantDetailSubRoute.Home }) {
                        Text("← Back to Overview")
                    }

                    Text(
                        "Assistant Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    LastChatFormItem(label = { Text("Name") }) {
                        OutlinedTextField(
                            value = assistantName,
                            onValueChange = { assistantName = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.InputField,
                            singleLine = true,
                        )
                    }

                    Button(
                        onClick = {
                            onHapticPop()
                            onSaveAssistant(assistantName, systemPrompt)
                            subRoute = AssistantDetailSubRoute.Home
                        },
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save Changes")
                    }
                }
            }

            AssistantDetailSubRoute.Prompt -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextButton(onClick = { subRoute = AssistantDetailSubRoute.Home }) {
                        Text("← Back to Overview")
                    }

                    Text(
                        "System Instructions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    LastChatFormItem(
                        label = { Text("Prompt") },
                        description = { Text("Define how this character thinks, behaves, and responds") },
                    ) {
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.InputField,
                            minLines = 6,
                            maxLines = 14,
                        )
                    }

                    Button(
                        onClick = {
                            onHapticPop()
                            onSaveAssistant(assistantName, systemPrompt)
                            subRoute = AssistantDetailSubRoute.Home
                        },
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save Changes")
                    }
                }
            }

            AssistantDetailSubRoute.Models -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextButton(onClick = { subRoute = AssistantDetailSubRoute.Home }) {
                        Text("← Back to Overview")
                    }
                    onNavigateToModels()
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Assistant") },
            text = { Text("Are you sure you want to delete \"${state.assistant.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onHapticPop()
                        onDeleteAssistant(state.assistant.id)
                        showDeleteConfirmation = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp),
        )
    }
}
