package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AppSurfaceLevel
import me.rerere.rikkahub.ui.theme.appSurfaceColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    maxHeightFraction: Float? = null,
    sheetGesturesEnabled: Boolean = true,
    showCloseButton: Boolean = false,
    dragHandle: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        dragHandle = dragHandle,
        containerColor = appSurfaceColor(AppSurfaceLevel.Container),
        shape = AppShapes.BottomSheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (maxHeightFraction != null) {
                        Modifier.fillMaxHeight(maxHeightFraction)
                    } else {
                        Modifier
                    }
                )
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = horizontalAlignment,
        ) {
            if (title != null || showCloseButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (showCloseButton) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }
                }
            }

            content()
        }
    }
}

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = icon,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = AppShapes.Dialog,
        containerColor = appSurfaceColor(AppSurfaceLevel.ContainerHigh),
    )
}
