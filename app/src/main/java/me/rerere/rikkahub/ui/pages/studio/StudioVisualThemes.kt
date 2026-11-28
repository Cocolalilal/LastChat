package me.rerere.rikkahub.ui.pages.studio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.StudioAppearanceSettings
import me.rerere.rikkahub.data.datastore.StudioAttributionStyle
import me.rerere.rikkahub.data.repository.StudioHubItem
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode

internal data class StudioNoteColorSpec(
    val token: String?,
    val label: String,
    val lightBg: Color,
    val darkBg: Color,
    val amoledBg: Color,
    val lightInk: Color,
    val darkInk: Color,
    val lightAccent: Color,
    val darkAccent: Color,
)

internal val STUDIO_NOTE_COLORS: List<StudioNoteColorSpec> = listOf(
    StudioNoteColorSpec(
        token = null,
        label = "Default",
        lightBg = Color.Unspecified,
        darkBg = Color.Unspecified,
        amoledBg = Color.Unspecified,
        lightInk = Color.Unspecified,
        darkInk = Color.Unspecified,
        lightAccent = Color.Unspecified,
        darkAccent = Color.Unspecified,
    ),
    StudioNoteColorSpec(
        token = "teal",
        label = "Deep Teal",
        lightBg = Color(0xFFD8EFEF),
        darkBg = Color(0xFF1B4D58),
        amoledBg = Color(0xFF11333B),
        lightInk = Color(0xFF0E2C33),
        darkInk = Color(0xFFE7F5F7),
        lightAccent = Color(0xFF0F766E),
        darkAccent = Color(0xFF7AD6DF),
    ),
    StudioNoteColorSpec(
        token = "sage",
        label = "Sage",
        lightBg = Color(0xFFE0F1E4),
        darkBg = Color(0xFF1E3E2E),
        amoledBg = Color(0xFF11261B),
        lightInk = Color(0xFF132A1D),
        darkInk = Color(0xFFE6F4EA),
        lightAccent = Color(0xFF2B6B44),
        darkAccent = Color(0xFF8CD7A5),
    ),
    StudioNoteColorSpec(
        token = "amber",
        label = "Honey",
        lightBg = Color(0xFFFFF1CE),
        darkBg = Color(0xFF4B3717),
        amoledBg = Color(0xFF2D200B),
        lightInk = Color(0xFF312208),
        darkInk = Color(0xFFFCF2DC),
        lightAccent = Color(0xFF9A5B09),
        darkAccent = Color(0xFFF3C66F),
    ),
    StudioNoteColorSpec(
        token = "terracotta",
        label = "Clay",
        lightBg = Color(0xFFFBE2DA),
        darkBg = Color(0xFF4F2A21),
        amoledBg = Color(0xFF301812),
        lightInk = Color(0xFF361912),
        darkInk = Color(0xFFFBEAE5),
        lightAccent = Color(0xFFA2442C),
        darkAccent = Color(0xFFF3A792),
    ),
    StudioNoteColorSpec(
        token = "sky",
        label = "Twilight",
        lightBg = Color(0xFFDCEBFA),
        darkBg = Color(0xFF1C3552),
        amoledBg = Color(0xFF0F2034),
        lightInk = Color(0xFF112438),
        darkInk = Color(0xFFE6F1FC),
        lightAccent = Color(0xFF1E5B9A),
        darkAccent = Color(0xFF8EC2F8),
    ),
    StudioNoteColorSpec(
        token = "rose",
        label = "Rose",
        lightBg = Color(0xFFFADEE6),
        darkBg = Color(0xFF4B2231),
        amoledBg = Color(0xFF2D121C),
        lightInk = Color(0xFF351320),
        darkInk = Color(0xFFFCE8EE),
        lightAccent = Color(0xFF9C3558),
        darkAccent = Color(0xFFF2A4BD),
    ),
    StudioNoteColorSpec(
        token = "moss",
        label = "Moss",
        lightBg = Color(0xFFEBF1D6),
        darkBg = Color(0xFF2F3C1E),
        amoledBg = Color(0xFF1C2410),
        lightInk = Color(0xFF212B13),
        darkInk = Color(0xFFF0F5E1),
        lightAccent = Color(0xFF4D6B1D),
        darkAccent = Color(0xFFB5D67D),
    ),
    StudioNoteColorSpec(
        token = "sand",
        label = "Sand",
        lightBg = Color(0xFFF4ECE2),
        darkBg = Color(0xFF3E342A),
        amoledBg = Color(0xFF251E18),
        lightInk = Color(0xFF2C231A),
        darkInk = Color(0xFFF5EFE7),
        lightAccent = Color(0xFF7D5D3B),
        darkAccent = Color(0xFFD9BE9E),
    ),
    StudioNoteColorSpec(
        token = "slate",
        label = "Slate",
        lightBg = Color(0xFFE2E9F0),
        darkBg = Color(0xFF253240),
        amoledBg = Color(0xFF151E27),
        lightInk = Color(0xFF16202A),
        darkInk = Color(0xFFE9F0F6),
        lightAccent = Color(0xFF3D5873),
        darkAccent = Color(0xFF9CB8D4),
    ),
    StudioNoteColorSpec(
        token = "plum",
        label = "Plum",
        lightBg = Color(0xFFEDE2F5),
        darkBg = Color(0xFF362544),
        amoledBg = Color(0xFF21152B),
        lightInk = Color(0xFF261733),
        darkInk = Color(0xFFF2E9FA),
        lightAccent = Color(0xFF6B428F),
        darkAccent = Color(0xFFC9A6E8),
    ),
    StudioNoteColorSpec(
        token = "charcoal",
        label = "Graphite",
        lightBg = Color(0xFFE8EAEE),
        darkBg = Color(0xFF23262B),
        amoledBg = Color(0xFF14161A),
        lightInk = Color(0xFF17191D),
        darkInk = Color(0xFFECEDF0),
        lightAccent = Color(0xFF4C535E),
        darkAccent = Color(0xFFAAB2BE),
    ),
)

