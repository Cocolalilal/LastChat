package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Stable
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
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.memory.MemoryActivityInterest
import me.rerere.rikkahub.data.memory.MemoryGraphRepository
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.compose.koinInject

/**
 * The redesigned in-chat memory cue (§7.5): a small, subtle "ball" that pops in only when something
 * *interesting* happened for the active conversation (new memories, merges, promotions,
 * contradictions — NOT routine reinforcement/housekeeping, per [MemoryActivityInterest]). It has the
 * memory hue (secondaryContainer) but is far less obtrusive than the old center popup. Tapping opens
 * a bottom sheet listing the events with deep links into the memory pages.
 */
@Stable
class MemoryActivityBallState(
    val freshRows: List<MemoryActivityEntity>,
    val acknowledge: () -> Unit,
)

@Composable
fun rememberMemoryActivityBallState(
    assistantId: String,
    conversationId: String,
): MemoryActivityBallState {
    val repository = koinInject<MemoryGraphRepository>()
    val activityFlow = remember(assistantId) { repository.observeActivity(assistantId, limit = 40) }
    val activity by activityFlow.collectAsStateWithLifecycle(emptyList())

    var baseline by remember(conversationId) { mutableStateOf(System.currentTimeMillis()) }

    val fresh = remember(activity, baseline, conversationId) {
        activity.filter {
            it.at > baseline &&
                it.conversationId == conversationId &&
                MemoryActivityInterest.isInteresting(it.kind, it.summary)
        }
    }
    return remember(fresh) {
        MemoryActivityBallState(
            freshRows = fresh,
            acknowledge = { baseline = System.currentTimeMillis() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryActivityBall(
    state: MemoryActivityBallState,
    onOpenMemory: (focusNodeId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPremiumHaptics()
    var showSheet by remember { mutableStateOf(false) }
    val visible = state.freshRows.isNotEmpty() && !showSheet

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f), initialScale = 0.4f) + fadeIn(),
        exit = scaleOut(targetScale = 0.4f) + fadeOut(),
        modifier = modifier,
    ) {
        // 24dp touch target around an 11dp dot.
        Surface(
            onClick = { haptics.perform(HapticPattern.Pop); showSheet = true },
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(11.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.25f), CircleShape)
                )
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                state.acknowledge()
                showSheet = false
            },
            sheetState = sheetState,
            shape = AppShapes.BottomSheet,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Memory updated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.freshRows, key = { it.id }) { row ->
                        Surface(
                            onClick = { onOpenMemory(firstNodeId(row.nodeIds)) },
                            shape = AppShapes.CardLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
