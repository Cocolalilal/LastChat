package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File

@OptIn(ExperimentalTextApi::class)
actual fun fontFamilyFromBytes(
    identity: String,
    bytes: ByteArray,
    variationSettings: FontVariation.Settings,
): FontFamily? {
    if (bytes.isEmpty()) return null
    return try {
        val tmp = File.createTempFile("lastchat-font-", "-$identity.ttf")
        tmp.deleteOnExit()
        tmp.writeBytes(bytes)
        FontFamily(
            Font(tmp, weight = FontWeight.Light, variationSettings = variationSettings),
            Font(tmp, weight = FontWeight.Normal, variationSettings = variationSettings),
            Font(tmp, weight = FontWeight.Medium, variationSettings = variationSettings),
            Font(tmp, weight = FontWeight.SemiBold, variationSettings = variationSettings),
            Font(tmp, weight = FontWeight.Bold, variationSettings = variationSettings),
        )
    } catch (_: Exception) {
        null
    }
}
