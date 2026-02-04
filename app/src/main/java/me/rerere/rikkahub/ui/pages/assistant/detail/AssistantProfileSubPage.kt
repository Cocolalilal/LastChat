package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.data.model.Tag as DataTag

/**
 * Profile tab - Assistant identity and appearance settings.
 * Designed with cohesive SettingsGroup pattern.
 */
@Composable
fun AssistantProfileSubPage(
    assistant: Assistant,
    tags: List<DataTag>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // AVATAR SECTION (prominent, centered)
        // ═══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = { avatar ->
                    onUpdate(assistant.copy(avatar = avatar))
                },
                modifier = Modifier.size(96.dp)
            )
            
            Text(
                text = "Tap to change avatar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // IDENTITY GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Identity") {
            // Name
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_name),
                subtitle = "Display name for this character",
                trailing = {
                    DebouncedTextField(
                        value = assistant.name,
                        onValueChange = { onUpdate(assistant.copy(name = it)) },
                        stateKey = assistant.id,
                        modifier = Modifier.fillMaxWidth(0.5f),
                        singleLine = true
                    )
                }
            )
            
            // Tags - vertical layout to prevent height growth
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.assistant_page_tags),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Organize with custom tags",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TagsInput(
                        value = assistant.tags,
                        tags = tags,
                        onValueChange = { tagIds, updatedTags ->
                            vm.updateTags(tagIds, updatedTags)
                        },
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // APPEARANCE GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Appearance") {
            val hasImageSource = assistant.background != null ||
                assistant.avatar is Avatar.Image ||
                assistant.avatar is Avatar.Resource

            if (hasImageSource) {
                SettingGroupItem(
                    title = stringResource(R.string.assistant_page_material_you_from_character),
                    subtitle = stringResource(R.string.assistant_page_material_you_from_character_desc),
                    trailing = {
                        HapticSwitch(
                            checked = assistant.useAssistantMaterialYouColors,
                            onCheckedChange = { enabled ->
                                onUpdate(assistant.copy(useAssistantMaterialYouColors = enabled))
                            }
                        )
                    }
                )
            }

            // Background Picker
            BackgroundPicker(
                background = assistant.background,
                backgroundDim = assistant.backgroundDim,
                onUpdate = { background ->
                    onUpdate(assistant.copy(background = background))
                },
                onDimChange = { dim ->
                    onUpdate(assistant.copy(backgroundDim = dim))
                }
            )
        }
    }
}
