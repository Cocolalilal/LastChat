package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingUICustomizationPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(
            settings.copy(
                displaySetting = setting
            )
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_ui_customization_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // New Chat Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_new_chat_title)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_new_chat_header),
                        subtitle = stringResource(R.string.setting_new_chat_header_desc),
                        trailing = {
                            me.rerere.rikkahub.ui.components.ui.Select(
                                options = me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.entries.toList(),
                                selectedOption = displaySetting.newChatHeaderStyle,
                                onOptionSelected = { updateDisplaySetting(displaySetting.copy(newChatHeaderStyle = it)) },
                                optionToString = { style ->
                                    when (style) {
                                        me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.NONE -> stringResource(R.string.setting_new_chat_header_none)
                                        me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.GREETING -> stringResource(R.string.setting_new_chat_header_greeting)
                                        me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.BIG_ICON -> stringResource(R.string.setting_new_chat_header_big_icon)
                                    }
                                },
                                modifier = Modifier.wrapContentWidth()
                            )
                        }
                    )
                    
                    // Only show avatar toggle if header style is not NONE
                    if (displaySetting.newChatHeaderStyle != me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.NONE) {
                        SettingGroupItem(
                            title = stringResource(R.string.setting_ui_show_avatar_header_title),
                            subtitle = stringResource(R.string.setting_ui_show_avatar_header_global_desc),
                            trailing = {
                                HapticSwitch(
                                    checked = displaySetting.newChatShowAvatar,
                                    onCheckedChange = {
                                        updateDisplaySetting(displaySetting.copy(newChatShowAvatar = it))
                                    }
                                )
                            }
                        )
                    }
                    
                    SettingGroupItem(
                        title = stringResource(R.string.setting_new_chat_content),
                        subtitle = stringResource(R.string.setting_new_chat_content_desc),
                        trailing = {
                            me.rerere.rikkahub.ui.components.ui.Select(
                                options = me.rerere.rikkahub.data.datastore.NewChatContentStyle.entries.toList(),
                                selectedOption = displaySetting.newChatContentStyle,
                                onOptionSelected = { updateDisplaySetting(displaySetting.copy(newChatContentStyle = it)) },
                                optionToString = { style ->
                                    when (style) {
                                        me.rerere.rikkahub.data.datastore.NewChatContentStyle.NONE -> stringResource(R.string.setting_new_chat_content_none)
                                        me.rerere.rikkahub.data.datastore.NewChatContentStyle.TEMPLATES -> stringResource(R.string.setting_new_chat_content_templates)
                                        me.rerere.rikkahub.data.datastore.NewChatContentStyle.ACTIONS -> stringResource(R.string.setting_new_chat_content_actions)
                                    }
                                },
                                modifier = Modifier.wrapContentWidth()
                            )
                        }
                    )
                }
            }

            // Chat Display Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_chat_settings)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_enable_blur_effect_title),
                        subtitle = stringResource(R.string.setting_display_page_enable_blur_effect_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.enableBlurEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableBlurEffect = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_ui_move_toolbar_bottom_title),
                        subtitle = stringResource(R.string.setting_ui_move_toolbar_bottom_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.chatToolbarAtBottom,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(chatToolbarAtBottom = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_ui_show_character_avatar_title),
                        subtitle = stringResource(R.string.setting_ui_show_character_avatar_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showModelIcon,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showModelIcon = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_show_assistant_bubbles_title),
                        subtitle = stringResource(R.string.setting_display_page_show_assistant_bubbles_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showAssistantBubbles,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showAssistantBubbles = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_show_token_usage_title),
                        subtitle = stringResource(R.string.setting_display_page_show_token_usage_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showTokenUsage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showTokenUsage = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
                        subtitle = stringResource(R.string.setting_display_page_auto_collapse_thinking_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.autoCloseThinking,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(autoCloseThinking = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_reasoning_preview_title),
                        subtitle = stringResource(R.string.setting_display_page_reasoning_preview_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.reasoningPreviewEnabled,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(reasoningPreviewEnabled = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_ui_show_context_stacks_title),
                        subtitle = stringResource(R.string.setting_ui_show_context_stacks_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showContextStacks,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showContextStacks = it))
                                }
                            )
                        }
                    )
                }
            }
            
            // Message Jumper Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_ui_message_jumper_group)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_show_message_jumper_title),
                        subtitle = stringResource(R.string.setting_display_page_show_message_jumper_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showMessageJumper,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showMessageJumper = it))
                                }
                            )
                        }
                    )
                    if (displaySetting.showMessageJumper) {
                        SettingGroupItem(
                            title = stringResource(R.string.setting_display_page_message_jumper_position_title),
                            subtitle = stringResource(R.string.setting_display_page_message_jumper_position_desc),
                            trailing = {
                                HapticSwitch(
                                    checked = displaySetting.messageJumperOnLeft,
                                    onCheckedChange = {
                                        updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it))
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Haptics Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_ui_haptics_group)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title),
                        subtitle = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.enableMessageGenerationHapticEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_ui_haptics_ui_title),
                        subtitle = stringResource(R.string.setting_ui_haptics_ui_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.enableUIHaptics,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableUIHaptics = it))
                                }
                            )
                        }
                    )
                }
            }

            // Code Blocks Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_ui_code_blocks_group)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_code_block_auto_wrap_title),
                        subtitle = stringResource(R.string.setting_display_page_code_block_auto_wrap_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.codeBlockAutoWrap,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_code_block_auto_collapse_title),
                        subtitle = stringResource(R.string.setting_display_page_code_block_auto_collapse_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.codeBlockAutoCollapse,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it))
                                }
                            )
                        }
                    )
                }
            }

            // Font Size Slider
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_display_page_font_size_title)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Slider(
                            value = displaySetting.fontSizeRatio,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(fontSizeRatio = it))
                            },
                            valueRange = 0.5f..2f,
                            steps = 11,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(displaySetting.fontSizeRatio * 100).toInt()}%",
                        )
                    }
                    MarkdownBlock(
                        content = stringResource(R.string.setting_display_page_font_size_preview),
                        modifier = Modifier.padding(8.dp),
                        style = LocalTextStyle.current.copy(
                            fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                            lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                        )
                    )
                }
            }
        }
    }
}
