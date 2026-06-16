package me.rerere.rikkahub.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.InputStream

private const val PALETTE_TARGET_SIZE = 128
private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

@Composable
fun AssistantChatTheme(
    assistant: Assistant,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = LocalDarkMode.current
    val seedColor by produceState<Color?>(
        initialValue = null,
        assistant.useAssistantMaterialYouColors,
        assistant.materialYouColorIndex,
        assistant.avatar,
        assistant.background
    ) {
        if (!assistant.useAssistantMaterialYouColors) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            extractSeedColor(
                context = context,
                assistant = assistant,
                colorIndex = assistant.materialYouColorIndex
            )
        }
    }

    if (seedColor == null) {
        content()
        return
    }

    val scheme = buildAssistantColorScheme(
        baseScheme = MaterialTheme.colorScheme,
        seedColor = seedColor!!,
        darkTheme = darkTheme
    ).let { baseScheme ->
        if (darkTheme) {
            baseScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = AMOLED_DARK_BACKGROUND
            )
        } else {
            baseScheme
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

private suspend fun extractSeedColor(
    context: Context,
    assistant: Assistant,
    colorIndex: Int = 0
): Color? {
    val candidates = extractColorCandidates(context, assistant)
    if (candidates.isEmpty()) return null
    return candidates[colorIndex.coerceIn(candidates.indices)]
}

/**
 * Extract up to 4 distinct seed color candidates from the assistant's
 * background and/or avatar images.
 *
 * The first candidate (index 0) always matches the legacy single-color
 * behavior: background takes priority, falls back to avatar. The remaining
 * slots (up to 6 total) are filled from both sources (if available) to give variety.
 */
suspend fun extractColorCandidates(
    context: Context,
    assistant: Assistant
): List<Color> {
    val backgroundSource = assistant.background
    val avatarSource = when (val avatar = assistant.avatar) {
        is Avatar.Image -> avatar.url
        is Avatar.Resource -> null // handled separately below
        else -> null
    }
    val avatarResourceId = (assistant.avatar as? Avatar.Resource)?.id

    val bgCandidates = backgroundSource?.let { source ->
        loadBitmap(context, source)?.let { extractCandidatesFromBitmap(it) }
    } ?: emptyList()

    val avatarCandidates = when {
        avatarSource != null -> {
            loadBitmap(context, avatarSource)?.let { extractCandidatesFromBitmap(it) }
        }
        avatarResourceId != null -> {
            BitmapFactory.decodeResource(context.resources, avatarResourceId)
                ?.let { extractCandidatesFromBitmap(it) }
        }
        else -> null
    } ?: emptyList()

    // The "primary" source is whichever one the old code would have used:
    // background first, then avatar.
    val primaryCandidates: List<Color>
    val secondaryCandidates: List<Color>
    if (bgCandidates.isNotEmpty()) {
        primaryCandidates = bgCandidates
        secondaryCandidates = avatarCandidates
    } else {
        primaryCandidates = avatarCandidates
        secondaryCandidates = emptyList()
    }

    if (primaryCandidates.isEmpty()) return emptyList()

    // Slot 0 = the old default (first from primary source).
    // Fill remaining slots: first from primary extras, then secondary.
    val result = mutableListOf(primaryCandidates.first())
    val remaining = primaryCandidates.drop(1) + secondaryCandidates
    for (candidate in remaining) {
        if (result.size >= 6) break
        // Only add if visually distinct from what we already have
        if (result.none { existing -> existing.isColorClose(candidate) }) {
            result.add(candidate)
        }
    }
    return result
}

// ═══════════════════════════════════════════════════════════════════════════
// Color scheme building
// ═══════════════════════════════════════════════════════════════════════════

private fun buildAssistantColorScheme(
    baseScheme: ColorScheme,
    seedColor: Color,
    darkTheme: Boolean
): ColorScheme {
    // Derive mode-appropriate tones from the normalized seed hue & saturation.
    // In dark mode, primary needs to be bright enough to read on dark backgrounds.
    // In light mode, primary needs to be dark enough to read on light backgrounds.
    val primary = adjustTone(seedColor, if (darkTheme) 0.72f else 0.40f)
    val secondary = adjustTone(
        lerp(seedColor, baseScheme.secondary, 0.35f),
        if (darkTheme) 0.68f else 0.42f
    )
    val tertiary = adjustTone(
        lerp(seedColor, baseScheme.tertiary, 0.50f),
        if (darkTheme) 0.68f else 0.42f
    )

    // Containers: subtle tinted surfaces
    val primaryContainer = adjustTone(seedColor, if (darkTheme) 0.22f else 0.90f)
    val secondaryContainer = adjustTone(
        lerp(seedColor, baseScheme.secondary, 0.35f),
        if (darkTheme) 0.20f else 0.92f
    )
    val tertiaryContainer = adjustTone(
        lerp(seedColor, baseScheme.tertiary, 0.50f),
        if (darkTheme) 0.20f else 0.92f
    )

    // onContainer: high contrast text on containers
    val onPrimaryContainer = adjustTone(seedColor, if (darkTheme) 0.92f else 0.10f)
    val onSecondaryContainer = adjustTone(
        lerp(seedColor, baseScheme.secondary, 0.35f),
        if (darkTheme) 0.92f else 0.10f
    )
    val onTertiaryContainer = adjustTone(
        lerp(seedColor, baseScheme.tertiary, 0.50f),
        if (darkTheme) 0.92f else 0.10f
    )

    val inversePrimary = adjustTone(seedColor, if (darkTheme) 0.38f else 0.75f)

    return baseScheme.copy(
        primary = primary,
        onPrimary = contrastSafeOnColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = contrastSafeOnColor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = contrastSafeOnColor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
    )
}

/**
 * Shift a color to a target HSL lightness while preserving hue and saturation.
 */
private fun adjustTone(color: Color, targetLightness: Float): Color {
    val hsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color.toArgbInt(), hsl)
    hsl[2] = targetLightness
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Returns black or white, whichever provides at least 4.5:1 contrast ratio
 * against [color]. Falls back to the higher-contrast option if neither meets
 * the threshold exactly.
 */
private fun contrastSafeOnColor(color: Color): Color {
    val argb = color.toArgbInt()
    val contrastWhite = ColorUtils.calculateContrast(0xFFFFFFFF.toInt(), argb)
    val contrastBlack = ColorUtils.calculateContrast(0xFF000000.toInt(), argb)
    return if (contrastWhite >= contrastBlack) Color.White else Color.Black
}

/**
 * Convert Compose [Color] to an ARGB int suitable for [ColorUtils].
 */
private fun Color.toArgbInt(): Int {
    val a = (alpha * 255 + 0.5f).toInt() shl 24
    val r = (red * 255 + 0.5f).toInt() shl 16
    val g = (green * 255 + 0.5f).toInt() shl 8
    val b = (blue * 255 + 0.5f).toInt()
    return a or r or g or b
}

// ═══════════════════════════════════════════════════════════════════════════
// Bitmap palette extraction
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Extract up to 4 distinct, normalized seed colors from a single bitmap.
 * The first color matches the legacy auto-pick (dominant → muted → vibrant).
 */
private fun extractCandidatesFromBitmap(
    bitmap: Bitmap
): List<Color> {
    val scaled = scaleBitmap(bitmap, PALETTE_TARGET_SIZE)
    if (scaled != bitmap) {
        bitmap.recycle()
    }

    val palette = Palette.from(scaled).generate()
    scaled.recycle()

    // Ordered swatch priority – first non-null becomes candidate 0 (the default).
    // Remaining distinct swatches fill slots 1-5.
    val swatchesInPriority = listOfNotNull(
        palette.dominantSwatch,
        palette.mutedSwatch,
        palette.vibrantSwatch,
        palette.lightMutedSwatch,
        palette.lightVibrantSwatch,
        palette.darkMutedSwatch,
        palette.darkVibrantSwatch
    )

    if (swatchesInPriority.isEmpty()) return emptyList()

    val normalized = swatchesInPriority.map { swatch -> normalizeSeedColor(Color(swatch.rgb)) }

    // Keep first (default), then pick those that are visually distinct.
    val result = mutableListOf(normalized.first())
    for (i in 1 until normalized.size) {
        if (result.size >= 6) break
        val candidate = normalized[i]
        if (result.none { existing -> existing.isColorClose(candidate) }) {
            result.add(candidate)
        }
    }
    return result
}

/**
 * Check if two normalized seed colors are too visually similar to both
 * appear in the picker. Uses weighted HSL distance so that after
 * normalization (which clamps saturation & lightness to narrow bands)
 * we still get meaningful differentiation.
 */
private fun Color.isColorClose(other: Color): Boolean {
    val hsl1 = floatArrayOf(0f, 0f, 0f)
    val hsl2 = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(toArgbInt(), hsl1)
    ColorUtils.colorToHSL(other.toArgbInt(), hsl2)

    // Circular hue distance (0–180)
    val hueDiff = kotlin.math.abs(hsl1[0] - hsl2[0]).let { minOf(it, 360f - it) }
    val satDiff = kotlin.math.abs(hsl1[1] - hsl2[1])
    val litDiff = kotlin.math.abs(hsl1[2] - hsl2[2])

    // Weighted: hue matters most on [0-360], sat/lightness on [0-1]
    // Scale hue to roughly same range as sat/lightness for comparison
    val normalizedHueDist = hueDiff / 360f  // 0–0.5
    val distance = normalizedHueDist * 2f + satDiff + litDiff
    return distance < 0.08f // ~29° hue-only or equivalent combined distance
}

// ═══════════════════════════════════════════════════════════════════════════
// Seed normalization
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Normalize a raw extracted color into a well-behaved seed.
 *
 * Clamps the HSL values to:
 * - **Saturation**: 0.12 – 0.75 → prevents both desaturated "gray" seeds
 *   and over-saturated "neon" seeds.
 * - **Lightness**: 0.30 – 0.58 → the mid-tone sweet spot that works as a
 *   starting point for both dark-mode and light-mode tone mapping.
 *
 * The hue is always preserved so the theme still "feels" like the character.
 *
 * For very low-chroma colors (near grayscale, saturation < 0.08), we
 * bump the saturation to a subtle minimum so the theme isn't completely flat.
 */
private fun normalizeSeedColor(color: Color): Color {
    val hsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color.toArgbInt(), hsl)

    // Clamp saturation: avoid gray & neon
    hsl[1] = hsl[1].coerceIn(MIN_SEED_SATURATION, MAX_SEED_SATURATION)

    // Clamp lightness: avoid too-dark & too-bright seeds
    hsl[2] = hsl[2].coerceIn(MIN_SEED_LIGHTNESS, MAX_SEED_LIGHTNESS)

    return Color(ColorUtils.HSLToColor(hsl))
}

