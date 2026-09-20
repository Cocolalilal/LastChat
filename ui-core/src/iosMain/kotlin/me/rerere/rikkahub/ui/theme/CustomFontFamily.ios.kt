package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

@OptIn(ExperimentalTextApi::class)
actual fun fontFamilyFromBytes(
    identity: String,
    bytes: ByteArray,
    variationSettings: FontVariation.Settings,
): FontFamily? {
    if (bytes.isEmpty()) return null
    return try {
        FontFamily(
            Font("$identity-300", bytes, FontWeight.Light, variationSettings = variationSettings),
            Font("$identity-400", bytes, FontWeight.Normal, variationSettings = variationSettings),
            Font("$identity-500", bytes, FontWeight.Medium, variationSettings = variationSettings),
            Font("$identity-600", bytes, FontWeight.SemiBold, variationSettings = variationSettings),
            Font("$identity-700", bytes, FontWeight.Bold, variationSettings = variationSettings),
        )
    } catch (_: Throwable) {
        runCatching {
            FontFamily(
                Font("$identity-300", bytes, FontWeight.Light),
                Font("$identity-400", bytes, FontWeight.Normal),
                Font("$identity-500", bytes, FontWeight.Medium),
                Font("$identity-600", bytes, FontWeight.SemiBold),
                Font("$identity-700", bytes, FontWeight.Bold),
            )
        }.getOrNull()
    }
}
