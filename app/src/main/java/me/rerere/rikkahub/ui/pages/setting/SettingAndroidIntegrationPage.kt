package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.DEFAULT_TEXT_SELECTION_ACTIONS
import me.rerere.rikkahub.data.model.TextSelectionAction
import me.rerere.rikkahub.data.model.TextSelectionConfig
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
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
    
    val config = settings.textSelectionConfig
    var editingAction by remember { mutableStateOf<TextSelectionAction?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

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
                            
                            // Demo text box
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
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

                // Settings Section
                item {
                    SettingsGroup(title = stringResource(R.string.settings)) {
                        // Assistant picker - default to Generical
                        val defaultAssistant = settings.assistants.find { it.name == "Generical" }
                            ?: settings.assistants.firstOrNull()
                        
                        SettingGroupItem(
                            title = stringResource(R.string.text_selection_assistant),
                            subtitle = settings.assistants.find { it.id == config.assistantId }?.name
                                ?: defaultAssistant?.name ?: "None",
                            trailing = {
                                Select(
                                    options = settings.assistants.map { it.id },
                                    selectedOption = config.assistantId ?: defaultAssistant?.id,
                                    onOptionSelected = { selected ->
                                        scope.launch {
                                            settingsStore.update {
                                                it.copy(textSelectionConfig = config.copy(assistantId = selected))
                                            }
                                        }
                                    },
                                    optionToString = { id ->
                                        settings.assistants.find { it.id == id }?.name ?: "Unknown"
                                    },
                                )
                            }
                        )

                        // Translate language
                        SettingGroupItem(
                            title = stringResource(R.string.text_selection_translate_language),
                            subtitle = config.translateLanguage,
                            trailing = {
                                Select(
                                    options = COMMON_LANGUAGES,
                                    selectedOption = config.translateLanguage,
                                    onOptionSelected = { lang ->
                                        scope.launch {
                                            settingsStore.update {
                                                it.copy(textSelectionConfig = config.copy(translateLanguage = lang))
                                            }
                                        }
                                    },
                                    optionToString = { it },
                                )
                            }
                        )

                        // Reset to defaults button
                        SettingGroupItem(
                            title = stringResource(R.string.reset),
                            subtitle = "Restore default actions",
                            onClick = { showResetDialog = true }
                        )
                    }
                }

                // Quick Actions Section (grouped)
                item {
                    SettingsGroup(title = stringResource(R.string.text_selection_actions)) {
                        config.actions.forEachIndexed { index, action ->
                            ActionCard(
                                action = action,
                                onClick = { editingAction = action }
                            )
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

    // Edit Action Dialog
    editingAction?.let { action ->
        EditActionDialog(
            action = action,
            onDismiss = { editingAction = null },
            onSave = { updated ->
                scope.launch {
                    val newActions = config.actions.map {
                        if (it.id == updated.id) updated else it
                    }
                    settingsStore.update {
                        it.copy(textSelectionConfig = config.copy(actions = newActions))
                    }
                }
                editingAction = null
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset)) },
            text = { Text("Are you sure you want to restore default actions? This will replace all your custom actions.") },
            confirmButton = {
                Button(
                    onClick = {
                        val oldActions = config.actions
                        scope.launch {
                            settingsStore.update {
                                it.copy(textSelectionConfig = config.copy(
                                    actions = DEFAULT_TEXT_SELECTION_ACTIONS
                                ))
                            }
                        }
                        toaster.show(
                            message = "Actions reset to defaults",
                            action = ToastAction(
                                label = context.getString(R.string.undo),
                                onClick = {
                                    scope.launch {
                                        settingsStore.update {
                                            it.copy(textSelectionConfig = it.textSelectionConfig.copy(
                                                actions = oldActions
                                            ))
                                        }
                                    }
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

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides if (amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
        // Edge-to-edge Surface like actual popup
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp), // No horizontal padding - edge to edge
            shape = RoundedCornerShape(40.dp),
            color = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with LastChat icon
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
                            modifier = Modifier.size(40.dp) // Match popup icon size
                        )
                        Text(
                            text = stringResource(R.string.text_selection_menu_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Preview text
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh // Same as popup
                ) {
                    Text(
                        text = "Selected text preview...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Action buttons grid - matching actual popup layout
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
                                    shape = getButtonShape(rowIndex, colIndex, rows.size, rowActions.size),
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "button_scale"
    )

    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        // Use true Color.Black to match popup exactly
        color = if (isBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isBlack) 0.dp else 6.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForName(action.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
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

@Composable
private fun ActionCard(
    action: TextSelectionAction,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) 
                MaterialTheme.colorScheme.surfaceContainerLow 
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = AppShapes.CardLarge
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = action.name,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = action.prompt.take(60).replace("\n", " ") + 
                        if (action.prompt.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            },
            leadingContent = {
                Icon(
                    imageVector = getIconForName(action.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                if (action.isCustomPrompt) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "Input",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun EditActionDialog(
    action: TextSelectionAction,
    isNew: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (TextSelectionAction) -> Unit
) {
    var prompt by remember { mutableStateOf(action.prompt) }

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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.text_selection_action_prompt)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    minLines = 5
                )
                // Show variable hint only for relevant actions
                val variableHint = when (action.id) {
                    "translate" -> "Variable: {{language}}"
                    "custom" -> "Variable: {{custom_prompt}}"
                    else -> null
                }
                if (variableHint != null) {
                    Text(
                        text = variableHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(action.copy(prompt = prompt)) },
                enabled = prompt.isNotBlank()
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

private fun getButtonShape(rowIndex: Int, colIndex: Int, totalRows: Int, colsInRow: Int): RoundedCornerShape {
    val isFirstRow = rowIndex == 0
    val isLastRow = rowIndex == totalRows - 1
    val isFirstCol = colIndex == 0
    val isLastCol = colIndex == colsInRow - 1
    val isFullWidth = colsInRow == 1

    return RoundedCornerShape(
        topStart = if (isFirstRow && isFirstCol) 24.dp else 10.dp,
        topEnd = if (isFirstRow && (isLastCol || isFullWidth)) 24.dp else 10.dp,
        bottomStart = if (isLastRow && isFirstCol) 24.dp else 10.dp,
        bottomEnd = if (isLastRow && (isLastCol || isFullWidth)) 24.dp else 10.dp
    )
}

private fun getIconForName(name: String): ImageVector {
    return when (name.lowercase()) {
        "translate" -> Icons.Rounded.Translate
        "lightbulb" -> Icons.Rounded.Lightbulb
        "summarize" -> Icons.Rounded.Summarize
        "autoawesome" -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.AutoAwesome
    }
}
