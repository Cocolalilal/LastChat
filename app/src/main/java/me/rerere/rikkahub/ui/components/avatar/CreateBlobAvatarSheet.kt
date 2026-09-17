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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
    var pickingGlow by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShapes.BottomSheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                BlobAvatar(
                    spec = spec,
                    modifier = Modifier.size(160.dp),
                    lifecycle = previewLifecycle,
                )
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

            Text(
                text = stringResource(R.string.avatar_lifecycle),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BlobLifecycle.entries.forEach { state ->
                    FilterChip(
                        selected = previewLifecycle == state,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            previewLifecycle = state
                        },
                        label = { Text(lifecycleLabel(state)) },
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
                BlobShape.entries.forEach { shape ->
                    val selected = spec.shape == shape
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(AppShapes.CardSmall)
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
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BlobAvatar(
                            spec = spec.copy(shape = shape),
                            modifier = Modifier.size(44.dp),
                            lifecycle = BlobLifecycle.Idle,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.avatar_color),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PRESET_COLORS.forEach { hex ->
                    val selected = spec.color.equals(hex, ignoreCase = true)
                    ColorSwatch(
                        hex = hex,
                        selected = selected,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            spec = spec.copy(color = hex)
                        },
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
                        .clickable {
                            pickingGlow = false
                            showColorPicker = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.avatar_custom_color),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (spec.eyes == BlobEyePack.Generical) {
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
                                pickingGlow = true
                                showColorPicker = true
                            },
                        )
                    }
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

    if (showColorPicker) {
        BlobColorPickerDialog(
            initialHex = if (pickingGlow) spec.glowColor else spec.color,
            onDismiss = { showColorPicker = false },
            onSave = { hex ->
                spec = if (pickingGlow) spec.copy(glowColor = hex) else spec.copy(color = hex)
                showColorPicker = false
            },
        )
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
        glowEnabled = when {
            switchingToGrok -> false
            switchingToGenerical -> true
            else -> glowEnabled
        },
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
                drawCircle(Color.Black, radius = 8.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
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