internal data class StudioCanvasThemeSpec(
    val id: String?,
    val label: String,
)

internal val STUDIO_CANVAS_THEMES: List<StudioCanvasThemeSpec> = listOf(
    StudioCanvasThemeSpec(id = null, label = "Clean"),
    StudioCanvasThemeSpec(id = "dots", label = "Dot Grid"),
    StudioCanvasThemeSpec(id = "lined", label = "Ruled"),
    StudioCanvasThemeSpec(id = "blueprint", label = "Studio Grid"),
    StudioCanvasThemeSpec(id = "dusk", label = "Warm Dusk"),
    StudioCanvasThemeSpec(id = "botanical", label = "Botanical"),
    StudioCanvasThemeSpec(id = "constellation", label = "Starfield"),
    StudioCanvasThemeSpec(id = "paper", label = "Folio"),
)

internal data class StudioResolvedPalette(
    val container: Color,
    val onContainer: Color,
    val onContainerMuted: Color,
    val chipContainer: Color,
    val chipContent: Color,
    val accent: Color,
    val border: Color,
    val toolbarContainer: Color,
    val isCustomColored: Boolean,
)

internal fun normalizeColorToken(token: String?): String? {
    return when (token?.trim()?.lowercase()) {
        null, "" -> null
        "lavender" -> "plum"
        else -> token.trim().lowercase()
    }
}

@Composable
internal fun rememberStudioPalette(colorToken: String?): StudioResolvedPalette {
    val isDark = LocalDarkMode.current
    val amoled by rememberAmoledDarkMode()
    val normalized = normalizeColorToken(colorToken)
    val spec = STUDIO_NOTE_COLORS.firstOrNull { it.token == normalized }

    if (spec == null || spec.token == null) {
        val bg = if (amoled && isDark) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
        return StudioResolvedPalette(
            container = bg,
            onContainer = MaterialTheme.colorScheme.onSurface,
            onContainerMuted = MaterialTheme.colorScheme.onSurfaceVariant,
            chipContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
            chipContent = MaterialTheme.colorScheme.onSurfaceVariant,
            accent = MaterialTheme.colorScheme.primary,
            border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.42f else 0.55f),
            toolbarContainer = if (amoled && isDark) Color(0xFF0E1013) else MaterialTheme.colorScheme.surfaceContainer,
            isCustomColored = false,
        )
    }

    val container = when {
        amoled && isDark -> spec.amoledBg
        isDark -> spec.darkBg
        else -> spec.lightBg
    }
    val ink = if (isDark) spec.darkInk else spec.lightInk
    val accent = if (isDark) spec.darkAccent else spec.lightAccent

    return StudioResolvedPalette(
        container = container,
        onContainer = ink,
        onContainerMuted = ink.copy(alpha = if (isDark) 0.74f else 0.72f),
        chipContainer = ink.copy(alpha = if (isDark) 0.13f else 0.10f),
        chipContent = ink.copy(alpha = if (isDark) 0.88f else 0.86f),
        accent = accent,
        border = ink.copy(alpha = if (isDark) 0.16f else 0.14f),
        toolbarContainer = container,
        isCustomColored = true,
    )
}

