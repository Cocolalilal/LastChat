package me.rerere.lastchat.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.mcp.PortableMcpServer
import me.rerere.rikkahub.data.mcp.PortableMcpTransport
import me.rerere.rikkahub.data.prompt.LorebookActivationKind
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.ui.components.settings.LastChatFormItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupInputItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.uuid.Uuid

@Composable
internal fun IosSkillsSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSave: (List<PortableSkill>, List<PortableLorebook>, Set<String>, Set<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    LastChatSettingsGroup(title = "Skills", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Prompt skills",
            subtitle = "Injected with Android's skill positions and manage_skills semantics",
            darkTheme = darkTheme,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    singleLine = true,
                    label = { Text("Name") },
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    minLines = 3,
                    label = { Text("Instructions") },
                )
                Button(
                    onClick = {
                        val skill = PortableSkill(
                            id = Uuid.random().toString(),
                            name = name.trim(),
                            instructions = instructions.trim(),
                        )
                        if (skill.name.isBlank() || skill.instructions.isBlank()) return@Button
                        onSave(
                            state.skills + skill,
                            state.lorebooks,
                            state.assistant.enabledSkillIds + skill.id,
                            state.assistant.enabledLorebookIds,
                        )
                        name = ""
                        instructions = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add skill") }
            }
        }
        state.skills.forEach { skill ->
            val enabled = skill.id in state.assistant.enabledSkillIds || skill.alwaysEnabled
            LastChatSettingGroupInputItem(
                title = skill.name,
                subtitle = skill.description.ifBlank { skill.instructions.take(80) },
                darkTheme = darkTheme,
            ) {
                LastChatFormItem(
                    label = { Text("Enabled for this assistant") },
                    tail = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val ids = if (checked) {
                                    state.assistant.enabledSkillIds + skill.id
                                } else {
                                    state.assistant.enabledSkillIds - skill.id
                                }
                                onSave(state.skills, state.lorebooks, ids, state.assistant.enabledLorebookIds)
                            },
                        )
                    },
                )
                TextButton(onClick = {
                    onSave(
                        state.skills.filterNot { it.id == skill.id },
                        state.lorebooks,
                        state.assistant.enabledSkillIds - skill.id,
                        state.assistant.enabledLorebookIds,
                    )
                }) { Text("Delete") }
            }
        }
    }
}

@Composable
internal fun IosLorebookSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSave: (List<PortableSkill>, List<PortableLorebook>, Set<String>, Set<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    LastChatSettingsGroup(title = "Lorebooks", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "World info",
            subtitle = "Always-on and keyword activation use the shared injection engine",
            darkTheme = darkTheme,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("Book name") })
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, minLines = 3, label = { Text("Entry prompt") })
                OutlinedTextField(value = keywords, onValueChange = { keywords = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("Keywords (comma separated)") })
                Button(
                    onClick = {
                        val bookName = name.trim().ifBlank { "Lorebook" }
                        val entryPrompt = prompt.trim()
                        if (entryPrompt.isBlank()) return@Button
                        val book = PortableLorebook(
                            id = Uuid.random().toString(),
                            name = bookName,
                            entries = listOf(
                                PortableLorebookEntry(
                                    id = Uuid.random().toString(),
                                    name = bookName,
                                    prompt = entryPrompt,
                                    activationType = if (keywords.isBlank()) {
                                        LorebookActivationKind.ALWAYS
                                    } else {
                                        LorebookActivationKind.KEYWORDS
                                    },
                                    keywords = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                )
                            ),
                        )
                        onSave(
                            state.skills,
                            state.lorebooks + book,
                            state.assistant.enabledSkillIds,
                            state.assistant.enabledLorebookIds + book.id,
                        )
                        name = ""
                        prompt = ""
                        keywords = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add lorebook") }
            }
        }
        state.lorebooks.forEach { book ->
            val enabled = book.id in state.assistant.enabledLorebookIds
            LastChatSettingGroupInputItem(title = book.name, subtitle = "${book.entries.size} entries", darkTheme = darkTheme) {
                LastChatFormItem(
                    label = { Text("Enabled for this assistant") },
                    tail = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val ids = if (checked) {
                                    state.assistant.enabledLorebookIds + book.id
                                } else {
                                    state.assistant.enabledLorebookIds - book.id
                                }
                                onSave(state.skills, state.lorebooks, state.assistant.enabledSkillIds, ids)
                            },
                        )
                    },
                )
                TextButton(onClick = {
                    onSave(
                        state.skills,
                        state.lorebooks.filterNot { it.id == book.id },
                        state.assistant.enabledSkillIds,
                        state.assistant.enabledLorebookIds - book.id,
                    )
                }) { Text("Delete") }
            }
        }
    }
}

