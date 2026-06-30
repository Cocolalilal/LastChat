package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.SKILL_SELECTION_OVERRIDE_ID
import me.rerere.rikkahub.data.model.hasManualSkillSelectionOverride
import me.rerere.rikkahub.data.model.withoutSkillSelectionOverride
import me.rerere.rikkahub.ui.components.ui.icons.ModeIcons
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Composable
internal fun SkillsPickerSheet(
    settings: Settings,
    assistant: Assistant,
    conversation: Conversation,
    onUpdateConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val cornerRadius = 28.dp
    val smallCorner = 8.dp

    val availableSkills = remember(settings.skills) {
        settings.skills
    }
    val availableSkillIds = remember(availableSkills) { availableSkills.map { it.id }.toSet() }
    val assistantAvailableSkillIds = remember(settings.skills, assistant.id) {
        settings.skills.filter { it.isAvailableForAssistant(assistant.id) }.map { it.id }.toSet()
    }
    val alwaysEnabledSkillIds = remember(availableSkills, assistantAvailableSkillIds) {
        availableSkills.filter { it.alwaysEnabled && assistantAvailableSkillIds.contains(it.id) }.map { it.id }.toSet()
    }
    val effectiveEnabledIds = remember(
        conversation.enabledModeIds,
        assistant.enabledSkillIds,
        availableSkillIds,
        assistantAvailableSkillIds,
        alwaysEnabledSkillIds
    ) {
        val hasOverride = conversation.enabledModeIds.hasManualSkillSelectionOverride()
        val base = if (hasOverride || conversation.enabledModeIds.isNotEmpty()) {
            conversation.enabledModeIds.withoutSkillSelectionOverride()
        } else {
            assistant.enabledSkillIds.intersect(assistantAvailableSkillIds)
        }
        (base + if (hasOverride) emptySet() else alwaysEnabledSkillIds).intersect(availableSkillIds)
    }

    var localEnabledIds by remember(conversation.id, conversation.enabledModeIds, assistant.enabledSkillIds) {
        mutableStateOf(effectiveEnabledIds)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.skills_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (availableSkills.isEmpty()) {
                Text(
                    text = stringResource(R.string.skills_picker_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                availableSkills.forEachIndexed { index, skill ->
                    val isEnabled = localEnabledIds.contains(skill.id)

                    val position = when {
                        availableSkills.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == availableSkills.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }

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
                                containerColor = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = shape
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = ModeIcons.getIcon(skill.icon ?: "category"),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = skill.name.ifEmpty { stringResource(R.string.skills_page_unnamed) },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = when {
                                            skill.description.isNotBlank() -> skill.description
                                            skill.instructions.isNotBlank() -> skill.instructions.take(50) + if (skill.instructions.length > 50) "..." else ""
                                            else -> stringResource(R.string.skills_page_empty_desc)
                                        },
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                HapticSwitch(
                                    checked = isEnabled,
                                    onCheckedChange = { newEnabled ->
                                        val newEnabledIds = if (newEnabled) {
                                            localEnabledIds + skill.id
                                        } else {
                                            localEnabledIds - skill.id
                                        }
                                        localEnabledIds = newEnabledIds
                                        onUpdateConversation(
                                            conversation.copy(enabledModeIds = newEnabledIds + SKILL_SELECTION_OVERRIDE_ID)
                                        )
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                containerColor = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
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