@Composable
internal fun StudioCanvasThemeBackdrop(
    themeId: String?,
    inkColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isCardPreview: Boolean = false,
) {
    if (themeId.isNullOrBlank()) return
    val baseAlpha = if (isCardPreview) 0.085f else 0.095f

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        when (themeId) {
            "dots" -> {
                val step = if (isCardPreview) 16.dp.toPx() else 22.dp.toPx()
                val radius = if (isCardPreview) 1.0.dp.toPx() else 1.25.dp.toPx()
                val dotColor = inkColor.copy(alpha = baseAlpha * 1.45f)
                var y = step * 0.7f
                while (y < h) {
                    var x = step * 0.7f
                    while (x < w) {
                        drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }

            "lined" -> {
                val lineSpacing = if (isCardPreview) 18.dp.toPx() else 28.dp.toPx()
                val lineColor = inkColor.copy(alpha = baseAlpha * 1.15f)
                var y = lineSpacing * 1.2f
                while (y < h) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    y += lineSpacing
                }
                if (!isCardPreview) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.14f),
                        start = Offset(20.dp.toPx(), 0f),
                        end = Offset(20.dp.toPx(), h),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            "blueprint" -> {
                val step = if (isCardPreview) 18.dp.toPx() else 26.dp.toPx()
                val lineColor = inkColor.copy(alpha = baseAlpha)
                var x = step
                while (x < w) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.8.dp.toPx())
                    x += step
                }
                var y = step
                while (y < h) {
                    drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.8.dp.toPx())
                    y += step
                }
            }

            "dusk" -> {
                val wave1 = Path().apply {
                    moveTo(0f, h * 0.78f)
                    cubicTo(w * 0.28f, h * 0.68f, w * 0.68f, h * 0.88f, w, h * 0.74f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                val wave2 = Path().apply {
                    moveTo(0f, h * 0.86f)
                    cubicTo(w * 0.35f, h * 0.94f, w * 0.72f, h * 0.76f, w, h * 0.88f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(wave1, color = accentColor.copy(alpha = 0.09f))
                drawPath(wave2, color = inkColor.copy(alpha = 0.06f))
            }

            "botanical" -> {
                val strokeColor = accentColor.copy(alpha = 0.16f)
                val stem = Path().apply {
                    moveTo(w * 0.94f, h * 1.02f)
                    quadraticTo(w * 0.78f, h * 0.84f, w * 0.86f, h * 0.62f)
                }
                drawPath(stem, color = strokeColor, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
                val leaf1 = Path().apply {
                    moveTo(w * 0.84f, h * 0.78f)
                    quadraticTo(w * 0.70f, h * 0.74f, w * 0.68f, h * 0.66f)
                    quadraticTo(w * 0.78f, h * 0.68f, w * 0.84f, h * 0.78f)
                }
                val leaf2 = Path().apply {
                    moveTo(w * 0.85f, h * 0.68f)
                    quadraticTo(w * 0.96f, h * 0.62f, w * 0.98f, h * 0.54f)
                    quadraticTo(w * 0.89f, h * 0.57f, w * 0.85f, h * 0.68f)
                }
                drawPath(leaf1, color = accentColor.copy(alpha = 0.08f))
                drawPath(leaf1, color = strokeColor, style = Stroke(width = 1.2.dp.toPx()))
                drawPath(leaf2, color = accentColor.copy(alpha = 0.08f))
                drawPath(leaf2, color = strokeColor, style = Stroke(width = 1.2.dp.toPx()))
            }

            "constellation" -> {
                val starColor = inkColor.copy(alpha = 0.18f)
                val arcColor = accentColor.copy(alpha = 0.11f)
                val points = listOf(
                    Offset(w * 0.78f, h * 0.16f),
                    Offset(w * 0.90f, h * 0.24f),
                    Offset(w * 0.82f, h * 0.34f),
                    Offset(w * 0.68f, h * 0.28f),
                    Offset(w * 0.14f, h * 0.82f),
                    Offset(w * 0.24f, h * 0.89f),
                )
                drawLine(arcColor, points[0], points[1], strokeWidth = 0.9.dp.toPx())
                drawLine(arcColor, points[1], points[2], strokeWidth = 0.9.dp.toPx())
                drawLine(arcColor, points[2], points[3], strokeWidth = 0.9.dp.toPx())
                drawLine(arcColor, points[4], points[5], strokeWidth = 0.9.dp.toPx())
                points.forEachIndexed { idx, pt ->
                    drawCircle(
                        color = starColor,
                        radius = if (idx % 2 == 0) 2.dp.toPx() else 1.3.dp.toPx(),
                        center = pt,
                    )
                }
            }

            "paper" -> {
                val frameInset = if (isCardPreview) 6.dp.toPx() else 12.dp.toPx()
                val cornerLen = if (isCardPreview) 10.dp.toPx() else 18.dp.toPx()
                val stroke = inkColor.copy(alpha = 0.14f)
                val sw = 1.dp.toPx()
                // Top-left & bottom-right editorial corner marks
                drawLine(stroke, Offset(frameInset, frameInset), Offset(frameInset + cornerLen, frameInset), sw)
                drawLine(stroke, Offset(frameInset, frameInset), Offset(frameInset, frameInset + cornerLen), sw)
                drawLine(stroke, Offset(w - frameInset, h - frameInset), Offset(w - frameInset - cornerLen, h - frameInset), sw)
                drawLine(stroke, Offset(w - frameInset, h - frameInset), Offset(w - frameInset, h - frameInset - cornerLen), sw)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            inkColor.copy(alpha = 0.03f),
                        )
                    )
                )
            }
        }
    }
}

