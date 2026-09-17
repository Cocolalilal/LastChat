package me.rerere.rikkahub.ui.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes

private val PRESET_COLORS = listOf(
    Avatar.Blob.DEFAULT_GENERICAL_COLOR,
    Avatar.Blob.DEFAULT_GROK_COLOR,
    "#2563EB",
    "#7C3AED",
    "#DB2777",
    "#EA580C",
    "#16A34A",
    "#0891B2",
    "#F4F6F8",
    "#111827",
)

private val ACCENT_COLORS = listOf(
    Avatar.Blob.DEFAULT_GENERICAL_ACCENT,
    "#FFFFFF",
    "#FDE68A",
    "#FCA5A5",
    "#A5B4FC",
    "#86EFAC",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateBlobAvatarSheet(
    initial: Avatar.Blob,
    onDismiss: () -> Unit,
    onSave: (Avatar.Blob) -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    var spec by remember(initial) { mutableStateOf(initial) }
    var previewLifecycle by remember { mutableStateOf(BlobLifecycle.Idle) }
    var showColorPicker by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(ColorTarget.Body) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val glance = remember { BlobGlanceController(cooldownMs = 0L) }
    val lives = BlobLifecycle.entries
    val shapes = BlobShape.entries

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShapes.BottomSheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CompositionLocalProvider(
            LocalBlobGlance provides glance.glance,
            LocalBlobGlancePulse provides BlobGlancePulse { x, y -> glance.pulse(x, y, force = true) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.avatar_create_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.avatar_create_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .clip(AppShapes.CardLarge)
                        .background(Color(0xFF2C333C)),
                    contentAlignment = Alignment.Center,
                ) {
                    BlobAvatar(
                        spec = spec,
                        modifier = Modifier.size(168.dp),
                        lifecycle = previewLifecycle,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF3F5F7))
                            .border(1.dp, Color(0x14000000), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BlobAvatar(
                            spec = spec,
                            modifier = Modifier.size(36.dp),
                            lifecycle = previewLifecycle,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.avatar_eye_pack),
                    style = MaterialTheme.typography.titleSmall,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val packs = listOf(BlobEyePack.Generical, BlobEyePack.Grok)
                    packs.forEachIndexed { index, pack ->
                        SegmentedButton(
                            selected = spec.eyes == pack,
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                spec = spec.withPack(pack)
                                glance.pulse(0f, 0.15f, force = true)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = packs.size),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (pack == BlobEyePack.Generical) {
                                    stringResource(R.string.avatar_eye_pack_generical)
                                } else {
                                    stringResource(R.string.avatar_eye_pack_grok)
                                }
                            )
                        }
                    }
                }
                if (spec.eyes == BlobEyePack.Grok) {
                    Text(
                        text = stringResource(R.string.avatar_grok_fill_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = stringResource(R.string.avatar_lifecycle),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    lives.forEachIndexed { index, state ->
                        FilterChip(
                            selected = previewLifecycle == state,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                previewLifecycle = state
                                val x = (index / (lives.lastIndex.coerceAtLeast(1).toFloat()) - 0.5f) * 1.3f
                                glance.pulse(x, 0.72f, force = true)
                            },
                            label = { Text(lifecycleLabel(state)) },
                            shape = AppShapes.Chip,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.avatar_shape),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    shapes.forEachIndexed { index, shape ->
                        val selected = spec.shape == shape
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = AppShapes.CardSmall,
                                )
                                .clickable {
                                    haptics.perform(HapticPattern.Pop)
                                    spec = spec.copy(shape = shape)
                                    val x = (index / (shapes.lastIndex.coerceAtLeast(1).toFloat()) - 0.5f) * 1.4f
                                    glance.pulse(x, 0.85f, force = true)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BlobAvatar(
                                spec = spec.copy(shape = shape),
                                modifier = Modifier.size(48.dp),
                                lifecycle = previewLifecycle,
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.avatar_color),
                    style = MaterialTheme.typography.titleSmall,
                )
                ColorRow(
                    selected = spec.color,
                    colors = PRESET_COLORS,
                    onSelect = { hex ->
                        haptics.perform(HapticPattern.Pop)
                        spec = spec.copy(color = hex)
                    },
                    onCustom = {
                        pickingTarget = ColorTarget.Body
                        showColorPicker = true
                    },
                )

                Text(
                    text = stringResource(R.string.avatar_knobs),
                    style = MaterialTheme.typography.titleSmall,
                )
                KnobSlider(
                    label = stringResource(R.string.avatar_eye_size),
                    value = spec.clampedEyeSize(),
                    range = 0.6f..1.5f,
                    onChange = { spec = spec.copy(eyeSize = it) },
                )
                KnobSlider(
                    label = stringResource(R.string.avatar_eye_spacing),
                    value = spec.clampedEyeSpacing(),
                    range = 0.65f..1.4f,
                    onChange = { spec = spec.copy(eyeSpacing = it) },
                )
                KnobSlider(
                    label = stringResource(R.string.avatar_look_around),
                    value = spec.clampedLookAround(),
                    range = 0f..1.5f,
                    onChange = { spec = spec.copy(lookAround = it) },
                )

                if (spec.eyes == BlobEyePack.Generical) {
                    KnobSlider(
                        label = stringResource(R.string.avatar_eye_roundness),
                        value = spec.clampedEyeRoundness(),
                        range = 0.15f..1f,
                        onChange = { spec = spec.copy(eyeRoundness = it) },
                    )
                    FormItem(
                        label = { Text(stringResource(R.string.avatar_glow)) },
                        description = { Text(stringResource(R.string.avatar_glow_desc)) },
                        tail = {
                            HapticSwitch(
                                checked = spec.glowEnabled,
                                onCheckedChange = { spec = spec.copy(glowEnabled = it) },
                            )
                        },
                    )
                    if (spec.glowEnabled) {
                        KnobSlider(
                            label = stringResource(R.string.avatar_glow_strength),
                            value = spec.clampedGlowStrength(),
                            range = 0f..1f,
                            onChange = { spec = spec.copy(glowStrength = it) },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.avatar_glow_color),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ColorSwatch(
                                hex = spec.glowColor,
                                selected = true,
                                onClick = {
                                    pickingTarget = ColorTarget.Glow
                                    showColorPicker = true
                                },
                            )
                        }
                    }
                    FormItem(
                        label = { Text(stringResource(R.string.avatar_accent)) },
                        description = { Text(stringResource(R.string.avatar_accent_desc)) },
                    ) {
                        ColorRow(
                            selected = spec.accentColor,
                            colors = ACCENT_COLORS,
                            onSelect = { hex ->
                                haptics.perform(HapticPattern.Pop)
                                spec = spec.copy(accentColor = hex)
                            },
                            onCustom = {
                                pickingTarget = ColorTarget.Accent
                                showColorPicker = true
                            },
                        )
                    }
                }

                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        onSave(spec)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.ButtonRounded,
                ) {
                    Text(stringResource(R.string.avatar_save))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showColorPicker) {
        val initialHex = when (pickingTarget) {
            ColorTarget.Body -> spec.color
            ColorTarget.Glow -> spec.glowColor
            ColorTarget.Accent -> spec.accentColor
        }
        BlobColorPickerDialog(
            initialHex = initialHex,
            onDismiss = { showColorPicker = false },
            onSave = { hex ->
                spec = when (pickingTarget) {
                    ColorTarget.Body -> spec.copy(color = hex)
                    ColorTarget.Glow -> spec.copy(glowColor = hex)
                    ColorTarget.Accent -> spec.copy(accentColor = hex)
                }
                showColorPicker = false
            },
        )
    }
}

private enum class ColorTarget { Body, Glow, Accent }

@Composable
private fun KnobSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    FormItem(label = { Text(label) }) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

@Composable
private fun ColorRow(
    selected: String,
    colors: List<String>,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { hex ->
            ColorSwatch(
                hex = hex,
                selected = selected.equals(hex, ignoreCase = true),
                onClick = { onSelect(hex) },
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .clickable(onClick = onCustom),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.avatar_custom_color),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(parseHexArgb(hex))
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.5.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun lifecycleLabel(state: BlobLifecycle): String {
    val res = when (state) {
        BlobLifecycle.Idle -> R.string.avatar_lifecycle_idle
        BlobLifecycle.Thinking -> R.string.avatar_lifecycle_thinking
        BlobLifecycle.Working -> R.string.avatar_lifecycle_working
        BlobLifecycle.Waiting -> R.string.avatar_lifecycle_waiting
        BlobLifecycle.Blocked -> R.string.avatar_lifecycle_blocked
        BlobLifecycle.Done -> R.string.avatar_lifecycle_done
    }
    return stringResource(res)
}

private fun Avatar.Blob.withPack(pack: BlobEyePack): Avatar.Blob {
    val switchingToGrok = pack == BlobEyePack.Grok && eyes != BlobEyePack.Grok
    val switchingToGenerical = pack == BlobEyePack.Generical && eyes != BlobEyePack.Generical
    return copy(
        eyes = pack,
        color = when {
            switchingToGrok && color.equals(Avatar.Blob.DEFAULT_GENERICAL_COLOR, true) -> {
                Avatar.Blob.DEFAULT_GROK_COLOR
            }
            switchingToGenerical && color.equals(Avatar.Blob.DEFAULT_GROK_COLOR, true) -> {
                Avatar.Blob.DEFAULT_GENERICAL_COLOR
            }
            else -> color
        },
        glowEnabled = if (switchingToGrok) false else glowEnabled,
    )
}

@Composable
private fun BlobColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val initialColor = Color(parseHexArgb(initialHex))
    val hsv = remember(initialHex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember(initialHex) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(initialHex) { mutableFloatStateOf(hsv[1]) }
    var brightness by remember(initialHex) { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    val hex = "#%06X".format(color.toArgb() and 0xFFFFFF)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.avatar_custom_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                SaturationBrightnessPicker(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onChange = { s, v ->
                        saturation = s
                        brightness = v
                    },
                )
                HueBar(
                    hue = hue,
                    onChange = { hue = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(hex) }) {
                Text(stringResource(R.string.avatar_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.avatar_cancel))
            }
        },
    )
}

@Composable
private fun SaturationBrightnessPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(AppShapes.CardSmall)
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val x = saturation * size.width
                val y = (1f - brightness) * size.height
                drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, y))
                drawCircle(
                    color = Color.Black,
                    radius = 8.dp.toPx(),
                    center = Offset(x, y),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                )
            }
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onChange(s, v)
                }
            }
    )
}

@Composable
private fun HueBar(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    val hueColors = remember {
        listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(CircleShape)
            .drawBehind {
                drawRect(Brush.horizontalGradient(hueColors))
                val x = (hue / 360f * size.width).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
                drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, size.height / 2f))
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
    )
}