@Composable
internal fun IosMcpSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSave: (List<PortableMcpServer>, Set<String>) -> Unit,
    onRefresh: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    LastChatSettingsGroup(title = "MCP servers", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Streamable HTTP",
            subtitle = "Uses the shared JSON-RPC client over PlatformHttpClient",
            darkTheme = darkTheme,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("Name") })
                OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("URL") })
                Button(
                    onClick = {
                        val server = PortableMcpServer(
                            name = name.trim().ifBlank { "MCP" },
                            url = url.trim(),
                            transport = PortableMcpTransport.STREAMABLE_HTTP,
                        )
                        if (server.url.isBlank()) return@Button
                        onSave(state.mcpServers + server, state.assistant.enabledMcpServerIds + server.id)
                        name = ""
                        url = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add MCP server") }
            }
        }
        state.mcpServers.forEach { server ->
            val enabled = server.id in state.assistant.enabledMcpServerIds
            LastChatSettingGroupInputItem(
                title = server.name.ifBlank { server.url },
                subtitle = "${server.tools.count { it.enable }} tools · ${server.transport.name.lowercase()}",
                darkTheme = darkTheme,
            ) {
                LastChatFormItem(
                    label = { Text("Use with this assistant") },
                    tail = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val ids = if (checked) {
                                    state.assistant.enabledMcpServerIds + server.id
                                } else {
                                    state.assistant.enabledMcpServerIds - server.id
                                }
                                onSave(state.mcpServers, ids)
                            },
                        )
                    },
                )
                TextButton(onClick = { onRefresh(server.id) }) { Text("Discover tools") }
                TextButton(onClick = {
                    onSave(
                        state.mcpServers.filterNot { it.id == server.id },
                        state.assistant.enabledMcpServerIds - server.id,
                    )
                }) { Text("Delete") }
            }
        }
    }
}

@Composable
internal fun IosSttSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSave: (IosSttPreferences, String) -> Unit,
    onClearKey: () -> Unit,
) {
    var preferences by remember(state.stt) { mutableStateOf(state.stt) }
    var apiKey by remember { mutableStateOf("") }
    LastChatSettingsGroup(title = "Speech-to-text", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "OpenAI-compatible transcriptions",
            subtitle = "Posts WAV audio to /audio/transcriptions. Keys stay in Keychain.",
            darkTheme = darkTheme,
        ) {
            LastChatFormItem(
                label = { Text("Enable speech-to-text") },
                tail = {
                    Switch(
                        checked = preferences.enabled,
                        onCheckedChange = { preferences = preferences.copy(enabled = it) },
                    )
                },
            )
            LastChatFormItem(label = { Text("Base URL") }) {
                OutlinedTextField(value = preferences.baseUrl, onValueChange = { preferences = preferences.copy(baseUrl = it) }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true)
            }
            LastChatFormItem(label = { Text("Model") }) {
                OutlinedTextField(value = preferences.model, onValueChange = { preferences = preferences.copy(model = it) }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true)
            }
            LastChatFormItem(label = { Text("Language") }) {
                OutlinedTextField(value = preferences.language, onValueChange = { preferences = preferences.copy(language = it) }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true)
            }
            LastChatFormItem(label = { Text(if (state.hasSttApiKey) "API key (saved in Keychain)" else "API key") }) {
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true)
            }
            Button(
                onClick = {
                    onSave(preferences, apiKey)
                    apiKey = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save speech-to-text") }
            if (state.hasSttApiKey) {
                TextButton(onClick = onClearKey) { Text("Remove saved API key") }
            }
        }
    }
}