internal fun resolveStudioCreatorName(
    item: StudioHubItem,
    settings: Settings,
): String? {
    if (item.createdBy == "user") return null
    val explicitName = item.createdByAssistantName?.trim()?.takeIf { it.isNotBlank() }
    if (explicitName != null) return explicitName
    val byId = item.createdByAssistantId?.let { idStr ->
        settings.assistants.firstOrNull { it.id.toString() == idStr }?.name?.trim()?.takeIf { it.isNotBlank() }
    }
    if (byId != null) return byId
    val activeName = settings.assistants.firstOrNull { it.id == settings.assistantId }
        ?.name?.trim()?.takeIf { it.isNotBlank() }
        ?: settings.assistants.firstOrNull()?.name?.trim()?.takeIf { it.isNotBlank() }
    return when (item.createdBy) {
        "quick_ask" -> activeName ?: "Quick Ask"
        else -> activeName ?: "Assistant"
    }
}

internal fun formatStudioAttributionLabel(
    creatorName: String,
    formatTemplate: String,
): String {
    val cleanFormat = formatTemplate.trim().ifBlank { "Created by {name}" }
    return if (cleanFormat.contains("{name}")) {
        cleanFormat.replace("{name}", creatorName)
    } else {
        "$cleanFormat $creatorName"
    }
}

