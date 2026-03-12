package me.rerere.rikkahub.ui.pages.assistant.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.Screen
import kotlin.uuid.Uuid

@Composable
fun AssistantLorebooksSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lorebooks = settings.lorebooks
    val navController = LocalNavController.current
    
    if (lorebooks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Book,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.assistant_lorebooks_empty),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.assistant_lorebooks_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp) // Match Lorebooks settings spacing
        ) {
            // Description card header
            item(key = "description") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_lorebooks_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.assistant_lorebooks_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            
            // Section header for lorebook list
            item(key = "section_header") {
                Text(
                    text = stringResource(R.string.lorebooks_section_available),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
                )
            }
            
            itemsIndexed(lorebooks, key = { _, it -> it.id }) { index, lorebook ->
                val isEnabled = assistant.enabledLorebookIds.contains(lorebook.id)
                
                // Calculate position for connected card styling
                val position = when {
                    lorebooks.size == 1 -> me.rerere.rikkahub.ui.components.ui.ItemPosition.ONLY
                    index == 0 -> me.rerere.rikkahub.ui.components.ui.ItemPosition.FIRST
                    index == lorebooks.lastIndex -> me.rerere.rikkahub.ui.components.ui.ItemPosition.LAST
                    else -> me.rerere.rikkahub.ui.components.ui.ItemPosition.MIDDLE
                }
                
                LorebookSelectionCard(
                    lorebook = lorebook,
                    isEnabled = isEnabled,
                    position = position,
                    onClick = { navController.navigate(Screen.SettingLorebookDetail(lorebook.id.toString())) },
                    onToggle = { enabled ->
                        val newIds = if (enabled) {
                            assistant.enabledLorebookIds + lorebook.id
                        } else {
                            assistant.enabledLorebookIds - lorebook.id
                        }
                        onUpdate(assistant.copy(enabledLorebookIds = newIds))
                    }
                )
            }
        }
    }
}

@Composable
private fun LorebookSelectionCard(
    lorebook: Lorebook,
    isEnabled: Boolean,
    position: me.rerere.rikkahub.ui.components.ui.ItemPosition,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    // Calculate shape based on position for connected look (matching Lorebooks settings page)
    val cornerRadius = 28.dp
    val smallCorner = 8.dp
    val shape = when (position) {
        me.rerere.rikkahub.ui.components.ui.ItemPosition.ONLY -> RoundedCornerShape(cornerRadius)
        me.rerere.rikkahub.ui.components.ui.ItemPosition.FIRST -> RoundedCornerShape(
            topStart = cornerRadius, topEnd = cornerRadius,
            bottomStart = smallCorner, bottomEnd = smallCorner
        )
        me.rerere.rikkahub.ui.components.ui.ItemPosition.MIDDLE -> RoundedCornerShape(smallCorner)
        me.rerere.rikkahub.ui.components.ui.ItemPosition.LAST -> RoundedCornerShape(
            topStart = smallCorner, topEnd = smallCorner,
            bottomStart = cornerRadius, bottomEnd = cornerRadius
        )
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = shape,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book cover or placeholder
            val bookShape = when (position) {
                me.rerere.rikkahub.ui.components.ui.ItemPosition.ONLY -> RoundedCornerShape(
                    topStart = 16.dp, topEnd = 6.dp,
                    bottomStart = 16.dp, bottomEnd = 6.dp
                )
                me.rerere.rikkahub.ui.components.ui.ItemPosition.FIRST -> RoundedCornerShape(
                    topStart = 16.dp, topEnd = 6.dp,
                    bottomStart = 6.dp, bottomEnd = 6.dp
                )
                me.rerere.rikkahub.ui.components.ui.ItemPosition.MIDDLE -> RoundedCornerShape(6.dp)
                me.rerere.rikkahub.ui.components.ui.ItemPosition.LAST -> RoundedCornerShape(
                    topStart = 6.dp, topEnd = 6.dp,
                    bottomStart = 16.dp, bottomEnd = 6.dp
                )
            }
            Surface(
                shape = bookShape,
                color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(width = 50.dp, height = 70.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (val cover = lorebook.cover) {
                        is me.rerere.rikkahub.data.model.Avatar.Image -> {
                            coil3.compose.AsyncImage(
                                model = cover.url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        is me.rerere.rikkahub.data.model.Avatar.Emoji -> {
                            Text(
                                text = cover.content,
                                fontSize = 24.sp
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Rounded.Book,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Lorebook info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = lorebook.name.ifEmpty { stringResource(R.string.lorebooks_page_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lorebook.description.isNotEmpty()) {
                    Text(
                        text = lorebook.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(R.string.lorebooks_page_entries_count, lorebook.entries.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Toggle
            HapticSwitch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}
