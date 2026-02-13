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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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
    val primary = seedColor
    val secondary = lerp(seedColor, baseScheme.secondary, 0.4f)
    val tertiary = lerp(seedColor, baseScheme.tertiary, 0.6f)
    val containerBlend = if (darkTheme) 0.35f else 0.82f

    val primaryContainer = lerp(primary, baseScheme.surface, containerBlend)
    val secondaryContainer = lerp(secondary, baseScheme.surface, containerBlend)
    val tertiaryContainer = lerp(tertiary, baseScheme.surface, containerBlend)
    val inversePrimary = if (darkTheme) {
        lerp(primary, Color.White, 0.55f)
    } else {
        lerp(primary, Color.Black, 0.3f)
    }

    return baseScheme.copy(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onColorFor(primaryContainer),
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onColorFor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onColorFor(secondaryContainer),
        tertiary = tertiary,
        onTertiary = onColorFor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onColorFor(tertiaryContainer),
    )
}

private fun onColorFor(color: Color): Color {
    return if (color.luminance() > 0.5f) Color.Black else Color.White
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
    val color = swatch?.rgb?.let { Color(it) }
    scaled.recycle()
    return color
}

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
