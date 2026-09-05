package me.rerere.lastchat.ios.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import me.rerere.lastchat.ios.IosAppState
import me.rerere.lastchat.ios.IosConversation
import me.rerere.rikkahub.ui.components.nav.LastChatBarChartIcon
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerAction
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerQuickAction
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerQuickActionGroup
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerSearch
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerActionIcon
import me.rerere.rikkahub.ui.theme.rememberDefaultGenericalPainter
import me.rerere.rikkahub.ui.theme.AppShapes

private fun formatConversationDateGroup(epochMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val daysDiff = today.toEpochDays() - date.toEpochDays()
    return when {
        daysDiff == 0L -> "Today"
        daysDiff == 1L -> "Yesterday"
        today.year == date.year -> "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
        else -> "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.year}"
    }
}

@Composable
fun IosDrawerContent(
    state: IosAppState,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onSelectAssistant: (String) -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
    onImageGeneration: () -> Unit,
    onAssistantDetail: () -> Unit = onSettings,
    onHapticPop: () -> Unit = {},
    onHapticTick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }

    val filteredConversations = remember(state.conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            state.conversations
        } else {
            state.conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    val pinnedConversations = remember(filteredConversations) {
        filteredConversations.filter { it.isPinned }
    }

    // Group regular conversations by date
    val groupedRegularConversations = remember(filteredConversations) {
        filteredConversations
            .filter { !it.isPinned }
            .sortedByDescending { it.updatedAtEpochMs }
            .groupBy { formatConversationDateGroup(it.updatedAtEpochMs) }
    }

    val currentHour = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    }
    val greetingText = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Good Morning!"
            in 12..13 -> "Good Noon!"
            in 14..17 -> "Good Afternoon!"
            in 18..20 -> "Good Evening!"
            else -> "Good Night!"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // User Header with Greeting
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = rememberDefaultGenericalPainter(),
                            contentDescription = "User Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Julian",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Search Capsule
            item {
                LastChatDrawerSearch(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    placeholder = "Search conversations",
                    hint = "Search titles",
                )
            }

            // Quick Actions: Imagine + Stats
            item {
                AnimatedVisibility(
                    visible = !searchExpanded,
                    enter = fadeIn(animationSpec = spring(stiffness = 300f)) +
                        expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)),
                    exit = fadeOut(animationSpec = spring(stiffness = 500f)) +
                        shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)),
                ) {
                    LastChatDrawerQuickActionGroup {
                        LastChatDrawerQuickAction(
                            label = "Imagine",
                            onClick = onImageGeneration,
                            onHaptic = onHapticTick,
                            icon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                        )
                        LastChatDrawerQuickAction(
                            label = "Statistics",
                            onClick = onStatistics,
                            onHaptic = onHapticTick,
                            icon = { LastChatBarChartIcon(contentDescription = null) },
                        )
                    }
                }
            }

            if (filteredConversations.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = AppShapes.CardSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "No conversations",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            // Pinned Conversations
            if (pinnedConversations.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                items(pinnedConversations, key = { "pinned_" + it.id }) { conversation ->
                    IosDrawerConversationRow(
                        conversation = conversation,
                        selected = conversation.id == state.selectedConversationId,
                        onSelect = {
                            onSelectConversation(conversation.id)
                            onDismiss()
                        },
                        onRename = { title -> onRenameConversation(conversation.id, title) },
                        onDelete = { onDeleteConversation(conversation.id) },
                        onTogglePin = { onTogglePinConversation(conversation.id) },
                        onHapticPop = onHapticPop,
                    )
                }
            }

            // Date Grouped Regular Conversations
            groupedRegularConversations.forEach { (dateHeader, convs) ->
                item(key = "header_$dateHeader") {
                    Text(
                        text = dateHeader,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }

                items(convs, key = { it.id }) { conversation ->
                    IosDrawerConversationRow(
                        conversation = conversation,
                        selected = conversation.id == state.selectedConversationId,
                        onSelect = {
                            onSelectConversation(conversation.id)
                            onDismiss()
                        },
                        onRename = { title -> onRenameConversation(conversation.id, title) },
                        onDelete = { onDeleteConversation(conversation.id) },
                        onTogglePin = { onTogglePinConversation(conversation.id) },
                        onHapticPop = onHapticPop,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Assistant Footer Pill + Settings Button
        val actionButtonSize = 44.dp
        val assistantAvatarSize = 32.dp
        val itemColor = MaterialTheme.colorScheme.surfaceContainerHighest

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = itemColor,
                shape = AppShapes.ButtonPill,
                modifier = Modifier
                    .weight(1f)
                    .height(actionButtonSize),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                onHapticPop()
                                onAssistantDetail()
                                onDismiss()
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = state.assistant.name.ifBlank { "Generical" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(assistantAvatarSize)
                            .clip(CircleShape)
                            .clickable {
                                onHapticPop()
                                onAssistantDetail()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = rememberDefaultGenericalPainter(),
                            contentDescription = state.assistant.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            LastChatDrawerAction(
                onClick = {
                    onHapticPop()
                    onSettings()
                    onDismiss()
                },
                onHaptic = onHapticPop,
                containerColor = itemColor,
                size = actionButtonSize,
            ) { containerSize, iconSize ->
                LastChatDrawerActionIcon(
                    containerSize = containerSize,
                    iconSize = iconSize,
                    contentDescription = "Settings",
                )
            }
        }
    }
}

@Composable
private fun IosDrawerConversationRow(
    conversation: IosConversation,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onHapticPop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(conversation.title) }

    val hasBranchIndicator = conversation.title.contains("↰")

    Surface(
        onClick = onSelect,
        shape = AppShapes.ListItem,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasBranchIndicator) {
                Text(
                    text = "↰ ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = conversation.title.replace("↰", "").trim(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Box {
                IconButton(
                    onClick = {
                        onHapticPop()
                        showMenu = true
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text(if (conversation.isPinned) "Unpin" else "Pin") },
                        onClick = {
                            onHapticPop()
                            onTogglePin()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            onHapticPop()
                            renameText = conversation.title
                            showRenameDialog = true
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onHapticPop()
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRename(renameText)
                        }
                        showRenameDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp),
        )
    }
}
