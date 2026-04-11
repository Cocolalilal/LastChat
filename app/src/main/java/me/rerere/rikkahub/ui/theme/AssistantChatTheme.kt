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
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import java.io.File
import java.io.InputStream
import java.net.URL

private const val PALETTE_TARGET_SIZE = 128
private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

@Composable
fun AssistantChatTheme(
    assistant: Assistant,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = LocalDarkMode.current
    val seedColor by produceState<Color?>(initialValue = null, assistant) {
        if (!assistant.useAssistantMaterialYouColors) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            extractSeedColor(context = context, assistant = assistant)
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

private fun extractSeedColor(
    context: Context,
    assistant: Assistant
): Color? {
    val backgroundSource = assistant.background
    val backgroundColor = backgroundSource?.let { source ->
        extractSeedColorFromSource(context = context, source = source)
    }
    if (backgroundColor != null) {
        return backgroundColor
    }

    val avatar = assistant.avatar
    return when (avatar) {
        is Avatar.Image -> extractSeedColorFromSource(context = context, source = avatar.url)
        is Avatar.Resource -> {
            val bitmap = BitmapFactory.decodeResource(context.resources, avatar.id) ?: return null
            extractSeedColorFromBitmap(bitmap)
        }
        else -> null
    }
}

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

private fun extractSeedColorFromSource(
    context: Context,
    source: String
): Color? {
    val bitmap = loadBitmap(context, source) ?: return null
    return extractSeedColorFromBitmap(bitmap)
}

private fun extractSeedColorFromBitmap(
    bitmap: Bitmap
): Color? {
    val scaled = scaleBitmap(bitmap, PALETTE_TARGET_SIZE)
    if (scaled != bitmap) {
        bitmap.recycle()
    }

    val palette = Palette.from(scaled).generate()
    val swatch = palette.vibrantSwatch
        ?: palette.dominantSwatch
        ?: palette.mutedSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.lightMutedSwatch
        ?: palette.darkMutedSwatch
    val color = swatch?.rgb?.let { Color(it) }?.let { normalizeSeedColor(it) }
    scaled.recycle()
    return color
}

/**
 * Normalize a raw extracted color into a well-behaved seed.
 *
 * Clamps the HSL values to:
 * - **Saturation**: 0.30 – 0.75 → prevents both desaturated "gray" seeds
 *   and over-saturated "neon" seeds.
 * - **Lightness**: 0.35 – 0.55 → the mid-tone sweet spot that works as a
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

// Seed normalization bounds
private const val MIN_SEED_SATURATION = 0.30f
private const val MAX_SEED_SATURATION = 0.75f
private const val MIN_SEED_LIGHTNESS = 0.35f
private const val MAX_SEED_LIGHTNESS = 0.55f

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

private fun loadBitmap(context: Context, source: String): Bitmap? {
    return runCatching {
        val uri = Uri.parse(source)
        when (uri.scheme) {
            "content", "file", "android.resource" -> {
                decodeBitmap {
                    context.contentResolver.openInputStream(uri)
                }
            }
            "http", "https" -> {
                decodeBitmap {
                    val connection = URL(source).openConnection().apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    connection.getInputStream()
                }
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

private fun calculateInSampleSize(width: Int, height: Int, targetSize: Int): Int {
    var sampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while (halfWidth / sampleSize >= targetSize && halfHeight / sampleSize >= targetSize) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}
