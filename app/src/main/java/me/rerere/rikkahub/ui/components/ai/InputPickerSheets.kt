package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.activity.PrepareAssistantDialog
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.icons.ModeIcons
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.modifier.lastChatSheetContainerColor
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject

@Composable
internal fun SkillsPickerSheet(
    settings: Settings,
    assistant: Assistant,
    onInvokeSkill: (me.rerere.rikkahub.data.model.Skill) -> Unit,
    onDismiss: () -> Unit,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    val skills = settings.skills.filter {
        it.isAvailableForAssistant(assistant.id) && it.userInvocable && it.instructions.isNotBlank() &&
            it.name.isNotBlank() && (it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true))
    }
    ModalBottomSheet(
        containerColor = lastChatSheetContainerColor(),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PrepareAssistantDialog()
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Invoke a skill", style = MaterialTheme.typography.titleLarge)
            Text("Choose a skill for your next message.", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search skills") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (skills.isEmpty()) Text("No accessible skills match. Manage access in character settings.")
            skills.forEach { skill ->
                Card(
                    onClick = { onInvokeSkill(skill); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(ModeIcons.getIcon(skill.icon ?: "category"), contentDescription = null)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("/${skill.name}", style = MaterialTheme.typography.titleMedium)
                            if (skill.description.isNotBlank()) Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                            skill.argumentHint?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LorebooksPickerSheet(
    settings: Settings,
    assistant: Assistant,
    conversation: Conversation,
    onUpdateConversation: (Conversation) -> Unit,
    onNavigateToLorebook: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current

    val lorebookIds = remember(settings.lorebooks) { settings.lorebooks.map { it.id }.toSet() }
    val effectiveEnabledIds = (conversation.enabledLorebookIds ?: assistant.enabledLorebookIds).intersect(lorebookIds)
    var localEnabledIds by remember(conversation.id, conversation.enabledLorebookIds, assistant.enabledLorebookIds, lorebookIds) {
        mutableStateOf(effectiveEnabledIds)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        containerColor = lastChatSheetContainerColor(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        }
    ) {
        PrepareAssistantDialog()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.lorebooks_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (settings.lorebooks.isEmpty()) {
                Text(
                    text = stringResource(R.string.lorebooks_picker_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                settings.lorebooks.forEachIndexed { index, lorebook ->
                    val isEnabled = localEnabledIds.contains(lorebook.id)

                    val position = when {
                        settings.lorebooks.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == settings.lorebooks.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }

                    val cornerRadius = 28.dp
                    val smallCorner = 8.dp
                    val shape = when (position) {
                        ItemPosition.ONLY -> RoundedCornerShape(cornerRadius)
                        ItemPosition.FIRST -> RoundedCornerShape(
                            topStart = cornerRadius,
                            topEnd = cornerRadius,
                            bottomStart = smallCorner,
                            bottomEnd = smallCorner
                        )

                        ItemPosition.MIDDLE -> RoundedCornerShape(smallCorner)
                        ItemPosition.LAST -> RoundedCornerShape(
                            topStart = smallCorner,
                            topEnd = smallCorner,
                            bottomStart = cornerRadius,
                            bottomEnd = cornerRadius
                        )
                    }

                    CompositionLocalProvider(LocalAbsoluteTonalElevation provides if (amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            shape = shape,
                            onClick = { onNavigateToLorebook(lorebook.id.toString()) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bookShape = when (position) {
                                    ItemPosition.ONLY -> RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 6.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 6.dp
                                    )

                                    ItemPosition.FIRST -> RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 6.dp,
                                        bottomStart = 6.dp,
                                        bottomEnd = 6.dp
                                    )

                                    ItemPosition.MIDDLE -> RoundedCornerShape(6.dp)
                                    ItemPosition.LAST -> RoundedCornerShape(
                                        topStart = 6.dp,
                                        topEnd = 6.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 6.dp
                                    )
                                }
                                Surface(
                                    shape = bookShape,
                                    color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(width = 40.dp, height = 56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when (val cover = lorebook.cover) {
                                            is Avatar.Image -> {
                                                AsyncImage(
                                                    model = cover.url,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }

                                            is Avatar.Emoji -> {
                                                Text(
                                                    text = cover.content,
                                                    fontSize = 20.sp
                                                )
                                            }

                                            else -> {
                                                Text(
                                                    text = lorebook.name.take(1).uppercase().ifEmpty { "L" },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = lorebook.name.ifEmpty { stringResource(R.string.lorebooks_page_unnamed) },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.lorebooks_page_entries_count, lorebook.entries.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                HapticSwitch(
                                    checked = isEnabled,
                                    onCheckedChange = { newEnabled ->
                                        val newIds = if (newEnabled) {
                                            localEnabledIds + lorebook.id
                                        } else {
                                            localEnabledIds - lorebook.id
                                        }
                                        localEnabledIds = newIds
                                        onUpdateConversation(conversation.copy(enabledLorebookIds = newIds))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PluginsPickerSheet(
    settings: Settings,
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val mcpManager = koinInject<McpManager>()
    val syncingStatus by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = syncingStatus.values.any { it == McpStatus.Connecting }

    ModalBottomSheet(
        containerColor = lastChatSheetContainerColor(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        }
    ) {
        PrepareAssistantDialog()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.mcp_picker_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (loading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LinearWavyProgressIndicator()
                    Text(
                        text = stringResource(R.string.mcp_picker_syncing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            McpPicker(
                assistant = assistant,
                servers = settings.mcpServers,
                onUpdateAssistant = onUpdateAssistant,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