@Composable
internal fun StudioAttributionBadge(
    creatorName: String,
    appearance: StudioAppearanceSettings,
    palette: StudioResolvedPalette,
    modifier: Modifier = Modifier,
) {
    if (appearance.attributionStyle == StudioAttributionStyle.HIDDEN) return
    val formatted = formatStudioAttributionLabel(creatorName, appearance.attributionLabelFormat)

    when (appearance.attributionStyle) {
        StudioAttributionStyle.SIGNATURE -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                )
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                        letterSpacing = 0.2.sp,
                    ),
                    color = palette.onContainerMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        StudioAttributionStyle.MONOGRAM -> {
            val initial = creatorName.firstOrNull()?.uppercase() ?: "A"
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(palette.accent.copy(alpha = 0.18f))
                        .border(0.75.dp, palette.accent.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = palette.accent,
                    )
                }
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = palette.onContainerMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        StudioAttributionStyle.CHIP -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.chipContainer,
                border = BorderStroke(0.5.dp, palette.border),
                modifier = modifier,
            ) {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = palette.chipContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        StudioAttributionStyle.HIDDEN -> Unit
    }
}

/**
 * Converts basic inline Markdown (**bold**, *italic*, `code`, heading hashes, bullet hyphens)
 * into a clean, readable AnnotatedString so Studio cards and Reader Mode never display raw syntax clutter.
 */
