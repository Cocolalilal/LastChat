package me.rerere.lastchat.ios.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.common.platform.PlatformPickedFile
import me.rerere.common.platform.PlatformPickedFileKind
import me.rerere.rikkahub.ui.components.chat.LastChatComposerActionButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAction
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAddButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAttachmentRow
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAudioIcon
import me.rerere.rikkahub.ui.components.chat.LastChatComposerCapsule
import me.rerere.rikkahub.ui.components.chat.LastChatComposerImageAttachment
import me.rerere.rikkahub.ui.components.chat.LastChatComposerMediaAttachment
import me.rerere.rikkahub.ui.components.chat.LastChatComposerRow
import me.rerere.rikkahub.ui.components.chat.LastChatComposerVideoIcon
import me.rerere.rikkahub.ui.components.chat.LastChatDocumentAttachmentTile
import me.rerere.rikkahub.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosChatComposer(
    inputState: TextFieldState,
    action: LastChatComposerAction,
    onActionClick: () -> Unit,
    onPickFile: () -> Unit,
    pendingAttachments: List<PlatformPickedFile>,
    onRemoveAttachment: (String) -> Unit,
    modelName: String,
    providerName: String,
    searchEnabled: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    placeholderText: String = "Message",
    onHapticPop: () -> Unit = {},
    modifier: Modifier = Modifier,
    actionContent: @Composable (LastChatComposerAction) -> Unit,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LastChatComposerRow(modifier = modifier) {
        // The clean 48dp "+" button
        LastChatComposerAddButton(
            onClick = {
                onHapticPop()
                showBottomSheet = true
            },
            onLongClick = {
                onHapticPop()
                onPickFile()
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Options and Attachments",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        // The clean input capsule
        LastChatComposerCapsule {
            Column(Modifier.fillMaxWidth()) {
                if (pendingAttachments.isNotEmpty()) {
                    val orderedAttachments = remember(pendingAttachments) {
                        PlatformPickedFileKind.entries.flatMap { kind ->
                            pendingAttachments.filter { it.kind == kind }
                        }
                    }
                    LastChatComposerAttachmentRow {
                        items(
                            items = orderedAttachments,
                            key = { it.storagePath },
                        ) { attachment ->
                            val remove = {
                                onHapticPop()
                                onRemoveAttachment(attachment.storagePath)
                            }
                            when (attachment.kind) {
                                PlatformPickedFileKind.Image -> {
                                    LastChatComposerImageAttachment(
                                        onClick = {},
                                        onRemove = remove,
                                        removeContentDescription = "Remove attachment",
                                    ) {
                                        AsyncImage(
                                            model = attachment.localUrl,
                                            contentDescription = attachment.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                PlatformPickedFileKind.Video -> {
                                    LastChatComposerMediaAttachment(onRemove = remove) {
                                        LastChatComposerVideoIcon()
                                    }
                                }
                                PlatformPickedFileKind.Audio -> {
                                    LastChatComposerMediaAttachment(onRemove = remove) {
                                        LastChatComposerAudioIcon()
                                    }
                                }
                                PlatformPickedFileKind.Document -> {
                                    LastChatDocumentAttachmentTile(
                                        fileName = attachment.displayName,
                                        modifier = Modifier.size(60.dp),
                                        onRemove = remove,
                                    )
                                }
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    TextField(
                        state = inputState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        placeholder = {
                            Text(
                                text = placeholderText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        },
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 12.dp,
                            end = 48.dp,
                            bottom = 12.dp,
                        ),
                    )

                    LastChatComposerActionButton(
                        action = action,
                        onClick = onActionClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(36.dp),
                        content = actionContent,
                    )
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Actions & Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 1. Attach File / Photo Card
                Card(
                    onClick = {
                        onHapticPop()
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onPickFile()
                        }
                    },
                    shape = AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Attach Photo, Video or File",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Send images, documents, or media to the conversation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 2. Active Model Card
                Card(
                    onClick = {
                        onHapticPop()
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onOpenSettings()
                        }
                    },
                    shape = AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                modelName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Provider: $providerName · Tap to change",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 3. Web Search Toggle Card
                Card(
                    shape = AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (searchEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = if (searchEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Web Search",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (searchEnabled) "Search is enabled for this chat" else "Search is disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = searchEnabled,
                            onCheckedChange = {
                                onHapticPop()
                                onToggleSearch(it)
                            },
                        )
                    }
                }
            }
        }
    }
}
