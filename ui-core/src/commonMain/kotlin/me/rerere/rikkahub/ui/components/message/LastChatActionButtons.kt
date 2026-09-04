package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Bottom action buttons displayed beneath message turns (Copy, Regenerate, Delete, Branch, More).
 * Shared across Android and iOS in Compose Multiplatform.
 */
@Composable
fun LastChatActionRow(
    messageText: String,
    modifier: Modifier = Modifier,
    isAssistant: Boolean = true,
    branchCurrentIndex: Int = 0,
    branchTotalCount: Int = 1,
    onSelectBranch: ((Int) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    isSpeakingTts: Boolean = false,
    onToggleTts: (() -> Unit)? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    var showActionSheet by remember { mutableStateOf(false) }
    var isPendingDelete by remember { mutableStateOf(false) }
    var spinDegrees by remember { mutableStateOf(0f) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000)
            isPendingDelete = false
        }
    }

    val rotation by animateFloatAsState(
        targetValue = spinDegrees,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "regen-spin"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Branch Version Selector (< 1 / 3 >)
            if (branchTotalCount > 1 && onSelectBranch != null) {
                LastChatBranchSelector(
                    currentIndex = branchCurrentIndex,
                    totalVersions = branchTotalCount,
                    onSelectVersion = onSelectBranch,
                )
            }

            // Copy Action Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(messageText))
                    }
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy message",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp),
                )
            }

            // Regenerate Action Button (for assistant turns)
            if (isAssistant && onRegenerate != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            spinDegrees += 360f
                            onRegenerate()
                        }
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Regenerate",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp).rotate(rotation),
                    )
                }
            }

            // TTS Speech Action Button
            if (isAssistant && onToggleTts != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onToggleTts() }
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = if (isSpeakingTts) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Read aloud",
                        tint = if (isSpeakingTts) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // Delete Action Button (with 3-second confirm)
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (isPendingDelete) {
                                onDelete()
                                isPendingDelete = false
                            } else {
                                isPendingDelete = true
                            }
                        }
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = if (isPendingDelete) "Confirm delete" else "Delete",
                        tint = if (isPendingDelete) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // More Options (...) Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { showActionSheet = true }
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "More actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }

    // Modal Action Bottom Sheet
    if (showActionSheet) {
        LastChatActionsSheet(
            onDismiss = { showActionSheet = false },
            onCopy = {
                clipboardManager.setText(AnnotatedString(messageText))
                showActionSheet = false
            },
            onEdit = onEdit?.let {
                {
                    showActionSheet = false
                    it()
                }
            },
            onFork = onFork?.let {
                {
                    showActionSheet = false
                    it()
                }
            },
            onDelete = onDelete?.let {
                {
                    showActionSheet = false
                    it()
                }
            },
        )
    }
}

/**
 * Bottom Sheet displaying full turn actions (Select & Copy, Edit, Fork, Delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastChatActionsSheet(
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groupColor = MaterialTheme.colorScheme.surfaceContainerHighest

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(groupColor),
            ) {
                ActionSheetRow(
                    label = "Copy Text",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = onCopy,
                )

                if (onEdit != null) {
                    ActionSheetRow(
                        label = "Edit Message",
                        icon = Icons.Rounded.Edit,
                        onClick = onEdit,
                    )
                }

                if (onFork != null) {
                    ActionSheetRow(
                        label = "Fork Chat from Here",
                        icon = Icons.AutoMirrored.Rounded.CallSplit,
                        onClick = onFork,
                    )
                }
            }

            // Delete action in separate danger card
            if (onDelete != null) {
                Card(
                    onClick = onDelete,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Delete Turn",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionSheetRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
