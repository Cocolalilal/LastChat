package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.core.generated.resources.Res
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
    /**
     * Stroke on a photo that is itself the message bubble. Color comes from
     * [me.rerere.rikkahub.ui.components.chat.LocalMessageBubbleColor] so the rim
     * is the bubble fill. Thicker than a 1dp hairline, still a rim rather than a mat.
     */
    val ImageRimWidth = 2.dp
    val TonalElevation = 0.dp

    fun fill(colorScheme: ColorScheme): Color = colorScheme.surfaceContainer

    fun softEdgeColor(colorScheme: ColorScheme): Color =
        colorScheme.outlineVariant.copy(alpha = SoftEdgeAlpha)

    /**
     * Stroke painted over the glass. Src-over this color lightens whatever blur is
     * already under the edge. On a black backdrop it matches [softEdgeColor] composited
     * on glass-over-black, so the rim does not change in that case.
     *
     * [surface] is the opaque fill (not the glass alpha). [glassAlpha] is the alpha used
     * when blur is on.
     */
    fun softEdgeLightenStroke(
        outline: Color,
        surface: Color,
        glassAlpha: Float,
        edgeAlpha: Float = SoftEdgeAlpha,
    ): Color {
        fun glass(channel: Float) = channel * glassAlpha
        fun current(outlineChannel: Float, glassChannel: Float) =
            outlineChannel * edgeAlpha + glassChannel * (1f - edgeAlpha)

        val glassRed = glass(surface.red)
        val glassGreen = glass(surface.green)
        val glassBlue = glass(surface.blue)
        val currentRed = current(outline.red, glassRed)
        val currentGreen = current(outline.green, glassGreen)
        val currentBlue = current(outline.blue, glassBlue)

        fun ratio(currentChannel: Float, glassChannel: Float): Float {
            val denom = 1f - glassChannel
            if (denom <= 0.0001f) return 0f
            return (currentChannel - glassChannel) / denom
        }

        val alpha = maxOf(
            ratio(currentRed, glassRed),
            ratio(currentGreen, glassGreen),
            ratio(currentBlue, glassBlue),
        ).coerceIn(0f, 1f)
        if (alpha <= 0.0001f) return outline.copy(alpha = edgeAlpha)

        fun source(currentChannel: Float, glassChannel: Float): Float =
            (glassChannel + (currentChannel - glassChannel) / alpha).coerceIn(0f, 1f)

        return Color(
            red = source(currentRed, glassRed),
            green = source(currentGreen, glassGreen),
            blue = source(currentBlue, glassBlue),
            alpha = alpha,
        )
    }

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
    /** Outer corner of chat bubbles and expanded activity timelines. */
    val MessageBubbleRadius = 24.dp

    /** Shared tuck between stacked bubbles, stacked compact pills, and a pill meeting a bubble. */
    val MessageBubbleJoint = 6.dp

    /**
     * [GroupedMessageBubble] content padding. Horizontal is the larger inset, so nested
     * shapes step down by this much. Full-bleed blocks add the 4dp difference on the
     * vertical edges so their optical inset matches.
     */
    val MessageBubblePaddingHorizontal = 16.dp
    val MessageBubblePaddingVertical = 12.dp

    val CardLarge = RoundedCornerShape(28.dp)
    val CardMedium = RoundedCornerShape(MessageBubbleRadius)
    val CardSmall = RoundedCornerShape(16.dp)
    val ButtonPill = RoundedCornerShape(50)
    val ButtonRounded = RoundedCornerShape(20.dp)
    val ButtonSquared = RoundedCornerShape(12.dp)
    val InputField = CardMedium
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
    /** 24dp bubble minus the 16dp content inset. */
    val MessageBubbleInner = concentricShape(MessageBubbleRadius, MessageBubblePaddingHorizontal)

    /** Inner corner of a shape inset from [outer] by [inset] and an optional stroke. Never negative. */
    fun concentric(outer: Dp, inset: Dp, stroke: Dp = 0.dp): Dp =
        (outer - inset - stroke).coerceAtLeast(0.dp)

    fun concentricShape(outer: Dp, inset: Dp, stroke: Dp = 0.dp): RoundedCornerShape =
        RoundedCornerShape(concentric(outer, inset, stroke))

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

/** Loads the same Google Sans Flex binary that is canonical for the Android UI. */
@androidx.compose.runtime.Composable
fun rememberLastChatFontFamily(): FontFamily = FontFamily(
    Font(Res.font.google_sans_flex, weight = FontWeight.Light),
    Font(Res.font.google_sans_flex, weight = FontWeight.Normal),
    Font(Res.font.google_sans_flex, weight = FontWeight.Medium),
    Font(Res.font.google_sans_flex, weight = FontWeight.SemiBold),
    Font(Res.font.google_sans_flex, weight = FontWeight.Bold),
    Font(Res.font.google_sans_flex, weight = FontWeight.ExtraBold),
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

/**
 * Parent corner and the padding between it and a nested shape.
 * [inset] of 0 means the caller is not inside a bubble or timeline card.
 * When horizontal and vertical padding differ, pass the larger one so the
 * nested radius is not rounder than either axis allows.
 */
data class OpticalFrame(
    val outer: Dp,
    val inset: Dp,
) {
    val nested: Boolean get() = inset > 0.dp
    val inner: Dp get() = AppShapes.concentric(outer, inset)
    val innerShape: RoundedCornerShape get() = AppShapes.concentricShape(outer, inset)
}

val LocalOpticalFrame = staticCompositionLocalOf { OpticalFrame(0.dp, 0.dp) }
