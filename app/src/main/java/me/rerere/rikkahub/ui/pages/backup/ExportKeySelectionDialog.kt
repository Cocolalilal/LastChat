package me.rerere.rikkahub.ui.pages.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ApiKeyEntry
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.uuid.Uuid

/**
 * Dialog to select which API keys to include when exporting backups.
 */
@Composable
fun ExportKeySelectionDialog(
    providers: List<ProviderSetting>,
    onDismiss: () -> Unit,
    onConfirm: (selectedKeyIds: Set<Uuid>) -> Unit,
) {
    val haptics = rememberPremiumHaptics()

    // Collect all keys with their provider info
    data class KeyWithProvider(
        val entry: ApiKeyEntry,
        val providerName: String,
    )

    val allKeys = remember(providers) {
        providers.flatMap { provider ->
            provider.apiKeyPool.map { entry ->
                KeyWithProvider(entry, provider.name.ifBlank { "Provider" })
            }
        }
    }

    // Selected state map keyed by entry.id, initialized to entry.exportable
    val selectedMap = remember(allKeys) {
        mutableStateMapOf<Uuid, Boolean>().apply {
            allKeys.forEach { put(it.entry.id, it.entry.exportable) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.backup_export_select_keys_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_export_select_keys_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (allKeys.isEmpty()) {
                    Text(
                        text = stringResource(R.string.backup_export_no_keys),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    // Select All / Deselect All Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                allKeys.forEach { selectedMap[it.entry.id] = true }
                            },
                            shape = AppShapes.ButtonPill,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.backup_export_select_all))
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                allKeys.forEach { selectedMap[it.entry.id] = false }
                            },
                            shape = AppShapes.ButtonPill,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.backup_export_deselect_all))
                        }
                    }

                    // Key List grouped by provider
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val grouped = allKeys.groupBy { it.providerName }
                        grouped.forEach { (providerName, keys) ->
                            item(key = "header_$providerName") {
                                Text(
                                    text = providerName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }

                            items(keys, key = { it.entry.id }) { item ->
                                val isChecked = selectedMap[item.entry.id] ?: false
                                Surface(
                                    shape = AppShapes.CardSmall,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    onClick = {
                                        haptics.perform(HapticPattern.Tick)
                                        selectedMap[item.entry.id] = !isChecked
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.entry.name.ifBlank { "API Key" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!item.entry.exportable) {
                                                Text(
                                                    text = "Marked private by default",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                haptics.perform(HapticPattern.Tick)
                                                selectedMap[item.entry.id] = checked
                                            }
                                        )
                                    }
                                }
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
                    val selectedIds = selectedMap.filterValues { it }.keys.toSet()
                    onConfirm(selectedIds)
                },
                shape = AppShapes.ButtonRounded
            ) {
                Text(stringResource(R.string.backup_export_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = AppShapes.Dialog
    )
}
