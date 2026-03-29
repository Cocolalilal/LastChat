package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UsedLorebookEntry
import me.rerere.ai.ui.UsedMemory
import me.rerere.ai.ui.UsedMode
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.icons.ModeIcons
import me.rerere.rikkahub.ui.theme.LocalDarkMode

private val json = Json { ignoreUnknownKeys = true }

/**
 * Represents a single context source item for unified display
 */
private sealed class ContextStackItem {
    abstract val priority: Int
    
    data class Mode(val mode: UsedMode) : ContextStackItem() {
        override val priority: Int get() = mode.priority
    }
    
    data class Memory(val memory: UsedMemory) : ContextStackItem() {
        override val priority: Int get() = memory.priority
    }
    
    data class Lorebook(val entry: UsedLorebookEntry) : ContextStackItem() {
        override val priority: Int get() = entry.priority
    }
}

/**
 * Displays stacked context source covers indicating which modes, memories, and lorebook entries were used.
 * Up to 3 covers are shown stacked, with a "+N" badge if more exist.
 * Left-most = highest priority at full visibility.
 * Uses color overlay for dimming instead of transparency.
 */
@Composable
fun ContextStackIndicator(
    modes: List<UsedMode> = emptyList(),
    memories: List<UsedMemory> = emptyList(),
    entries: List<UsedLorebookEntry> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode = LocalDarkMode.current
    
    // Combine all items and sort by priority
    val allItems = remember(modes, memories, entries) {
        buildList {
            modes.forEach { add(ContextStackItem.Mode(it)) }
            memories.forEach { add(ContextStackItem.Memory(it)) }
            entries.forEach { add(ContextStackItem.Lorebook(it)) }
        }.sortedByDescending { it.priority }
    }
    
    if (allItems.isEmpty()) return
    
    val displayItems = allItems.take(3)
    val extraCount = (allItems.size - 3).coerceAtLeast(0)
    
    // Config
    val bookWidth = 24.dp
    val bookHeight = 32.dp
    val overlap = 14.dp
    val badgeSize = 20.dp
    
    val totalWidth = if (displayItems.isNotEmpty()) {
        val booksWidth = bookWidth + (overlap * (displayItems.size - 1))
        if (extraCount > 0) booksWidth + (badgeSize / 2) else booksWidth
    } else {
        0.dp
    }
    
    // Overlay color for dimming (darken in dark mode, lighten in light mode)
    val overlayColor = if (isDarkMode) Color.Black else Color.White

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(bookHeight)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        displayItems.forEachIndexed { index, item ->
            // Calculate overlay alpha for dimming effect
            val overlayAlpha = when (index) {
                0 -> 0f       // First (left-most) - no overlay
                1 -> 0.3f     // Second - slight dim
                else -> 0.55f // Third - more dim
            }
            
            when (item) {
                is ContextStackItem.Mode -> {
                    ModeCover(
                        mode = item.mode,
                        overlayColor = overlayColor,
                        overlayAlpha = overlayAlpha,
                        modifier = Modifier
                            .offset(x = overlap * index)
                            .zIndex((displayItems.size - index).toFloat())
                            .width(bookWidth)
                            .height(bookHeight)
                    )
                }
                is ContextStackItem.Memory -> {
                    MemoryCover(
                        memory = item.memory,
                        overlayColor = overlayColor,
                        overlayAlpha = overlayAlpha,
                        modifier = Modifier
                            .offset(x = overlap * index)
                            .zIndex((displayItems.size - index).toFloat())
                            .width(bookWidth)
                            .height(bookHeight)
                    )
                }
                is ContextStackItem.Lorebook -> {
                    val cover = remember(item.entry.lorebookCover) {
                        item.entry.lorebookCover?.let { coverJson ->
                            try {
                                json.decodeFromString(Avatar.serializer(), coverJson)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    
                    LorebookCover(
                        lorebookName = item.entry.lorebookName,
                        cover = cover,
                        entryIndex = item.entry.entryIndex,
                        overlayColor = overlayColor,
                        overlayAlpha = overlayAlpha,
                        modifier = Modifier
                            .offset(x = overlap * index)
                            .zIndex((displayItems.size - index).toFloat())
                            .width(bookWidth)
                            .height(bookHeight)
                    )
                }
            }
        }
        
        // "+N" circle badge
        if (extraCount > 0) {
            val badgeOffset = bookWidth + (overlap * (displayItems.size - 1)) - 10.dp
            Box(
                modifier = Modifier
                    .offset(x = badgeOffset)
                    .size(badgeSize)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extraCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * Mode cover with Material icon and colored background
 */
@Composable
private fun ModeCover(
    mode: UsedMode,
    overlayColor: Color,
    overlayAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .drawWithContent {
                drawContent()
                // Draw overlay on top for dimming
                if (overlayAlpha > 0f) {
                    drawRect(overlayColor.copy(alpha = overlayAlpha))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!mode.modeIcon.isNullOrBlank()) {
            Icon(
                imageVector = ModeIcons.getIcon(mode.modeIcon),
                contentDescription = mode.modeName,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        } else {
            Text(
                text = mode.modeName.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * Memory cover with type-based styling and icons
 */
@Composable
private fun MemoryCover(
    memory: UsedMemory,
    overlayColor: Color,
    overlayAlpha: Float,
    modifier: Modifier = Modifier
) {
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
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .drawWithContent {
                drawContent()
                if (overlayAlpha > 0f) {
                    drawRect(overlayColor.copy(alpha = overlayAlpha))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isCore -> {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = memoryTypeLabel,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
            memory.memoryId < 0 -> {
                // Recent chat reference (non-RAG mode)
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = memoryTypeLabel,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
            else -> {
                // True episodic memory (RAG mode)
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.search_activity_24),
                    contentDescription = memoryTypeLabel,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(contentColor)
                )
            }
        }
    }
}

/**
 * Lorebook entry cover
 */
@Composable
private fun LorebookCover(
    lorebookName: String,
    cover: Avatar?,
    entryIndex: Int,
    overlayColor: Color,
    overlayAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .drawWithContent {
                drawContent()
                if (overlayAlpha > 0f) {
                    drawRect(overlayColor.copy(alpha = overlayAlpha))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (cover) {
            is Avatar.Image -> {
                AsyncImage(
                    model = cover.url,
                    contentDescription = lorebookName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            is Avatar.Emoji -> {
                Text(
                    text = cover.content,
                    fontSize = 14.sp
                )
            }
            is Avatar.Resource -> {
                AsyncImage(
                    model = cover.id,
                    contentDescription = lorebookName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Text(
                    text = lorebookName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // Entry number at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(bottom = 1.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "#${entryIndex + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Color.White
            )
        }
    }
}