internal fun buildCleanMarkdownAnnotatedString(
    raw: String,
    codeBackground: Color = Color.Transparent,
): AnnotatedString = buildAnnotatedString {
    val lines = raw.lines()
    lines.forEachIndexed { lineIdx, originalLine ->
        if (lineIdx > 0) append('\n')
        var line = originalLine.trimEnd()
        var isHeading = false
        val headingMatch = Regex("^#{1,6}\\s+(.+)$").matchEntire(line.trimStart())
        if (headingMatch != null) {
            line = headingMatch.groupValues[1]
            isHeading = true
        } else if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            val trimmed = line.trimStart()
            line = "• " + trimmed.drop(2)
        }

        val startOfLine = length
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    val end = line.indexOf("**", i + 2)
                    if (end > i + 2) {
                        val content = line.substring(i + 2, end)
                        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                        append(content)
                        pop()
                        i = end + 2
                    } else {
                        append(line[i])
                        i++
                    }
                }

                line.startsWith("`", i) -> {
                    val end = line.indexOf('`', i + 1)
                    if (end > i + 1) {
                        val content = line.substring(i + 1, end)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                            )
                        )
                        append(content)
                        pop()
                        i = end + 1
                    } else {
                        append(line[i])
                        i++
                    }
                }

                line.startsWith("*", i) && !line.startsWith("**", i) -> {
                    val end = line.indexOf('*', i + 1)
                    if (end > i + 1 && !line.substring(i + 1, end).contains('\n')) {
                        val content = line.substring(i + 1, end)
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(content)
                        pop()
                        i = end + 1
                    } else {
                        append(line[i])
                        i++
                    }
                }

                else -> {
                    append(line[i])
                    i++
                }
            }
        }
        if (isHeading && length > startOfLine) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), startOfLine, length)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioColorAndThemePickerSheet(
    selectedColorToken: String?,
    selectedThemeId: String?,
    onSelectColor: (String?) -> Unit,
    onSelectTheme: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val isDark = LocalDarkMode.current
    val amoled by rememberAmoledDarkMode()
    val currentPalette = rememberStudioPalette(selectedColorToken)
    val normalizedSelected = normalizeColorToken(selectedColorToken)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Color & Background",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Give this note its own palette and subtle canvas texture",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Color palette rail
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "COLOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(STUDIO_NOTE_COLORS, key = { it.token ?: "default" }) { spec ->
                        val isSelected = normalizedSelected == spec.token
                        val swatchColor = if (spec.token == null) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else when {
                            amoled && isDark -> spec.amoledBg
                            isDark -> spec.darkBg
                            else -> spec.lightBg
                        }
                        val checkTint = if (spec.token == null) {
                            MaterialTheme.colorScheme.primary
                        } else if (isDark) {
                            spec.darkInk
                        } else {
                            spec.lightInk
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptics.perform(HapticPattern.Selection)
                                    onSelectColor(spec.token)
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    isSelected -> Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = spec.label,
                                        tint = checkTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    spec.token == null -> Icon(
                                        imageVector = Icons.Rounded.FormatColorReset,
                                        contentDescription = "Default",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                text = spec.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Canvas theme rail
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CANVAS THEME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(STUDIO_CANVAS_THEMES, key = { it.id ?: "clean" }) { theme ->
                        val isSelected = selectedThemeId == theme.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    haptics.perform(HapticPattern.Selection)
                                    onSelectTheme(theme.id)
                                },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(currentPalette.container)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            currentPalette.border
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    ),
                            ) {
                                StudioCanvasThemeBackdrop(
                                    themeId = theme.id,
                                    inkColor = currentPalette.onContainer,
                                    accentColor = currentPalette.accent,
                                    isCardPreview = true,
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = theme.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StudioAppearanceSettingsSheet(
    settings: Settings,
    activeAssistantName: String,
    enhanceModelName: String,
    onUpdateAppearance: (StudioAppearanceSettings) -> Unit,
    onOpenDefaultModelsSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val appearance = settings.studioAppearance
    val previewPalette = rememberStudioPalette(appearance.defaultNoteColorToken ?: "teal")
    val labelPresets = listOf(
        "Created by {name}",
        "by {name}",
        "Curated by {name}",
        "Written with {name}",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = "Studio Customization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Assistant attribution, default canvas, and AI enhance model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Live attribution preview card
            val animPreviewBg by animateColorAsState(
                targetValue = previewPalette.container,
                animationSpec = tween(if (LocalMotionPolicy.current.reduceMotion) 0 else 220),
                label = "preview_card_bg",
            )
            Surface(
                shape = AppShapes.CardMedium,
                color = animPreviewBg,
                border = BorderStroke(1.dp, previewPalette.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    StudioCanvasThemeBackdrop(
                        themeId = appearance.defaultNoteThemeId,
                        inkColor = previewPalette.onContainer,
                        accentColor = previewPalette.accent,
                        isCardPreview = true,
                        modifier = Modifier.matchParentSize(),
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StudioAttributionBadge(
                            creatorName = activeAssistantName,
                            appearance = appearance,
                            palette = previewPalette,
                        )
                        Text(
                            text = "Weekend reading list",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = previewPalette.onContainer,
                        )
                        Text(
                            text = "Notes created by $activeAssistantName use your chosen signature and default canvas style.",
                            style = MaterialTheme.typography.bodySmall,
                            color = previewPalette.onContainerMuted,
                        )
                    }
                }
            }

            // Attribution style selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ASSISTANT ATTRIBUTION STYLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val styles = listOf(
                        StudioAttributionStyle.SIGNATURE to "Signature",
                        StudioAttributionStyle.MONOGRAM to "Monogram",
                        StudioAttributionStyle.CHIP to "Soft Tag",
                        StudioAttributionStyle.HIDDEN to "Hidden",
                    )
                    styles.forEach { (style, label) ->
                        val selected = appearance.attributionStyle == style
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.perform(HapticPattern.Selection)
                                onUpdateAppearance(appearance.copy(attributionStyle = style))
                            },
                            label = { Text(label) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // Attribution format templates
            if (appearance.attributionStyle != StudioAttributionStyle.HIDDEN) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "LABEL WORDING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        labelPresets.forEach { preset ->
                            val selected = appearance.attributionLabelFormat == preset
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptics.perform(HapticPattern.Selection)
                                    onUpdateAppearance(appearance.copy(attributionLabelFormat = preset))
                                },
                                label = {
                                    Text(formatStudioAttributionLabel(activeAssistantName, preset))
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = appearance.attributionLabelFormat,
                        onValueChange = { onUpdateAppearance(appearance.copy(attributionLabelFormat = it)) },
                        label = { Text("Custom template (use {name})") },
                        singleLine = true,
                        shape = AppShapes.InputField,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Default AI Enhance Model shortcut
            Surface(
                shape = AppShapes.CardSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = {
                    haptics.perform(HapticPattern.Selection)
                    onDismiss()
                    onOpenDefaultModelsSettings()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enhance Model",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = enhanceModelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Change model",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Greeting header toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome header",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Show time-of-day greeting and summary at top of Studio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = appearance.showGreetingHeader,
                    onCheckedChange = { enabled ->
                        haptics.perform(HapticPattern.Pop)
                        onUpdateAppearance(appearance.copy(showGreetingHeader = enabled))
                    },
                )
            }
        }
    }
}
