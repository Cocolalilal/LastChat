package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.util.KeyRoulette
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes

/**
 * Pill-shaped surface button for managing provider API key pool.
 * Shows key count, add button when empty, and auth error badge if any key is unhealthy.
 */
@Composable
fun ApiKeyPoolButton(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPremiumHaptics()
    var showSheet by remember { mutableStateOf(false) }

    val pool = provider.apiKeyPool
    val hasKeys = pool.isNotEmpty()
    val hasAuthError = pool.any { entry -> 
        val h = roulette.getKeyHealth(entry.id)
        h.hasAuthError || h.hasQuotaError 
    }

    val containerColor by animateColorAsState(
        targetValue = when {
            hasAuthError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            hasKeys -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "ApiKeyPoolButtonColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            hasAuthError -> MaterialTheme.colorScheme.onErrorContainer
            hasKeys -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "ApiKeyPoolButtonContentColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = AppShapes.ButtonPill,
        color = containerColor,
        contentColor = contentColor,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (hasKeys) Icons.Rounded.Key else Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = if (hasKeys) {
                        stringResource(R.string.api_key_pool_count, pool.size)
                    } else {
                        stringResource(R.string.api_key_pool_add_key)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (hasAuthError) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Auth Error",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
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
