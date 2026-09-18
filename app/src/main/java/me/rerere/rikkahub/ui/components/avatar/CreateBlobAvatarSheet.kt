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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
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

private val BODY_COLORS = listOf(
    Avatar.Blob.DEFAULT_GENERICAL_COLOR,
    Avatar.Blob.DEFAULT_GROK_COLOR,
    "#2563EB", "#7C3AED", "#DB2777", "#EA580C",
    "#16A34A", "#0891B2", "#F1EFE9", "#111827", "#0A0A0C",
)

private val EYE_COLORS = listOf(
    Avatar.Blob.DEFAULT_EYE_COLOR,
    Avatar.Blob.DEFAULT_GROK_EYE_COLOR,
    "#FFFFFF", "#0A0A0C", "#111827", "#FDE68A", "#A5B4FC",
)

private val ACCENT_COLORS = listOf(
    Avatar.Blob.DEFAULT_GENERICAL_ACCENT,
    "#FFFFFF", "#FDE68A", "#FCA5A5", "#A5B4FC", "#86EFAC",
)

/**
 * Consumes residual fling velocity that bubbles up from the inner list, and a
 * top over-drag, so a fast scroll inside the sheet can never reach the sheet's
 * dismiss path. See docs/create-avatar-research.md §3.
 */
private class KeepSheetOpenConnection(
    private val canScrollUp: () -> Boolean,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // At the top, dragging further down would start dragging the sheet — eat it.
        return if (available.y > 0f && !canScrollUp() && source == NestedScrollSource.UserInput) {
            Offset(0f, available.y)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // Swallow everything the list didn't consume so the sheet never fling-dismisses.
        return available
    }
}

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
    val listState = rememberLazyListState()
    val keepOpen = remember(listState) { KeepSheetOpenConnection { listState.canScrollBackward } }
    val lives = BlobLifecycle.entries
    val shapes = BlobShape.entries
    val isGenerical = spec.eyes == BlobEyePack.Generical

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShapes.BottomSheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(keepOpen)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("header") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.avatar_create_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.avatar_create_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item("preview") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .clip(AppShapes.CardLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    BlobAvatar(
                        spec = spec,
                        modifier = Modifier.size(148.dp),
                        lifecycle = previewLifecycle,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        BlobAvatar(
                            spec = spec,
                            modifier = Modifier.size(34.dp),
                            lifecycle = previewLifecycle,
                        )
                    }
                }
            }

            item("eyePack") {
                SectionLabel(stringResource(R.string.avatar_eye_pack))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val packs = listOf(BlobEyePack.Generical, BlobEyePack.Grok)
                    packs.forEachIndexed { index, pack ->
                        SegmentedButton(
                            selected = spec.eyes == pack,
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                spec = spec.withPack(pack)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = packs.size),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (pack == BlobEyePack.Generical) stringResource(R.string.avatar_eye_pack_generical)
                                else stringResource(R.string.avatar_eye_pack_grok)
                            )
                        }
                    }
                }
                if (!isGenerical) {
                    Text(
                        text = stringResource(R.string.avatar_grok_fill_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item("lifecycle") {
                SectionLabel(stringResource(R.string.avatar_lifecycle))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    lives.forEach { state ->
                        FilterChip(
                            selected = previewLifecycle == state,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                previewLifecycle = state
                            },
                            label = { Text(lifecycleLabel(state)) },
                            shape = AppShapes.Chip,
                        )
                    }
                }
            }

            item("shape") {
                SectionLabel(stringResource(R.string.avatar_shape))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    shapes.forEach { shape ->
                        val selected = spec.shape == shape
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = AppShapes.CardSmall,
                                )
                                .clickable {
                                    haptics.perform(HapticPattern.Pop)
                                    spec = spec.copy(shape = shape)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BlobAvatar(
                                spec = spec.copy(shape = shape),
                                modifier = Modifier.size(46.dp),
                                lifecycle = previewLifecycle,
                            )
                        }
                    }
                }
            }

            item("bodyColor") {
                SectionLabel(stringResource(R.string.avatar_color))
                ColorRow(
                    selected = spec.color,
                    colors = BODY_COLORS,
                    onSelect = {
                        haptics.perform(HapticPattern.Pop)
                        spec = spec.copy(color = it)
                    },
                    onCustom = { pickingTarget = ColorTarget.Body; showColorPicker = true },
                )
            }

            item("eyeColor") {
                SectionLabel(stringResource(R.string.avatar_eye_color))
                ColorRow(
                    selected = spec.eyeColor,
                    colors = EYE_COLORS,
                    onSelect = {
                        haptics.perform(HapticPattern.Pop)
                        spec = spec.copy(eyeColor = it)
                    },
                    onCustom = { pickingTarget = ColorTarget.Eye; showColorPicker = true },
                )
            }

            item("knobs") {
                SectionLabel(stringResource(R.string.avatar_knobs))
                KnobSlider(
                    label = stringResource(R.string.avatar_eye_size),
                    value = spec.clampedEyeSize(), range = 0.6f..1.5f,
                    onChange = { spec = spec.copy(eyeSize = it) },
                )
                KnobSlider(
                    label = stringResource(R.string.avatar_eye_spacing),
                    value = spec.clampedEyeSpacing(), range = 0.65f..1.4f,
                    onChange = { spec = spec.copy(eyeSpacing = it) },
                )
                KnobSlider(
                    label = stringResource(R.string.avatar_eye_roundness),
                    value = spec.clampedEyeRoundness(), range = 0.1f..1f,
                    onChange = { spec = spec.copy(eyeRoundness = it) },
                )
                KnobSlider(
                    label = stringResource(R.string.avatar_look_around),
                    value = spec.clampedLookAround(), range = 0f..1.5f,
                    onChange = { spec = spec.copy(lookAround = it) },
                )
                FormItem(
                    label = { Text(stringResource(R.string.avatar_flat3d)) },
                    description = { Text(stringResource(R.string.avatar_flat3d_desc)) },
                    tail = {
                        HapticSwitch(
                            checked = spec.flat3d,
                            onCheckedChange = { spec = spec.copy(flat3d = it) },
                        )
                    },
                )
            }

            if (isGenerical) {
                item("glow") {
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
                            value = spec.clampedGlowStrength(), range = 0f..1f,
                            onChange = { spec = spec.copy(glowStrength = it) },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.avatar_glow_color),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ColorSwatch(
                                hex = spec.glowColor, selected = true,
                                onClick = { pickingTarget = ColorTarget.Glow; showColorPicker = true },
                            )
                        }
                    }
                }

                item("accent") {
                    SectionLabel(stringResource(R.string.avatar_accent))
                    Text(
                        text = stringResource(R.string.avatar_accent_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ColorRow(
                        selected = spec.accentColor,
                        colors = ACCENT_COLORS,
                        onSelect = {
                            haptics.perform(HapticPattern.Pop)
                            spec = spec.copy(accentColor = it)
                        },
                        onCustom = { pickingTarget = ColorTarget.Accent; showColorPicker = true },
                    )
                }
            }

            item("save") {
                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        onSave(spec)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    shape = AppShapes.ButtonRounded,
                ) {
                    Text(stringResource(R.string.avatar_save))
                }
            }
        }
    }

    if (showColorPicker) {
        val initialHex = when (pickingTarget) {
            ColorTarget.Body -> spec.color
            ColorTarget.Eye -> spec.eyeColor
            ColorTarget.Glow -> spec.glowColor
            ColorTarget.Accent -> spec.accentColor
        }
        BlobColorPickerDialog(
            initialHex = initialHex,
            onDismiss = { showColorPicker = false },
            onSave = { hex ->
                spec = when (pickingTarget) {
                    ColorTarget.Body -> spec.copy(color = hex)
                    ColorTarget.Eye -> spec.copy(eyeColor = hex)
                    ColorTarget.Glow -> spec.copy(glowColor = hex)
                    ColorTarget.Accent -> spec.copy(accentColor = hex)
                }
                showColorPicker = false
            },
        )
    }
}

