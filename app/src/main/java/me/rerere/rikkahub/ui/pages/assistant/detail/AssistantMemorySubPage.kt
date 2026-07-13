package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.SummarizerModelTipBanner
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics

/**
 * Memory mode based on current settings
 */
private fun getMemoryMode(assistant: Assistant): MemoryMode {
    return when {
        !assistant.enableMemory -> MemoryMode.OFF
        assistant.enableMemoryConsolidation -> MemoryMode.ADVANCED
        assistant.useRagMemoryRetrieval -> MemoryMode.BASIC_RAG
        assistant.enableRecentChatsReference -> MemoryMode.BASIC_RECENT
        else -> MemoryMode.BASIC
    }
}

private enum class MemoryMode(
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int
) {
    OFF(R.string.assistant_memory_mode_off, R.string.assistant_memory_mode_off_desc),
    BASIC(R.string.assistant_memory_mode_basic, R.string.assistant_memory_mode_basic_desc),
    BASIC_RECENT(R.string.assistant_memory_mode_basic_recent, R.string.assistant_memory_mode_basic_recent_desc),
    BASIC_RAG(R.string.assistant_memory_mode_basic_rag, R.string.assistant_memory_mode_basic_rag_desc),
    ADVANCED(R.string.assistant_memory_mode_advanced, R.string.assistant_memory_mode_advanced_desc)
}

private enum class MemorySortOrder(@StringRes val displayNameRes: Int) {
    NEWEST_FIRST(R.string.assistant_memory_sort_newest),
    OLDEST_FIRST(R.string.assistant_memory_sort_oldest),
    ALPHABETICAL(R.string.assistant_memory_sort_alpha)
}

