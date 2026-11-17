package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import me.rerere.rikkahub.ui.pages.setting.components.AddonListItem
import me.rerere.rikkahub.ui.pages.setting.components.AddonEmptyState
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.HapticPattern
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.ui.components.ui.EmptyStateCard
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.icons.ModeIcons
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Composable
fun AssistantSkillsSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val availableSkills = settings.skills

    if (availableSkills.isEmpty() && settings.additionalContexts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AddonEmptyState(Icons.Rounded.Category, stringResource(R.string.assistant_skills_empty))
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "automatic_skill_invocation") {
            me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup(title = "", horizontalPadding = 0.dp) {
                me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem(
                    title = "Automatic skill selection",
                    trailing = {
                        HapticSwitch(assistant.enableAutomaticSkillInvocation, {
                            onUpdate(assistant.copy(enableAutomaticSkillInvocation = it))
                        })
                    },
                    onClick = { onUpdate(assistant.copy(enableAutomaticSkillInvocation = !assistant.enableAutomaticSkillInvocation)) },
                )
            }
            Spacer(Modifier.size(12.dp))
        }

        itemsIndexed(availableSkills, key = { _, it -> it.id }) { index, skill ->
            val position = when {
                availableSkills.size == 1 -> ItemPosition.ONLY
                index == 0 -> ItemPosition.FIRST
                index == availableSkills.lastIndex -> ItemPosition.LAST
                else -> ItemPosition.MIDDLE
            }
            val isEnabled = skill.isAvailableForAssistant(assistant.id)

            AddonSelectionCard(
                name = skill.name,
                description = skill.description,
                icon = skill.icon ?: "category",
                isEnabled = isEnabled,
                position = position,
                onClick = { navController.navigate(Screen.SettingSkillDetail(skill.id.toString())) },
                onToggle = { enabled ->
                    vm.setSkillAccess(skill.id, enabled)
                }
            )
        }
        if (settings.additionalContexts.isNotEmpty()) {
            item { Text("Additional context", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp)) }
            itemsIndexed(settings.additionalContexts, key = { _, snippet -> "context-${snippet.id}" }) { index, snippet ->
                AddonSelectionCard(
                    name = snippet.name.ifBlank { "Untitled context" },
                    description = snippet.prompt,
                    icon = "notes",
                    isEnabled = snippet.allAssistants || assistant.id in snippet.assistantIds,
                    position = when {
                        settings.additionalContexts.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == settings.additionalContexts.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    },
                    onClick = { navController.navigate(Screen.SettingAdditionalContextDetail(snippet.id.toString())) },
                    onToggle = { vm.setAdditionalContextAccess(snippet.id, it) },
                )
            }
        }
    }
}

@Composable
private fun AddonSelectionCard(
    name: String,
    description: String,
    icon: String,
    isEnabled: Boolean,
    position: ItemPosition,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val haptics = rememberPremiumHaptics()
    AddonListItem(
        title = name.ifBlank { stringResource(R.string.skills_page_unnamed) }, subtitle = description,
        position = position,
        leading = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(ModeIcons.getIcon(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp))
                }
            }
        },
        trailing = {
            Checkbox(isEnabled, onCheckedChange = { haptics.perform(HapticPattern.Pop); onToggle(it) },
                modifier = Modifier.semantics { contentDescription = "$name access" })
        },
        onClick = onClick,
    )
}
