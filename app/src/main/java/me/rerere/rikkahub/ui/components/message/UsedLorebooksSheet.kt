package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UsedLorebookEntry
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private val json = Json { ignoreUnknownKeys = true }

// Corner radius values matching PhysicsSwipeToDelete
private val groupCornerRadius = 24.dp
private val itemCornerRadius = 10.dp

/**
 * Bottom sheet displaying all lorebook entries used in a message.
 * Each entry can be clicked to open its edit dialog.
 */
@Composable
fun UsedLorebooksSheet(
    entries: List<UsedLorebookEntry>,
    onEntryClick: (UsedLorebookEntry) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
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
            // Header
            Text(
                text = stringResource(R.string.used_lorebooks_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
            
            // Entries list - grouped with small gap
            val sortedEntries = entries.sortedByDescending { it.priority }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(sortedEntries) { index, entry ->
                    val shape = when {
                        sortedEntries.size == 1 -> RoundedCornerShape(groupCornerRadius)
                        index == 0 -> RoundedCornerShape(
                            topStart = groupCornerRadius, topEnd = groupCornerRadius,
                            bottomStart = itemCornerRadius, bottomEnd = itemCornerRadius
                        )
                        index == sortedEntries.lastIndex -> RoundedCornerShape(
                            topStart = itemCornerRadius, topEnd = itemCornerRadius,
                            bottomStart = groupCornerRadius, bottomEnd = groupCornerRadius
                        )
                        else -> RoundedCornerShape(itemCornerRadius)
                    }
                    
                    UsedLorebookEntryItem(
                        entry = entry,
                        onClick = { onEntryClick(entry) },
                        shape = shape,
                        isFirst = index == 0,
                        isLast = index == sortedEntries.lastIndex
                    )
                }
            }
        }
    }
}

/**
 * Single entry item in the used lorebooks sheet
 */
@Composable
private fun UsedLorebookEntryItem(
    entry: UsedLorebookEntry,
    onClick: () -> Unit,
    shape: RoundedCornerShape,
    isFirst: Boolean,
    isLast: Boolean
) {
    val isDarkMode = LocalDarkMode.current
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
            // Book cover (Card shape)
            // Deserialize cover from JSON string
            val cover = remember(entry.lorebookCover) {
                entry.lorebookCover?.let { coverJson ->
                    try {
                        json.decodeFromString(Avatar.serializer(), coverJson)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            BookCoverItem(
                lorebookName = entry.lorebookName,
                cover = cover,
                entryIndex = entry.entryIndex,
                isFirst = isFirst,
                isLast = isLast
            )
            
            // Entry info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Lorebook Name (above entry title)
                Text(
                    text = entry.lorebookName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Entry Title
                Text(
                    text = entry.entryName.ifBlank { "Entry #${entry.entryIndex + 1}" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Activation Reason
                if (!entry.activationReason.isNullOrBlank()) {
                    Text(
                        text = entry.activationReason!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCoverItem(
    lorebookName: String,
    cover: Avatar?,
    entryIndex: Int,
    isFirst: Boolean,
    isLast: Boolean
) {
    val width = 45.dp
    val height = 60.dp
    
    // Optical roundness calculation:
    // Outer Radius (24dp) - Padding (12dp) = 12dp
    // Default radius = 6dp
    val opticalRadius = 12.dp
    val defaultRadius = 6.dp
    
    val shape = RoundedCornerShape(
        topStart = if (isFirst) opticalRadius else defaultRadius,
        topEnd = defaultRadius,
        bottomStart = if (isLast) opticalRadius else defaultRadius,
        bottomEnd = defaultRadius
    )
    
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Render actual cover image or letter-based fallback
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
                    fontSize = 24.sp
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
                // Letter-based fallback
                Text(
                    text = lorebookName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface 
                )
            }
        }
        
        // Entry number at bottom with gradient background
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
                text = "#${entryIndex + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White
            )
        }
    }
}
