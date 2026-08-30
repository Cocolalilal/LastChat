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
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ReasoningConfig
import me.rerere.ai.provider.ReasoningModeType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton

fun resolveReasoningConfig(model: Model?): ReasoningConfig {
    val modelConfig = model?.reasoningConfig
    if (modelConfig != null) return modelConfig
    val modelId = model?.modelId?.lowercase() ?: ""

    return when {
        // Claude series -> Budget
        modelId.contains("claude") -> ReasoningConfig(
            type = ReasoningModeType.BUDGET,
            minTokens = 1024,
            maxTokens = 64_000,
            presetTokens = listOf(1024, 4096, 8192, 16_000, 32_000, 64_000)
        )
        // Gemini 2.5 -> Budget (0..65536)
        modelId.contains("gemini-2.5") -> ReasoningConfig(
            type = ReasoningModeType.BUDGET,
            minTokens = 0,
            maxTokens = 65_536,
            presetTokens = listOf(0, 1024, 4096, 8192, 16_000, 32_000)
        )
        // DashScope Qwen -> Budget
        modelId.contains(Regex("qwen.*(max|plus|turbo|3\\.|2\\.5)")) -> ReasoningConfig(
            type = ReasoningModeType.BUDGET,
            minTokens = 1024,
            maxTokens = 32_768,
            presetTokens = listOf(1024, 4096, 8192, 16_000, 32_000)
        )
        // DeepSeek, InternLM, Grok, Doubao Ark, GLM, Hunyuan -> Binary
        modelId.contains(Regex("deepseek|intern|grok|doubao|glm|hunyuan")) -> ReasoningConfig(
            type = ReasoningModeType.BINARY,
            supportedLevels = listOf("off", "auto")
        )
        // OpenAI o-series / gpt-5 / Gemini 3 -> Effort
        modelId.contains(Regex("o[134]|gpt-5|gemini-3")) -> ReasoningConfig(
            type = ReasoningModeType.EFFORT,
            supportedLevels = listOf("off", "auto", "low", "medium", "high", "max")
        )
        // Default backward compatible effort
        else -> ReasoningConfig(
            type = ReasoningModeType.EFFORT,
            supportedLevels = listOf("off", "auto", "low", "medium", "high")
        )
    }
}

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    model: Model? = null,
    onlyIcon: Boolean = false,
    reasoningTokens: Int,
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
    onUpdateReasoningTokens: (Int) -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var showReasoningPicker by remember { mutableStateOf(false) }

    if (showReasoningPicker) {
        ReasoningPicker(
            model = model,
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
    model: Model? = null,
    reasoningTokens: Int,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningTokens: (Int) -> Unit,
) {
    val config = remember(model) { resolveReasoningConfig(model) }
    val currentLevel = ReasoningLevel.fromBudgetTokens(reasoningTokens)
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    
    ModalBottomSheet(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
            when (config.type) {
                ReasoningModeType.BINARY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReasoningOptionItem(
                            selected = currentLevel == ReasoningLevel.OFF,
                            icon = { Icon(Icons.Rounded.LightbulbCircle, null, modifier = Modifier.size(20.dp)) },
                            title = stringResource(id = R.string.reasoning_off),
                            subtitle = stringResource(id = R.string.reasoning_off_desc),
                            onClick = { onUpdateReasoningTokens(0) },
                            position = ItemPosition.FIRST,
                            isAmoled = isAmoled
                        )
                        ReasoningOptionItem(
                            selected = currentLevel != ReasoningLevel.OFF,
                            icon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(20.dp)) },
                            title = stringResource(id = R.string.reasoning_auto),
                            subtitle = stringResource(id = R.string.reasoning_auto_desc),
                            onClick = { onUpdateReasoningTokens(-1) },
                            position = ItemPosition.LAST,
                            isAmoled = isAmoled
                        )
                    }
                }

                ReasoningModeType.EFFORT -> {
                    val supported = config.supportedLevels.ifEmpty { listOf("off", "auto", "low", "medium", "high", "max") }
                    val showOff = supported.contains("off")
                    val showAuto = supported.contains("auto")

                    if (showOff || showAuto) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (showOff) {
                                ReasoningOptionItem(
                                    selected = currentLevel == ReasoningLevel.OFF,
                                    icon = { Icon(Icons.Rounded.LightbulbCircle, null, modifier = Modifier.size(20.dp)) },
                                    title = stringResource(id = R.string.reasoning_off),
                                    subtitle = stringResource(id = R.string.reasoning_off_desc),
                                    onClick = { onUpdateReasoningTokens(0) },
                                    position = if (showAuto) ItemPosition.FIRST else ItemPosition.SINGLE,
                                    isAmoled = isAmoled
                                )
                            }
                            if (showAuto) {
                                ReasoningOptionItem(
                                    selected = currentLevel == ReasoningLevel.AUTO,
                                    icon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(20.dp)) },
                                    title = stringResource(id = R.string.reasoning_auto),
                                    subtitle = stringResource(id = R.string.reasoning_auto_desc),
                                    onClick = { onUpdateReasoningTokens(-1) },
                                    position = if (showOff) ItemPosition.LAST else ItemPosition.SINGLE,
                                    isAmoled = isAmoled
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    data class EffortOption(
                        val level: ReasoningLevel,
                        val tokens: Int,
                        val title: String,
                        val desc: String,
                    )

                    val effortOptions = buildList {
                        if (supported.contains("low")) {
                            add(EffortOption(ReasoningLevel.LOW, 1024, stringResource(R.string.reasoning_light), stringResource(R.string.reasoning_light_desc)))
                        }
                        if (supported.contains("medium")) {
                            add(EffortOption(ReasoningLevel.MEDIUM, 16_000, stringResource(R.string.reasoning_medium), stringResource(R.string.reasoning_medium_desc)))
                        }
                        if (supported.contains("high")) {
                            add(EffortOption(ReasoningLevel.HIGH, 32_000, stringResource(R.string.reasoning_heavy), stringResource(R.string.reasoning_heavy_desc)))
                        }
                        if (supported.contains("max") || supported.contains("xhigh")) {
                            add(EffortOption(ReasoningLevel.MAX, 64_000, "Max", "Maximum reasoning effort"))
                        }
                    }

                    if (effortOptions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            effortOptions.forEachIndexed { index, option ->
                                val position = when {
                                    effortOptions.size == 1 -> ItemPosition.SINGLE
                                    index == 0 -> ItemPosition.FIRST
                                    index == effortOptions.size - 1 -> ItemPosition.LAST
                                    else -> ItemPosition.MIDDLE
                                }
                                ReasoningOptionItem(
                                    selected = currentLevel == option.level,
                                    icon = { Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(20.dp)) },
                                    title = option.title,
                                    subtitle = option.desc,
                                    onClick = { onUpdateReasoningTokens(option.tokens) },
                                    position = position,
                                    isAmoled = isAmoled
                                )
                            }
                        }
                    }
                }

                ReasoningModeType.BUDGET -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReasoningOptionItem(
                            selected = currentLevel == ReasoningLevel.OFF,
                            icon = { Icon(Icons.Rounded.LightbulbCircle, null, modifier = Modifier.size(20.dp)) },
                            title = stringResource(id = R.string.reasoning_off),
                            subtitle = stringResource(id = R.string.reasoning_off_desc),
                            onClick = { onUpdateReasoningTokens(0) },
                            position = if (config.minTokens == 0) ItemPosition.FIRST else ItemPosition.SINGLE,
                            isAmoled = isAmoled
                        )
                        if (config.minTokens == 0) {
                            ReasoningOptionItem(
                                selected = currentLevel == ReasoningLevel.AUTO,
                                icon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(20.dp)) },
                                title = stringResource(id = R.string.reasoning_auto),
                                subtitle = stringResource(id = R.string.reasoning_auto_desc),
                                onClick = { onUpdateReasoningTokens(-1) },
                                position = ItemPosition.LAST,
                                isAmoled = isAmoled
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.reasoning_custom),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    val presets = config.presetTokens.filter { it in config.minTokens..config.maxTokens }
                    if (presets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            presets.forEachIndexed { index, tokens ->
                                val isSelected = reasoningTokens == tokens
                                val position = when {
                                    presets.size == 1 -> ItemPosition.SINGLE
                                    index == 0 -> ItemPosition.FIRST
                                    index == presets.size - 1 -> ItemPosition.LAST
                                    else -> ItemPosition.MIDDLE
                                }
                                val formattedTokens = if (tokens >= 1000) "${tokens / 1000}K tokens" else "$tokens tokens"
                                ReasoningOptionItem(
                                    selected = isSelected,
                                    icon = { Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(20.dp)) },
                                    title = formattedTokens,
                                    subtitle = stringResource(R.string.assistant_page_thinking_budget),
                                    onClick = { onUpdateReasoningTokens(tokens) },
                                    position = position,
                                    isAmoled = isAmoled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Position in a group for determining corner radius
private enum class ItemPosition {
    FIRST,   // Top rounded (24dp top, 10dp bottom)
    MIDDLE,  // All corners 10dp
    LAST,    // Bottom rounded (10dp top, 24dp bottom)
    SINGLE   // All corners 24dp (only item in group)
}

// Matches SettingGroupItem layout exactly but with selection support
@Composable
private fun ReasoningOptionItem(
    selected: Boolean,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: ItemPosition = ItemPosition.SINGLE,
    isAmoled: Boolean  // Only use Color.Black when actually in OLED mode
) {
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    
    // Animated corner radius - selected items animate to fully round
    val topCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) 50.dp else when (position) {
            ItemPosition.FIRST, ItemPosition.SINGLE -> 24.dp
            else -> 10.dp
        },
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "topCorner"
    )
    val bottomCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) 50.dp else when (position) {
            ItemPosition.LAST, ItemPosition.SINGLE -> 24.dp
            else -> 10.dp
        },
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
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
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
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    
    val defaultContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val resolvedContainerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else (containerColor ?: defaultContainerColor)
    
    val defaultContentColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
    val resolvedContentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else defaultContentColor
    
    val elevation = if (isAmoled) 0.dp else 6.dp
    val tonalElevation = if (isAmoled) 0.dp else LocalAbsoluteTonalElevation.current

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides tonalElevation) {
        val cardElevation = CardDefaults.cardElevation(defaultElevation = elevation)
        val cardColors = CardDefaults.cardColors(
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor
        )
        // Selected items are completely round, non-selected use 10.dp corners
        val resolvedShape = if (selected) RoundedCornerShape(50) else RoundedCornerShape(10.dp)
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
