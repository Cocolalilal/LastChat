package me.rerere.rikkahub.ui.components.textselection

import androidx.compose.foundation.shape.RoundedCornerShape
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.GroupedItemRadii

val QuickAskOuterShape = AppShapes.CardLarge
val QuickAskInnerShape = AppShapes.CardLargeInner16

private val QuickAskGroupedOuterCorner = AppShapes.CardLargeInner16Radius
private val QuickAskGroupedInnerCorner = AppShapes.GroupedInnerRadius

fun quickAskGroupedButtonRadii(
    rowIndex: Int,
    colIndex: Int,
    totalRows: Int,
    colsInRow: Int,
): GroupedItemRadii {
    val isFirstRow = rowIndex == 0
    val isLastRow = rowIndex == totalRows - 1
    val isFirstCol = colIndex == 0
    val isLastCol = colIndex == colsInRow - 1
    val isFullWidth = colsInRow == 1

    return GroupedItemRadii(
        topStart = if (isFirstRow && isFirstCol) QuickAskGroupedOuterCorner else QuickAskGroupedInnerCorner,
        topEnd = if (isFirstRow && (isLastCol || isFullWidth)) QuickAskGroupedOuterCorner else QuickAskGroupedInnerCorner,
        bottomEnd = if (isLastRow && (isLastCol || isFullWidth)) QuickAskGroupedOuterCorner else QuickAskGroupedInnerCorner,
        bottomStart = if (isLastRow && isFirstCol) QuickAskGroupedOuterCorner else QuickAskGroupedInnerCorner,
    )
}

fun quickAskGroupedButtonShape(
    rowIndex: Int,
    colIndex: Int,
    totalRows: Int,
    colsInRow: Int,
): RoundedCornerShape {
    val radii = quickAskGroupedButtonRadii(
        rowIndex = rowIndex,
        colIndex = colIndex,
        totalRows = totalRows,
        colsInRow = colsInRow,
    )
    return RoundedCornerShape(
        topStart = radii.topStart,
        topEnd = radii.topEnd,
        bottomEnd = radii.bottomEnd,
        bottomStart = radii.bottomStart,
    )
}
