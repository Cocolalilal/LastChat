@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.common.font.clampFontGrade
import me.rerere.common.font.clampFontRoundness
import me.rerere.common.font.clampFontWidth
import me.rerere.rikkahub.ui.core.generated.resources.Res
import me.rerere.rikkahub.ui.core.generated.resources.google_sans_code
import me.rerere.rikkahub.ui.core.generated.resources.google_sans_flex
import org.jetbrains.compose.resources.Font

/** Shared spacing scale. Prefer these over one-off 4/8/12/16/24 paddings. */
object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 28.dp
    val xxl = 32.dp
}

/**
 * Shared chrome sizes. Menu, status pills, composer + control, and toolbar icons
 * use [ChromePill]. Search/share bars may use [ChromeBar].
 */
object AppSize {
    val ChromePill = 48.dp
    val ChromeBar = 56.dp
    val ComposerAction = 36.dp
    val Icon = 24.dp
}

/**
 * Charcoal floating-layer recipe. True-black canvas stays on [ColorScheme.background];
 * floating chrome/sheets/dialogs sit on [fill]. Glass alpha is applied only when blur
 * is actually running (see [resolve]).
 *
 * No accent object — new accent values need Julian.
 */
object AppSurface {
    const val GlassAlphaDark = 0.34f
    const val GlassAlphaLight = 0.28f
    const val SoftEdgeAlpha = 0.6f
    val SoftEdgeWidth = 1.dp
    val TonalElevation = 0.dp

    fun fill(colorScheme: ColorScheme): Color = colorScheme.surfaceContainer

    fun softEdgeColor(colorScheme: ColorScheme): Color =
        colorScheme.outlineVariant.copy(alpha = SoftEdgeAlpha)

    /**
     * Blur on + haze available → tasteful glass over charcoal.
     * Blur off (or no haze) → opaque charcoal. Never keeps a pre-alpha'd fallback.
     */
    fun resolve(charcoal: Color, blurEnabled: Boolean, dark: Boolean): Color {
        val opaque = charcoal.copy(alpha = 1f)
        if (!blurEnabled) return opaque
        val glassAlpha = if (dark) GlassAlphaDark else GlassAlphaLight
        return opaque.copy(alpha = glassAlpha)
    }
}

/** Shared, platform-independent LastChat shape tokens. Android remains the visual reference. */
object AppShapes {
    val CardLarge = RoundedCornerShape(28.dp)
    val CardMedium = RoundedCornerShape(24.dp)
    val CardSmall = RoundedCornerShape(16.dp)
    val ButtonPill = RoundedCornerShape(50)
    val ButtonRounded = RoundedCornerShape(20.dp)
    val ButtonSquared = RoundedCornerShape(12.dp)
    val InputField = RoundedCornerShape(24.dp)
    val SearchField = ButtonPill
    val Chip = RoundedCornerShape(12.dp)
    val Tag = RoundedCornerShape(50)
    val Dialog = RoundedCornerShape(28.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Avatar = RoundedCornerShape(50)
    val IconButton = RoundedCornerShape(50)
    val Indicator = RoundedCornerShape(8.dp)
    val ListItem = RoundedCornerShape(24.dp)
    val ListItemFirst = RoundedCornerShape(24.dp, 24.dp, 10.dp, 10.dp)
    val ListItemMiddle = RoundedCornerShape(10.dp)
    val ListItemLast = RoundedCornerShape(10.dp, 10.dp, 24.dp, 24.dp)
    val CardLargeInner12 = RoundedCornerShape(16.dp)
    val CardLargeInner8 = RoundedCornerShape(20.dp)
    val CardMediumInner12 = RoundedCornerShape(12.dp)
    val CardSmallInner8 = RoundedCornerShape(8.dp)
    val MessageBubbleInner = RoundedCornerShape(8.dp)
    val MessageOutgoing = RoundedCornerShape(
        topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp,
    )
    val MessageIncoming = RoundedCornerShape(
        topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 24.dp,
    )
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The exact Android typography metric table, parameterized only by platform font family. */
fun buildLastChatTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = style(fontFamily, FontWeight.Bold, 57, 64, -0.25f),
    displayMedium = style(fontFamily, FontWeight.Bold, 45, 52, 0f),
    displaySmall = style(fontFamily, FontWeight.SemiBold, 36, 44, 0f),
    headlineLarge = style(fontFamily, FontWeight.SemiBold, 32, 40, 0f),
    headlineMedium = style(fontFamily, FontWeight.SemiBold, 28, 36, 0f),
    headlineSmall = style(fontFamily, FontWeight.SemiBold, 24, 32, 0f),
    titleLarge = style(fontFamily, FontWeight.SemiBold, 22, 28, 0f),
    titleMedium = style(fontFamily, FontWeight.Medium, 16, 24, 0.15f),
    titleSmall = style(fontFamily, FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = style(fontFamily, FontWeight.Medium, 16, 24, 0.5f),
    bodyMedium = style(fontFamily, FontWeight.Medium, 14, 20, 0.25f),
    bodySmall = style(fontFamily, FontWeight.Medium, 12, 16, 0.4f),
    labelLarge = style(fontFamily, FontWeight.Medium, 14, 20, 0.1f),
    labelMedium = style(fontFamily, FontWeight.Medium, 12, 16, 0.5f),
    labelSmall = style(fontFamily, FontWeight.Medium, 11, 16, 0.5f),
)

fun lastChatFontVariationSettings(
    width: Float = 100f,
    roundness: Float = 100f,
    grade: Float = 0f,
): FontVariation.Settings = FontVariation.Settings(
    FontVariation.width(clampFontWidth(width)),
    FontVariation.Setting("ROND", clampFontRoundness(roundness)),
    FontVariation.Setting("GRAD", clampFontGrade(grade)),
)

/** Loads the same Google Sans Flex binary that is canonical for the Android UI. */
@Composable
fun rememberLastChatFontFamily(
    width: Float = 100f,
    roundness: Float = 100f,
    grade: Float = 0f,
): FontFamily {
    val settings = remember(width, roundness, grade) {
        lastChatFontVariationSettings(width, roundness, grade)
    }
    return FontFamily(
        Font(Res.font.google_sans_flex, weight = FontWeight.Light, variationSettings = settings),
        Font(Res.font.google_sans_flex, weight = FontWeight.Normal, variationSettings = settings),
        Font(Res.font.google_sans_flex, weight = FontWeight.Medium, variationSettings = settings),
        Font(Res.font.google_sans_flex, weight = FontWeight.SemiBold, variationSettings = settings),
        Font(Res.font.google_sans_flex, weight = FontWeight.Bold, variationSettings = settings),
        Font(Res.font.google_sans_flex, weight = FontWeight.ExtraBold, variationSettings = settings),
    )
}

/** Loads the same Google Sans Code binary Android uses for SYSTEM_CODE. */
@Composable
fun rememberLastChatCodeFontFamily(): FontFamily = FontFamily(
    Font(Res.font.google_sans_code, weight = FontWeight.Light),
    Font(Res.font.google_sans_code, weight = FontWeight.Normal),
    Font(Res.font.google_sans_code, weight = FontWeight.Medium),
    Font(Res.font.google_sans_code, weight = FontWeight.SemiBold),
    Font(Res.font.google_sans_code, weight = FontWeight.Bold),
)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)
