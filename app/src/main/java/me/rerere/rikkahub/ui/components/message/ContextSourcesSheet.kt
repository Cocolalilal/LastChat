package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UsedLorebookEntry
import me.rerere.ai.ui.UsedMemory
import me.rerere.ai.ui.UsedMode
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.icons.ModeIcons
import me.rerere.rikkahub.ui.theme.LocalDarkMode

private val json = Json { ignoreUnknownKeys = true }

// Corner radius values matching PhysicsSwipeToDelete
private val groupCornerRadius = 24.dp
private val itemCornerRadius = 10.dp

/**
 * Bottom sheet displaying all context sources used in a message:
 * modes, memories, and lorebook entries.
 */
@Composable
fun ContextSourcesSheet(
    modes: List<UsedMode> = emptyList(),
    memories: List<UsedMemory> = emptyList(),
    entries: List<UsedLorebookEntry> = emptyList(),
    onModeClick: ((UsedMode) -> Unit)? = null,
    onMemoryClick: ((UsedMemory) -> Unit)? = null,
    onEntryClick: ((UsedLorebookEntry) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    val sortedModes = remember(modes) { modes.sortedByDescending { it.priority } }
    val sortedMemories = remember(memories) { memories.sortedByDescending { it.priority } }
    val sortedEntries = remember(entries) { entries.sortedByDescending { it.priority } }
    
    ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismissRequest()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header - centered
            Text(
                text = stringResource(R.string.context_sources_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Skills Section
                if (sortedModes.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.context_sources_skills)) }
                    itemsIndexed(sortedModes) { index, mode ->
                        val shape = getGroupedShape(index, sortedModes.size)
                        ModeItem(
                            mode = mode,
                            shape = shape,
                            isFirst = index == 0,
                            isLast = index == sortedModes.lastIndex,
                            onClick = { onModeClick?.invoke(mode) }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                
                // Memories Section
                if (sortedMemories.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.context_sources_memories)) }
                    itemsIndexed(sortedMemories) { index, memory ->
                        val shape = getGroupedShape(index, sortedMemories.size)
                        MemoryItem(
                            memory = memory,
                            shape = shape,
                            isFirst = index == 0,
                            isLast = index == sortedMemories.lastIndex,
                            onClick = { onMemoryClick?.invoke(memory) }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                
                // Lorebook Entries Section
                if (sortedEntries.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.context_sources_lorebook_entries)) }
                    itemsIndexed(sortedEntries) { index, entry ->
                        val shape = getGroupedShape(index, sortedEntries.size)
                        LorebookEntryItem(
                            entry = entry,
                            shape = shape,
                            isFirst = index == 0,
                            isLast = index == sortedEntries.lastIndex,
                            onClick = { onEntryClick?.invoke(entry) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculates the corner shape for a grouped item based on position
 */
private fun getGroupedShape(index: Int, total: Int): RoundedCornerShape {
    return when {
        total == 1 -> RoundedCornerShape(groupCornerRadius)
        index == 0 -> RoundedCornerShape(
            topStart = groupCornerRadius, topEnd = groupCornerRadius,
            bottomStart = itemCornerRadius, bottomEnd = itemCornerRadius
        )
        index == total - 1 -> RoundedCornerShape(
            topStart = itemCornerRadius, topEnd = itemCornerRadius,
            bottomStart = groupCornerRadius, bottomEnd = groupCornerRadius
        )
        else -> RoundedCornerShape(itemCornerRadius)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun ModeItem(
    mode: UsedMode,
    shape: RoundedCornerShape,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val isDarkMode = LocalDarkMode.current
    
    // Optical roundness for the cover
    val opticalRadius = 12.dp
    val defaultRadius = 6.dp
    val coverShape = RoundedCornerShape(
        topStart = if (isFirst) opticalRadius else defaultRadius,
        topEnd = defaultRadius,
        bottomStart = if (isLast) opticalRadius else defaultRadius,
        bottomEnd = defaultRadius
    )
    
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skill icon cover
            Box(
                modifier = Modifier
                    .width(45.dp)
                    .height(60.dp)
                    .clip(coverShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = coverShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!mode.modeIcon.isNullOrBlank()) {
                    Icon(
                        imageVector = ModeIcons.getIcon(mode.modeIcon),
                        contentDescription = mode.modeName,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    Text(
                        text = mode.modeName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            
            // Skill info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.context_sources_skill),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = mode.modeName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                mode.activationReason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryItem(
    memory: UsedMemory,
    shape: RoundedCornerShape,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val isDarkMode = LocalDarkMode.current
    val isCore = memory.memoryType == 0
    val memoryTypeLabel = when {
        isCore -> stringResource(R.string.activity_timeline_memory_core)
        memory.memoryId < 0 -> stringResource(R.string.context_sources_recent_chat)
        else -> stringResource(R.string.activity_timeline_memory_episodic)
    }
    
    val backgroundColor = if (isCore) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isCore) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    
    // Optical roundness for the cover
    val opticalRadius = 12.dp
    val defaultRadius = 6.dp
    val coverShape = RoundedCornerShape(
        topStart = if (isFirst) opticalRadius else defaultRadius,
        topEnd = defaultRadius,
        bottomStart = if (isLast) opticalRadius else defaultRadius,
        bottomEnd = defaultRadius
    )
    
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Memory type icon
            Box(
                modifier = Modifier
                    .width(45.dp)
                    .height(60.dp)
                    .clip(coverShape)
                    .background(backgroundColor)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = coverShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCore -> {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = memoryTypeLabel,
                            modifier = Modifier.size(24.dp),
                            tint = contentColor
                        )
                    }
                    memory.memoryId < 0 -> {
                        // Recent chat reference (non-RAG mode)
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = memoryTypeLabel,
                            modifier = Modifier.size(24.dp),
                            tint = contentColor
                        )
                    }
                    else -> {
                        // True episodic memory (RAG mode)
                        Image(
                            painter = painterResource(R.drawable.search_activity_24),
                            contentDescription = memoryTypeLabel,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(contentColor)
                        )
                    }
                }
            }
            
            // Memory info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = memoryTypeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = memory.memoryContent,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                memory.activationReason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LorebookEntryItem(
    entry: UsedLorebookEntry,
    shape: RoundedCornerShape,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val isDarkMode = LocalDarkMode.current
    
    val cover = remember(entry.lorebookCover) {
        entry.lorebookCover?.let { coverJson ->
            try {
                json.decodeFromString(Avatar.serializer(), coverJson)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // Optical roundness for the cover
    val opticalRadius = 12.dp
    val defaultRadius = 6.dp
    val coverShape = RoundedCornerShape(
        topStart = if (isFirst) opticalRadius else defaultRadius,
        topEnd = defaultRadius,
        bottomStart = if (isLast) opticalRadius else defaultRadius,
        bottomEnd = defaultRadius
    )
    
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book cover
            Box(
                modifier = Modifier
                    .width(45.dp)
                    .height(60.dp)
                    .clip(coverShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = coverShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (cover) {
                    is Avatar.Image -> {
                        AsyncImage(
                            model = cover.url,
                            contentDescription = entry.lorebookName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    is Avatar.Emoji -> {
                        Text(text = cover.content, fontSize = 24.sp)
                    }
                    is Avatar.Resource -> {
                        AsyncImage(
                            model = cover.id,
                            contentDescription = entry.lorebookName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        Text(
                            text = entry.lorebookName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Entry number
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .padding(bottom = 2.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "#${entry.entryIndex + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White
                    )
                }
            }
            
            // Entry info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.lorebookName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.entryName.ifBlank { "Entry #${entry.entryIndex + 1}" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                entry.activationReason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
