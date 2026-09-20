package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import me.rerere.common.font.PortableFontAxis
import me.rerere.common.font.clampFontGrade
import me.rerere.common.font.clampFontRoundness
import me.rerere.common.font.clampFontWidth

@OptIn(ExperimentalTextApi::class)
fun customFontVariationSettings(
    width: Float = 100f,
    roundness: Float = 100f,
    grade: Float = 0f,
    customAxes: List<PortableFontAxis> = emptyList(),
): FontVariation.Settings {
    val settings = buildList {
        add(FontVariation.width(clampFontWidth(width)))
        add(FontVariation.Setting("ROND", clampFontRoundness(roundness)))
        add(FontVariation.Setting("GRAD", clampFontGrade(grade)))
        customAxes.forEach { axis ->
            val tag = axis.tag.trim()
            if (tag.length == 4 && tag.uppercase() !in setOf("WDTH", "ROND", "GRAD")) {
                add(FontVariation.Setting(tag, axis.currentValue.coerceIn(axis.minValue, axis.maxValue)))
            }
        }
    }
    return FontVariation.Settings(*settings.toTypedArray())
}

/**
 * Builds a FontFamily from a user-imported TTF/OTF. Android uses file-backed Font;
 * iOS uses Skia `Font(identity, data)` with the same variation axes.
 */
@OptIn(ExperimentalTextApi::class)
expect fun fontFamilyFromBytes(
    identity: String,
    bytes: ByteArray,
    variationSettings: FontVariation.Settings = FontVariation.Settings(),
): FontFamily?
