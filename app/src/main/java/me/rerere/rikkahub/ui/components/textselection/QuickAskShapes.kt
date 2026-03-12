package me.rerere.rikkahub.ui.components.textselection

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val QuickAskOuterShape = RoundedCornerShape(32.dp)
val QuickAskInnerShape = RoundedCornerShape(16.dp)

private val QuickAskGroupedOuterCorner = 16.dp
private val QuickAskGroupedInnerCorner = 8.dp

fun quickAskGroupedButtonShape(
    rowIndex: Int,
    colIndex: Int,
    totalRows: Int,
    colsInRow: Int,
): RoundedCornerShape {
    val isFirstRow = rowIndex == 0
    val isLastRow = rowIndex == totalRows - 1
    val isFirstCol = colIndex == 0
    val isLastCol = colIndex == colsInRow - 1
    val isFullWidth = colsInRow == 1

    return RoundedCornerShape(
        topStart = if (isFirstRow && isFirstCol) QuickAskGroupedOuterCorner else QuickAskGroupedInnerCorner,
        topEnd = if (isFirstRow && (isLastCol || isFullWidth)) QuickAskGroupedOuterCorner else QuickAskGroupedInnerCorner,
        bottomStart = if (isLastRow && isFirstCol) QuickAskGroupedOuterCorner else QuickAskGroupedInnerCorner,
        bottomEnd = if (isLastRow && (isLastCol || isFullWidth)) QuickAskGroupedOuterCorner else QuickAskGroupedInnerCorner
    )
}
