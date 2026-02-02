package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.ui.components.ai.McpPickerButton
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.search.SearchServiceOptions

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

        // ═══════════════════════════════════════════════════════════════════
        // LOCAL TOOLS GROUP
        // ═══════════════════════════════════════════════════════════════════
        val deviceControlPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            val newLocalTools = assistant.localTools + LocalToolOption.DeviceControl
            onUpdate(assistant.copy(localTools = newLocalTools))
        }
        
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
            
            // Device Control
            SettingGroupItem(
                title = "Device Control",
                subtitle = "Notifications, apps, alarms, reminders",
                trailing = {
                    HapticSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.DeviceControl),
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val permissions = mutableListOf<String>()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissions.add(Manifest.permission.CAMERA)
                                
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
        }

        // ═══════════════════════════════════════════════════════════════════
        // MCP GROUP (only show if servers configured)
        // ═══════════════════════════════════════════════════════════════════
        if (mcpServerConfigs.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.assistant_page_tab_mcp)) {
            SettingGroupItem(
                    title = "MCP Servers",
                    subtitle = "Enable external tool servers",
                    trailing = {
                        McpPickerButton(
                            assistant = assistant,
                            servers = mcpServerConfigs,
                            mcpManager = org.koin.compose.koinInject(),
                            onUpdateAssistant = onUpdate
                        )
                    }
                )
            }
        }
    }
}
