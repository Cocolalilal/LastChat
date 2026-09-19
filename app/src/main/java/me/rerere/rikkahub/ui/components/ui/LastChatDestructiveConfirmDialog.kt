package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatDialogContainerColor
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AppSurface

/**
 * Single irreversible-confirm pattern: floating rounded dialog, clear title,
 * one-line consequence, Cancel + danger confirm (Delete by default).
 */
@Composable
fun LastChatDestructiveConfirmDialog(
    title: String,
    consequence: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.delete),
    cancelLabel: String = stringResource(R.string.cancel),
) {
    val containerColor = lastChatDialogContainerColor()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.lastChatBlurEffect(containerColor, AppShapes.Dialog),
        shape = AppShapes.Dialog,
        containerColor = containerColor,
        tonalElevation = AppSurface.TonalElevation,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = consequence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}
