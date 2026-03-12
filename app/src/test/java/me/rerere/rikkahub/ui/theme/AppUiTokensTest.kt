package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.ui.components.ui.groupedStackPositionFor
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.swipeRevealRadii
import me.rerere.rikkahub.ui.components.textselection.quickAskGroupedButtonRadii
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.unit.dp

class AppUiTokensTest {
    @Test
    fun groupedItemRadii_returnsExpectedCornersForGroupedPositions() {
        assertEquals(
<<<<<<< HEAD
            GroupedItemRadii(24.dp, 24.dp, 8.dp, 8.dp),
=======
            GroupedItemRadii(20.dp, 20.dp, 8.dp, 8.dp),
>>>>>>> parent of f7993ce (Working on making the UI standardization actually work)
            groupedItemRadii(ItemPosition.FIRST)
        )
        assertEquals(
            GroupedItemRadii(8.dp, 8.dp, 8.dp, 8.dp),
            groupedItemRadii(ItemPosition.MIDDLE)
        )
        assertEquals(
<<<<<<< HEAD
            GroupedItemRadii(8.dp, 8.dp, 24.dp, 24.dp),
            groupedItemRadii(ItemPosition.LAST)
        )
        assertEquals(
            GroupedItemRadii(24.dp, 24.dp, 24.dp, 24.dp),
=======
            GroupedItemRadii(8.dp, 8.dp, 20.dp, 20.dp),
            groupedItemRadii(ItemPosition.LAST)
        )
        assertEquals(
            GroupedItemRadii(20.dp, 20.dp, 20.dp, 20.dp),
>>>>>>> parent of f7993ce (Working on making the UI standardization actually work)
            groupedItemRadii(ItemPosition.ONLY)
        )
    }

    @Test
<<<<<<< HEAD
    fun groupedInsetItemRadii_appliesInsetToExposedCorners() {
        assertEquals(
            GroupedItemRadii(20.dp, 20.dp, 4.dp, 4.dp),
            groupedInsetItemRadii(
                position = ItemPosition.FIRST,
                inset = 4.dp,
            )
        )
        assertEquals(
            GroupedItemRadii(46.dp, 46.dp, 46.dp, 46.dp),
            groupedInsetItemRadii(
                position = ItemPosition.MIDDLE,
                inset = 4.dp,
                selected = true,
            )
        )
    }

    @Test
=======
>>>>>>> parent of f7993ce (Working on making the UI standardization actually work)
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
            surfaceContainer = Color(0xFF282828),
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
        assertEquals(
            Color(0xFF282828),
            settingsSurfaceColor(colorScheme, darkTheme = true)
        )
        assertEquals(
            Color(0xFF404040),
            settingsSurfaceColor(colorScheme, darkTheme = false)
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

    @Test
    fun quickAskGroupedButtonRadii_usesOpticalInnerGridCorners() {
        assertEquals(
            GroupedItemRadii(12.dp, 8.dp, 8.dp, 8.dp),
            quickAskGroupedButtonRadii(
                rowIndex = 0,
                colIndex = 0,
                totalRows = 2,
                colsInRow = 2,
            )
        )
        assertEquals(
            GroupedItemRadii(8.dp, 8.dp, 12.dp, 8.dp),
            quickAskGroupedButtonRadii(
                rowIndex = 1,
                colIndex = 1,
                totalRows = 2,
                colsInRow = 2,
            )
        )
        assertEquals(
            GroupedItemRadii(8.dp, 8.dp, 12.dp, 12.dp),
            quickAskGroupedButtonRadii(
                rowIndex = 1,
                colIndex = 0,
                totalRows = 2,
                colsInRow = 1,
            )
        )
    }

    @Test
    fun swipeRevealRadii_usesGroupedOuterRadiusWhileDragging() {
        assertEquals(
            GroupedItemRadii(24.dp, 24.dp, 24.dp, 24.dp),
            swipeRevealRadii(
                position = ItemPosition.FIRST,
                selected = false,
                revealProgress = 1f,
            )
        )
        assertEquals(
            GroupedItemRadii(50.dp, 50.dp, 50.dp, 50.dp),
            swipeRevealRadii(
                position = ItemPosition.MIDDLE,
                selected = true,
                revealProgress = 1f,
            )
        )
    }
}
