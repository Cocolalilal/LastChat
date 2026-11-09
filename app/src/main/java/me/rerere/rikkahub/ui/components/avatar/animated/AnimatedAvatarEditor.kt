package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
    val start = initialAvatar ?: Avatar.Animated.defaultGenerical()
    val initialShape = start.shape
    var shape by remember {
        mutableStateOf(
            MarkShapes.migrate(initialShape)
        )
    }
    var eyeType by remember { mutableStateOf(start.eyeType) }
    // Canonical pair: the selected swatch is exactly the colour on screen.
    val startBody = remember { MarkColors.canonicalBody(start.colorHex, start.colorPreset, start.eyeType) }
    var colorHex by remember { mutableStateOf(startBody.first) }
    var colorPreset by remember { mutableStateOf(startBody.second) }
    var eyeColorHex by remember { mutableStateOf(initialAvatar?.eyeColorHex) }
    var eyeShade by remember { mutableStateOf(initialAvatar?.eyeShade ?: true) }
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
            initialHex = eyeColorHex ?: MarkColors.defaultEyeHex(eyeType),
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

    // Engine16.2c: sheet drag gestures off. With them on, a downward drag/fling at the top of
    // the scrolled content was handed to the sheet through nested scroll and dismissed the
    // editor. Dismiss stays explicit: Cancel, system back, or tapping the scrim.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, sheetGesturesEnabled = false) {
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
            eyeShade = eyeShade,
            onEyeShade = { eyeShade = it },
            onPickColor = { showColorPicker = true },
            onPickEyeColor = { showEyeColorPicker = true },
            onDismiss = onDismiss,
            onSave = {
                onSave(
                    Avatar.Animated(
                        stockColorVersion = 1,
                        shape = shape,
                        eyeType = eyeType.lowercase(),
                        colorHex = MarkColors.normalizeHex(colorHex),
                        colorPreset = colorPreset,
                        eyeColorHex = eyeColorHex?.let { MarkColors.normalizeHex(it) },
                        eyeShade = eyeShade,
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
internal fun defaultEyeHex(eyeType: String): String = MarkColors.defaultEyeHex(eyeType)

/**
 * Eye-colour swatches: identical order for both eye styles (white = the default first,
 * then ink and the colours).
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
    eyeShade: Boolean = true,
    onEyeShade: (Boolean) -> Unit = {},
    onPickColor: () -> Unit,
    onPickEyeColor: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    /** False only in the static render harness (Robolectric can't idle a live frame loop). */
    livePreview: Boolean = true,
) {
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(80.dp) }
    val cs = MaterialTheme.colorScheme
    val bodyColor = MarkColors.hexToColor(colorHex)
    val isGenerical = eyeType.equals("generical", ignoreCase = true)
    // Engine16.2: every change becomes a cue the live preview may notice (it glances at the
    // control's position, sometimes reacts after you stop).
    var cue by remember { mutableStateOf<EditorCue?>(null) }
    val spots = remember { HashMap<EditorCue.Kind, androidx.compose.ui.geometry.Offset>() }
    fun spot(k: EditorCue.Kind) = Modifier.onGloballyPositioned { spots[k] = it.boundsInWindow().center }
    fun fire(k: EditorCue.Kind) { cue = EditorCue((cue?.seq ?: 0) + 1, k, spots[k]) }
    // Engine17.2: avatars here sit on the editor container surface (contrast guard backdrop).
    androidx.compose.runtime.CompositionLocalProvider(ContrastGuard.LocalAvatarBackdrop provides cs.surfaceContainerLow) { // Engine20.1: previews sit on the sheet
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = barHeight + 72.dp), // Engine20: the end scrolls well clear of the buttons
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Stage: the live avatar on a soft spotlight of its own colour.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp), // Engine20.1: no surface behind the preview; only the label has a pill
                contentAlignment = Alignment.Center,
            ) {
                AnimatedMarkAvatar(
                    shapeId = shape,
                    eyeType = eyeType,
                    colorHex = colorHex,
                    colorPreset = colorPreset,
                    eyeColorHex = eyeColorHex,
                    eyeShade = eyeShade,
                    isLoading = false,
                    calmPreview = !livePreview,
                    editorCue = cue,
                    modifier = Modifier.size(164.dp),
                )
                Text(
                    text = (if (isGenerical) stringResource(R.string.avatar_animated_eye_generical) else stringResource(R.string.avatar_animated_eye_grok)) +
                        "  ·  " + shape.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(CircleShape)
                        .background(softSurface(0.5f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // ---- Shape
            EditorSection(stringResource(R.string.avatar_animated_shape), card = false) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().then(spot(EditorCue.Kind.Shape)),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MarkShapes.SHAPES.forEach { s ->
                        val sel = shape == s
                        // Engine20: no tile behind the shapes; the selection is a ring that follows
                        // the shape's own contour with a small gap, fading in.
                        val ringK by androidx.compose.animation.core.animateFloatAsState(if (sel) 1f else 0f,
                            androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), label = "shapeRing")
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .drawBehind {
                                    if (ringK > 0.01f) {
                                        // Engine20.1: a true constant-distance band around the drawn
                                        // silhouette of the 36 dp preview (real geometry, see SelectionRing).
                                        val inner = SHAPE_PREVIEW.toPx()
                                        val ring = SelectionRing.ring(s, inner, SEL_GAP.toPx(), SEL_STROKE.toPx(), SEL_ROUND.toPx())
                                        if (ring != null) {
                                            val o = (size.width - inner) / 2f
                                            val g = 0.94f + 0.06f * ringK
                                            withTransform({ translate(o, o); scale(g, g, androidx.compose.ui.geometry.Offset(inner / 2f, inner / 2f)) }) {
                                                drawPath(ring, bodyColor.copy(alpha = ringK))
                                            }
                                        }
                                    }
                                }
                                .clip(CircleShape)
                                .clickable { if (!sel) { onShape(s); fire(EditorCue.Kind.Shape) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedMarkAvatar(
                                shapeId = s,
                                eyeType = eyeType,
                                colorHex = colorHex,
                                colorPreset = colorPreset,
                                eyeColorHex = eyeColorHex,
                                eyeShade = eyeShade,
                                isLoading = false,
                                calmPreview = true,
                                modifier = Modifier.size(SHAPE_PREVIEW),
                            )
                        }
                    }
                }
            }

            // ---- Body colour
            EditorSection(stringResource(R.string.avatar_animated_color)) {
                SwatchRow(spot(EditorCue.Kind.BodyColor)) {
                    MarkColors.PRESETS.forEach { (name, hex) ->
                        Swatch(MarkColors.hexToColor(hex), selected = colorPreset == name) { onColor(hex, name); fire(EditorCue.Kind.BodyColor) }
                    }
                    CustomSwatch(current = if (colorPreset == null) bodyColor else null, contentDescription = stringResource(R.string.avatar_animated_color), onClick = onPickColor)
                }
            }

            // ---- Eyes: style, colour, shade
            EditorSection(stringResource(R.string.avatar_animated_eyes)) {
                // Engine20: the track is the sheet (screen) colour, the sliding indicator the
                // colour of this card.
                val eyeIds = listOf("generical" to R.string.avatar_animated_eye_generical, "grok" to R.string.avatar_animated_eye_grok)
                val selIdx = eyeIds.indexOfFirst { eyeType.equals(it.first, ignoreCase = true) }.coerceAtLeast(0)
                val slide by androidx.compose.animation.core.animateFloatAsState(selIdx.toFloat(),
                    androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 420f), label = "eyeSeg")
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(spot(EditorCue.Kind.EyeStyle))
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainerLow)
                        .padding(4.dp),
                ) {
                    val segW = (maxWidth - 4.dp) / 2
                    Box(
                        Modifier
                            .matchParentSize()
                            .padding(end = segW + 4.dp)
                            .graphicsLayer { translationX = slide * (segW + 4.dp).toPx() }
                            .clip(RoundedCornerShape(12.dp))
                            .background(softSurface(0f)),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((id, label) in eyeIds) {
                        val sel = eyeType.equals(id, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { if (!sel) { onEyeType(id); fire(EditorCue.Kind.EyeStyle) } }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedMarkAvatar(
                                shapeId = "blob", eyeType = id, colorHex = colorHex, colorPreset = colorPreset,
                                isLoading = false, calmPreview = true, modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(label),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sel) cs.onSurface else cs.onSurfaceVariant,
                            )
                        }
                    }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.avatar_animated_eye_color), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val def = defaultEyeHex(eyeType)
                val current = MarkColors.normalizeHex(eyeColorHex?.takeIf { it.isNotBlank() } ?: def)
                val swatches = eyeSwatches(eyeType)
                // Engine20: the per-type options animate in and out when the eye type changes.
                androidx.compose.animation.AnimatedContent(
                    // Keyed on the option set: animates only when the options really change.
                    targetState = swatches.joinToString(),
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, 60)) +
                            androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.7f, 500f), initialScale = 0.85f))
                            .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(140), targetScale = 0.9f))
                            .using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    label = "eyeSwatches",
                ) { _ ->
                SwatchRow(spot(EditorCue.Kind.EyeColor)) {
                    swatches.forEach { hex ->
                        val isDefault = hex.equals(def, ignoreCase = true)
                        // Picking the mode default stores null so it tracks the mode.
                        Swatch(MarkColors.hexToColor(hex), selected = current.equals(hex, ignoreCase = true),
                            contentDescription = if (isDefault) stringResource(R.string.avatar_animated_eye_color_default) else null,
                        ) { onEyeColor(if (isDefault) null else hex); fire(EditorCue.Kind.EyeColor) }
                    }
                    val custom = eyeColorHex?.takeIf { h -> swatches.none { it.equals(MarkColors.normalizeHex(h), ignoreCase = true) } }
                    CustomSwatch(current = custom?.let { MarkColors.hexToColor(it) }, contentDescription = stringResource(R.string.avatar_animated_eye_color), onClick = onPickEyeColor)
                }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = isGenerical,
                    enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(0.85f, 420f)) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, 80)),
                    exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
                ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(spot(EditorCue.Kind.Shade))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onEyeShade(!eyeShade); fire(EditorCue.Kind.Shade) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.avatar_animated_eye_shade),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurface,
                        )
                        Spacer(Modifier.width(16.dp))
                        androidx.compose.material3.Switch(checked = eyeShade, onCheckedChange = { onEyeShade(it); fire(EditorCue.Kind.Shade) })
                    }
                }
                }
            }
        }

        // Actions in the first-time-setup family: soft fade from the sheet colour, two calm
        // 52 dp pills (outlined secondary with a thick soft ring, filled primary), bold
        // titleMedium labels, springy press.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, cs.surfaceContainerLow.copy(alpha = 0.92f), cs.surfaceContainerLow)
                    )
                )
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 20.dp)
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } - 16.dp },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditorActionButton(stringResource(R.string.avatar_cancel), primary = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                EditorActionButton(stringResource(R.string.avatar_animated_save), primary = true, icon = Icons.Rounded.Check, onClick = onSave, modifier = Modifier.weight(1f))
            }
        }
    }
    }
}

