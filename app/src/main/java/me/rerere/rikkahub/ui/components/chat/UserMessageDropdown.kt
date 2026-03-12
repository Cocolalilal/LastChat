package me.rerere.rikkahub.ui.components.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppActionMenuContent
import me.rerere.rikkahub.ui.components.ui.AppActionMenuDestructiveItem
import me.rerere.rikkahub.ui.components.ui.AppActionMenuItem
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.placedSurfaceColor

/**
 * Actions available for user messages via long-press.
 */
enum class UserMessageAction {
    COPY,
    SELECT_TEXT,
    EDIT,
    SHARE,
    DELETE
}

/**
 * Dropdown menu for user message actions.
 * Appears on long-press of a user message bubble.
 * 
 * Design: Similar to chat history context menu in sidebar.
 */
@Composable
fun UserMessageDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onAction: (UserMessageAction) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = AppShapes.CardMedium,
        containerColor = placedSurfaceColor(),
    ) {
        AppActionMenuContent(
            destructiveAction = {
                AppActionMenuDestructiveItem(
                    icon = Icons.Rounded.Delete,
                    title = stringResource(R.string.delete),
                    onClick = {
                        onAction(UserMessageAction.DELETE)
                        onDismissRequest()
                    }
                )
            }
        ) {
            AppActionMenuItem(
                icon = Icons.Rounded.ContentCopy,
                title = stringResource(R.string.copy),
                onClick = {
                    onAction(UserMessageAction.COPY)
                    onDismissRequest()
                }
            )
            AppActionMenuItem(
                icon = Icons.Rounded.SelectAll,
                title = stringResource(R.string.select_and_copy),
                onClick = {
                    onAction(UserMessageAction.SELECT_TEXT)
                    onDismissRequest()
                }
            )
            AppActionMenuItem(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.edit),
                onClick = {
                    onAction(UserMessageAction.EDIT)
                    onDismissRequest()
                }
            )
            AppActionMenuItem(
                icon = Icons.Rounded.Share,
                title = stringResource(R.string.share),
                onClick = {
                    onAction(UserMessageAction.SHARE)
                    onDismissRequest()
                }
            )
        }
    }
}
