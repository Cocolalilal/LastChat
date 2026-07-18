package me.rerere.rikkahub.ui.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class LastChatSettingsPaneEntry(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val description: String? = null,
    val children: List<LastChatSettingsPaneEntry> = emptyList(),
)

data class LastChatSettingsPaneGroup(
    val id: String,
    val title: String,
    val entries: List<LastChatSettingsPaneEntry>,
)

private const val SettingsPanePressMillis = 80
private const val SettingsPaneFadeMillis = 90
private const val SettingsPaneShapeMillis = 120
private const val SettingsPaneExpandMillis = 140

private val SettingsPaneItemOuterRadius = 24.dp
private val SettingsPaneItemInnerRadius = 8.dp
private val SettingsPaneChildOuterRadius = 20.dp
private val SettingsPaneChildInnerRadius = 8.dp

/** Android's production 336 dp adaptive Settings pane, shared verbatim with iOS. */
@Composable
fun LastChatSettingsNavigationPane(
    groups: List<LastChatSettingsPaneGroup>,
    selectedId: String,
    selectedMainId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onHaptic: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Settings",
    listState: LazyListState = rememberLazyListState(),
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(336.dp)
            .statusBarsPadding()
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            onHaptic()
                            onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            groups.forEach { group ->
                item(key = group.id) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                        )
                        SettingsPaneSection(
                            group = group,
                            selectedId = selectedId,
                            selectedMainId = selectedMainId,
                            onHaptic = onHaptic,
                            onNavigate = onNavigate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPaneSection(
    group: LastChatSettingsPaneGroup,
    selectedId: String,
    selectedMainId: String,
    onHaptic: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val expandedEntry = group.entries.firstOrNull { entry ->
        selectedMainId == entry.id && entry.children.isNotEmpty()
    }
    val hasExpandedEntry = expandedEntry != null

    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(
            animationSpec = tween(SettingsPaneExpandMillis, easing = FastOutSlowInEasing),
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        group.entries.forEachIndexed { index, entry ->
            val expanded = entry == expandedEntry
            val groupedWithSection = !hasExpandedEntry
            val topRadius = when {
                expanded -> SettingsPaneItemOuterRadius
                groupedWithSection && index == 0 -> SettingsPaneItemOuterRadius
                groupedWithSection -> SettingsPaneItemInnerRadius
                else -> SettingsPaneItemOuterRadius
            }
            val bottomRadius = when {
                expanded -> SettingsPaneItemInnerRadius
                groupedWithSection && index == group.entries.lastIndex -> SettingsPaneItemOuterRadius
                groupedWithSection -> SettingsPaneItemInnerRadius
                else -> SettingsPaneItemOuterRadius
            }
            val itemPadding by animateDpAsState(
                targetValue = if (hasExpandedEntry && !expanded) 8.dp else 0.dp,
                animationSpec = tween(SettingsPaneShapeMillis, easing = FastOutSlowInEasing),
                label = "settings_pane_section_item_padding",
            )
            SettingsPaneEntryGroup(
                entry = entry,
                selectedId = selectedId,
                expanded = expanded,
                topRadius = topRadius,
                bottomRadius = bottomRadius,
                verticalPadding = itemPadding,
                onHaptic = onHaptic,
                onNavigate = onNavigate,
            )
        }
    }
}

@Composable
private fun SettingsPaneEntryGroup(
    entry: LastChatSettingsPaneEntry,
    selectedId: String,
    expanded: Boolean,
    topRadius: Dp,
    bottomRadius: Dp,
    verticalPadding: Dp,
    onHaptic: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val groupPadding by animateDpAsState(
        targetValue = if (expanded) 4.dp else verticalPadding,
        animationSpec = tween(SettingsPaneShapeMillis, easing = FastOutSlowInEasing),
        label = "settings_pane_entry_group_padding",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = groupPadding.coerceAtLeast(0.dp) / 2)
            .animateContentSize(
                animationSpec = tween(SettingsPaneExpandMillis, easing = FastOutSlowInEasing),
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsPaneItem(
            entry = entry,
            selected = selectedId == entry.id,
            expanded = expanded,
            showDescription = false,
            isChild = false,
            topRadius = topRadius,
            bottomRadius = bottomRadius,
            onHaptic = onHaptic,
            onClick = { onNavigate(entry.id) },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(
                tween(SettingsPaneFadeMillis, easing = LinearOutSlowInEasing),
            ) + expandVertically(
                tween(SettingsPaneExpandMillis, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(tween(SettingsPaneFadeMillis)) + shrinkVertically(
                tween(SettingsPaneExpandMillis, easing = FastOutSlowInEasing),
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entry.children.forEachIndexed { index, child ->
                    SettingsPaneItem(
                        entry = child,
                        selected = selectedId == child.id,
                        expanded = false,
                        showDescription = false,
                        isChild = true,
                        topRadius = SettingsPaneChildInnerRadius,
                        bottomRadius = if (index == entry.children.lastIndex) {
                            SettingsPaneChildOuterRadius
                        } else {
                            SettingsPaneChildInnerRadius
                        },
                        onHaptic = onHaptic,
                        onClick = { onNavigate(child.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPaneItem(
    entry: LastChatSettingsPaneEntry,
    selected: Boolean,
    expanded: Boolean,
    showDescription: Boolean,
    isChild: Boolean,
    topRadius: Dp,
    bottomRadius: Dp,
    onHaptic: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(SettingsPanePressMillis, easing = FastOutSlowInEasing),
        label = "settings_pane_item_scale",
    )
    val animatedTopRadius by animateDpAsState(
        targetValue = topRadius,
        animationSpec = tween(SettingsPaneShapeMillis, easing = FastOutSlowInEasing),
        label = "settings_pane_item_top_radius",
    )
    val animatedBottomRadius by animateDpAsState(
        targetValue = bottomRadius,
        animationSpec = tween(SettingsPaneShapeMillis, easing = FastOutSlowInEasing),
        label = "settings_pane_item_bottom_radius",
    )
    val itemHeight by animateDpAsState(
        targetValue = when {
            showDescription -> 78.dp
            isChild -> 48.dp
            else -> 58.dp
        },
        animationSpec = tween(SettingsPaneShapeMillis, easing = FastOutSlowInEasing),
        label = "settings_pane_item_height",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        },
        animationSpec = tween(SettingsPaneShapeMillis, easing = FastOutSlowInEasing),
        label = "settings_pane_item_container_color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(SettingsPaneShapeMillis, easing = FastOutSlowInEasing),
        label = "settings_pane_item_content_color",
    )

    Surface(
        onClick = {
            if (!selected || entry.children.isNotEmpty()) {
                onHaptic()
                onClick()
            }
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(
            topStart = animatedTopRadius,
            topEnd = animatedTopRadius,
            bottomStart = animatedBottomRadius,
            bottomEnd = animatedBottomRadius,
        ),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier
                .height(itemHeight)
                .padding(horizontal = if (isChild) 16.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(if (isChild) 28.dp else 34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(entry.icon, null, modifier = Modifier.size(if (isChild) 16.dp else 19.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = if (isChild) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showDescription && entry.description != null) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.children.isNotEmpty() && expanded) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}