/** Engine20: one selection-ring weight and gap for shapes and swatches. */
private val SEL_STROKE = 2.5.dp
private val SEL_GAP = 3.dp
private val SHAPE_PREVIEW = 36.dp
/** Fillet radius for concave corners of the shape rings. */
private val SEL_ROUND = 3.dp

@Composable
private fun EditorActionButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.92f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "editorAction",
    )
    val shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill
    val mod = modifier.height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    val label: @Composable () -> Unit = {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
    }
    if (primary) {
        Button(onClick = onClick, interactionSource = interaction, shape = shape, modifier = mod,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
        ) { label() }
    } else {
        androidx.compose.material3.OutlinedButton(onClick = onClick, interactionSource = interaction, shape = shape, modifier = mod,
            border = androidx.compose.foundation.BorderStroke(3.dp, softSurface(0.55f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = cs.surfaceContainerLow, contentColor = cs.onSurface),
        ) { label() }
    }
}

/**
 * Engine16.2c: low-contrast editor surfaces - a small step from the sheet colour toward
 * surfaceContainerHigh ([k] 0…1), so cards read as gentle tints in light and dark themes.
 */
@Composable
private fun softSurface(k: Float): Color {
    // Engine17.2: editor containers use the exact surface the drawer search bar
    // (LastChatDrawerSearch -> TextFieldDefaults.colors() filled container) uses:
    // colorScheme.surfaceContainerHighest, in both themes. `k` kept for call sites.
    @Suppress("UNUSED_PARAMETER") val unused = k
    return MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun EditorSection(title: String, card: Boolean = true, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (card) Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(softSurface(0.22f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                else Modifier.padding(vertical = 4.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Centred rows that wrap (nothing hides off-screen behind a scroll).
    FlowRow(
        modifier = Modifier.fillMaxWidth().then(modifier).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** Colour dot; selection is a ring with a gap (no check glyph over the colour). */
@Composable
private fun Swatch(color: Color, selected: Boolean, contentDescription: String? = null, onClick: () -> Unit) {
    // Engine20: the ring is the selected colour itself, as thick as the shape ring
    // (a faint edge keeps a near-background colour's ring visible).
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val weak = kotlin.math.abs(color.luminance() - bg.luminance()) < 0.08f
    val ring = if (weak) androidx.compose.ui.graphics.lerp(color, MaterialTheme.colorScheme.onSurface, 0.35f) else color
    val k by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), label = "swatchRing")
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (k > 0.01f) Modifier.border(SEL_STROKE, ring.copy(alpha = k), CircleShape) else Modifier)
            .clickable(onClickLabel = contentDescription) { onClick() }
            .padding(3.dp + (SEL_STROKE + SEL_GAP - 3.dp) * k) // exact: ring, then the same gap as the shapes
            .clip(CircleShape)
            .background(color)
            .then(
                when {
                    color.luminance() < 0.08f -> Modifier.border(1.dp, Color(0x33FFFFFF), CircleShape)
                    color.luminance() > 0.9f -> Modifier.border(1.dp, Color(0x22000000), CircleShape)
                    else -> Modifier
                }
            ),
    )
}

/** "Custom…" swatch: a colour-wheel ring; filled with the custom colour once one is chosen. */
@Composable
private fun CustomSwatch(current: Color?, contentDescription: String, onClick: () -> Unit) {
    val wheel = androidx.compose.ui.graphics.Brush.sweepGradient(
        listOf(Color(0xFFFF453A), Color(0xFFFFD60A), Color(0xFF30D158), Color(0xFF64D2FF), Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF453A))
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(if (current != null) 2.5.dp else 2.dp, wheel, CircleShape)
            .clickable { onClick() }
            .padding(5.dp)
            .clip(CircleShape)
            .background(current ?: MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Edit,
            contentDescription = contentDescription,
            tint = when {
                current == null -> MaterialTheme.colorScheme.onSurfaceVariant
                current.luminance() > 0.5f -> Color.Black
                else -> Color.White
            },
            modifier = Modifier.size(16.dp),
        )
    }
}
