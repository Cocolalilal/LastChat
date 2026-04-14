package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.theme.extractColorCandidates
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
                text = stringResource(R.string.assistant_profile_tap_change_avatar),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // IDENTITY GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.assistant_profile_identity)) {
            // Name
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_name),
                subtitle = stringResource(R.string.assistant_profile_name_desc),
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
                        text = stringResource(R.string.assistant_profile_tags_desc),
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
        SettingsGroup(title = stringResource(R.string.assistant_profile_appearance)) {
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

                // Color palette picker – visible when Material You colors are enabled
                if (assistant.useAssistantMaterialYouColors) {
                    ColorPalettePicker(
                        assistant = assistant,
                        onUpdate = onUpdate
                    )
                }
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

/**
 * A row of circular color swatches extracted from the assistant's
 * avatar / background images. The user can tap to switch their
 * preferred theme color index.
 */
@Composable
private fun ColorPalettePicker(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()

    // Extract candidates async on IO
    val candidates by produceState<List<Color>>(
        initialValue = emptyList(),
        assistant.avatar,
        assistant.background
    ) {
        value = withContext(Dispatchers.IO) {
            extractColorCandidates(context, assistant)
        }
    }

    if (candidates.isEmpty()) return

    SettingGroupInputItem(
        title = stringResource(R.string.assistant_page_theme_color),
        subtitle = stringResource(R.string.assistant_page_theme_color_desc)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            candidates.forEachIndexed { index, color ->
                val isSelected = assistant.materialYouColorIndex == index
                ColorSwatch(
                    color = color,
                    isSelected = isSelected,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onUpdate(assistant.copy(materialYouColorIndex = index))
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "swatch_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        label = "swatch_border"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.5.dp,
                color = borderColor,
                shape = CircleShape
            )
            .background(color, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // Determine check icon color for contrast
            val checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Simple luminance calculation for contrast decisions.
 */
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
