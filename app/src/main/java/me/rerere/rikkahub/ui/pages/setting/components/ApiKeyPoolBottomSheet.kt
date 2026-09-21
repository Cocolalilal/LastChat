package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ApiKeyEntry
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.withApiKeyPool
import me.rerere.ai.provider.withKeyPoolConfig
import me.rerere.ai.util.KeyRoulette
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

/**
 * Bottom sheet dialog displaying the list of pooled API keys for a provider.
 * Allows reordering (priority assignment), adding, editing, disabling, and deleting keys.
 */
@Composable
fun ApiKeyPoolBottomSheet(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    val roulette = remember { KeyRoulette.default() }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<ApiKeyEntry?>(null) }

    val pool = provider.apiKeyPool
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = pool.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onEdit(provider.withApiKeyPool(newList))
        haptics.perform(HapticPattern.Selection)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title and Plus Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.api_key_pool_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = provider.name.ifBlank { "Provider" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            showSettingsDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.api_key_pool_settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            showAddDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.api_key_pool_add_key),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            if (pool.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = stringResource(R.string.api_key_pool_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            showAddDialog = true
                        },
                        shape = AppShapes.ButtonPill,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.api_key_pool_add_key))
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(pool, key = { _, item -> item.id }) { index, entry ->
                        val health = roulette.getKeyHealth(entry.id)
                        val hasAuthError = health.hasAuthError || health.hasQuotaError

                        ReorderableItem(state = reorderableState, key = entry.id) { isDragging ->
                            Card(
                                onClick = {
                                    editingEntry = entry
                                },
                                shape = AppShapes.CardMedium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasAuthError) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ),
                                border = if (hasAuthError) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                                } else null,
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isDragging) 6.dp else 0.dp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // 1. Priority Circle (Left)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (hasAuthError) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (index + 1).toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasAuthError) {
                                                    MaterialTheme.colorScheme.onError
                                                } else {
                                                    MaterialTheme.colorScheme.onSecondary
                                                }
                                            )
                                        }
                                    }

                                    // 2. Middle Content (Name, Masked Key, Auth Warning)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = entry.name.ifBlank { "Key ${index + 1}" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (hasAuthError) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )

                                            if (!entry.exportable) {
                                                Surface(
                                                    shape = AppShapes.Tag,
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.padding(start = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Private",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }

                                        // Masked key preview
                                        val resolvedSecret = provider.resolvedApiKeyPool
                                            .find { it.id == entry.id }?.value
                                            ?: entry.key
                                        val maskedKey = when {
                                            resolvedSecret.length > 8 -> "••••" + resolvedSecret.takeLast(4)
                                            resolvedSecret.isNotBlank() -> "••••••••"
                                            else -> "••••••••"
                                        }

                                        Text(
                                            text = maskedKey,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (hasAuthError) {
                                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )

                                        if (hasAuthError) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Warning,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = stringResource(R.string.api_key_auth_error_warning),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    maxLines = 2,
                                                )
                                            }
                                        }
                                    }

                                    // 3. Actions: Enable Switch, Delete, Drag Handle
                                    HapticSwitch(
                                        checked = entry.enabled,
                                        onCheckedChange = { enabled ->
                                            val updated = pool.map {
                                                if (it.id == entry.id) it.copy(enabled = enabled) else it
                                            }
                                            onEdit(provider.withApiKeyPool(updated))
                                        }
                                    )

                                    IconButton(
                                        onClick = { deletingEntry = entry },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Drag handle
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .size(36.dp)
                                            .draggableHandle(
                                                onDragStarted = { haptics.perform(HapticPattern.Pop) },
                                                onDragStopped = { haptics.perform(HapticPattern.Thud) },
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Pool Settings Dialog
    if (showSettingsDialog) {
        ApiKeyPoolSettingsDialog(
            initialConfig = provider.keyPoolConfig,
            onConfirm = { newConfig ->
                onEdit(provider.withKeyPoolConfig(newConfig))
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false },
        )
    }

    // Add Key Dialog
    if (showAddDialog) {
        val defaultName = when (pool.size) {
            0 -> "Primary"
            1 -> "Secondary"
            2 -> "Tertiary"
            else -> "Key ${pool.size + 1}"
        }
        ApiKeyEditDialog(
            title = stringResource(R.string.api_key_pool_add_key),
            initialName = defaultName,
            initialKey = "",
            initialExportable = true,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, key, exportable ->
                val newEntry = ApiKeyEntry(
                    id = Uuid.random(),
                    name = name.ifBlank { defaultName },
                    enabled = true,
                    exportable = exportable,
                    key = key,
                )
                onEdit(provider.withApiKeyPool(pool + newEntry))
                showAddDialog = false
            }
        )
    }

    // Edit Key Dialog
    editingEntry?.let { entry ->
        val resolvedSecret = provider.resolvedApiKeyPool
            .find { it.id == entry.id }?.value
            ?: entry.key
        ApiKeyEditDialog(
            title = stringResource(R.string.api_key_pool_edit_key),
            initialName = entry.name,
            initialKey = resolvedSecret,
            initialExportable = entry.exportable,
            onDismiss = { editingEntry = null },
            onConfirm = { name, key, exportable ->
                val updatedPool = pool.map {
                    if (it.id == entry.id) {
                        it.copy(
                            name = name.ifBlank { entry.name },
                            exportable = exportable,
                            key = key,
                        )
                    } else it
                }
                onEdit(provider.withApiKeyPool(updatedPool))
                editingEntry = null
            }
        )
    }

    // Delete Confirmation Dialog
    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text(stringResource(R.string.api_key_pool_delete_confirm)) },
            text = { Text(entry.name.ifBlank { "API Key" }) },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedPool = pool.filter { it.id != entry.id }
                        onEdit(provider.withApiKeyPool(updatedPool))
                        deletingEntry = null
                    },
                    shape = AppShapes.ButtonRounded
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = AppShapes.Dialog
        )
    }
}

@Composable
private fun ApiKeyEditDialog(
    title: String,
    initialName: String,
    initialKey: String,
    initialExportable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, key: String, exportable: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var key by remember { mutableStateOf(initialKey) }
    var exportable by remember { mutableStateOf(initialExportable) }
    val haptics = rememberPremiumHaptics()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.api_key_pool_key_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.InputField,
                    singleLine = true,
                )

                SecureOutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = stringResource(R.string.api_key_pool_key_value),
                    modifier = Modifier.fillMaxWidth(),
                    singleLineWhenHidden = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.api_key_pool_exportable),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Allow exporting in backups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HapticSwitch(
                        checked = exportable,
                        onCheckedChange = { exportable = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (key.isNotBlank()) {
                        haptics.perform(HapticPattern.Pop)
                        onConfirm(name, key, exportable)
                    }
                },
                enabled = key.isNotBlank(),
                shape = AppShapes.ButtonRounded,
            ) {
                Text(stringResource(R.string.confirm))
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
