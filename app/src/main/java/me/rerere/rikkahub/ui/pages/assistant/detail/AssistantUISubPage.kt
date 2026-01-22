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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantUISettings
import me.rerere.rikkahub.ui.components.message.ChatMessageAssistantAvatar
import me.rerere.rikkahub.ui.components.message.ChatMessageReasoning
import me.rerere.rikkahub.ui.components.message.ChatMessageUserAvatar
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import kotlin.time.Clock

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
                title = stringResource(R.string.setting_display_page_show_user_avatar_title),
                subtitle = stringResource(R.string.setting_display_page_show_user_avatar_desc),
                value = uiSettings.showUserAvatar,
                globalValue = settings.displaySetting.showUserAvatar,
                onValueChange = { updateUI(uiSettings.copy(showUserAvatar = it)) }
            )

            TriStateSettingItem(
                title = "Show Character Avatar",
                subtitle = "Show the character's avatar before messages",
                value = uiSettings.showAssistantAvatar,
                globalValue = settings.displaySetting.showModelIcon,
                onValueChange = { updateUI(uiSettings.copy(showAssistantAvatar = it)) }
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
 * Live preview of chat with current settings applied - uses actual chat components
 */
@Composable
private fun ChatPreview(
    assistant: Assistant,
    effectiveDisplay: me.rerere.rikkahub.data.datastore.DisplaySetting,
    modifier: Modifier = Modifier
) {
    val settings = LocalSettings.current
    val userNickname = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) }
    val userAvatar = settings.displaySetting.userAvatar

    // Create sample UIMessages for preview
    val userMessage = remember {
        me.rerere.ai.ui.UIMessage(
            role = me.rerere.ai.core.MessageRole.USER,
            parts = listOf(me.rerere.ai.ui.UIMessagePart.Text("Hello, how are you?"))
        )
    }
    val assistantMessage = remember {
        me.rerere.ai.ui.UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(me.rerere.ai.ui.UIMessagePart.Text("I'm doing great! How can I help you today?")),
            usage = me.rerere.ai.core.TokenUsage(promptTokens = 24, completionTokens = 42)
        )
    }

    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * effectiveDisplay.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * effectiveDisplay.fontSizeRatio
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // User message
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // User avatar using actual component
            ChatMessageUserAvatar(
                message = userMessage,
                previousRole = null,
                avatar = userAvatar,
                nickname = userNickname,
                modifier = Modifier.fillMaxWidth()
            )

            // User message bubble (Card style matching actual implementation)
            androidx.compose.material3.Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    androidx.compose.material3.ProvideTextStyle(textStyle) {
                        MarkdownBlock(content = "Hello, how are you?")
                    }
                }
            }
        }

        // Assistant message
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Assistant avatar using actual component
            ChatMessageAssistantAvatar(
                message = assistantMessage,
                previousRole = me.rerere.ai.core.MessageRole.USER,
                loading = false,
                model = null,
                assistant = assistant,
                modifier = Modifier.fillMaxWidth()
            )

            // Reasoning example - uses actual ChatMessageReasoning component
            val now = Clock.System.now()
            val sampleReasoning = remember(now) {
                me.rerere.ai.ui.UIMessagePart.Reasoning(
                    reasoning = "Let me think about this... The user is greeting me, so I should respond warmly.",
                    createdAt = now,
                    finishedAt = now
                )
            }
            ChatMessageReasoning(
                reasoning = sampleReasoning,
                model = null,
                assistant = assistant
            )

            // Assistant message (plain markdown matching actual implementation)
            androidx.compose.material3.ProvideTextStyle(textStyle) {
                MarkdownBlock(content = "I'm doing great! How can I help you today?")
            }

            // Token usage display
            if (effectiveDisplay.showTokenUsage) {
                TokenUsagePreview()
            }

            // Toolbar preview
            ToolbarPreview()
        }
    }
}

/**
 * Static toolbar preview showing action icons
 */
@Composable
private fun ToolbarPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = null,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .padding(8.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .padding(8.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .padding(8.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = null,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .padding(8.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Simple token usage preview row
 */
@Composable
private fun TokenUsagePreview() {
    val grayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = grayColor
            )
            Text(
                text = "24 tokens",
                style = MaterialTheme.typography.labelSmall,
                color = grayColor
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = grayColor
            )
            Text(
                text = "42 tokens",
                style = MaterialTheme.typography.labelSmall,
                color = grayColor
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