private enum class ColorTarget { Body, Eye, Glow, Accent }

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun KnobSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    FormItem(label = { Text(label) }) {
        Slider(value = value, onValueChange = onChange, valueRange = range)
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
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val color = Color(parseArgb(hex))
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
private fun lifecycleLabel(state: BlobLifecycle): String = stringResource(
    when (state) {
        BlobLifecycle.Idle -> R.string.avatar_lifecycle_idle
        BlobLifecycle.Thinking -> R.string.avatar_lifecycle_thinking
        BlobLifecycle.Working -> R.string.avatar_lifecycle_working
        BlobLifecycle.Waiting -> R.string.avatar_lifecycle_waiting
        BlobLifecycle.Blocked -> R.string.avatar_lifecycle_blocked
        BlobLifecycle.Done -> R.string.avatar_lifecycle_done
    }
)

private fun Avatar.Blob.withPack(pack: BlobEyePack): Avatar.Blob {
    val toGrok = pack == BlobEyePack.Grok && eyes != BlobEyePack.Grok
    val toGenerical = pack == BlobEyePack.Generical && eyes != BlobEyePack.Generical
    return copy(
        eyes = pack,
        color = when {
            toGrok && color.equals(Avatar.Blob.DEFAULT_GENERICAL_COLOR, true) -> Avatar.Blob.DEFAULT_GROK_COLOR
            toGenerical && color.equals(Avatar.Blob.DEFAULT_GROK_COLOR, true) -> Avatar.Blob.DEFAULT_GENERICAL_COLOR
            else -> color
        },
        eyeColor = when {
            toGrok && eyeColor.equals(Avatar.Blob.DEFAULT_EYE_COLOR, true) -> Avatar.Blob.DEFAULT_GROK_EYE_COLOR
            toGenerical && eyeColor.equals(Avatar.Blob.DEFAULT_GROK_EYE_COLOR, true) -> Avatar.Blob.DEFAULT_EYE_COLOR
            else -> eyeColor
        },
        glowEnabled = if (toGrok) false else glowEnabled,
    )
}

@Composable
private fun BlobColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val initialColor = Color(parseArgb(initialHex))
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
                        .background(color),
                )
                SaturationBrightnessPicker(hue, saturation, brightness) { s, v ->
                    saturation = s; brightness = v
                }
                HueBar(hue) { hue = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(hex) }) { Text(stringResource(R.string.avatar_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.avatar_cancel)) } },
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
                    color = Color.Black, radius = 8.dp.toPx(), center = Offset(x, y),
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
            },
    )
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val hueColors = remember {
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
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
            },
    )
}
