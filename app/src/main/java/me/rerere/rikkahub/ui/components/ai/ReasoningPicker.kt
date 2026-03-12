package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LightbulbCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.groupedItemRadii
import me.rerere.rikkahub.ui.theme.placedSurfaceColor

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    reasoningTokens: Int,
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
    onUpdateReasoningTokens: (Int) -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var showReasoningPicker by remember { mutableStateOf(false) }

    if (showReasoningPicker) {
        ReasoningPicker(
            reasoningTokens = reasoningTokens,
            onDismissRequest = { showReasoningPicker = false },
            onUpdateReasoningTokens = onUpdateReasoningTokens
        )
    }

    ToggleSurface(
        checked = ReasoningLevel.fromBudgetTokens(reasoningTokens).isEnabled,
        checkedColor = Color.Transparent,
        uncheckedColor = Color.Transparent,
        contentColor = contentColor,
        onClick = {
            showReasoningPicker = true
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = if (onlyIcon) 8.dp else 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                )
            }
            if (!onlyIcon) Text(stringResource(R.string.setting_provider_page_reasoning))
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningTokens: Int,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningTokens: (Int) -> Unit,
) {
    val currentLevel = ReasoningLevel.fromBudgetTokens(reasoningTokens)

    ModalBottomSheet(
        containerColor = placedSurfaceColor(),
        onDismissRequest = {
            onDismissRequest()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // Group 1: OFF and AUTO (2 items)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReasoningOptionItem(
                    selected = currentLevel == ReasoningLevel.OFF,
                    icon = { Icon(Icons.Rounded.LightbulbCircle, null, modifier = Modifier.size(20.dp)) },
                    title = stringResource(id = R.string.reasoning_off),
                    subtitle = stringResource(id = R.string.reasoning_off_desc),
                    onClick = { onUpdateReasoningTokens(0) },
                    position = ItemPosition.FIRST,
                )
                ReasoningOptionItem(
                    selected = currentLevel == ReasoningLevel.AUTO,
                    icon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(20.dp)) },
                    title = stringResource(id = R.string.reasoning_auto),
                    subtitle = stringResource(id = R.string.reasoning_auto_desc),
                    onClick = { onUpdateReasoningTokens(-1) },
                    position = ItemPosition.LAST,
                )
            }
            
            // Spacer between groups
            Spacer(modifier = Modifier.height(16.dp))
            
            // Group 2: LOW, MEDIUM, HIGH (3 items)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReasoningOptionItem(
                    selected = currentLevel == ReasoningLevel.LOW,
                    icon = { Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(20.dp)) },
                    title = stringResource(id = R.string.reasoning_light),
                    subtitle = stringResource(id = R.string.reasoning_light_desc),
                    onClick = { onUpdateReasoningTokens(1024) },
                    position = ItemPosition.FIRST,
                )
                ReasoningOptionItem(
                    selected = currentLevel == ReasoningLevel.MEDIUM,
                    icon = { Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(20.dp)) },
                    title = stringResource(id = R.string.reasoning_medium),
                    subtitle = stringResource(id = R.string.reasoning_medium_desc),
                    onClick = { onUpdateReasoningTokens(16_000) },
                    position = ItemPosition.MIDDLE,
                )
                ReasoningOptionItem(
                    selected = currentLevel == ReasoningLevel.HIGH,
                    icon = { Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(20.dp)) },
                    title = stringResource(id = R.string.reasoning_heavy),
                    subtitle = stringResource(id = R.string.reasoning_heavy_desc),
                    onClick = { onUpdateReasoningTokens(32_000) },
                    position = ItemPosition.LAST,
                )
            }
        }
    }
}

// Matches SettingGroupItem layout exactly but with selection support
@Composable
private fun ReasoningOptionItem(
    selected: Boolean,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: ItemPosition = ItemPosition.ONLY,
) {
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()

    val targetRadii = groupedItemRadii(position = position, selected = selected)
    val topCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetRadii.topStart,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "topCorner"
    )
    val bottomCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetRadii.bottomStart,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "bottomCorner"
    )
    
    val itemShape = RoundedCornerShape(
        topStart = topCorner, topEnd = topCorner,
        bottomStart = bottomCorner, bottomEnd = bottomCorner
    )
    
    // Use Row with clip+background like ModelItem does (Surface may apply M3 color transformations)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else placedSurfaceColor()
            )
            .clickable {
                haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReasoningLevelCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: @Composable () -> Unit = {},
    title: @Composable () -> Unit = {},
    description: @Composable () -> Unit = {},
    onClick: () -> Unit,
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
    containerColor: Color? = null
) {
    val defaultContainerColor = placedSurfaceColor()
    val resolvedContainerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else (containerColor ?: defaultContainerColor)

    val defaultContentColor = MaterialTheme.colorScheme.onSurface
    val resolvedContentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else defaultContentColor

    val elevation = 0.dp
    val tonalElevation = 0.dp

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides tonalElevation) {
        val cardElevation = CardDefaults.cardElevation(defaultElevation = elevation)
        val cardColors = CardDefaults.cardColors(
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor
        )
        val resolvedShape = if (selected) AppShapes.GroupedSelected else RoundedCornerShape(AppShapes.GroupedInnerRadius)
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = resolvedShape,
            elevation = cardElevation,
            colors = cardColors
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                        title()
                    }
                    ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                        description()
                    }
                }
            }
        }

    }
}

@Composable
@Preview(showBackground = true)
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var reasoningTokens by remember { mutableIntStateOf(0) }
        ReasoningPicker(
            onDismissRequest = {},
            reasoningTokens = reasoningTokens,
            onUpdateReasoningTokens = {
                reasoningTokens = it
            }
        )
    }
}


