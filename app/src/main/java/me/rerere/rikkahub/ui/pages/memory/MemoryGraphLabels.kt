package me.rerere.rikkahub.ui.pages.memory

/**
 * Pure (Compose-free, unit-tested) level-of-detail label selection for the full-screen graph, with a
 * hard **no-overlap** guarantee. Labels render at a constant screen size (they are NOT scaled by the
 * camera), so a label's screen-space box depends only on the camera scale — not the pan — which makes
 * the visible set recomputable only when the scale changes, never on every pan frame.
 *
 * Algorithm: a node is a *candidate* once its on-screen radius crosses [minScreenRadiusPx]. Candidates
 * are accepted greedily in a fixed priority order (bigger nodes first, id-stable tiebreak → the same
 * input always yields the same output, so labels never flicker), and a candidate is rejected if its
 * screen-space axis-aligned box would intersect any already-accepted label's box. O(n²) over the
 * ≤180-node neighborhood is trivial.
 */
object MemoryGraphLabels {

    /** A label's un-positioned screen-space size in pixels (from the cached [androidx] TextLayoutResult). */
    data class LabelSize(val width: Float, val height: Float)

    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun intersects(o: Box): Boolean = left < o.right && right > o.left && top < o.bottom && bottom > o.top
    }

    /**
     * @param worldX/worldY per-node world coordinates (length = node count).
     * @param radii per-node world radius (length = node count).
     * @param labelSizes per-node measured label size in px (constant screen size, camera-independent).
     * @param scale current camera scale.
     * @param offsetX/offsetY current camera translation (screen = world*scale + offset).
     * @param minScreenRadiusPx a node must be at least this big on screen before its label appears.
     * @param labelGapPx padding added around each label box so accepted labels keep breathing room.
     * @return indices of nodes whose labels are visible, deterministic given identical input.
     */
    fun computeVisibleLabels(
        worldX: FloatArray,
        worldY: FloatArray,
        radii: FloatArray,
        labelSizes: List<LabelSize>,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        minScreenRadiusPx: Float,
        labelGapPx: Float = 2f,
    ): IntArray {
        val n = minOf(worldX.size, worldY.size, radii.size, labelSizes.size)
        if (n == 0) return IntArray(0)

        // Candidates: on-screen radius past the threshold.
        val candidates = ArrayList<Int>(n)
        for (i in 0 until n) {
            if (radii[i] * scale >= minScreenRadiusPx) candidates.add(i)
        }
        if (candidates.isEmpty()) return IntArray(0)

        // Deterministic priority: larger radius first, index (a proxy for stable id order) as tiebreak.
        candidates.sortWith(compareByDescending<Int> { radii[it] }.thenBy { it })

        val accepted = ArrayList<Box>(candidates.size)
        val result = ArrayList<Int>(candidates.size)
        for (i in candidates) {
            val size = labelSizes[i]
            // Label sits centered below the node (matching the renderer), constant screen size.
            val nodeScreenX = worldX[i] * scale + offsetX
            val nodeScreenY = worldY[i] * scale + offsetY
            val top = nodeScreenY + radii[i] * scale + 2f
            val left = nodeScreenX - size.width / 2f
            val box = Box(
                left = left - labelGapPx,
                top = top - labelGapPx,
                right = left + size.width + labelGapPx,
                bottom = top + size.height + labelGapPx,
            )
            if (accepted.none { it.intersects(box) }) {
                accepted.add(box)
                result.add(i)
            }
        }
        result.sort() // stable, index order (draw order irrelevant but keeps output canonical)
        return result.toIntArray()
    }
}