@Composable
internal fun IosWebSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSave: (IosWebPreferences, String) -> Unit,
) {
    var preferences by remember(state.web) { mutableStateOf(state.web) }
    var password by remember { mutableStateOf("") }
    LastChatSettingsGroup(title = "Web server", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Remote access",
            subtitle = if (state.webApiRunning) {
                "Local JSON API is listening on port ${state.web.port}."
            } else {
                "Serves a password-gated JSON API for conversations. The bundled React web-ui still needs Xcode asset packaging before the full Android SPA is hosted here."
            },
            darkTheme = darkTheme,
        ) {
            LastChatFormItem(
                label = { Text("Enable local web API") },
                description = { Text("Listens on port ${preferences.port} after the next launch on device") },
                tail = {
                    Switch(
                        checked = preferences.enabled,
                        onCheckedChange = { preferences = preferences.copy(enabled = it) },
                    )
                },
            )
            LastChatFormItem(label = { Text("Port") }) {
                OutlinedTextField(
                    value = preferences.port.toString(),
                    onValueChange = { value ->
                        preferences = preferences.copy(port = value.filter(Char::isDigit).toIntOrNull() ?: preferences.port)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    singleLine = true,
                )
            }
            LastChatFormItem(label = { Text("Password (Keychain)") }) {
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true)
            }
            Button(
                onClick = {
                    onSave(preferences, password)
                    password = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save web server") }
        }
    }
}

@Composable
internal fun IosWebDavSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSave: (IosWebDavPreferences, String) -> Unit,
    onTest: () -> Unit,
    onList: () -> Unit,
    onBackup: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var preferences by remember(state.webDav) { mutableStateOf(state.webDav) }
    var password by remember { mutableStateOf("") }
    LastChatSettingsGroup(title = "WebDAV backup", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Remote backups",
            subtitle = "Uses the shared WebDAV client over PlatformHttpClient. The password stays in Keychain.",
            darkTheme = darkTheme,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = preferences.url,
                    onValueChange = { preferences = preferences.copy(url = it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    singleLine = true,
                    label = { Text("Server URL") },
                )
                OutlinedTextField(
                    value = preferences.username,
                    onValueChange = { preferences = preferences.copy(username = it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    singleLine = true,
                    label = { Text("Username") },
                )
                OutlinedTextField(
                    value = preferences.path,
                    onValueChange = { preferences = preferences.copy(path = it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    singleLine = true,
                    label = { Text("Remote folder") },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    singleLine = true,
                    label = { Text(if (state.hasWebDavPassword) "Password (saved in Keychain)" else "Password") },
                )
                Button(
                    onClick = {
                        onSave(preferences, password)
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.webDavBusy,
                ) { Text("Save WebDAV") }
                Button(onClick = onTest, modifier = Modifier.fillMaxWidth(), enabled = !state.webDavBusy) {
                    Text("Test connection")
                }
                Button(onClick = onBackup, modifier = Modifier.fillMaxWidth(), enabled = !state.webDavBusy) {
                    Text("Upload current backup")
                }
                Button(onClick = onList, modifier = Modifier.fillMaxWidth(), enabled = !state.webDavBusy) {
                    Text("Refresh remote files")
                }
            }
        }
        state.webDavItems.forEach { item ->
            LastChatSettingGroupInputItem(
                title = item.displayName,
                subtitle = item.contentLength?.let { "$it bytes" } ?: item.href,
                darkTheme = darkTheme,
            ) {
                TextButton(onClick = { onRestore(item.href) }, enabled = !state.webDavBusy) {
                    Text("Restore")
                }
                TextButton(onClick = { onDelete(item.href) }, enabled = !state.webDavBusy) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
internal fun IosWorkspaceSettings(darkTheme: Boolean) {
    LastChatSettingsGroup(title = "Workspaces", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Linux sandbox",
            subtitle = WORKSPACE_UNAVAILABLE_REASON,
            darkTheme = darkTheme,
        ) {
            Text(
                "Attachments, generated images, and document text extraction already use the iOS app container. Shell/PRoot workspace tools remain Android-only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun IosAndroidIntegrationSettings(darkTheme: Boolean) {
    LastChatSettingsGroup(title = "Android integration", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Android-only surfaces",
            subtitle = ANDROID_INTEGRATION_UNAVAILABLE_REASON,
            darkTheme = darkTheme,
        ) {}
    }
}
