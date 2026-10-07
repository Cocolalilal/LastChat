package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import android.graphics.Matrix

/**
 * Create Avatar picker shapes (12) — droplet/teardrop removed after design review.
 * Full engine `Wy` (25) is not dumped in the UI; teardrop stays resolvable
 * for older saved avatars but is not offered in the picker.
 */
object MarkShapes {
    /** Picker list — no teardrop/droplet. */
    val PICKER_IDS = listOf(
        "blob", "cloud", "square", "sparkle", "clover", "heart", "flower",
        "tablet", "wedge", "house", "star", "hex",
    )

    /** @deprecated use PICKER_IDS — kept for call sites expecting SHAPE_IDS */
    val SHAPE_IDS: List<String> get() = PICKER_IDS

    val SHAPES: List<String>
        get() = PICKER_IDS.filter { id ->
            !AvatarPackData.isLoaded() || AvatarPackData.grok.shapes.containsKey(id)
        }.ifEmpty { PICKER_IDS }

    fun resolve(shapeId: String): GrokShape? {
        if (!AvatarPackData.isLoaded()) return null
        val shapes = AvatarPackData.grok.shapes
        return shapes[shapeId] ?: shapes["blob"]
    }

    fun getShapePath(shapeId: String, size: Size): Path {
        val shape = resolve(shapeId) ?: return Path()
        val geo = AvatarPackData.grok
        val vb = geo.viewBox
        val path = AvatarPackData.parseSvgPath(shape.pathData)
        val androidPath = path.asAndroidPath()
        val matrix = Matrix()
        val scale = minOf(size.width / vb.width, size.height / vb.height)
        matrix.setScale(scale, scale)
        matrix.preTranslate(-vb.minX, -vb.minY)
        androidPath.transform(matrix)
        return androidPath.asComposePath()
    }
}
