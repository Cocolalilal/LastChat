package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemReality
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.model.MemoryNodeTypeCodec
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.theme.AppShapes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Shared building blocks for the memory pages (character page, browse, graph, node sheet).

/** Dashed outline used by the "Provisional" chip — Compose has no dashed border modifier. */
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

/**
 * Container/on-container color pair for a node type chip. Matched to the mockups: Episode is the
 * teal (secondary) chip, entities purple-ish (tertiary), frames primary, facts neutral. The pair
 * keeps the text readable on every container (never onSecondaryContainer over another container —
 * that's what produced black-on-dark text).
 */
@Composable
fun nodeTypeChipColors(type: Int): Pair<Color, Color> = when (type) {
    MemNodeType.EPISODE, MemNodeType.GIST ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    MemNodeType.ENTITY, MemNodeType.HABIT ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    MemNodeType.FRAME, MemNodeType.GOAL ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    else -> // FACT and anything unknown
        MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
}

/** One pill-shaped tag, sized like the mockups' chip row. */
@Composable
fun MemoryPill(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(shape = AppShapes.Tag, color = container, modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = content,
        )
    }
}

@Composable
fun TypeChip(type: Int) {
    val label = MemoryNodeTypeCodec.toString(type).lowercase().replaceFirstChar { it.uppercase() }
    val (container, content) = nodeTypeChipColors(type)
    MemoryPill(label, container, content)
}

/** "Literal" for real-world memories, "Roleplay" for fiction — the sketch's second (gray) chip. */
@Composable
fun RealityChip(reality: Int) {
    val roleplay = reality == MemReality.FICTION
    MemoryPill(
        label = if (roleplay) "Roleplay" else "Literal",
        container = if (roleplay) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        content = if (roleplay) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Dashed-outline "( Provisional )" chip per the sketch — only shown while unconfirmed. */
@Composable
fun ProvisionalChip() {
    Box(
        modifier = Modifier.dashedBorder(MaterialTheme.colorScheme.outline, cornerRadius = 50.dp),
    ) {
        Text(
            "Provisional",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SmallTag(text: String) {
    MemoryPill(text, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
fun StatusLabel(status: Int) {
    val (label, color) = when (status) {
        MemStatus.ACTIVE -> "active" to MaterialTheme.colorScheme.primary
        MemStatus.PROVISIONAL -> "provisional" to MaterialTheme.colorScheme.tertiary
        MemStatus.DORMANT -> "dormant" to MaterialTheme.colorScheme.onSurfaceVariant
        MemStatus.SUPERSEDED -> "superseded" to MaterialTheme.colorScheme.onSurfaceVariant
        MemStatus.CLOSED -> "closed" to MaterialTheme.colorScheme.onSurfaceVariant
        MemStatus.FORGOTTEN -> "forgotten" to MaterialTheme.colorScheme.error
        else -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
}

/** Accent-colored section title ("Memories", "Activity", "Suggestions") per the mockups. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The mockups' "Memories" graph container: near-black fill with a subtle outline ring, large
 * corner radius. Used by both the minimized preview and the node sheet's focused mini-graph.
 */
@Composable
fun GraphCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = AppShapes.CardLarge
    val color = MaterialTheme.colorScheme.surface
    val border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = border, modifier = modifier) { content() }
    } else {
        Surface(shape = shape, color = color, border = border, modifier = modifier) { content() }
    }
}

/**
 * Threshold for switching a memory's text from bold-title styling to plain body styling — long
 * memories set in bold read like a wall of shouting; short ones are titles.
 */
const val MEMORY_TITLE_MAX_CHARS = 80

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Mockup stat card: large rounded square, big accent number over a quiet lowercase label. */
@Composable
fun StatCard(modifier: Modifier, value: String, label: String, color: Color) {
    Surface(modifier = modifier, shape = AppShapes.CardLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

fun edgeTypeLabel(type: Int): String = when (type) {
    MemEdgeType.ABOUT -> "about"
    MemEdgeType.SUPERSEDES -> "supersedes"
    MemEdgeType.CONTRADICTS -> "contradicts"
    MemEdgeType.INSTANCE_OF -> "instance of"
    MemEdgeType.DERIVED_FROM -> "derived from"
    MemEdgeType.IN_FRAME -> "in frame"
    else -> "relates to"
}

fun activityIcon(kind: String) = when {
    kind.startsWith("GOAL") -> Icons.Rounded.AutoAwesome
    kind == "MERGED" || kind == "COMPRESSED" -> Icons.Rounded.Nightlight
    kind == "FORGOTTEN" || kind == "DECAYED" || kind == "EVICTED" -> Icons.Rounded.History
    else -> Icons.Rounded.AutoAwesome
}

fun prettyKind(kind: String): String = kind.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

/** Day divider label: "Today", "Yesterday", or "Wed, Jul 1". */
fun dayKey(millis: Long, now: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = cal.get(Calendar.YEAR) to cal.get(Calendar.DAY_OF_YEAR)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = cal.get(Calendar.YEAR) to cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = millis
    val day = cal.get(Calendar.YEAR) to cal.get(Calendar.DAY_OF_YEAR)
    return when (day) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> dayFormat.format(Date(millis))
    }
}

fun parseFirstNodeId(json: String): String? = runCatching {
    me.rerere.rikkahub.utils.JsonInstant.decodeFromString<List<String>>(json).firstOrNull()
}.getOrNull()

/** Live (browsable, non-forgotten) subset used for the stat cards. */
fun liveMemoryNodes(all: List<MemoryNodeEntity>): List<MemoryNodeEntity> =
    all.filter {
        it.status == MemStatus.ACTIVE || it.status == MemStatus.PROVISIONAL ||
            it.status == MemStatus.DORMANT || it.status == MemStatus.CLOSED
    }

// ─────────────────────────────── Dialogs ───────────────────────────────

@Composable
fun AddMemoryDialog(onDismiss: () -> Unit, onConfirm: (String, Int, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var importance by remember { mutableIntStateOf(3) }
    var pinned by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth(), placeholder = { Text("A fact to remember") })
                FormItem(label = { Text("Importance: $importance") }) {
                    Slider(value = importance.toFloat(), onValueChange = { importance = it.toInt() }, valueRange = 1f..5f, steps = 3)
                }
                FormItem(label = { Text("Pin") }, description = { Text("Exempt from automatic forgetting") }, tail = { HapticSwitch(checked = pinned, onCheckedChange = { pinned = it }) })
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text, importance, pinned) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WipeConfirmSheet(isGlobal: Boolean, characterName: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val expected = if (isGlobal) "everything" else (characterName ?: "everything")
    var typed by remember { mutableStateOf("") }
    var enableCountdown by remember { mutableIntStateOf(3) }
    val matched = typed.trim().equals(expected, ignoreCase = true)
    LaunchedEffect(matched) {
        if (matched) {
            enableCountdown = 3
            while (enableCountdown > 0) { delay(1000); enableCountdown-- }
        } else enableCountdown = 3
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = AppShapes.BottomSheet) {
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
                Text("Erase memory", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                if (isGlobal) "This permanently erases ALL memory across every character. This cannot be undone."
                else "This permanently erases everything ${characterName ?: "this character"} remembers. The shared layer is kept. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Type “$expected” to confirm") })
            Button(
                onClick = onConfirm,
                enabled = matched && enableCountdown == 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(if (matched && enableCountdown > 0) "Erase in $enableCountdown…" else "Erase permanently")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
