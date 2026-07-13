package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.model.MemoryConversionDirection
import me.rerere.rikkahub.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryConversionPanel(vm: AssistantDetailVM) {
    val available by vm.conversionAvailable.collectAsStateWithLifecycle()
    val busy by vm.conversionBusy.collectAsStateWithLifecycle()
    val error by vm.conversionError.collectAsStateWithLifecycle()
    val preview by vm.conversionPreview.collectAsStateWithLifecycle()

    if (available) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Surface(
                onClick = { if (!busy) vm.generateConversionPreview() },
                shape = AppShapes.CardLarge,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("New memories are available", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Review what changed in the other memory system and choose what to bring over.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (busy) CircularProgressIndicator() else Icon(Icons.Rounded.ChevronRight, "Review")
                }
            }
        }
    }

    preview?.let { current ->
        ModalBottomSheet(
            onDismissRequest = { if (!busy) vm.cancelConversionPreview() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Review memory suggestions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    current.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current.direction == MemoryConversionDirection.ENTRY_TO_DOCUMENT) {
                    current.userProfileReplacement?.let {
                        ProposedChangeCard("User Profile", it)
                    }
                    current.characterMemoryReplacement?.let {
                        ProposedChangeCard("Character Memory", it)
                    }
                } else if (current.entryProposals.isEmpty()) {
                    Text("No new entry changes were found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    current.entryProposals.take(20).forEach { proposal ->
                        ProposedChangeCard(if (proposal.existingEntryId == null) "New memory" else "Updated memory", proposal.content)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::applyConversionPreview, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Add these memories")
                }
                TextButton(onClick = vm::cancelConversionPreview, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Not now")
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ProposedChangeCard(title: String, content: String) {
    Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(content, maxLines = 7, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
