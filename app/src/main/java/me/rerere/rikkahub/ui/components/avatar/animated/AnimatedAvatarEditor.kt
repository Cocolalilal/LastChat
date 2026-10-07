package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomThemeColorDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnimatedAvatarEditor(
    initialAvatar: Avatar.Animated?,
    onDismiss: () -> Unit,
    onSave: (Avatar.Animated) -> Unit,
) {
    val initialShape = initialAvatar?.shape ?: "blob"
    var shape by remember {
        mutableStateOf(
            if (initialShape == "teardrop" || initialShape == "droplet") "blob"
            else initialShape
        )
    }
    var eyeType by remember { mutableStateOf(initialAvatar?.eyeType ?: "generical") }
    var colorHex by remember { mutableStateOf(initialAvatar?.colorHex ?: MarkColors.DEFAULT_GENERICAL_HEX) }
    var colorPreset by remember {
        mutableStateOf<String?>(initialAvatar?.colorPreset ?: "cyan")
    }
    var eyeColorHex by remember { mutableStateOf(initialAvatar?.eyeColorHex) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showEyeColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        CustomThemeColorDialog(
            initialHex = colorHex,
            onDismiss = { showColorPicker = false },
            onSave = { hex ->
                colorHex = MarkColors.normalizeHex(hex)
                colorPreset = null
                showColorPicker = false
            },
        )
    }
    if (showEyeColorPicker) {
        CustomThemeColorDialog(
            initialHex = eyeColorHex ?: MarkColors.DEFAULT_GENERICAL_EYE_TINT,
            onDismiss = { showEyeColorPicker = false },
            onSave = { hex ->
                eyeColorHex = MarkColors.normalizeHex(hex)
                showEyeColorPicker = false
            },
        )
    }

    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    // Open fully expanded: a half-expanded sheet hid the lower shape rows.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AnimatedAvatarEditorContent(
            shape = shape,
            onShape = { shape = it },
            eyeType = eyeType,
            onEyeType = { eyeType = it },
            colorHex = colorHex,
            colorPreset = colorPreset,
            onColor = { hex, preset -> colorHex = hex; colorPreset = preset },
            eyeColorHex = eyeColorHex,
            onEyeColor = { eyeColorHex = it },
            onPickColor = { showColorPicker = true },
            onPickEyeColor = { showEyeColorPicker = true },
            onDismiss = onDismiss,
            onSave = {
                onSave(
                    Avatar.Animated(
                        shape = shape,
                        eyeType = eyeType.lowercase(),
                        colorHex = MarkColors.normalizeHex(colorHex),
                        colorPreset = colorPreset,
                        eyeColorHex = eyeColorHex?.let { MarkColors.normalizeHex(it) },
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding(),
        )
    }
}

/** Mode default eye color (what `eyeColorHex == null` renders as). */
internal fun defaultEyeHex(eyeType: String): String =
    if (eyeType.equals("grok", true)) "#1A1A1A" else MarkColors.DEFAULT_GENERICAL_EYE_TINT

/**
 * Eye-colour swatches without duplicates. The mode default is one of the presets
 * (Generical #87D2E9 = sky, Grok #1A1A1A = ink); polish3 prepended it as an extra
 * swatch, so light blue / black showed twice.
 */
internal fun eyeSwatches(eyeType: String): List<String> {
    val def = defaultEyeHex(eyeType).uppercase()
    val presets = MarkColors.EYE_PRESETS.values.filter { it.isNotBlank() }.map { it.uppercase() }
    return (listOf(def) + presets).distinct()
}

/** Sheet body — split out so the render harness can screenshot it without a popup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnimatedAvatarEditorContent(
    shape: String,
    onShape: (String) -> Unit,
    eyeType: String,
    onEyeType: (String) -> Unit,
    colorHex: String,
    colorPreset: String?,
    onColor: (String, String?) -> Unit,
    eyeColorHex: String?,
    onEyeColor: (String?) -> Unit,
    onPickColor: () -> Unit,
    onPickEyeColor: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(72.dp) }
    Box(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = barHeight + 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedMarkAvatar(
                    shapeId = shape,
                    eyeType = eyeType,
                    colorHex = colorHex,
                    colorPreset = colorPreset,
                    eyeColorHex = eyeColorHex,
                    isLoading = false,
                    calmPreview = true,
                    modifier = Modifier.size(168.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.avatar_animated_eyes), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FilterChip(
                        selected = eyeType.equals("generical", ignoreCase = true),
                        onClick = { onEyeType("generical") },
                        label = { Text(stringResource(R.string.avatar_animated_eye_generical)) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = eyeType.equals("grok", ignoreCase = true),
                        onClick = { onEyeType("grok") },
                        label = { Text(stringResource(R.string.avatar_animated_eye_grok)) },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.avatar_animated_eye_color), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val def = defaultEyeHex(eyeType)
                    val current = (eyeColorHex?.takeIf { it.isNotBlank() } ?: def)
                        .let { MarkColors.normalizeHex(it) }
                    eyeSwatches(eyeType).forEach { hex ->
                        val color = MarkColors.hexToColor(hex)
                        val selected = current.equals(hex, ignoreCase = true)
                        val isDefault = hex.equals(def, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (color.luminance() < 0.08f) Modifier.border(1.dp, Color(0x33FFFFFF), CircleShape)
                                    else Modifier
                                )
                                // Picking the mode default stores null so it tracks the mode.
                                .clickable { onEyeColor(if (isDefault) null else hex) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = if (isDefault) {
                                        stringResource(R.string.avatar_animated_eye_color_default)
                                    } else null,
                                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                eyeColorHex?.let { MarkColors.hexToColor(it) }
                                    ?: MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onPickEyeColor() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.avatar_animated_eye_color),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.avatar_animated_color), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarkColors.PRESETS.forEach { (name, hex) ->
                        val color = MarkColors.hexToColor(hex)
                        val selected = colorPreset == name || colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { onColor(hex, name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected && colorPreset != null) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MarkColors.hexToColor(colorHex))
                            .clickable { onPickColor() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.avatar_animated_color),
                            tint = if (MarkColors.hexToColor(colorHex).luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.avatar_animated_shape), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                // FlowRow scrolls with parent — no nested LazyVerticalGrid pushing Save off-screen.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MarkShapes.SHAPES.forEach { s ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (shape == s) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { onShape(s) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedMarkAvatar(
                                shapeId = s,
                                eyeType = eyeType,
                                colorHex = colorHex,
                                colorPreset = colorPreset,
                                eyeColorHex = eyeColorHex,
                                isLoading = false,
                                calmPreview = true,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Pinned Save/Cancel overlays the bottom; scroll content is padded by its
            // measured height so the last shape row is always reachable.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .onSizeChanged { barHeight = with(density) { it.height.toDp() } }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.avatar_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave) {
                    Text(stringResource(R.string.avatar_animated_save))
                }
            }
        }
    }
