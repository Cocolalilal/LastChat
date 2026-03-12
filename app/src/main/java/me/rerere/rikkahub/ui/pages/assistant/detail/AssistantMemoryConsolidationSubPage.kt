package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Psychology
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.utils.toLocalString

@Composable
fun AssistantMemoryConsolidationSubPage(
    vm: AssistantDetailVM,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    onConsolidate: (Boolean) -> Unit
) {
    val episodes: List<ChatEpisodeEntity> by vm.episodes.collectAsStateWithLifecycle(initialValue = emptyList())
    val stats by vm.episodeStats.collectAsStateWithLifecycle()
    val snackbarMessage: String? by vm.snackbarMessage.collectAsStateWithLifecycle(initialValue = null)

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Settings Card
        item {
            Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                colors = CardDefaults.cardColors(
                    containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Memory Consolidation Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }


                    // Enable Memory Consolidation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Memory Consolidation",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Allow character to form long-term memories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HapticSwitch(
                            checked = assistant.enableMemory,
                            onCheckedChange = { onUpdate(assistant.copy(enableMemory = it)) }
                        )
                    }

                    if (assistant.enableMemory) {
                        // Consolidation Delay
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Consolidation Delay: ${assistant.consolidationDelayMinutes} minutes",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Wait time after a chat ends before consolidating it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.material3.Slider(
                                value = assistant.consolidationDelayMinutes.toFloat(),
                                onValueChange = { 
                                    onUpdate(assistant.copy(consolidationDelayMinutes = it.toInt())) 
                                },
                                valueRange = 0f..240f, // 0 to 4 hours
                                steps = 23 // 10 min steps approx
                            )
                        }
                    }
                }
            }
        }

        if (assistant.enableMemory) {
            // Status Card
            item {
                Card(
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = "Memory Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
    

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                label = "Core Memories",
                                value = stats.coreMemoryCount.toString()
                            )
                            StatItem(
                                label = "Episodic Memories",
                                value = stats.totalEpisodes.toString()
                            )
                        }
                        
                        // Detailed Run Stats
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Recent Activity",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Track A Stats
                            Column {
                                Text(
                                    text = "Consolidation (Track A)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (assistant.lastConsolidationTime > 0) {
                                    val time = java.time.Instant.ofEpochMilli(assistant.lastConsolidationTime)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime()
                                        .toLocalString()
                                    Text(
                                        text = "Last Run: $time",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Result: ${assistant.lastConsolidationResult}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text("No run recorded yet", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Button(
                            onClick = { onConsolidate(true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Psychology, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Consolidate All Memories Now")
                        }
                        
                        if (snackbarMessage != null && snackbarMessage!!.contains("consolidation")) {
                            Text(
                                text = snackbarMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Episodes List Section
            item {
                Text(
                    text = "Recent Memory Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            item {
                if (episodes.isNotEmpty()) {
                    Text(
                        text = "${episodes.count()} total episodes recorded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
