package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UsedLorebookEntry
import me.rerere.rikkahub.data.model.Avatar

private val json = Json { ignoreUnknownKeys = true }

/**
 * Displays stacked lorebook covers indicating which entries were used.
 * Up to 3 covers are shown stacked, with a "+N" badge if more exist.
 * Left-most = highest priority at full opacity.
 */
@Composable
fun LorebookStackIndicator(
    entries: List<UsedLorebookEntry>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    
    // Sort by priority (higher = first) and take top 3
    val sortedEntries = entries.sortedByDescending { it.priority }
    val displayEntries = sortedEntries.take(3)
    val extraCount = (entries.size - 3).coerceAtLeast(0)
    
    // Config
    val bookWidth = 24.dp
    val bookHeight = 32.dp
    val overlap = 14.dp // How much subsequent books are visible (offset amount)
    val badgeSize = 20.dp
    
    // Calculate total container size explicitly to avoid layout issues
    val totalWidth = if (displayEntries.isNotEmpty()) {
        val booksWidth = bookWidth + (overlap * (displayEntries.size - 1))
        // Badge overlaps by 10dp, so only add 10dp (half badge) for visible part
        if (extraCount > 0) booksWidth + (badgeSize / 2) else booksWidth
    } else {
        0.dp
    }


    Box(
        modifier = modifier
            .width(totalWidth)
            .height(bookHeight)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        // Draw the books
        displayEntries.forEachIndexed { index, entry ->
            val alpha = when (index) {
                0 -> 1f      // First (left-most) - full opacity
                1 -> 0.7f    // Second - reduced
                else -> 0.45f // Third - more reduced
            }
            
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
            
            BookCover(
                lorebookName = entry.lorebookName,
                cover = cover,
                entryIndex = entry.entryIndex,
                alpha = alpha,
                modifier = Modifier
                    .offset(x = overlap * index)
                    .zIndex((displayEntries.size - index).toFloat()) // First = Highest Z (Front)
                    .width(bookWidth)
                    .height(bookHeight)
            )
        }
        
        // "+N" circle badge
        if (extraCount > 0) {
            // Overlap halfway with the last card for depth (-10.dp = half of 20dp badge)
            val badgeOffset = bookWidth + (overlap * (displayEntries.size - 1)) - 10.dp
            Box(
                modifier = Modifier
                    .offset(x = badgeOffset)
                    .size(badgeSize)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape) // Add border for separation
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .zIndex(10f), // Badge on top
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
 * Single book cover shape - taller than wide like a real book
 */
@Composable
private fun BookCover(
    lorebookName: String,
    cover: Avatar?,
    entryIndex: Int,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
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
                // Letter-based fallback
                Text(
                    text = lorebookName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface 
                )
            }
        }
        
        // Entry number at bottom with gradient background
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
