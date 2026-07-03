package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.decodeFromString
import me.rerere.ai.util.fuzzyMemoryAgeLabel
import me.rerere.rikkahub.data.memory.MemoryGraphRepository
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.compose.koinInject

/**
 * The §7.5 "🧠 N memories updated" pill: a non-blocking, in-chat cue that extraction/sleep ran for
 * the active conversation. Tapping opens a bottom sheet listing the fresh activity rows with links
 * into the Memory Center. Never a dialog, never interrupts streaming — it observes the append-only
 * activity feed and simply appears when new rows arrive.
 *
 * [onOpenMemory] receives an optional node id so a tapped row deep-links straight to that node sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryUpdatePill(
    assistantId: String,
    conversationId: String,
    modifier: Modifier = Modifier,
    onOpenMemory: (focusNodeId: String?) -> Unit,
) {
    val repository = koinInject<MemoryGraphRepository>()
    val activityFlow = remember(assistantId) { repository.observeActivity(assistantId, limit = 40) }
    val activity by activityFlow.collectAsStateWithLifecycle(emptyList())

    // Only surface rows created after the pill mounted for this conversation, until dismissed.
    var baseline by remember(conversationId) { mutableStateOf(System.currentTimeMillis()) }
    var showSheet by remember { mutableStateOf(false) }

    val fresh = remember(activity, baseline, conversationId) {
        activity.filter { it.at > baseline && it.conversationId == conversationId }
    }

    AnimatedVisibility(
        visible = fresh.isNotEmpty() && !showSheet,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Surface(
            onClick = { showSheet = true },
            shape = AppShapes.ButtonPill,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                Text(
                    "${fresh.size} ${if (fresh.size == 1) "memory" else "memories"} updated",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                // Dismissing acknowledges these updates: advance the baseline so the pill hides.
                baseline = System.currentTimeMillis()
                showSheet = false
            },
            sheetState = sheetState,
            shape = AppShapes.BottomSheet,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Memory updated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fresh, key = { it.id }) { row ->
                        Surface(
                            onClick = { onOpenMemory(firstNodeId(row.nodeIds)) },
                            shape = AppShapes.CardSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(row.summary.ifBlank { "memory updated" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(fuzzyMemoryAgeLabel(row.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun firstNodeId(json: String): String? = runCatching {
    me.rerere.rikkahub.utils.JsonInstant.decodeFromString<List<String>>(json).firstOrNull()
}.getOrNull()
