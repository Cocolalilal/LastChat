package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.ui.components.ui.groupedStackPositionFor
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.unit.dp

class AppUiTokensTest {
    @Test
    fun groupedItemRadii_returnsExpectedCornersForGroupedPositions() {
        assertEquals(
            GroupedItemRadii(20.dp, 20.dp, 8.dp, 8.dp),
            groupedItemRadii(ItemPosition.FIRST)
        )
        assertEquals(
            GroupedItemRadii(8.dp, 8.dp, 8.dp, 8.dp),
            groupedItemRadii(ItemPosition.MIDDLE)
        )
        assertEquals(
            GroupedItemRadii(8.dp, 8.dp, 20.dp, 20.dp),
            groupedItemRadii(ItemPosition.LAST)
        )
        assertEquals(
            GroupedItemRadii(20.dp, 20.dp, 20.dp, 20.dp),
            groupedItemRadii(ItemPosition.ONLY)
        )
    }

    @Test
    fun groupedItemRadii_selectedAlwaysUsesPillShape() {
        assertEquals(
            GroupedItemRadii(50.dp, 50.dp, 50.dp, 50.dp),
            groupedItemRadii(
                position = ItemPosition.MIDDLE,
                selected = true,
            )
        )
        assertEquals(
            GroupedItemRadii(36.dp, 36.dp, 36.dp, 36.dp),
            groupedItemRadii(
                position = ItemPosition.MIDDLE,
                selected = true,
                selectedRadius = 36.dp,
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
            Color(0xFF303030),
            appSurfaceColor(colorScheme, darkTheme = false, level = AppSurfaceLevel.ContainerHigh)
        )
        assertEquals(
            Color(0xFF202020),
            appSurfaceColor(colorScheme, darkTheme = true, level = AppSurfaceLevel.Container)
        )
        assertEquals(
            Color(0xFF202020),
            appSurfaceColor(colorScheme, darkTheme = true, level = AppSurfaceLevel.ContainerHigh)
        )
    }

    @Test
    fun groupedStackPositionFor_returnsExpectedSequencePositions() {
        assertEquals(ItemPosition.ONLY, groupedStackPositionFor(index = 0, count = 0))
        assertEquals(ItemPosition.ONLY, groupedStackPositionFor(index = 0, count = 1))
        assertEquals(ItemPosition.FIRST, groupedStackPositionFor(index = 0, count = 3))
        assertEquals(ItemPosition.MIDDLE, groupedStackPositionFor(index = 1, count = 3))
        assertEquals(ItemPosition.LAST, groupedStackPositionFor(index = 2, count = 3))
    }
}
