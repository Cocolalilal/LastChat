package me.rerere.lastchat.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import me.rerere.common.log.PortableDebugLog
import me.rerere.rikkahub.data.ai.AILogging
import me.rerere.rikkahub.data.mcp.PortableMcpServer
import me.rerere.rikkahub.data.mcp.PortableMcpTransport
import me.rerere.rikkahub.data.prompt.LorebookActivationKind
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition
import me.rerere.rikkahub.ui.components.settings.LastChatFormItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupInputItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.common.runtime.local.LocalModelKind
import me.rerere.common.runtime.local.PortableDownload
import kotlin.uuid.Uuid

@Composable
internal fun IosSkillsSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSave: (List<PortableSkill>, List<PortableLorebook>, Set<String>, Set<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    LastChatSettingsGroup(title = "Skills", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Prompt skills",
            subtitle = "Create, edit, enable, and delete skills with Android's injection positions",
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
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    singleLine = true,
                    label = { Text("Description") },
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
                            description = description.trim(),
                            instructions = instructions.trim(),
                        )
                        if (skill.name.isBlank() || skill.instructions.isBlank()) return@Button
                        onSave(
                            state.skills.upsertSkill(skill),
                            state.lorebooks,
                            state.assistant.enabledSkillIds + skill.id,
                            state.assistant.enabledLorebookIds,
                        )
                        name = ""
                        description = ""
                        instructions = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add skill") }
            }
        }
        state.skills.forEach { skill ->
            val enabled = skill.id in state.assistant.enabledSkillIds || skill.alwaysEnabled
            val editing = editingId == skill.id
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
                LastChatFormItem(
                    label = { Text("Always enabled") },
                    tail = {
                        Switch(
                            checked = skill.alwaysEnabled,
                            onCheckedChange = { checked ->
                                onSave(
                                    state.skills.upsertSkill(skill.copy(alwaysEnabled = checked)),
                                    state.lorebooks,
                                    state.assistant.enabledSkillIds,
                                    state.assistant.enabledLorebookIds,
                                )
                            },
                        )
                    },
                )
                TextButton(onClick = { editingId = if (editing) null else skill.id }) {
                    Text(if (editing) "Close editor" else "Edit")
                }
                if (editing) {
                    IosSkillEditor(
                        skill = skill,
                        onSaveSkill = { updated ->
                            onSave(
                                state.skills.upsertSkill(updated),
                                state.lorebooks,
                                state.assistant.enabledSkillIds,
                                state.assistant.enabledLorebookIds,
                            )
                            editingId = null
                        },
                    )
                }
                TextButton(onClick = {
                    onSave(
                        state.skills.filterNot { it.id == skill.id },
                        state.lorebooks,
                        state.assistant.enabledSkillIds - skill.id,
                        state.assistant.enabledLorebookIds,
                    )
                    if (editingId == skill.id) editingId = null
                }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun IosSkillEditor(
    skill: PortableSkill,
    onSaveSkill: (PortableSkill) -> Unit,
) {
    var name by remember(skill.id) { mutableStateOf(skill.name) }
    var description by remember(skill.id) { mutableStateOf(skill.description) }
    var instructions by remember(skill.id) { mutableStateOf(skill.instructions) }
    var position by remember(skill.id) { mutableStateOf(skill.injectionPosition) }
    var depth by remember(skill.id) { mutableStateOf(skill.depth.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            singleLine = true,
            label = { Text("Name") },
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            singleLine = true,
            label = { Text("Description") },
        )
        OutlinedTextField(
            value = instructions,
            onValueChange = { instructions = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            minLines = 3,
            label = { Text("Instructions") },
        )
        Text("Injection: ${promptInjectionLabel(position)}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            PromptInjectionPosition.entries.forEach { option ->
                val label = promptInjectionLabel(option).substringBefore(" ")
                if (option == position) {
                    Button(onClick = {}) { Text(label) }
                } else {
                    TextButton(onClick = { position = option }) { Text(label) }
                }
            }
        }
        if (position == PromptInjectionPosition.AT_DEPTH) {
            OutlinedTextField(
                value = depth,
                onValueChange = { depth = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.InputField,
                singleLine = true,
                label = { Text("Depth") },
            )
        }
        Button(
            onClick = {
                val updated = skill.copy(
                    name = name.trim().ifBlank { skill.name },
                    description = description.trim(),
                    instructions = instructions.trim().ifBlank { skill.instructions },
                    injectionPosition = position,
                    depth = depth.toIntOrNull() ?: skill.depth,
                )
                onSaveSkill(updated)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save skill") }
    }
}

@Composable
internal fun IosLorebookSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSave: (List<PortableSkill>, List<PortableLorebook>, Set<String>, Set<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var editingBookId by remember { mutableStateOf<String?>(null) }
    LastChatSettingsGroup(title = "Lorebooks", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "World info",
            subtitle = "Create, edit, enable, and delete lorebooks and entries with Android's activation kinds",
            darkTheme = darkTheme,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("Book name") })
                OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("Description") })
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, minLines = 3, label = { Text("First entry prompt") })
                OutlinedTextField(value = keywords, onValueChange = { keywords = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("Keywords (comma separated)") })
                Button(
                    onClick = {
                        val bookName = name.trim().ifBlank { "Lorebook" }
                        val entryPrompt = prompt.trim()
                        if (entryPrompt.isBlank()) return@Button
                        val parsedKeywords = parseLorebookKeywords(keywords)
                        val book = PortableLorebook(
                            id = Uuid.random().toString(),
                            name = bookName,
                            description = description.trim(),
                            entries = listOf(
                                PortableLorebookEntry(
                                    id = Uuid.random().toString(),
                                    name = bookName,
                                    prompt = entryPrompt,
                                    activationType = lorebookActivationForKeywords(
                                        parsedKeywords,
                                        LorebookActivationKind.KEYWORDS,
                                    ),
                                    keywords = parsedKeywords,
                                )
                            ),
                        )
                        onSave(
                            state.skills,
                            state.lorebooks.upsertLorebook(book),
                            state.assistant.enabledSkillIds,
                            state.assistant.enabledLorebookIds + book.id,
                        )
                        name = ""
                        description = ""
                        prompt = ""
                        keywords = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add lorebook") }
            }
        }
        state.lorebooks.forEach { book ->
            val enabled = book.id in state.assistant.enabledLorebookIds
            val editing = editingBookId == book.id
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
                TextButton(onClick = { editingBookId = if (editing) null else book.id }) {
                    Text(if (editing) "Close editor" else "Edit")
                }
                if (editing) {
                    IosLorebookEditor(
                        book = book,
                        onSaveBook = { updated ->
                            onSave(
                                state.skills,
                                state.lorebooks.upsertLorebook(updated),
                                state.assistant.enabledSkillIds,
                                state.assistant.enabledLorebookIds,
                            )
                        },
                    )
                }
                TextButton(onClick = {
                    onSave(
                        state.skills,
                        state.lorebooks.filterNot { it.id == book.id },
                        state.assistant.enabledSkillIds,
                        state.assistant.enabledLorebookIds - book.id,
                    )
                    if (editingBookId == book.id) editingBookId = null
                }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun IosLorebookEditor(
    book: PortableLorebook,
    onSaveBook: (PortableLorebook) -> Unit,
) {
    var name by remember(book.id) { mutableStateOf(book.name) }
    var description by remember(book.id) { mutableStateOf(book.description) }
    var entryName by remember(book.id) { mutableStateOf("") }
    var entryPrompt by remember(book.id) { mutableStateOf("") }
    var entryKeywords by remember(book.id) { mutableStateOf("") }
    var entryActivation by remember(book.id) { mutableStateOf(LorebookActivationKind.KEYWORDS) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            singleLine = true,
            label = { Text("Book name") },
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            singleLine = true,
            label = { Text("Description") },
        )
        Button(
            onClick = {
                onSaveBook(book.copy(name = name.trim().ifBlank { book.name }, description = description.trim()))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save book") }
        book.entries.forEach { entry ->
            IosLorebookEntryEditor(
                entry = entry,
                onSaveEntry = { onSaveBook(book.replaceEntry(it)) },
                onDeleteEntry = { onSaveBook(book.removeEntry(entry.id)) },
            )
        }
        Text("New entry", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = entryName,
            onValueChange = { entryName = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            singleLine = true,
            label = { Text("Entry name") },
        )
        OutlinedTextField(
            value = entryPrompt,
            onValueChange = { entryPrompt = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            minLines = 3,
            label = { Text("Prompt") },
        )
        OutlinedTextField(
            value = entryKeywords,
            onValueChange = { entryKeywords = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            singleLine = true,
            label = { Text("Keywords") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LorebookActivationKind.entries.forEach { kind ->
                val label = kind.name.lowercase().replaceFirstChar { it.uppercase() }
                if (kind == entryActivation) {
                    Button(onClick = {}) { Text(label) }
                } else {
                    TextButton(onClick = { entryActivation = kind }) { Text(label) }
                }
            }
        }
        Button(
            onClick = {
                val parsed = parseLorebookKeywords(entryKeywords)
                val created = PortableLorebookEntry(
                    id = Uuid.random().toString(),
                    name = entryName.trim().ifBlank { "Entry" },
                    prompt = entryPrompt.trim(),
                    activationType = lorebookActivationForKeywords(parsed, entryActivation),
                    keywords = parsed,
                )
                if (created.prompt.isBlank()) return@Button
                onSaveBook(book.replaceEntry(created))
                entryName = ""
                entryPrompt = ""
                entryKeywords = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add entry") }
    }
}

@Composable
private fun IosLorebookEntryEditor(
    entry: PortableLorebookEntry,
    onSaveEntry: (PortableLorebookEntry) -> Unit,
    onDeleteEntry: () -> Unit,
) {
    var name by remember(entry.id) { mutableStateOf(entry.name) }
    var prompt by remember(entry.id) { mutableStateOf(entry.prompt) }
    var keywords by remember(entry.id) { mutableStateOf(entry.keywords.joinToString(", ")) }
    var activation by remember(entry.id) { mutableStateOf(entry.activationType) }
    var caseSensitive by remember(entry.id) { mutableStateOf(entry.caseSensitive) }
    var enabled by remember(entry.id) { mutableStateOf(entry.enabled) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Text(entry.name.ifBlank { "Entry" }, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            singleLine = true,
            label = { Text("Name") },
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            minLines = 2,
            label = { Text("Prompt") },
        )
        OutlinedTextField(
            value = keywords,
            onValueChange = { keywords = it },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.InputField,
            singleLine = true,
            label = { Text("Keywords") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LorebookActivationKind.entries.forEach { kind ->
                val label = kind.name.lowercase().replaceFirstChar { it.uppercase() }
                if (kind == activation) {
                    Button(onClick = {}) { Text(label) }
                } else {
                    TextButton(onClick = { activation = kind }) { Text(label) }
                }
            }
        }
        LastChatFormItem(
            label = { Text("Enabled") },
            tail = { Switch(checked = enabled, onCheckedChange = { enabled = it }) },
        )
        LastChatFormItem(
            label = { Text("Case sensitive") },
            tail = { Switch(checked = caseSensitive, onCheckedChange = { caseSensitive = it }) },
        )
        Button(
            onClick = {
                val parsed = parseLorebookKeywords(keywords)
                onSaveEntry(
                    entry.copy(
                        name = name.trim().ifBlank { entry.name },
                        prompt = prompt.trim().ifBlank { entry.prompt },
                        keywords = parsed,
                        activationType = lorebookActivationForKeywords(parsed, activation),
                        caseSensitive = caseSensitive,
                        enabled = enabled,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save entry") }
        TextButton(onClick = onDeleteEntry) { Text("Delete entry") }
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
    var transport by remember { mutableStateOf(PortableMcpTransport.STREAMABLE_HTTP) }
    LastChatSettingsGroup(title = "MCP servers", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "MCP transport",
            subtitle = "Streamable HTTP and SSE use the shared JSON-RPC client over PlatformHttpClient",
            darkTheme = darkTheme,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("Name") })
                OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), shape = AppShapes.InputField, singleLine = true, label = { Text("URL") })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PortableMcpTransport.entries.forEach { option ->
                        val label = if (option == PortableMcpTransport.SSE) "SSE" else "HTTP"
                        if (option == transport) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            TextButton(onClick = { transport = option }) { Text(label) }
                        }
                    }
                }
                Button(
                    onClick = {
                        val server = PortableMcpServer(
                            name = name.trim().ifBlank { "MCP" },
                            url = url.trim(),
                            transport = transport,
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PortableMcpTransport.entries.forEach { option ->
                        val label = if (option == PortableMcpTransport.SSE) "SSE" else "HTTP"
                        if (option == server.transport) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            TextButton(onClick = {
                                onSave(
                                    state.mcpServers.map { if (it.id == server.id) it.copy(transport = option) else it },
                                    state.assistant.enabledMcpServerIds,
                                )
                            }) { Text(label) }
                        }
                    }
                }
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
                if (IosNativeFiles.webUiBundled) {
                    "Serving the bundled web UI and JSON API on port ${state.web.port}."
                } else {
                    "JSON API and fallback web client are listening on port ${state.web.port}. Package web-ui/build/client into the Xcode webui folder for the full React SPA."
                }
            } else {
                "Serves a password-gated JSON API plus the React web-ui when the Xcode webui folder is populated from web-ui/build/client."
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
internal fun IosWorkspaceSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onDownloadLlm: (String) -> Unit,
    onDownloadStt: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteLlm: (String) -> Unit,
    onDeleteStt: (String) -> Unit,
) {
    val installedSttIds = state.installedStt.map { it.id }.toSet()
    LastChatSettingsGroup(title = "Workspaces", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = if (state.onDeviceWorkspaceAvailable) "Linux sandbox" else "On-device sandbox",
            subtitle = if (state.onDeviceWorkspaceAvailable) {
                "Workspace tools are active for this assistant."
            } else {
                state.onDeviceWorkspaceUnavailableReason
            },
            darkTheme = darkTheme,
        ) {
            Text(
                if (state.onDeviceWorkspaceAvailable) {
                    "Read/write/shell tools share the same generation loop as Android."
                } else {
                    "Chat orchestration still uses the shared tool loop. Workspace tools report this unavailable reason instead of a second iOS engine."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        LastChatSettingGroupInputItem(
            title = "On-device LLM",
            subtitle = if (state.onDeviceLlmAvailable) {
                "Local models are available."
            } else {
                state.onDeviceLlmUnavailableReason
            },
            darkTheme = darkTheme,
        ) {
            Text(
                "Catalog, download, and delete use the shared PortableOnDeviceModelManager. Inference still goes through OnDeviceLlmRuntime — currently ${if (state.onDeviceLlmAvailable) "available" else "unavailable"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        state.llmCatalog.models.forEach { meta ->
            val download = state.localDownloads[meta.id]
            val installed = state.installedLlm.firstOrNull { it.id == meta.id }
            LastChatSettingGroupInputItem(
                title = meta.name,
                subtitle = buildString {
                    append(iosByteSize(meta.sizeInBytes))
                    append(" · ")
                    append(if (installed != null) "installed" else "catalog")
                    if (meta.kind == LocalModelKind.EMBEDDING) append(" · embedding")
                },
                darkTheme = darkTheme,
            ) {
                Text(
                    meta.description.ifBlank { meta.id },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (download) {
                    is PortableDownload.Running -> {
                        LinearProgressIndicator(
                            progress = { download.progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        TextButton(onClick = { onCancelDownload(meta.id) }) { Text("Cancel") }
                    }
                    is PortableDownload.Failed -> {
                        Text(
                            download.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onDownloadLlm(meta.id) }) { Text("Retry") }
                    }
                    null -> {
                        if (installed != null) {
                            TextButton(onClick = { onDeleteLlm(meta.id) }) { Text("Delete") }
                        } else {
                            TextButton(onClick = { onDownloadLlm(meta.id) }) { Text("Download") }
                        }
                    }
                }
            }
        }
        LastChatSettingGroupInputItem(
            title = "On-device speech-to-text",
            subtitle = if (state.sttCatalog.models.isEmpty()) {
                "No bundled Sherpa catalog"
            } else {
                "${state.sttCatalog.models.size} catalog models · ${installedSttIds.size} installed"
            },
            darkTheme = darkTheme,
        ) {
            Text(
                "Downloads use the same manager as LLM files. On-device STT inference is not a separate iOS path — it reports the shared unavailable runtime until a native decoder exists.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        state.sttCatalog.models.forEach { meta ->
            val download = state.localDownloads[meta.id]
            val installed = installedSttIds.contains(meta.id)
            LastChatSettingGroupInputItem(
                title = meta.name,
                subtitle = "${iosByteSize(meta.archiveSizeBytes)} · ${meta.family.name.lowercase()}",
                darkTheme = darkTheme,
            ) {
                when (download) {
                    is PortableDownload.Running -> {
                        LinearProgressIndicator(
                            progress = { download.progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        TextButton(onClick = { onCancelDownload(meta.id) }) { Text("Cancel") }
                    }
                    is PortableDownload.Failed -> {
                        Text(
                            download.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onDownloadStt(meta.id) }) { Text("Retry") }
                    }
                    null -> {
                        if (installed) {
                            TextButton(onClick = { onDeleteStt(meta.id) }) { Text("Delete") }
                        } else {
                            TextButton(onClick = { onDownloadStt(meta.id) }) { Text("Download") }
                        }
                    }
                }
            }
        }
    }
}

private fun iosByteSize(bytes: Long): String {
    if (bytes < 1_000L) return "$bytes B"
    if (bytes < 1_000_000L) return "${bytes / 1_000L} KB"
    if (bytes < 1_000_000_000L) return "${bytes / 1_000_000L} MB"
    val tenths = (bytes * 10L) / 1_000_000_000L
    return "${tenths / 10}.${tenths % 10} GB"
}

@Composable
internal fun IosAndroidIntegrationSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSaveOverlay: (String?, Boolean, Boolean, Boolean) -> Unit,
) {
    var overlayAssistantId by remember(state.overlay.assistantId) {
        mutableStateOf(state.overlay.assistantId)
    }
    var autoStartStt by remember(state.overlay.autoStartStt) { mutableStateOf(state.overlay.autoStartStt) }
    var autoSend by remember(state.overlay.autoSendOnSttFinish) {
        mutableStateOf(state.overlay.autoSendOnSttFinish)
    }
    var autoRead by remember(state.overlay.autoReadReply) { mutableStateOf(state.overlay.autoReadReply) }
    LastChatSettingsGroup(title = "Platform integrations", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Digital assistant overlay",
            subtitle = "Siri shortcut \"Ask LastChat\", lastchat://overlay deep links, and the in-app overlay composer share the same send path as Android Assist.",
            darkTheme = darkTheme,
        ) {
            LastChatFormItem(label = { Text("Overlay assistant") }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.assistants.forEach { assistant ->
                        val selected = (overlayAssistantId ?: state.assistant.id) == assistant.id
                        if (selected) {
                            Button(onClick = {}) { Text(assistant.name) }
                        } else {
                            TextButton(onClick = { overlayAssistantId = assistant.id }) {
                                Text(assistant.name)
                            }
                        }
                    }
                }
            }
            LastChatFormItem(
                label = { Text("Start speech-to-text when overlay opens") },
                tail = {
                    Switch(checked = autoStartStt, onCheckedChange = { autoStartStt = it })
                },
            )
            LastChatFormItem(
                label = { Text("Send when transcription finishes") },
                tail = {
                    Switch(checked = autoSend, onCheckedChange = { autoSend = it })
                },
            )
            LastChatFormItem(
                label = { Text("Read replies aloud") },
                tail = {
                    Switch(checked = autoRead, onCheckedChange = { autoRead = it })
                },
            )
            Button(
                onClick = { onSaveOverlay(overlayAssistantId, autoStartStt, autoSend, autoRead) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save overlay settings") }
        }
        LastChatSettingGroupInputItem(
            title = "Share sheet",
            subtitle = "Conversation export uses the iOS share sheet (UIActivityViewController).",
            darkTheme = darkTheme,
        ) {}
        LastChatSettingGroupInputItem(
            title = "Home screen widget",
            subtitle = "Shared AssistantWidgetSnapshot is published to App Group UserDefaults for the WidgetKit shell.",
            darkTheme = darkTheme,
        ) {}
        LastChatSettingGroupInputItem(
            title = "System TTS",
            subtitle = "AVSpeech synthesizer is wired through the shared PlatformSystemTts contract.",
            darkTheme = darkTheme,
        ) {}
        LastChatSettingGroupInputItem(
            title = "Android-only surfaces",
            subtitle = ANDROID_INTEGRATION_UNAVAILABLE_REASON,
            darkTheme = darkTheme,
        ) {}
    }
}

@Composable
internal fun IosDeveloperSettings(
    state: IosAppState,
    darkTheme: Boolean,
    onSaveDeveloperMode: (Boolean) -> Unit,
    onClearAiLogs: () -> Unit = {},
) {
    var debugTick by remember { mutableStateOf(0) }
    val debugLogs = remember(debugTick, state.aiLogs.size) { PortableDebugLog.getRecentLogs() }
    LastChatSettingsGroup(title = "Developer", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Developer mode",
            subtitle = "Same product knob as Android's developer destination. Generation requests use the shared AILoggingManager ring.",
            darkTheme = darkTheme,
        ) {
            LastChatFormItem(
                label = { Text("Enable developer tools") },
                tail = {
                    Switch(
                        checked = state.appearance.developerMode,
                        onCheckedChange = onSaveDeveloperMode,
                    )
                },
            )
        }
        LastChatSettingGroupInputItem(
            title = "AI request log",
            subtitle = if (state.aiLogs.isEmpty()) {
                "No generation requests yet. The last 10 prepared turns are kept in memory."
            } else {
                "${state.aiLogs.size} generation request(s)"
            },
            darkTheme = darkTheme,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.aiLogs.asReversed().forEach { log ->
                    val generation = log as? AILogging.Generation ?: return@forEach
                    Text(
                        generation.developerLine(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.aiLogs.isNotEmpty() || debugLogs.isNotEmpty()) {
                    TextButton(onClick = {
                        onClearAiLogs()
                        PortableDebugLog.clear()
                        debugTick += 1
                    }) { Text("Clear logs") }
                }
            }
        }
        if (debugLogs.isNotEmpty()) {
            LastChatSettingGroupInputItem(
                title = "Debug ring",
                subtitle = "${debugLogs.size} recent platform log lines (shared PortableDebugLog)",
                darkTheme = darkTheme,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    debugLogs.take(20).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        LastChatSettingGroupInputItem(
            title = "Siri and overlays",
            subtitle = "Ask LastChat App Intent writes pending_overlay_prompt into the App Group suite. lastchat://overlay?text= opens the same overlay.",
            darkTheme = darkTheme,
        ) {}
        LastChatSettingGroupInputItem(
            title = "Portable analytics",
            subtitle = "${state.usageTotals.conversationCount} conversations · ${state.usageTotals.messageCount} messages · ${state.dailyActivity.size} heatmap days (survives chat deletion)",
            darkTheme = darkTheme,
        ) {}
    }
}
