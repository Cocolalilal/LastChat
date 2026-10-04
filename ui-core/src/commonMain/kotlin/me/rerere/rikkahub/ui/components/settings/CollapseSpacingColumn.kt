package me.rerere.rikkahub.ui.components.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Progress of a child whose height is already scaled by [progress].
 * Null children are ordinary rows and always take a full gap.
 */
data class SectionExpandProgress(val progress: Float)

private data class SectionExpandProgressModifier(
    val progress: Float,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = SectionExpandProgress(progress)
}

/** Marks a settings child so [CollapseSpacingColumn] can take its gap with it. */
fun Modifier.sectionExpandProgress(progress: Float): Modifier =
    this.then(SectionExpandProgressModifier(progress))

data class CollapseSpacingLayout(
    val positions: IntArray,
    val totalHeight: Int,
)

/**
 * Spacing for a column whose some children collapse.
 *
 * A plain [androidx.compose.foundation.layout.Arrangement.spacedBy] keeps a full gap
 * beside a child until that child leaves the composition, then deletes the gap in one
 * frame. That is the rest-then-hard-cut at the end of a settings minimize. This keeps
 * the gap that should remain between the neighbors and scales away only the extra gap
 * the collapsing child introduced, so height 0 matches the layout after removal.
 */
fun collapseSpacingLayout(
    heights: IntArray,
    expandProgress: Array<Float?>,
    gap: Int,
): CollapseSpacingLayout {
    val count = heights.size
    val positions = IntArray(count)
    if (count == 0) return CollapseSpacingLayout(positions, 0)
    val space = gap.coerceAtLeast(0)
    var cursor = 0
    var index = 0
    while (index < count) {
        if (expandProgress[index] == null) {
            if (index > 0 && expandProgress[index - 1] == null) cursor += space
            positions[index] = cursor
            cursor += heights[index]
            index++
            continue
        }
        val start = index
        while (index < count && expandProgress[index] != null) index++
        val end = index
        val precededBySolid = start > 0
        val followedBySolid = end < count
        if (precededBySolid && followedBySolid) {
            cursor += space
        } else if (precededBySolid) {
            var open = 0f
            for (child in start until end) {
                open = max(open, expandProgress[child]!!.coerceIn(0f, 1f))
            }
            cursor += (space * open).roundToInt()
        }
        for (child in start until end) {
            positions[child] = cursor
            cursor += heights[child]
            val progress = expandProgress[child]!!.coerceIn(0f, 1f)
            if (child + 1 < end) {
                val next = expandProgress[child + 1]!!.coerceIn(0f, 1f)
                cursor += (space * min(progress, next)).roundToInt()
            } else if (followedBySolid) {
                cursor += (space * progress).roundToInt()
            }
        }
    }
    return CollapseSpacingLayout(positions, cursor)
}

private object CollapseSpacingColumnScope : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

@Composable
fun CollapseSpacingColumn(
    modifier: Modifier = Modifier,
    spacing: Dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        content = { CollapseSpacingColumnScope.content() },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val gapPx = spacing.roundToPx()
        val heights = IntArray(placeables.size) { placeables[it].height }
        val progress = Array(measurables.size) { measurables[it].sectionProgress() }
        val spacingLayout = collapseSpacingLayout(heights, progress, gapPx)
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        }
        val height = spacingLayout.totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val x = horizontalAlignment.align(placeable.width, width, layoutDirection)
                placeable.place(x, spacingLayout.positions[index])
            }
        }
    }
}

private fun Measurable.sectionProgress(): Float? =
    (parentData as? SectionExpandProgress)?.progress
