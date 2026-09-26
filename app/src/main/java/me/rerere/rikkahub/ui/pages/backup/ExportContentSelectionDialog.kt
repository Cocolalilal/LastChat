package me.rerere.rikkahub.ui.pages.backup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ApiKeyEntry
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.sync.BackupContentOption
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.uuid.Uuid

private data class KeyWithProvider(
    val entry: ApiKeyEntry,
    val providerName: String,
)

private fun BackupContentOption.icon(): ImageVector = when (this) {
    BackupContentOption.CHARACTERS -> Icons.Rounded.SmartToy
    BackupContentOption.CHATS -> Icons.Rounded.Chat
    BackupContentOption.PROVIDERS -> Icons.Rounded.Hub
    BackupContentOption.API_KEYS -> Icons.Rounded.Key
    BackupContentOption.WORKSPACES -> Icons.Rounded.FolderZip
    BackupContentOption.SKILLS -> Icons.Rounded.Handyman
    BackupContentOption.LOREBOOKS -> Icons.Rounded.AutoStories
    BackupContentOption.SETTINGS -> Icons.Rounded.Settings
}

/**
 * Dialog to select which app contents to include when exporting backups.
 * By default, all items are checked so that no user data is lost.
 */
@Composable
fun ExportContentSelectionDialog(
    providers: List<ProviderSetting>,
    onDismiss: () -> Unit,
    onConfirm: (selectedContent: Set<BackupContentOption>, selectedKeyIds: Set<Uuid>) -> Unit,
    title: String = stringResource(R.string.backup_export_content_title),
    description: String = stringResource(R.string.backup_export_content_desc),
) {
    val haptics = rememberPremiumHaptics()

    val allKeys = remember(providers) {
        providers.flatMap { provider ->
            provider.apiKeyPool.map { entry ->
                KeyWithProvider(entry, provider.name.ifBlank { "Provider" })
            }
        }
    }

    // Default all content options to true (lossless export by default)
    val selectedContentMap = remember {
        mutableStateMapOf<BackupContentOption, Boolean>().apply {
            BackupContentOption.entries.forEach { put(it, true) }
        }
    }

    // Default all API keys to true
    val selectedKeyMap = remember(allKeys) {
        mutableStateMapOf<Uuid, Boolean>().apply {
            allKeys.forEach { put(it.entry.id, true) }
        }
    }

    var keysExpanded by remember { mutableStateOf(false) }

    val hasAnyContentSelected = selectedContentMap.values.any { it }
    val providersSelected = selectedContentMap[BackupContentOption.PROVIDERS] ?: false
    val apiKeysSelected = selectedContentMap[BackupContentOption.API_KEYS] ?: false

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Select All / Deselect All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            BackupContentOption.entries.forEach { selectedContentMap[it] = true }
                            allKeys.forEach { selectedKeyMap[it.entry.id] = true }
                        },
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.backup_export_select_all))
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            BackupContentOption.entries.forEach { selectedContentMap[it] = false }
                            allKeys.forEach { selectedKeyMap[it.entry.id] = false }
                        },
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.backup_export_deselect_all))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(BackupContentOption.entries, key = { it.name }) { option ->
                        val isChecked = selectedContentMap[option] ?: false
                        val isEnabled = if (option == BackupContentOption.API_KEYS) {
                            providersSelected
                        } else {
                            true
                        }

                        Surface(
                            shape = AppShapes.CardSmall,
                            color = if (isEnabled) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                            },
                            onClick = {
                                if (isEnabled) {
                                    haptics.perform(HapticPattern.Tick)
                                    val next = !isChecked
                                    selectedContentMap[option] = next
                                    if (option == BackupContentOption.PROVIDERS && !next) {
                                        selectedContentMap[BackupContentOption.API_KEYS] = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = option.icon(),
                                        contentDescription = null,
                                        tint = if (isEnabled && isChecked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(22.dp),
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(option.titleRes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isEnabled) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(option.descRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isEnabled) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    Checkbox(
                                        checked = isChecked && isEnabled,
                                        enabled = isEnabled,
                                        onCheckedChange = { checked ->
                                            haptics.perform(HapticPattern.Tick)
                                            selectedContentMap[option] = checked
                                            if (option == BackupContentOption.PROVIDERS && !checked) {
                                                selectedContentMap[BackupContentOption.API_KEYS] = false
                                            }
                                        },
                                    )
                                }

                                // Key customization accordion if API_KEYS is checked
                                if (option == BackupContentOption.API_KEYS && isChecked && isEnabled && allKeys.isNotEmpty()) {
                                    val selectedKeyCount = selectedKeyMap.filterValues { it }.size
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                haptics.perform(HapticPattern.Pop)
                                                keysExpanded = !keysExpanded
                                            }
                                            .padding(top = 4.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.backup_content_api_keys_customize,
                                                selectedKeyCount,
                                                allKeys.size,
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Icon(
                                            imageVector = if (keysExpanded) {
                                                Icons.Rounded.KeyboardArrowUp
                                            } else {
                                                Icons.Rounded.KeyboardArrowDown
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = keysExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            val grouped = allKeys.groupBy { it.providerName }
                                            grouped.forEach { (providerName, keys) ->
                                                Text(
                                                    text = providerName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(top = 4.dp),
                                                )
                                                keys.forEach { item ->
                                                    val keyChecked = selectedKeyMap[item.entry.id] ?: false
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                haptics.perform(HapticPattern.Tick)
                                                                selectedKeyMap[item.entry.id] = !keyChecked
                                                            }
                                                            .padding(vertical = 2.dp, horizontal = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Text(
                                                            text = item.entry.name.ifBlank { "API Key" },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        Checkbox(
                                                            checked = keyChecked,
                                                            onCheckedChange = { checked ->
                                                                haptics.perform(HapticPattern.Tick)
                                                                selectedKeyMap[item.entry.id] = checked
                                                            },
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
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
                    val selectedOptions = selectedContentMap.filterValues { it }.keys.toSet()
                    val selectedKeys = if (BackupContentOption.API_KEYS in selectedOptions) {
                        selectedKeyMap.filterValues { it }.keys.toSet()
                    } else {
                        emptySet()
                    }
                    onConfirm(selectedOptions, selectedKeys)
                },
                enabled = hasAnyContentSelected,
                shape = AppShapes.ButtonRounded,
            ) {
                Text(stringResource(R.string.backup_export_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = AppShapes.Dialog,
    )
}
