package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.LinuxEnvironmentManager
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.setLinuxEnvironmentEnabled
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.ui.components.ai.McpPicker
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.utils.PermissionChecker
import me.rerere.search.SearchServiceOptions
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Tools & Search tab - Combined search, local tools, and MCP settings.
 * Designed with cohesive SettingsGroup pattern.
 */
@Composable
fun AssistantToolsSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM,
    mcpServerConfigs: List<McpServerConfig>
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    fun requestNotificationAccess() {
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
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // SEARCH GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.assistant_tools_search_group)) {
            // Build options list for Select
            val currentSearchMode = assistant.searchMode
            
            // Create sealed class options for the selector
            data class SearchOption(val mode: AssistantSearchMode, val displayName: String)
            
            val searchOptions = buildList {
                add(SearchOption(AssistantSearchMode.Off, stringResource(R.string.common_off)))
                settings.searchServices.forEachIndexed { index, service ->
                    val name = SearchServiceOptions.TYPES[service::class]
                        ?: stringResource(R.string.assistant_tools_provider_fallback, index + 1)
                    add(SearchOption(AssistantSearchMode.Provider(index), name))
                }
            }
            
            val selectedOption = searchOptions.find { option ->
                when (val mode = option.mode) {
                    is AssistantSearchMode.Off -> currentSearchMode is AssistantSearchMode.Off || currentSearchMode is AssistantSearchMode.BuiltIn
                    is AssistantSearchMode.BuiltIn -> currentSearchMode is AssistantSearchMode.BuiltIn
                    is AssistantSearchMode.Provider -> currentSearchMode is AssistantSearchMode.Provider && currentSearchMode.index == mode.index
                }
            } ?: searchOptions.first()
            
            SettingGroupItem(
                title = stringResource(R.string.assistant_tools_search_provider),
                subtitle = selectedOption.displayName,
                trailing = {
                    Select(
                        options = searchOptions,
                        selectedOption = selectedOption,
                        onOptionSelected = { option ->
                            onUpdate(assistant.copy(searchMode = option.mode))
                        },
                        optionToString = { it.displayName },
                        modifier = Modifier.width(150.dp)
                    )
                }
            )
            
            // Prefer Built-in Search
            SettingGroupItem(
                title = stringResource(R.string.assistant_tools_prefer_builtin),
                subtitle = stringResource(R.string.assistant_tools_prefer_builtin_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.preferBuiltInSearch,
                        onCheckedChange = { onUpdate(assistant.copy(preferBuiltInSearch = it)) }
                    )
                }
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // LOCAL TOOLS GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.assistant_page_tab_local_tools)) {
            val linuxOption = assistant.localTools.filterIsInstance<LocalToolOption.LinuxEnvironment>().firstOrNull()
            val linuxEnabled = linuxOption != null

            SettingGroupItem(
                title = stringResource(R.string.assistant_page_local_tools_linux_environment_title),
                subtitle = stringResource(R.string.assistant_page_local_tools_linux_environment_desc),
                trailing = {
                    HapticSwitch(
                        checked = linuxEnabled,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    localTools = setLinuxEnvironmentEnabled(assistant.localTools, enabled)
                                )
                            )
                        }
                    )
                }
            )

            if (linuxEnabled) {
                val activeLinuxOption = linuxOption ?: LocalToolOption.LinuxEnvironment()
                val linuxManager = remember { LinuxEnvironmentManager(context) }
                var linuxStatus by remember(linuxEnabled) { mutableStateOf(linuxManager.getStatus()) }
                var installingLinux by remember { mutableStateOf(false) }
                var linuxInstallMessage by remember { mutableStateOf("") }
                data class LinuxProfileOption(val fullToolchain: Boolean, val label: String)
                val profileOptions = listOf(
                    LinuxProfileOption(false, stringResource(R.string.assistant_page_local_tools_linux_profile_base)),
                    LinuxProfileOption(true, stringResource(R.string.assistant_page_local_tools_linux_profile_full)),
                )
                val selectedProfile = profileOptions.first { it.fullToolchain == activeLinuxOption.fullToolchain }

                SettingGroupItem(
                    title = stringResource(R.string.assistant_page_local_tools_linux_status_title),
                    subtitle = if (linuxStatus.ready) {
                        stringResource(R.string.assistant_page_local_tools_linux_status_ready)
                    } else if (linuxInstallMessage.isNotBlank()) {
                        linuxInstallMessage
                    } else {
                        stringResource(
                            R.string.assistant_page_local_tools_linux_status_missing,
                            linuxStatus.missing.joinToString()
                        )
                    },
                    trailing = {
                        Button(
                            enabled = !installingLinux,
                            onClick = {
                                installingLinux = true
                                linuxInstallMessage = context.getString(R.string.assistant_page_local_tools_linux_installing)
                                scope.launch {
                                    val result = linuxManager.installOrRepair(
                                        fullToolchain = activeLinuxOption.fullToolchain
                                    )
                                    linuxStatus = result.status
                                    linuxInstallMessage = result.message
                                    installingLinux = false
                                }
                            }
                        ) {
                            Text(
                                if (linuxStatus.ready) {
                                    stringResource(R.string.assistant_page_local_tools_linux_repair)
                                } else {
                                    stringResource(R.string.assistant_page_local_tools_linux_install)
                                }
                            )
                        }
                    }
                )

                SettingGroupItem(
                    title = stringResource(R.string.assistant_page_local_tools_linux_profile_title),
                    subtitle = selectedProfile.label,
                    trailing = {
                        Select(
                            options = profileOptions,
                            selectedOption = selectedProfile,
                            onOptionSelected = { option ->
                                val updated = assistant.localTools.map { tool ->
                                    if (tool is LocalToolOption.LinuxEnvironment) {
                                        tool.copy(fullToolchain = option.fullToolchain)
                                    } else {
                                        tool
                                    }
                                }
                                onUpdate(assistant.copy(localTools = updated))
                            },
                            optionToString = { it.label },
                            modifier = Modifier.width(170.dp)
                        )
                    }
                )

                SettingGroupItem(
                    title = stringResource(R.string.assistant_page_local_tools_linux_network_title),
                    subtitle = stringResource(R.string.assistant_page_local_tools_linux_network_desc),
                    trailing = {
                        HapticSwitch(
                            checked = activeLinuxOption.networkAccess,
                            onCheckedChange = { enabled ->
                                val updated = assistant.localTools.map { tool ->
                                    if (tool is LocalToolOption.LinuxEnvironment) {
                                        tool.copy(networkAccess = enabled)
                                    } else {
                                        tool
                                    }
                                }
                                onUpdate(assistant.copy(localTools = updated))
                            }
                        )
                    }
                )
            }

            // JavaScript Engine
            if (!linuxEnabled) {
                SettingGroupItem(
                    title = stringResource(R.string.assistant_page_local_tools_javascript_engine_title),
                    subtitle = stringResource(R.string.assistant_page_local_tools_javascript_engine_desc),
                    trailing = {
                        HapticSwitch(
                            checked = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                            onCheckedChange = { enabled ->
                                val newLocalTools = if (enabled) {
                                    assistant.localTools + LocalToolOption.JavascriptEngine
                                } else {
                                    assistant.localTools - LocalToolOption.JavascriptEngine
                                }
                                onUpdate(assistant.copy(localTools = newLocalTools))
                            }
                        )
                    }
                )
            }
            
            SettingGroupItem(
                title = stringResource(R.string.notification_tools_title),
                subtitle = stringResource(R.string.notification_tools_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.Notifications),
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val newLocalTools = assistant.localTools + LocalToolOption.Notifications
                                onUpdate(assistant.copy(localTools = newLocalTools))
                                requestNotificationAccess()
                            } else {
                                val newLocalTools = assistant.localTools - LocalToolOption.Notifications
                                onUpdate(assistant.copy(localTools = newLocalTools))
                            }
                        }
                    )
                }
            )
            
            // Python Engine
            val pythonOption = assistant.localTools.filterIsInstance<LocalToolOption.PythonEngine>().firstOrNull()
            if (!linuxEnabled) {
                SettingGroupItem(
                    title = stringResource(R.string.assistant_page_local_tools_python_engine_title),
                    subtitle = stringResource(R.string.assistant_page_local_tools_python_engine_desc),
                    trailing = {
                        HapticSwitch(
                            checked = pythonOption != null,
                            onCheckedChange = { enabled ->
                                val newLocalTools = if (enabled) {
                                    assistant.localTools + LocalToolOption.PythonEngine
                                } else {
                                    assistant.localTools.filterNot { it is LocalToolOption.PythonEngine }
                                }
                                onUpdate(assistant.copy(localTools = newLocalTools))
                            }
                        )
                    }
                )
            }

            SettingGroupItem(
                title = stringResource(R.string.assistant_page_local_tools_tts_title),
                subtitle = stringResource(R.string.assistant_page_local_tools_tts_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.Tts),
                        onCheckedChange = { enabled ->
                            val newLocalTools = if (enabled) {
                                assistant.localTools + LocalToolOption.Tts
                            } else {
                                assistant.localTools - LocalToolOption.Tts
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )

            SettingGroupItem(
                title = stringResource(R.string.assistant_page_local_tools_character_questions_title),
                subtitle = stringResource(R.string.assistant_page_local_tools_character_questions_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.AskUser),
                        onCheckedChange = { enabled ->
                            val newLocalTools = if (enabled) {
                                assistant.localTools + LocalToolOption.AskUser
                            } else {
                                assistant.localTools - LocalToolOption.AskUser
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )

            SettingGroupItem(
                title = stringResource(R.string.assistant_page_local_tools_image_generation_title),
                subtitle = stringResource(R.string.assistant_page_local_tools_image_generation_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.ImageGeneration),
                        onCheckedChange = { enabled ->
                            val newLocalTools = if (enabled) {
                                assistant.localTools + LocalToolOption.ImageGeneration
                            } else {
                                assistant.localTools - LocalToolOption.ImageGeneration
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // MCP GROUP (only show if servers configured)
        // ═══════════════════════════════════════════════════════════════════
        if (mcpServerConfigs.isNotEmpty()) {
            var showMcpPicker by remember { mutableStateOf(false) }
            val mcpManager = koinInject<McpManager>()
            val syncingStatus by mcpManager.syncingStatus.collectAsStateWithLifecycle()
            val loading = syncingStatus.values.any { it == McpStatus.Connecting }
            val availableServerCount = mcpServerConfigs.count { it.commonOptions.enable }
            val enabledServerCount = mcpServerConfigs.count {
                it.commonOptions.enable && assistant.mcpServers.contains(it.id)
            }

            SettingsGroup(title = stringResource(R.string.assistant_page_tab_mcp)) {
                SettingGroupItem(
                    title = stringResource(R.string.mcp_picker_title),
                    subtitle = when {
                        loading -> stringResource(R.string.mcp_picker_syncing)
                        enabledServerCount > 0 -> stringResource(
                            R.string.assistant_tools_mcp_enabled_count,
                            enabledServerCount,
                            availableServerCount
                        )
                        else -> stringResource(R.string.assistant_tools_mcp_select)
                    },
                    onClick = { showMcpPicker = true }
                )
            }

            if (showMcpPicker) {
                ModalBottomSheet(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    onDismissRequest = { showMcpPicker = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.7f)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.mcp_picker_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(loading) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                LinearWavyProgressIndicator()
                                Text(
                                    text = stringResource(id = R.string.mcp_picker_syncing),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        McpPicker(
                            assistant = assistant,
                            servers = mcpServerConfigs,
                            onUpdateAssistant = onUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showNotificationAccessDialog && pendingNotificationAccess.specialAccesses.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showNotificationAccessDialog = false },
            title = { Text(stringResource(R.string.notification_access_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.notification_access_desc))
                    PermissionChecker.getFeatureAccessDescriptions(pendingNotificationAccess).forEach { description ->
                        Text("- $description", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationAccessDialog = false
                        val nextAccess = pendingNotificationAccess.specialAccesses.firstOrNull() ?: return@Button
                        notificationSettingsLauncher.launch(
                            PermissionChecker.createSpecialAccessIntent(nextAccess)
                        )
                    }
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotificationAccessDialog = false }
                ) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    }
}