// Seed normalization bounds – intentionally wide so that soft
// palettes (browns, pastels) are not pushed to stronger colors.
private const val MIN_SEED_SATURATION = 0.12f
private const val MAX_SEED_SATURATION = 0.75f
private const val MIN_SEED_LIGHTNESS = 0.30f
private const val MAX_SEED_LIGHTNESS = 0.58f

// ═══════════════════════════════════════════════════════════════════════════
// Bitmap loading utilities
// ═══════════════════════════════════════════════════════════════════════════

private fun scaleBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= targetSize && height <= targetSize) {
        return bitmap
    }
    val scale = targetSize.toFloat() / maxOf(width, height).toFloat()
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
}

private suspend fun loadBitmap(context: Context, source: String): Bitmap? {
    return runCatching {
        val uri = Uri.parse(source)
        when (uri.scheme) {
            "content", "file", "android.resource" -> {
                decodeBitmap {
                    context.contentResolver.openInputStream(uri)
                }
            }
            "http", "https" -> {
                loadRemoteBitmap(source)
            }
            null -> {
                File(source).takeIf { it.exists() }?.let { file ->
                    decodeBitmap {
                        file.inputStream()
                    }
                }
            }
            else -> null
        }
    }.getOrNull()
}

private suspend fun loadRemoteBitmap(source: String): Bitmap? {
    val response = withTimeout(5_000L) {
        GlobalContext.get().get<PlatformHttpClient>().execute(
            PlatformHttpRequest(
                method = "GET",
                url = source,
            )
        )
    }
    if (response.statusCode != 200) return null
    return decodeBitmap(response.body)
}

private fun decodeBitmap(openStream: () -> InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    openStream()?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, PALETTE_TARGET_SIZE)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return openStream()?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}

private fun decodeBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, PALETTE_TARGET_SIZE)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun calculateInSampleSize(width: Int, height: Int, targetSize: Int): Int {
    var sampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while (halfWidth / sampleSize >= targetSize && halfHeight / sampleSize >= targetSize) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}
