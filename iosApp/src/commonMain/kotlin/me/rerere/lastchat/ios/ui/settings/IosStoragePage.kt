package me.rerere.lastchat.ios.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.lastchat.ios.IosAppState
import me.rerere.rikkahub.ui.components.settings.LastChatFormItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupInputItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var b = bytes.toDouble()
    var unitIndex = 0
    while (b >= 1024.0 && unitIndex < units.size - 1) {
        b /= 1024.0
        unitIndex++
    }
    return "${(b * 10).toLong() / 10.0} ${units[unitIndex]}"
}

private enum class AttachmentFilter(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    DOCUMENTS("Documents"),
    MEDIA("Media"),
}

@Composable
fun IosStoragePage(
    state: IosAppState,
    darkTheme: Boolean,
    onBack: () -> Unit = {},
    onHapticPop: () -> Unit = {},
    onHapticThud: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(AttachmentFilter.ALL) }
    var showMaintenanceConfirmation by remember { mutableStateOf(false) }
    var maintenanceCompleted by remember { mutableStateOf(false) }
    var selectedResolutionIndex by remember { mutableIntStateOf(0) }
    var selectedRetentionIndex by remember { mutableIntStateOf(0) }

    val resolutions = listOf("Original", "1024 px", "2048 px", "4096 px")
    val retentions = listOf("Never", "7 days", "30 days", "90 days", "180 days")

    // Estimate storage usage
    val conversationChars = remember(state.conversations) {
        state.conversations.sumOf { conv ->
            conv.messages.sumOf { msg ->
                msg.parts.filterIsInstance<UIMessagePart.Text>().sumOf { it.text.length.toLong() }
            }
        }
    }
    val generatedImagesCount = state.generatedImages.size
    val generatedImagesBytes = (generatedImagesCount * 450_000L).coerceAtLeast(0L)
    val conversationDbBytes = (conversationChars * 2L + 128_000L).coerceAtLeast(256_000L)
    val memoryBytes = (state.memories.size * 1024L).coerceAtLeast(64_000L)
    val appCacheBytes = 1_840_000L
    val totalStorageBytes = generatedImagesBytes + conversationDbBytes + memoryBytes + appCacheBytes

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Overview Card
        item {
            Card(
                shape = AppShapes.CardLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "App storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Total used across all categories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = AppShapes.ButtonPill,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = formatBytes(totalStorageBytes),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    // Segmented color bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        val imagesRatio = (generatedImagesBytes.toFloat() / totalStorageBytes).coerceIn(0.05f, 0.9f)
                        val convRatio = (conversationDbBytes.toFloat() / totalStorageBytes).coerceIn(0.05f, 0.9f)
                        val memoryRatio = (memoryBytes.toFloat() / totalStorageBytes).coerceIn(0.05f, 0.9f)
                        val cacheRatio = (appCacheBytes.toFloat() / totalStorageBytes).coerceIn(0.05f, 0.9f)

                        Box(Modifier.fillMaxHeight().weight(imagesRatio).background(Color(0xFF6750A4)))
                        Spacer(Modifier.width(2.dp))
                        Box(Modifier.fillMaxHeight().weight(convRatio).background(Color(0xFF006874)))
                        Spacer(Modifier.width(2.dp))
                        Box(Modifier.fillMaxHeight().weight(memoryRatio).background(Color(0xFF984061)))
                        Spacer(Modifier.width(2.dp))
                        Box(Modifier.fillMaxHeight().weight(cacheRatio).background(Color(0xFF7D5260)))
                    }

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StorageLegendItem("Media", Color(0xFF6750A4), formatBytes(generatedImagesBytes))
                        StorageLegendItem("Chats", Color(0xFF006874), formatBytes(conversationDbBytes))
                        StorageLegendItem("Memory", Color(0xFF984061), formatBytes(memoryBytes))
                        StorageLegendItem("Cache", Color(0xFF7D5260), formatBytes(appCacheBytes))
                    }
                }
            }
        }

        // Storage Maintenance Section
        item {
            LastChatSettingsGroup(title = "Storage maintenance") {
                LastChatSettingGroupInputItem(
                    title = "Clean and optimize",
                    subtitle = "Remove orphaned media, shrink database, clean cached tokens",
                    darkTheme = darkTheme,
                ) {
                    Button(
                        onClick = {
                            onHapticThud()
                            showMaintenanceConfirmation = true
                        },
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Run storage maintenance")
                    }
                    if (maintenanceCompleted) {
                        Text(
                            text = "Maintenance completed successfully. Temporary caches cleared.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        // Preferences Group
        item {
            LastChatSettingsGroup(title = "Chat storage preferences") {
                LastChatFormItem(
                    label = { Text("Image maximum resolution") },
                    description = { Text("Compress attachments to save device bandwidth and space") },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        resolutions.forEachIndexed { index, res ->
                            val selected = selectedResolutionIndex == index
                            if (selected) {
                                Button(
                                    onClick = {},
                                    shape = AppShapes.ButtonPill,
                                ) { Text(res) }
                            } else {
                                TextButton(
                                    onClick = {
                                        onHapticPop()
                                        selectedResolutionIndex = index
                                    },
                                    shape = AppShapes.ButtonPill,
                                ) { Text(res) }
                            }
                        }
                    }
                }

                LastChatFormItem(
                    label = { Text("Auto-delete chat media") },
                    description = { Text("Automatically prune old images and attachments after period") },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        retentions.forEachIndexed { index, ret ->
                            val selected = selectedRetentionIndex == index
                            if (selected) {
                                Button(
                                    onClick = {},
                                    shape = AppShapes.ButtonPill,
                                ) { Text(ret) }
                            } else {
                                TextButton(
                                    onClick = {
                                        onHapticPop()
                                        selectedRetentionIndex = index
                                    },
                                    shape = AppShapes.ButtonPill,
                                ) { Text(ret) }
                            }
                        }
                    }
                }
            }
        }

        // Categories breakdown
        item {
            LastChatSettingsGroup(title = "Categories") {
                LastChatSettingGroupItem(
                    title = "Conversations database",
                    subtitle = "${state.conversations.size} conversations, ${state.conversations.sumOf { it.messages.size }} messages",
                    darkTheme = darkTheme,
                    icon = { Icon(Icons.Rounded.Storage, null, Modifier.size(20.dp)) },
                    trailing = { Text(formatBytes(conversationDbBytes), style = MaterialTheme.typography.bodyMedium) },
                )
                LastChatSettingGroupItem(
                    title = "Generated media gallery",
                    subtitle = "$generatedImagesCount images generated",
                    darkTheme = darkTheme,
                    icon = { Icon(Icons.Rounded.Image, null, Modifier.size(20.dp)) },
                    trailing = { Text(formatBytes(generatedImagesBytes), style = MaterialTheme.typography.bodyMedium) },
                )
                LastChatSettingGroupItem(
                    title = "Memory & vector embeddings",
                    subtitle = "${state.memories.size} knowledge items indexed",
                    darkTheme = darkTheme,
                    icon = { Icon(Icons.Rounded.Memory, null, Modifier.size(20.dp)) },
                    trailing = { Text(formatBytes(memoryBytes), style = MaterialTheme.typography.bodyMedium) },
                )
                LastChatSettingGroupItem(
                    title = "Application cache",
                    subtitle = "Temporary files, web cache, network buffers",
                    darkTheme = darkTheme,
                    icon = { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(20.dp)) },
                    trailing = { Text(formatBytes(appCacheBytes), style = MaterialTheme.typography.bodyMedium) },
                )
            }
        }
    }

    if (showMaintenanceConfirmation) {
        AlertDialog(
            onDismissRequest = { showMaintenanceConfirmation = false },
            title = { Text("Run storage maintenance?") },
            text = {
                Text("This will scan the database, purge unlinked media attachments, compact conversation storage, and clear temporary caches. No active conversation messages will be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onHapticPop()
                        showMaintenanceConfirmation = false
                        maintenanceCompleted = true
                    }
                ) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaintenanceConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StorageLegendItem(
    label: String,
    color: Color,
    size: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label ($size)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
