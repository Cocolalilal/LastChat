package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.KeyPoolConfig
import me.rerere.ai.provider.KeyPoolStrategy
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.math.roundToInt

@Composable
fun ApiKeyPoolSettingsDialog(
    initialConfig: KeyPoolConfig,
    onConfirm: (KeyPoolConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    var selectedStrategy by remember { mutableStateOf(initialConfig.strategy) }
    var speculativeEnabled by remember { mutableStateOf(initialConfig.enableSpeculativeRouting) }
    var speculativeTimeout by remember { mutableFloatStateOf(initialConfig.speculativeTimeoutSeconds.toFloat().coerceIn(2f, 30f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.Dialog,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.api_key_pool_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Strategy Section
                Text(
                    text = stringResource(R.string.api_key_pool_strategy_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrategyOptionCard(
                        title = stringResource(R.string.api_key_pool_strategy_sticky),
                        description = stringResource(R.string.api_key_pool_strategy_sticky_desc),
                        selected = selectedStrategy == KeyPoolStrategy.STICKY_UNTIL_FAILURE,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            selectedStrategy = KeyPoolStrategy.STICKY_UNTIL_FAILURE
                        },
                    )

                    StrategyOptionCard(
                        title = stringResource(R.string.api_key_pool_strategy_priority),
                        description = stringResource(R.string.api_key_pool_strategy_priority_desc),
                        selected = selectedStrategy == KeyPoolStrategy.PRIORITY_FAILOVER,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            selectedStrategy = KeyPoolStrategy.PRIORITY_FAILOVER
                        },
                    )

                    StrategyOptionCard(
                        title = stringResource(R.string.api_key_pool_strategy_round_robin),
                        description = stringResource(R.string.api_key_pool_strategy_round_robin_desc),
                        selected = selectedStrategy == KeyPoolStrategy.ROUND_ROBIN,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            selectedStrategy = KeyPoolStrategy.ROUND_ROBIN
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Speculative Hedged Racing Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.api_key_pool_speculative_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.api_key_pool_speculative_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            HapticSwitch(
                                checked = speculativeEnabled,
                                onCheckedChange = {
                                    haptics.perform(HapticPattern.Pop)
                                    speculativeEnabled = it
                                },
                            )
                        }

                        AnimatedVisibility(
                            visible = speculativeEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.api_key_pool_speculative_timeout,
                                        speculativeTimeout.roundToInt(),
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                                Slider(
                                    value = speculativeTimeout,
                                    onValueChange = {
                                        speculativeTimeout = it
                                    },
                                    valueRange = 2f..30f,
                                    steps = 27,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    onConfirm(
                        KeyPoolConfig(
                            strategy = selectedStrategy,
                            enableSpeculativeRouting = speculativeEnabled,
                            speculativeTimeoutSeconds = speculativeTimeout.roundToInt(),
                        )
                    )
                },
                shape = AppShapes.ButtonPill,
            ) {
                Text(stringResource(R.string.setting_provider_page_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = AppShapes.ButtonPill,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun StrategyOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.CardSmall)
            .clickable(onClick = onClick),
        shape = AppShapes.CardSmall,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
