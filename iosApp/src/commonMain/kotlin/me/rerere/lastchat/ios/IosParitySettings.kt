package me.rerere.lastchat.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
) {
    LastChatSettingsGroup(title = "Developer", horizontalPadding = 0.dp, titleStartPadding = 0.dp) {
        LastChatSettingGroupInputItem(
            title = "Developer mode",
            subtitle = "Same product knob as Android's developer destination. Live request logs stay in the Android debug page until a portable log ring is shared.",
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
