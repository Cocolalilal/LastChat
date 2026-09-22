package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.util.KeyRoulette
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * Pill-shaped surface button for managing provider API key pool.
 * Uses surfaceContainerHigh to match list cards, with white icons/text in dark mode,
 * and a right-aligned circle matching the parent surface color displaying the key count.
 */
@Composable
fun ApiKeyPoolButton(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPremiumHaptics()
    val isDark = LocalDarkMode.current
    var showSheet by remember { mutableStateOf(false) }

    val roulette = KeyRoulette.default()
    val pool = provider.apiKeyPool
    val hasKeys = pool.isNotEmpty()
    val hasAuthError = pool.any { entry -> 
        val h = roulette.getKeyHealth(entry.id)
        h.hasAuthError || h.hasQuotaError 
    }

    val containerColor by animateColorAsState(
        targetValue = when {
            hasAuthError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "ApiKeyPoolButtonColor"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            hasAuthError -> MaterialTheme.colorScheme.error
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "ApiKeyPoolButtonIconColor"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            hasAuthError -> MaterialTheme.colorScheme.onErrorContainer
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "ApiKeyPoolButtonTextColor"
    )

    val circleColor by animateColorAsState(
        targetValue = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "ApiKeyPoolButtonCircleColor"
    )

    val circleTextColor by animateColorAsState(
        targetValue = when {
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "ApiKeyPoolButtonCircleTextColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = AppShapes.ButtonPill,
        color = containerColor,
        border = if (hasAuthError) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else null,
        onClick = {
            haptics.perform(HapticPattern.Pop)
            showSheet = true
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left content: Key / Add icon and button label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (hasKeys) Icons.Rounded.Key else Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = iconColor,
                )
                Text(
                    text = if (hasKeys) {
                        stringResource(R.string.api_key_pool_title)
                    } else {
                        stringResource(R.string.api_key_pool_add_key)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
            }

            // Right content: Auth error indicator and key count circle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasAuthError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Auth Error",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (hasKeys) {
                    Surface(
                        shape = CircleShape,
                        color = circleColor,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = pool.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = circleTextColor,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ApiKeyPoolBottomSheet(
            provider = provider,
            onEdit = onEdit,
            onDismiss = { showSheet = false },
        )
    }
}