@Composable
fun AssistantMemorySettings(
    assistant: Assistant,
    hasSummarizerModelConfigured: Boolean,
    memories: List<AssistantMemory>,
    onUpdateAssistant: (Assistant) -> Unit,
    onAddMemory: (AssistantMemory) -> Unit,
    onUpdateMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
    onRegenerateEmbeddings: (() -> Unit)? = null,
    embeddingProgress: EmbeddingProgress? = null,
    onTestRetrieval: ((String) -> Unit)? = null,
    retrievalResults: List<Pair<AssistantMemory, Float>> = emptyList(),
    assistantDetailVM: AssistantDetailVM,
    estimatedMemoryCapacity: Int,
    needsEmbeddingRegeneration: Boolean = false,
    initialMemoryTab: Int? = null,  // 0 = Core, 1 = Episodic
    scrollToMemoryId: Int? = null,
    onNavigateToSummarizerSettings: () -> Unit = {}
) {
    val memoryDialogState = useEditState<AssistantMemory> {
        if (it.id == 0) {
            onAddMemory(it)
        } else {
            onUpdateMemory(it)
        }
    }
    
    // Embedding progress dialog
    if (embeddingProgress != null && embeddingProgress.isRunning) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.assistant_memory_generating_embeddings)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.assistant_memory_embedding_progress,
                            embeddingProgress.current,
                            embeddingProgress.total
                        )
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { embeddingProgress.current.toFloat() / embeddingProgress.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { }
        )
    }

    // Memory edit dialog
    memoryDialogState.EditStateContent { memory, update ->
        AlertDialog(
            onDismissRequest = { memoryDialogState.dismiss() },
            title = { Text(stringResource(R.string.assistant_page_manage_memory_title)) },
            text = {
                TextField(
                    value = memory.content,
                    onValueChange = { update(memory.copy(content = it)) },
                    label = { Text(stringResource(R.string.assistant_page_manage_memory_title)) },
                    minLines = 1,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(onClick = { memoryDialogState.confirm() }) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryDialogState.dismiss() }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    val memorySearchQuery by assistantDetailVM.memorySearchQuery.collectAsState()
    val currentEmbeddingModelId by assistantDetailVM.currentEmbeddingModelId.collectAsState()
    val currentMode = getMemoryMode(assistant)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Indicator
        MemoryModeIndicator(mode = currentMode)
        
        // ═══════════════════════════════════════════════════════════════════
        // SETTINGS GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroupHeader(title = stringResource(R.string.assistant_memory_settings_title))
        
        Column(
            modifier = Modifier.clip(RoundedCornerShape(24.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Master Toggle - Enable Memory (always visible)
            MemorySettingsItem(
                title = stringResource(R.string.assistant_page_memory),
                subtitle = stringResource(R.string.assistant_page_memory_desc),
                position = if (!assistant.enableMemory) "ONLY" else "FIRST",
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableMemory,
                        onCheckedChange = { onUpdateAssistant(assistant.copy(enableMemory = it)) }
                    )
                }
            )

            // Recent Chats Toggle (when memory enabled)
            AnimatedVisibility(
                visible = assistant.enableMemory,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val isLockedByConsolidation = assistant.enableMemoryConsolidation
                
                MemorySettingsItem(
                    title = stringResource(R.string.assistant_page_recent_chats),
                    subtitle = stringResource(R.string.assistant_page_recent_chats_desc),
                    // RAG toggle is always visible below when memory is on, so this is always MIDDLE
                    position = "MIDDLE",
                    trailing = {
                        // Use 0.75f alpha for disabled state - subtle but visible
                        val toggleAlpha by animateFloatAsState(
                            targetValue = if (isLockedByConsolidation) 0.75f else 1f,
                            animationSpec = spring(stiffness = 300f),
                            label = "toggle_alpha"
                        )
                        Box(modifier = Modifier.graphicsLayer { alpha = toggleAlpha }) {
                            HapticSwitch(
                                checked = assistant.enableRecentChatsReference || isLockedByConsolidation,
                                onCheckedChange = { 
                                    if (!isLockedByConsolidation) {
                                        onUpdateAssistant(assistant.copy(enableRecentChatsReference = it))
                                    }
                                },
                                enabled = !isLockedByConsolidation
                            )
                        }
                    }
                )
            }

            // Memory Search Tool Toggle (when memory enabled)
            AnimatedVisibility(
                visible = assistant.enableMemory,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                MemorySettingsItem(
                    title = stringResource(R.string.assistant_memory_search_tool),
                    subtitle = stringResource(R.string.assistant_memory_search_tool_desc),
                    position = "MIDDLE",
                    trailing = {
                        HapticSwitch(
                            checked = assistant.enableMemorySearchTool,
                            onCheckedChange = { enabled ->
                                onUpdateAssistant(assistant.copy(enableMemorySearchTool = enabled))
                            }
                        )
                    }
                )
            }

            // RAG Toggle (when memory enabled)
            AnimatedVisibility(
                visible = assistant.enableMemory,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                MemorySettingsItem(
                    title = stringResource(R.string.assistant_memory_rag_retrieval),
                    subtitle = stringResource(R.string.assistant_memory_rag_retrieval_desc),
                    position = if (!assistant.useRagMemoryRetrieval) "LAST" else "MIDDLE",
                    trailing = {
                        HapticSwitch(
                            checked = assistant.useRagMemoryRetrieval,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    onUpdateAssistant(assistant.copy(
                                        useRagMemoryRetrieval = false,
                                        enableMemoryConsolidation = false
                                    ))
                                } else {
                                    onUpdateAssistant(assistant.copy(useRagMemoryRetrieval = true))
                                }
                            }
                        )
                    }
                )
            }

            // Memory Consolidation Toggle (requires RAG)
            AnimatedVisibility(
                visible = assistant.enableMemory && assistant.useRagMemoryRetrieval,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                MemorySettingsItem(
                    title = stringResource(R.string.assistant_memory_advanced_title),
                    subtitle = stringResource(R.string.assistant_memory_advanced_desc),
                    position = "LAST",
                    trailing = {
                        HapticSwitch(
                            checked = assistant.enableMemoryConsolidation,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    onUpdateAssistant(assistant.copy(
                                        enableMemoryConsolidation = false
                                    ))
                                } else {
                                    onUpdateAssistant(assistant.copy(
                                        enableMemoryConsolidation = true,
                                        enableRecentChatsReference = true
                                    ))
                                }
                            }
                        )
                    }
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // RAG SETTINGS (when RAG is enabled)
        // ═══════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = assistant.enableMemory && assistant.useRagMemoryRetrieval,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsGroupHeader(title = stringResource(R.string.assistant_memory_rag_settings))
                RagSettingsCard(assistant = assistant, onUpdateAssistant = onUpdateAssistant)

                // Regenerate embeddings button (visible when embeddings are missing or outdated)
                AnimatedVisibility(
                    visible = needsEmbeddingRegeneration && onRegenerateEmbeddings != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.assistant_memory_regenerate_embeddings),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Some memories are missing embeddings or were embedded with a different model.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onRegenerateEmbeddings?.invoke() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.assistant_memory_regenerate_embeddings))
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // CONSOLIDATION SETTINGS (when consolidation is enabled)
        // ═══════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = assistant.enableMemory && assistant.enableMemoryConsolidation,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsGroupHeader(title = stringResource(R.string.assistant_memory_advanced_settings))
                
                ConsolidationSettingsCard(
                    assistant = assistant,
                    onUpdateAssistant = onUpdateAssistant,
                    onConsolidate = { assistantDetailVM.consolidateMemories(true) },
                    showSummarizerWarning = !hasSummarizerModelConfigured,
                    onNavigateToSummarizerSettings = onNavigateToSummarizerSettings
                )
            }
        }


        // ═══════════════════════════════════════════════════════════════════
        // MEMORY STATISTICS (when memory is enabled)
        // ═══════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = assistant.enableMemory,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            MemoryStatisticsCard(
                assistant = assistant,
                memories = memories,
                estimatedMemoryCapacity = estimatedMemoryCapacity
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // MANAGE MEMORIES (when memory is enabled)
        // ═══════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = assistant.enableMemory,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ManageMemoriesSection(
                memories = memories,
                assistant = assistant,
                onAddMemory = { memoryDialogState.open(AssistantMemory(0, "")) },
                onEditMemory = { memoryDialogState.open(it) },
                onDeleteMemory = onDeleteMemory,
                memorySearchQuery = memorySearchQuery,
                onSearchQueryChange = { assistantDetailVM.updateMemorySearchQuery(it) },
                currentEmbeddingModelId = currentEmbeddingModelId,
                showMemoryTypes = assistant.enableMemoryConsolidation,
                initialMemoryTab = initialMemoryTab,
                scrollToMemoryId = scrollToMemoryId,
                onRegenerateEmbeddings = onRegenerateEmbeddings,
                embeddingProgress = embeddingProgress,
                needsEmbeddingRegeneration = needsEmbeddingRegeneration
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // MEMORY DEBUGGER (RAG only)
        // ═══════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = assistant.enableMemory && assistant.useRagMemoryRetrieval && onTestRetrieval != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (onTestRetrieval != null) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsGroupHeader(title = stringResource(R.string.assistant_memory_debugger))
                    MemoryDebugger(
                        onTestRetrieval = onTestRetrieval,
                        retrievalResults = retrievalResults
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun MemorySettingsItem(
    title: String,
    subtitle: String? = null,
    position: String = "MIDDLE", // ONLY, FIRST, MIDDLE, LAST
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "scale"
    )
    
    val topCorner by animateDpAsState(
        targetValue = when (position) {
            "ONLY", "FIRST" -> 24.dp
            else -> 10.dp
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "topCorner"
    )
    val bottomCorner by animateDpAsState(
        targetValue = when (position) {
            "ONLY", "LAST" -> 24.dp
            else -> 10.dp
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "bottomCorner"
    )
    
    Surface(
        onClick = {
            if (onClick != null) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
        },
        enabled = onClick != null,
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner
        ),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@Composable
private fun MemoryModeIndicator(mode: MemoryMode) {
    val backgroundColor by animateColorAsState(
        targetValue = if (mode == MemoryMode.OFF)
            if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
        else
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        animationSpec = spring(),
        label = "modeColor"
    )
    
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        modifier = Modifier.animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = if (mode == MemoryMode.OFF)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                AnimatedContent(
                    targetState = mode.displayNameRes,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "modeName"
                ) { nameRes ->
                    Text(
                        text = stringResource(R.string.assistant_memory_mode_label, stringResource(nameRes)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                AnimatedContent(
                    targetState = mode.descriptionRes,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "modeDesc"
                ) { descRes ->
                    Text(
                        text = stringResource(descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RagSettingsCard(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Similarity Threshold
        Surface(
            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var threshold by remember(assistant.ragSimilarityThreshold) {
                    mutableFloatStateOf(assistant.ragSimilarityThreshold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.assistant_memory_similarity_threshold),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = String.format("%.2f", threshold),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = threshold,
                    onValueChange = { newValue ->
                        threshold = newValue
                        onUpdateAssistant(assistant.copy(ragSimilarityThreshold = newValue))
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.assistant_memory_similarity_all),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.assistant_memory_similarity_exact),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsolidationSettingsCard(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onConsolidate: () -> Unit,
    showSummarizerWarning: Boolean = false,
    onNavigateToSummarizerSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AnimatedVisibility(
            visible = showSummarizerWarning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SummarizerModelTipBanner(onClick = onNavigateToSummarizerSettings)
        }
        Surface(
            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.assistant_memory_consolidation_delay_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.assistant_memory_consolidation_delay_value,
                            assistant.consolidationDelayMinutes
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.assistant_memory_consolidation_delay_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = assistant.consolidationDelayMinutes.toFloat(),
                    onValueChange = { onUpdateAssistant(assistant.copy(consolidationDelayMinutes = it.toInt())) },
                    valueRange = 0f..240f,
                    steps = 23,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Surface(
            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConsolidate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Psychology, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.assistant_memory_consolidate_now))
                }

                if (assistant.lastConsolidationTime > 0) {
                    val time = java.time.Instant.ofEpochMilli(assistant.lastConsolidationTime)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()
                        .toLocalString()
                    Text(
                        text = stringResource(R.string.assistant_memory_last_run, time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryStatisticsCard(
    assistant: Assistant,
    memories: List<AssistantMemory>,
    estimatedMemoryCapacity: Int
) {
    val coreMemories = memories.count { it.type == 0 }
    val episodicMemories = memories.count { it.type == 1 }
    val withEmbeddings = memories.count { it.hasEmbedding }

    Surface(
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.assistant_memory_statistics),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Only show Core/Episodic split when consolidation is enabled
                if (assistant.enableMemoryConsolidation) {
                    StatItem(
                        value = coreMemories.toString(),
                        label = stringResource(R.string.assistant_memory_core_memories),
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatItem(
                        value = episodicMemories.toString(),
                        label = stringResource(R.string.assistant_memory_episodic_memories),
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    StatItem(
                        value = memories.size.toString(),
                        label = stringResource(R.string.assistant_memory_total),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Show embeddings when RAG is enabled
                AnimatedVisibility(visible = assistant.useRagMemoryRetrieval) {
                    StatItem(
                        value = withEmbeddings.toString(),
                        label = stringResource(R.string.assistant_memory_embedded),
                        color = if (withEmbeddings < memories.size) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            AnimatedVisibility(visible = assistant.useRagMemoryRetrieval) {
                Text(
                    text = stringResource(
                        R.string.assistant_memory_estimated_capacity,
                        estimatedMemoryCapacity
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ManageMemoriesSection(
    memories: List<AssistantMemory>,
    assistant: Assistant,
    onAddMemory: () -> Unit,
    onEditMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
    memorySearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentEmbeddingModelId: String,
    showMemoryTypes: Boolean,
    initialMemoryTab: Int? = null,
    scrollToMemoryId: Int? = null,
    onRegenerateEmbeddings: (() -> Unit)? = null,
    embeddingProgress: EmbeddingProgress? = null,
    needsEmbeddingRegeneration: Boolean = false
) {
    // Use initialMemoryTab if provided, otherwise default to 0
    var selectedTab by remember { mutableIntStateOf(initialMemoryTab ?: 0) }
    var sortOrder by remember { mutableStateOf(MemorySortOrder.NEWEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Auto-select tab when navigating from context sources
    LaunchedEffect(initialMemoryTab) {
        if (initialMemoryTab != null) {
            selectedTab = initialMemoryTab
        }
    }
    
    // Auto-open memory editor when navigating from context sources
    LaunchedEffect(scrollToMemoryId, memories) {
        if (scrollToMemoryId != null && memories.isNotEmpty()) {
            val targetMemory = memories.find { it.id == scrollToMemoryId }
            if (targetMemory != null) {
                onEditMemory(targetMemory)
            }
        }
    }

    val coreMemories = memories.filter { it.type == 0 }
    val episodicMemories = memories.filter { it.type == 1 }
    
    // Filter and sort based on current settings
    val displayMemories = if (showMemoryTypes) {
        when (selectedTab) {
            0 -> coreMemories
            else -> episodicMemories
        }
    } else {
        memories
    }.filter { memory ->
        memorySearchQuery.isBlank() || memory.content.contains(memorySearchQuery, ignoreCase = true)
    }.let { list ->
        when (sortOrder) {
            MemorySortOrder.NEWEST_FIRST -> list.sortedByDescending { it.timestamp }
            MemorySortOrder.OLDEST_FIRST -> list.sortedBy { it.timestamp }
            MemorySortOrder.ALPHABETICAL -> list.sortedBy { it.content.lowercase() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.assistant_page_manage_memory_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Regenerate button
                if (onRegenerateEmbeddings != null && assistant.useRagMemoryRetrieval) {
                    IconButton(
                        onClick = { onRegenerateEmbeddings() },
                        enabled = embeddingProgress?.isRunning != true
                    ) {
                        if (embeddingProgress?.isRunning == true) {
                            val progress = embeddingProgress.current.toFloat() / embeddingProgress.total.coerceAtLeast(1)
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = if (needsEmbeddingRegeneration) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                }

                // Sort button
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.Rounded.Sort,
                            contentDescription = stringResource(R.string.assistant_memory_sort)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        MemorySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(stringResource(order.displayNameRes)) },
                                onClick = {
                                    sortOrder = order
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortOrder == order) {
                                        Icon(Icons.Rounded.Checklist, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    }
                }
                

                IconButton(onClick = onAddMemory) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.assistant_memory_add_memory)
                    )
                }
            }
        }

        // Category Tabs (only when consolidation is enabled)
        AnimatedVisibility(
            visible = showMemoryTypes,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.assistant_memory_tab_core, coreMemories.size)) },
                    icon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.assistant_memory_tab_episodic, episodicMemories.size)) },
                    icon = { Icon(Icons.Rounded.History, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        // Search
        TextField(
            value = memorySearchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.assistant_memory_search_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        // Memory list with animation
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            displayMemories.forEachIndexed { index, memory ->
                key(memory.id) {
                    val position = when {
                        displayMemories.size == 1 -> "ONLY"
                        index == 0 -> "FIRST"
                        index == displayMemories.size - 1 -> "LAST"
                        else -> "MIDDLE"
                    }
                    MemoryItem(
                        memory = memory,
                        onEditMemory = onEditMemory,
                        onDeleteMemory = onDeleteMemory,
                        useRagMemoryRetrieval = assistant.useRagMemoryRetrieval,
                        currentEmbeddingModelId = currentEmbeddingModelId,
                        showType = showMemoryTypes,
                        position = position
                    )
                }
            }
            
            if (displayMemories.isEmpty()) {
                Surface(
                    color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (memorySearchQuery.isBlank()) {
                            stringResource(R.string.assistant_memory_empty)
                        } else {
                            stringResource(R.string.assistant_memory_empty_search)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryItem(
    memory: AssistantMemory,
    onEditMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
    useRagMemoryRetrieval: Boolean = false,
    currentEmbeddingModelId: String = "",
    showType: Boolean = false,
    position: String = "MIDDLE"
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "scale"
    )
    
    val topCorner by animateDpAsState(
        targetValue = when (position) {
            "ONLY", "FIRST" -> 24.dp
            else -> 10.dp
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "topCorner"
    )
    val bottomCorner by animateDpAsState(
        targetValue = when (position) {
            "ONLY", "LAST" -> 24.dp
            else -> 10.dp
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "bottomCorner"
    )
    
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.assistant_page_delete)) },
            text = { 
                Text(
                    text = stringResource(R.string.delete_memory_confirmation) + "\n\n\"${memory.content.take(100)}${if (memory.content.length > 100) "..." else ""}\""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteMemory(memory)
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }
    
    Surface(
        onClick = { onEditMemory(memory) },
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner
        ),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Show type and embedding badges only when needed
                val isModelMismatch = useRagMemoryRetrieval && memory.hasEmbedding &&
                    memory.embeddingModelId != null && memory.embeddingModelId != currentEmbeddingModelId
                val isMissingEmbedding = useRagMemoryRetrieval && !memory.hasEmbedding
                val showBadges = showType || isMissingEmbedding || isModelMismatch
                AnimatedVisibility(
                    visible = showBadges,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showType) {
                            Surface(
                                color = if (memory.type == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = if (memory.type == 0) {
                                        stringResource(R.string.assistant_memory_type_core)
                                    } else {
                                        stringResource(R.string.assistant_memory_type_episodic)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = if (memory.type == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        
                        if (isMissingEmbedding) {
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = stringResource(R.string.assistant_memory_no_embedding),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = Color.White
                                )
                            }
                        } else if (isModelMismatch) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = stringResource(R.string.assistant_memory_outdated_embedding),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                        }
                    }
                }
                
                Text(
                    text = memory.content,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Only show delete for core memories (user-created)
            if (memory.type == 0) {
                IconButton(onClick = { 
                    haptics.perform(HapticPattern.Pop)
                    showDeleteConfirmation = true 
                }) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.assistant_page_delete))
                }
            }
        }
    }
}

@Composable
private fun MemoryDebugger(
    onTestRetrieval: (String) -> Unit,
    retrievalResults: List<Pair<AssistantMemory, Float>>
) {
    val (query, setQuery) = remember { mutableStateOf("") }

    Surface(
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_memory_test_retrieval_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = query,
                    onValueChange = setQuery,
                    placeholder = { Text(stringResource(R.string.assistant_memory_test_query_placeholder)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Button(
                    onClick = { onTestRetrieval(query) },
                    enabled = query.isNotBlank()
                ) {
                    Text(stringResource(R.string.assistant_memory_test))
                }
            }

            AnimatedVisibility(
                visible = retrievalResults.isNotEmpty(),
                enter = fadeIn() + expandVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.assistant_memory_results, retrievalResults.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    retrievalResults.forEachIndexed { index, (memory, score) ->
                        Surface(
                            color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("#${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        stringResource(
                                            R.string.assistant_memory_score,
                                            String.format("%.4f", score)
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (score >= 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = memory.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
