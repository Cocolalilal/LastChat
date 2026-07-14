package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.components.chat.BubblePosition
import me.rerere.rikkahub.ui.components.chat.getBubblePosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LastChatDesignTokensTest {
    @Test
    fun typographyMatchesProductionMetrics() {
        val typography = buildLastChatTypography(FontFamily.Default)
        assertEquals(57.sp, typography.displayLarge.fontSize)
        assertEquals(64.sp, typography.displayLarge.lineHeight)
        assertEquals(FontWeight.Medium, typography.bodyLarge.fontWeight)
        assertEquals(16.sp, typography.bodyLarge.fontSize)
        assertEquals(0.5.sp, typography.bodyLarge.letterSpacing)
        assertEquals(11.sp, typography.labelSmall.fontSize)
    }

    @Test
    fun shapeAndPaletteTokensMatchAndroidReference() {
        assertEquals(RoundedCornerShape(28.dp), AppShapes.CardLarge)
        assertEquals(Color(0xFF8E4955), sakuraColorScheme(false).primary)
        assertEquals(Color(0xFF0E6B58), seafoamMintColorScheme(false).primary)
        assertEquals(Color(0xFF86D6BE), seafoamMintColorScheme(true).primary)
        assertEquals(Color.Black, sakuraColorScheme(true).withLastChatAmoledSurface(true).background)
        assertEquals(Color.Black, sakuraColorScheme(true).withLastChatAmoledSurface(true).surface)
    }

    @Test
    fun everyProductionThemeIsAvailableToSharedUi() {
        val expectedLightPrimaries = mapOf(
            "seafoam_mint" to Color(0xFF0E6B58),
            "ocean" to Color(0xFF116682),
            "sakura" to Color(0xFF8E4955),
            "spring" to Color(0xFF4C662B),
            "autumn" to Color(0xFF735C0C),
            "black" to Color(0xFF606060),
        )

        expectedLightPrimaries.forEach { (id, expectedPrimary) ->
            assertEquals(expectedPrimary, presetColorScheme(id, dark = false).primary, id)
            assertNotEquals(
                presetColorScheme(id, dark = false).background,
                presetColorScheme(id, dark = true).background,
                id,
            )
        }
        assertEquals(
            seafoamMintColorScheme(false),
            presetColorScheme("unknown", dark = false),
        )
    }

    @Test
    fun groupedBubblePositionsAreStable() {
        assertEquals(BubblePosition.SINGLE, getBubblePosition(0, 1))
        assertEquals(BubblePosition.FIRST, getBubblePosition(0, 3))
        assertEquals(BubblePosition.MIDDLE, getBubblePosition(1, 3))
        assertEquals(BubblePosition.LAST, getBubblePosition(2, 3))
    }
}
