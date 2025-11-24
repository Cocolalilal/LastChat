package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Delete
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Represents different types of items in the conversation list
 */
sealed class ConversationListItem {
    data class DateHeader(
        val date: LocalDate,
        val label: String
    ) : ConversationListItem()
    data object PinnedHeader : ConversationListItem()
    data class Item(
        val conversation: Conversation
    ) : ConversationListItem()
}

@Composable
fun ColumnScope.ConversationList(
    current: Conversation,
    conversations: LazyPagingItems<ConversationListItem>,
    conversationJobs: Collection<Uuid>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (Conversation) -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
    onRegenerateTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    showUnconsolidatedDot: Boolean = false
) {
    val navController = LocalNavController.current

    // fix: compose很奇怪，会自动聚焦到第一个文本框
    // 在这里放一个空的Box，防止自动聚焦到第一个文本框弹出IME
    Box(modifier = Modifier.focusable())

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .weight(1f),
            shape = RoundedCornerShape(50),
            trailingIcon = {
                AnimatedVisibility(searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onSearchQueryChange("")
                        }
                    ) {
                        Icon(Icons.Rounded.Close, null)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            placeholder = {
                Text(stringResource(id = R.string.chat_page_search_placeholder))
            }
        )
    }

    Box(modifier = modifier) {
        val listState = rememberLazyListState()
        val canScrollBackward by remember {
            derivedStateOf { listState.canScrollBackward }
        }
        val canScrollForward by remember {
            derivedStateOf { listState.canScrollForward }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (conversations.itemCount == 0) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = stringResource(id = R.string.chat_page_no_conversations),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(
                count = conversations.itemCount,
                key = conversations.itemKey { item ->
                    when (item) {
                        is ConversationListItem.DateHeader -> "date_${item.date}"
                        is ConversationListItem.PinnedHeader -> "pinned_header"
                        is ConversationListItem.Item -> item.conversation.id.toString()
                    }
                }
            ) { index ->
                when (val item = conversations[index]) {
                    is ConversationListItem.DateHeader -> {
                        DateHeaderItem(
                            label = item.label,
                            modifier = Modifier.animateItem()
                        )
                    }

                    is ConversationListItem.PinnedHeader -> {
                        PinnedHeader(
                            modifier = Modifier.animateItem()
                        )
                    }

                    is ConversationListItem.Item -> {
                        ConversationItem(
                            conversation = item.conversation,
                            selected = item.conversation.id == current.id,
                            loading = item.conversation.id in conversationJobs,
                            onClick = onClick,
                            onDelete = onDelete,
                            onRegenerateTitle = onRegenerateTitle,
                            onPin = onPin,
                            showUnconsolidatedDot = showUnconsolidatedDot,
                            modifier = Modifier.animateItem()
                        )
                    }

                    null -> {
                        // Placeholder for loading state
                    }
                }
            }
        }

        // Top Fade - only show when can scroll backward
        if (canScrollBackward) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .size(32.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainer,
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Bottom Fade - only show when can scroll forward
        if (canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .size(32.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun DateHeaderItem(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PinnedHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.pinned_chats),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    selected: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onDelete: (Conversation) -> Unit = {},
    onRegenerateTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    showUnconsolidatedDot: Boolean = false,
    onClick: (Conversation) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    } else {
        Color.Transparent
    }
    var showDropdownMenu by remember {
        mutableStateOf(false)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50f))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onClick(conversation) },
                onLongClick = {
                    showDropdownMenu = true
                }
            )
            .background(backgroundColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = conversation.title.ifBlank { stringResource(id = R.string.chat_page_new_message) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            
            // Unconsolidated Dot
            AnimatedVisibility(showUnconsolidatedDot && !conversation.isConsolidated) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .size(6.dp)
                )
            }

            // 置顶图标
            AnimatedVisibility(conversation.isPinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(loading) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.extendColors.green6)
                        .size(4.dp)
                        .semantics {
                            contentDescription = "Loading"
                        }
                )
            }
            DropdownMenu(
                expanded = showDropdownMenu,
                onDismissRequest = { showDropdownMenu = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (conversation.isPinned) stringResource(R.string.unpin_chat) else stringResource(R.string.pin_chat)
                        )
                    },
                    onClick = {
                        onPin(conversation)
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.PushPin,
                            null
                        )
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(stringResource(id = R.string.chat_page_regenerate_title))
                    },
                    onClick = {
                        onRegenerateTitle(conversation)
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Refresh, null)
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(stringResource(id = R.string.chat_page_delete))
                    },
                    onClick = {
                        onDelete(conversation)
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, null)
                    }
                )
            }
        }
    }
}
