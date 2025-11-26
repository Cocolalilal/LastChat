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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LightbulbCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    reasoningTokens: Int,
    shape: Shape = RoundedCornerShape(24.dp),
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
        onDismissRequest = {
            onDismissRequest()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReasoningLevelCard(
                selected = currentLevel == ReasoningLevel.OFF,
                icon = {
                    Icon(Icons.Rounded.LightbulbCircle, null)
                },
                title = {
                    Text(stringResource(id = R.string.reasoning_off))
                },
                description = {
                    Text(stringResource(id = R.string.reasoning_off_desc))
                },
                onClick = {
                    onUpdateReasoningTokens(0)
                },
                containerColor = if (LocalDarkMode.current) Color.Black else Color.White
            )
            ReasoningLevelCard(
                selected = currentLevel == ReasoningLevel.AUTO,
                icon = {
                    Icon(Icons.Rounded.AutoAwesome, null)
                },
                title = {
                    Text(stringResource(id = R.string.reasoning_auto))
                },
                description = {
                    Text(stringResource(id = R.string.reasoning_auto_desc))
                },
                onClick = {
                    onUpdateReasoningTokens(-1)
                },
                containerColor = if (LocalDarkMode.current) Color.Black else Color.White
            )
            
            val amoledMode by rememberAmoledDarkMode()
            val isDarkMode = LocalDarkMode.current
            val isAmoled = amoledMode && isDarkMode
            
            val containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
            val contentColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
            val elevation = if (isAmoled) 0.dp else 6.dp
            val tonalElevation = if (isAmoled) 0.dp else LocalAbsoluteTonalElevation.current
            
            CompositionLocalProvider(LocalAbsoluteTonalElevation provides tonalElevation) {
                val cardElevation = CardDefaults.cardElevation(defaultElevation = elevation)
                val cardColors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                )
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = cardElevation,
                    colors = cardColors
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ReasoningLevelCard(
                            selected = currentLevel == ReasoningLevel.LOW,
                            icon = {
                                Icon(Icons.Rounded.Lightbulb, null)
                            },
                            title = {
                                Text(stringResource(id = R.string.reasoning_light))
                            },
                            description = {
                                Text(stringResource(id = R.string.reasoning_light_desc))
                            },
                            onClick = {
                                onUpdateReasoningTokens(1024)
                            },
                            shape = RoundedCornerShape(0.dp),
                            containerColor = containerColor
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ReasoningLevelCard(
                            selected = currentLevel == ReasoningLevel.MEDIUM,
                            icon = {
                                Icon(Icons.Rounded.Lightbulb, null)
                            },
                            title = {
                                Text(stringResource(id = R.string.reasoning_medium))
                            },
                            description = {
                                Text(stringResource(id = R.string.reasoning_medium_desc))
                            },
                            onClick = {
                                onUpdateReasoningTokens(16_000)
                            },
                            shape = RoundedCornerShape(0.dp),
                            containerColor = containerColor
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ReasoningLevelCard(
                            selected = currentLevel == ReasoningLevel.HIGH,
                            icon = {
                                Icon(Icons.Rounded.Lightbulb, null)
                            },
                            title = {
                                Text(stringResource(id = R.string.reasoning_heavy))
                            },
                            description = {
                                Text(stringResource(id = R.string.reasoning_heavy_desc))
                            },
                            onClick = {
                                onUpdateReasoningTokens(32_000)
                            },
                            shape = RoundedCornerShape(0.dp),
                            containerColor = containerColor
                        )
                    }
                }
            }
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
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color? = null
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    
    val defaultContainerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
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
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
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
