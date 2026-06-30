package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.ai.McpPicker
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.pages.extensions.workspace.toShellStatusLabel
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.PermissionChecker
import me.rerere.search.SearchServiceOptions
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

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
    val workspaceRepository = koinInject<WorkspaceRepository>()
    val workspaces by workspaceRepository.listFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.firstOrNull { it.id == assistant.workspaceId?.toString() }
    }
    val context = LocalContext.current
    var pendingNotificationAccess by remember {
        mutableStateOf(PermissionChecker.MissingFeatureAccess())
    }
    var showNotificationAccessDialog by remember { mutableStateOf(false) }
    var showWorkspacePicker by remember { mutableStateOf(false) }

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
        // SEARCH GROUP
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

        // LOCAL TOOLS GROUP
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
            
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_local_tools_linux_workspace_title),
                subtitle = selectedWorkspace?.name
                    ?: stringResource(R.string.assistant_page_local_tools_linux_workspace_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = { showWorkspacePicker = true },
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HapticSwitch(
                            checked = selectedWorkspace != null,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val workspace = workspaces.firstOrNull()
                                    if (workspace != null) {
                                        onUpdate(
                                            assistant.copy(
                                                workspaceId = Uuid.parse(workspace.id),
                                                localTools = assistant.localTools.filterNot { it is LocalToolOption.PythonEngine },
                                            )
                                        )
                                    } else {
                                        showWorkspacePicker = true
                                    }
                                } else {
                                    onUpdate(assistant.copy(workspaceId = null))
                                }
                            }
                        )
                    }
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

        // MCP GROUP (only show if servers configured)
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

    if (showWorkspacePicker) {
        WorkspaceBindingDialog(
            workspaces = workspaces,
            selectedWorkspaceId = assistant.workspaceId?.toString(),
            onDismiss = { showWorkspacePicker = false },
            onSelect = { workspaceId ->
                onUpdate(
                    assistant.copy(
                        workspaceId = workspaceId?.let { Uuid.parse(it) },
                        localTools = assistant.localTools.filterNot { it is LocalToolOption.PythonEngine },
                    )
                )
                showWorkspacePicker = false
            },
        )
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

@Composable
private fun WorkspaceBindingDialog(
    workspaces: List<me.rerere.rikkahub.data.db.entity.WorkspaceEntity>,
    selectedWorkspaceId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.workspace_select),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            WorkspaceBindingOption(
                title = stringResource(R.string.workspace_no_binding),
                subtitle = stringResource(R.string.assistant_page_local_tools_linux_workspace_desc),
                icon = Icons.Rounded.Close,
                selected = selectedWorkspaceId == null,
                onClick = { onSelect(null) },
            )

            workspaces.forEach { workspace ->
                WorkspaceBindingOption(
                    title = workspace.name,
                    subtitle = workspace.shellStatus.toShellStatusLabel(),
                    icon = Icons.Rounded.Computer,
                    selected = workspace.id == selectedWorkspaceId,
                    onClick = { onSelect(workspace.id) },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceBindingOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.CardSmall,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.background,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
