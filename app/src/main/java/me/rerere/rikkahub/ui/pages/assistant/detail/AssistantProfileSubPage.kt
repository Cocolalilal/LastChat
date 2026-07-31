package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.theme.CUSTOM_MATERIAL_YOU_COLOR_INDEX
import me.rerere.rikkahub.ui.theme.extractColorCandidates
import me.rerere.rikkahub.ui.theme.parseMaterialYouColor
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

            // Color palette picker – visible when custom colors are enabled
            if (assistant.useAssistantMaterialYouColors) {
                ColorPalettePicker(
                    assistant = assistant,
                    onUpdate = onUpdate
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
    var showCustomColorDialog by remember { mutableStateOf(false) }

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

    SettingGroupInputItem(
        title = stringResource(R.string.assistant_page_theme_color),
        subtitle = stringResource(R.string.assistant_page_theme_color_desc)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 3.dp)
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
            CustomColorSwatch(
                color = parseMaterialYouColor(assistant.customMaterialYouColor)
                    ?: MaterialTheme.colorScheme.primary,
                isSelected = assistant.materialYouColorIndex == CUSTOM_MATERIAL_YOU_COLOR_INDEX,
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    showCustomColorDialog = true
                }
            )
        }
    }

    if (showCustomColorDialog) {
        CustomThemeColorDialog(
            initialHex = assistant.customMaterialYouColor ?: "#808080",
            onDismiss = { showCustomColorDialog = false },
            onSave = { colorHex ->
                onUpdate(
                    assistant.copy(
                        materialYouColorIndex = CUSTOM_MATERIAL_YOU_COLOR_INDEX,
                        customMaterialYouColor = colorHex
                    )
                )
                showCustomColorDialog = false
            }
        )
    }
}

@Composable
private fun CustomColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .background(color, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.assistant_page_edit_custom_color),
            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CustomThemeColorDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val initialColor = parseMaterialYouColor(initialHex) ?: Color.Gray
    val initialHsv = remember(initialHex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var colorHex by remember(initialHex) { mutableStateOf(normalizeHexInput(initialHex)) }
    var hue by remember(initialHex) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialHex) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initialHex) { mutableFloatStateOf(initialHsv[2]) }

    fun updateHexFromHsv() {
        colorHex = hsvColor(hue, saturation, brightness).toHexColor()
    }

    val previewColor = parseMaterialYouColor(colorHex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.assistant_page_custom_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationValuePicker(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onValueChange = { newSaturation, newBrightness ->
                        saturation = newSaturation
                        brightness = newBrightness
                        updateHexFromHsv()
                    }
                )

                HuePicker(
                    hue = hue,
                    onHueChange = { newHue ->
                        hue = newHue
                        updateHexFromHsv()
                    }
                )

                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { input ->
                        colorHex = normalizeHexInput(input)
                        parseMaterialYouColor(colorHex)?.let { parsed ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed.toArgb(), hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    label = { Text(stringResource(R.string.setting_rp_color_hex)) },
                    placeholder = { Text("#808080") },
                    singleLine = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(previewColor ?: Color.Gray)
                        )
                    },
                    isError = previewColor == null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = previewColor != null,
                onClick = { onSave(colorHex) }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onValueChange: (saturation: Float, brightness: Float) -> Unit
) {
    val hueColor = hsvColor(hue, 1f, 1f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .clip(me.rerere.rikkahub.ui.theme.AppShapes.InputField)
            .pointerInput(Unit) {
                fun update(position: Offset) {
                    onValueChange(
                        (position.x / size.width.toFloat()).coerceIn(0f, 1f),
                        (1f - position.y / size.height.toFloat()).coerceIn(0f, 1f)
                    )
                }
                detectDragGestures(
                    onDragStart = { update(it) },
                    onDrag = { change, _ ->
                        update(change.position)
                        change.consume()
                    }
                )
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val radius = 10.dp.toPx()
        val selector = Offset(
            x = (saturation * size.width).coerceIn(radius, size.width - radius),
            y = ((1f - brightness) * size.height).coerceIn(radius, size.height - radius)
        )
        drawCircle(Color.Black.copy(alpha = 0.65f), radius = radius + 2.dp.toPx(), center = selector)
        drawCircle(Color.White, radius = radius, center = selector, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun HuePicker(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    val hueColors = remember {
        (0..6).map { step -> hsvColor(step * 60f, 1f, 1f) }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                fun update(x: Float) {
                    onHueChange((x / size.width.toFloat()).coerceIn(0f, 1f) * 360f)
                }
                detectDragGestures(
                    onDragStart = { update(it.x) },
                    onDrag = { change, _ ->
                        update(change.position.x)
                        change.consume()
                    }
                )
            }
    ) {
        drawRect(Brush.horizontalGradient(hueColors))
        val x = (hue / 360f * size.width).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
        drawLine(Color.Black.copy(alpha = 0.65f), Offset(x, 0f), Offset(x, size.height), 6.dp.toPx())
        drawLine(Color.White, Offset(x, 2.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), 2.dp.toPx())
    }
}

private fun hsvColor(hue: Float, saturation: Float, brightness: Float): Color {
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
}

private fun Color.toHexColor(): String {
    return "#%02X%02X%02X".format(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

private fun normalizeHexInput(value: String): String {
    val digits = value.trim().removePrefix("#")
        .filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        .take(6)
        .uppercase()
    return "#$digits"
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
