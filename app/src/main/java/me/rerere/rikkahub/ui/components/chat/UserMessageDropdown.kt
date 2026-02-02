package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import me.rerere.rikkahub.R

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
        shape = MaterialTheme.shapes.medium,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy)) },
            onClick = {
                onAction(UserMessageAction.COPY)
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        
        DropdownMenuItem(
            text = { Text(stringResource(R.string.select_and_copy)) },
            onClick = {
                onAction(UserMessageAction.SELECT_TEXT)
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.SelectAll,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        
        DropdownMenuItem(
            text = { Text(stringResource(R.string.edit)) },
            onClick = {
                onAction(UserMessageAction.EDIT)
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share)) },
            onClick = {
                onAction(UserMessageAction.SHARE)
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        
        DropdownMenuItem(
            text = { 
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onAction(UserMessageAction.DELETE)
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        )
    }
}
