package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.unit.dp

class AppUiTokensTest {
    @Test
    fun groupedItemRadii_returnsExpectedCornersForGroupedPositions() {
        assertEquals(
            GroupedItemRadii(24.dp, 24.dp, 10.dp, 10.dp),
            groupedItemRadii(ItemPosition.FIRST)
        )
        assertEquals(
            GroupedItemRadii(10.dp, 10.dp, 10.dp, 10.dp),
            groupedItemRadii(ItemPosition.MIDDLE)
        )
        assertEquals(
            GroupedItemRadii(10.dp, 10.dp, 24.dp, 24.dp),
            groupedItemRadii(ItemPosition.LAST)
        )
        assertEquals(
            GroupedItemRadii(24.dp, 24.dp, 24.dp, 24.dp),
            groupedItemRadii(ItemPosition.ONLY)
        )
    }

    @Test
    fun groupedItemRadii_selectedAlwaysUsesPillShape() {
        assertEquals(
            GroupedItemRadii(30.dp, 30.dp, 30.dp, 30.dp),
            groupedItemRadii(
                position = ItemPosition.MIDDLE,
                selected = true,
                groupRadius = 30.dp,
                itemRadius = 8.dp,
            )
        )
    }

    @Test
    fun appSurfaceColor_usesConsistentHierarchyForLightAndDark() {
        val colorScheme = lightColorScheme(
            surface = Color(0xFF101010),
            surfaceContainerLow = Color(0xFF202020),
            surfaceContainerHigh = Color(0xFF303030),
            surfaceContainerHighest = Color(0xFF404040),
        )

        assertEquals(
            Color(0xFF101010),
            appSurfaceColor(colorScheme, darkTheme = false, level = AppSurfaceLevel.Flat)
        )
        assertEquals(
            Color(0xFF303030),
            appSurfaceColor(colorScheme, darkTheme = false, level = AppSurfaceLevel.Container)
        )
        assertEquals(
            Color(0xFF404040),
            appSurfaceColor(colorScheme, darkTheme = false, level = AppSurfaceLevel.ContainerHigh)
        )
        assertEquals(
            Color(0xFF202020),
            appSurfaceColor(colorScheme, darkTheme = true, level = AppSurfaceLevel.Container)
        )
        assertEquals(
            Color(0xFF303030),
            appSurfaceColor(colorScheme, darkTheme = true, level = AppSurfaceLevel.ContainerHigh)
        )
    }
}
