package me.rerere.rikkahub.ui.pages.setting

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_IN_WORLD_ARTIFACTS_PROMPT
import me.rerere.rikkahub.data.repository.ArtifactStorageManager
import me.rerere.rikkahub.data.repository.SavedArtifact
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.richtext.ArtifactZoomDialog
import me.rerere.rikkahub.ui.components.richtext.InWorldArtifact
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

private const val SAMPLE_ARTIFACT_CODE = """
<div style="max-width:320px;margin:10px auto;font-family:Georgia,serif;">
  <div style="background:linear-gradient(135deg,#0a0a0c 0%,#16161a 100%);border:1px solid #3a2a1a;border-radius:12px;padding:16px 18px;box-shadow:0 12px 28px rgba(0,0,0,.7);color:#d8b56a;">
    <div style="font-size:9px;letter-spacing:3px;color:#8a6b3a;">HOTEL NOCTURNE</div>
    <div style="font-size:20px;font-weight:bold;margin-top:2px;">VELVET</div>
    <div style="font-size:16px;color:#c1122c;font-weight:bold;margin-top:12px;text-align:right;">ROOM 69</div>
  </div>
</div>
"""

@Composable
fun SettingArtifactsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings.displaySetting) { mutableStateOf(settings.displaySetting) }

    fun updateDisplay(mutate: (me.rerere.rikkahub.data.datastore.DisplaySetting) -> me.rerere.rikkahub.data.datastore.DisplaySetting) {
        val updated = mutate(displaySetting)
        displaySetting = updated
        vm.updateSettings { current -> current.copy(displaySetting = updated) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_in_world_artifacts_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = lazyListState,
            contentPadding = contentPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Group 1: General Toggles
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Master Toggle
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (displaySetting.enableInWorldArtifacts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = "Enable In-World Artifacts",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "Render self-contained physical objects (notes, letters, phone screens, receipts) inside chat.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                HapticSwitch(
                                    checked = displaySetting.enableInWorldArtifacts,
                                    onCheckedChange = { enabled ->
                                        updateDisplay { it.copy(enableInWorldArtifacts = enabled) }
                                    }
                                )
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                        )

                        // Bubble Background Toggle
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (displaySetting.showArtifactBubble) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = "Message Bubble Background",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = if (displaySetting.showArtifactBubble) {
                                        "Enabled: Artifacts are housed inside the message bubble card."
                                    } else {
                                        "Disabled: Frameless — artifacts float cleanly on the canvas without enclosing bubble borders."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                HapticSwitch(
                                    checked = displaySetting.showArtifactBubble,
                                    onCheckedChange = { showBubble ->
                                        updateDisplay { it.copy(showArtifactBubble = showBubble) }
                                    }
                                )
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                        )

                        // Orb Animation Toggle
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = if (displaySetting.showArtifactOrb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = "Thinking Orb Animation",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "Display 3D orbital particle thought orb while the in-world visual is being generated.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                HapticSwitch(
                                    checked = displaySetting.showArtifactOrb,
                                    onCheckedChange = { showOrb ->
                                        updateDisplay { it.copy(showArtifactOrb = showOrb) }
                                    }
                                )
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                        )

                        // Auto-Save Artifacts Toggle
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = if (displaySetting.autoSaveArtifactsToStorage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.artifact_auto_save_title),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.artifact_auto_save_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                HapticSwitch(
                                    checked = displaySetting.autoSaveArtifactsToStorage,
                                    onCheckedChange = { autoSave ->
                                        updateDisplay { it.copy(autoSaveArtifactsToStorage = autoSave) }
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Group: Browse Artifacts
            item {
                var savedArtifacts by remember { mutableStateOf<List<SavedArtifact>>(emptyList()) }
                var showBrowserSheet by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    savedArtifacts = ArtifactStorageManager.getSavedArtifacts(context)
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge,
                    onClick = { showBrowserSheet = true }
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.artifact_browse_title),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        supportingContent = {
                            val count = savedArtifacts.size
                            val totalBytes = ArtifactStorageManager.getTotalSizeBytes(context)
                            Text(
                                text = if (count > 0) "$count saved (${totalBytes.fileSizeToString()}) • Tap to browse gallery"
                                else stringResource(R.string.artifact_browse_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                if (showBrowserSheet) {
                    ArtifactBrowserModalSheet(
                        artifacts = savedArtifacts,
                        onDismissRequest = {
                            coroutineScope.launch {
                                savedArtifacts = ArtifactStorageManager.getSavedArtifacts(context)
                            }
                            showBrowserSheet = false
                        },
                        onArtifactDeleted = { deletedId ->
                            coroutineScope.launch {
                                ArtifactStorageManager.deleteArtifact(context, deletedId)
                                savedArtifacts = ArtifactStorageManager.getSavedArtifacts(context)
                            }
                        },
                        onClearAll = {
                            coroutineScope.launch {
                                ArtifactStorageManager.clearAll(context)
                                savedArtifacts = emptyList()
                            }
                        }
                    )
                }
            }

            // Group 2: System Prompt Instructions
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    var promptText by remember(displaySetting.inWorldArtifactsPrompt) {
                        mutableStateOf(displaySetting.inWorldArtifactsPrompt.ifBlank { DEFAULT_IN_WORLD_ARTIFACTS_PROMPT })
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "System Prompt Instructions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = {
                                    promptText = DEFAULT_IN_WORLD_ARTIFACTS_PROMPT
                                    updateDisplay { it.copy(inWorldArtifactsPrompt = "") }
                                }
                            ) {
                                Text("Reset to Default")
                            }
                        }

                        Text(
                            text = "Instructions sent to the AI when generating artifacts. Distinguishes real in-world items from standard code blocks (HTML, SVG, Python).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { newPrompt ->
                                promptText = newPrompt
                                updateDisplay { it.copy(inWorldArtifactsPrompt = newPrompt) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 320.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = AppShapes.InputField,
                            placeholder = { Text(DEFAULT_IN_WORLD_ARTIFACTS_PROMPT) }
                        )
                    }
                }
            }

            // Group 3: Live Preview
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Appearance Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (displaySetting.showArtifactBubble) {
                                "Previewing inside standard message bubble card:"
                            } else {
                                "Previewing frameless (floating directly on chat canvas):"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (displaySetting.showArtifactBubble) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                    shape = AppShapes.CardLarge
                                )
                                .padding(if (displaySetting.showArtifactBubble) 12.dp else 0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            InWorldArtifact(
                                code = SAMPLE_ARTIFACT_CODE.trim(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactBrowserModalSheet(
    artifacts: List<SavedArtifact>,
    onDismissRequest: () -> Unit,
    onArtifactDeleted: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val haptics = rememberPremiumHaptics()
    var inspectedArtifact by remember { mutableStateOf<SavedArtifact?>(null) }
    var inspectedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showConfirmClear by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = AppShapes.BottomSheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.artifact_browse_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${artifacts.size} saved artifacts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (artifacts.isNotEmpty()) {
                    TextButton(
                        onClick = { showConfirmClear = true }
                    ) {
                        Text(
                            stringResource(R.string.artifact_clear_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (artifacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.artifact_empty_gallery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(artifacts, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            shape = AppShapes.CardMedium,
                            onClick = {
                                val bmp = item.imageFile?.let { f ->
                                    runCatching {
                                        BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                                    }.getOrNull()
                                }
                                inspectedBitmap = bmp
                                inspectedArtifact = item
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bmp = remember(item.id) {
                                    item.imageFile?.let { f ->
                                        runCatching {
                                            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                                        }.getOrNull()
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.sizeBytes.fileSizeToString() + " • " +
                                                java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                                    .format(java.util.Date(item.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Thud)
                                        onArtifactDeleted(item.id)
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (inspectedArtifact != null) {
        val code = remember(inspectedArtifact) {
            runCatching { inspectedArtifact!!.htmlFile.readText() }.getOrDefault("")
        }
        ArtifactZoomDialog(
            code = code,
            cachedBitmap = inspectedBitmap,
            onDismissRequest = { inspectedArtifact = null }
        )
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text(stringResource(R.string.artifact_clear_all)) },
            text = { Text("Are you sure you want to delete all cached in-world artifacts from storage?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmClear = false
                        onClearAll()
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
