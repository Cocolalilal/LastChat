package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons



import androidx.compose.material.icons.rounded.Check

import androidx.compose.material.icons.rounded.KeyboardArrowDown


import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantUISettings





import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem


/**
 * UI Customization subpage - Per-assistant display settings.
 * Each setting has 3 states: Global (null) / On / Off
 */
@Composable
fun AssistantUISubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val settings = LocalSettings.current
    val uiSettings = assistant.uiSettings
    val effectiveDisplay = settings.getEffectiveDisplaySetting(assistant)

    fun updateUI(newSettings: AssistantUISettings) {
        onUpdate(assistant.copy(uiSettings = newSettings))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // New Chat Settings - moved to top as requested
        SettingsGroup(title = stringResource(R.string.setting_new_chat_title)) {
            // Header style dropdown with optional override (null = use global)
            val headerOptions: List<me.rerere.rikkahub.data.datastore.NewChatHeaderStyle?> = listOf(null) + me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.entries
            SettingGroupItem(
                title = stringResource(R.string.setting_new_chat_header),
                subtitle = stringResource(R.string.setting_new_chat_header_desc),
                trailing = {
                    me.rerere.rikkahub.ui.components.ui.Select(
                        options = headerOptions,
                        selectedOption = uiSettings.newChatHeaderStyle,
                        onOptionSelected = { updateUI(uiSettings.copy(newChatHeaderStyle = it)) },
                        optionToString = { style ->
                            when (style) {
                                null -> stringResource(R.string.use_global)
                                me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.NONE -> stringResource(R.string.setting_new_chat_header_none)
                                me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.GREETING -> stringResource(R.string.setting_new_chat_header_greeting)
                                me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.BIG_ICON -> stringResource(R.string.setting_new_chat_header_big_icon)
                            }
                        },
                        modifier = Modifier.width(130.dp)
                    )
                }
            )
            
            // Avatar toggle - only show if header style is not NONE (resolved through per-assistant or global)
            val effectiveHeaderStyle = uiSettings.newChatHeaderStyle ?: settings.displaySetting.newChatHeaderStyle
            if (effectiveHeaderStyle != me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.NONE) {
                // Changed to use TriStateSettingItem for consistent look with other settings
                TriStateSettingItem(
                    title = "Show Avatar in Header",
                    subtitle = "Display character avatar in the new chat header",
                    value = uiSettings.newChatShowAvatar,
                    globalValue = settings.displaySetting.newChatShowAvatar,
                    onValueChange = { updateUI(uiSettings.copy(newChatShowAvatar = it)) }
                )
            }
            
            // Content style dropdown with optional override (null = use global)
            val contentOptions: List<me.rerere.rikkahub.data.datastore.NewChatContentStyle?> = listOf(null) + me.rerere.rikkahub.data.datastore.NewChatContentStyle.entries
            SettingGroupItem(
                title = stringResource(R.string.setting_new_chat_content),
                subtitle = stringResource(R.string.setting_new_chat_content_desc),
                trailing = {
                    me.rerere.rikkahub.ui.components.ui.Select(
                        options = contentOptions,
                        selectedOption = uiSettings.newChatContentStyle,
                        onOptionSelected = { updateUI(uiSettings.copy(newChatContentStyle = it)) },
                        optionToString = { style ->
                            when (style) {
                                null -> stringResource(R.string.use_global)
                                me.rerere.rikkahub.data.datastore.NewChatContentStyle.NONE -> stringResource(R.string.setting_new_chat_content_none)
                                me.rerere.rikkahub.data.datastore.NewChatContentStyle.TEMPLATES -> stringResource(R.string.setting_new_chat_content_templates)
                                me.rerere.rikkahub.data.datastore.NewChatContentStyle.STATS -> stringResource(R.string.setting_new_chat_content_stats)
                                me.rerere.rikkahub.data.datastore.NewChatContentStyle.ACTIONS -> stringResource(R.string.setting_new_chat_content_actions)
                            }
                        },
                        modifier = Modifier.width(130.dp)
                    )
                }
            )
        }

        // Chat Display Settings
        SettingsGroup(title = stringResource(R.string.setting_page_chat_settings)) {
            // Input Style dropdown - added as first item in chat settings
            val inputOptions: List<me.rerere.rikkahub.data.datastore.ChatInputStyle?> = listOf(null) + me.rerere.rikkahub.data.datastore.ChatInputStyle.entries
            SettingGroupItem(
                title = stringResource(R.string.setting_chat_input_style),
                subtitle = stringResource(R.string.setting_chat_input_style_desc),
                trailing = {
                    me.rerere.rikkahub.ui.components.ui.Select(
                        options = inputOptions,
                        selectedOption = uiSettings.chatInputStyle,
                        onOptionSelected = { updateUI(uiSettings.copy(chatInputStyle = it)) },
                        optionToString = { style ->
                            when (style) {
                                null -> stringResource(R.string.use_global)
                                me.rerere.rikkahub.data.datastore.ChatInputStyle.FLOATING -> stringResource(R.string.setting_chat_input_style_floating)
                                me.rerere.rikkahub.data.datastore.ChatInputStyle.MINIMAL -> stringResource(R.string.setting_chat_input_style_minimal)
                            }
                        },
                        modifier = Modifier.width(130.dp)
                    )
                }
            )
            
            TriStateSettingItem(
                title = "Show Character Avatar",
                subtitle = "Show the character's avatar before messages",
                value = uiSettings.showAssistantAvatar,
                globalValue = settings.displaySetting.showModelIcon,
                onValueChange = { updateUI(uiSettings.copy(showAssistantAvatar = it)) }
            )

            TriStateSettingItem(
                title = "Message Bubbles for Characters",
                subtitle = "Show rounded chat bubbles for assistant messages",
                value = uiSettings.showAssistantBubbles,
                globalValue = settings.displaySetting.showAssistantBubbles,
                onValueChange = { updateUI(uiSettings.copy(showAssistantBubbles = it)) }
            )

            TriStateSettingItem(
                title = stringResource(R.string.setting_display_page_show_token_usage_title),
                subtitle = stringResource(R.string.setting_display_page_show_token_usage_desc),
                value = uiSettings.showTokenUsage,
                globalValue = settings.displaySetting.showTokenUsage,
                onValueChange = { updateUI(uiSettings.copy(showTokenUsage = it)) }
            )

            TriStateSettingItem(
                title = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
                subtitle = stringResource(R.string.setting_display_page_auto_collapse_thinking_desc),
                value = uiSettings.autoCloseThinking,
                globalValue = settings.displaySetting.autoCloseThinking,
                onValueChange = { updateUI(uiSettings.copy(autoCloseThinking = it)) }
            )
        }

        // Message Jumper Settings
        SettingsGroup(title = "Message Jumper") {
            TriStateSettingItem(
                title = stringResource(R.string.setting_display_page_show_message_jumper_title),
                subtitle = stringResource(R.string.setting_display_page_show_message_jumper_desc),
                value = uiSettings.showMessageJumper,
                globalValue = settings.displaySetting.showMessageJumper,
                onValueChange = { updateUI(uiSettings.copy(showMessageJumper = it)) }
            )

            TriStateSettingItem(
                title = stringResource(R.string.setting_display_page_message_jumper_position_title),
                subtitle = stringResource(R.string.setting_display_page_message_jumper_position_desc),
                value = uiSettings.messageJumperOnLeft,
                globalValue = settings.displaySetting.messageJumperOnLeft,
                onValueChange = { updateUI(uiSettings.copy(messageJumperOnLeft = it)) }
            )
        }

        // Code Blocks Settings
        SettingsGroup(title = "Code Blocks") {
            TriStateSettingItem(
                title = stringResource(R.string.setting_display_page_code_block_auto_wrap_title),
                subtitle = stringResource(R.string.setting_display_page_code_block_auto_wrap_desc),
                value = uiSettings.codeBlockAutoWrap,
                globalValue = settings.displaySetting.codeBlockAutoWrap,
                onValueChange = { updateUI(uiSettings.copy(codeBlockAutoWrap = it)) }
            )

            TriStateSettingItem(
                title = stringResource(R.string.setting_display_page_code_block_auto_collapse_title),
                subtitle = stringResource(R.string.setting_display_page_code_block_auto_collapse_desc),
                value = uiSettings.codeBlockAutoCollapse,
                globalValue = settings.displaySetting.codeBlockAutoCollapse,
                onValueChange = { updateUI(uiSettings.copy(codeBlockAutoCollapse = it)) }
            )
        }

        // Context Sources Settings
        SettingsGroup(title = "Context Sources") {
            TriStateSettingItem(
                title = "Show Context Stacks",
                subtitle = "Show context sources (modes, memories, lorebooks) in message toolbar",
                value = uiSettings.showContextStacks,
                globalValue = settings.displaySetting.showContextStacks,
                onValueChange = { updateUI(uiSettings.copy(showContextStacks = it)) }
            )
        }

        // Font Size Settings
        SettingsGroup(title = stringResource(R.string.setting_display_page_font_size_title)) {
            FontSizeSettingItem(
                value = uiSettings.fontSizeRatio,
                onValueChange = { updateUI(uiSettings.copy(fontSizeRatio = it)) }
            )
        }
    }
}

