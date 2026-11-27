package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.isTextSelectionFollowingActiveAssistant
import me.rerere.rikkahub.data.datastore.resolveTextSelectionAssistant
import me.rerere.rikkahub.data.model.DEFAULT_TEXT_SELECTION_ACTIONS
import me.rerere.rikkahub.data.model.TextSelectionAction
import me.rerere.rikkahub.data.model.TextSelectionConfig
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.textselection.QUICK_ASK_ICON_OPTIONS
import me.rerere.rikkahub.ui.components.textselection.QuickAskInnerShape
import me.rerere.rikkahub.ui.components.textselection.QuickAskOuterShape
import me.rerere.rikkahub.ui.components.textselection.quickAskGroupedButtonShape
import me.rerere.rikkahub.ui.components.textselection.quickAskIconForName
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.motion.rememberControlPressValue
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private val COMMON_LANGUAGES = listOf(
    "English", "Spanish", "French", "German", "Italian", "Portuguese",
    "Russian", "Japanese", "Chinese", "Korean", "Arabic", "Hindi"
)

@Composable
fun SettingAndroidIntegrationPage(
    settingsStore: SettingsStore = koinInject()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()

    val config = settings.textSelectionConfig
    var editingAction by remember { mutableStateOf<TextSelectionAction?>(null) }
    var isNewAction by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showWaveOriginPicker by remember { mutableStateOf(false) }

    fun updateTextSelectionConfig(transform: (TextSelectionConfig) -> TextSelectionConfig) {
        scope.launch {
            settingsStore.update { current ->
                current.copy(textSelectionConfig = transform(current.textSelectionConfig))
            }
        }
    }

    if (showWaveOriginPicker) {
        me.rerere.rikkahub.ui.pages.setting.components.WaveOriginPickerDialog(
            initialX = settings.assistantOverlayConfig.waveOriginX,
            initialY = settings.assistantOverlayConfig.waveOriginY,
            onDismiss = { showWaveOriginPicker = false },
            onSave = { x, y ->
                showWaveOriginPicker = false
                scope.launch {
                    settingsStore.update {
                        it.copy(
                            assistantOverlayConfig = it.assistantOverlayConfig.copy(
                                waveOriginX = x,
                                waveOriginY = y,
                            )
                        )
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_android_integration),
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

                // Try It Section - Demo with selectable text
                item {
                    SettingsGroup(title = stringResource(R.string.text_selection_try_it)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.text_selection_setup_instructions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = stringResource(R.string.text_selection_demo_text),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.text_selection_demo_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Digital Assistant Section
                item {
                    val overlayConfig = settings.assistantOverlayConfig
                    val isDefaultAssistant = remember(settings) { isLastChatDefaultAssistant(context) }
                    SettingsGroup(title = stringResource(R.string.assistant_overlay_section)) {
                        SettingGroupItem(
                            title = stringResource(R.string.assistant_overlay_set_default),
                            subtitle = if (isDefaultAssistant) {
                                stringResource(R.string.assistant_overlay_is_default)
                            } else {
                                stringResource(R.string.assistant_overlay_set_default_desc)
                            },
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure {
                                    runCatching {
                                        context.startActivity(
                                            Intent(AndroidSettings.ACTION_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                        )

                        val overlayDefaultAssistant = settings.assistants.find { it.name == "Generical" }
                            ?: settings.assistants.firstOrNull()
                        SettingGroupItem(
                            title = stringResource(R.string.assistant_overlay_assistant),
                            subtitle = settings.assistants.find { it.id == overlayConfig.assistantId }?.name
                                ?: overlayDefaultAssistant?.name ?: "None",
                            trailing = {
                                Select(
                                    options = settings.assistants.map { it.id },
                                    selectedOption = overlayConfig.assistantId ?: overlayDefaultAssistant?.id,
                                    onOptionSelected = { selected ->
                                        scope.launch {
                                            settingsStore.update {
                                                it.copy(assistantOverlayConfig = it.assistantOverlayConfig.copy(assistantId = selected))
                                            }
                                        }
                                    },
                                    optionToString = { id ->
                                        settings.assistants.find { it.id == id }?.name ?: "Unknown"
                                    },
                                )
                            }
                        )

                        SettingGroupItem(
                            title = stringResource(R.string.assistant_overlay_auto_stt),
                            subtitle = stringResource(R.string.assistant_overlay_auto_stt_desc),
                            trailing = {
                                HapticSwitch(
                                    checked = overlayConfig.autoStartStt,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            settingsStore.update {
                                                it.copy(assistantOverlayConfig = it.assistantOverlayConfig.copy(autoStartStt = checked))
                                            }
                                        }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = stringResource(R.string.assistant_overlay_auto_send),
                            subtitle = stringResource(R.string.assistant_overlay_auto_send_desc),
                            trailing = {
                                HapticSwitch(
                                    checked = overlayConfig.autoSendOnSttFinish,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            settingsStore.update {
                                                it.copy(assistantOverlayConfig = it.assistantOverlayConfig.copy(autoSendOnSttFinish = checked))
                                            }
                                        }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = stringResource(R.string.assistant_overlay_auto_read),
                            subtitle = stringResource(R.string.assistant_overlay_auto_read_desc),
                            trailing = {
                                HapticSwitch(
                                    checked = overlayConfig.autoReadReply,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            settingsStore.update {
                                                it.copy(assistantOverlayConfig = it.assistantOverlayConfig.copy(autoReadReply = checked))
                                            }
                                        }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = stringResource(R.string.assistant_overlay_attach_screenshot),
                            subtitle = stringResource(R.string.assistant_overlay_attach_screenshot_desc),
                            trailing = {
                                HapticSwitch(
                                    checked = overlayConfig.attachScreenshot,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            settingsStore.update {
                                                it.copy(assistantOverlayConfig = it.assistantOverlayConfig.copy(attachScreenshot = checked))
                                            }
                                        }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = "Wave origin",
                            subtitle = "Choose where the glow wave comes from",
                            onClick = { showWaveOriginPicker = true },
                        )
                    }
                }

                // Ask LastChat Assistant & Language Settings
                item {
                    val resolvedAssistant = settings.resolveTextSelectionAssistant()
                    val isFollowingActive = settings.isTextSelectionFollowingActiveAssistant()
                    val selectedAssistantOption = config.assistantId
                        ?.takeIf { id -> settings.getAssistantById(id) != null }
                    val assistantOptions: List<Uuid?> = listOf(null) + settings.assistants.map { it.id }
                    val activeAssistantName = settings.getCurrentAssistant().name

                    SettingsGroup(title = stringResource(R.string.text_selection_menu_label)) {
                        SettingGroupItem(
                            title = stringResource(R.string.text_selection_assistant),
                            subtitle = if (isFollowingActive) {
                                "Follow active chat assistant (${resolvedAssistant.name})"
                            } else {
                                resolvedAssistant.name
                            },
                            trailing = {
                                Select(
                                    options = assistantOptions,
                                    selectedOption = selectedAssistantOption,
                                    onOptionSelected = { selected ->
                                        updateTextSelectionConfig { it.copy(assistantId = selected) }
                                    },
                                    optionToString = { id ->
                                        if (id == null) {
                                            "Active ($activeAssistantName)"
                                        } else {
                                            settings.getAssistantById(id)?.name ?: "Unknown"
                                        }
                                    },
                                )
                            }
                        )

                        SettingGroupItem(
                            title = stringResource(R.string.text_selection_translate_language),
                            subtitle = config.translateLanguage,
                            trailing = {
                                Select(
                                    options = COMMON_LANGUAGES,
                                    selectedOption = config.translateLanguage,
                                    onOptionSelected = { lang ->
                                        updateTextSelectionConfig { it.copy(translateLanguage = lang) }
                                    },
                                    optionToString = { it },
                                )
                            }
                        )

                        SettingGroupItem(
                            title = stringResource(R.string.reset),
                            subtitle = stringResource(R.string.setting_android_integration_reset_actions),
                            onClick = { showResetDialog = true }
                        )
                    }
                }

                // Context & Isolation Settings
                item {
                    SettingsGroup(title = "Context & Isolation") {
                        SettingGroupItem(
                            title = "Include assistant system prompt",
                            subtitle = "Prepend the selected assistant's persona/system instructions to non-translate actions",
                            trailing = {
                                HapticSwitch(
                                    checked = config.inheritAssistantSystemPrompt,
                                    onCheckedChange = { checked ->
                                        updateTextSelectionConfig { it.copy(inheritAssistantSystemPrompt = checked) }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = "Read memories & memory summary",
                            subtitle = "Inject stored memories and memory summary before answering (adds latency)",
                            trailing = {
                                HapticSwitch(
                                    checked = config.enableMemoryRead,
                                    onCheckedChange = { checked ->
                                        updateTextSelectionConfig { it.copy(enableMemoryRead = checked) }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = "Allow tool execution",
                            subtitle = "Allow Search, MCP, and Workspace tool calls during Quick Ask",
                            trailing = {
                                HapticSwitch(
                                    checked = config.enableTools,
                                    onCheckedChange = { checked ->
                                        updateTextSelectionConfig {
                                            it.copy(
                                                enableTools = checked,
                                                enableMemoryWrite = if (checked) it.enableMemoryWrite else false,
                                            )
                                        }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = "Allow saving new memories",
                            subtitle = if (config.enableTools) {
                                "Let Quick Ask create, edit, or delete assistant memories"
                            } else {
                                "Enable tool execution above to allow memory writes"
                            },
                            trailing = {
                                HapticSwitch(
                                    checked = config.enableMemoryWrite && config.enableTools,
                                    enabled = config.enableTools,
                                    onCheckedChange = { checked ->
                                        updateTextSelectionConfig { it.copy(enableMemoryWrite = checked) }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = "Include global prompt injections",
                            subtitle = "Include Custom Instructions, Skills, Lorebooks, Artifacts, TMDB, and Time Awareness",
                            trailing = {
                                HapticSwitch(
                                    checked = config.enableExtraPromptInjections,
                                    onCheckedChange = { checked ->
                                        updateTextSelectionConfig { it.copy(enableExtraPromptInjections = checked) }
                                    }
                                )
                            }
                        )

                        SettingGroupItem(
                            title = "Expand thinking by default",
                            subtitle = "Automatically open the reasoning trace card in the floating sheet",
                            trailing = {
                                HapticSwitch(
                                    checked = config.showThinkingByDefault,
                                    onCheckedChange = { checked ->
                                        updateTextSelectionConfig { it.copy(showThinkingByDefault = checked) }
                                    }
                                )
                            }
                        )
                    }
                }

                // Quick Actions Section (Live Preview + Full CRUD + Reorder)
                item {
                    SettingsGroup(title = stringResource(R.string.text_selection_actions)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PreviewCard(
                                config = config,
                                onActionClick = { action ->
                                    isNewAction = false
                                    editingAction = action
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${config.actions.count { it.enabled }}/${config.actions.size} enabled • Swipe left to delete",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        isNewAction = true
                                        editingAction = TextSelectionAction(
                                            name = "",
                                            icon = "AutoAwesome",
                                            prompt = "",
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.text_selection_add_action))
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        config.actions.forEachIndexed { index, action ->
                            val position = when {
                                config.actions.size == 1 -> ItemPosition.ONLY
                                index == 0 -> ItemPosition.FIRST
                                index == config.actions.lastIndex -> ItemPosition.LAST
                                else -> ItemPosition.MIDDLE
                            }
                            PhysicsSwipeToDelete(
                                position = position,
                                deleteEnabled = config.actions.size > 1,
                                onDelete = {
                                    val previousActions = config.actions
                                    val updated = config.actions.filterNot { it.id == action.id }
                                    updateTextSelectionConfig { it.copy(actions = updated) }
                                    toaster.show(
                                        message = "Removed \"${action.name}\"",
                                        action = ToastAction(
                                            label = context.getString(R.string.undo),
                                            onClick = {
                                                updateTextSelectionConfig { it.copy(actions = previousActions) }
                                            }
                                        )
                                    )
                                }
                            ) { shape ->
                                ActionCard(
                                    action = action,
                                    settings = settings,
                                    shape = shape,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < config.actions.lastIndex,
                                    onMoveUp = {
                                        if (index > 0) {
                                            haptics.perform(HapticPattern.Selection)
                                            val mutable = config.actions.toMutableList()
                                            val moved = mutable.removeAt(index)
                                            mutable.add(index - 1, moved)
                                            updateTextSelectionConfig { it.copy(actions = mutable) }
                                        }
                                    },
                                    onMoveDown = {
                                        if (index < config.actions.lastIndex) {
                                            haptics.perform(HapticPattern.Selection)
                                            val mutable = config.actions.toMutableList()
                                            val moved = mutable.removeAt(index)
                                            mutable.add(index + 1, moved)
                                            updateTextSelectionConfig { it.copy(actions = mutable) }
                                        }
                                    },
                                    onToggleEnabled = { enabled ->
                                        val updated = config.actions.map { current ->
                                            if (current.id == action.id) current.copy(enabled = enabled) else current
                                        }
                                        updateTextSelectionConfig { it.copy(actions = updated) }
                                    },
                                    onModelSelected = { modelId ->
                                        val updated = config.actions.map { current ->
                                            if (current.id == action.id) current.copy(modelId = modelId) else current
                                        }
                                        updateTextSelectionConfig { it.copy(actions = updated) }
                                    },
                                    onClick = {
                                        isNewAction = false
                                        editingAction = action
                                    }
                                )
                            }
                            if (index < config.actions.lastIndex) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    editingAction?.let { action ->
        EditActionDialog(
            action = action,
            settings = settings,
            isNew = isNewAction,
            onDismiss = { editingAction = null },
            onSave = { updated ->
                if (isNewAction) {
                    updateTextSelectionConfig { it.copy(actions = it.actions + updated) }
                } else {
                    updateTextSelectionConfig { current ->
                        current.copy(
                            actions = current.actions.map { existing ->
                                if (existing.id == updated.id) updated else existing
                            }
                        )
                    }
                }
                editingAction = null
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset)) },
            text = { Text(stringResource(R.string.setting_android_integration_reset_actions_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        val oldActions = config.actions
                        updateTextSelectionConfig { it.copy(actions = DEFAULT_TEXT_SELECTION_ACTIONS) }
                        toaster.show(
                            message = context.getString(R.string.setting_android_integration_reset_actions_success),
                            action = ToastAction(
                                label = context.getString(R.string.undo),
                                onClick = {
                                    updateTextSelectionConfig { it.copy(actions = oldActions) }
                                }
                            )
                        )
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PreviewCard(
    config: TextSelectionConfig,
    onActionClick: (TextSelectionAction) -> Unit
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val enabledActions = config.actions.filter { it.enabled }

    CompositionLocalProvider(
        LocalAbsoluteTonalElevation provides if (amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = QuickAskOuterShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = if (amoledMode && isDarkMode) 0.dp else 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_lastchat_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.text_selection_menu_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.text_selection_preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = QuickAskInnerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "Selected text preview…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (enabledActions.isEmpty()) {
                    Text(
                        text = "No enabled presets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val rows = enabledActions.chunked(2)
                        rows.forEachIndexed { rowIndex, rowActions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rowActions.forEachIndexed { colIndex, action ->
                                    val isLastOdd = rowIndex == rows.lastIndex && rowActions.size == 1
                                    PreviewActionButton(
                                        modifier = Modifier.weight(if (isLastOdd) 2f else 1f),
                                        action = action,
                                        shape = quickAskGroupedButtonShape(rowIndex, colIndex, rows.size, rowActions.size),
                                        isBlack = amoledMode && isDarkMode,
                                        onClick = { onActionClick(action) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewActionButton(
    modifier: Modifier = Modifier,
    action: TextSelectionAction,
    shape: RoundedCornerShape,
    isBlack: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by rememberControlPressValue(isPressed, 0.95f, "preview_button_scale")

    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = if (isBlack) 0.dp else 4.dp,
        interactionSource = interactionSource,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = quickAskIconForName(action.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = action.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCard(
    action: TextSelectionAction,
    settings: Settings,
    shape: androidx.compose.ui.graphics.Shape,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onModelSelected: (Uuid?) -> Unit,
    onClick: () -> Unit
) {
    val configuredModel = action.modelId?.let(settings::findModelById)
    val containerColor = if (LocalDarkMode.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val promptPreview = action.prompt
        .trim()
        .take(84)
        .replace("\n", " ")
        .let { preview ->
            if (action.prompt.trim().length > 84) "$preview…" else preview
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = shape
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = quickAskIconForName(action.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = action.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = promptPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (action.saveToStudio) {
                            ActionBadgePill("Save to Studio")
                        }
                        if (action.isCustomPrompt) {
                            ActionBadgePill("Input")
                        }
                        if (!action.allowContinueInChat) {
                            ActionBadgePill("No chat handoff")
                        }
                        action.enableMemoryReadOverride?.let { mem ->
                            ActionBadgePill("Memory: ${if (mem) "On" else "Off"}")
                        }
                        action.enableToolsOverride?.let { tools ->
                            ActionBadgePill("Tools: ${if (tools) "On" else "Off"}")
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = canMoveUp,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = canMoveDown,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HapticSwitch(
                        checked = action.enabled,
                        onCheckedChange = onToggleEnabled
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Model",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = configuredModel?.displayName ?: "Use assistant default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ModelSelector(
                    modelId = configuredModel?.id,
                    providers = settings.providers,
                    type = ModelType.CHAT,
                    allowClear = true,
                    onSelect = { selectedModel ->
                        onModelSelected(settings.findModelById(selectedModel.id)?.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionBadgePill(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditActionDialog(
    action: TextSelectionAction,
    settings: Settings,
    isNew: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (TextSelectionAction) -> Unit
) {
    var name by remember(action.id) { mutableStateOf(action.name) }
    var icon by remember(action.id) { mutableStateOf(action.icon) }
    var prompt by remember(action.id) { mutableStateOf(action.prompt) }
    var modelId by remember(action.id) { mutableStateOf(action.modelId) }
    var isCustomPrompt by remember(action.id) { mutableStateOf(action.isCustomPrompt) }
    var allowContinueInChat by remember(action.id) { mutableStateOf(action.allowContinueInChat) }
    var memoryOverride by remember(action.id) { mutableStateOf(action.enableMemoryReadOverride) }
    var toolsOverride by remember(action.id) { mutableStateOf(action.enableToolsOverride) }
    var saveToStudio by remember(action.id) { mutableStateOf(action.saveToStudio) }
    var autoSummarizeOnSave by remember(action.id) { mutableStateOf(action.autoSummarizeOnSave) }
    val haptics = rememberPremiumHaptics()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) stringResource(R.string.text_selection_add_action)
                else stringResource(R.string.text_selection_edit_action)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.text_selection_action_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Icon",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_ASK_ICON_OPTIONS.forEach { option ->
                            val selected = icon.equals(option.name, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        haptics.perform(HapticPattern.Selection)
                                        icon = option.name
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.name,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.text_selection_action_prompt)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp),
                    minLines = 4,
                    shape = AppShapes.InputField,
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Insert placeholder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("{{language}}", "{{custom_prompt}}").forEach { placeholder ->
                            SuggestionChip(
                                onClick = {
                                    haptics.perform(HapticPattern.Selection)
                                    prompt = if (prompt.isBlank() || prompt.endsWith(" ") || prompt.endsWith("\n")) {
                                        "$prompt$placeholder"
                                    } else {
                                        "$prompt $placeholder"
                                    }
                                },
                                label = { Text(placeholder) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ask for question input",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Prompt the user before running",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HapticSwitch(
                        checked = isCustomPrompt,
                        onCheckedChange = { isCustomPrompt = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Continue in Chat",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Show button to open reply as a full chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HapticSwitch(
                        checked = allowContinueInChat,
                        onCheckedChange = { allowContinueInChat = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save to Studio Hub",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Save selected text or shared images directly to Studio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HapticSwitch(
                        checked = saveToStudio,
                        onCheckedChange = { saveToStudio = it }
                    )
                }

                if (saveToStudio) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-summarize with AI",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Enrich title & summary asynchronously after saving",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HapticSwitch(
                            checked = autoSummarizeOnSave,
                            onCheckedChange = { autoSummarizeOnSave = it }
                        )
                    }
                }

                OverrideTriStateRow(
                    title = "Memory read override",
                    value = memoryOverride,
                    onChange = { memoryOverride = it }
                )

                OverrideTriStateRow(
                    title = "Tool execution override",
                    value = toolsOverride,
                    onChange = { toolsOverride = it }
                )

                val configuredModel = modelId?.let(settings::findModelById)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Model override",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = configuredModel?.displayName ?: "Use assistant default",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ModelSelector(
                        modelId = configuredModel?.id,
                        providers = settings.providers,
                        type = ModelType.CHAT,
                        allowClear = true,
                        onSelect = { selected ->
                            modelId = settings.findModelById(selected.id)?.id
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        action.copy(
                            name = name.trim(),
                            icon = icon,
                            prompt = prompt,
                            modelId = modelId,
                            isCustomPrompt = isCustomPrompt,
                            allowContinueInChat = allowContinueInChat,
                            enableMemoryReadOverride = memoryOverride,
                            enableToolsOverride = toolsOverride,
                            saveToStudio = saveToStudio,
                            autoSummarizeOnSave = saveToStudio && autoSummarizeOnSave,
                        )
                    )
                },
                enabled = name.isNotBlank() && prompt.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverrideTriStateRow(
    title: String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                null to "Inherit",
                true to "On",
                false to "Off",
            ).forEach { (option, label) ->
                FilterChip(
                    selected = value == option,
                    onClick = { onChange(option) },
                    label = { Text(label) }
                )
            }
        }
    }
}

private fun isLastChatDefaultAssistant(context: Context): Boolean {
    return runCatching {
        val value = AndroidSettings.Secure.getString(
            context.contentResolver,
            "voice_interaction_service",
        )
        !value.isNullOrBlank() && value.contains(context.packageName)
    }.getOrDefault(false)
}
