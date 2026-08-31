package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.core.generated.resources.Res
import me.rerere.rikkahub.ui.core.generated.resources.google_sans_flex
import org.jetbrains.compose.resources.Font

/** Shared, platform-independent LastChat shape tokens. Android remains the visual reference. */
object AppShapes {
    val CardLarge = RoundedCornerShape(28.dp)
    val CardMedium = RoundedCornerShape(24.dp)
    val CardSmall = RoundedCornerShape(16.dp)
    val ButtonPill = RoundedCornerShape(50)
    val ButtonRounded = RoundedCornerShape(20.dp)
    val ButtonSquared = RoundedCornerShape(12.dp)
    val InputField = RoundedCornerShape(20.dp)
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
        topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp,
    )
    val MessageIncoming = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp,
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
