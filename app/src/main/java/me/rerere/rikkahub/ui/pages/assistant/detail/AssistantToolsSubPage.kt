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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.ui.components.ai.McpPicker
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.utils.PermissionChecker
import me.rerere.search.SearchServiceOptions
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
        // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
        // SEARCH GROUP
        // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
        SettingsGroup(title = "Search") {
            // Build options list for Select
            val currentSearchMode = assistant.searchMode
            
            // Create sealed class options for the selector
            data class SearchOption(val mode: AssistantSearchMode, val displayName: String)
            
            val searchOptions = buildList {
                add(SearchOption(AssistantSearchMode.Off, "Off"))
                settings.searchServices.forEachIndexed { index, service ->
                    val name = SearchServiceOptions.TYPES[service::class] ?: "Provider ${index + 1}"
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
                title = "Search Provider",
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
                title = "Prefer Built-in Search",
                subtitle = "Use model's native search when available",
                trailing = {
                    HapticSwitch(
                        checked = assistant.preferBuiltInSearch,
                        onCheckedChange = { onUpdate(assistant.copy(preferBuiltInSearch = it)) }
                    )
                }
            )
        }

        // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
        // LOCAL TOOLS GROUP
        // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
        SettingsGroup(title = stringResource(R.string.assistant_page_tab_local_tools)) {
            // JavaScript Engine
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
            
            // Notifications
            SettingGroupItem(
                title = "Notifications",
                subtitle = "Notifications, notification reading, scheduled follow-ups",
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
                title = stringResource(R.string.assistant_page_local_tools_ask_user_title),
                subtitle = stringResource(R.string.assistant_page_local_tools_ask_user_desc),
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
        }

        // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
        // MCP GROUP (only show if servers configured)
        // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
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
                        enabledServerCount > 0 -> "$enabledServerCount enabled of $availableServerCount"
                        else -> "Select external tool servers"
                    },
                    onClick = { showMcpPicker = true }
                )
            }

            if (showMcpPicker) {
                ModalBottomSheet(
                    containerColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor(),
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
            title = { Text("Notification Access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enable the remaining access below so notification tools and scheduled follow-ups work reliably:")
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
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotificationAccessDialog = false }
                ) {
                    Text("Not now")
                }
            }
        )
    }
}