/**
 * A tri-state setting item: Global (null) / On / Off
 */
@Composable
private fun TriStateSettingItem(
    title: String,
    subtitle: String,
    value: Boolean?,
    globalValue: Boolean,
    onValueChange: (Boolean?) -> Unit
) {
    SettingGroupItem(
        title = title,
        subtitle = subtitle,
        trailing = {
            Column(
                modifier = Modifier.width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // On/Off row - sets the natural width
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = value == true,
                        onClick = { onValueChange(true) },
                        label = { Text("On") }
                    )
                    FilterChip(
                        selected = value == false,
                        onClick = { onValueChange(false) },
                        label = { Text("Off") }
                    )
                }
                // Global below, fills to match On/Off width
                FilterChip(
                    selected = value == null,
                    onClick = { onValueChange(null) },
                    label = { Text("Global (${if (globalValue) "On" else "Off"})") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

/**
 * Font size setting with Global option and slider
 */
@Composable
private fun FontSizeSettingItem(
    value: Float?,
    onValueChange: (Float?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = value == null,
                onClick = { onValueChange(null) },
                label = { Text("Global") }
            )
            FilterChip(
                selected = value != null,
                onClick = { if (value == null) onValueChange(1.0f) },
                label = { Text("Custom") }
            )
        }

        if (value != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = value,
                    onValueChange = { onValueChange(it) },
                    valueRange = 0.5f..2f,
                    steps = 11,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(value * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
